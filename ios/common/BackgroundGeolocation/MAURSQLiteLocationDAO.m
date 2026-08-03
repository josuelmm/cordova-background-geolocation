//
//  MAURSQLiteLocationDAO.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 10/06/16.
//

#import <sqlite3.h>
#import <CoreLocation/CoreLocation.h>
#import "MAURSQLiteHelper.h"
#import "MAURGeolocationOpenHelper.h"
#import "MAURSQLiteLocationDAO.h"
#import "MAURLocationContract.h"

@implementation MAURSQLiteLocationDAO {
    FMDatabaseQueue* queue;
    MAURGeolocationOpenHelper *helper;
}

#pragma mark Singleton Methods

+ (instancetype) sharedInstance
{
    static MAURSQLiteLocationDAO *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[self alloc] init];
    });

    return instance;
}

- (id) init {
    if (self = [super init]) {
        helper = [[MAURGeolocationOpenHelper alloc] init];
        queue = [helper getWritableDatabase];
    }
    return self;
}

- (NSArray<MAURLocation*>*) getValidLocations
{
    __block NSMutableArray* locations = [[NSMutableArray alloc] init];
    
    NSString *sql = [[self getLocationSelectString] stringByAppendingString: @" WHERE " @LC_COLUMN_NAME_STATUS @" = ? ORDER BY " @LC_COLUMN_NAME_RECORDED_AT];

    [queue inDatabase:^(FMDatabase *database) {
        FMResultSet *rs = [database executeQuery:sql, [NSString stringWithFormat:@"%ld", MAURLocationPostPending]];
        while([rs next]) {
            MAURLocation *location = [self convertToLocation:rs];
            [locations addObject:location];
        }
        // TODO
        // NSLog(@"Retrieving locations failed code: %d: message: %s", sqlite3_errcode(database), sqlite3_errmsg(database));

        [rs close];
    }];

    return locations;
}

// v5.0 — D5: SELECT the valid locations and mark exactly those rows Deleted, in ONE transaction.
// `getValidLocationsAndDelete` used to be wired to -getLocationsForSync, which only flips the rows
// to SyncPending; nothing ever confirmed the upload, so -restoreStaleSyncLocationsOlderThan:
// pushed them back to PostPending and the caller got the same locations again.
// The UPDATE is scoped to the ids we actually collected (never a blanket
// `WHERE status = PostPending`), so rows INSERTed while this transaction is open survive.
- (NSArray<MAURLocation*>*) getValidLocationsAndDelete
{
    __block NSMutableArray* locations = [[NSMutableArray alloc] init];

    NSString *sql = [[self getLocationSelectString] stringByAppendingString: @" WHERE " @LC_COLUMN_NAME_STATUS @" = ? ORDER BY " @LC_COLUMN_NAME_RECORDED_AT];

    [queue inTransaction:^(FMDatabase *database, BOOL *rollback) {
        NSMutableArray *locationIds = [[NSMutableArray alloc] init];

        FMResultSet *rs = [database executeQuery:sql, @(MAURLocationPostPending)];
        while([rs next]) {
            MAURLocation *location = [self convertToLocation:rs];
            [locations addObject:location];
            if (location.locationId != nil) {
                [locationIds addObject:location.locationId];
            }
        }
        [rs close];

        if ([locationIds count] == 0) {
            return;
        }

        NSString *upd = [NSString stringWithFormat:@"UPDATE %@ SET %@ = ? WHERE %@ IN (%@)",
                         @LC_TABLE_NAME, @LC_COLUMN_NAME_STATUS, @LC_COLUMN_NAME_ID,
                         [self placeholdersForCount:[locationIds count]]];
        NSMutableArray *args = [[NSMutableArray alloc] initWithCapacity:([locationIds count] + 1)];
        [args addObject:@(MAURLocationDeleted)];
        [args addObjectsFromArray:locationIds];

        if (![database executeUpdate:upd withArgumentsInArray:args]) {
            NSLog(@"getValidLocationsAndDelete: marking %lu rows deleted failed code: %d: message: %@",
                  (unsigned long)[locationIds count], [database lastErrorCode], [database lastErrorMessage]);
            // Returning rows we could not mark deleted is exactly what produces duplicates on the
            // consumer side: roll back and report nothing.
            *rollback = YES;
            [locations removeAllObjects];
        }
    }];

    return locations;
}

- (NSArray<MAURLocation*>*) getAllLocations
{
    __block NSMutableArray* locations = [[NSMutableArray alloc] init];

    NSString *sql = [[self getLocationSelectString] stringByAppendingString: @" ORDER BY " @LC_COLUMN_NAME_RECORDED_AT];

    [queue inDatabase:^(FMDatabase *database) {
        FMResultSet *rs = [database executeQuery:sql];
        while([rs next]) {
            MAURLocation *location = [self convertToLocation:rs];
            [locations addObject:location];
        }
        // TODO
        // NSLog(@"Retrieving locations failed code: %d: message: %s", sqlite3_errcode(database), sqlite3_errmsg(database));

        [rs close];
    }];

    return locations;
}

- (NSArray<MAURLocation*>*) getLocationsForSync
{
    __block NSMutableArray* locations = [[NSMutableArray alloc] init];

    [queue inTransaction:^(FMDatabase *database, BOOL *rollback) {
        NSString *sql = [[self getLocationSelectString] stringByAppendingString: @" WHERE " @LC_COLUMN_NAME_STATUS @" = ? ORDER BY " @LC_COLUMN_NAME_RECORDED_AT];

        FMResultSet *rs = [database executeQuery:sql, [NSString stringWithFormat:@"%ld", MAURLocationPostPending]];
        while([rs next]) {
            MAURLocation *location = [self convertToLocation:rs];
            [locations addObject:location];
        }
        [rs close];

        // v4.5.1 FIX (CRITICAL): mark the rows we just selected as SyncPending — NOT Deleted.
        // The previous code UPDATEd the WHOLE table to Deleted before the upload had even started,
        // losing every fix on HTTP failure / network drop. Now:
        //   PostPending → SyncPending  (in-flight, do not re-include)
        //   on success in the network task: SyncPending → Deleted (deleteSyncedLocationsBefore:)
        //   on failure in the network task: SyncPending → PostPending (restoreFailedSyncLocations)
        NSString *upd = @"UPDATE " @LC_TABLE_NAME @" SET " @LC_COLUMN_NAME_STATUS @" = ? WHERE " @LC_COLUMN_NAME_STATUS @" = ?";
        if (![database executeUpdate:upd,
                [NSString stringWithFormat:@"%ld", MAURLocationSyncPending],
                [NSString stringWithFormat:@"%ld", MAURLocationPostPending]]) {
            NSLog(@"Marking PostPending → SyncPending failed code: %d: message: %@", [database lastErrorCode], [database lastErrorMessage]);
        }
    }];

    return locations;

}

- (NSNumber*) getLocationsForSyncCount
{
    __block NSNumber* rowCount = nil;

    [queue inTransaction:^(FMDatabase *database, BOOL *rollback) {
        NSString *sql = @"SELECT COUNT(*) FROM " @LC_TABLE_NAME @" WHERE " @LC_COLUMN_NAME_STATUS @" = ?";

        FMResultSet *rs = [database executeQuery:sql, [NSString stringWithFormat:@"%ld", MAURLocationPostPending]];
        if ([rs next]) {
            rowCount = [NSNumber numberWithInt:[rs intForColumnIndex:0]];
        }
        [rs close];
    }];

    return rowCount;
}

- (NSNumber*) persistLocation:(MAURLocation*)location intoDatabase:(FMDatabase*)database
{
    NSNumber* locationId = nil;
    NSTimeInterval timestamp = [[NSDate date] timeIntervalSince1970];
    NSNumber *recordedAt = [NSNumber numberWithDouble:timestamp];

    NSString *sql = @"INSERT INTO " @LC_TABLE_NAME @" ("
    @LC_COLUMN_NAME_TIME
    @COMMA_SEP @LC_COLUMN_NAME_ACCURACY
    @COMMA_SEP @LC_COLUMN_NAME_SPEED
    @COMMA_SEP @LC_COLUMN_NAME_BEARING
    @COMMA_SEP @LC_COLUMN_NAME_ALTITUDE
    @COMMA_SEP @LC_COLUMN_NAME_LATITUDE
    @COMMA_SEP @LC_COLUMN_NAME_LONGITUDE
    @COMMA_SEP @LC_COLUMN_NAME_PROVIDER
    @COMMA_SEP @LC_COLUMN_NAME_LOCATION_PROVIDER
    @COMMA_SEP @LC_COLUMN_NAME_STATUS
    @COMMA_SEP @LC_COLUMN_NAME_RECORDED_AT
    @COMMA_SEP @LC_COLUMN_NAME_EVENTS_JSON
    @COMMA_SEP @LC_COLUMN_NAME_BATTERY_LEVEL
    @COMMA_SEP @LC_COLUMN_NAME_IS_CHARGING
    @") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    // v4.5: serialize driving events array to JSON for SQLite storage.
    NSString *eventsJson = nil;
    if (location.drivingEvents != nil && [location.drivingEvents count] > 0) {
        NSError *jerr = nil;
        NSData *jd = [NSJSONSerialization dataWithJSONObject:location.drivingEvents options:0 error:&jerr];
        if (jd != nil) eventsJson = [[NSString alloc] initWithData:jd encoding:NSUTF8StringEncoding];
    }

    BOOL success = [database executeUpdate:sql,
        [NSNumber numberWithDouble:[location.time timeIntervalSince1970]],
        location.accuracy,
        location.speed,
        location.heading,
        location.altitude,
        location.latitude,
        location.longitude,
        location.provider ?: [NSNull null],
        location.locationProvider ?: [NSNull null],
        location.isValid == YES ? @(1) : @(0),
        recordedAt,
        eventsJson ?: [NSNull null],
        location.batteryLevel ?: [NSNull null],
        location.isCharging ?: [NSNull null]
    ];

    if (success) {
        locationId = [NSNumber numberWithLongLong:[database lastInsertRowId]];
    } else {
        NSLog(@"Inserting location %@ failed code: %d: message: %@", location.time, [database lastErrorCode], [database lastErrorMessage]);
    }

    return locationId;
}

- (NSNumber*) persistLocation:(MAURLocation*)location
{
    __block NSNumber* locationId = nil;

    [queue inDatabase:^(FMDatabase *database) {
        locationId = [self persistLocation:location intoDatabase:database];
    }];

    return locationId;
}

- (NSNumber*) persistLocation:(MAURLocation*)location limitRows:(NSInteger)maxRows
{
    // v4.5.5 — with maxRows <= 0 the row-count branches below could never insert: `rowCount <
    // maxRows` is false for an empty table, so control fell through to the recycling UPDATE,
    // which ran against `WHERE id = 0` (MIN(id) of an empty table) and silently dropped the
    // location. Treat a non-positive cap as "no cap" and just insert.
    if (maxRows <= 0) {
        return [self persistLocation:location];
    }

    __block NSNumber *locationId;
    NSTimeInterval timestamp = [[NSDate date] timeIntervalSince1970];
    NSNumber *recordedAt = [NSNumber numberWithDouble:timestamp];

    [queue inDatabase:^(FMDatabase *database) {
        NSInteger rowCount = 0;
        NSString *sql = @"SELECT COUNT(*) FROM " @LC_TABLE_NAME;

        FMResultSet *rs = [database executeQuery:sql];
        if ([rs next]) {
            rowCount = [rs intForColumnIndex:0];
        }
        [rs close];

        // v5.0.1 — paridad con ContentProviderLocationDAO de Android, que ya se corrigio.
        //
        // Habia dos problemas, los dos de PERDIDA DE DATOS y los dos por ordenar unicamente por
        // `recorded_at`:
        //   1. El DELETE sacrificaba las filas mas antiguas AUNQUE estuvieran SyncPending, es
        //      decir sin haberse enviado nunca. Agravante: con el borrado LOGICO restaurado, las
        //      lapidas (status = Deleted) consumen cupo de maxRows, asi que el recorte se dispara
        //      antes y se lleva por delante datos utiles.
        //   2. El UPDATE de reciclado apuntaba a MIN(id) con min(recorded_at) — otra vez la mas
        //      antigua, sin mirar el estado — y ademas se ejecutaba sobre una fila que el DELETE
        //      de arriba podia acabar de borrar.
        //
        // Ahora, igual que Android: se recorta a maxRows - 1 sacrificando PRIMERO las ya
        // entregadas (Deleted) y solo despues las mas antiguas, y luego se INSERTA. Se pierde
        // historico antes que datos sin enviar, y desaparece el reciclado con todos sus bordes.
        if (rowCount >= maxRows) {
            NSInteger toDelete = rowCount - maxRows + 1;
            sql = [NSString stringWithFormat:
                   @"DELETE FROM %1$@ WHERE %2$@ IN (SELECT %2$@ FROM %1$@ "
                   @"ORDER BY CASE %5$@ WHEN %6$ld THEN 0 ELSE 1 END, %3$@ LIMIT %4$ld);",
                   @LC_TABLE_NAME, @LC_COLUMN_NAME_ID, @LC_COLUMN_NAME_RECORDED_AT,
                   (long)toDelete, @LC_COLUMN_NAME_STATUS, (long)MAURLocationDeleted];
            if (![database executeStatements:sql]) {
                NSLog(@"%@ failed code: %d: message: %@", sql, [database lastErrorCode], [database lastErrorMessage]);
            }
        }

        locationId = [self persistLocation:location intoDatabase:database];
        return;
    }];

    return locationId;
}

- (BOOL) deleteLocation:(NSNumber*)locationId error:(NSError * __autoreleasing *)outError
{
    __block BOOL success;
    NSString *sql = @"UPDATE " @LC_TABLE_NAME @" SET " @LC_COLUMN_NAME_STATUS @" = ? WHERE " @LC_COLUMN_NAME_ID @" = ?";

    [queue inDatabase:^(FMDatabase *database) {
        if ([database executeUpdate:sql, [NSString stringWithFormat:@"%ld", MAURLocationDeleted], locationId]) {
            success = YES;
        } else {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"Delete location %@ failed code: %d: message: %@", locationId, errorCode, errorMessage);

            if (outError != NULL) {
                NSDictionary *errorDictionary = @{
                                                  NSLocalizedDescriptionKey: NSLocalizedString(errorMessage, nil)
                                                  };
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:errorDictionary];
            }

            success = NO;
        }
    }];

    return success;
}

- (BOOL) deleteAllLocations:(NSError * __autoreleasing *)outError
{
    __block BOOL success;
    NSString *sql = @"UPDATE " @LC_TABLE_NAME @" SET " @LC_COLUMN_NAME_STATUS @" = ?";

    [queue inDatabase:^(FMDatabase *database) {
        if ([database executeUpdate:sql, [NSString stringWithFormat:@"%ld", MAURLocationDeleted]]) {
            success = YES;
        } else {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"Deleting all locations failed code: %d: message: %@", errorCode, errorMessage);

            if (outError != NULL) {
                NSDictionary *errorDictionary = @{
                                                  NSLocalizedDescriptionKey: NSLocalizedString(errorMessage, nil)
                                                  };
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:errorDictionary];
            }

            success = NO;
        }
    }];

    return success;
}

- (BOOL) deleteSyncedLocationsBefore:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError
{
    __block BOOL success = YES;
    // v4.5.1 — operate on SyncPending (the rows the network task is/was uploading), not on
    // PostPending (those are still queued for real-time POST and must NOT be touched).
    NSString *sql = @"UPDATE " @LC_TABLE_NAME
        @" SET " @LC_COLUMN_NAME_STATUS @" = ? "
        @" WHERE " @LC_COLUMN_NAME_STATUS @" = ? AND " @LC_COLUMN_NAME_RECORDED_AT @" <= ?";
    [queue inDatabase:^(FMDatabase *database) {
        if (![database executeUpdate:sql,
                @(MAURLocationDeleted),
                @(MAURLocationSyncPending),
                @(cutoff)]) {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"deleteSyncedLocationsBefore failed code: %d: message: %@", errorCode, errorMessage);
            if (outError != NULL) {
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:@{ NSLocalizedDescriptionKey: errorMessage ?: @"" }];
            }
            success = NO;
        }
    }];
    return success;
}

- (BOOL) restoreStaleSyncLocationsOlderThan:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError
{
    // v4.5.1 — rows left as SyncPending because the previous sync's task was killed
    // (app suspended mid-upload, OS process death, manual kill) never reach their
    // success/failure callback. Call at the start of each sync window to rescue them.
    __block BOOL success = YES;
    NSString *sql = @"UPDATE " @LC_TABLE_NAME
        @" SET " @LC_COLUMN_NAME_STATUS @" = ? "
        @" WHERE " @LC_COLUMN_NAME_STATUS @" = ? AND " @LC_COLUMN_NAME_RECORDED_AT @" < ?";
    [queue inDatabase:^(FMDatabase *database) {
        if (![database executeUpdate:sql,
                @(MAURLocationPostPending),
                @(MAURLocationSyncPending),
                @(cutoff)]) {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"restoreStaleSyncLocations failed code: %d: message: %@", errorCode, errorMessage);
            if (outError != NULL) {
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:@{ NSLocalizedDescriptionKey: errorMessage ?: @"" }];
            }
            success = NO;
        }
    }];
    return success;
}

- (BOOL) restoreFailedSyncLocations:(NSError * __autoreleasing *)outError
{
    // v4.5.1 — undo the in-flight transition: SyncPending → PostPending so the next sync window
    // (or real-time post) re-tries them.
    __block BOOL success = YES;
    NSString *sql = @"UPDATE " @LC_TABLE_NAME @" SET " @LC_COLUMN_NAME_STATUS @" = ? WHERE " @LC_COLUMN_NAME_STATUS @" = ?";
    [queue inDatabase:^(FMDatabase *database) {
        if (![database executeUpdate:sql, @(MAURLocationPostPending), @(MAURLocationSyncPending)]) {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"restoreFailedSyncLocations failed code: %d: message: %@", errorCode, errorMessage);
            if (outError != NULL) {
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:@{ NSLocalizedDescriptionKey: errorMessage ?: @"" }];
            }
            success = NO;
        }
    }];
    return success;
}

- (BOOL) restoreFailedSyncLocations:(NSArray<NSNumber*>*)locationIds error:(NSError * __autoreleasing *)outError
{
    // v5.0 — D4: batch-scoped undo. The global -restoreFailedSyncLocations: flips EVERY SyncPending
    // row back to PostPending, so when one upload fails while a sibling upload is still in flight
    // (single mode creates one task per location), the sibling's rows are resurrected and uploaded
    // a second time. Restore only the ids this task owns.
    if (locationIds == nil || [locationIds count] == 0) {
        return YES;
    }

    __block BOOL success = YES;
    NSString *sql = [NSString stringWithFormat:@"UPDATE %@ SET %@ = ? WHERE %@ = ? AND %@ IN (%@)",
                     @LC_TABLE_NAME, @LC_COLUMN_NAME_STATUS, @LC_COLUMN_NAME_STATUS, @LC_COLUMN_NAME_ID,
                     [self placeholdersForCount:[locationIds count]]];
    NSMutableArray *args = [[NSMutableArray alloc] initWithCapacity:([locationIds count] + 2)];
    [args addObject:@(MAURLocationPostPending)];
    [args addObject:@(MAURLocationSyncPending)];
    [args addObjectsFromArray:locationIds];

    [queue inDatabase:^(FMDatabase *database) {
        if (![database executeUpdate:sql withArgumentsInArray:args]) {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"restoreFailedSyncLocations(ids) failed code: %d: message: %@", errorCode, errorMessage);
            if (outError != NULL) {
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:@{ NSLocalizedDescriptionKey: errorMessage ?: @"" }];
            }
            success = NO;
        }
    }];
    return success;
}

- (BOOL) restoreFailedSyncLocationsBefore:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError
{
    // v5.0 — D4: batch-mode counterpart of -deleteSyncedLocationsBefore:. A batch upload owns the
    // SyncPending rows recorded at or before the cutoff captured when it started; rows recorded
    // during the upload belong to the next batch and must keep their current state.
    __block BOOL success = YES;
    NSString *sql = @"UPDATE " @LC_TABLE_NAME
        @" SET " @LC_COLUMN_NAME_STATUS @" = ? "
        @" WHERE " @LC_COLUMN_NAME_STATUS @" = ? AND " @LC_COLUMN_NAME_RECORDED_AT @" <= ?";
    [queue inDatabase:^(FMDatabase *database) {
        if (![database executeUpdate:sql,
                @(MAURLocationPostPending),
                @(MAURLocationSyncPending),
                @(cutoff)]) {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"restoreFailedSyncLocationsBefore failed code: %d: message: %@", errorCode, errorMessage);
            if (outError != NULL) {
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:@{ NSLocalizedDescriptionKey: errorMessage ?: @"" }];
            }
            success = NO;
        }
    }];
    return success;
}

- (BOOL) deletePendingSyncLocations:(NSError * __autoreleasing *)outError
{
    __block BOOL success = YES;
    NSString *sql = @"UPDATE " @LC_TABLE_NAME @" SET " @LC_COLUMN_NAME_STATUS @" = ? WHERE " @LC_COLUMN_NAME_STATUS @" = ?";

    [queue inDatabase:^(FMDatabase *database) {
        if (![database executeUpdate:sql, [NSString stringWithFormat:@"%ld", MAURLocationDeleted], [NSString stringWithFormat:@"%ld", MAURLocationPostPending]]) {
            int errorCode = [database lastErrorCode];
            NSString *errorMessage = [database lastErrorMessage];
            NSLog(@"deletePendingSyncLocations failed code: %d: message: %@", errorCode, errorMessage);
            if (outError != NULL) {
                *outError = [NSError errorWithDomain:Domain code:errorCode userInfo:@{ NSLocalizedDescriptionKey: errorMessage ?: @"" }];
            }
            success = NO;
        }
    }];

    return success;
}

- (BOOL) clearDatabase
{
    __block BOOL success;

    [queue inDatabase:^(FMDatabase *database) {
        NSString *sql = [NSString stringWithFormat: @"DROP TABLE %@", @LC_TABLE_NAME];
        if (![database executeStatements:sql]) {
            NSLog(@"%@ failed code: %d: message: %@", sql, [database lastErrorCode], [database lastErrorMessage]);
        }
        sql = [MAURLocationContract createTableSQL];
        if (![database executeStatements:sql]) {
            NSLog(@"%@ failed code: %d: message: %@", sql, [database lastErrorCode], [database lastErrorMessage]);
            success = NO;
        } else {
            success = YES;
        }
    }];

    return success;
}

- (NSString*) getDatabaseName
{
    return [helper getDatabaseName];
}

- (NSString*) getDatabasePath
{
    return [helper getDatabasePath];
}

// v5.0 — D5/D4: "?,?,?" bind list for an `id IN (...)` clause. Ids are never interpolated into
// the SQL text, they stay bound parameters.
- (NSString*) placeholdersForCount:(NSUInteger)count
{
    NSMutableString *placeholders = [NSMutableString string];
    for (NSUInteger i = 0; i < count; i++) {
        [placeholders appendString:(i == 0 ? @"?" : @",?")];
    }
    return placeholders;
}

- (NSString*) getLocationSelectString {
    return @"SELECT " @LC_COLUMN_NAME_ID
    @COMMA_SEP @LC_COLUMN_NAME_TIME
    @COMMA_SEP @LC_COLUMN_NAME_ACCURACY
    @COMMA_SEP @LC_COLUMN_NAME_SPEED
    @COMMA_SEP @LC_COLUMN_NAME_BEARING
    @COMMA_SEP @LC_COLUMN_NAME_ALTITUDE
    @COMMA_SEP @LC_COLUMN_NAME_LATITUDE
    @COMMA_SEP @LC_COLUMN_NAME_LONGITUDE
    @COMMA_SEP @LC_COLUMN_NAME_PROVIDER
    @COMMA_SEP @LC_COLUMN_NAME_LOCATION_PROVIDER
    @COMMA_SEP @LC_COLUMN_NAME_STATUS
    @COMMA_SEP @LC_COLUMN_NAME_RECORDED_AT
    @COMMA_SEP @LC_COLUMN_NAME_EVENTS_JSON
    @COMMA_SEP @LC_COLUMN_NAME_BATTERY_LEVEL
    @COMMA_SEP @LC_COLUMN_NAME_IS_CHARGING
    @" FROM " @LC_TABLE_NAME;
}

- (MAURLocation*) convertToLocation:(FMResultSet*)rs {
    MAURLocation *location = [[MAURLocation alloc] init];
    location.locationId = [NSNumber numberWithLongLong:[rs longLongIntForColumnIndex:0]];
    NSTimeInterval timestamp = [rs doubleForColumnIndex:1];
    location.time = [NSDate dateWithTimeIntervalSince1970:timestamp];
    location.accuracy = [NSNumber numberWithDouble:[rs doubleForColumnIndex:2]];
    location.speed = [NSNumber numberWithDouble:[rs doubleForColumnIndex:3]];
    location.heading = [NSNumber numberWithDouble:[rs doubleForColumnIndex:4]];
    location.altitude = [NSNumber numberWithDouble:[rs doubleForColumnIndex:5]];
    location.latitude = [NSNumber numberWithDouble:[rs doubleForColumnIndex:6]];
    location.longitude = [NSNumber numberWithDouble:[rs doubleForColumnIndex:7]];
    location.provider = [rs stringForColumnIndex:8];
    location.locationProvider = [NSNumber numberWithInt:[rs intForColumnIndex:9]];
    location.isValid = [rs intForColumnIndex:10] == 1 ? YES : NO;
    NSTimeInterval recordedAt = [rs longForColumnIndex:11];
    location.recordedAt = [NSDate dateWithTimeIntervalSince1970:recordedAt];
    // v4.5: events / battery / charging
    NSString *eventsJson = [rs stringForColumnIndex:12];
    if (eventsJson != nil && eventsJson.length > 0) {
        NSError *jerr = nil;
        id parsed = [NSJSONSerialization JSONObjectWithData:[eventsJson dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:&jerr];
        if ([parsed isKindOfClass:[NSMutableArray class]]) location.drivingEvents = parsed;
        else if ([parsed isKindOfClass:[NSArray class]]) location.drivingEvents = [parsed mutableCopy];
    }
    if (![rs columnIndexIsNull:13]) location.batteryLevel = @([rs intForColumnIndex:13]);
    if (![rs columnIndexIsNull:14]) location.isCharging = @([rs intForColumnIndex:14] == 1);
    return location;
}

- (void) dealloc {
    [helper close];
    [queue close];
    helper = nil;
    queue = nil;
}

@end
