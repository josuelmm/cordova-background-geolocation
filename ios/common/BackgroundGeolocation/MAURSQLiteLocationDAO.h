//
//  MAURSQLiteLocationDAO.h
//  BackgroundGeolocation
//
//  Created by Marian Hello on 10/06/16.
//

#ifndef MAURSQLiteLocationDAO_h
#define MAURSQLiteLocationDAO_h

#import <Foundation/Foundation.h>
#import "FMDB.h"
#import "MAURLocation.h"

@class Location;

@interface MAURSQLiteLocationDAO : NSObject

+ (instancetype) sharedInstance;
- (id) init NS_UNAVAILABLE;
- (NSArray<MAURLocation*>*) getAllLocations;
- (NSArray<MAURLocation*>*) getLocationsForSync;
- (NSArray<MAURLocation*>*) getValidLocations;
/** v5.0 — D5: returns the same rows as -getValidLocations and marks EXACTLY those rows as
 *  MAURLocationDeleted inside one transaction. The previous implementation of the public
 *  `getValidLocationsAndDelete` API reused -getLocationsForSync, which only moves the rows to
 *  SyncPending: the stale-sync rescue then resurrected them ~15 min later and the consumer
 *  received them twice. Rows are scoped by id, so locations inserted during the transaction are
 *  never touched. Returns an empty array (and rolls back) if the UPDATE fails — never hand out
 *  rows we failed to mark deleted. */
- (NSArray<MAURLocation*>*) getValidLocationsAndDelete;
- (NSNumber*) getLocationsForSyncCount;
- (NSNumber*) persistLocation:(MAURLocation*)location;
- (NSNumber*) persistLocation:(MAURLocation*)location limitRows:(NSInteger)maxRows;
- (BOOL) deleteLocation:(NSNumber*)locationId error:(NSError * __autoreleasing *)outError;
- (BOOL) deleteAllLocations:(NSError * __autoreleasing *)outError;
/** Mark all locations pending sync (PostPending) as deleted. Clears the sync queue without sending. */
- (BOOL) deletePendingSyncLocations:(NSError * __autoreleasing *)outError;
/** v4.5.1 — soft-delete only sync-pending rows whose `recorded_at` is <= cutoff (UNIX seconds).
 *  Used after a successful background-sync POST so locations persisted DURING the upload (race
 *  window) are NOT incorrectly marked deleted. */
- (BOOL) deleteSyncedLocationsBefore:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError;
/** v4.5.1 — undo the in-flight SyncPending state when the upload failed. SyncPending → PostPending
 *  so the next sync window re-tries them. Without this, a network failure during background-sync
 *  would silently drop every pending location. */
- (BOOL) restoreFailedSyncLocations:(NSError * __autoreleasing *)outError;
/** v5.0 — D4: batch-scoped variant of -restoreFailedSyncLocations:. SyncPending → PostPending for
 *  the given ids ONLY, so a failing upload cannot resurrect rows that a sibling upload still has
 *  in flight (those would then be sent twice). Mirrors the success path, which is already scoped
 *  through the task's "locationIds" associated object. A nil/empty array is a no-op. */
- (BOOL) restoreFailedSyncLocations:(NSArray<NSNumber*>*)locationIds error:(NSError * __autoreleasing *)outError;
/** v5.0 — D4: batch-scoped variant for batch-mode uploads, which own every SyncPending row whose
 *  `recorded_at` is <= the cutoff captured when the upload started (the task's "uploadCutoff").
 *  Rows recorded during the upload belong to a later batch and stay SyncPending / untouched. */
- (BOOL) restoreFailedSyncLocationsBefore:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError;
/** v4.5.1 — recover SyncPending rows that got stuck (app/process killed between getLocationsForSync
 *  and the upload's success/failure callback). Rows whose `recorded_at` is older than `cutoff`
 *  (UNIX seconds) are restored to PostPending so they get retried. Call before each sync window. */
- (BOOL) restoreStaleSyncLocationsOlderThan:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError;
- (BOOL) clearDatabase;
- (NSString*) getDatabaseName;
- (NSString*) getDatabasePath;
// private
/** v5.0 — D5/D4: builds a "?,?,?" bind list of `count` placeholders for an `id IN (...)` clause. */
- (NSString*) placeholdersForCount:(NSUInteger)count;
- (NSString*) getLocationSelectString;
- (MAURLocation*) convertToLocation:(FMResultSet*)rs;

@end

#endif /* MAURSQLiteLocationDAO_h */
