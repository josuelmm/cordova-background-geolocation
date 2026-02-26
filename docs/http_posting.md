---
layout: default
title: HTTP Location Posting
nav_order: 6
---

# HTTP Location Posting

All locations updates are recorded in the local db at all times. When the App is in foreground or background, in addition to storing location in local db,
the location callback function is triggered. The number of locations stored in db is limited by `option.maxLocations` and never exceeds this number.
Instead, old locations are replaced by new ones.

When `option.url` is defined, each location is also immediately posted to url defined by `option.url`.
If the post is successful, the location is marked as deleted in local db.

When `option.syncUrl` is defined, all locations that fail to post to `url` (or that are only queued for sync) are coalesced and sent later in batch. Batch sync runs automatically when the number of pending locations reaches `option.syncThreshold` (default 100). You can also trigger sync on demand with **`forceSync()`** (see [Sync queue](#sync-queue-getpendingsynccount-forcesync-clearsync) below).

**Option `sync`** (default `true`): When set to `false`, automatic sync and `forceSync()` do nothing; locations are still stored and can be synced later by setting `sync: true` and calling `forceSync()` if needed.

The request body of posted locations is always an array when using **application/json**. When using **application/x-www-form-urlencoded**, batch sync sends **one POST per location** (same flat format as real-time) so the same server endpoint can accept both; see [Content-Type and batch sync](#content-type-and-batch-sync).

**Warning:** `option.maxLocations` must be larger than `option.syncThreshold` (recommended 2×). Otherwise location syncing may not work properly.
{: .bg-yellow-000}

## HTTP headers (two ways)

Headers are sent with every POST to `url` and with every batch POST to `syncUrl`. You can set them in two ways:

1. **Static** — Set `httpHeaders` once in `configure()`. The same headers are used for all requests (e.g. API key, `Content-Type`, `Authorization`).
2. **Dynamic** — When your server responds with **401 Unauthorized**, the plugin emits the `http_authorization` event. In the listener you can refresh your token (or other headers) and call `configure({ httpHeaders: { ... } })` again. The next requests will use the new headers (e.g. a new `Authorization` bearer token).

Example (static + dynamic):

```javascript
BackgroundGeolocation.configure({
  url: 'https://api.example.com/locations',
  httpHeaders: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + initialToken
  }
});

BackgroundGeolocation.on('http_authorization', function () {
  // Refresh token and update headers for subsequent requests
  BackgroundGeolocation.configure({
    httpHeaders: { 'Authorization': 'Bearer ' + newToken }
  });
});
```

## Content-Type (two options)

You can set the `Content-Type` header in `httpHeaders`. The plugin supports two values; it sends the body in the matching format.

| Content-Type | Real-time POST to `url` | Batch sync to `syncUrl` |
|--------------|-------------------------|--------------------------|
| **application/json** (default) | One location as JSON object (or single-element array). | One POST with JSON array of all locations. |
| **application/x-www-form-urlencoded** | One POST per location: flat `key=value&key2=value2`. | **One POST per location** with the same flat format, so the same endpoint accepts both. |

**Content-Type and batch sync:** If your server only accepts form-encoded key-value pairs (e.g. `altitude=1020&lat=3.39&lon=-76.52&...`), use `Content-Type: application/x-www-form-urlencoded`. The plugin then sends **one request per location** during batch sync (same as real-time), so you do not need a different endpoint or to parse a `locations=[...]` parameter.

Example with form encoding:

```javascript
BackgroundGeolocation.configure({
  url: 'https://api.example.com/locations',
  syncUrl: 'https://api.example.com/locations',
  httpHeaders: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'Authorization': 'Bearer YOUR_TOKEN'
  }
});
```

## Sync queue: getPendingSyncCount, forceSync, clearSync

When using `syncUrl`, you can manage the pending sync queue from your app:

- **`getPendingSyncCount()`** — Returns the number of locations waiting to be sent to `syncUrl`. Use it to show “X locations pending” or to enable/disable a “Sync now” or “Clear queue” button.
- **`forceSync()`** — Sends all pending locations to `syncUrl` immediately (ignores `syncThreshold`). No-op if `sync: false` in config.
- **`clearSync()`** — Discards all pending locations; they will not be sent. Use for a “Clear queue” or “Discard” action.

Example:

```javascript
BackgroundGeolocation.getPendingSyncCount()
  .then(function (count) {
    console.log('Pending to sync:', count);
  });

BackgroundGeolocation.forceSync().then(function () {
  console.log('Sync completed');
});

BackgroundGeolocation.clearSync().then(function () {
  console.log('Queue cleared');
});
```

Full method signatures and options: [API — forceSync, clearSync, getPendingSyncCount](api).

## Custom post template

With `option.postTemplate` it is possible to specify which location properties should be posted to `option.url` or `option.syncUrl`. This can be useful to reduce
the number of bytes sent "over the wire".

All wanted location properties have to be prefixed with `@`. For all available properties check [Location event](events#location-event).

Two forms are supported:

### jsonObject

```javascript
BackgroundGeolocation.configure({
  postTemplate: {
    lat: '@latitude',
    lon: '@longitude',
    foo: 'bar' // you can also add your own properties
  }
});
```

### jsonArray

```javascript
BackgroundGeolocation.configure({
  postTemplate: ['@latitude', '@longitude', 'foo', 'bar']
});
```

**Note:** Keep in mind that all locations (even a single one) will be sent as an array of object(s), when postTemplate is `jsonObject` and array of array(s) for `jsonArray`!
