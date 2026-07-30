//
//  MAURRawLocationProvider.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 06/11/2017.
//  Copyright © 2017 mauron85. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "MAURRawLocationProvider.h"
#import "MAURLocationManager.h"
#import "MAURLogging.h"

static NSString * const TAG = @"RawLocationProvider";
static NSString * const Domain = @"com.marianhello";

@implementation MAURRawLocationProvider {

    BOOL isStarted;
    MAURLocationManager *locationManager;
    
    MAURConfig *_config;
}

- (instancetype) init
{
    self = [super init];

    if (self) {
        isStarted = NO;
    }

    return self;
}

- (void) onCreate {
    locationManager = [MAURLocationManager sharedInstance];
    locationManager.delegate = self;
}

- (BOOL) onConfigure:(MAURConfig*)config error:(NSError * __autoreleasing *)outError
{
    DDLogVerbose(@"%@ configure", TAG);
    _config = config;

    locationManager.pausesLocationUpdatesAutomatically = [config pauseLocationUpdates];
    // v4.5.6 — D20: showsBackgroundLocationIndicator used to be applied only by the
    // DISTANCE_FILTER provider, so the setting was silently ignored under RAW.
    if ([config hasShowsBackgroundLocationIndicator]) {
        [locationManager setShowsBackgroundLocationIndicator:[config showsBackgroundLocationIndicator]];
    }
    locationManager.activityType = [config decodeActivityType];
    locationManager.distanceFilter = config.distanceFilter.integerValue; // meters
    locationManager.desiredAccuracy = [config decodeDesiredAccuracy];

    return YES;
}

- (BOOL) onStart:(NSError * __autoreleasing *)outError
{
    DDLogInfo(@"%@ will start", TAG);

    if (!isStarted) {
        [locationManager stopMonitoringSignificantLocationChanges];
        isStarted = [locationManager start:outError];
    }

    return isStarted;
}

- (BOOL) onStop:(NSError * __autoreleasing *)outError
{
    DDLogInfo(@"%@ will stop", TAG);

    if (!isStarted) {
        return YES;
    }

    [locationManager stopMonitoringSignificantLocationChanges];
    if ([locationManager stop:outError]) {
        isStarted = NO;
        return YES;
    }

    return NO;
}

- (void) onTerminate
{
    if (isStarted && !_config.stopOnTerminate) {
        [locationManager startMonitoringSignificantLocationChanges];
    }
}

- (void) onAuthorizationChanged:(MAURLocationAuthorizationStatus)authStatus
{
    [self.delegate onAuthorizationChanged:authStatus];
}

- (void) onLocationsChanged:(NSArray*)locations
{
    for (CLLocation *location in locations) {
        MAURLocation *bgloc = [MAURLocation fromCLLocation:location];
        // v4.5.6 — D6: stamp the numeric provider id (RAW_PROVIDER == 2). The default post
        // template maps @locationProvider, which used to resolve to null on iOS.
        bgloc.locationProvider = [NSNumber numberWithInt:RAW_PROVIDER];
        [self.delegate onLocationChanged:bgloc];
    }
}

- (void) onError:(NSError*)error
{
    [self.delegate onError:error];
}

// v4.5.5 — renamed from onPause:/onResume: to match the MAURLocationManagerDelegate protocol.
// MAURLocationManager guards these calls with respondsToSelector:@selector(onLocationPause:),
// so under the old names pause/resume events were never delivered.
- (void) onLocationPause:(CLLocationManager*)manager
{
    [self.delegate onLocationPause];
}

- (void) onLocationResume:(CLLocationManager*)manager
{
    [self.delegate onLocationResume];
}

- (void) onDestroy {
    DDLogInfo(@"Destroying %@ ", TAG);
    [self onStop:nil];

    // v4.5.4: MAURLocationManager is a singleton shared with the other providers.
    // Release our delegate slot so a later provider swap does not leave this
    // (already destroyed) instance as the active delegate.
    if (locationManager != nil && locationManager.delegate == self) {
        locationManager.delegate = nil;
    }
}

- (void) dealloc
{
    if (locationManager != nil && locationManager.delegate == self) {
        locationManager.delegate = nil;
    }
}

@end

