//
//  MAURBackgroundSync.h
//
//  Created by Marian Hello on 07/07/16.
//  Copyright © 2016 mauron85. All rights reserved.
//

#ifndef MAURBackgroundSync_h
#define MAURBackgroundSync_h

#import <Foundation/Foundation.h>

@class MAURBackgroundSync;

// v3.5 Phase 4: notification names for sync events. The plugin layer observes them
// via NSNotificationCenter to forward into JS as syncStart / syncSuccess / syncError / syncProgress.
extern NSString * _Nonnull const MAURBackgroundSyncDidStartNotification;
extern NSString * _Nonnull const MAURBackgroundSyncDidSucceedNotification;
extern NSString * _Nonnull const MAURBackgroundSyncDidFailNotification;
extern NSString * _Nonnull const MAURBackgroundSyncDidProgressNotification;

@protocol MAURBackgroundSyncDelegate <NSObject>

@optional
- (void)backgroundSyncRequestedAbortUpdates:(MAURBackgroundSync * _Nonnull)task;
- (void)backgroundSyncHttpAuthorizationUpdates:(MAURBackgroundSync * _Nonnull)task;
// v3.5 Phase 4
- (void)backgroundSyncStarted:(MAURBackgroundSync * _Nonnull)task;
- (void)backgroundSyncSucceeded:(MAURBackgroundSync * _Nonnull)task locationsSent:(NSInteger)locationsSent;
- (void)backgroundSyncFailed:(MAURBackgroundSync * _Nonnull)task httpStatus:(NSInteger)httpStatus message:(NSString * _Nullable)message;

@end

@interface MAURBackgroundSync : NSObject

@property (nonatomic, weak) id<MAURBackgroundSyncDelegate> _Nullable delegate;

- (instancetype) init;

/** v5.0 — D10/D19: identifier of the PROCESS-WIDE background NSURLSession used for uploads.
 *  iOS allows a single live session per identifier, so it is created once (see the dispatch_once
 *  in MAURBackgroundSync.m) and every instance reuses it. The host app needs this value to route
 *  -application:handleEventsForBackgroundURLSession:completionHandler: to this plugin. */
+ (NSString* _Nonnull) sessionIdentifier;

/** v5.0 — D10: hand over the completion handler UIKit passes to
 *  -application:handleEventsForBackgroundURLSession:completionHandler:. It is stored (copied) and
 *  invoked on the main queue from -URLSessionDidFinishEventsForBackgroundURLSession:, then
 *  released. Without this, uploads that complete while the app is suspended are never flushed and
 *  iOS eventually stops relaunching the app for background transfers.
 *  The class method exists for callers that do not hold a MAURBackgroundSync instance (the Cordova
 *  plugin does not own the uploader); the handler is process-wide, exactly like the session. */
- (void) setBackgroundSessionCompletionHandler:(void (^ _Nullable)(void))completionHandler;
+ (void) setBackgroundSessionCompletionHandler:(void (^ _Nullable)(void))completionHandler;

- (NSString*) status;
/** v4.5.6 — D10: adopts the upload tasks left over in the background NSURLSession by a previous
 *  process (app relaunched mid-upload) and resumes them. Was implemented but never declared, so
 *  no caller could reach it and orphaned uploads were stuck forever. Call once on start. */
- (void) start;
- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders;
- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders withMethod:(NSString * _Nullable)method;
/** v4.5.6 — D26: `mode` is the configured syncMode: "single" uploads one request per location,
 *  anything else keeps the historical batch behaviour (one request with a JSON array). */
- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders withMethod:(NSString * _Nullable)method withMode:(NSString * _Nullable)mode;
- (void) cancel;

@end

#endif /* MAURBackgroundSync_h */
