//
//  MAURBackgroundSync.m
//
//  Created by Marian Hello on 07/07/16.
//  Copyright © 2016 mauron85. All rights reserved.
//

#import "UIKit/UIKit.h"
#import "MAURLogging.h"
#import "MAURBackgroundSync.h"
#import "MAURSQLiteLocationDAO.h"
#import <objc/runtime.h>

NSString * const MAURBackgroundSyncDidStartNotification    = @"MAURBackgroundSyncDidStart";
NSString * const MAURBackgroundSyncDidSucceedNotification  = @"MAURBackgroundSyncDidSucceed";
NSString * const MAURBackgroundSyncDidFailNotification     = @"MAURBackgroundSyncDidFail";
NSString * const MAURBackgroundSyncDidProgressNotification = @"MAURBackgroundSyncDidProgress";

// v5.0 — D19: one identifier, one session, for the whole process.
static NSString * const MAURBackgroundSyncSessionIdentifier = @"com.marianhello.session";
static NSURLSession *sSession = nil;
static dispatch_once_t sOnce;
// v5.0 — D10: the handler UIKit hands to
// -application:handleEventsForBackgroundURLSession:completionHandler:. Process-wide, like the
// session it belongs to. Only touched inside @synchronized (sHandlerLock).
static void (^sBackgroundSessionCompletionHandler)(void) = nil;
static NSString * const sHandlerLock = @"MAURBackgroundSyncHandlerLock";

@interface MAURBackgroundSync ()  <NSURLSessionDelegate, NSURLSessionTaskDelegate, NSURLSessionDataDelegate>
{
    NSURLSession *urlSession;
    NSMutableArray *tasks;
    // v5.0.1 — B3: cola de subidas pendientes en modo `single`. Ver -enqueueSingleUploads:.
    NSMutableArray *pendingSingleUploads;
    BOOL singleChainActive;
    // v4.5.6 — D26: makes each upload's temp file name unique (single mode creates several
    // uploads within the same second). Only mutated inside @synchronized (self).
    NSUInteger uploadSequence;
}
+ (NSURLSession*) sharedSession;
+ (void) invokeBackgroundSessionCompletionHandler;
@end

/**
 * v5.0 — D19: iOS allows exactly ONE live NSURLSession per background identifier. -init used to
 * build a new session with the same identifier every time, so a WebView reload (which creates a
 * second MAURBackgroundSync while the first is still alive) tripped
 * "A background URLSession with identifier com.marianhello.session already exists!" and threw.
 * The session is now created once with dispatch_once and shared by every instance.
 *
 * Because that session outlives any single MAURBackgroundSync, it cannot be an instance's delegate.
 * Chosen approach: this tiny delegate-forwarding singleton is the session's permanent delegate and
 * holds a WEAK pointer to the current MAURBackgroundSync, forwarding each callback to it. It was
 * picked over the alternatives (per-instance sessions, re-parenting the delegate, NSProxy-style
 * runtime forwarding) because it leaves every existing delegate method on MAURBackgroundSync
 * completely unchanged and needs no rework of the `tasks` bookkeeping.
 *
 * `tasks` stays per-instance: tasks an older instance started are adopted by the current one in
 * -start (-getTasksWithCompletionHandler: now returns the shared session's tasks), and
 * -removeObject: for a task the current instance never tracked is a harmless no-op.
 */
@interface MAURBackgroundSyncDelegateProxy : NSObject <NSURLSessionDelegate, NSURLSessionTaskDelegate, NSURLSessionDataDelegate>
@property (atomic, weak) MAURBackgroundSync *target;
+ (instancetype) sharedProxy;
@end

@implementation MAURBackgroundSyncDelegateProxy

+ (instancetype) sharedProxy
{
    static MAURBackgroundSyncDelegateProxy *proxy = nil;
    static dispatch_once_t proxyOnce;
    dispatch_once(&proxyOnce, ^{
        proxy = [[MAURBackgroundSyncDelegateProxy alloc] init];
    });
    return proxy;
}

- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
   didSendBodyData:(int64_t)bytesSent
    totalBytesSent:(int64_t)totalBytesSent
totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend
{
    [self.target URLSession:session task:task didSendBodyData:bytesSent totalBytesSent:totalBytesSent totalBytesExpectedToSend:totalBytesExpectedToSend];
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(nullable NSError *)error
{
    [self.target URLSession:session task:task didCompleteWithError:error];
}

- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveData:(NSData *)data
{
    [self.target URLSession:session dataTask:dataTask didReceiveData:data];
}

- (void)URLSession:(NSURLSession *)session didBecomeInvalidWithError:(nullable NSError *)error
{
    [self.target URLSession:session didBecomeInvalidWithError:error];
}

- (void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session
{
    MAURBackgroundSync *current = self.target;
    if (current != nil) {
        [current URLSessionDidFinishEventsForBackgroundURLSession:session];
    } else {
        // v5.0 — D10: no live instance (the WebView went away while uploads were still running).
        // UIKit's completion handler MUST still be called or iOS stops relaunching us.
        [MAURBackgroundSync invokeBackgroundSessionCompletionHandler];
    }
}

@end

#pragma mark - v5.0.1 (R12) form-urlencoded helpers

/**
 * v5.0.1 — R12. La ruta de sync serializaba SIEMPRE un array JSON, tambien cuando el usuario
 * declaraba `Content-Type: application/x-www-form-urlencoded`. Android manda N peticiones planas
 * (`lat=..&lon=..`) y iOS mandaba `[{...}]` etiquetado como formulario: ningun decoder
 * OsmAnd/Traccar lo entiende -> HTTP 400 en cada ventana de sync. Estas tres funciones replican
 * exactamente lo que ya hacen MAURPostLocationTask (iOS) y HttpPostService (Android).
 */
static NSString * MAURContentTypeFromHeaders(NSDictionary *httpHeaders)
{
    if (httpHeaders == nil) return nil;
    // La clave de cabecera es case-insensitive en HTTP: `content-type` en minusculas tiene que
    // contar igual, si no el aplanado se desactiva por la puerta de atras.
    for (id key in httpHeaders) {
        if ([key isKindOfClass:[NSString class]]
                && [(NSString *)key caseInsensitiveCompare:@"Content-Type"] == NSOrderedSame) {
            return [NSString stringWithFormat:@"%@", [httpHeaders objectForKey:key]];
        }
    }
    return nil;
}

/** `application/x-www-form-urlencoded; charset=UTF-8` -> `application/x-www-form-urlencoded`. */
static BOOL MAURIsFormUrlEncoded(NSString *contentType)
{
    if (contentType == nil) return NO;
    NSString *mediaType = [[contentType componentsSeparatedByString:@";"] firstObject] ?: contentType;
    mediaType = [[mediaType stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]] lowercaseString];
    return [@"application/x-www-form-urlencoded" isEqualToString:mediaType];
}

static NSString * MAURFormUrlEncodedBody(NSDictionary *dict)
{
    NSMutableArray *parts = [NSMutableArray array];
    for (id key in dict) {
        if (![key isKindOfClass:[NSString class]]) continue;
        id raw = [dict objectForKey:key];
        // v4.5.4: los NSNull (placeholders sin valor: @speed, @events, @battery...) se omiten.
        // Serializarlos como "<null>" hace que Traccar responda 400 (NumberFormatException).
        if (raw == nil || raw == [NSNull null]) continue;
        NSString *value = [NSString stringWithFormat:@"%@", raw];
        if ([@"null" isEqualToString:value] || [@"<null>" isEqualToString:value]) continue;
        NSString *encodedKey = [(NSString *)key stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]];
        NSString *encodedValue = [value stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]];
        [parts addObject:[NSString stringWithFormat:@"%@=%@", encodedKey, encodedValue]];
    }
    return [parts componentsJoinedByString:@"&"];
}

@implementation MAURBackgroundSync

+ (NSString*) sessionIdentifier
{
    return MAURBackgroundSyncSessionIdentifier;
}

// v5.0 — D19: created exactly once per process; every MAURBackgroundSync reuses it.
+ (NSURLSession*) sharedSession
{
    dispatch_once(&sOnce, ^{
        // v4.5.5 — +backgroundSessionConfiguration: is deprecated since iOS 8; the replacement is
        // +backgroundSessionConfigurationWithIdentifier:.
        NSURLSessionConfiguration *conf = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:MAURBackgroundSyncSessionIdentifier];
        conf.allowsCellularAccess = YES;
        sSession = [NSURLSession sessionWithConfiguration:conf
                                                delegate:[MAURBackgroundSyncDelegateProxy sharedProxy]
                                           delegateQueue:[NSOperationQueue mainQueue]];
    });
    return sSession;
}

+ (void) setBackgroundSessionCompletionHandler:(void (^)(void))completionHandler
{
    @synchronized (sHandlerLock) {
        sBackgroundSessionCompletionHandler = [completionHandler copy];
    }
}

- (void) setBackgroundSessionCompletionHandler:(void (^)(void))completionHandler
{
    [MAURBackgroundSync setBackgroundSessionCompletionHandler:completionHandler];
}

+ (void) invokeBackgroundSessionCompletionHandler
{
    dispatch_async(dispatch_get_main_queue(), ^{
        void (^handler)(void) = nil;
        @synchronized (sHandlerLock) {
            handler = sBackgroundSessionCompletionHandler;
            sBackgroundSessionCompletionHandler = nil;
        }
        if (handler != nil) {
            DDLogInfo(@"Calling back UIKit background session completion handler");
            handler();
        }
    });
}

- (instancetype) init
{
    if(!(self = [super init])) return nil;

    // v3.5 Phase 4: previously `tasks` was never allocated; addObject/removeObject/cancel/status
    // silently no-op'd on nil. Allocate now so cancel and status actually work.
    tasks = [[NSMutableArray alloc] init];
    pendingSingleUploads = [[NSMutableArray alloc] init];
    singleChainActive = NO;

    // v5.0 — D19: reuse the process-wide background session and become the instance its delegate
    // callbacks are forwarded to. The newest instance wins, which is what a WebView reload wants.
    urlSession = [MAURBackgroundSync sharedSession];
    [MAURBackgroundSyncDelegateProxy sharedProxy].target = self;

    return self;
}

- (void) dealloc
{
    // v5.0 — D19: the session is process-wide now, so an instance going away must NOT invalidate
    // it: the identifier could never be recreated and every later upload would fail. The forwarding
    // proxy holds us weakly, so there is nothing to unregister either (previously this called
    // -finishTasksAndInvalidate because the session was per-instance and retained us as delegate).
    urlSession = nil;
}

- (void)start
{
    __block UIBackgroundTaskIdentifier bgTask = [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
        [[UIApplication sharedApplication] endBackgroundTask:bgTask];
    }];    
    
    [urlSession getTasksWithCompletionHandler:^(NSArray *dataTasks, NSArray *uploadTasks, NSArray *downloadTasks) {
        for(NSURLSessionUploadTask *task in uploadTasks) {
            DDLogInfo(@"Restored upload task %zu for %@", (unsigned long)task.taskIdentifier, task.originalRequest.URL);
            // v4.5.5 — `tasks` is mutated from this session queue and from the caller's queue
            // in -sync:, and read back on the delegate queue. All accesses are serialized.
            // v4.5.6 — D10: -start may run more than once (every [facade start:]); do not track
            // the same task twice, -removeObject: only drops the first occurrence.
            @synchronized (tasks) {
                if (![tasks containsObject:task]) {
                    [tasks addObject:task];
                }
            }
            [task resume];
        }

        [[UIApplication sharedApplication] endBackgroundTask:bgTask];
    }];
}

- (void)cancel
{
    // Snapshot under the lock, then cancel outside it: -cancel can synchronously drive
    // delegate callbacks that mutate `tasks`.
    NSArray *snapshot = nil;
    @synchronized (tasks) {
        snapshot = [tasks copy];
    }
    for(NSURLSessionTask *task in snapshot) {
        [task cancel];
    }
}

- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders
{
    [self sync:url withTemplate:locationTemplate withHttpHeaders:httpHeaders withMethod:@"POST"];
}

- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders withMethod:(NSString * _Nullable)method
{
    [self sync:url withTemplate:locationTemplate withHttpHeaders:httpHeaders withMethod:method withMode:@"batch"];
}

// v4.5.6 — D26: syncMode was parsed and persisted but the uploader always built a JSON array.
// "single" now produces one upload task per location; every other value keeps the batch payload.
- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders withMethod:(NSString * _Nullable)method withMode:(NSString * _Nullable)mode
{
    MAURSQLiteLocationDAO* locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    NSArray *locations = [locationDAO getLocationsForSync];

    // v5.0.1 — paridad con SyncAdapter de Android, que envía `x-batch-id` en cada subida. Un
    // backend que deduplica lotes por esa cabecera funcionaba en Android y duplicaba en iOS al
    // reintentar. No se muta el diccionario del usuario: se copia.
    NSMutableDictionary *syncHeaders = (httpHeaders != nil)
            ? [httpHeaders mutableCopy]
            : [NSMutableDictionary dictionary];
    long long batchId = (long long)([[NSDate date] timeIntervalSince1970] * 1000.0);
    syncHeaders[@"x-batch-id"] = [NSString stringWithFormat:@"%lld", batchId];
    httpHeaders = syncHeaders;

    BOOL singleMode = (mode != nil && [@"single" isEqualToString:[mode lowercaseString]]);

    // v5.0.1 — R12: form-urlencoded no tiene forma de representar un array de N posiciones en un
    // solo cuerpo. Android resuelve esto mandando una peticion plana por elemento; aqui se fuerza
    // la misma ruta por-posicion, que ademas ya lleva la contabilidad correcta de locationIds
    // (una fila borrada solo cuando SU peticion respondio 2xx).
    if (!singleMode && MAURIsFormUrlEncoded(MAURContentTypeFromHeaders(httpHeaders))) {
        DDLogInfo(@"Sync: Content-Type form-urlencoded con syncMode 'batch'; se envia una peticion "
                  @"plana por posicion (paridad con Android). Usa application/json si quieres un lote unico.");
        singleMode = YES;
    }

    if (singleMode) {
        if ([locations count] == 0) {
            return;
        }
        // v5.0.1 — B3: esto lanzaba las N subidas de golpe. Android es SECUENCIAL y corta en el
        // primer fallo (HttpPostService, bucle per-item), asi que con 500 posiciones en cola y el
        // servidor caido Android hacia 1 peticion y iOS 500. Ahora se encolan y se envia la
        // siguiente solo cuando la anterior responde 2xx; un fallo (o un 285) vacia la cola y el
        // resto se reintenta en la proxima ventana de sync.
        NSMutableArray *queued = [NSMutableArray arrayWithCapacity:[locations count]];
        for (MAURLocation *location in locations) {
            id payload = [location toResultFromTemplate:locationTemplate];
            NSArray *ids = (location.locationId != nil) ? @[location.locationId] : @[];
            [queued addObject:@{ @"payload": payload,
                                 @"url": url,
                                 @"headers": (httpHeaders ?: @{}),
                                 @"method": (method ?: @"POST"),
                                 @"ids": ids }];
        }
        @synchronized (pendingSingleUploads) {
            [pendingSingleUploads addObjectsFromArray:queued];
            if (singleChainActive) {
                // Ya hay una cadena en curso; sus completions consumiran tambien lo recien anadido.
                return;
            }
            singleChainActive = YES;
        }
        [self startNextSingleUpload];
        return;
    }

    // v5.0.1 — sin esta guarda, un forceSync() con la cola vacía subía un cuerpo `[]` real:
    // Android corta en dos sitios (BatchManager y SyncAdapter) y nunca crea un lote vacío. Si el
    // backend contestaba 285 a ese `[]`, se emitía abort_updates y el tracking se paraba solo;
    // con un 4xx se emitía un syncError espurio.
    if ([locations count] == 0) {
        return;
    }

    NSMutableArray *jsonArray = [[NSMutableArray alloc] initWithCapacity:[locations count]];
    for (MAURLocation *location in locations) {
        [jsonArray addObject:[location toResultFromTemplate:locationTemplate]];
    }

    [self upload:jsonArray
           toUrl:url
 withHttpHeaders:httpHeaders
      withMethod:method
  locationsCount:[jsonArray count]
     locationIds:nil];
}

/**
 * v5.0.1 — B3: envia la siguiente subida encolada del modo `single`. Marca la tarea con el flag
 * "singleChain" para que -URLSession:task:didCompleteWithError: sepa continuar (2xx) o abortar la
 * cadena (fallo o 285), igual que el bucle per-item de Android.
 *
 * Si el proceso muere a mitad de cadena la cola en memoria se pierde: las filas restantes siguen
 * SyncPending y las recupera -restoreStaleSyncLocationsOlderThan: en la siguiente ventana de sync.
 */
- (void) startNextSingleUpload
{
    // Bucle en vez de una sola llamada: si -upload: no llega a crear la tarea (serializacion
    // fallida, URL invalida), NO habra ningun didCompleteWithError que continue la cadena. Sin
    // este bucle singleChainActive se quedaba en YES PARA SIEMPRE y todos los -sync: posteriores
    // encolaban y salian sin enviar nada: el sync moria hasta reiniciar la app, con
    // pendingSingleUploads creciendo sin limite. Ahora la entrada que no se pudo enviar se
    // devuelve a PostPending y se prueba la siguiente.
    while (YES) {
        NSDictionary *next = nil;
        @synchronized (pendingSingleUploads) {
            if ([pendingSingleUploads count] == 0) {
                singleChainActive = NO;
                return;
            }
            next = [pendingSingleUploads firstObject];
            [pendingSingleUploads removeObjectAtIndex:0];
        }

        NSArray *ids = next[@"ids"];
        BOOL started = [self upload:next[@"payload"]
                              toUrl:next[@"url"]
                    withHttpHeaders:[next[@"headers"] mutableCopy]
                         withMethod:next[@"method"]
                     locationsCount:1
                        locationIds:([ids count] > 0 ? ids : nil)
                        singleChain:YES];
        if (started) {
            return; // la continuacion la hara didCompleteWithError
        }
        DDLogError(@"Sync 'single': no se pudo crear la subida de %@; se devuelve a la cola y se "
                   @"continua con la siguiente", ids);
        [self restorePendingIds:ids];
    }
}

/**
 * v5.0.1 — B3: devuelve a PostPending las filas de las entradas descartadas.
 *
 * -getLocationsForSync marca TODAS las filas PostPending -> SyncPending al empezar el ciclo. Si la
 * cadena se aborta sin enviarlas, solo la tarea que fallo restaura sus propios ids: las demas se
 * quedaban SyncPending e invisibles hasta que
 * -restoreStaleSyncLocationsOlderThan: las rescataba... 15 MINUTOS despues. Con 500 posiciones y
 * un 502 en la primera, eran 499 filas congeladas un cuarto de hora, y las ventanas de sync
 * intermedias veian la cola vacia. Android restaura el lote entero al momento.
 */
- (void) restorePendingIds:(NSArray *)ids
{
    if ([ids count] == 0) return;
    NSError *err = nil;
    if (![[MAURSQLiteLocationDAO sharedInstance] restoreFailedSyncLocations:ids error:&err]) {
        DDLogError(@"restoreFailedSyncLocations tras abortar la cadena fallo: %@",
                   err.localizedDescription ?: @"unknown");
    }
}

/** v5.0.1 — B3: descarta lo que quede en la cadena tras un fallo, devolviendo las filas a la cola. */
- (void) abortSingleChain
{
    NSArray *dropped = nil;
    @synchronized (pendingSingleUploads) {
        dropped = [pendingSingleUploads copy];
        [pendingSingleUploads removeAllObjects];
        singleChainActive = NO;
    }
    if ([dropped count] == 0) return;

    NSMutableArray *ids = [NSMutableArray array];
    for (NSDictionary *entry in dropped) {
        NSArray *entryIds = entry[@"ids"];
        if ([entryIds count] > 0) {
            [ids addObjectsFromArray:entryIds];
        }
    }
    DDLogWarn(@"Sync 'single': %lu posiciones devueltas a la cola tras un fallo",
              (unsigned long)[dropped count]);
    [self restorePendingIds:ids];
}

/**
 * v4.5.6 — D26: extracted from -sync: so both batch and single mode share one code path.
 * `locationIds` is nil in batch mode (success deletes every synced row older than the captured
 * cutoff, as before) and carries the single location's id in single mode (success deletes exactly
 * that row, so a sibling upload that succeeded first cannot drop rows this one still owns).
 */
- (void) upload:(id)jsonPayload
          toUrl:(NSString * _Nonnull)url
withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders
     withMethod:(NSString * _Nullable)method
 locationsCount:(NSUInteger)locationsCount
    locationIds:(NSArray * _Nullable)locationIds
{
    [self upload:jsonPayload toUrl:url withHttpHeaders:httpHeaders withMethod:method
  locationsCount:locationsCount locationIds:locationIds singleChain:NO];
}

/** Devuelve YES solo si se llego a crear y arrancar la NSURLSessionTask. Ver -startNextSingleUpload. */
- (BOOL) upload:(id)jsonPayload
          toUrl:(NSString * _Nonnull)url
withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders
     withMethod:(NSString * _Nullable)method
 locationsCount:(NSUInteger)locationsCount
    locationIds:(NSArray * _Nullable)locationIds
    singleChain:(BOOL)singleChain
{
    // v5.0.1 — R12: el cuerpo se construye segun el Content-Type configurado, no siempre JSON.
    NSString *configuredContentType = MAURContentTypeFromHeaders(httpHeaders);
    BOOL isFormUrlEncoded = MAURIsFormUrlEncoded(configuredContentType);

    NSData *bodyData = nil;
    if (isFormUrlEncoded) {
        NSDictionary *dict = nil;
        if ([jsonPayload isKindOfClass:[NSDictionary class]]) {
            dict = (NSDictionary *)jsonPayload;
        } else if ([jsonPayload isKindOfClass:[NSArray class]]
                   && [(NSArray *)jsonPayload count] == 1
                   && [[(NSArray *)jsonPayload firstObject] isKindOfClass:[NSDictionary class]]) {
            dict = (NSDictionary *)[(NSArray *)jsonPayload firstObject];
        }
        if (dict != nil) {
            bodyData = [MAURFormUrlEncodedBody(dict) dataUsingEncoding:NSUTF8StringEncoding];
        } else {
            // Paridad con la guarda H1 de Android: un postTemplate de tipo array no se puede
            // aplanar a clave=valor. No enviar nada en silencio devolveria un 200 falso y borraria
            // las filas; se envia sin aplanar para que el servidor rechace y el fallo sea visible.
            DDLogError(@"form-urlencoded requiere un postTemplate de tipo objeto; el payload es %@. "
                       @"Se envia sin aplanar y el servidor lo rechazara.", NSStringFromClass([jsonPayload class]));
        }
    }

    if (bodyData == nil) {
        NSError *error = nil;
        bodyData = [NSJSONSerialization dataWithJSONObject:jsonPayload options:0 error:&error];
        if (bodyData == nil) {
            DDLogError(@"Sync payload serialization failed: %@", error.localizedDescription);
            return NO;
        }
    }

    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    dateFormatter.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
    dateFormatter.dateFormat = @"yyyyMMdd_HHmms";
    dateFormatter.timeZone = [NSTimeZone timeZoneForSecondsFromGMT:0];
    // v4.5.6 — D26: the old name had 1-second resolution. In single mode several uploads are
    // created within the same second and would all share one file: the first completion handler
    // deleted it out from under the others. A monotonic counter keeps every path unique.
    NSUInteger sequence;
    @synchronized (self) {
        sequence = ++uploadSequence;
    }
    NSString *fileName = [NSString stringWithFormat:@"locations_%@_%lu.%@",
                          [dateFormatter stringFromDate:[NSDate date]], (unsigned long)sequence,
                          (isFormUrlEncoded ? @"txt" : @"json")];
    NSURL *jsonUrl = [NSURL fileURLWithPath:[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0] stringByAppendingPathComponent:fileName]];
    [bodyData writeToFile:jsonUrl.path atomically:NO];
    uint64_t bytesTotalForThisFile = [[[NSFileManager defaultManager] attributesOfItemAtPath:jsonUrl.path error:nil] fileSize];

    // v5.0.1 — +URLWithString: devuelve nil ante una URL malformada (p.ej. un placeholder de
    // queryParams sin escapar), y -initWithURL:nil lanza NSInvalidArgumentException: crash del
    // proceso en cada ventana de sync. Se aborta esta subida y el llamante continua.
    NSURL *requestUrl = [NSURL URLWithString:url];
    if (requestUrl == nil) {
        DDLogError(@"Sync: URL invalida, no se puede subir: %@", url);
        [[NSFileManager defaultManager] removeItemAtURL:jsonUrl error:NULL];
        return NO;
    }

    NSMutableURLRequest *request = [[NSMutableURLRequest alloc] initWithURL:requestUrl];
    NSString *resolvedMethod = (method != nil && method.length > 0) ? [method uppercaseString] : @"POST";
    [request setHTTPMethod:resolvedMethod];
    [request setTimeoutInterval:120]; // Prevents sync from hanging indefinitely if server does not respond
    [request setValue:[NSString stringWithFormat:@"%llu", bytesTotalForThisFile] forHTTPHeaderField:@"Content-Length"];
    // v5.0.1 — H3: el Content-Type se fijaba a application/json y luego el bucle de abajo hacia
    // addValue: para TODAS las claves, incluida Content-Type. addValue: CONCATENA, asi que con
    // httpHeaders = {Content-Type: application/x-www-form-urlencoded} la cabecera que salia al
    // cable era literalmente "application/json,application/x-www-form-urlencoded" — invalida.
    // Ahora se respeta la del usuario y se usa setValue: (reemplaza) para esa clave concreta,
    // igual que ya hacia MAURPostLocationTask. `configuredContentType` se resolvio arriba (R12).
    [request setValue:(configuredContentType ?: @"application/json") forHTTPHeaderField:@"Content-Type"];

    if (httpHeaders != nil) {
        for(id key in httpHeaders) {
            if ([key isKindOfClass:[NSString class]]
                    && [(NSString *)key caseInsensitiveCompare:@"Content-Type"] == NSOrderedSame) {
                continue; // ya aplicada arriba con setValue:
            }
            id value = [httpHeaders objectForKey:key];
            [request addValue:value forHTTPHeaderField:key];
        }
    }
    NSURLSessionTask *task = [urlSession uploadTaskWithRequest:request fromFile:jsonUrl];
    task.taskDescription = fileName;
    @synchronized (tasks) {
        [tasks addObject:task];
    }
    DDLogInfo(@"Started upload for %@ as task %zu/%@/%@", jsonUrl.lastPathComponent, (unsigned long)task.taskIdentifier, task.taskDescription, task);

    // v3.5 Phase 4: emit syncStart now that we are about to push to the server.
    if (self.delegate && [self.delegate respondsToSelector:@selector(backgroundSyncStarted:)]) {
        [self.delegate backgroundSyncStarted:self];
    }
    [[NSNotificationCenter defaultCenter] postNotificationName:MAURBackgroundSyncDidStartNotification object:self];

    // Stash count so didCompleteWithError can report it as syncSuccess payload.
    objc_setAssociatedObject(task, "locationsSent", @(locationsCount), OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    // v4.5.1: capture cutoff timestamp NOW so on success we delete only the rows that existed
    // before the upload started. Locations persisted DURING the upload are preserved.
    objc_setAssociatedObject(task, "uploadCutoff",
        @([[NSDate date] timeIntervalSince1970]), OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    // v4.5.6 — D26: single mode only owns its own row(s).
    if (locationIds != nil) {
        objc_setAssociatedObject(task, "locationIds", locationIds, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    // v5.0.1 — B3: marca de pertenencia a la cadena secuencial del modo `single`.
    if (singleChain) {
        objc_setAssociatedObject(task, "singleChain", @YES, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }

    [task resume];

    return YES;
}

// http://stackoverflow.com/a/572623/48125
NSString *stringFromFileSize(unsigned long long theSize)
{
    double floatSize = theSize;
    if (theSize<1023)
        return([NSString stringWithFormat:@"%lli bytes",theSize]);
    floatSize = floatSize / 1024;
    if (floatSize<1023)
        return([NSString stringWithFormat:@"%1.1f KB",floatSize]);
    floatSize = floatSize / 1024;
    if (floatSize<1023)
        return([NSString stringWithFormat:@"%1.1f MB",floatSize]);
    floatSize = floatSize / 1024;
    
    return([NSString stringWithFormat:@"%1.1f GB",floatSize]);
}

- (NSString*)status
{
    int64_t sent = 0, toSend = 0;
    NSArray *snapshot = nil;
    @synchronized (tasks) {
        snapshot = [tasks copy];
    }
    for(NSURLSessionUploadTask *task in snapshot) {
        sent += task.countOfBytesSent;
        toSend += task.countOfBytesExpectedToSend;
    }
    return [NSString stringWithFormat:@"%@ being uploaded (%@ of %@)\nFiles on disk: %@",
        [snapshot valueForKeyPath:@"taskDescription"],
        stringFromFileSize(sent),
        stringFromFileSize(toSend),

        [[NSFileManager defaultManager]
         contentsOfDirectoryAtPath:NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0]
         error:NULL]
    ];
}


#pragma mark -
// v3.5 Phase 4: forward upload progress as syncProgress (0..100).
- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
   didSendBodyData:(int64_t)bytesSent
    totalBytesSent:(int64_t)totalBytesSent
totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend
{
    if (totalBytesExpectedToSend <= 0) return;
    NSInteger progress = (NSInteger)((totalBytesSent * 100) / totalBytesExpectedToSend);
    if (progress < 0) progress = 0;
    if (progress > 100) progress = 100;
    [[NSNotificationCenter defaultCenter]
        postNotificationName:MAURBackgroundSyncDidProgressNotification
                      object:self
                    userInfo:@{@"progress": @(progress)}];
}

/**
 * v5.0 — D4: undo the in-flight SyncPending state for the rows THIS upload owns, mirroring the
 * success path. The old code called the global -restoreFailedSyncLocations:, which flips every
 * SyncPending row in the table back to PostPending — so one failing upload also resurrected the
 * rows a sibling upload still had in flight, and those got sent (and stored) twice.
 * Scope, in the same order the success path uses:
 *   "locationIds"  → single mode: exactly this task's row(s)
 *   "uploadCutoff" → batch mode: SyncPending rows recorded at or before the upload's start
 *   neither        → legacy/adopted task with no bookkeeping: fall back to the global restore
 */
- (void) restoreFailedSyncForTask:(NSURLSessionTask *)task
{
    MAURSQLiteLocationDAO *locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    NSError *restErr = nil;
    NSArray *locationIds = objc_getAssociatedObject(task, "locationIds");
    NSNumber *cutoffNum = objc_getAssociatedObject(task, "uploadCutoff");

    if (locationIds != nil) {
        if (![locationDAO restoreFailedSyncLocations:locationIds error:&restErr]) {
            DDLogError(@"restoreFailedSyncLocations(ids) failed: %@", restErr.localizedDescription ?: @"unknown");
        }
    } else if (cutoffNum != nil) {
        if (![locationDAO restoreFailedSyncLocationsBefore:[cutoffNum doubleValue] error:&restErr]) {
            DDLogError(@"restoreFailedSyncLocationsBefore failed: %@", restErr.localizedDescription ?: @"unknown");
        }
    } else {
        // v5.0 — D4: do NOT fall back to the global restore. This branch is reached when the
        // task was adopted after a relaunch (associated objects die with the process), and a
        // global SyncPending → PostPending would also revert the rows a sibling upload still has
        // in flight — the exact duplication this item is about. Those orphaned rows are already
        // covered: -restoreStaleSyncLocationsOlderThan: runs at the start of every sync window
        // and rescues any SyncPending row older than the cutoff. Log and let it do its job.
        DDLogWarn(@"restoreFailedSyncForTask: task %lu has no locationIds/uploadCutoff (adopted "
                  @"after relaunch); leaving its rows SyncPending for restoreStaleSyncLocationsOlderThan:",
                  (unsigned long)[task taskIdentifier]);
    }
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(nullable NSError *)error
{
    NSInteger statusCode = [(NSHTTPURLResponse *)task.response statusCode];
    
    DDLogInfo(@"Finished uploading task %zu %@: %@ %@, HTTP %ld", (unsigned long)[task taskIdentifier], task.originalRequest.URL, error ?: @"Success", task.response, (long)statusCode);
    
    @synchronized (tasks) {
        [tasks removeObject:task];
    }
    // v5.0 — D10: with background completions now actually delivered after a relaunch, this runs
    // for tasks adopted in -start too, whose taskDescription can be nil — and
    // -stringByAppendingPathComponent:nil throws.
    NSString *taskFileName = task.taskDescription;
    if (taskFileName.length > 0) {
        NSURL *fullPath = [NSURL fileURLWithPath:[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0] stringByAppendingPathComponent:taskFileName]];
        [[NSFileManager defaultManager] removeItemAtURL:fullPath error:NULL];
    }
    
    if (statusCode == 285)
    {
        // Okay, but we don't need to continue sending these
        DDLogDebug(@"Locations were uploaded to the server, and received an \"HTTP 285 Updates Not Required\"");
        
        dispatch_async(dispatch_get_main_queue(), ^{
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncRequestedAbortUpdates:)])
            {
                [_delegate backgroundSyncRequestedAbortUpdates:self];
            }
        });
    }

    if (statusCode == 401)
    {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncHttpAuthorizationUpdates:)])
            {
                [_delegate backgroundSyncHttpAuthorizationUpdates:self];
            }
        });
    }

    // v3.5 Phase 4: emit syncSuccess / syncError.
    NSNumber *sentNum = objc_getAssociatedObject(task, "locationsSent");
    NSInteger locationsSent = sentNum != nil ? [sentNum integerValue] : 0;
    BOOL isStatusOkay = (statusCode >= 200 && statusCode < 300);

    // v5.0.1 — B3: cadena secuencial del modo `single`. 2xx (salvo 285) -> siguiente; cualquier
    // otra cosa vacia la cola y el resto se reintenta en la proxima ventana, igual que corta el
    // bucle per-item de Android. Se hace ANTES del dispatch al hilo principal para no encadenar
    // una peticion nueva despues de un fallo por culpa del reordenamiento de colas.
    if (objc_getAssociatedObject(task, "singleChain") != nil) {
        if (error == nil && isStatusOkay && statusCode != 285) {
            [self startNextSingleUpload];
        } else {
            [self abortSingleChain];
        }
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        if (error != nil) {
            // v4.5.1 — restore SyncPending → PostPending so the failed locations get retried
            // on the next sync window. Without this, a single network drop loses everything.
            // v5.0 — D4: scoped to THIS upload's rows (see -restoreFailedSyncForTask:).
            [self restoreFailedSyncForTask:task];
            NSString *msg = error.localizedDescription ?: @"";
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncFailed:httpStatus:message:)]) {
                [_delegate backgroundSyncFailed:self httpStatus:0 message:msg];
            }
            [[NSNotificationCenter defaultCenter]
                postNotificationName:MAURBackgroundSyncDidFailNotification
                              object:self
                            userInfo:@{@"httpStatus": @0, @"message": msg}];
        } else if (!isStatusOkay) {
            // v4.5.1 — server-side failure (5xx, 4xx other than 285/401): also restore the rows.
            // v5.0 — D4: scoped to THIS upload's rows (see -restoreFailedSyncForTask:).
            [self restoreFailedSyncForTask:task];
            NSString *msg = [NSString stringWithFormat:@"HTTP %ld", (long)statusCode];
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncFailed:httpStatus:message:)]) {
                [_delegate backgroundSyncFailed:self httpStatus:statusCode message:msg];
            }
            [[NSNotificationCenter defaultCenter]
                postNotificationName:MAURBackgroundSyncDidFailNotification
                              object:self
                            userInfo:@{@"httpStatus": @(statusCode), @"message": msg}];
        } else {
            // v4.5.6 — D26: in single mode this task owns exactly the rows listed in
            // "locationIds"; delete only those so a sibling upload still in flight keeps its own.
            NSArray *locationIds = objc_getAssociatedObject(task, "locationIds");
            if (locationIds != nil) {
                for (NSNumber *locationId in locationIds) {
                    NSError *singleDelErr = nil;
                    if (![[MAURSQLiteLocationDAO sharedInstance] deleteLocation:locationId error:&singleDelErr]) {
                        NSLog(@"deleteLocation %@ after success failed: %@", locationId, singleDelErr.localizedDescription ?: @"unknown");
                    }
                }
            } else {
                // v4.5.1: drop SYNC_PENDING locations whose recorded_at is <= the captured
                // upload-start cutoff. This preserves any new locations persisted DURING the
                // upload (race window).
                NSNumber *cutoffNum = objc_getAssociatedObject(task, "uploadCutoff");
                NSTimeInterval cutoff = cutoffNum != nil ? [cutoffNum doubleValue] : [[NSDate date] timeIntervalSince1970];
                NSError *delErr = nil;
                BOOL deleted = [[MAURSQLiteLocationDAO sharedInstance] deleteSyncedLocationsBefore:cutoff error:&delErr];
                if (!deleted) {
                    NSLog(@"deleteSyncedLocationsBefore after success failed: %@", delErr.localizedDescription ?: @"unknown");
                }
            }
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncSucceeded:locationsSent:)]) {
                [_delegate backgroundSyncSucceeded:self locationsSent:locationsSent];
            }
            [[NSNotificationCenter defaultCenter]
                postNotificationName:MAURBackgroundSyncDidSucceedNotification
                              object:self
                            userInfo:@{@"sent": @(locationsSent)}];
        }
    });
}

- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveData:(NSData *)data
{
    DDLogInfo(@"Response:: %@", [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]);
}

- (void)URLSession:(NSURLSession *)session didBecomeInvalidWithError:(nullable NSError *)error
{
    DDLogError(@"Autosync failed :( %@", error);
}

- (void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session
{
    DDLogInfo(@"finished events for bg session");
    // v5.0 — D10: every queued completion callback for this background session has been delivered;
    // give UIKit's completion handler back (on the main queue) so the app can be suspended again.
    // Not calling it makes iOS throttle and eventually stop background-session relaunches, which is
    // why uploads finished while suspended used to be lost.
    [MAURBackgroundSync invokeBackgroundSessionCompletionHandler];
}

@end
