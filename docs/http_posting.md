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

When `option.syncUrl` is defined, all locations that fail to post locations will be coalesced and sent in some time later in a one single batch.
Batch sync takes place only when the number of failed-to-post locations reaches `option.syncThreshold`.
Locations are sent only in single batch, when the number of locations reaches `option.syncThreshold`. (No individual locations will be sent)

The request body of posted locations is always an array, even when only one location is sent.

Warning: `option.maxLocations` has to be larger than `option.syncThreshold`. It's recommended to be 2x larger. In any other case the location syncing might not work properly.
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

| Content-Type | Behavior |
|--------------|----------|
| **application/json** (default) | Body is sent as JSON. If you do not set `Content-Type` in `httpHeaders`, this is used. |
| **application/x-www-form-urlencoded** | The plugin converts the JSON body to form-encoded format (`key=value&key2=value2`). For an array of locations, it sends a single parameter `locations` with the URL-encoded JSON array. Use this when your server expects form data. |

Example with form encoding:

```javascript
BackgroundGeolocation.configure({
  url: 'https://api.example.com/locations',
  httpHeaders: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'Authorization': 'Bearer YOUR_TOKEN'
  }
});
```

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
