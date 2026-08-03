//
//  MAURConfig.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 11/06/16.
//

#import "MAURConfig.h"
#import "MAURLogging.h"

#define isNull(value) (value == nil || value == (id)[NSNull null])
#define isNotNull(value) (value != nil && value != (id)[NSNull null])

@implementation MAURConfig 

@synthesize stationaryRadius, distanceFilter, desiredAccuracy, _debug, activityType, activitiesInterval, _stopOnTerminate, url, syncUrl, syncThreshold, syncEnabled, httpHeaders, httpMethod, syncHttpMethod, httpMode, syncMode, queryParams, _showsBackgroundLocationIndicator, heartbeatInterval, mockLocationPolicy, drivingEvents, includeBattery, activityConfidenceThreshold, maxAcceptedAccuracy, _saveBatteryOnBackground, maxLocations, _pauseLocationUpdates, locationProvider, _template;

-(instancetype) initWithDefaults {
    self = [super init];
    
    if (self == nil) {
        return self;
    }
    
    stationaryRadius = [NSNumber numberWithInt:50];
    distanceFilter = [NSNumber numberWithInt:500];
    desiredAccuracy = [NSNumber numberWithInt:100];
    _debug = [NSNumber numberWithBool:NO];
    activityType = @"OtherNavigation";
    activitiesInterval = [NSNumber numberWithInt:10000];
    _stopOnTerminate = [NSNumber numberWithBool:YES];
    _saveBatteryOnBackground = [NSNumber numberWithBool:NO];
    maxLocations = [NSNumber numberWithInt:10000];
    syncThreshold = [NSNumber numberWithInt:100];
    syncEnabled = [NSNumber numberWithBool:YES];
    _pauseLocationUpdates = [NSNumber numberWithBool:NO];
    locationProvider = [NSNumber numberWithInt:DISTANCE_FILTER_PROVIDER];
    httpMethod = @"POST";
    syncHttpMethod = @"POST";
    httpMode = @"batch";
    syncMode = @"batch";
    heartbeatInterval = [NSNumber numberWithInt:0];
    mockLocationPolicy = @"allow";
    // v4.5.4 — match Android defaults so the JS layer sees the same behavior
    // regardless of platform when the host doesn't override these.
    activityConfidenceThreshold = [NSNumber numberWithInt:50];
    maxAcceptedAccuracy = nil; // off by default
//    template =

    return self;
}

+(instancetype) fromDictionary:(NSDictionary*)config
{
    MAURConfig *instance = [[MAURConfig alloc] init];

    if (isNotNull(config[@"stationaryRadius"])) {
        instance.stationaryRadius = config[@"stationaryRadius"];
    }
    if (isNotNull(config[@"distanceFilter"])) {
        instance.distanceFilter = config[@"distanceFilter"];
    }
    if (isNotNull(config[@"desiredAccuracy"])) {
        instance.desiredAccuracy = config[@"desiredAccuracy"];
    }
    if (isNotNull(config[@"debug"])) {
        instance._debug = config[@"debug"];
    }
    if (isNotNull(config[@"activityType"])) {
        instance.activityType = config[@"activityType"];
    }
    if (isNotNull(config[@"activitiesInterval"])) {
        instance.activitiesInterval = config[@"activitiesInterval"];
    }
    if (isNotNull(config[@"stopOnTerminate"])) {
        instance._stopOnTerminate = config[@"stopOnTerminate"];
    }
    if (config[@"url"] != nil) {
        instance.url = config[@"url"];
    }
    if (config[@"syncUrl"] != nil) {
        instance.syncUrl = config[@"syncUrl"];
    }
    if (isNotNull(config[@"syncThreshold"])) {
        instance.syncThreshold = config[@"syncThreshold"];
    }
    if (isNotNull(config[@"sync"])) {
        instance.syncEnabled = config[@"sync"];
    }
    if (config[@"httpHeaders"] != nil) {
        instance.httpHeaders = config[@"httpHeaders"];
    }
    // headers (alias of httpHeaders)
    if (config[@"headers"] != nil) {
        instance.httpHeaders = config[@"headers"];
    }
    // v3.3 Phase 2: HTTP transport
    if (isNotNull(config[@"httpMethod"])) {
        instance.httpMethod = [(NSString*)config[@"httpMethod"] uppercaseString];
    }
    if (isNotNull(config[@"syncHttpMethod"])) {
        // v5.0.1 — R14: 'GET' se RECHAZA en -validate:, igual que en ConfigMapper de Android (la
        // URL de sync se resuelve con location:nil, así que no se sustituye ningún placeholder por
        // posición: un 200 borraría el lote con cero datos). Aquí solo se normaliza.
        instance.syncHttpMethod = [(NSString*)config[@"syncHttpMethod"] uppercaseString];
    }
    if (isNotNull(config[@"httpMode"])) {
        instance.httpMode = [(NSString*)config[@"httpMode"] lowercaseString];
    }
    if (isNotNull(config[@"syncMode"])) {
        instance.syncMode = [(NSString*)config[@"syncMode"] lowercaseString];
    }
    if (config[@"queryParams"] != nil) {
        instance.queryParams = config[@"queryParams"];
    }
    if (isNotNull(config[@"showsBackgroundLocationIndicator"])) {
        instance._showsBackgroundLocationIndicator = config[@"showsBackgroundLocationIndicator"];
    }
    if (isNotNull(config[@"heartbeatInterval"])) {
        instance.heartbeatInterval = config[@"heartbeatInterval"];
    }
    if (isNotNull(config[@"mockLocationPolicy"])) {
        instance.mockLocationPolicy = [(NSString*)config[@"mockLocationPolicy"] lowercaseString];
    }
    if ([config[@"drivingEvents"] isKindOfClass:[NSDictionary class]]) {
        instance.drivingEvents = config[@"drivingEvents"];
    }
    if (isNotNull(config[@"includeBattery"])) {
        instance.includeBattery = config[@"includeBattery"];
    }
    // v4.5.4 provider hardening
    if (isNotNull(config[@"activityConfidenceThreshold"])) {
        instance.activityConfidenceThreshold = config[@"activityConfidenceThreshold"];
    }
    // v4.5.6 — D30: an explicit null must be able to switch the accuracy filter back off.
    // Previously `maxAcceptedAccuracy: null` was ignored and the previous value survived,
    // so once set the filter could never be disabled at runtime.
    if (config[@"maxAcceptedAccuracy"] != nil) {
        if (isNull(config[@"maxAcceptedAccuracy"])) {
            instance.maxAcceptedAccuracy = nil;
            instance.resetMaxAcceptedAccuracy = YES;
        } else {
            instance.maxAcceptedAccuracy = config[@"maxAcceptedAccuracy"];
        }
    }
    if (isNotNull(config[@"saveBatteryOnBackground"])) {
        instance._saveBatteryOnBackground = config[@"saveBatteryOnBackground"];
    }
    if (isNotNull(config[@"maxLocations"])) {
        instance.maxLocations = config[@"maxLocations"];
    }
    if (isNotNull(config[@"pauseLocationUpdates"])) {
        instance._pauseLocationUpdates = config[@"pauseLocationUpdates"];
    }
    if (isNotNull(config[@"locationProvider"])) {
        instance.locationProvider = config[@"locationProvider"];
    }
    if (config[@"postTemplate"] != nil) {
        instance._template = config[@"postTemplate"];
        instance.hasUserTemplate = YES;
    }
    // bodyTemplate (alias of postTemplate)
    if (config[@"bodyTemplate"] != nil) {
        instance._template = config[@"bodyTemplate"];
        instance.hasUserTemplate = YES;
    }

    return instance;
}

+ (instancetype) merge:(MAURConfig*)config withConfig:(MAURConfig*)newConfig
{
    if (config == nil) {
        return newConfig;
    }

    if (newConfig == nil) {
        return config;
    }
    
    MAURConfig *merger= [config copy];

    if ([newConfig hasStationaryRadius]) {
        merger.stationaryRadius = newConfig.stationaryRadius;
    }
    if ([newConfig hasDistanceFilter]) {
        merger.distanceFilter = newConfig.distanceFilter;
    }
    if ([newConfig hasDesiredAccuracy]) {
        merger.desiredAccuracy = newConfig.desiredAccuracy;
    }
    if ([newConfig hasDebug]) {
        merger._debug = newConfig._debug;
    }
    if ([newConfig hasActivityType]) {
        merger.activityType = newConfig.activityType;
    }
    if ([newConfig hasActivitiesInterval]) {
        merger.activitiesInterval = newConfig.activitiesInterval;
    }
    if ([newConfig hasStopOnTerminate]) {
        merger._stopOnTerminate = newConfig._stopOnTerminate;
    }
    if ([newConfig hasUrl]) {
        merger.url = newConfig.url;
    }
    if ([newConfig hasSyncUrl]) {
        merger.syncUrl = newConfig.syncUrl;
    }
    if ([newConfig hasSyncThreshold]) {
        merger.syncThreshold = newConfig.syncThreshold;
    }
    if ([newConfig hasSyncEnabled]) {
        merger.syncEnabled = [NSNumber numberWithBool:[newConfig syncEnabled]];
    }
    if ([newConfig hasHttpHeaders]) {
        merger.httpHeaders = newConfig.httpHeaders;
    }
    if (newConfig.httpMethod != nil) {
        merger.httpMethod = newConfig.httpMethod;
    }
    if (newConfig.syncHttpMethod != nil) {
        merger.syncHttpMethod = newConfig.syncHttpMethod;
    }
    if (newConfig.httpMode != nil) {
        merger.httpMode = newConfig.httpMode;
    }
    if (newConfig.syncMode != nil) {
        merger.syncMode = newConfig.syncMode;
    }
    if (newConfig.queryParams != nil) {
        merger.queryParams = newConfig.queryParams;
    }
    if ([newConfig hasShowsBackgroundLocationIndicator]) {
        merger._showsBackgroundLocationIndicator = newConfig._showsBackgroundLocationIndicator;
    }
    if (newConfig.heartbeatInterval != nil) {
        merger.heartbeatInterval = newConfig.heartbeatInterval;
    }
    if (newConfig.mockLocationPolicy != nil) {
        merger.mockLocationPolicy = newConfig.mockLocationPolicy;
    }
    if (newConfig.drivingEvents != nil) {
        merger.drivingEvents = newConfig.drivingEvents;
    }
    if (newConfig.includeBattery != nil) {
        merger.includeBattery = newConfig.includeBattery;
    }
    if (newConfig.activityConfidenceThreshold != nil) {
        merger.activityConfidenceThreshold = newConfig.activityConfidenceThreshold;
    }
    // v4.5.6 — D30: honour an explicit `maxAcceptedAccuracy: null` as "disable the filter".
    if (newConfig.resetMaxAcceptedAccuracy) {
        merger.maxAcceptedAccuracy = nil;
    } else if (newConfig.maxAcceptedAccuracy != nil) {
        merger.maxAcceptedAccuracy = newConfig.maxAcceptedAccuracy;
    }
    if ([newConfig hasSaveBatteryOnBackground]) {
        merger._saveBatteryOnBackground = newConfig._saveBatteryOnBackground;
    }
    if ([newConfig hasMaxLocations]) {
        merger.maxLocations = newConfig.maxLocations;
    }
    if ([newConfig hasPauseLocationUpdates]) {
        merger._pauseLocationUpdates = newConfig._pauseLocationUpdates;
    }
    if ([newConfig hasLocationProvider]) {
        merger.locationProvider = newConfig.locationProvider;
    }
    if ([newConfig hasTemplate]) {
        merger._template = newConfig._template;
        // v5.0.1 — B7: el flag viaja con el template.
        if (newConfig.hasUserTemplate) {
            merger.hasUserTemplate = YES;
        }
    }

    return merger;
}

-(id) copyWithZone: (NSZone *) zone
{
    MAURConfig *copy = [[[self class] allocWithZone: zone] init];
    if (copy) {
        copy.stationaryRadius = stationaryRadius;
        copy.distanceFilter = distanceFilter;
        copy.desiredAccuracy = desiredAccuracy;
        copy._debug = _debug;
        copy.activityType = activityType;
        copy.activitiesInterval = activitiesInterval;
        copy._stopOnTerminate = _stopOnTerminate;
        copy.url = url;
        copy.syncUrl = syncUrl;
        copy.syncThreshold = syncThreshold;
        copy.syncEnabled = syncEnabled;
        copy.httpHeaders = httpHeaders;
        copy.httpMethod = httpMethod;
        copy.syncHttpMethod = syncHttpMethod;
        copy.httpMode = httpMode;
        copy.syncMode = syncMode;
        copy.queryParams = queryParams;
        copy._showsBackgroundLocationIndicator = _showsBackgroundLocationIndicator;
        copy.heartbeatInterval = heartbeatInterval;
        copy.mockLocationPolicy = mockLocationPolicy;
        copy.drivingEvents = drivingEvents;
        copy.includeBattery = includeBattery;
        copy.activityConfidenceThreshold = activityConfidenceThreshold;
        copy.maxAcceptedAccuracy = maxAcceptedAccuracy;
        copy._saveBatteryOnBackground = _saveBatteryOnBackground;
        copy.maxLocations = maxLocations;
        copy._pauseLocationUpdates = _pauseLocationUpdates;
        copy.locationProvider = locationProvider;
        copy._template = _template;
        // v5.0.1 — B7: sin esto, el `[config copy]` de +merge: perdia el flag y getConfig()
        // volvia a devolver `null` en un postTemplate que el usuario si habia configurado.
        copy.hasUserTemplate = self.hasUserTemplate;
    }
    
    return copy;
}

- (BOOL) hasStationaryRadius
{
    return stationaryRadius != nil;
}

- (BOOL) hasDistanceFilter
{
    return distanceFilter != nil;
}

- (BOOL) hasDesiredAccuracy
{
    return desiredAccuracy != nil;
}

- (BOOL) hasDebug
{
    return _debug != nil;
}

- (BOOL) hasActivityType
{
    return activityType != nil;
}

- (BOOL) hasActivitiesInterval
{
    return activitiesInterval != nil;
}

- (BOOL) hasStopOnTerminate
{
    return _stopOnTerminate != nil;
}

- (BOOL) hasUrl
{
    return url != nil;
}

- (BOOL) hasValidUrl
{
    return url != nil && url.length > 0;
}

- (void) setUrl:(NSString*)newUrl
{
    if (newUrl == (id)[NSNull null]) {
        url = @"";
    } else {
        url = newUrl;
    }
}

- (NSString*) url
{
    if (url == nil) {
        url = @"";
    }
    return url;
}

- (BOOL) hasSyncUrl
{
    return syncUrl != nil;
}

- (BOOL) hasValidSyncUrl
{
    return syncUrl != nil && syncUrl.length > 0;
}

/**
 * v5.0.1 — R15 (paridad con Config.getEffectiveSyncUrl() de Android). Sin syncUrl, los POST
 * fallidos se marcaban SyncPending y ahí se quedaban para siempre: el único lector es -sync, que
 * abortaba justo por no haber syncUrl. Se acumulaban hasta que maxLocations los reciclaba, que es
 * lo contrario de lo que promete la documentación. `url` actúa de destino de reserva.
 */
- (NSString*) effectiveSyncUrl
{
    if (syncUrl != nil && syncUrl.length > 0) {
        return syncUrl;
    }
    return self.url;
}

- (BOOL) hasEffectiveSyncUrl
{
    NSString *effective = [self effectiveSyncUrl];
    return effective != nil && effective.length > 0;
}

- (void) setSyncUrl:(NSString*)newSyncUrl
{
    if (newSyncUrl == (id)[NSNull null]) {
        syncUrl = @"";
    } else {
        syncUrl = newSyncUrl;
    }
}

- (NSString*) syncUrl
{
    if (syncUrl == nil) {
        syncUrl = @"";
    }
    return syncUrl;
}

- (BOOL) hasSyncThreshold
{
    return syncThreshold != nil;
}

- (BOOL) hasSyncEnabled
{
    return syncEnabled != nil;
}

- (BOOL) syncEnabled
{
    return syncEnabled == nil ? YES : [syncEnabled boolValue];
}

- (BOOL) hasHttpHeaders
{
    return httpHeaders != nil;
}

- (void) setHttpHeaders:(NSMutableDictionary *)newHttpHeaders
{
    if (newHttpHeaders == (id)[NSNull null]) {
        httpHeaders = [[NSMutableDictionary alloc] init];
    } else {
        httpHeaders = newHttpHeaders;
    }
}

- (NSMutableDictionary *) httpHeaders
{
    if (httpHeaders == nil) {
        httpHeaders = [[NSMutableDictionary alloc] init];
    }
    return httpHeaders;
}

- (BOOL) hasSaveBatteryOnBackground
{
    return _saveBatteryOnBackground != nil;
}

- (BOOL) hasShowsBackgroundLocationIndicator
{
    return _showsBackgroundLocationIndicator != nil;
}

- (BOOL) showsBackgroundLocationIndicator
{
    return _showsBackgroundLocationIndicator != nil ? [_showsBackgroundLocationIndicator boolValue] : NO;
}

- (BOOL) hasMaxLocations
{
    return maxLocations != nil;
}

- (BOOL) hasPauseLocationUpdates
{
    return _pauseLocationUpdates != nil;
}

- (BOOL) hasLocationProvider
{
    return locationProvider != nil;
}

- (BOOL) hasTemplate
{
    return _template != nil;
}

- (void) set_template:(NSObject*)template
{
    if (template == (id)[NSNull null]) {
        _template = [MAURConfig getDefaultTemplate];
    } else {
        _template = template;
    }
}

- (NSObject*) _template{
    if (_template == nil) {
        _template = [MAURConfig getDefaultTemplate];
    }
    return _template;
}

- (BOOL) isDebugging
{
    return _debug.boolValue;
}

- (BOOL) stopOnTerminate
{
    return _stopOnTerminate.boolValue;
}

- (BOOL) saveBatteryOnBackground
{
    return _saveBatteryOnBackground.boolValue;
}

- (BOOL) pauseLocationUpdates
{
    return _pauseLocationUpdates.boolValue;
}

- (CLActivityType) decodeActivityType
{
    if ([activityType caseInsensitiveCompare:@"AutomotiveNavigation"] == NSOrderedSame) {
        return CLActivityTypeAutomotiveNavigation;
    }
    if ([activityType caseInsensitiveCompare:@"OtherNavigation"] == NSOrderedSame) {
        return CLActivityTypeOtherNavigation;
    }
    if ([activityType caseInsensitiveCompare:@"Fitness"] == NSOrderedSame) {
        return CLActivityTypeFitness;
    }

    return CLActivityTypeOther;
}

- (NSInteger) decodeDesiredAccuracy
{
    NSInteger desiredAccuracy = self.desiredAccuracy.integerValue;

    if (desiredAccuracy >= 1000) {
        return kCLLocationAccuracyKilometer;
    }
    if (desiredAccuracy >= 100) {
        return kCLLocationAccuracyHundredMeters;
    }
    if (desiredAccuracy >= 10) {
        return kCLLocationAccuracyNearestTenMeters;
    }
    if (desiredAccuracy >= 0) {
        return kCLLocationAccuracyBest;
    }

    return kCLLocationAccuracyHundredMeters;
}

+ (NSDictionary*) getDefaultTemplate
{
    return @{
             @"time": @"@time",
             @"accuracy": @"@accuracy",
             @"altitudeAccuracy": @"@altitudeAccuracy",
             @"speed": @"@speed",
             @"bearing": @"@bearing",
             @"altitude": @"@altitude",
             @"latitude": @"@latitude",
             @"longitude": @"@longitude",
             @"provider": @"@provider",          // v4.5.1 — was literal "provider" (bug)
             @"locationProvider": @"@locationProvider",
             @"radius": @"@radius",
             // v4.5.1 — README promete events/battery/isCharging en payload default. Sin esto,
             // el template default que se usa siempre que la app no configura postTemplate omitía
             // estos campos al serializar via toResultFromTemplate.
             @"events": @"@events",
             @"battery": @"@battery",
             @"isCharging": @"@isCharging",
             };
}

- (NSString*) getHttpHeadersAsString:(NSError * __autoreleasing *)outError;
{
    NSError *error = nil;
    NSString *httpHeadersString;
    
    if ([self hasHttpHeaders]) {
        NSData *jsonHttpHeaders = [NSJSONSerialization dataWithJSONObject:httpHeaders options:NSJSONWritingPrettyPrinted error:&error];
        if (jsonHttpHeaders) {
            httpHeadersString = [[NSString alloc] initWithData:jsonHttpHeaders encoding:NSUTF8StringEncoding];
        } else {
            if (outError != nil) {
                NSLog(@"Http headers serialization error: %@", error);
                *outError = error;
            }
        }
    }

    return httpHeadersString;
}

- (NSString*) getTemplateAsString:(NSError * __autoreleasing *)outError;
{
    NSError *error = nil;
    NSString *templateAsString;

    if ([self hasTemplate]) {
        NSData *jsonTemplate = [NSJSONSerialization dataWithJSONObject:_template options:0 error:&error];
        if (jsonTemplate) {
            templateAsString = [[NSString alloc] initWithData:jsonTemplate encoding:NSUTF8StringEncoding];
        } else {
            if (outError != nil) {
                NSLog(@"Template serialization error: %@", error);
                *outError = error;
            }
        }
    }

    return templateAsString;
}

- (NSDictionary*) toDictionary
{
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:10];
 
    if ([self hasActivityType]) [dict setObject:self.activityType forKey:@"activityType"];
    if ([self hasActivitiesInterval]) [dict setObject:self.activitiesInterval forKey:@"activitiesInterval"];
    if ([self hasUrl]) [dict setObject:self.url forKey:@"url"];
    if ([self hasSyncUrl]) [dict setObject:self.syncUrl forKey:@"syncUrl"];
    if ([self hasHttpHeaders]) [dict setObject:self.httpHeaders forKey:@"httpHeaders"];
    if (self.httpMethod != nil) [dict setObject:self.httpMethod forKey:@"httpMethod"];
    if (self.syncHttpMethod != nil) [dict setObject:self.syncHttpMethod forKey:@"syncHttpMethod"];
    if (self.httpMode != nil) [dict setObject:self.httpMode forKey:@"httpMode"];
    if (self.syncMode != nil) [dict setObject:self.syncMode forKey:@"syncMode"];
    if (self.queryParams != nil) [dict setObject:self.queryParams forKey:@"queryParams"];
    if ([self hasShowsBackgroundLocationIndicator]) [dict setObject:self._showsBackgroundLocationIndicator forKey:@"showsBackgroundLocationIndicator"];
    if (self.heartbeatInterval != nil) [dict setObject:self.heartbeatInterval forKey:@"heartbeatInterval"];
    if (self.mockLocationPolicy != nil) [dict setObject:self.mockLocationPolicy forKey:@"mockLocationPolicy"];
    if (self.drivingEvents != nil) [dict setObject:self.drivingEvents forKey:@"drivingEvents"];
    // v5.0.1 — B7: paridad de FORMA con ConfigMapper.toJSONObject() de Android, que siempre emite
    // includeBattery con su valor efectivo (true por defecto). iOS lo omitia si nunca se habia
    // fijado, asi que `if (cfg.includeBattery === false)` daba resultados opuestos por plataforma
    // aunque el comportamiento efectivo fuese el mismo (nil se trata como ON en el facade).
    [dict setObject:(self.includeBattery ?: @YES) forKey:@"includeBattery"];
    if (self.activityConfidenceThreshold != nil) [dict setObject:self.activityConfidenceThreshold forKey:@"activityConfidenceThreshold"];
    if (self.maxAcceptedAccuracy != nil) [dict setObject:self.maxAcceptedAccuracy forKey:@"maxAcceptedAccuracy"];
    if ([self hasStationaryRadius]) [dict setObject:self.stationaryRadius forKey:@"stationaryRadius"];
    if ([self hasDistanceFilter]) [dict setObject:self.distanceFilter forKey:@"distanceFilter"];
    if ([self hasDesiredAccuracy]) [dict setObject:self.desiredAccuracy forKey:@"desiredAccuracy"];
    if ([self hasDebug]) [dict setObject:self._debug forKey:@"debug"];
    if ([self hasStopOnTerminate]) [dict setObject:self._stopOnTerminate forKey:@"stopOnTerminate"];
    if ([self hasSyncThreshold]) [dict setObject:self.syncThreshold forKey:@"syncThreshold"];
    if ([self hasSyncEnabled]) [dict setObject:syncEnabled forKey:@"sync"];
    if ([self hasSaveBatteryOnBackground]) [dict setObject:self._saveBatteryOnBackground forKey:@"saveBatteryOnBackground"];
    if ([self hasMaxLocations]) [dict setObject:self.maxLocations forKey:@"maxLocations"];
    if ([self hasPauseLocationUpdates]) [dict setObject:self._pauseLocationUpdates forKey:@"pauseLocationUpdates"];
    if ([self hasLocationProvider]) [dict setObject:self.locationProvider forKey:@"locationProvider"];
    // v5.0.1 — B7: Android emite `postTemplate: null` cuando el usuario no configuro ninguno;
    // iOS devolvia SIEMPRE el template por defecto ya materializado, asi que comprobar
    // `cfg.postTemplate == null` para saber si se habia personalizado el payload fallaba en iOS.
    // Se decide con el flag, NO con el ivar: el getter -_template materializa el default y lo
    // invoca cualquiera (incluido -description, o sea el log de configure()), asi que mirar el
    // ivar daba una respuesta dependiente del timing — peor que ser consistentemente incorrecta.
    [dict setObject:(self.hasUserTemplate ? self._template : (NSObject *)[NSNull null])
             forKey:@"postTemplate"];

    return dict;
}

- (NSString *) description
{
    return [NSString stringWithFormat:@"Config: distanceFilter=%@ stationaryRadius=%@ desiredAccuracy=%@ activityType=%@ activitiesInterval=%@ isDebugging=%@ stopOnTerminate=%@ url=%@ syncThreshold=%@ maxLocations=%@ httpHeaders=%@ pauseLocationUpdates=%@ saveBatteryOnBackground=%@ locationProvider=%@ postTemplate=%@", self.distanceFilter, self.stationaryRadius, self.desiredAccuracy, self.activityType, self.activitiesInterval, self._debug, self._stopOnTerminate, self.url, self.syncThreshold, self.maxLocations, self.httpHeaders, self._pauseLocationUpdates, self._saveBatteryOnBackground, self.locationProvider, self._template];

}

@end

#pragma mark - v5.0.1 (B1) validacion, paridad con ConfigMapper.validate() de Android

static NSString * const MAURConfigErrorDomain = @"com.marianhello";

static NSError * MAURConfigError(NSString *message)
{
    return [NSError errorWithDomain:MAURConfigErrorDomain
                               code:1002 /* MAURBGConfigureError */
                           userInfo:@{ NSLocalizedDescriptionKey: message }];
}

/** nil pasa (semantica de actualizacion parcial); un valor presente debe estar en la lista. */
static BOOL MAURRequireOneOfString(NSString *name, NSString *value, NSArray *allowed, NSError * __autoreleasing *outError)
{
    if (value == nil) return YES;
    for (NSString *candidate in allowed) {
        if ([candidate caseInsensitiveCompare:value] == NSOrderedSame) return YES;
    }
    if (outError != NULL) {
        *outError = MAURConfigError([NSString stringWithFormat:@"%@ must be one of %@, got '%@'",
                                     name, [allowed componentsJoinedByString:@", "], value]);
    }
    return NO;
}

static BOOL MAURRequireNonNegative(NSString *name, NSNumber *value, NSError * __autoreleasing *outError)
{
    if (value == nil) return YES;
    if ([value doubleValue] >= 0) return YES;
    if (outError != NULL) {
        *outError = MAURConfigError([NSString stringWithFormat:@"%@ must be >= 0, got %@", name, value]);
    }
    return NO;
}

static BOOL MAURRequireRange(NSString *name, NSNumber *value, double min, double max, NSError * __autoreleasing *outError)
{
    if (value == nil) return YES;
    double v = [value doubleValue];
    if (v >= min && v <= max) return YES;
    if (outError != NULL) {
        *outError = MAURConfigError([NSString stringWithFormat:@"%@ must be between %g and %g, got %@",
                                     name, min, max, value]);
    }
    return NO;
}

@implementation MAURConfig (MAURValidation)

- (BOOL) validate:(NSError * __autoreleasing *)outError
{
    if (self.locationProvider != nil) {
        int p = [self.locationProvider intValue];
        if (p != 0 && p != 1 && p != 2) {
            if (outError != NULL) {
                *outError = MAURConfigError([NSString stringWithFormat:
                        @"locationProvider must be one of 0, 1, 2, got %@", self.locationProvider]);
            }
            return NO;
        }
    }

    if (!MAURRequireNonNegative(@"stationaryRadius", self.stationaryRadius, outError)) return NO;
    if (!MAURRequireNonNegative(@"distanceFilter", self.distanceFilter, outError)) return NO;
    if (!MAURRequireNonNegative(@"activitiesInterval", self.activitiesInterval, outError)) return NO;
    if (!MAURRequireNonNegative(@"heartbeatInterval", self.heartbeatInterval, outError)) return NO;
    // 0 se acepta a proposito en ambos: syncThreshold 0 = sincronizar en cada posicion,
    // maxLocations 0 = no persistir. v4 los aceptaba y hay apps en produccion con esos valores.
    if (!MAURRequireNonNegative(@"syncThreshold", self.syncThreshold, outError)) return NO;
    if (!MAURRequireNonNegative(@"maxLocations", self.maxLocations, outError)) return NO;
    if (!MAURRequireNonNegative(@"maxAcceptedAccuracy", self.maxAcceptedAccuracy, outError)) return NO;
    if (!MAURRequireRange(@"activityConfidenceThreshold", self.activityConfidenceThreshold, 0, 100, outError)) return NO;

    if (!MAURRequireOneOfString(@"httpMethod", self.httpMethod, @[@"POST", @"GET", @"PUT", @"PATCH"], outError)) return NO;
    // R14: GET fuera del sync — la URL se resuelve con location:nil, asi que no se sustituye
    // ningun placeholder por posicion y un 200 borraria el lote con cero datos.
    if (!MAURRequireOneOfString(@"syncHttpMethod", self.syncHttpMethod, @[@"POST", @"PUT", @"PATCH"], outError)) return NO;
    if (!MAURRequireOneOfString(@"httpMode", self.httpMode, @[@"batch", @"single"], outError)) return NO;
    if (!MAURRequireOneOfString(@"syncMode", self.syncMode, @[@"batch", @"single"], outError)) return NO;
    if (!MAURRequireOneOfString(@"mockLocationPolicy", self.mockLocationPolicy, @[@"allow", @"flag", @"drop"], outError)) return NO;

    return YES;
}

@end
