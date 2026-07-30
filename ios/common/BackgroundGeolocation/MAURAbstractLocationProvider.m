//
//  MAURAbstractLocationProvider.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 14/09/2016.
//  Copyright © 2016 mauron85. All rights reserved.
//

#import "MAURAbstractLocationProvider.h"
// v4.5.6 — D25: UNUserNotificationCenter replaces the UILocalNotification API (iOS 10+).
#import <UserNotifications/UserNotifications.h>

@implementation MAURAbstractLocationProvider {
    UILocalNotification *localNotification;
}

@synthesize delegate;

- (instancetype) init
{
    if( [self class] == [MAURAbstractLocationProvider class])
    {
        NSAssert(false, @"You cannot init this class directly. Instead, use a subclass e.g. DistanceFilterLocationProvider.h");
        return nil;
    }
    
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    // v4.5.6 — D25: kept only as the iOS 9 fallback path of -notify:.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    localNotification = [[UILocalNotification alloc] init];
    localNotification.timeZone = [NSTimeZone defaultTimeZone];
#pragma clang diagnostic pop

    return self;
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
                                                              withCompletionHandler:nil];
        return;
    }

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    localNotification.fireDate = [NSDate date];
    localNotification.alertBody = message;
    [[UIApplication sharedApplication] scheduleLocalNotification:localNotification];
#pragma clang diagnostic pop
}

- (void) onTerminate
{
    // override in sub class
}

- (void) onSwitchMode:(MAUROperationalMode)mode
{
    // override in sub class
}

@end
