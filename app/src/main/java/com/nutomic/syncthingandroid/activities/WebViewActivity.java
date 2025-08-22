package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.util.Util;

/**
 * Holds a WebView that shows the web ui of the local syncthing instance.
 */
public class WebViewActivity extends SyncthingActivity {

    public static final String EXTRA_WEB_URL =
            ".activities.WebViewActivity.WEB_URL";

    private static final String TAG = "WebViewActivity";

    private AlertDialog mSecurityNoticeDialog;
    private Boolean sslNoticeUserDecision = false;

    private WebView mWebView;
    private View mLoadingView;

    private Boolean isRunningOnTV = false;
    private String webPageUrl = "";

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mWebView.canGoBack()) {
                mWebView.goBack();
            } else {
                finish();
            }
        }
    };

    /**
     * Hides the loading screen and shows the WebView once it is fully loaded.
     */
    private final WebViewClient mWebViewClient = new WebViewClient() {

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (sslNoticeUserDecision) {
                handler.proceed();
                return;
            }
            final DialogInterface.OnClickListener listener = (dialog, which) -> {
                try {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            handler.proceed();
                            sslNoticeUserDecision = true;
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            handler.cancel();
                            if (isRunningOnTV) {
                                // Finish as there is no other way to display the website.
                                finish();
                            }
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onReceivedSslError:OnClickListener", e);
                }
            };
            mSecurityNoticeDialog = new AlertDialog.Builder(WebViewActivity.this)
                .setTitle(R.string.security_notice)
                .setMessage(getString(R.string.ssl_cert_invalid_text, webPageUrl))
                .setPositiveButton(R.string.cont, listener)
                .setNegativeButton(R.string.cancel_title, listener)
                .show();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            mWebView.setVisibility(View.VISIBLE);
            mLoadingView.setVisibility(View.GONE);
        }
    };

    /**
     * Initialize WebView.
     */
    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isRunningOnTV = Util.isRunningOnTV(WebViewActivity.this);
        setContentView(R.layout.activity_web_gui);

        if (!getIntent().hasExtra(EXTRA_WEB_URL)) {
            Log.e(TAG, "EXTRA_WEB_URL not set.");
            finish();
        }
        webPageUrl = getIntent().getStringExtra(EXTRA_WEB_URL);

        mLoadingView = findViewById(R.id.loading);
        ((TextView) findViewById(R.id.loading_text)).setText(getString(R.string.web_page_loading, webPageUrl));
        mWebView = findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.clearCache(true);
        if (mWebView.getUrl() == null) {
            mWebView.stopLoading();
            mWebView.loadUrl(webPageUrl);
        }
        
        // Register the OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Util.dismissDialogSafe(mSecurityNoticeDialog, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.openBrowser).setVisible(!isRunningOnTV);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.openBrowser) {// This can only be triggered on a non-TV device.
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webPageUrl)));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        mWebView.onPause();
        mWebView.pauseTimers();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mWebView.resumeTimers();
        mWebView.onResume();
    }

    @Override
    protected void onDestroy() {
        mWebView.destroy();
        mWebView = null;
        super.onDestroy();
    }
}
