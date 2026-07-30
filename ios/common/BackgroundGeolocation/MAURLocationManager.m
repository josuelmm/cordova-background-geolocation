//
//  MAURLocationManager.m
//
//  Created by Jinru on 12/19/09.
//  Copyright 2009 Arizona State University. All rights reserved.
//

#import "MAURLocation.h"
#import "MAURLocationManager.h"
#import "MAURLogging.h"

#define SYSTEM_VERSION_EQUAL_TO(v)                  ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedSame)
#define SYSTEM_VERSION_GREATER_THAN(v)              ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedDescending)
#define SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(v)  ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] != NSOrderedAscending)
#define SYSTEM_VERSION_LESS_THAN(v)                 ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedAscending)
#define SYSTEM_VERSION_LESS_THAN_OR_EQUAL_TO(v)     ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] != NSOrderedDescending)

#define LOCATION_DENIED         "User denied use of location services."
#define LOCATION_RESTRICTED     "Application's use of location services is restricted."
#define LOCATION_NOT_DETERMINED "User undecided on application's use of location services."
// v5.0 — D3: reported through the existing onError: channel when the user granted location
// access with "Precise Location" switched off (iOS 14+).
#define LOCATION_REDUCED_ACCURACY "Reduced accuracy granted (Precise Location is off). Fixes may be off by kilometers."

// v5.0 — D3: must match the NSLocationTemporaryUsageDescriptionDictionary key added by plugin.xml.
static NSString *const PreciseLocationPurposeKey = @"PreciseLocationRequired";

static MAURLocationManager* sharedCLDelegate = nil;
static NSString *const TAG = @"MAURLocationManager";
static NSString *const Domain = @"com.marianhello";

@implementation MAURLocationManager {
    // v5.0 — D3: keeps the temporary-full-accuracy request to once per reduced-accuracy episode
    // instead of once per start (the ACTIVITY provider calls start:/stop: on every motion
    // transition) or, worse, once per fix.
    BOOL didHandleReducedAccuracy;
}
@synthesize locationManager, delegate;

- (id)init
{
    self = [super init];
    if (self != nil) {
        locationManager = [[CLLocationManager alloc] init];

        if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"9.0")) {
            locationManager.allowsBackgroundLocationUpdates = YES;
        }

        locationManager.delegate = self;
        locationManager.desiredAccuracy = kCLLocationAccuracyBest;
    }
    return self;
}

- (BOOL) start:(NSError * __autoreleasing *)outError
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");

    NSUInteger authStatus;

    if ([CLLocationManager respondsToSelector:@selector(authorizationStatus)]) { // iOS 4.2+
        // v4.5.6 — D21: +[CLLocationManager authorizationStatus] is deprecated since iOS 14;
        // use the instance property there and keep the class method for iOS < 14.
        if (@available(iOS 14.0, *)) {
            authStatus = locationManager.authorizationStatus;
        } else {
            authStatus = [CLLocationManager authorizationStatus];
        }

        if (authStatus == kCLAuthorizationStatusDenied) {
            if (outError != NULL) {
                NSDictionary *errorDictionary = @{
                                                  NSLocalizedDescriptionKey: NSLocalizedString(@LOCATION_DENIED, nil)
                                                  };

                *outError = [NSError errorWithDomain:Domain code:MAURBGPermissionDenied userInfo:errorDictionary];
            }
            
            return NO;
        }
        
        if (authStatus == kCLAuthorizationStatusRestricted) {
            if (outError != NULL) {
                NSDictionary *errorDictionary = @{
                                                  NSLocalizedDescriptionKey: NSLocalizedString(@LOCATION_RESTRICTED, nil)
                                                  };
                *outError = [NSError errorWithDomain:Domain code:MAURBGPermissionDenied userInfo:errorDictionary];
            }
            
            return NO;
        }
        
#ifdef __IPHONE_8_0
        // we do startUpdatingLocation even though we might not get permissions granted
        // we can stop later on when recieved callback on user denial
        // it's neccessary to start call startUpdatingLocation in iOS < 8.0 to show user prompt!
        
        // v4.5.5 — also escalate from "When In Use" to "Always". Previously only the
        // NotDetermined case asked, so a user who granted WhenInUse was never prompted for
        // the Always permission that background tracking actually requires.
        if (authStatus == kCLAuthorizationStatusNotDetermined ||
            authStatus == kCLAuthorizationStatusAuthorizedWhenInUse) {
            if ([locationManager respondsToSelector:@selector(requestAlwaysAuthorization)]) {  //iOS 8.0+
                [locationManager requestAlwaysAuthorization];
            }
        }
#endif
    }
    
    [locationManager startUpdatingLocation];
    return YES;
}

- (BOOL) stop:(NSError * __autoreleasing *)outError
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");
    [locationManager stopUpdatingLocation];
    return YES;
}

/**
 * v5.0 — D3: iOS 14 lets the user grant location access with "Precise Location" switched off.
 * CoreLocation then fuzzes every fix to a ~1-3 km accuracy circle, which the plugin used to record
 * as a genuine position, drawing kilometer-long phantom tracks. Ask (once per reduced-accuracy
 * episode, never on every fix) for temporary full accuracy and log a warning while it stays
 * reduced. Tracking is never aborted: coarse fixes are still better than nothing.
 *
 * The accuracy authorization is app-wide, so this reads the shared CLLocationManager and is valid
 * for every location provider, including MAURDistanceFilterLocationProvider which owns its own
 * CLLocationManager instance.
 *
 * @return YES the first time a reduced authorization is observed, so the caller can report it once
 *         through the plugin error channel. NO when full accuracy is granted, when nothing has
 *         been granted yet, when it was already reported, or below iOS 14.
 */
- (BOOL) requestTemporaryFullAccuracyIfReduced
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");

    if (@available(iOS 14.0, *)) {
        CLAuthorizationStatus authStatus = locationManager.authorizationStatus;
        if (authStatus != kCLAuthorizationStatusAuthorizedAlways &&
            authStatus != kCLAuthorizationStatusAuthorizedWhenInUse) {
            // Nothing granted yet: the accuracy is meaningless until the user answered the
            // prompt. Re-evaluated from the authorization-changed callback.
            return NO;
        }

        if (locationManager.accuracyAuthorization != CLAccuracyAuthorizationReducedAccuracy) {
            didHandleReducedAccuracy = NO; // full accuracy: re-arm for a later downgrade
            return NO;
        }

        DDLogWarn(@"%@ %@", TAG, @"reduced accuracy authorization: Precise Location is off, recorded fixes may be off by kilometers");

        if (didHandleReducedAccuracy) {
            return NO;
        }
        didHandleReducedAccuracy = YES;

        if ([locationManager respondsToSelector:@selector(requestTemporaryFullAccuracyAuthorizationWithPurposeKey:)]) {
            [locationManager requestTemporaryFullAccuracyAuthorizationWithPurposeKey:PreciseLocationPurposeKey];
        }

        return YES;
    }

    return NO;
}

/**
 * v5.0 — D3: message the caller reports through the plugin error channel when
 * requestTemporaryFullAccuracyIfReduced returned YES.
 */
+ (NSError *) reducedAccuracyError
{
    NSDictionary *errorDictionary = @{
                                      NSLocalizedDescriptionKey: NSLocalizedString(@LOCATION_REDUCED_ACCURACY, nil)
                                      };
    return [NSError errorWithDomain:Domain code:MAURBGServiceError userInfo:errorDictionary];
}

- (BOOL) startMonitoringSignificantLocationChanges
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");
    [locationManager startMonitoringSignificantLocationChanges];
    return YES;
}

- (BOOL) stopMonitoringSignificantLocationChanges
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");
    [locationManager stopMonitoringSignificantLocationChanges];
    return YES;
}

- (void) startMonitoringForRegion:(CLRegion*)region
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");
    [locationManager startMonitoringForRegion:region];
}

- (void) stopMonitoringForRegion:(CLRegion*)region
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");
    [locationManager stopMonitoringForRegion:region];
}

- (void) stopMonitoringForRegionIdentifier:(NSString*)identifier
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");
    for (CLRegion *region in [locationManager monitoredRegions]){
        if([region.identifier isEqualToString:identifier]){
            [locationManager stopMonitoringForRegion:region];
        }
    }
}

- (void) stopMonitoringAllRegions
{
    NSAssert([NSThread isMainThread], @"%@ %@", TAG, @"should only be called from the main thread.");
    for (CLRegion *region in [locationManager monitoredRegions]) {
        [locationManager stopMonitoringForRegion:region];
    }
}

- (NSSet<__kindof CLRegion *>*) monitoredRegions
{
    return locationManager.monitoredRegions;
}

- (void) setShowsBackgroundLocationIndicator:(BOOL)shows
{
    if (@available(iOS 11, *)) {
        locationManager.showsBackgroundLocationIndicator = shows;
    }
}

- (void) setPausesLocationUpdatesAutomatically:(BOOL)newPausesLocationsUpdatesAutomatically
{
    locationManager.pausesLocationUpdatesAutomatically = newPausesLocationsUpdatesAutomatically;
}

- (BOOL) pausesLocationUpdatesAutomatically
{
    return locationManager.pausesLocationUpdatesAutomatically;
}

- (void) setDistanceFilter:(CLLocationDistance)newDistanceFiler
{
    locationManager.distanceFilter = newDistanceFiler;
}

- (CLLocationDistance) distanceFilter
{
    return locationManager.distanceFilter;
}

- (void) setActivityType:(CLActivityType)newActivityType
{
    locationManager.activityType = newActivityType;
}

- (CLActivityType) activityType
{
    return locationManager.activityType;
}

- (void) setDesiredAccuracy:(CLLocationAccuracy)newDesiredAccuracy
{
    locationManager.desiredAccuracy = newDesiredAccuracy;
}

- (CLLocationAccuracy) desiredAccuracy
{
    return locationManager.desiredAccuracy;
}


#pragma mark -
#pragma mark CLLocationManagerDelegate Methods
- (void) locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations
{
    [self.delegate onLocationsChanged:locations];
}

- (void) locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error
{
    if (self.delegate && [self.delegate respondsToSelector:@selector(onError:)]) {
        NSDictionary *errorDictionary = @{
                                          NSUnderlyingErrorKey : error
                                          };
        NSError *outError = [NSError errorWithDomain:Domain code:MAURBGServiceError userInfo:errorDictionary];

        [self.delegate onError:outError];
    }
}

// v4.5.4: iOS 14+ delegate callback. The legacy
// `locationManager:didChangeAuthorizationStatus:` is deprecated in iOS 14 but
// still delivered alongside this one, so we ignore the legacy variant when
// running on iOS 14+ to avoid double-notifying delegates (RAW + ACTIVITY
// providers go through this MAURLocationManager singleton).
- (void) locationManagerDidChangeAuthorization:(CLLocationManager *)manager API_AVAILABLE(ios(14.0))
{
    [self maurDispatchAuthorizationStatus:manager.authorizationStatus];
}

- (void) locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status
{
    if (@available(iOS 14.0, *)) {
        return; // delivered by locationManagerDidChangeAuthorization: above
    }
    [self maurDispatchAuthorizationStatus:status];
}

- (void) maurDispatchAuthorizationStatus:(CLAuthorizationStatus)status
{
    MAURLocationAuthorizationStatus authStatus;

    switch(status) {
        case kCLAuthorizationStatusRestricted:
        case kCLAuthorizationStatusDenied:
            authStatus = MAURLocationAuthorizationDenied;
            break;
        case kCLAuthorizationStatusAuthorizedAlways:
            authStatus = MAURLocationAuthorizationAlways;
            break;
        case kCLAuthorizationStatusAuthorizedWhenInUse:
            authStatus = MAURLocationAuthorizationForeground;
            break;
        default:
            return;
    }

    if (self.delegate && [self.delegate respondsToSelector:@selector(onAuthorizationChanged:)]) {
        [self.delegate onAuthorizationChanged:authStatus];
    }
}

- (void) locationManagerDidPauseLocationUpdates:(CLLocationManager *)manager
{
    if (self.delegate && [self.delegate respondsToSelector:@selector(onLocationPause:)]) {
        [self.delegate onLocationPause:manager];
    }
}

- (void) locationManagerDidResumeLocationUpdates:(CLLocationManager *)manager
{
    if (self.delegate && [self.delegate respondsToSelector:@selector(onLocationResume:)]) {
        [self.delegate onLocationResume:manager];
    }
}

- (void) locationManager:(CLLocationManager *)manager didExitRegion:(CLRegion *)region
{
    if (self.delegate && [self.delegate respondsToSelector:@selector(onRegionExit:)]) {
        [self.delegate onRegionExit:region];
    }
}

#pragma mark - Singleton implementation in ARC
+ (MAURLocationManager *)sharedInstance
{
    static MAURLocationManager *sharedLocationControllerInstance = nil;
    static dispatch_once_t predicate;
    dispatch_once(&predicate, ^{
        sharedLocationControllerInstance = [[self alloc] init];
    });
    return sharedLocationControllerInstance;
}

+ (id)allocWithZone:(NSZone *)zone {
    @synchronized(self) {
        if (sharedCLDelegate == nil) {
            sharedCLDelegate = [super allocWithZone:zone];
            return sharedCLDelegate;  // assignment and return on first allocation
        }
    }
    return nil; // on subsequent allocation attempts return nil
}

- (id)copyWithZone:(NSZone *)zone
{
    return self;
}

@end
