//
//  MAURLocation.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 10/06/16.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import "MAURLocation.h"

enum {
    TWO_MINUTES = 120,
    MAX_SECONDS_FROM_NOW = 86400
};

@interface MAURLocationMapper : NSObject
- (NSDictionary*) withDictionary:(NSDictionary*)values;
- (NSArray*) withArray:(NSArray*)values;
+ (instancetype) map:(MAURLocation*)location;
@end

// v4.5.1 — _location was previously a file-scope global. With real-time post (background queue)
// and background sync (NSURLSession queue) running concurrently, the second [+map:] invocation
// overwrote _location while the first mapper was mid-serialize, producing mixed location fields
// at the backend. Now per-instance ivar.
@implementation MAURLocationMapper {
    MAURLocation *_location;
}

- (id) mapValue:(id)value
{
    if ([value isKindOfClass:[NSString class]]) {
        id locationValue = [_location getValueForKey:value];
        // v4.5.1 — for placeholder keys ("@time", "@events", "@battery", ...), if the location
        // has no value, return NSNull instead of leaking the literal "@events" string to the
        // backend. For non-placeholder static strings (e.g. a `deviceId` literal in postTemplate)
        // keep the previous behaviour and return the string as-is.
        if ([value hasPrefix:@"@"]) {
            return locationValue != nil ? locationValue : [NSNull null];
        }
        return locationValue != nil ? locationValue : value;
    } else if ([value isKindOfClass:[NSDictionary class]]) {
        return [self withDictionary:value];
    } else if ([value isKindOfClass:[NSArray class]]) {
        return [self withArray:value];
    }

    return value;
}

- (NSDictionary*) withDictionary:(NSDictionary*)values
{
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:values.count];

    for (id key in values) {
        id value = [values objectForKey:key];
        [dict setObject:[self mapValue:value] forKey:key];
    }

    return dict;
}

- (NSArray*) withArray:(NSArray*)values
{
    NSMutableArray *locationArray = [[NSMutableArray alloc] initWithCapacity:values.count];

    for (id value in values) {
        [locationArray addObject:[self mapValue:value]];
    }

    return locationArray;
}

+ (instancetype) map:(MAURLocation*)location
{
    MAURLocationMapper *instance = [[MAURLocationMapper alloc] init];
    instance->_location = location;
    return instance;
}
@end


@implementation MAURLocation

@synthesize locationId, time, accuracy, altitudeAccuracy, speed, heading, altitude, latitude, longitude, provider, locationProvider, radius, isValid, recordedAt, simulated, drivingEvents, batteryLevel, isCharging;

+ (instancetype) fromCLLocation:(CLLocation*)location;
{
    MAURLocation *instance = [[MAURLocation alloc] init];

    instance.time = location.timestamp;
    instance.accuracy = [NSNumber numberWithDouble:location.horizontalAccuracy];
    instance.altitudeAccuracy = [NSNumber numberWithDouble:location.verticalAccuracy];
    // v5.0.3 — CoreLocation reporta **-1** en `speed` y en `course` cuando no tiene lectura
    // valida (primer fix, interior, reacquisicion, o parado sin rumbo). Guardarlo tal cual hacia
    // que ese -1 viajara hasta JS, hasta el `postTemplate` (`@speed` / `@bearing`) y hasta el
    // servidor: una flota registraba vehiculos a -1 m/s con rumbo -1 grados. Android nunca ha
    // hecho eso — `hasSpeed()` / `hasBearing()` son false y la clave sencillamente no se emite.
    // Aqui se hace lo mismo: nil => la clave no aparece en el diccionario ni en el template.
    // El detector interno ya trataba el -1 aparte (`hasSpeed`), asi que no cambia su semantica.
    instance.speed = location.speed >= 0 ? [NSNumber numberWithDouble:location.speed] : nil;
    instance.heading = location.course >= 0 ? [NSNumber numberWithDouble:location.course] : nil; // will be deprecated
    instance.altitude = [NSNumber numberWithDouble:location.altitude];
    instance.latitude = [NSNumber numberWithDouble:location.coordinate.latitude];
    instance.longitude = [NSNumber numberWithDouble:location.coordinate.longitude];
    // v4.5.6 — D6: iOS only ever produces CoreLocation fixes, so the textual provider is
    // always "gps". Without this the default post template resolved @provider to null and
    // the JS `providerChange` event (which keys off loc.provider) never fired on iOS.
    // `locationProvider` (the numeric DISTANCE_FILTER/ACTIVITY/RAW id) is stamped by each
    // provider right after building the instance.
    instance.provider = @"gps";

    if (@available(iOS 15.0, *)) {
        if (location.sourceInformation != nil && location.sourceInformation.isSimulatedBySoftware) {
            instance.simulated = @YES;
        } else {
            instance.simulated = @NO;
        }
    }

    return instance;
}

+ (NSTimeInterval) locationAge:(CLLocation*)location
{
    return -[location.timestamp timeIntervalSinceNow];
}

+ (NSDictionary*) toDictionary:(CLLocation*)location;
{
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:10];

    NSNumber* timestamp = [NSNumber numberWithDouble:([location.timestamp timeIntervalSince1970] * 1000)];
    [dict setObject:timestamp forKey:@"time"];
    [dict setObject:[NSNumber numberWithDouble:location.horizontalAccuracy] forKey:@"accuracy"];
    [dict setObject:[NSNumber numberWithDouble:location.verticalAccuracy] forKey:@"altitudeAccuracy"];
    // v5.0.3 — mismo criterio que +fromCLLocation:: -1 es "sin lectura", no un valor.
    if (location.speed >= 0) {
        [dict setObject:[NSNumber numberWithDouble:location.speed] forKey:@"speed"];
    }
    if (location.course >= 0) {
        [dict setObject:[NSNumber numberWithDouble:location.course] forKey:@"heading"];
        [dict setObject:[NSNumber numberWithDouble:location.course] forKey:@"bearing"];
    }
    [dict setObject:[NSNumber numberWithDouble:location.altitude] forKey:@"altitude"];
    [dict setObject:[NSNumber numberWithDouble:location.coordinate.latitude] forKey:@"latitude"];
    [dict setObject:[NSNumber numberWithDouble:location.coordinate.longitude] forKey:@"longitude"];

    return dict;
}

- (instancetype) init
{
    self = [super init];
    if (self != nil) {
        [self commonInit];
    }

    return self;
}

- (void) commonInit
{
    isValid = true;
}

/*
 * Age of location measured from now in seconds
 *
 */
- (NSTimeInterval) locationAge
{
    return -[time timeIntervalSinceNow];
}

- (NSDictionary*) toDictionaryWithId
{
    NSMutableDictionary *dict = (NSMutableDictionary*)[self toDictionary];

    // locationId is solely for internal purposes like deleteLocation method!!!
    if (locationId != nil) [dict setObject:locationId forKey:@"id"];

    return dict;
}

- (NSMutableDictionary*) toDictionary
{
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:13];

    if (time != nil) [dict setObject:[NSNumber numberWithDouble:([time timeIntervalSince1970] * 1000)] forKey:@"time"];
    if (accuracy != nil) [dict setObject:accuracy forKey:@"accuracy"];
    if (altitudeAccuracy != nil) [dict setObject:altitudeAccuracy forKey:@"altitudeAccuracy"];
    if (speed != nil) [dict setObject:speed forKey:@"speed"];
    if (heading != nil) [dict setObject:heading forKey:@"heading"]; // @deprecated
    if (heading != nil) [dict setObject:heading forKey:@"bearing"];
    if (altitude != nil) [dict setObject:altitude forKey:@"altitude"];
    if (latitude != nil) [dict setObject:latitude forKey:@"latitude"];
    if (longitude != nil) [dict setObject:longitude forKey:@"longitude"];
    if (provider != nil) [dict setObject:provider forKey:@"provider"];
    if (locationProvider != nil) [dict setObject:locationProvider forKey:@"locationProvider"];
    if (radius != nil) [dict setObject:radius forKey:@"radius"];
    if (recordedAt != nil) [dict setObject:[NSNumber numberWithDouble:([recordedAt timeIntervalSince1970] * 1000)] forKey:@"recordedAt"];
    if (simulated != nil) [dict setObject:simulated forKey:@"simulated"];
    // v4.3 — driving events anexados a este fix
    if (drivingEvents != nil && [drivingEvents count] > 0) [dict setObject:drivingEvents forKey:@"events"];
    // v4.4 — battery snapshot
    if (batteryLevel != nil) [dict setObject:batteryLevel forKey:@"battery"];
    if (isCharging != nil)   [dict setObject:isCharging   forKey:@"isCharging"];

    return dict;
}

- (id) getValueForKey:(id)key
{
    if (key == nil || ![key isKindOfClass:[NSString class]]) {
        return nil;
    }

    if ([key isEqualToString:@"@id"]) {
        return locationId;
    }
    if ([key isEqualToString:@"@time"]) {
        return [NSNumber numberWithDouble:([time timeIntervalSince1970] * 1000)];
    }
    // v4.5.4 — Unix epoch in SECONDS (not ms). Useful for Traccar OsmAnd
    // protocol and backends that treat 13-digit millis as garbage and silently
    // fall back to server time, dropping the real GPS fix time.
    if ([key isEqualToString:@"@time_seconds"]) {
        if (time == nil) return [NSNull null];
        return [NSNumber numberWithLongLong:(long long)[time timeIntervalSince1970]];
    }
    // v5.0.1 — documentado en docs/api.md y README pero sin implementar en ninguna plataforma.
    // Mismo formato que MAURUrlTemplateResolver +isoUtc:, que si lo tenia para las URLs.
    // v5.0.1 — `@mocked` es la clave documentada y multiplataforma; iOS solo entendia
    // `@simulated`, asi que tras revertir R2 (quitar `mocked` del template por defecto) el
    // CHANGELOG decia "`@mocked` sigue resolviendose" y en iOS era falso: quien lo pusiera en su
    // postTemplate recibia el campo en Android y nada en iOS.
    if ([key isEqualToString:@"@mocked"]) {
        return [self getValueForKey:@"@simulated"];
    }
    if ([key isEqualToString:@"@timestamp_iso"]) {
        if (time == nil) return [NSNull null];
        // Cacheado con dispatch_once: NSDateFormatter es el objeto mas caro de Foundation y esto
        // corre por cada posicion y por cada elemento de cada lote de sync (500 posiciones =
        // 500 formatters). Mismo patron que MAURUrlTemplateResolver +isoUtc:.
        static NSDateFormatter *iso = nil;
        static dispatch_once_t isoOnce;
        dispatch_once(&isoOnce, ^{
            iso = [[NSDateFormatter alloc] init];
            iso.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
            iso.dateFormat = @"yyyy-MM-dd'T'HH:mm:ss'Z'";
            iso.timeZone = [NSTimeZone timeZoneForSecondsFromGMT:0];
        });
        return [iso stringFromDate:time];
    }
    if ([key isEqualToString:@"@accuracy"]) {
        return accuracy;
    }
    if ([key isEqualToString:@"@altitudeAccuracy"]) {
        return altitudeAccuracy;
    }
    if ([key isEqualToString:@"@speed"]) {
        return speed;
    }
    if ([key isEqualToString:@"@heading"]) {
        return heading;
    }
    if ([key isEqualToString:@"@bearing"]) {
        return heading;
    }
    if ([key isEqualToString:@"@altitude"]) {
        return altitude;
    }
    if ([key isEqualToString:@"@latitude"]) {
        return latitude;
    }
    if ([key isEqualToString:@"@longitude"]) {
        return longitude;
    }
    if ([key isEqualToString:@"@provider"]) {
        return provider;
    }
    if ([key isEqualToString:@"@locationProvider"]) {
        return locationProvider;
    }
    if ([key isEqualToString:@"@radius"]) {
        return radius;
    }
    if ([key isEqualToString:@"@recordedAt"]) {
        return [NSNumber numberWithDouble:([recordedAt timeIntervalSince1970] * 1000)];
    }
    if ([key isEqualToString:@"@simulated"]) {
        return simulated;
    }
    // v4.3 — driving events array (nil when no events on this fix; mapper drops nil keys).
    if ([key isEqualToString:@"@events"]) {
        return (drivingEvents != nil && [drivingEvents count] > 0) ? drivingEvents : nil;
    }
    // v4.4 — battery snapshot
    if ([key isEqualToString:@"@battery"])    return batteryLevel;
    if ([key isEqualToString:@"@isCharging"]) return isCharging;

    return nil;
}

- (id) toResultFromTemplate:(id)locationTemplate
{
    if ([locationTemplate isKindOfClass:[NSArray class]]) {
        MAURLocationMapper *mapper = [MAURLocationMapper map:self];
        return [mapper withArray:locationTemplate];
    } else if ([locationTemplate isKindOfClass:[NSDictionary class]]) {
        MAURLocationMapper *mapper = [MAURLocationMapper map:self];
        return [mapper withDictionary:locationTemplate];
    }
    
    return [self toDictionary];
}

- (CLLocationCoordinate2D) coordinate
{
    CLLocationCoordinate2D coordinate;
    coordinate.latitude = [latitude doubleValue];
    coordinate.longitude = [longitude doubleValue];
    return coordinate;
}

- (double) distanceFromLocation:(MAURLocation*)location
{
    const float EarthRadius = 6378137.0f;
    double a_lat = [self.latitude doubleValue];
    double a_lon = [self.longitude doubleValue];
    double b_lat = [location.latitude doubleValue];
    double b_lon = [location.longitude doubleValue];
    double dtheta = (a_lat - b_lat) * (M_PI / 180.0);
    double dlambda = (a_lon - b_lon) * (M_PI / 180.0);
    double mean_t = (a_lat + b_lat) * (M_PI / 180.0) / 2.0;
    double cos_meant = cosf(mean_t);

    return sqrtf((EarthRadius * EarthRadius) * (dtheta * dtheta + cos_meant * cos_meant * dlambda * dlambda));
}

/**
 * Determines whether instance is better then Location reading
 * @param location  The new Location that you want to evaluate
 * Note: code taken from https://developer.android.com/guide/topics/location/strategies.html
 */
- (BOOL) isBetterLocation:(MAURLocation*)location
{
    if (location == nil) {
        // A instance location is always better than no location
        return NO;
    }

    // Check whether the new location fix is newer or older
    NSTimeInterval timeDelta = [self.time timeIntervalSinceDate:location.time];
    BOOL isSignificantlyNewer = timeDelta > TWO_MINUTES;
    BOOL isSignificantlyOlder = timeDelta < -TWO_MINUTES;
    BOOL isNewer = timeDelta > 0;

    // If it's been more than two minutes since the current location, use the new location
    // because the user has likely moved
    if (isSignificantlyNewer) {
        return YES;
        // If the new location is more than two minutes older, it must be worse
    } else if (isSignificantlyOlder) {
        return NO;
    }

    // Check whether the new location fix is more or less accurate
    NSInteger accuracyDelta = [self.accuracy integerValue] - [location.accuracy integerValue];
    BOOL isLessAccurate = accuracyDelta > 0;
    BOOL isMoreAccurate = accuracyDelta < 0;
    BOOL isSignificantlyLessAccurate = accuracyDelta > 200;

    // Check if the old and new location are from the same provider
    BOOL isFromSameProvider = YES; //TODO: check

    // Determine location quality using a combination of timeliness and accuracy
    if (isMoreAccurate) {
        return YES;
    } else if (isNewer && !isLessAccurate) {
        return YES;
    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
        return YES;
    }

    return NO;
}

- (BOOL) isBeyond:(MAURLocation*)location radius:(NSInteger)definedRadius
{
    double pointDistance = [self distanceFromLocation:location];
    return (pointDistance - [self.accuracy doubleValue] - [location.accuracy doubleValue]) > definedRadius;
}

- (BOOL) hasAccuracy
{
    // v4.5.5 — `accuracy` is an NSNumber*; the old `accuracy < 0` compared the POINTER,
    // which is never negative, so a negative (invalid) accuracy was reported as valid.
    if (accuracy == nil || [accuracy doubleValue] < 0) return NO;
    return YES;
}

- (BOOL) hasTime
{
    if (time != nil && [time timeIntervalSinceNow] > MAX_SECONDS_FROM_NOW) return NO;
    return YES;
}

- (NSString *) description
{
    return [NSString stringWithFormat:@"Location: id=%@ time=%@ lat=%@ lon=%@ accu=%@ aaccu=%@ speed=%@ bear=%@ alt=%@", locationId, time, latitude, longitude, accuracy, altitudeAccuracy, speed, heading, altitude];
}

-(id) copyWithZone: (NSZone *) zone
{
    MAURLocation *copy = [[[self class] allocWithZone: zone] init];
    if (copy) {
        copy.time = time;
        copy.accuracy = accuracy;
        copy.altitudeAccuracy = altitudeAccuracy;
        copy.speed = speed;
        copy.heading = heading;
        copy.altitude = altitude;
        copy.latitude = latitude;
        copy.longitude = longitude;
        copy.provider = provider;
        copy.locationProvider = locationProvider;
        copy.radius = radius;
        copy.isValid = isValid;
        copy.simulated = simulated;
        // v4.3: copy driving events array reference (transient; mutating one affects the other,
        // but in practice the original is discarded right after the copy is posted).
        copy.drivingEvents = drivingEvents != nil ? [drivingEvents mutableCopy] : nil;
        copy.batteryLevel = batteryLevel;
        copy.isCharging = isCharging;
    }

    return copy;
}

@end
