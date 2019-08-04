package com.nutomic.syncthingandroid.http;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.common.base.Optional;

import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class ImageGetRequest extends ApiRequest {

    public static final String QR_CODE_GENERATOR = "/qr/";

    public ImageGetRequest(Context context, URL url, String path, String apiKey,
                           @Nullable Map<String, String> params,
                           OnImageSuccessListener onSuccessListener, OnErrorListener onErrorListener) {
        super(context, url, path, apiKey);
        Map<String, String> safeParams = Optional.fromNullable(params).or(Collections.emptyMap());
        Uri uri = buildUri(safeParams);
        makeImageRequest(uri, onSuccessListener, onErrorListener);
    }
}
