package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.function.Consumer;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.fragments.DeviceListFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.Util;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static java.lang.Math.min;

/**
 * Shows {@link FolderListFragment} and
 * {@link DeviceListFragment} in different tabs, and
 * {@link DrawerFragment} in the navigation drawer.
 */
public class MainActivity extends StateDialogActivity
        implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "MainActivity";
    private static final String IS_SHOWING_RESTART_DIALOG = "RESTART_DIALOG_STATE";
    private static final String BATTERY_DIALOG_DISMISSED = "BATTERY_DIALOG_STATE";
    private static final String IS_QRCODE_DIALOG_DISPLAYED = "QRCODE_DIALOG_STATE";
    private static final String QRCODE_BITMAP_KEY = "QRCODE_BITMAP";
    private static final String DEVICEID_KEY = "DEVICEID";

    /**
     * Time after first start when usage reporting dialog should be shown.
     *
     * @see #showUsageReportingDialog()
     */
    private static final long USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(3);

    private AlertDialog mBatteryOptimizationsDialog;
    private AlertDialog mQrCodeDialog;
    private Dialog mRestartDialog;

    private boolean mBatteryOptimizationDialogDismissed;

    private ViewPager mViewPager;

    private FolderListFragment mFolderListFragment;
    private DeviceListFragment mDeviceListFragment;
    private DrawerFragment     mDrawerFragment;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout          mDrawerLayout;
    @Inject SharedPreferences mPreferences;

    /**
     * Handles various dialogs based on current state.
     */
    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        switch (currentState) {
            case STARTING:
                break;
            case ACTIVE:
                getIntent().putExtra(this.EXTRA_KEY_GENERATION_IN_PROGRESS, false);
                showBatteryOptimizationDialogIfNecessary();
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                mDrawerFragment.requestGuiUpdate();

                // Check if the usage reporting minimum delay passed by.
                Boolean usageReportingDelayPassed = (new Date().getTime() > getFirstStartTime() + USAGE_REPORTING_DIALOG_DELAY);
                RestApi restApi = getApi();
                if (usageReportingDelayPassed && restApi != null && !restApi.isUsageReportingDecided()) {
                    showUsageReportingDialog(restApi);
                }
                break;
            case ERROR:
                finish();
                break;
            case DISABLED:
                break;
        }
    }

    private void showBatteryOptimizationDialogIfNecessary() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean dontShowAgain = mPreferences.getBoolean("battery_optimization_dont_show_again", false);
        if (dontShowAgain || mBatteryOptimizationsDialog != null ||
                pm.isIgnoringBatteryOptimizations(getPackageName()) ||
                mBatteryOptimizationDialogDismissed) {
            return;
        }

        mBatteryOptimizationsDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_disable_battery_optimization_title)
                .setMessage(R.string.dialog_disable_battery_optimization_message)
                .setPositiveButton(R.string.dialog_disable_battery_optimization_turn_off, (d, i) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        // Some devices dont seem to support this request (according to Google Play
                        // crash reports).
                        Log.w(TAG, "Request ignore battery optimizations not supported", e);
                        Toast.makeText(this, R.string.dialog_disable_battery_optimizations_not_supported, Toast.LENGTH_LONG).show();
                        mPreferences.edit().putBoolean("battery_optimization_dont_show_again", true).apply();
                    }
                })
                .setNeutralButton(R.string.dialog_disable_battery_optimization_later, (d, i) -> mBatteryOptimizationDialogDismissed = true)
                .setNegativeButton(R.string.dialog_disable_battery_optimization_dont_show_again, (d, i) ->
                        mPreferences.edit().putBoolean("battery_optimization_dont_show_again", true).apply())
                .setOnCancelListener(d -> mBatteryOptimizationDialogDismissed = true)
                .show();
    }

    /**
     * Returns the unix timestamp at which the app was first installed.
     */
    private long getFirstStartTime() {
        PackageManager pm = getPackageManager();
        long firstInstallTime = 0;
        try {
            firstInstallTime = pm.getPackageInfo(getPackageName(), 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "This should never happen", e);
        }
        return firstInstallTime;
    }

    private final FragmentPagerAdapter mSectionsPagerAdapter =
            new FragmentPagerAdapter(getSupportFragmentManager()) {

                @Override
                public Fragment getItem(int position) {
                    switch (position) {
                        case 0:
                            return mFolderListFragment;
                        case 1:
                            return mDeviceListFragment;
                        default:
                            return null;
                    }
                }

                @Override
                public int getCount() {
                    return 2;
                }

                @Override
                public CharSequence getPageTitle(int position) {
                    switch (position) {
                        case 0:
                            return getResources().getString(R.string.folders_fragment_title);
                        case 1:
                            return getResources().getString(R.string.devices_fragment_title);
                        default:
                            return String.valueOf(position);
                    }
                }
            };

    /**
     * Initializes tab navigation.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        setContentView(R.layout.activity_main);
        mDrawerLayout = findViewById(R.id.drawer_layout);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState != null) {
            mFolderListFragment = (FolderListFragment) fm.getFragment(
                    savedInstanceState, FolderListFragment.class.getName());
            mDeviceListFragment = (DeviceListFragment) fm.getFragment(
                    savedInstanceState, DeviceListFragment.class.getName());
            mDrawerFragment = (DrawerFragment) fm.getFragment(
                    savedInstanceState, DrawerFragment.class.getName());
        } else {
            mFolderListFragment = new FolderListFragment();
            mDeviceListFragment = new DeviceListFragment();
            mDrawerFragment = new DrawerFragment();
        }

        mViewPager = findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        TabLayout tabLayout = findViewById(R.id.tabContainer);
        tabLayout.setupWithViewPager(mViewPager);
        if (savedInstanceState != null) {
            mViewPager.setCurrentItem(savedInstanceState.getInt("currentTab"));
            if (savedInstanceState.getBoolean(IS_SHOWING_RESTART_DIALOG)){
                showRestartDialog();
            }
            mBatteryOptimizationDialogDismissed = savedInstanceState.getBoolean(BATTERY_DIALOG_DISMISSED);
            if(savedInstanceState.getBoolean(IS_QRCODE_DIALOG_DISPLAYED)) {
                showQrCodeDialog(savedInstanceState.getString(DEVICEID_KEY), savedInstanceState.getParcelable(QRCODE_BITMAP_KEY));
            }
        }

        fm.beginTransaction().replace(R.id.drawer, mDrawerFragment).commit();
        mDrawerToggle = new Toggle(this, mDrawerLayout);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        setOptimalDrawerWidth(findViewById(R.id.drawer));

        // SyncthingService needs to be started from this activity as the user
        // can directly launch this activity from the recent activity switcher.
        startService(new Intent(this, SyncthingService.class));

        onNewIntent(getIntent());
    }

    @Override
    public void onResume() {
        // Check if storage permission has been revoked at runtime.
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED)) {
            startActivity(new Intent(this, FirstStartActivity.class));
            this.finish();
        }
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService mSyncthingService = getService();
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnServiceStateChangeListener(this);
            mSyncthingService.unregisterOnServiceStateChangeListener(mFolderListFragment);
            mSyncthingService.unregisterOnServiceStateChangeListener(mDeviceListFragment);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        SyncthingService syncthingService = syncthingServiceBinder.getService();
        syncthingService.registerOnServiceStateChangeListener(this);
        syncthingService.registerOnServiceStateChangeListener(mFolderListFragment);
        syncthingService.registerOnServiceStateChangeListener(mDeviceListFragment);
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        FragmentManager fm = getSupportFragmentManager();
        Consumer<Fragment> putFragment = fragment -> {
            if (fragment != null && fragment.isAdded()) {
                fm.putFragment(outState, fragment.getClass().getName(), fragment);
            }
        };
        putFragment.accept(mFolderListFragment);
        putFragment.accept(mDeviceListFragment);
        putFragment.accept(mDrawerFragment);

        outState.putInt("currentTab", mViewPager.getCurrentItem());
        outState.putBoolean(BATTERY_DIALOG_DISMISSED, mBatteryOptimizationsDialog == null || !mBatteryOptimizationsDialog.isShowing());
        outState.putBoolean(IS_SHOWING_RESTART_DIALOG, mRestartDialog != null && mRestartDialog.isShowing());
        if(mQrCodeDialog != null && mQrCodeDialog.isShowing()) {
            outState.putBoolean(IS_QRCODE_DIALOG_DISPLAYED, true);
            ImageView qrCode = mQrCodeDialog.findViewById(R.id.qrcode_image_view);
            TextView deviceID = mQrCodeDialog.findViewById(R.id.device_id);
            outState.putParcelable(QRCODE_BITMAP_KEY, ((BitmapDrawable) qrCode.getDrawable()).getBitmap());
            outState.putString(DEVICEID_KEY, deviceID.getText().toString());
        }
        Util.dismissDialogSafe(mRestartDialog, this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        mDrawerToggle.syncState();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    public void showRestartDialog(){
        mRestartDialog = createRestartDialog();
        mRestartDialog.show();
    }

    private Dialog createRestartDialog(){
        return  new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_confirm_restart)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i1) -> this.startService(new Intent(this, SyncthingService.class)
                        .setAction(SyncthingService.ACTION_RESTART)))
                .setNegativeButton(android.R.string.no, null)
                .create();
    }

    public void showQrCodeDialog(String deviceId, Bitmap qrCode) {
        @SuppressLint("InflateParams")
        View qrCodeDialogView = this.getLayoutInflater().inflate(R.layout.dialog_qrcode, null);
        TextView deviceIdTextView = qrCodeDialogView.findViewById(R.id.device_id);
        TextView shareDeviceIdTextView = qrCodeDialogView.findViewById(R.id.actionShareId);
        ImageView qrCodeImageView = qrCodeDialogView.findViewById(R.id.qrcode_image_view);

        deviceIdTextView.setText(deviceId);
        deviceIdTextView.setOnClickListener(v -> Util.copyDeviceId(this, deviceIdTextView.getText().toString()));
        shareDeviceIdTextView.setOnClickListener(v -> shareDeviceId(deviceId));
        qrCodeImageView.setImageBitmap(qrCode);

        mQrCodeDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.device_id)
                .setView(qrCodeDialogView)
                .setPositiveButton(R.string.finish, null)
                .create();

        mQrCodeDialog.show();
    }

    private void shareDeviceId(String deviceId) {
        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, deviceId);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_device_id_chooser)));
    }

    @Override
     public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    /**
     * Handles drawer opened and closed events, toggling option menu state.
     */
    private class Toggle extends ActionBarDrawerToggle {
        public Toggle(Activity activity, DrawerLayout drawerLayout) {
            super(activity, drawerLayout, R.string.app_name, R.string.app_name);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            mDrawerFragment.onDrawerOpened();
        }

        @Override
        public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            mDrawerFragment.onDrawerClosed();
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            super.onDrawerSlide(drawerView, 0);
        }
    }

    /**
     * Closes the drawer. Use when navigating away from activity.
     */
    public void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    /**
     * Toggles the drawer on menu button press.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START))
                mDrawerLayout.openDrawer(GravityCompat.START);
            else
                closeDrawer();

            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            // Close drawer on back button press.
            closeDrawer();
        } else {
            /**
             * Leave MainActivity in its state as the home button was pressed.
             * This will avoid waiting for the loading spinner when getting back
             * and give changes to do UI updates based on EventProcessor in the future.
             */
            moveTaskToBack(true);
        }
    }

    /**
     * Calculating width based on
     * http://www.google.com/design/spec/patterns/navigation-drawer.html#navigation-drawer-specs.
     */
    private void setOptimalDrawerWidth(View drawerContainer) {
        int actionBarSize = 0;
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarSize = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
        }

        ViewGroup.LayoutParams params = drawerContainer.getLayoutParams();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int minScreenWidth = min(displayMetrics.widthPixels, displayMetrics.heightPixels);

        params.width = min(minScreenWidth - actionBarSize, 5 * actionBarSize);
        drawerContainer.requestLayout();
    }

    /**
     * Displays dialog asking user to accept/deny usage reporting.
     */
    private void showUsageReportingDialog(RestApi restApi) {
        final DialogInterface.OnClickListener listener = (dialog, which) -> {
            try {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        restApi.setUsageReporting(true);
                        restApi.saveConfigAndRestart();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        restApi.setUsageReporting(false);
                        restApi.saveConfigAndRestart();
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        Uri uri = Uri.parse("https://data.syncthing.net");
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "showUsageReportingDialog:OnClickListener", e);
            }
        };

        restApi.getUsageReport(report -> {
            @SuppressLint("InflateParams")
            View v = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.dialog_usage_reporting, null);
            TextView tv = v.findViewById(R.id.example);
            tv.setText(report);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.usage_reporting_dialog_title)
                    .setView(v)
                    .setPositiveButton(R.string.yes, listener)
                    .setNegativeButton(R.string.no, listener)
                    .setNeutralButton(R.string.open_website, listener)
                    .show();
        });
    }

}
