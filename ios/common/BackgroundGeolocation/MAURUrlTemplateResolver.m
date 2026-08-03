//
//  MAURUrlTemplateResolver.m
//  BackgroundGeolocation
//

#import "MAURUrlTemplateResolver.h"
#import "MAURLocation.h"

@implementation MAURUrlTemplateResolver

+ (NSString *)resolve:(NSString *)urlTemplate
             location:(MAURLocation *)location
          queryParams:(NSDictionary *)queryParams
{
    if (urlTemplate == nil || urlTemplate.length == 0) return urlTemplate;

    NSDictionary *ctx = [self buildContext:location queryParams:queryParams];

    NSError *error = nil;
    NSRegularExpression *regex = [NSRegularExpression
        regularExpressionWithPattern:@"\\{([a-zA-Z0-9_]+)\\}"
        options:0
        error:&error];
    if (regex == nil) return urlTemplate;

    NSMutableString *result = [NSMutableString stringWithString:urlTemplate];
    NSArray *matches = [regex matchesInString:urlTemplate
                                      options:0
                                        range:NSMakeRange(0, urlTemplate.length)];

    // Iterate in reverse so ranges don't shift.
    for (NSTextCheckingResult *match in [matches reverseObjectEnumerator]) {
        NSString *key = [urlTemplate substringWithRange:[match rangeAtIndex:1]];
        NSString *value = ctx[key];
        if (value != nil) {
            NSString *encoded = [self urlEncode:value];
            [result replaceCharactersInRange:[match range] withString:encoded];
        }
        // else: leave the placeholder as-is.
    }
    return result;
}

+ (NSDictionary *)buildContext:(MAURLocation *)loc queryParams:(NSDictionary *)queryParams
{
    NSMutableDictionary *ctx = [NSMutableDictionary dictionary];

    // queryParams first; location-derived values can override below.
    if (queryParams != nil) {
        for (id key in queryParams) {
            id value = queryParams[key];
            if (value != nil && value != [NSNull null]) {
                ctx[key] = [NSString stringWithFormat:@"%@", value];
            }
        }
    }

    if (loc != nil) {
        if (loc.latitude != nil) {
            ctx[@"latitude"] = [loc.latitude stringValue];
            ctx[@"lat"] = [loc.latitude stringValue];
        }
        if (loc.longitude != nil) {
            ctx[@"longitude"] = [loc.longitude stringValue];
            ctx[@"lon"] = [loc.longitude stringValue];
        }
        if (loc.time != nil) {
            long long ms = (long long)([loc.time timeIntervalSince1970] * 1000.0);
            ctx[@"time"] = [NSString stringWithFormat:@"%lld", ms];
            ctx[@"timestamp"] = [NSString stringWithFormat:@"%lld", ms];
            ctx[@"timestamp_iso"] = [self isoUtc:loc.time];
        }
        if (loc.speed != nil) {
            ctx[@"speed"] = [loc.speed stringValue];
            // v5.0.1 — paridad con UrlTemplateResolver de Android: {is_moving} está documentado
            // en docs/api.md y en iOS salía literal (`moving={is_moving}`) porque nadie lo
            // ponía en el contexto. Mismo umbral que Android: 0.5 m/s ~ paso de peatón.
            ctx[@"is_moving"] = ([loc.speed doubleValue] > 0.5) ? @"true" : @"false";
        }
        if (loc.altitude != nil) ctx[@"altitude"] = [loc.altitude stringValue];
        if (loc.heading != nil) ctx[@"bearing"] = [loc.heading stringValue];
        if (loc.accuracy != nil) ctx[@"accuracy"] = [loc.accuracy stringValue];
        if (loc.provider != nil) ctx[@"provider"] = loc.provider;
    }

    return ctx;
}

+ (NSString *)urlEncode:(NSString *)s
{
    if (s == nil) return @"";
    // v5.0.1 — B6: paridad con UrlTemplateResolver de Android, que usa URLEncoder y por tanto
    // escapa TODO lo que no sea `[A-Za-z0-9-_.*]`. URLQueryAllowedCharacterSet dejaba pasar
    // `/ = ; : ? @ , $`, así que `queryParams: {id: 'flota/AB-12'}` viajaba como `id=flota/AB-12`
    // y algunos proxies lo interpretaban como un cambio de ruta.
    //
    // El conjunto se declara CARÁCTER A CARÁCTER a propósito. `+alphanumericCharacterSet` NO es
    // `[A-Za-z0-9]`: son las categorías Unicode L*, M* y N*, así que dejaba pasar sin escapar
    // `é ñ á 中`… El `NSURL URLWithString:` resultante es **nil**, y `requestWithURL:nil` lanza
    // NSInvalidArgumentException en cada posición. Java sí los codifica en UTF-8, que es lo que
    // hace ahora este conjunto explícito.
    static NSCharacterSet *allowed = nil;
    static dispatch_once_t allowedOnce;
    dispatch_once(&allowedOnce, ^{
        allowed = [NSCharacterSet characterSetWithCharactersInString:
                   @"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.*"];
    });
    NSString *enc = [s stringByAddingPercentEncodingWithAllowedCharacters:allowed];
    return enc != nil ? enc : s;
}

+ (NSString *)isoUtc:(NSDate *)date
{
    static NSDateFormatter *fmt = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        fmt = [[NSDateFormatter alloc] init];
        fmt.dateFormat = @"yyyy-MM-dd'T'HH:mm:ss'Z'";
        fmt.timeZone = [NSTimeZone timeZoneWithAbbreviation:@"UTC"];
        fmt.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    });
    return [fmt stringFromDate:date];
}

@end
