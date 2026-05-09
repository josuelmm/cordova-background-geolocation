//
//  CDVBackgroundGeolocation.h
//
//  Created by Marian Hello on 04/06/16.
//  Version 2.0.0
//
//  According to apache license
//
//  This is class is using code from christocracy cordova-plugin-background-geolocation plugin
//  https://github.com/christocracy/cordova-plugin-background-geolocation

#import "CDVBackgroundGeolocation.h"
#import "MAURConfig.h"
#import "MAURBackgroundGeolocationFacade.h"
#import "MAURBackgroundTaskManager.h"
#import "MAURSQLiteLocationDAO.h"
#import "MAURBackgroundSync.h"
#import <CoreMotion/CoreMotion.h>
#import <UIKit/UIKit.h>

static NSString * const TAG = @"CDVBackgroundGeolocation";

@implementation CDVBackgroundGeolocation {
    NSString *callbackId;
    MAURConfig *config;
    MAURBackgroundGeolocationFacade* facade;

    API_AVAILABLE(ios(10.0))
    __weak id<UNUserNotificationCenterDelegate> prevNotificationDelegate;
}

- (void)pluginInitialize
{

    facade = [[MAURBackgroundGeolocationFacade alloc] init];
    facade.delegate = self;

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onAppPause:) name:UIApplicationDidEnterBackgroundNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onAppResume:) name:UIApplicationWillEnterForegroundNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onFinishLaunching:) name:UIApplicationDidFinishLaunchingNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onAppTerminate:) name:UIApplicationWillTerminateNotification object:nil];

    // v3.5 Phase 4: forward sync notifications from MAURBackgroundSync into JS events.
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSyncStart:)    name:MAURBackgroundSyncDidStartNotification    object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSyncSuccess:)  name:MAURBackgroundSyncDidSucceedNotification  object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSyncError:)    name:MAURBackgroundSyncDidFailNotification     object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSyncProgress:) name:MAURBackgroundSyncDidProgressNotification object:nil];
    // v3.5 Phase 4: forward heartbeat notification from the facade timer into JS events.
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onHeartbeat:)    name:MAURHeartbeatNotification                 object:nil];

    // v4.0 Phase 6: forward driver-insights notifications.
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onTripStartN:)      name:MAURTripStartNotification      object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onTripEndN:)        name:MAURTripEndNotification        object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onMovingN:)         name:MAURMovingNotification         object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onStoppedN:)        name:MAURStoppedNotification        object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSpeedingN:)       name:MAURSpeedingNotification       object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onProviderChangeN:) name:MAURProviderChangeNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSOSN:)            name:MAURSOSNotification            object:nil];
    // v4.1 GPS-derived sensor-like events
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onHardBrakeN:)         name:MAURHardBrakeNotification         object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onRapidAccelerationN:) name:MAURRapidAccelerationNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSharpTurnN:)         name:MAURSharpTurnNotification         object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onPossibleCrashN:)     name:MAURPossibleCrashNotification     object:nil];
    // v4.2 sensor fusion
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onPhoneUsageWhileDrivingN:) name:MAURPhoneUsageWhileDrivingNotification object:nil];
}

#pragma mark - v4.0 Phase 6 driver-insight observers

- (void) onTripStartN:(NSNotification *)note
{
    MAURLocation *loc = note.userInfo[@"location"];
    if (loc != nil) [self sendEvent:@"tripStart" result:[loc toDictionaryWithId]];
    else            [self sendEvent:@"tripStart"];
}

- (void) onTripEndN:(NSNotification *)note
{
    NSMutableDictionary *p = [NSMutableDictionary dictionary];
    MAURLocation *loc = note.userInfo[@"location"];
    p[@"location"]   = loc != nil ? [loc toDictionaryWithId] : [NSNull null];
    p[@"distance"]   = note.userInfo[@"distance"]   ?: @0;
    p[@"durationMs"] = note.userInfo[@"durationMs"] ?: @0;
    [self sendEvent:@"tripEnd" result:p];
}

- (void) onMovingN:(NSNotification *)note
{
    MAURLocation *loc = note.userInfo[@"location"];
    if (loc != nil) [self sendEvent:@"moving" result:[loc toDictionaryWithId]];
    else            [self sendEvent:@"moving"];
}

- (void) onStoppedN:(NSNotification *)note
{
    MAURLocation *loc = note.userInfo[@"location"];
    if (loc != nil) [self sendEvent:@"stopped" result:[loc toDictionaryWithId]];
    else            [self sendEvent:@"stopped"];
}

- (void) onSpeedingN:(NSNotification *)note
{
    NSMutableDictionary *p = [NSMutableDictionary dictionary];
    MAURLocation *loc = note.userInfo[@"location"];
    p[@"location"] = loc != nil ? [loc toDictionaryWithId] : [NSNull null];
    p[@"speedKmh"] = note.userInfo[@"speedKmh"] ?: @0;
    p[@"limitKmh"] = note.userInfo[@"limitKmh"] ?: @0;
    [self sendEvent:@"speeding" result:p];
}

- (void) onProviderChangeN:(NSNotification *)note
{
    NSDictionary *p = @{ @"provider": note.userInfo[@"provider"] ?: @"" };
    [self sendEvent:@"providerChange" result:p];
}

- (void) onSOSN:(NSNotification *)note
{
    NSMutableDictionary *p = [NSMutableDictionary dictionary];
    NSDictionary *userPayload = note.userInfo[@"payload"];
    if ([userPayload isKindOfClass:[NSDictionary class]]) [p addEntriesFromDictionary:userPayload];
    MAURLocation *loc = note.userInfo[@"location"];
    if (loc != nil) p[@"location"] = [loc toDictionaryWithId];
    [self sendEvent:@"sos" result:p];
}

- (void) triggerSOS:(CDVInvokedUrlCommand *)command
{
    NSDictionary *payload = nil;
    if (command.arguments.count > 0 && [command.arguments[0] isKindOfClass:[NSDictionary class]]) {
        payload = command.arguments[0];
    }
    [facade triggerSOS:payload];
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK]
                                callbackId:command.callbackId];
}

// v4.1 GPS-derived sensor-like events
- (void) sendDrivingEventN:(NSString *)name note:(NSNotification *)note
{
    NSMutableDictionary *p = [NSMutableDictionary dictionary];
    MAURLocation *loc = note.userInfo[@"location"];
    p[@"location"] = loc != nil ? [loc toDictionaryWithId] : [NSNull null];
    p[@"value"]    = note.userInfo[@"value"] ?: @0;
    if (note.userInfo[@"source"] != nil) p[@"source"] = note.userInfo[@"source"];
    [self sendEvent:name result:p];
}
- (void) onHardBrakeN:(NSNotification *)n         { [self sendDrivingEventN:@"hardBrake"         note:n]; }
- (void) onRapidAccelerationN:(NSNotification *)n { [self sendDrivingEventN:@"rapidAcceleration" note:n]; }
- (void) onSharpTurnN:(NSNotification *)n         { [self sendDrivingEventN:@"sharpTurn"         note:n]; }
- (void) onPossibleCrashN:(NSNotification *)n     { [self sendDrivingEventN:@"possibleCrash"     note:n]; }
// v4.2 sensor fusion
- (void) onPhoneUsageWhileDrivingN:(NSNotification *)note
{
    MAURLocation *loc = note.userInfo[@"location"];
    if (loc != nil) [self sendEvent:@"phoneUsageWhileDriving" result:[loc toDictionaryWithId]];
    else            [self sendEvent:@"phoneUsageWhileDriving"];
}

- (void) onSyncStart:(NSNotification *)note
{
    [self sendEvent:@"syncStart"];
}

- (void) onSyncSuccess:(NSNotification *)note
{
    NSDictionary *info = note.userInfo ?: @{};
    [self sendEvent:@"syncSuccess" result:@{@"sent": info[@"sent"] ?: @0}];
}

- (void) onSyncError:(NSNotification *)note
{
    NSDictionary *info = note.userInfo ?: @{};
    [self sendEvent:@"syncError" result:@{
        @"httpStatus": info[@"httpStatus"] ?: @0,
        @"message": info[@"message"] ?: @""
    }];
}

- (void) onSyncProgress:(NSNotification *)note
{
    NSDictionary *info = note.userInfo ?: @{};
    [self sendEvent:@"syncProgress" resultAsNumber:(info[@"progress"] ?: @0)];
}

- (void) onHeartbeat:(NSNotification *)note
{
    NSDictionary *info = note.userInfo ?: @{};
    MAURLocation *loc = info[@"location"];
    if (loc != nil) {
        [self sendEvent:@"heartbeat" result:[loc toDictionaryWithId]];
    } else {
        [self sendEvent:@"heartbeat"];
    }
}

// v3.6 Phase 5 — Battery / OEM helpers. iOS does not expose Doze whitelist or
// vendor "auto-start" screens, so these are best-effort no-ops that resolve true
// (whitelist concept N/A) or open the app's Settings entry.

- (void) isIgnoringBatteryOptimizations:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:YES]
                                callbackId:command.callbackId];
}

- (void) requestIgnoreBatteryOptimizations:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:YES]
                                callbackId:command.callbackId];
}

- (void) openBatterySettings:(CDVInvokedUrlCommand *)command
{
    NSURL *url = [NSURL URLWithString:UIApplicationOpenSettingsURLString];
    if (url != nil && [[UIApplication sharedApplication] canOpenURL:url]) {
        [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:nil];
    }
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK]
                                callbackId:command.callbackId];
}

- (void) openAutoStartSettings:(CDVInvokedUrlCommand *)command
{
    // iOS has no per-OEM auto-start screen. Open the app's Settings entry as a best-effort
    // and report opened=false so the JS layer can decide whether to render a help screen.
    NSURL *url = [NSURL URLWithString:UIApplicationOpenSettingsURLString];
    BOOL opened = NO;
    if (url != nil && [[UIApplication sharedApplication] canOpenURL:url]) {
        [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:nil];
        opened = YES;
    }
    NSDictionary *info = @{
        @"opened": @(opened),
        @"manufacturer": @"apple",
        @"screen": @"UIApplicationOpenSettingsURLString"
    };
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:info]
                                callbackId:command.callbackId];
}

- (void) getManufacturerHelp:(CDVInvokedUrlCommand *)command
{
    NSDictionary *info = @{
        @"manufacturer": @"apple",
        @"steps": @[
            @"Settings → Privacy & Security → Location Services → [your app] → Always.",
            @"Settings → Privacy & Security → Location Services → [your app] → Precise Location → ON.",
            @"Settings → General → Background App Refresh → enable globally and for [your app].",
            @"Settings → Battery → Low Power Mode → off (Low Power Mode pauses background activity)."
        ]
    };
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:info]
                                callbackId:command.callbackId];
}

/**
 * configure plugin
 * @param {Number} stationaryRadius
 * @param {Number} distanceFilter
 * @param {Number} locationTimeout
 */
- (void) configure:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"configure");
    [self.commandDelegate runInBackground:^{
        config = [MAURConfig fromDictionary:[command.arguments objectAtIndex:0]];

        NSError *error = nil;
        CDVPluginResult* result = nil;
        if ([facade configure:config error:&error]) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:[self errorToDictionary:error]];
        }
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

/**
 * Turn on background geolocation
 * in case of failure it calls error callback from configure method
 * may fire two callback when location services are disabled and when authorization failed
 */
- (void) start:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"start");
    [self.commandDelegate runInBackground:^{
        NSError *error = nil;

        [facade start:&error];
        if (error == nil) {
            [self sendEvent:@"start"];
        } else {
            [self sendError:error];
        }
        CDVPluginResult* result = nil;
        if ([facade configure:config error:&error]) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:[self errorToDictionary:error]];
        }
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

/**
 * Turn it off
 */
- (void) stop:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"stop");
    [self.commandDelegate runInBackground:^{
        NSError *error = nil;

        [facade stop:&error];
        if (error == nil) {
            [self sendEvent:@"stop"];
        } else {
            [self sendError:error];
        }
        CDVPluginResult* result = nil;
        if ([facade configure:config error:&error]) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:[self errorToDictionary:error]];
        }
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

/**
 * Change
 * @param {Number} operation mode BACKGROUND/FOREGROUND
 */
- (void) switchMode:(CDVInvokedUrlCommand *)command
{
    NSLog(@"%@ #%@", TAG, @"switchMode");
    [self.commandDelegate runInBackground:^{
        MAUROperationalMode mode = [[command.arguments objectAtIndex: 0] intValue];
        [facade switchMode:mode];
    }];
}

- (void) getConfig:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"getConfig");
    [self.commandDelegate runInBackground:^{
        MAURConfig *config = [facade getConfig];
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[config toDictionary]];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) checkStatus:(CDVInvokedUrlCommand *)command
{
    NSLog(@"%@ #%@", TAG, @"checkStatus");
    [self.commandDelegate runInBackground:^{
        BOOL isRunning = [facade isStarted];
        BOOL locationServicesEnabled = [facade locationServicesEnabled];
        NSInteger authorizationStatus = [facade authorizationStatus];

        NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:3];
        [dict setObject:[NSNumber numberWithBool:isRunning] forKey:@"isRunning"];
        [dict setObject:[NSNumber numberWithBool:locationServicesEnabled] forKey:@"hasPermissions"]; // @deprecated
        [dict setObject:[NSNumber numberWithBool:locationServicesEnabled] forKey:@"locationServicesEnabled"];
        [dict setObject:[NSNumber numberWithInteger:authorizationStatus] forKey:@"authorization"];
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:dict];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

/**
 * v3.5 Phase 4: extended diagnostics. Returns permissions, precise location,
 * background refresh, low power and motion authorization on iOS.
 */
- (void) getDiagnostics:(CDVInvokedUrlCommand *)command
{
    NSLog(@"%@ #%@", TAG, @"getDiagnostics");
    [self.commandDelegate runInBackground:^{
        NSMutableDictionary *d = [NSMutableDictionary dictionary];

        // Common
        [d setObject:[NSNumber numberWithBool:[facade isStarted]] forKey:@"isRunning"];
        [d setObject:[NSNumber numberWithBool:[facade locationServicesEnabled]] forKey:@"locationServicesEnabled"];

        // Authorization status (raw + human-readable)
        CLAuthorizationStatus status;
        if (@available(iOS 14.0, *)) {
            CLLocationManager *lm = [[CLLocationManager alloc] init];
            status = lm.authorizationStatus;
            // accuracyAuthorization (Precise vs Reduced)
            BOOL precise = (lm.accuracyAuthorization == CLAccuracyAuthorizationFullAccuracy);
            [d setObject:[NSNumber numberWithBool:precise] forKey:@"preciseLocationEnabled"];
        } else {
            status = [CLLocationManager authorizationStatus];
            // Pre-iOS 14 had no Reduced accuracy concept; report YES.
            [d setObject:[NSNumber numberWithBool:YES] forKey:@"preciseLocationEnabled"];
        }
        [d setObject:[NSNumber numberWithInteger:status] forKey:@"authorization"];
        [d setObject:[self authorizationStatusText:status] forKey:@"authorizationStatusText"];

        // Background App Refresh
        [d setObject:[self backgroundRefreshStatusText] forKey:@"backgroundRefreshStatus"];

        // Low Power Mode (iOS 9+)
        if (@available(iOS 9.0, *)) {
            [d setObject:[NSNumber numberWithBool:[NSProcessInfo processInfo].lowPowerModeEnabled]
                  forKey:@"lowPowerModeEnabled"];
        } else {
            [d setObject:[NSNumber numberWithBool:NO] forKey:@"lowPowerModeEnabled"];
        }

        // Motion authorization. Use the flag exposed by CMMotionActivityManager.
        [d setObject:[self motionPermissionText] forKey:@"motionPermissionStatus"];

        // Pending sync count (best-effort).
        @try {
            NSNumber *pending = [[MAURSQLiteLocationDAO sharedInstance] getLocationsForSyncCount];
            [d setObject:(pending != nil ? pending : [NSNumber numberWithInt:0]) forKey:@"pendingSyncCount"];
        } @catch (NSException *e) {
            [d setObject:[NSNumber numberWithInt:0] forKey:@"pendingSyncCount"];
        }

        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:d];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (NSString *) authorizationStatusText:(CLAuthorizationStatus)status
{
    switch (status) {
        case kCLAuthorizationStatusNotDetermined: return @"notDetermined";
        case kCLAuthorizationStatusRestricted:    return @"restricted";
        case kCLAuthorizationStatusDenied:        return @"denied";
        case kCLAuthorizationStatusAuthorizedAlways:    return @"authorizedAlways";
        case kCLAuthorizationStatusAuthorizedWhenInUse: return @"authorizedWhenInUse";
    }
    return @"unknown";
}

- (NSString *) backgroundRefreshStatusText
{
    UIBackgroundRefreshStatus s = [UIApplication sharedApplication].backgroundRefreshStatus;
    switch (s) {
        case UIBackgroundRefreshStatusAvailable:  return @"available";
        case UIBackgroundRefreshStatusDenied:     return @"denied";
        case UIBackgroundRefreshStatusRestricted: return @"restricted";
    }
    return @"unknown";
}

- (NSString *) motionPermissionText
{
    if (![CMMotionActivityManager isActivityAvailable]) return @"restricted";
    if (@available(iOS 11.0, *)) {
        switch ([CMMotionActivityManager authorizationStatus]) {
            case CMAuthorizationStatusNotDetermined: return @"notDetermined";
            case CMAuthorizationStatusRestricted:    return @"restricted";
            case CMAuthorizationStatusDenied:        return @"denied";
            case CMAuthorizationStatusAuthorized:    return @"authorized";
        }
    }
    return @"notDetermined";
}

/**
 * Fetches current stationaryLocation
 */
- (void) getStationaryLocation:(CDVInvokedUrlCommand *)command
{
    NSLog(@"%@ #%@", TAG, @"getStationaryLocation");
    [self.commandDelegate runInBackground:^{
        CDVPluginResult* result = nil;

        MAURLocation* stationaryLocation = [facade getStationaryLocation];
        if (stationaryLocation) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[stationaryLocation toDictionary]];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:NO];
        }

        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) isLocationEnabled:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"isLocationEnabled");
    [self.commandDelegate runInBackground:^{
        BOOL isLocationEnabled = [facade locationServicesEnabled];
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:isLocationEnabled];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) showAppSettings:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"showAppSettings");
    [self.commandDelegate runInBackground:^{
        [facade showAppSettings];
    }];
}

- (void) showLocationSettings:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"showLocationSettings");
    [self.commandDelegate runInBackground:^{
        [facade showLocationSettings];
    }];
}

- (void) getPluginVersion:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"getPluginVersion");
    NSString *version = @"4.2.3"; // keep in sync with plugin.xml and Android PLUGIN_VERSION
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:version];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void) getLocations:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"getLocations");
    [self.commandDelegate runInBackground:^{
        NSArray *locations = [facade getLocations];
        NSMutableArray* dictionaryLocations = [[NSMutableArray alloc] initWithCapacity:[locations count]];
        for (MAURLocation* location in locations) {
            [dictionaryLocations addObject:[location toDictionaryWithId]];
        }
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:dictionaryLocations];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) getValidLocations:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"getValidLocations");
    [self.commandDelegate runInBackground:^{
        NSArray *locations = [facade getValidLocations];
        NSMutableArray* dictionaryLocations = [[NSMutableArray alloc] initWithCapacity:[locations count]];
        for (MAURLocation* location in locations) {
            [dictionaryLocations addObject:[location toDictionaryWithId]];
        }
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:dictionaryLocations];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) getValidLocationsAndDelete:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"getValidLocationsAndDelete");
    [self.commandDelegate runInBackground:^{
        NSArray *locations = [facade getValidLocationsAndDelete];
        NSMutableArray* dictionaryLocations = [[NSMutableArray alloc] initWithCapacity:[locations count]];
        for (MAURLocation* location in locations) {
            [dictionaryLocations addObject:[location toDictionaryWithId]];
        }
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:dictionaryLocations];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) deleteLocation:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"deleteLocation");
    [self.commandDelegate runInBackground:^{
        NSError *error = nil;
        int locationId = [[command.arguments objectAtIndex: 0] intValue];
        BOOL success = [facade deleteLocation:[[NSNumber alloc] initWithInt:locationId] error:&error];
        CDVPluginResult* result;
        if (success) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:[self errorToDictionary:error]];
        }
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) deleteAllLocations:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"deleteAllLocations");
    [self.commandDelegate runInBackground:^{
        NSError *error = nil;
        BOOL success = [facade deleteAllLocations:&error];
        CDVPluginResult* result;
        if (success) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:[self errorToDictionary:error]];
        }
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) getCurrentLocation:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"getCurrentLocation");
    [self.commandDelegate runInBackground:^{
        NSError *error = nil;
        NSArray *args = command.arguments;
        int timeout = [args objectAtIndex: 0] == [NSNull null] ? INT_MAX : [[args objectAtIndex: 0] intValue];
        long maximumAge = [args objectAtIndex: 1] == [NSNull null] ? LONG_MAX : [[args objectAtIndex: 1] longValue];
        BOOL enableHighAccuracy = [args objectAtIndex: 2] == [NSNull null] ? NO : [[args objectAtIndex: 2] boolValue];

        MAURLocation *location = [facade getCurrentLocation:timeout maximumAge:maximumAge enableHighAccuracy:enableHighAccuracy error:&error];
        CDVPluginResult* result;
        if (location != nil) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[location toDictionary]];
        } else {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:[self errorToDictionary:error]];
        }
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) getLogEntries:(CDVInvokedUrlCommand*)command
{
    NSLog(@"%@ #%@", TAG, @"getLogEntries");
    [self.commandDelegate runInBackground:^{
        NSArray *args = command.arguments;
        NSInteger limit = [args objectAtIndex: 0] == [NSNull null]
            ? 0 : [[args objectAtIndex: 0] integerValue];
        NSInteger entryId = [args objectAtIndex: 1] == [NSNull null]
            ? 0 : [[args objectAtIndex: 1] integerValue];
        NSString *minLogLevel = [args objectAtIndex: 2] == [NSNull null]
            ? @"DEBUG" : [args objectAtIndex: 2];

        NSArray *logs = [facade getLogEntries:limit fromLogEntryId:entryId minLogLevelFromString:minLogLevel];
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:logs];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) startTask:(CDVInvokedUrlCommand*)command
{
    NSUInteger taskKey = [[MAURBackgroundTaskManager sharedTasks] beginTask];
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsNSUInteger:taskKey];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void) endTask:(CDVInvokedUrlCommand*)command
{
    int taskKey = [[command.arguments objectAtIndex: 0] intValue];
    [[MAURBackgroundTaskManager sharedTasks] endTaskWithKey:taskKey];
}

- (void) forceSync:(CDVInvokedUrlCommand*)command
{
    [facade forceSync];
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void) clearSync:(CDVInvokedUrlCommand*)command
{
    [facade clearSync];
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void) getPendingSyncCount:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        NSInteger count = [facade getPendingSyncCount];
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsNSInteger:count];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) startSession:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [facade startSession];
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) getSessionLocations:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        NSArray *locations = [facade getSessionLocations];
        NSMutableArray *dictionaryLocations = [[NSMutableArray alloc] initWithCapacity:[locations count]];
        for (MAURLocation *location in locations) {
            [dictionaryLocations addObject:[location toDictionaryWithId]];
        }
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:dictionaryLocations];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) clearSession:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [facade clearSession];
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) getSessionLocationsCount:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        NSInteger count = [facade getSessionLocationsCount];
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsNSInteger:count];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void) addEventListener:(CDVInvokedUrlCommand*)command
{
    callbackId = command.callbackId;
}

- (void) removeEventListener:(CDVInvokedUrlCommand*)command
{
    callbackId = nil;
}

-(void) sendEvent:(NSString*)name
{
    if (callbackId == nil) {
        return;
    }

    NSDictionary *message = [[NSDictionary alloc] initWithObjectsAndKeys:[NSString stringWithFormat:@"%@", name], @"name", nil];
    CDVPluginResult* cordovaResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
    [cordovaResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:cordovaResult callbackId:callbackId];
}

-(void) sendEvent:(NSString*)name resultAsNumber:(NSNumber*)result
{
    if (callbackId == nil) {
        return;
    }

    NSDictionary *message = [[NSDictionary alloc] initWithObjectsAndKeys:
                           [NSString stringWithFormat:@"%@", name], @"name",
                           result, @"payload",
                           nil];
    CDVPluginResult* cordovaResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
    [cordovaResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:cordovaResult callbackId:callbackId];
}

-(void) sendEvent:(NSString*)name result:(id)result
{
    if (callbackId == nil) {
        return;
    }

    NSDictionary *message = [[NSDictionary alloc] initWithObjectsAndKeys:
                           [NSString stringWithFormat:@"%@", name], @"name",
                           result, @"payload",
                           nil];
    CDVPluginResult* cordovaResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
    [cordovaResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:cordovaResult callbackId:callbackId];
}

- (void) sendError:(NSError*)error
{
    NSLog(@"%@ #%@", TAG, @"onError");
    if (callbackId == nil) {
        return;
    }

    CDVPluginResult* cordovaResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:[self errorToDictionary:error]];
    [cordovaResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:cordovaResult callbackId:callbackId];
}

- (NSDictionary*) errorToDictionary:(NSError*)error
{
    NSDictionary *userInfo = [error userInfo];
    NSString *errorMessage = [error localizedDescription];
    if (errorMessage == nil) {
        errorMessage = [[userInfo objectForKey:NSUnderlyingErrorKey] localizedDescription];
    }
    return @{ @"code": [NSNumber numberWithLong:error.code], @"message": errorMessage};
}

- (void) onAuthorizationChanged:(MAURLocationAuthorizationStatus)authStatus
{
    NSLog(@"%@ #%@", TAG, @"onAuthorizationChanged");
    [self sendEvent:@"authorization" resultAsNumber:[NSNumber numberWithInt:authStatus]];
}

- (void) onLocationChanged:(MAURLocation*)location
{
    NSLog(@"%@ #%@", TAG, @"onLocationChanged");
    [self sendEvent:@"location" result:[location toDictionaryWithId]];
}

- (void) onStationaryChanged:(MAURLocation*)location
{
    NSLog(@"%@ #%@", TAG, @"onStationaryChanged");
    [self sendEvent:@"stationary" result:[location toDictionaryWithId]];
}

- (void) onLocationPause
{
    NSLog(@"%@ %@", TAG, @"location updates paused");
    [self sendEvent:@"stop"];
}

- (void) onLocationResume
{
    NSLog(@"%@ %@", TAG, @"location updates resumed");
    [self sendEvent:@"start"];
}

- (void) onActivityChanged:(MAURActivity *)activity
{
    NSLog(@"%@ #%@", TAG, @"onActivityChanged");
    [self sendEvent:@"activity" result:[activity toDictionary]];
}

- (void) onError:(NSError*)error
{
    NSLog(@"%@ #%@", TAG, @"onError");
    [self sendError:error];
}

-(void) onAppResume:(NSNotification *)notification
{
    NSLog(@"%@ %@", TAG, @"resumed");
    [facade switchMode:MAURForegroundMode];
}

-(void) onAppPause:(NSNotification *)notification
{
    NSLog(@"%@ %@", TAG, @"paused");
    [facade switchMode:MAURBackgroundMode];
}

-(void) onAbortRequested
{
    NSLog(@"%@ %@", TAG, @"abort requested by the server");
    [self sendEvent:@"abort_requested"];
}

- (void) onHttpAuthorization {
    NSLog(@"%@ %@", TAG, @"http authorization requested by the server");
    [self sendEvent:@"http_authorization"];
}

/**@
 * on UIApplicationDidFinishLaunchingNotification
 */
-(void) onFinishLaunching:(NSNotification *)notification
{
    NSDictionary *dict = [notification userInfo];
    MAURConfig *config = [facade getConfig];

    if (config.isDebugging)
    {
        if (@available(iOS 10, *))
        {
            UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
            prevNotificationDelegate = center.delegate;
            center.delegate = self;
        }
    }

    if ([dict objectForKey:UIApplicationLaunchOptionsLocationKey]) {
        NSLog(@"%@ %@", TAG, @"started by system on location event.");
        if (![config stopOnTerminate]) {
            [facade start:nil];
            [facade switchMode:MAURBackgroundMode];
        }
    }
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler
{
    if (prevNotificationDelegate && [prevNotificationDelegate respondsToSelector:@selector(userNotificationCenter:willPresentNotification:withCompletionHandler:)])
    {
        // Give other delegates (like FCM) the chance to process this notification

        [prevNotificationDelegate userNotificationCenter:center willPresentNotification:notification withCompletionHandler:^(UNNotificationPresentationOptions options) {
            completionHandler(UNNotificationPresentationOptionAlert);
        }];
    }
    else
    {
        completionHandler(UNNotificationPresentationOptionAlert);
    }
}

-(void) onAppTerminate:(NSNotification *)notification
{
    NSLog(@"%@ %@", TAG, @"appTerminate");
    [facade onAppTerminate];
}

@end
