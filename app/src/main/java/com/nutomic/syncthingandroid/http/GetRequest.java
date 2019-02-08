package com.nutomic.syncthingandroid.http;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.android.volley.Request;
import com.google.common.base.Optional;

import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * Performs a GET request to the Syncthing API
 */
public class GetRequest extends ApiRequest {

    public static final String URI_CONFIG           = "/rest/system/config";
    public static final String URI_DEBUG            = "/rest/system/debug";
    public static final String URI_SYSTEM_DISCOVERY = "/rest/system/discovery";
    public static final String URI_VERSION          = "/rest/system/version";
    public static final String URI_SYSTEM_STATUS    = "/rest/system/status";
    public static final String URI_CONNECTIONS      = "/rest/system/connections";
    public static final String URI_DB_IGNORES       = "/rest/db/ignores";
    public static final String URI_DB_STATUS        = "/rest/db/status";
    public static final String URI_DEVICEID         = "/rest/svc/deviceid";
    public static final String URI_REPORT           = "/rest/svc/report";
    public static final String URI_EVENTS           = "/rest/events";
    public static final String URI_EVENTS_DISK      = "/rest/events/disk";

    public GetRequest(Context context, URL url, String path, String apiKey,
                      @Nullable Map<String, String> params, OnSuccessListener listener) {
        super(context, url, path, apiKey);
        Map<String, String> safeParams = Optional.fromNullable(params).or(Collections.emptyMap());
        Uri uri = buildUri(safeParams);
        connect(Request.Method.GET, uri, null, listener, null);
    }

}
