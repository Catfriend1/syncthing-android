package com.nutomic.syncthingandroid.http;


import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public abstract class ApiRequest {

    private static final String TAG = "ApiRequest";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * The name of the HTTP header used for the syncthing API key.
     */
    private static final String HEADER_API_KEY = "X-API-Key";

    public interface OnSuccessListener {
        void onSuccess(String result);
    }

    public interface OnImageSuccessListener {
        void onImageSuccess(Bitmap result);
    }

    public interface OnErrorListener {
        void onError(VolleyError error);
    }

    private static RequestQueue sVolleyQueue;

    private RequestQueue getVolleyQueue() {
        if (sVolleyQueue == null) {
            Context context = mContext.getApplicationContext();
            sVolleyQueue = Volley.newRequestQueue(context, new NetworkStack());
        }
        return sVolleyQueue;
    }

    private final Context mContext;
    private final URL mUrl;
    private final String mPath;
    private final String mApiKey;

    ApiRequest(Context context, URL url, String path, String apiKey) {
        mContext = context;
        mUrl           = url;
        mPath          = path;
        mApiKey        = apiKey;
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(context);
    }

    Uri buildUri(Map<String, String> params) {
        Uri.Builder uriBuilder = Uri.parse(mUrl.toString())
                .buildUpon()
                .path(mPath);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        return uriBuilder.build();
    }

    /**
     * Opens the connection, then returns success status and response string.
     */
    void connect(int requestMethod, Uri uri, @Nullable String requestBody,
                 @Nullable OnSuccessListener listener, @Nullable OnErrorListener errorListener) {
        /*
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, "Performing request to " + uri.toString());
        }
        */
        StringRequest request = new StringRequest(requestMethod, uri.toString(), reply -> {
            if (listener != null) {
                listener.onSuccess(reply);
            }
        }, error -> {
            if (errorListener != null) {
                errorListener.onError(error);
            } else {
                int statusCode = 0;
                if (error.networkResponse != null) {
                    statusCode = error.networkResponse.statusCode;
                }
                Log.w(TAG, "Request to " + uri + " failed, code=" + statusCode + ", msg=" + error.getMessage());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return ImmutableMap.of(HEADER_API_KEY, mApiKey);
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return Optional.fromNullable(requestBody).transform(String::getBytes).orNull();
            }
        };

        // Some requests seem to be slow or fail, make sure this doesn't break the app
        // (eg if an event request fails, new event requests won't be triggered).
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 5,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        getVolleyQueue().add(request);
    }

    /**
     * Opens the connection, then returns success status and response bitmap.
     */
    void makeImageRequest(Uri uri, @Nullable OnImageSuccessListener imageListener,
                          @Nullable OnErrorListener errorListener) {
        ImageRequest imageRequest =  new ImageRequest(uri.toString(), bitmap -> {
            if (imageListener != null) {
                imageListener.onImageSuccess(bitmap);
            }
        }, 0, 0, ImageView.ScaleType.CENTER, Bitmap.Config.RGB_565, volleyError -> {
            if(errorListener != null) {
                errorListener.onError(volleyError);
            }
            Log.d(TAG, "onErrorResponse: " + volleyError);
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return ImmutableMap.of(HEADER_API_KEY, mApiKey);
            }
        };

        getVolleyQueue().add(imageRequest);
    }

    /**
     * Extends {@link HurlStack}, uses {@link #getSslSocketFactory()} and disables hostname
     * verification.
     */
    private class NetworkStack extends HurlStack {

        public NetworkStack() {
            super(null, getSslSocketFactory());
        }
        @Override
        protected HttpURLConnection createConnection(URL url) throws IOException {
            if (mUrl.toString().startsWith("https://")) {
                HttpsURLConnection connection = (HttpsURLConnection) super.createConnection(url);
                connection.setHostnameVerifier((hostname, session) -> true);
                return connection;
            }
            return super.createConnection(url);
        }
    }

    private SSLSocketFactory getSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            File httpsCertPath = Constants.getHttpsCertFile(mContext);
            sslContext.init(null, new TrustManager[]{new SyncthingTrustManager(httpsCertPath)},
                    new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            Log.w(TAG, e);
            return null;
        }
    }
}
