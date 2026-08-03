//
//  MAURPostLocationTask.h
//  BackgroundGeolocation
//
//  Created by Marian Hello on 27/04/2018.
//  Copyright © 2018 mauron85. All rights reserved.
//

#ifndef MAURPostLocationTask_h
#define MAURPostLocationTask_h

#import "MAURConfig.h"
#import "MAURLocation.h"

@class MAURPostLocationTask;

@protocol MAURPostLocationTaskDelegate <NSObject>

@optional
- (void)postLocationTaskRequestedAbortUpdates:(MAURPostLocationTask * _Nonnull)task;
- (void)postLocationTaskHttpAuthorizationUpdates:(MAURPostLocationTask * _Nonnull)task;
// v3.5 Phase 4
- (void)postLocationTaskSyncStarted:(MAURPostLocationTask * _Nonnull)task;
- (void)postLocationTaskSyncSucceeded:(MAURPostLocationTask * _Nonnull)task locationsSent:(NSInteger)locationsSent;
- (void)postLocationTaskSyncFailed:(MAURPostLocationTask * _Nonnull)task httpStatus:(NSInteger)httpStatus message:(NSString * _Nullable)message;

@end

@interface MAURPostLocationTask : NSObject

// v4.5.5 — strong, not weak: the asynchronous blocks in -add: read `config` long after the
// caller returned. A weak ref could be nilled mid-flight, silently disabling the POST/sync.
@property (nonatomic, strong) MAURConfig * _Nullable config;
@property (nonatomic, weak) id<MAURPostLocationTaskDelegate> _Nullable delegate;
/** v4.5.1 — pending driving events buffer owned by the facade; the task drains it onto the
 *  post-transform location so events fired without a simultaneous fix (provider change,
 *  sensor crash, phone usage) survive even if `locationTransform` returns a new instance.
 *  Weak ref: if the facade is gone, no flush — by design. */
@property (nonatomic, weak) NSMutableArray * _Nullable pendingDrivingEventsBuffer;
/** v4.5.1 — same idea for the battery snapshot block. The facade installs a block that the
 *  task invokes AFTER a successful transform, so even when `locationTransform` returns a
 *  fresh instance, battery/charging fields land on what actually gets POSTed. */
@property (nonatomic, copy) void (^ _Nullable attachBatterySnapshot)(MAURLocation * _Nonnull);


- (void) add:(MAURLocation * _Nonnull)location;

/**
 * v5.0.1 — B2. `onReady` se invoca en el hilo principal con la posición YA enriquecida (transform
 * aplicado, eventos de conducción volcados y snapshot de batería puesto), antes de la política de
 * mock y de persistir. El facade emitía el evento `location` a JS de forma síncrona justo tras
 * llamar a -add:, o sea en carrera con el enriquecimiento que ocurre dentro del dispatch_async:
 * `battery`, `isCharging` y `events` llegaban a JS como nil casi siempre, mientras que en Android
 * sí venían. Reproduce el orden de Android (`LocationServiceImpl.onLocation`): enriquecer, emitir,
 * y luego persistir/postear. Un transform que devuelve nil no invoca `onReady`, igual que Android.
 */
- (void) add:(MAURLocation * _Nonnull)location onReady:(void (^ _Nullable)(MAURLocation * _Nonnull))onReady;
- (void) start;
- (void) stop;
- (void) sync;

+ (void) setLocationTransform:(MAURLocationTransform _Nullable)transform;
+ (MAURLocationTransform _Nullable) locationTransform;

@end

#endif /* MAURPostLocationTask_h */
