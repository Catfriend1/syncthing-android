package com.nutomic.syncthingandroid.http;

import android.content.Context;
import android.net.Uri;

import com.android.volley.Request;

import java.net.URL;
import java.util.Collections;

public class PostConfigRequest extends ApiRequest {

    private static final String URI_CONFIG = "/rest/system/config";

    public PostConfigRequest(Context context, URL url, String apiKey, String config,
                             OnSuccessListener listener) {
        super(context, url, URI_CONFIG, apiKey);
        Uri uri = buildUri(Collections.emptyMap());
        connect(Request.Method.POST, uri, config, listener, null);
    }

}
