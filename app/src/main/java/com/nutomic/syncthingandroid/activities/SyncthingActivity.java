package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;

/**
 * Connects to {@link SyncthingService} and provides access to it.
 */
public abstract class SyncthingActivity extends ThemedAppCompatActivity implements ServiceConnection {

    private static final String TAG = "SyncthingActivity";

    private SyncthingService mSyncthingService;

    /**
     * Look for a Toolbar in the layout and bind it as the activity's actionbar with reasonable
     * defaults.
     *
     * The Toolbar must exist in the content view and have an id of R.id.toolbar. Trying to call
     * getSupportActionBar before this Activity's onPostCreate will cause a crash.
     */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }
        toolbar.setNavigationContentDescription(R.string.main_menu);
        toolbar.setNavigationIcon(R.drawable.baseline_arrow_back_24);
        toolbar.setTouchscreenBlocksFocus(false);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewGroup contentView = findViewById(android.R.id.content);
            if (contentView.getChildCount() > 0) {
                View topLevel = contentView.getChildAt(0);
                if (topLevel instanceof DrawerLayout) {
                    DrawerLayout drawerLayout = (DrawerLayout) topLevel;
                    ViewGroup verticalLayout = (ViewGroup) drawerLayout.getChildAt(0);
                    addSpacerIfNeeded(verticalLayout);
                } else if (topLevel instanceof ScrollView) {
                    ScrollView scrollView = (ScrollView) topLevel;
                    View child = scrollView.getChildAt(0);
                    if (child instanceof ViewGroup) {
                        addSpacerIfNeeded((ViewGroup) child); 
                    }
                } else {
                    addSpacerIfNeeded((ViewGroup) topLevel);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        unbindService(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, SyncthingService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        mSyncthingService = syncthingServiceBinder.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
    }

    /**
     * Returns service object (or null if not bound).
     */
    public SyncthingService getService() {
        return mSyncthingService;
    }

    /**
     * Returns RestApi instance, or null if SyncthingService is not yet connected.
     */
    public RestApi getApi() {
        return (getService() != null)
                ? getService().getApi()
                : null;
    }

    /**
     * Adds a top color bar (colorPrimary) to the given layout, matching the actionBarHeight.
     */
    public void addSpacerIfNeeded(ViewGroup parent) {
        View statusBarSpacer = new View(this);
        statusBarSpacer.setLayoutParams(
            new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getStatusBarHeight()
            )
        );
        statusBarSpacer.setBackgroundColor(
            ContextCompat.getColor(this, R.color.primary_dark)
        );
        parent.addView(statusBarSpacer, 0);
    }

    public final int getStatusBarHeight() {
        TypedValue typedValue = new TypedValue();
        int statusBarHeight = 0;
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            statusBarHeight = TypedValue.complexToDimensionPixelSize(
                    typedValue.data, getResources().getDisplayMetrics());
        }
        return statusBarHeight;
    }
}
