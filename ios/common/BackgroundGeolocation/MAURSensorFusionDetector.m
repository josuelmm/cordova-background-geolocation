//
//  MAURSensorFusionDetector.m
//  BackgroundGeolocation
//
//  v4.2 Phase 8 — sensor fusion detector implementation.
//

#import "MAURSensorFusionDetector.h"
// v5.0.3 — -setLastLocation: lee loc.speed para corroborar el impacto; el header solo tiene un
// @class MAURLocation, asi que aqui hace falta la interfaz completa.
#import "MAURLocation.h"
#import <CoreMotion/CoreMotion.h>
#import <UIKit/UIKit.h>

static const double kJitterGyroRadS  = 0.7;   // ~40 deg/s
/**
 * v5.0.3 — 2.0 m/s², antes 0.5. Paridad con SensorFusionDetector.JITTER_ACCEL_MPS2 en Android,
 * corregido alli en v5.0 y no portado a iOS. 0.5 m/s² queda POR DEBAJO de la aceleracion de un
 * coche saliendo de un semaforo (~1-2 m/s²), asi que combinado con el OR contra el giroscopo
 * `phoneUsageWhileDriving` se disparaba una vez por cooldown durante todo el trayecto sin que
 * nadie tocara el telefono.
 */
static const double kJitterAccelMps2 = 2.0;
/** Velocidad por debajo de la cual se considera corroborado un impacto (vehiculo detenido). */
static const double kCrashCorroborationSpeedMps = 3.0;
/** O bien: una caida de velocidad de al menos esto dentro de la ventana de corroboracion. */
static const double kCrashCorroborationDropMps  = 5.0;
static const NSTimeInterval kCrashCorroborationWindowMs = 15000.0;

@interface MAURSensorFusionDetector ()
@property (nonatomic, strong) CMMotionManager *motion;
@property (nonatomic, strong) NSOperationQueue *queue;
@property (nonatomic, assign) BOOL started;
@property (nonatomic, assign) NSTimeInterval lastCrashAt;
@property (nonatomic, assign) NSTimeInterval lastPhoneUsageAt;
@property (nonatomic, assign) NSTimeInterval jitterAboveSince;
// v4.5.5 — cached foreground-active flag. -isScreenOnApprox used to dispatch_sync onto the
// main queue at 50 Hz (once per device-motion sample), stalling the sensor queue and the main
// thread. The flag is `atomic` so the sensor queue can read it without any dispatch.
@property (atomic, assign) BOOL appIsActive;
@property (nonatomic, assign) BOOL appStateObserversRegistered;
// v5.0.3 — corroboracion GPS del impacto, como Android. Un telefono que se cae del soporte
// supera 3 g sin problema, asi que un pico del acelerometro por si solo no es un choque.
@property (atomic, assign) double lastSpeedMps;        // -1 = desconocida
@property (atomic, assign) double recentPeakSpeedMps;  // -1 = sin pico
@property (atomic, assign) NSTimeInterval recentPeakAtMs;
// v5.0.3 — el muestreo de CoreMotion solo esta activo durante un viaje (ver -setTripActive:).
@property (nonatomic, assign) BOOL sampling;
- (void)startSampling;
- (void)stopSampling;
- (BOOL)gpsCorroboratesImpact:(NSTimeInterval)nowMs;
@end

@implementation MAURSensorFusionDetector

- (instancetype)init {
    if ((self = [super init])) {
        _motion = [[CMMotionManager alloc] init];
        _motion.deviceMotionUpdateInterval = 1.0 / 50.0; // 50 Hz
        _queue = [[NSOperationQueue alloc] init];
        _queue.name = @"MAURSensorFusionQueue";
        _queue.maxConcurrentOperationCount = 1;
        _enabled = NO;
        _crashImpactG = 3.0;
        _crashCooldownMs = 10000;
        _phoneUsageWindowMs = 4000;
        _phoneUsageCooldownMs = 60000;
        _started = NO;
        _lastCrashAt = 0;
        _lastPhoneUsageAt = 0;
        _jitterAboveSince = 0;
        _appIsActive = NO;
        _appStateObserversRegistered = NO;
        _lastSpeedMps = -1;
        _recentPeakSpeedMps = -1;
        _recentPeakAtMs = 0;
        _sampling = NO;
    }
    return self;
}

- (BOOL)isAvailable {
    return self.motion.isDeviceMotionAvailable;
}

#pragma mark - Application state cache

- (void)registerAppStateObservers {
    if (self.appStateObserversRegistered) return;
    self.appStateObserversRegistered = YES;

    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    // -addObserver:selector:name:object: does not retain the observer, so no retain cycle.
    [nc addObserver:self
           selector:@selector(onAppDidBecomeActive:)
               name:UIApplicationDidBecomeActiveNotification
             object:nil];
    [nc addObserver:self
           selector:@selector(onAppWillResignActive:)
               name:UIApplicationWillResignActiveNotification
             object:nil];

    // Seed the cache with the current state. Read on the main thread, but never with
    // dispatch_sync — -start may itself be called from the main thread.
    if ([NSThread isMainThread]) {
        self.appIsActive = ([UIApplication sharedApplication].applicationState == UIApplicationStateActive);
    } else {
        __weak typeof(self) weakSelf = self;
        dispatch_async(dispatch_get_main_queue(), ^{
            weakSelf.appIsActive = ([UIApplication sharedApplication].applicationState == UIApplicationStateActive);
        });
    }
}

- (void)unregisterAppStateObservers {
    if (!self.appStateObserversRegistered) return;
    self.appStateObserversRegistered = NO;

    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc removeObserver:self name:UIApplicationDidBecomeActiveNotification object:nil];
    [nc removeObserver:self name:UIApplicationWillResignActiveNotification object:nil];
}

- (void)onAppDidBecomeActive:(NSNotification *)notification {
    self.appIsActive = YES;
}

- (void)onAppWillResignActive:(NSNotification *)notification {
    self.appIsActive = NO;
}

#pragma mark - Lifecycle

- (void)start {
    @synchronized (self) {
        if (self.started || !self.enabled) return;
        if (![self.motion isDeviceMotionAvailable]) return;
        [self registerAppStateObservers];
        self.started = YES;
        // v5.0.3 — el muestreo NO arranca aqui. Todo lo que emite esta clase esta condicionado a
        // `tripActive`, y CoreMotion a 50 Hz con la app en background es de lo mas caro que puede
        // hacer el plugin. Aparcado no cuesta nada, igual que Android desde v5.0 (A14).
        if (self.tripActive) [self startSampling];
    }
}

- (void)stop {
    @synchronized (self) {
        [self unregisterAppStateObservers];
        if (!self.started) return;
        [self stopSampling];
        self.started = NO;
        self.jitterAboveSince = 0;
    }
}

- (void)setTripActive:(BOOL)tripActive {
    @synchronized (self) {
        _tripActive = tripActive;
        if (!tripActive) {
            self.jitterAboveSince = 0;
            [self stopSampling];
        } else if (self.started) {
            [self startSampling];
        }
    }
}

- (void)setLastLocation:(MAURLocation *)lastLocation {
    // Asignacion directa, sin @synchronized: replica exactamente lo que hacia el setter
    // sintetizado (`nonatomic, strong`) para no cambiar la semantica de concurrencia existente.
    _lastLocation = lastLocation;
    // v5.0.3 — mantiene el pico reciente de velocidad para -gpsCorroboratesImpact:.
    // Espejo de SensorFusionDetector.setLastLocation() en Android.
    if (lastLocation == nil || lastLocation.speed == nil) return;
    double s = [lastLocation.speed doubleValue];
    if (s < 0) return;
    NSTimeInterval nowMs = [[NSDate date] timeIntervalSince1970] * 1000.0;
    if (self.recentPeakSpeedMps < 0
            || s >= self.recentPeakSpeedMps
            || (nowMs - self.recentPeakAtMs) > kCrashCorroborationWindowMs) {
        self.recentPeakSpeedMps = s;
        self.recentPeakAtMs = nowMs;
    }
    self.lastSpeedMps = s;
}

/** Llamar siempre bajo @synchronized (self). */
- (void)startSampling {
    if (self.sampling || !self.enabled) return;
    if (![self.motion isDeviceMotionAvailable]) return;
    __weak typeof(self) weakSelf = self;
    [self.motion startDeviceMotionUpdatesToQueue:self.queue
                                      withHandler:^(CMDeviceMotion * _Nullable motion, NSError * _Nullable error) {
        if (!motion || error) return;
        [weakSelf processMotion:motion];
    }];
    self.sampling = YES;
}

/** Llamar siempre bajo @synchronized (self). */
- (void)stopSampling {
    if (!self.sampling) return;
    [self.motion stopDeviceMotionUpdates];
    self.sampling = NO;
}

/**
 * v5.0.3 — un pico de 3 g por si solo no es un choque: un telefono que se cae del soporte lo
 * supera. Se exige que el GPS lo corrobore: o el vehiculo esta practicamente parado ahora, o
 * perdio mucha velocidad dentro de la ventana. Sin lectura de velocidad se emite igualmente
 * (parking subterraneo, tunel): un falso positivo molesta, un choque no notificado no.
 * Espejo de SensorFusionDetector.gpsCorroboratesImpact() en Android.
 */
- (BOOL)gpsCorroboratesImpact:(NSTimeInterval)nowMs {
    double speed = self.lastSpeedMps;
    if (speed < 0) return YES;
    if (speed <= kCrashCorroborationSpeedMps) return YES;
    double peak = self.recentPeakSpeedMps;
    return peak >= 0
        && (nowMs - self.recentPeakAtMs) <= kCrashCorroborationWindowMs
        && (peak - speed) >= kCrashCorroborationDropMps;
}

- (void)dealloc {
    // Safety net in case -stop was never called.
    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc removeObserver:self name:UIApplicationDidBecomeActiveNotification object:nil];
    [nc removeObserver:self name:UIApplicationWillResignActiveNotification object:nil];
}

- (void)processMotion:(CMDeviceMotion *)motion {
    if (!self.enabled) return;
    NSTimeInterval nowMs = [[NSDate date] timeIntervalSince1970] * 1000.0;

    // userAcceleration is in g (gravity removed); convert magnitude to g and to m/s².
    double ax = motion.userAcceleration.x;
    double ay = motion.userAcceleration.y;
    double az = motion.userAcceleration.z;
    double accelMagG  = sqrt(ax*ax + ay*ay + az*az);            // g
    double accelMagMs = accelMagG * 9.80665;                    // m/s²

    double gx = motion.rotationRate.x;
    double gy = motion.rotationRate.y;
    double gz = motion.rotationRate.z;
    double gyroMag = sqrt(gx*gx + gy*gy + gz*gz);               // rad/s

    BOOL tripActiveNow = self.tripActive;
    MAURLocation *loc = self.lastLocation;
    id<MAURSensorFusionListener> l = self.listener;

    // Crash detection
    if (tripActiveNow && self.crashImpactG > 0 && accelMagG >= self.crashImpactG
            && (nowMs - self.lastCrashAt) >= self.crashCooldownMs
            && [self gpsCorroboratesImpact:nowMs]) {
        self.lastCrashAt = nowMs;
        if ([l respondsToSelector:@selector(onSensorCrashWithImpactG:location:)]) {
            [l onSensorCrashWithImpactG:accelMagG location:loc];
        }
    }

    // phoneUsageWhileDriving
    if (!tripActiveNow) { self.jitterAboveSince = 0; return; }
    BOOL screenOn = [self isScreenOnApprox];
    if (!screenOn) { self.jitterAboveSince = 0; return; }

    // v5.0.3 — AND, no OR. Paridad con Android v5.0: manipular el telefono con la mano se ve en
    // AMBOS canales a la vez. La aceleracion del vehiculo mueve el acelerometro sin rotar el
    // dispositivo, y la vibracion de la carretera no sostiene ninguno de los dos durante la
    // ventana entera. Ambos valores salen de la misma muestra de CMDeviceMotion, asi que aqui no
    // hace falta el TTL de frescura que Android necesita al tener dos canales independientes.
    BOOL above = (accelMagMs >= kJitterAccelMps2) && (gyroMag >= kJitterGyroRadS);
    if (above) {
        if (self.jitterAboveSince == 0) self.jitterAboveSince = nowMs;
        if ((nowMs - self.jitterAboveSince) >= self.phoneUsageWindowMs
                && (nowMs - self.lastPhoneUsageAt) >= self.phoneUsageCooldownMs) {
            self.lastPhoneUsageAt = nowMs;
            self.jitterAboveSince = 0;
            if ([l respondsToSelector:@selector(onPhoneUsageWhileDriving:)]) {
                [l onPhoneUsageWhileDriving:loc];
            }
        }
    } else {
        self.jitterAboveSince = 0;
    }
}

- (BOOL)isScreenOnApprox {
    // Heuristic: app is foreground active => screen is on. Background sampling does
    // not constitute phone usage while driving (passenger may have screen off too).
    //
    // v4.5.5 — read the cached flag maintained by the UIApplicationDidBecomeActive /
    // UIApplicationWillResignActive observers instead of hopping to the main queue on
    // every one of the 50 device-motion samples per second.
    return self.appIsActive;
}

@end
