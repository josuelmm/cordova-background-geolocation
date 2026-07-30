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

    BOOL singleMode = (mode != nil && [@"single" isEqualToString:[mode lowercaseString]]);

    if (singleMode) {
        if ([locations count] == 0) {
            return;
        }
        for (MAURLocation *location in locations) {
            id payload = [location toResultFromTemplate:locationTemplate];
            NSArray *ids = (location.locationId != nil) ? @[location.locationId] : nil;
            [self upload:payload
                   toUrl:url
         withHttpHeaders:httpHeaders
              withMethod:method
          locationsCount:1
             locationIds:ids];
        }
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
    NSError *error = nil;
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:jsonPayload options:0 error:&error];
    if (jsonData == nil) {
        DDLogError(@"Sync payload serialization failed: %@", error.localizedDescription);
        return;
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
    NSString *fileName = [NSString stringWithFormat:@"locations_%@_%lu.json",
                          [dateFormatter stringFromDate:[NSDate date]], (unsigned long)sequence];
    NSURL *jsonUrl = [NSURL fileURLWithPath:[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0] stringByAppendingPathComponent:fileName]];
    [jsonData writeToFile:jsonUrl.path atomically:NO];
    uint64_t bytesTotalForThisFile = [[[NSFileManager defaultManager] attributesOfItemAtPath:jsonUrl.path error:nil] fileSize];

    NSMutableURLRequest *request = [[NSMutableURLRequest alloc] initWithURL:[NSURL URLWithString:url]];
    NSString *resolvedMethod = (method != nil && method.length > 0) ? [method uppercaseString] : @"POST";
    [request setHTTPMethod:resolvedMethod];
    [request setTimeoutInterval:120]; // Prevents sync from hanging indefinitely if server does not respond
    [request setValue:[NSString stringWithFormat:@"%llu", bytesTotalForThisFile] forHTTPHeaderField:@"Content-Length"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];

    if (httpHeaders != nil) {
        for(id key in httpHeaders) {
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

    [task resume];

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
