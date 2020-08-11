package com.nutomic.syncthingandroid.http;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.google.common.base.Optional;

import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class PostRequest extends ApiRequest {

    public static final String URI_DB_IGNORES       = "/rest/db/ignores";
    public static final String URI_DB_OVERRIDE      = "/rest/db/override";
    public static final String URI_DB_REVERT        = "/rest/db/revert";
    public static final String URI_DB_SCAN          = "/rest/db/scan";
    public static final String URI_SYSTEM_CONFIG    = "/rest/system/config";
    public static final String URI_SYSTEM_SHUTDOWN  = "/rest/system/shutdown";

    public PostRequest(Context context, URL url, String path, String apiKey,
        	           @Nullable Map<String, String> params, @Nullable String postBody,
                       OnSuccessListener listener) {
        super(context, url, path, apiKey);
        Map<String, String> safeParams = Optional.fromNullable(params).or(Collections.emptyMap());
        Uri uri = buildUri(safeParams);
        connect(Request.Method.POST, uri, postBody, listener, null);
    }

}
