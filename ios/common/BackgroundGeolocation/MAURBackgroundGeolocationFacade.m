//
//  MAURBackgroundGeolocationFacade.m
//
//  Created by Marian Hello on 04/06/16.
//  Version 2.0.0
//
//  According to apache license
//
//  This is class is using code from christocracy cordova-plugin-background-geolocation plugin
//  https://github.com/christocracy/cordova-plugin-background-geolocation
//

#import <UIKit/UIKit.h>
#import <CoreLocation/CoreLocation.h>
#import <AudioToolbox/AudioToolbox.h>
// v4.5.6 — D25: UNUserNotificationCenter replaces the UILocalNotification API (iOS 10+).
#import <UserNotifications/UserNotifications.h>
#import "MAURBackgroundGeolocationFacade.h"
#import "MAURPostLocationTask.h"
#import "MAURSQLiteConfigurationDAO.h"
#import "MAURSQLiteLocationDAO.h"
#import "MAURSessionLocationDAO.h"
#import "MAURBackgroundTaskManager.h"
#import "MAURLogging.h"
#import "FMDBLogger.h"
#import "MAURLogReader.h"
#import "MAURLocationManager.h"
#import "MAURActivityLocationProvider.h"
#import "MAURDistanceFilterLocationProvider.h"
#import "MAURRawLocationProvider.h"
#import "MAURUncaughtExceptionLogger.h"
#import "MAURPostLocationTask.h"
#import "INTULocationManager.h"
#import "MAURSensorFusionDetector.h"

// error messages
#define CONFIGURE_ERROR_MSG             "Configuration error."
#define SERVICE_ERROR_MSG               "Cannot start service error."
#define UNKNOWN_LOCATION_PROVIDER_MSG   "Unknown location provider."

// Position errors
// https://developer.mozilla.org/en-US/docs/Web/API/PositionError
#define PERMISSION_DENIED       1
#define POSITION_UNAVAILABLE    2
#define TIMEOUT                 3

static NSString * const BGGeolocationDomain = @"com.marianhello";
static NSString * const TAG = @"BgGeo";

FMDBLogger *sqliteLogger;

@interface MAURBackgroundGeolocationFacade () <MAURProviderDelegate, MAURPostLocationTaskDelegate, MAURSensorFusionListener>
@end

// v3.5 Phase 4: notification name for heartbeat events. CDVBackgroundGeolocation observes
// it to forward into the JS event "heartbeat" with the latest known location.
NSString * const MAURHeartbeatNotification     = @"MAURHeartbeatNotification";
// v4.0 Phase 6: driver-insight notifications.
NSString * const MAURTripStartNotification     = @"MAURTripStartNotification";
NSString * const MAURTripEndNotification       = @"MAURTripEndNotification";
NSString * const MAURMovingNotification        = @"MAURMovingNotification";
NSString * const MAURStoppedNotification       = @"MAURStoppedNotification";
NSString * const MAURSpeedingNotification      = @"MAURSpeedingNotification";
NSString * const MAURProviderChangeNotification = @"MAURProviderChangeNotification";
NSString * const MAURSOSNotification           = @"MAURSOSNotification";
// v4.1
NSString * const MAURHardBrakeNotification         = @"MAURHardBrakeNotification";
NSString * const MAURRapidAccelerationNotification = @"MAURRapidAccelerationNotification";
NSString * const MAURSharpTurnNotification         = @"MAURSharpTurnNotification";
NSString * const MAURPossibleCrashNotification     = @"MAURPossibleCrashNotification";
// v4.2
NSString * const MAURPhoneUsageWhileDrivingNotification = @"MAURPhoneUsageWhileDrivingNotification";

@implementation MAURBackgroundGeolocationFacade {
    BOOL isStarted;
    MAUROperationalMode operationMode;

    UILocalNotification *localNotification;

    // configurable options
    MAURConfig *_config;

    MAURLocation *stationaryLocation;
    MAURLocation *lastReceivedLocation;          // v3.5 Phase 4: heartbeat payload
    NSTimer      *heartbeatTimer;                 // v3.5 Phase 4
    MAURAbstractLocationProvider<MAURLocationProvider> *locationProvider;
    MAURPostLocationTask *postLocationTask;

    // v4.0 Phase 6: driver-insights state
    BOOL    drIsMoving;
    BOOL    drTripActive;
    NSTimeInterval drTripStartedAt;
    double  drTripDistanceMeters;
    BOOL    drHasPrev;
    double  drPrevLat, drPrevLon;
    NSTimeInterval drAboveTripSpeedSince;
    NSTimeInterval drBelowMovingSince;
    BOOL    drWasSpeeding;
    NSString *drLastProvider;
    // v4.1 GPS-derived sensor-like state
    double   drPrevSpeed;
    NSTimeInterval drPrevSpeedAt;
    double   drPrevBearing;
    BOOL     drHasPrevBearing;
    NSTimeInterval drPrevBearingAt;
    NSTimeInterval drLastHardBrakeAt, drLastRapidAccelAt, drLastSharpTurnAt, drLastCrashAt;
    // v4.2 sensor fusion
    MAURSensorFusionDetector *sensorFusion;
    // v4.3 — events buffered when no simultaneous fix is available; drained onto next location.
    NSMutableArray *pendingDrivingEvents;
    // v5.0 — F4: periodic tick for the driving state machine (see -scheduleDrivingTick).
    NSTimer      *drivingTickTimer;
}


- (instancetype) init
{
    self = [super init];
    
    if (self == nil) {
        return self;
    }
    
    [DDLog addLogger:[DDASLLogger sharedInstance] withLevel:DDLogLevelInfo];
    [DDLog addLogger:[DDTTYLogger sharedInstance] withLevel:DDLogLevelDebug];
    
    sqliteLogger = [[FMDBLogger alloc] initWithLogDirectory:[self loggerDirectory]];
    sqliteLogger.saveThreshold     = 1;
    sqliteLogger.saveInterval      = 0;
    sqliteLogger.maxAge            = 60 * 60 * 24 * 7; //  7 days
    sqliteLogger.deleteInterval    = 60 * 60 * 24;     //  1 day
    sqliteLogger.deleteOnEverySave = NO;
    
    [DDLog addLogger:sqliteLogger withLevel:DDLogLevelDebug];
    
    MAHUncaughtExceptionLogger *logger = mah_get_uncaught_exception_logger();
    logger->setEnabled(YES);
    
    postLocationTask = [[MAURPostLocationTask alloc] init];
    postLocationTask.delegate = self;

    // v4.5.6 — D25: kept only as the iOS 9 fallback path of -notify:.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    localNotification = [[UILocalNotification alloc] init];
    localNotification.timeZone = [NSTimeZone defaultTimeZone];
#pragma clang diagnostic pop

    isStarted = NO;
    pendingDrivingEvents = [[NSMutableArray alloc] init];

    // v4.5.1 — wire pending events + battery snapshot into the post task so they run AFTER
    // any locationTransform that may produce a new instance / return nil. The previous flow
    // (flush BEFORE add:) lost buffered events whenever the transform returned nil.
    postLocationTask.pendingDrivingEventsBuffer = pendingDrivingEvents;
    __weak typeof(self) weakSelf = self;
    postLocationTask.attachBatterySnapshot = ^(MAURLocation * _Nonnull loc) {
        __strong typeof(self) strongSelf = weakSelf;
        if (strongSelf == nil) return;
        BOOL includeBat = (strongSelf->_config == nil
                || strongSelf->_config.includeBattery == nil
                || [strongSelf->_config.includeBattery boolValue]);
        if (includeBat) [strongSelf attachBatterySnapshotTo:loc];
    };

    return self;
}

/**
 * configure manager
 * @param {Config} configuration
 * @param {NSError} optional error
 */
- (BOOL) configure:(MAURConfig*)config error:(NSError * __autoreleasing *)outError
{
    __block NSError *error = nil;
    
    MAURConfig *currentConfig = [self getConfig];
    _config = [MAURConfig merge:currentConfig withConfig:config];
    
    DDLogInfo(@"%@ #configure: %@", TAG, _config);
    
    postLocationTask.config = _config;
    
    MAURSQLiteConfigurationDAO* configDAO = [MAURSQLiteConfigurationDAO sharedInstance];
    [configDAO persistConfiguration:_config];
    
    // ios 8 requires permissions to send local-notifications
    if ([_config isDebugging]) {
        [self runOnMainThread:^{
            // v4.5.6 — D25: on iOS 10+ the notification permission is requested through
            // UNUserNotificationCenter; -registerUserNotificationSettings: is deprecated and
            // does not authorize UNNotificationRequests.
            if (@available(iOS 10, *)) {
                [[UNUserNotificationCenter currentNotificationCenter]
                    requestAuthorizationWithOptions:(UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge)
                                  completionHandler:^(BOOL granted, NSError * _Nullable error) {
                    if (!granted) {
                        DDLogDebug(@"%@ debug notifications not authorized: %@", TAG, error.localizedDescription);
                    }
                }];
                return;
            }
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
            UIApplication *app = [UIApplication sharedApplication];
            if ([[UIApplication sharedApplication]respondsToSelector:@selector(currentUserNotificationSettings)]) {
                UIUserNotificationType wantedTypes = UIUserNotificationTypeBadge|UIUserNotificationTypeSound|UIUserNotificationTypeAlert;
                UIUserNotificationSettings *currentSettings = [app currentUserNotificationSettings];
                if (!currentSettings || (currentSettings.types != wantedTypes)) {
                    if ([app respondsToSelector:@selector(registerUserNotificationSettings:)]) {
                        [app registerUserNotificationSettings:[UIUserNotificationSettings settingsForTypes:wantedTypes categories:nil]];
                    }
                }
            }
#pragma clang diagnostic pop
        }];
    }
    
    if (isStarted) {
        // Note: CLLocationManager must be created on a thread with an active run loop (main thread)
        [self runOnMainThread:^{

            // requesting new provider
            if (![currentConfig.locationProvider isEqual:_config.locationProvider]) {
                [locationProvider onDestroy]; // destroy current provider
                locationProvider = [self getProvider:_config.locationProvider.intValue error:&error];
            }

            if (locationProvider == nil) {
                return;
            }

            // trap configuration errors
            if (![locationProvider onConfigure:_config error:&error]) {
                return;
            }

            isStarted = [locationProvider onStart:&error];
            locationProvider.delegate = self;
        }];

        // v4.1: hot-reload heartbeat scheduler if heartbeatInterval changed.
        NSInteger prevHb = currentConfig.heartbeatInterval != nil ? [currentConfig.heartbeatInterval integerValue] : 0;
        NSInteger newHb  = _config.heartbeatInterval         != nil ? [_config.heartbeatInterval         integerValue] : 0;
        if (prevHb != newHb) {
            [self scheduleHeartbeat]; // cancels and reschedules; is a no-op if 0.
        }
        // Driver-insights detector reads `_config.drivingEvents` on every feed; no rebuild needed
        // unless the dictionary identity changed in a way that toggles `enabled`. Always reset
        // accumulators to apply the new thresholds cleanly from this point on.
        if (![[currentConfig.drivingEvents description] isEqualToString:[_config.drivingEvents description]]) {
            [self drivingDetectorReset];
            // v5.0 — F4: enabling/disabling drivingEvents at runtime must arm/disarm the tick.
            if (isStarted) [self scheduleDrivingTick]; else [self cancelDrivingTick];
            // v4.2: re-evaluate sensor fusion as well (might have just been enabled/disabled).
            [self configureSensorFusion];
            if (isStarted) [sensorFusion start];
        }
    }
    
    if (error != nil) {
        if (outError != nil) {
            NSDictionary *userInfo = @{
                                       NSLocalizedDescriptionKey: NSLocalizedString(@CONFIGURE_ERROR_MSG, nil),
                                       NSUnderlyingErrorKey : error
                                       };
            *outError = [NSError errorWithDomain:BGGeolocationDomain code:MAURBGConfigureError userInfo:userInfo];
        }
        
        return NO;
    }
    
    return YES;
}

/**
 * Turn on background geolocation
 * in case of failure it calls error callback from configure method
 * may fire two callback when location services are disabled and when authorization failed
 */
- (BOOL) start:(NSError * __autoreleasing *)outError
{
    DDLogInfo(@"%@ #start: %d", TAG, isStarted);
    
    if (isStarted) {
        return NO;
    }
    
    __block NSError *error = nil;
    MAURConfig *config = [self getConfig];
    
    postLocationTask.config = config;
    [postLocationTask start];
    
    // Note: CLLocationManager must be created on a thread with an active run loop (main thread)
    [self runOnMainThread:^{
        locationProvider = [self getProvider:config.locationProvider.intValue error:&error];
        
        if (locationProvider == nil) {
            return;
        }
        
        // trap configuration errors
        if (![locationProvider onConfigure:config error:&error]) {
            return;
        }
        
        isStarted = [locationProvider onStart:&error];
        locationProvider.delegate = self;

        // v5.0 — D3: with "Precise Location" off (iOS 14+) CoreLocation fuzzes every fix to a
        // ~1-3 km circle and the plugin used to store those as real positions, drawing
        // kilometer-long phantom tracks. Ask once for temporary full accuracy and, if the
        // authorization is still reduced, report it through the existing error channel so the app
        // can react instead of trusting the data. Runs after the delegate is wired so the report
        // is not swallowed. Checked again from -onAuthorizationChanged: because on a fresh install
        // the granted accuracy is only known once the user answered the permission prompt.
        if (isStarted && [[MAURLocationManager sharedInstance] requestTemporaryFullAccuracyIfReduced]) {
            [self onError:[MAURLocationManager reducedAccuracyError]];
        }
    }];


    if (!isStarted) {
        if (outError != nil) {
            *outError = error;
        }

        return NO;
    }

    // v3.5 Phase 4: schedule heartbeat once provider is up.
    [self scheduleHeartbeat];
    // v5.0 — F4: independent of heartbeatInterval and of enableWatchdog.
    [self scheduleDrivingTick];
    // v4.2 Phase 8: configure & start sensor fusion if requested.
    [self configureSensorFusion];
    [sensorFusion start];

    return isStarted;
}

/**
 * Turn off background geolocation
 */
- (BOOL) stop:(NSError * __autoreleasing *)outError
{
    DDLogInfo(@"%@ #stop", TAG);

    if (!isStarted) {
        return YES;
    }

    // v3.5 Phase 4: cancel heartbeat scheduler.
    [self cancelHeartbeat];
    // v5.0 — F4
    [self cancelDrivingTick];
    // v4.0 Phase 6: reset driver-insights state machine.
    [self drivingDetectorReset];
    // v4.2 Phase 8: stop sensor fusion sampling.
    sensorFusion.tripActive = NO;
    [sensorFusion stop];

    [postLocationTask stop];

    [self runOnMainThread:^{
        isStarted = ![locationProvider onStop:outError];
    }];

    return isStarted;
}

// v3.5 Phase 4: heartbeat scheduler.
- (void) scheduleHeartbeat
{
    [self cancelHeartbeat];
    if (_config == nil || _config.heartbeatInterval == nil) return;
    NSInteger ms = [_config.heartbeatInterval integerValue];
    if (ms <= 0) return;
    NSTimeInterval seconds = ms / 1000.0;
    DDLogDebug(@"%@ scheduling heartbeat every %.2fs", TAG, seconds);
    dispatch_async(dispatch_get_main_queue(), ^{
        heartbeatTimer = [NSTimer scheduledTimerWithTimeInterval:seconds
                                                         target:self
                                                       selector:@selector(onHeartbeatTick:)
                                                       userInfo:nil
                                                        repeats:YES];
    });
}

- (void) cancelHeartbeat
{
    // v4.5.5 — the timer is created on the main queue (see -scheduleHeartbeat) and NSTimer is
    // not thread safe: -invalidate must run on the thread that scheduled it. Hop to the main
    // queue unconditionally so every read/write of `heartbeatTimer` happens there, and so the
    // cancel/create pair stays FIFO-ordered against -scheduleHeartbeat's own main-queue block.
    dispatch_async(dispatch_get_main_queue(), ^{
        if (heartbeatTimer != nil) {
            [heartbeatTimer invalidate];
            heartbeatTimer = nil;
        }
    });
}

- (void) onHeartbeatTick:(NSTimer *)timer
{
    NSDictionary *userInfo = lastReceivedLocation != nil
        ? @{ @"location": lastReceivedLocation }
        : @{};
    [[NSNotificationCenter defaultCenter] postNotificationName:MAURHeartbeatNotification
                                                        object:self
                                                      userInfo:userInfo];
}

#pragma mark - v4.0 Phase 6 driver-insights state machine

- (void) drivingDetectorReset
{
    drIsMoving = NO;
    drTripActive = NO;
    drTripStartedAt = 0;
    drTripDistanceMeters = 0;
    drHasPrev = NO;
    drAboveTripSpeedSince = 0;
    drBelowMovingSince = 0;
    drWasSpeeding = NO;
    drLastProvider = nil;
    drPrevSpeed = 0;
    drPrevSpeedAt = 0;
    drPrevBearing = 0;
    drHasPrevBearing = NO;
    drPrevBearingAt = 0;
    drLastHardBrakeAt = drLastRapidAccelAt = drLastSharpTurnAt = drLastCrashAt = 0;
}

#pragma mark - v5.0 F4: periodic driving tick

/**
 * v5.0 — F4: the driving state machine only advanced inside -drivingDetectorFeed:, so when the
 * vehicle parks CoreLocation stops delivering fixes, the `stoppedDuration` timeout is never
 * evaluated and `stopped` / `tripEnd` never fire — the trip stays open forever. Android grew a
 * DrivingEventsDetector.tick(nowMs) for exactly this; this is its iOS counterpart.
 *
 * Deliberately its own timer and not the heartbeat: `heartbeatInterval` is opt-in and defaults to
 * off, so reusing it would leave the same hole this fixes.
 */
static NSTimeInterval const kDrivingTickIntervalSeconds = 60.0;

- (BOOL) drivingEventsEnabled
{
    NSDictionary *de = _config != nil ? _config.drivingEvents : nil;
    return [de isKindOfClass:[NSDictionary class]] && [[de objectForKey:@"enabled"] boolValue];
}

- (void) scheduleDrivingTick
{
    [self cancelDrivingTick];
    if (![self drivingEventsEnabled]) return;
    // NSTimer is not thread safe and must be scheduled on a thread with a live run loop.
    dispatch_async(dispatch_get_main_queue(), ^{
        drivingTickTimer = [NSTimer scheduledTimerWithTimeInterval:kDrivingTickIntervalSeconds
                                                            target:self
                                                          selector:@selector(onDrivingTick:)
                                                          userInfo:nil
                                                           repeats:YES];
    });
}

- (void) cancelDrivingTick
{
    // Same main-queue discipline as -cancelHeartbeat: -invalidate must run on the thread that
    // scheduled the timer, and every read/write of the ivar happens there.
    dispatch_async(dispatch_get_main_queue(), ^{
        if (drivingTickTimer != nil) {
            [drivingTickTimer invalidate];
            drivingTickTimer = nil;
        }
    });
}

- (void) onDrivingTick:(NSTimer *)timer
{
    if (!isStarted || ![self drivingEventsEnabled]) return;

    NSTimeInterval stoppedDuration = 60.0;
    NSDictionary *de = _config.drivingEvents;
    if ([de isKindOfClass:[NSDictionary class]] && [de objectForKey:@"stoppedDuration"]) {
        stoppedDuration = [[de objectForKey:@"stoppedDuration"] doubleValue] / 1000.0;
    }

    NSTimeInterval now = [[NSDate date] timeIntervalSince1970];
    if (!drIsMoving || drBelowMovingSince == 0) return;
    if ((now - drBelowMovingSince) < stoppedDuration) return;

    // Last fix we saw. May be nil (stopped before any location arrived); the notifications still
    // go out, and -bufferPendingEvent: keeps the event for the next fix if one shows up.
    MAURLocation *loc = lastReceivedLocation;

    drIsMoving = NO;
    if (loc != nil) [self attachDrivingEvent:@"stopped" to:loc extra:nil];
    [self bufferPendingEvent:@"stopped" extra:nil];
    NSMutableDictionary *stoppedInfo = [NSMutableDictionary dictionary];
    if (loc != nil) stoppedInfo[@"location"] = loc;
    [[NSNotificationCenter defaultCenter] postNotificationName:MAURStoppedNotification
                                                        object:self
                                                      userInfo:stoppedInfo];

    if (drTripActive) {
        // Duration measured to the moment the vehicle stopped moving, not to now — otherwise
        // every trip carries a tail as long as stoppedDuration. Matches Android's tick().
        NSTimeInterval durMs = (drBelowMovingSince - drTripStartedAt) * 1000.0;
        if (durMs < 0) durMs = 0;
        double dist = drTripDistanceMeters;
        drTripActive = NO;
        NSDictionary *extra = @{@"distance": @(dist), @"durationMs": @((long long)durMs)};
        if (loc != nil) [self attachDrivingEvent:@"tripEnd" to:loc extra:extra];
        [self bufferPendingEvent:@"tripEnd" extra:extra];
        NSMutableDictionary *tripInfo = [NSMutableDictionary dictionaryWithDictionary:extra];
        if (loc != nil) tripInfo[@"location"] = loc;
        [[NSNotificationCenter defaultCenter] postNotificationName:MAURTripEndNotification
                                                            object:self
                                                          userInfo:tripInfo];
        sensorFusion.tripActive = NO;
    }
}

#pragma mark - v4.2 Phase 8 sensor fusion

- (void) configureSensorFusion
{
    NSDictionary *de = _config.drivingEvents;
    BOOL want = [de isKindOfClass:[NSDictionary class]]
        && [de[@"enabled"] boolValue]
        && [de[@"sensorFusion"] boolValue];
    if (!want) {
        [sensorFusion stop];
        sensorFusion = nil;
        return;
    }
    if (sensorFusion == nil) {
        sensorFusion = [[MAURSensorFusionDetector alloc] init];
        sensorFusion.listener = self;
    }
    sensorFusion.enabled = YES;
    if (de[@"crashImpactG"])           sensorFusion.crashImpactG         = [de[@"crashImpactG"] doubleValue];
    if (de[@"sensorCrashCooldownMs"])  sensorFusion.crashCooldownMs      = [de[@"sensorCrashCooldownMs"] doubleValue];
    if (de[@"phoneUsageWindowMs"])     sensorFusion.phoneUsageWindowMs   = [de[@"phoneUsageWindowMs"] doubleValue];
    if (de[@"phoneUsageCooldownMs"])   sensorFusion.phoneUsageCooldownMs = [de[@"phoneUsageCooldownMs"] doubleValue];
    // v4.2 hot-reload: re-inject current trip state + last location so a config change
    // mid-trip starts the sensor pipeline in the correct mode.
    sensorFusion.tripActive   = drTripActive;
    sensorFusion.lastLocation = lastReceivedLocation;
}

// MAURSensorFusionListener
- (void) onSensorCrashWithImpactG:(double)impactG location:(MAURLocation *)location
{
    [self bufferPendingEvent:@"possibleCrash" extra:@{@"value": @(impactG), @"source": @"sensor"}];
    NSMutableDictionary *userInfo = [NSMutableDictionary dictionary];
    if (location != nil) userInfo[@"location"] = location;
    userInfo[@"value"] = @(impactG);
    userInfo[@"source"] = @"sensor";
    [[NSNotificationCenter defaultCenter] postNotificationName:MAURPossibleCrashNotification
                                                        object:self
                                                      userInfo:userInfo];
}
- (void) onPhoneUsageWhileDriving:(MAURLocation *)location
{
    [self bufferPendingEvent:@"phoneUsageWhileDriving" extra:nil];
    NSMutableDictionary *userInfo = [NSMutableDictionary dictionary];
    if (location != nil) userInfo[@"location"] = location;
    [[NSNotificationCenter defaultCenter] postNotificationName:MAURPhoneUsageWhileDrivingNotification
                                                        object:self
                                                      userInfo:userInfo];
}

// v4.3 — driving event helpers
- (void) attachDrivingEvent:(NSString *)type to:(MAURLocation *)loc extra:(NSDictionary *)extra
{
    if (loc == nil || type == nil) return;
    NSMutableDictionary *ev = [NSMutableDictionary dictionary];
    ev[@"type"] = type;
    ev[@"time"] = @((long long)([[NSDate date] timeIntervalSince1970] * 1000.0));
    if (extra != nil) [ev addEntriesFromDictionary:extra];
    if (loc.drivingEvents == nil) loc.drivingEvents = [NSMutableArray array];
    [loc.drivingEvents addObject:ev];
}

// v4.4.1 — pending driving events: cap + TTL.
static NSInteger      const kPendingDrivingEventsMax     = 20;
static NSTimeInterval const kPendingDrivingEventsTTLMs   = 60000.0;

- (void) bufferPendingEvent:(NSString *)type extra:(NSDictionary *)extra
{
    if (type == nil) return;
    NSMutableDictionary *ev = [NSMutableDictionary dictionary];
    ev[@"type"] = type;
    ev[@"time"] = @((long long)([[NSDate date] timeIntervalSince1970] * 1000.0));
    if (extra != nil) [ev addEntriesFromDictionary:extra];
    @synchronized (pendingDrivingEvents) {
        while ((NSInteger)[pendingDrivingEvents count] >= kPendingDrivingEventsMax) {
            [pendingDrivingEvents removeObjectAtIndex:0];
        }
        [pendingDrivingEvents addObject:ev];
    }
}

- (void) flushPendingDrivingEventsTo:(MAURLocation *)loc
{
    if (loc == nil) return;
    NSTimeInterval nowMs = [[NSDate date] timeIntervalSince1970] * 1000.0;
    @synchronized (pendingDrivingEvents) {
        if ([pendingDrivingEvents count] == 0) return;
        if (loc.drivingEvents == nil) loc.drivingEvents = [NSMutableArray array];
        for (NSDictionary *ev in pendingDrivingEvents) {
            NSNumber *t = ev[@"time"];
            NSTimeInterval evMs = t != nil ? [t doubleValue] : nowMs;
            if (nowMs - evMs <= kPendingDrivingEventsTTLMs) {
                [loc.drivingEvents addObject:ev];
            }
        }
        [pendingDrivingEvents removeAllObjects];
    }
}

// v4.4 — read device battery via UIDevice. Calling main thread; safe from any thread.
- (void) attachBatterySnapshotTo:(MAURLocation *)loc
{
    if (loc == nil) return;
    void (^read)(void) = ^{
        UIDevice *device = [UIDevice currentDevice];
        if (!device.batteryMonitoringEnabled) device.batteryMonitoringEnabled = YES;
        float lvl = device.batteryLevel; // 0.0 - 1.0, or -1 if unknown
        if (lvl >= 0) {
            loc.batteryLevel = @((int) round(lvl * 100.0));
        }
        UIDeviceBatteryState state = device.batteryState;
        BOOL charging = (state == UIDeviceBatteryStateCharging || state == UIDeviceBatteryStateFull);
        loc.isCharging = @(charging);
    };
    if ([NSThread isMainThread]) {
        read();
    } else {
        dispatch_sync(dispatch_get_main_queue(), read);
    }
}

- (void) drivingDetectorFeed:(MAURLocation *)loc
{
    if (loc == nil || _config == nil) return;
    BOOL enabled = NO;
    double speedLimit = 0;
    double minMovingSpeed = 1.0;
    NSTimeInterval stoppedDuration = 60.0;
    double minTripSpeed = 3.0;
    NSTimeInterval minTripDuration = 30.0;
    NSDictionary *de = [_config valueForKey:@"drivingEvents"]; // see MAURConfig: provided as NSDictionary
    if ([de isKindOfClass:[NSDictionary class]]) {
        enabled         = [[de objectForKey:@"enabled"] boolValue];
        speedLimit      = [[de objectForKey:@"speedLimit"] doubleValue];
        if ([de objectForKey:@"minMovingSpeed"])  minMovingSpeed  = [[de objectForKey:@"minMovingSpeed"] doubleValue];
        if ([de objectForKey:@"stoppedDuration"]) stoppedDuration = [[de objectForKey:@"stoppedDuration"] doubleValue] / 1000.0;
        if ([de objectForKey:@"minTripSpeed"])    minTripSpeed    = [[de objectForKey:@"minTripSpeed"] doubleValue];
        if ([de objectForKey:@"minTripDuration"]) minTripDuration = [[de objectForKey:@"minTripDuration"] doubleValue] / 1000.0;
    }
    if (!enabled) return;

    // v5.0 — F1: the state machine runs on the *fix* clock, never on wall-clock-at-processing.
    // CoreLocation delivers buffered fixes as a batch, so several are processed within the same
    // instant: dt collapsed toward 0 and dv/dt exploded into phantom hardBrake / possibleCrash
    // events. Mirrors DrivingEventsDetector.onLocation() on Android, which derives every delta
    // from loc.getTime(). The wall clock is only a fallback for a timestamp-less fix.
    NSTimeInterval now = (loc.time != nil) ? [loc.time timeIntervalSince1970] : 0;
    if (now <= 0) {
        now = [[NSDate date] timeIntervalSince1970];
    }
    // v5.0 — F1: per-event cooldowns live on a separate monotonic-ish clock (Android uses
    // SystemClock.elapsedRealtime()), so a batch of old fixes cannot rewind them and slip several
    // events through the anti-burst window, and an NTP step cannot park them in the future.
    NSTimeInterval mono = [NSProcessInfo processInfo].systemUptime;
    // v4.5.6 — F14: CoreLocation reports -1 for an unknown speed/course, so `!= nil` is never a
    // useful validity check on iOS. Track validity explicitly: an invalid speed must NOT feed the
    // acceleration / crash deltas, where -1 produced phantom hardBrake and possibleCrash events.
    // v5.0 — F2: nor the moving/stopped machine; see the bail-out below.
    BOOL hasSpeed = (loc.speed != nil && [loc.speed doubleValue] >= 0);
    double speed = hasSpeed ? [loc.speed doubleValue] : 0.0;
    // v4.5.6 — F14: same for the course (heading). -1 == no bearing available.
    BOOL hasHeading = (loc.heading != nil && [loc.heading doubleValue] >= 0);

    // Provider change
    NSString *provider = loc.provider;
    if (provider != nil && ![provider isEqualToString:drLastProvider]) {
        drLastProvider = provider;
        [self attachDrivingEvent:@"providerChange" to:loc extra:@{@"provider": provider}];
        [[NSNotificationCenter defaultCenter] postNotificationName:MAURProviderChangeNotification
                                                            object:self
                                                          userInfo:@{@"provider": provider}];
    }

    double curLat = [loc.latitude doubleValue];
    double curLon = [loc.longitude doubleValue];
    if (drHasPrev && drTripActive) {
        drTripDistanceMeters += [self drHaversineFromLat:drPrevLat lon:drPrevLon toLat:curLat lon:curLon];
    }
    drPrevLat = curLat;
    drPrevLon = curLon;
    drHasPrev = YES;

    // v4.1 GPS-derived sensor-like events
    // v5.0 — F2: parsed here (moved up from below the state machine) because the bearing / sharp
    // turn handling now runs before the speed bail-out.
    double hardBrakeMps2 = 3.5, rapidAccelMps2 = 3.5, sharpTurnDegPerSec = 30, crashImpactKmh = 25;
    NSTimeInterval crashWindow = 2.0;
    if ([de isKindOfClass:[NSDictionary class]]) {
        if ([de objectForKey:@"hardBrakeMps2"])      hardBrakeMps2      = [[de objectForKey:@"hardBrakeMps2"] doubleValue];
        if ([de objectForKey:@"rapidAccelMps2"])     rapidAccelMps2     = [[de objectForKey:@"rapidAccelMps2"] doubleValue];
        if ([de objectForKey:@"sharpTurnDegPerSec"]) sharpTurnDegPerSec = [[de objectForKey:@"sharpTurnDegPerSec"] doubleValue];
        if ([de objectForKey:@"crashImpactKmh"])     crashImpactKmh     = [[de objectForKey:@"crashImpactKmh"] doubleValue];
        if ([de objectForKey:@"crashWindowMs"])      crashWindow        = [[de objectForKey:@"crashWindowMs"] doubleValue] / 1000.0;
    }
    static const NSTimeInterval kCooldown = 4.0;
    // v5.0 — F1: minimum / maximum spacing between two fixes for a derivative (dv/dt,
    // dbearing/dt) to mean anything. Android: MIN_DELTA_MS / MAX_DELTA_MS.
    static const NSTimeInterval kMinDelta = 0.5;
    static const NSTimeInterval kMaxDelta = 5.0;

    // Sharp turn (bearing rate)
    // v5.0 — F2: kept above the speed bail-out, like Android, so a speed-less fix still refreshes
    // (or invalidates) the bearing anchor; emitting the event still requires a real speed reading.
    if (!hasHeading) {
        drHasPrevBearing = NO;
        drPrevBearingAt = 0;
    } else {
        double bearing = [loc.heading doubleValue];
        if (drHasPrevBearing) {
            NSTimeInterval dt = now - drPrevBearingAt;
            if (dt > kMaxDelta) {
                drHasPrevBearing = NO; // stale anchor; re-armed with this fix just below
            } else if (dt >= kMinDelta && sharpTurnDegPerSec > 0 && hasSpeed && speed >= 5.0) {
                double diff = fabs(bearing - drPrevBearing);
                if (diff > 180) diff = 360 - diff;
                double rate = diff / dt;
                if (rate >= sharpTurnDegPerSec && (mono - drLastSharpTurnAt) >= kCooldown) {
                    drLastSharpTurnAt = mono;
                    [self attachDrivingEvent:@"sharpTurn" to:loc extra:@{@"value": @(rate)}];
                    [[NSNotificationCenter defaultCenter] postNotificationName:MAURSharpTurnNotification
                                                                        object:self
                                                                      userInfo:@{@"location": loc, @"value": @(rate)}];
                }
            }
        }
        drPrevBearing = bearing;
        drPrevBearingAt = now;
        drHasPrevBearing = YES;
    }

    // v5.0 — F2: everything below is speed-derived. A fix without a usable speed (CoreLocation
    // reports -1 indoors, on the first fix and after a reacquisition) used to be clamped to 0 and
    // fed to the moving/stopped machine, which read it as "stopped" and could close an active
    // trip. Android bails out at exactly this point: `if (!hasSpeed) return;`. Provider change,
    // the distance accumulator and the bearing anchor above have already been updated.
    if (!hasSpeed) {
        return;
    }

    BOOL nowMoving = speed >= minMovingSpeed;
    if (nowMoving) {
        drBelowMovingSince = 0;
        if (!drIsMoving) {
            drIsMoving = YES;
            [self attachDrivingEvent:@"moving" to:loc extra:nil];
            [[NSNotificationCenter defaultCenter] postNotificationName:MAURMovingNotification
                                                                object:self
                                                              userInfo:@{@"location": loc}];
        }
        if (!drTripActive) {
            if (speed >= minTripSpeed) {
                if (drAboveTripSpeedSince == 0) drAboveTripSpeedSince = now;
                if (now - drAboveTripSpeedSince >= minTripDuration) {
                    drTripActive = YES;
                    drTripStartedAt = now;
                    drTripDistanceMeters = 0;
                    [self attachDrivingEvent:@"tripStart" to:loc extra:nil];
                    [[NSNotificationCenter defaultCenter] postNotificationName:MAURTripStartNotification
                                                                        object:self
                                                                      userInfo:@{@"location": loc}];
                    sensorFusion.tripActive = YES;
                }
            } else {
                drAboveTripSpeedSince = 0;
            }
        }
    } else {
        drAboveTripSpeedSince = 0;
        if (drBelowMovingSince == 0) drBelowMovingSince = now;
        if (drIsMoving && (now - drBelowMovingSince) >= stoppedDuration) {
            drIsMoving = NO;
            [self attachDrivingEvent:@"stopped" to:loc extra:nil];
            [[NSNotificationCenter defaultCenter] postNotificationName:MAURStoppedNotification
                                                                object:self
                                                              userInfo:@{@"location": loc}];
            if (drTripActive) {
                NSTimeInterval durMs = (now - drTripStartedAt) * 1000.0;
                double dist = drTripDistanceMeters;
                drTripActive = NO;
                [self attachDrivingEvent:@"tripEnd" to:loc extra:@{@"distance": @(dist), @"durationMs": @((long long)durMs)}];
                [[NSNotificationCenter defaultCenter] postNotificationName:MAURTripEndNotification
                                                                    object:self
                                                                  userInfo:@{
                                                                      @"location": loc,
                                                                      @"distance": @(dist),
                                                                      @"durationMs": @((long long)durMs)
                                                                  }];
                sensorFusion.tripActive = NO;
            }
        }
    }

    if (speedLimit > 0) {
        double kmh = speed * 3.6;
        if (kmh > speedLimit) {
            if (!drWasSpeeding) {
                drWasSpeeding = YES;
                [self attachDrivingEvent:@"speeding" to:loc extra:@{@"speedKmh": @(kmh), @"limitKmh": @(speedLimit)}];
                [[NSNotificationCenter defaultCenter] postNotificationName:MAURSpeedingNotification
                                                                    object:self
                                                                  userInfo:@{
                                                                      @"location": loc,
                                                                      @"speedKmh": @(kmh),
                                                                      @"limitKmh": @(speedLimit)
                                                                  }];
            }
        } else {
            drWasSpeeding = NO;
        }
    }

    if (drTripActive && drPrevSpeedAt > 0) {
        NSTimeInterval dt = now - drPrevSpeedAt;
        // v5.0 — F1: below kMinDelta the GPS speed quantisation noise dominates and batched or
        // duplicated fixes produce absurd accelerations; above kMaxDelta the two fixes are
        // unrelated. Android: MIN_DELTA_MS / MAX_DELTA_MS.
        if (dt >= kMinDelta && dt <= kMaxDelta) {
            double dv = speed - drPrevSpeed;
            double accel = dv / dt;
            if (hardBrakeMps2 > 0 && accel <= -hardBrakeMps2 && (mono - drLastHardBrakeAt) >= kCooldown) {
                drLastHardBrakeAt = mono;
                [self attachDrivingEvent:@"hardBrake" to:loc extra:@{@"value": @(accel)}];
                [[NSNotificationCenter defaultCenter] postNotificationName:MAURHardBrakeNotification
                                                                    object:self
                                                                  userInfo:@{@"location": loc, @"value": @(accel)}];
            }
            if (rapidAccelMps2 > 0 && accel >= rapidAccelMps2 && (mono - drLastRapidAccelAt) >= kCooldown) {
                drLastRapidAccelAt = mono;
                [self attachDrivingEvent:@"rapidAcceleration" to:loc extra:@{@"value": @(accel)}];
                [[NSNotificationCenter defaultCenter] postNotificationName:MAURRapidAccelerationNotification
                                                                    object:self
                                                                  userInfo:@{@"location": loc, @"value": @(accel)}];
            }
            if (crashImpactKmh > 0 && dt <= crashWindow) {
                double dropKmh = (drPrevSpeed - speed) * 3.6;
                if (dropKmh >= crashImpactKmh
                    && speed < 1.5
                    && drPrevSpeed * 3.6 >= crashImpactKmh
                    && (mono - drLastCrashAt) >= kCooldown) {
                    drLastCrashAt = mono;
                    [self attachDrivingEvent:@"possibleCrash" to:loc extra:@{@"value": @(dropKmh), @"source": @"gps"}];
                    [[NSNotificationCenter defaultCenter] postNotificationName:MAURPossibleCrashNotification
                                                                        object:self
                                                                      userInfo:@{@"location": loc, @"value": @(dropKmh), @"source": @"gps"}];
                }
            }
        }
    }

    // v4.5.6 — F14: only a valid speed may become the baseline for the next delta. An invalid
    // (-1) reading used to reset the baseline to 0 and fake a huge acceleration on the next fix.
    // v5.0 — F2: guaranteed by the bail-out above, kept for clarity.
    if (hasSpeed) {
        drPrevSpeed = speed;
        drPrevSpeedAt = now;
    }
}

- (double) drHaversineFromLat:(double)lat1 lon:(double)lon1 toLat:(double)lat2 lon:(double)lon2
{
    const double R = 6371000.0;
    double dLat = (lat2 - lat1) * M_PI / 180.0;
    double dLon = (lon2 - lon1) * M_PI / 180.0;
    double a = sin(dLat/2) * sin(dLat/2)
             + cos(lat1 * M_PI / 180.0) * cos(lat2 * M_PI / 180.0)
             * sin(dLon/2) * sin(dLon/2);
    return 2 * R * asin(sqrt(a));
}

- (void) triggerSOS:(NSDictionary *)payload
{
    NSMutableDictionary *userInfo = [NSMutableDictionary dictionary];
    if (lastReceivedLocation != nil) userInfo[@"location"] = lastReceivedLocation;
    userInfo[@"payload"] = payload != nil ? payload : @{};
    [[NSNotificationCenter defaultCenter] postNotificationName:MAURSOSNotification
                                                        object:self
                                                      userInfo:userInfo];
}

/**
 * toggle between foreground and background operation mode
 */
- (void) switchMode:(MAUROperationalMode)mode
{
    DDLogInfo(@"%@ #switchMode %lu", TAG, (unsigned long)mode);
    
    operationMode = mode;
    
    if (!isStarted) return;
    
    if ([self getConfig].isDebugging) {
        AudioServicesPlaySystemSound (operationMode  == MAURForegroundMode ? paceChangeYesSound : paceChangeNoSound);
    }
    
    [self runOnMainThread:^{
        [locationProvider onSwitchMode:mode];
    }];
}

- (BOOL) locationServicesEnabled
{
    if ([CLLocationManager respondsToSelector:@selector(locationServicesEnabled)]) { // iOS 4.x
        return [CLLocationManager locationServicesEnabled];
    }
    
    return NO;
}

- (MAURLocationAuthorizationStatus) authorizationStatus
{
    // v4.5.6 — D21: +[CLLocationManager authorizationStatus] is deprecated since iOS 14.
    // Read the instance property of the shared manager instead (and do it on the main thread,
    // where CLLocationManager expects to be touched).
    __block CLAuthorizationStatus authStatus = kCLAuthorizationStatusNotDetermined;
    [self runOnMainThread:^{
        if (@available(iOS 14.0, *)) {
            authStatus = [MAURLocationManager sharedInstance].locationManager.authorizationStatus;
        } else {
            authStatus = [CLLocationManager authorizationStatus];
        }
    }];

    switch (authStatus) {
        case kCLAuthorizationStatusNotDetermined:
            return MAURLocationAuthorizationNotDetermined;
        case kCLAuthorizationStatusRestricted:
        case kCLAuthorizationStatusDenied:
            return MAURLocationAuthorizationDenied;
        case kCLAuthorizationStatusAuthorizedAlways:
            return MAURLocationAuthorizationAlways;
        case kCLAuthorizationStatusAuthorizedWhenInUse:
            return MAURLocationAuthorizationForeground;
    }

    return MAURLocationAuthorizationNotDetermined;
}

- (BOOL) isStarted
{
    return isStarted;
}

- (MAURAbstractLocationProvider<MAURLocationProvider>*) getProvider:(int)providerId error:(NSError * __autoreleasing *)outError
{
    NSDictionary *errorDictionary;
    MAURAbstractLocationProvider<MAURLocationProvider> *locationProvider = nil;
    switch (providerId) {
        case DISTANCE_FILTER_PROVIDER:
            locationProvider = [[MAURDistanceFilterLocationProvider alloc] init];
            break;
        case ACTIVITY_PROVIDER:
            locationProvider = [[MAURActivityLocationProvider alloc] init];
            break;
        case RAW_PROVIDER:
            locationProvider = [[MAURRawLocationProvider alloc] init];
            break;
        default:
            if (outError != nil) {
                errorDictionary = @{
                                    NSLocalizedDescriptionKey: NSLocalizedString(@UNKNOWN_LOCATION_PROVIDER_MSG, nil),
                                    };
                *outError = [NSError errorWithDomain:BGGeolocationDomain code:MAURBGConfigureError userInfo:errorDictionary];
            }
            return nil;
    }
    [locationProvider onCreate];
    return locationProvider;
}

- (void) showAppSettings
{
    [self runOnMainThread:^{
        BOOL canGoToSettings = (UIApplicationOpenSettingsURLString != NULL);
        if (canGoToSettings) {
            NSURL *settingsURL = [NSURL URLWithString:UIApplicationOpenSettingsURLString];
            [[UIApplication sharedApplication] openURL:settingsURL 
                                               options:@{} 
                                     completionHandler:nil];
        }
    }];
}

- (void) showLocationSettings
{
    // NOOP - Since Apple started rejecting apps using non public url schemes
    // https://github.com/mauron85/cordova-plugin-background-geolocation/issues/394
}

- (MAURLocation*) getStationaryLocation
{
    return stationaryLocation;
}

- (NSArray<MAURLocation*>*) getLocations
{
    MAURSQLiteLocationDAO* locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    return [locationDAO getAllLocations];
}

- (NSArray<MAURLocation*>*) getValidLocations
{
    MAURSQLiteLocationDAO* locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    return [locationDAO getValidLocations];
}

- (NSArray<MAURLocation*>*) getValidLocationsAndDelete
{
    // v5.0 — D5: this used to call getLocationsForSync, which only moves rows
    // PostPending -> SyncPending. getPendingSyncCount() dropped to 0, the caller believed the
    // queue was drained, and 15 min later the stale-sync recovery resurrected the very same rows
    // -> duplicates on the server. Android's getValidLocationsAndDelete really deletes; this now
    // matches it: SELECT + mark Deleted in one transaction, rolled back if the UPDATE fails.
    MAURSQLiteLocationDAO* locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    return [locationDAO getValidLocationsAndDelete];
}

- (void) startSession
{
    [[MAURSessionLocationDAO sharedInstance] startSession];
}

- (NSArray<MAURLocation*>*) getSessionLocations
{
    return [[MAURSessionLocationDAO sharedInstance] getSessionLocations];
}

- (void) clearSession
{
    [[MAURSessionLocationDAO sharedInstance] clearSession];
}

- (NSInteger) getSessionLocationsCount
{
    return [[MAURSessionLocationDAO sharedInstance] getSessionLocationsCount];
}

- (BOOL) deleteLocation:(NSNumber*)locationId error:(NSError * __autoreleasing *)outError
{
    MAURSQLiteLocationDAO* locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    return [locationDAO deleteLocation:locationId error:outError];
}

- (BOOL) deleteAllLocations:(NSError * __autoreleasing *)outError
{
    MAURSQLiteLocationDAO* locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    return [locationDAO deleteAllLocations:outError];
}

- (MAURLocation*)getCurrentLocation:(int)timeout maximumAge:(long)maximumAge
                 enableHighAccuracy:(BOOL)enableHighAccuracy
                              error:(NSError * __autoreleasing *)outError
{
    __block NSError *error = nil;
    __block CLLocation *location = nil;
    
    dispatch_semaphore_t sema = dispatch_semaphore_create(0);
    [self runOnMainThread:^{
        CLLocation *currentLocation = [MAURLocationManager sharedInstance].locationManager.location;
        if (currentLocation != nil) {
            long locationAge = ceil(fabs([currentLocation.timestamp timeIntervalSinceNow]) * 1000);
            if (locationAge <= maximumAge) {
                location = currentLocation;
                dispatch_semaphore_signal(sema);
                return;
            }
        }

        INTULocationManager *locationManager = [INTULocationManager sharedInstance];
        float timeoutInSeconds = ceil((float)timeout/1000);
        [locationManager requestLocationWithDesiredAccuracy:enableHighAccuracy ? INTULocationAccuracyRoom : INTULocationAccuracyCity
                                                    timeout:timeoutInSeconds
                                       delayUntilAuthorized:YES    // This parameter is optional, defaults to NO if omitted
                                                      block:^(CLLocation *currentLocation, INTULocationAccuracy achievedAccuracy, INTULocationStatus status) {
                                                          if (status == INTULocationStatusSuccess) {
                                                              // Request succeeded, meaning achievedAccuracy is at least the requested accuracy, and
                                                              // currentLocation contains the device's current location.
                                                              location = currentLocation;
                                                          }
                                                          else if (status == INTULocationStatusTimedOut) {
                                                              // Wasn't able to locate the user with the requested accuracy within the timeout interval.
                                                              // However, currentLocation contains the best location available (if any) as of right now,
                                                              // and achievedAccuracy has info on the accuracy/recency of the location in currentLocation.
                                                              error = [NSError errorWithDomain:BGGeolocationDomain code:TIMEOUT userInfo:nil];
                                                          }
                                                          else {
                                                              // An error occurred, more info is available by looking at the specific status returned.
                                                              error = [NSError errorWithDomain:BGGeolocationDomain code:POSITION_UNAVAILABLE userInfo:nil];
                                                          }
                                                          dispatch_semaphore_signal(sema);
                                                      }];
    }];

    // v4.5.6 — D11: never wait forever. If the INTULocationManager block is never invoked
    // (request lost, authorization dialog left open, ...) the caller's thread used to be
    // parked for the whole lifetime of the process. Derive the deadline from the requested
    // timeout, add a small grace period so INTU can report its own timeout first, and cap
    // the whole thing at 60s.
    int effectiveTimeout = (timeout > 0) ? timeout : 30000;
    double waitSeconds = ceil((double)effectiveTimeout / 1000.0) + 5.0;
    if (waitSeconds > 60.0) {
        waitSeconds = 60.0;
    }
    dispatch_time_t deadline = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(waitSeconds * NSEC_PER_SEC));
    if (dispatch_semaphore_wait(sema, deadline) != 0) {
        DDLogWarn(@"%@ getCurrentLocation timed out after %.0fs waiting for a fix", TAG, waitSeconds);
        if (outError != nil) {
            *outError = [NSError errorWithDomain:BGGeolocationDomain code:TIMEOUT userInfo:nil];
        }
        return nil;
    }

    if (location != nil) {
        MAURLocation *bgloc = [MAURLocation fromCLLocation:location];
        // v4.5.6 — D6: keep locationProvider populated on one-shot fixes too.
        bgloc.locationProvider = [self getConfig].locationProvider;
        return bgloc;
    }

    if (outError != nil) {
        *outError = error;
    }

    return nil;
}

- (MAURConfig*) getConfig
{
    if (_config == nil) {
        MAURSQLiteConfigurationDAO* configDAO = [MAURSQLiteConfigurationDAO sharedInstance];
        _config = [configDAO retrieveConfiguration];
        if (_config == nil) {
            _config = [[MAURConfig alloc] initWithDefaults];
        }
    }
    
    return _config;
}

- (NSArray*) getLogEntries:(NSInteger)limit
{
    MAURLogReader *logReader = [[MAURLogReader alloc] initWithLogDirectory:[self loggerDirectory]];
    return [logReader getEntries:limit fromLogEntryId:0 minLogLevel:DDLogFlagDebug];
}

- (NSArray*) getLogEntries:(NSInteger)limit fromLogEntryId:(NSInteger)entryId minLogLevelFromString:(NSString *)minLogLevel
{
    MAURLogReader *logReader = [[MAURLogReader alloc] initWithLogDirectory:[self loggerDirectory]];
    return [logReader getLogEntries:limit fromLogEntryId:entryId minLogLevelAsString:minLogLevel];
}

- (NSArray*) getLogEntries:(NSInteger)limit fromLogEntryId:(NSInteger)entryId minLogLevel:(DDLogFlag)minLogLevel
{
    MAURLogReader *logReader = [[MAURLogReader alloc] initWithLogDirectory:[self loggerDirectory]];
    NSArray *logs = [logReader getEntries:limit fromLogEntryId:entryId minLogLevel:minLogLevel];
    return logs;
}

- (void) forceSync
{
    if (![_config syncEnabled]) {
        DDLogDebug(@"Sync disabled in config, skipping forceSync");
        return;
    }
    [postLocationTask sync];
}

- (void) clearSync
{
    MAURSQLiteLocationDAO *locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    NSError *error = nil;
    if ([locationDAO deletePendingSyncLocations:&error]) {
        DDLogDebug(@"Cleared pending sync locations");
    } else {
        DDLogError(@"clearSync failed: %@", error.localizedDescription);
    }
}

- (NSInteger) getPendingSyncCount
{
    MAURSQLiteLocationDAO *locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    NSNumber *count = [locationDAO getLocationsForSyncCount];
    return count != nil ? [count integerValue] : 0;
}

// v4.5.6 — D25: UILocalNotification / -scheduleLocalNotification: are deprecated since iOS 10.
// Only reachable with `debug: true`. Uses UNUserNotificationCenter where available and keeps
// the legacy path as a fallback for iOS 9.
- (void) notify:(NSString*)message
{
    if (message == nil) {
        return;
    }

    if (@available(iOS 10, *)) {
        UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
        content.body = message;
        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:[[NSUUID UUID] UUIDString]
                                                                             content:content
                                                                             trigger:nil];
        [[UNUserNotificationCenter currentNotificationCenter] addNotificationRequest:request
                                                              withCompletionHandler:^(NSError * _Nullable error) {
            if (error != nil) {
                DDLogWarn(@"%@ debug notification failed: %@", TAG, error.localizedDescription);
            }
        }];
        return;
    }

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    localNotification.fireDate = [NSDate date];
    localNotification.alertBody = message;
    [[UIApplication sharedApplication] scheduleLocalNotification:localNotification];
#pragma clang diagnostic pop
}

-(void) runOnMainThread:(dispatch_block_t)completionHandle {
    BOOL alreadyOnMainThread = [NSThread isMainThread];
    // this check avoids possible deadlock resulting from
    // calling dispatch_sync() on the same queue as current one
    if (alreadyOnMainThread) {
        // execute code in place
        completionHandle();
    } else {
        // dispatch to main queue
        dispatch_sync(dispatch_get_main_queue(), completionHandle);
    }
}

- (NSString *)loggerDirectory
{
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES);
    NSString *basePath = ([paths count] > 0) ? [paths objectAtIndex:0] : NSTemporaryDirectory();
    
    return [basePath stringByAppendingPathComponent:@"SQLiteLogger"];
}

- (void) onStationaryChanged:(MAURLocation *)location
{
    DDLogDebug(@"%@ #onStationaryChanged", TAG);

    // v4.5.4: drop stationary fixes whose accuracy is worse than the configured
    // maxAcceptedAccuracy threshold. Mirrors the Android filter in
    // AbstractLocationProvider.handleStationary.
    NSNumber *maxAcc = [self getConfig].maxAcceptedAccuracy;
    if (maxAcc != nil && maxAcc.doubleValue > 0 && location.accuracy != nil
            && [location.accuracy doubleValue] > maxAcc.doubleValue) {
        DDLogDebug(@"%@ dropping stationary fix accuracy=%@ exceeds maxAcceptedAccuracy=%@", TAG, location.accuracy, maxAcc);
        return;
    }

    stationaryLocation = location;

    // v4.5.1 — enrichment moved into MAURPostLocationTask.add: so pending events / battery
    // land on the post-transform instance (and survive transforms that return new instances).

    [postLocationTask add:location];
    
    MAURConfig *config = [self getConfig];
    if ([config isDebugging]) {
        double distanceFilter = [MAURLocationManager sharedInstance].distanceFilter;
        [self notify:[NSString stringWithFormat:@"Stationary update: %s\nSPD: %0.0f | DF: %f | ACY: %0.0f | RAD: %0.0f",
                      ((operationMode == MAURForegroundMode) ? "FG" : "BG"),
                      [location.speed doubleValue],
                      distanceFilter,
                      [location.accuracy doubleValue],
                      [location.radius doubleValue]
                      ]];
        
        AudioServicesPlaySystemSound (locationSyncSound);
    }
    
    // Any javascript stationaryRegion event-listeners?
    if (self.delegate && [self.delegate respondsToSelector:@selector(onStationaryChanged:)]) {
        [self.delegate onStationaryChanged:location];
    }
}

- (void) onLocationChanged:(MAURLocation *)location
{
    DDLogDebug(@"%@ #onLocationChanged %@", TAG, location);

    // v4.5.4: drop fixes whose accuracy is worse than maxAcceptedAccuracy.
    // Mirrors AbstractLocationProvider.handleLocation on Android.
    NSNumber *maxAcc = [self getConfig].maxAcceptedAccuracy;
    if (maxAcc != nil && maxAcc.doubleValue > 0 && location.accuracy != nil
            && [location.accuracy doubleValue] > maxAcc.doubleValue) {
        DDLogDebug(@"%@ dropping fix accuracy=%@ exceeds maxAcceptedAccuracy=%@", TAG, location.accuracy, maxAcc);
        return;
    }

    stationaryLocation = nil;
    lastReceivedLocation = location; // v3.5 Phase 4: cached for heartbeat payload

    // v4.0 Phase 6: feed driver-insights state machine. Listener may attach events to `location`.
    [self drivingDetectorFeed:location];
    // v4.2 Phase 8: keep sensor pipeline aware of the latest fix.
    sensorFusion.lastLocation = location;
    // v4.5.1 — pending events drain + battery snapshot moved into MAURPostLocationTask.add:
    // so they run AFTER any user-supplied locationTransform. The previous order lost them
    // whenever the transform returned nil.

    [postLocationTask add:location];
    
    MAURConfig *config = [self getConfig];
    if ([config isDebugging]) {
        double distanceFilter = [MAURLocationManager sharedInstance].distanceFilter;
        [self notify:[NSString stringWithFormat:@"Location update: %s\nSPD: %0.0f | DF: %f | ACY: %0.0f",
                      ((operationMode == MAURForegroundMode) ? "FG" : "BG"),
                      [location.speed doubleValue],
                      distanceFilter,
                      [location.accuracy doubleValue]
                      ]];
        
        AudioServicesPlaySystemSound (locationSyncSound);
    }
    
    // Delegate to main module
    if (self.delegate && [self.delegate respondsToSelector:@selector(onLocationChanged:)]) {
        [self.delegate onLocationChanged:location];
    }
}

- (void) onAuthorizationChanged:(MAURLocationAuthorizationStatus)authStatus
{
    // v5.0 — D3: on iOS 14+ this also fires when the user flips "Precise Location", and it is the
    // first moment the accuracy granted to a fresh install is known. Only while tracking, so the
    // temporary-full-accuracy prompt never appears out of the blue.
    if (isStarted && [NSThread isMainThread]
        && [[MAURLocationManager sharedInstance] requestTemporaryFullAccuracyIfReduced]) {
        [self onError:[MAURLocationManager reducedAccuracyError]];
    }
    [self.delegate onAuthorizationChanged:authStatus];
}

- (void) onError:(NSError*)error
{
    [self.delegate onError:error];
}

- (void) onLocationPause
{
    [self.delegate onLocationPause];
}

- (void) onLocationResume
{
    [self.delegate onLocationResume];
}

- (void) onActivityChanged:(MAURActivity *)activity
{
    DDLogDebug(@"%@ #onActivityChanged %@", TAG, activity);
    
    if ([self getConfig].isDebugging) {
        [self notify:[NSString stringWithFormat:@"%@ activity detected: %@ activity, confidence: %@", TAG, activity.type, activity.confidence]];
    }
    
    [self.delegate onActivityChanged:activity];
}

/**@
 * If you don't stopMonitoring when application terminates, the app will be awoken still when a
 * new location arrives, essentially monitoring the user's location even when they've killed the app.
 * Might be desirable in certain apps.
 */
- (void) onAppTerminate
{
    MAURConfig *config = [self getConfig];
    if ([config stopOnTerminate]) {
        DDLogInfo(@"%@ #onAppTerminate.", TAG);
        [self stop:nil];
    } else {
        [locationProvider onTerminate];
    }
}

- (void) dealloc
{
    DDLogDebug(@"%@ #dealloc", TAG);
    // currently noop
}

#pragma mark - Location transform

+ (void) setLocationTransform:(MAURLocationTransform _Nullable)transform
{
    [MAURPostLocationTask setLocationTransform:transform];
}

+ (MAURLocationTransform _Nullable) locationTransform
{
    return [MAURPostLocationTask locationTransform];
}

#pragma mark - MAURPostLocationTaskDelegate

- (void) postLocationTaskRequestedAbortUpdates:(MAURPostLocationTask *)task
{
    if (_delegate && [_delegate respondsToSelector:@selector(onAbortRequested)])
    {
        // We have a delegate, tell it that there's a request.
        // It will decide whether to stop or not.
        [_delegate onAbortRequested];
    }
    else
    {
        // No delegate, we may be running in the background.
        // Let's just stop.
        [self stop:nil];
    }
}

- (void) postLocationTaskHttpAuthorizationUpdates:(MAURPostLocationTask *)task
{
    if (_delegate && [_delegate respondsToSelector:@selector(onHttpAuthorization)])
    {
        [_delegate onHttpAuthorization];
    }
}

@end
