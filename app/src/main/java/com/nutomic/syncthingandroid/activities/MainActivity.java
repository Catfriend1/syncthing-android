package com.nutomic.syncthingandroid.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.annimon.stream.function.Consumer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.fragments.DeviceListFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.fragments.StatusFragment;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.PermissionUtil;
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
public class MainActivity extends SyncthingActivity
        implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "MainActivity";

    private Boolean ENABLE_VERBOSE_LOG = false;

    private static final String IS_SHOWING_RESTART_DIALOG = "RESTART_DIALOG_STATE";
    private static final String IS_QRCODE_DIALOG_DISPLAYED = "QRCODE_DIALOG_STATE";
    private static final String QRCODE_BITMAP_KEY = "QRCODE_BITMAP";
    private static final String DEVICEID_KEY = "DEVICEID";

    private static final int FOLDER_FRAGMENT_ID = 0;
    private static final int DEVICE_FRAGMENT_ID = 1;
    private static final int STATUS_FRAGMENT_ID = 2;
    
    /**
     * Intent action to exit app.
     */
    public static final String ACTION_EXIT =
            ".MainActivity.EXIT";

    /**
     * Time after first start when usage reporting dialog should be shown.
     * See {@link #showUsageReportingDialog}
     */
    private static final long USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(3);
    private static final Boolean DEBUG_FORCE_USAGE_REPORTING_DIALOG = false;

    private AlertDialog mQrCodeDialog;
    private AlertDialog mUsageReportingDialog;
    private Dialog mRestartDialog;

    private SyncthingService.State mSyncthingServiceState = SyncthingService.State.INIT;

    private ViewPager mViewPager;

    private FolderListFragment mFolderListFragment;
    private DeviceListFragment mDeviceListFragment;
    private StatusFragment     mStatusFragment;
    private DrawerFragment     mDrawerFragment;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout          mDrawerLayout;
    private FloatingActionButton  mExitFab;

    private Intent mLastIntent;
    private Boolean oneTimeShot = true;
    
    // Session flag to track if user chose "remind later" for important news
    private boolean mImportantNewsRemindLaterThisSession = false;

    @Inject SharedPreferences mPreferences;

    private final Handler mUIRefreshHandler = new Handler();

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
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
    };

    private Runnable mUIRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mUIRefreshHandler.postDelayed(this, Constants.GUI_UPDATE_INTERVAL);
        }
    };

    /**
     * Handles various dialogs based on current state.
     */
    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mSyncthingServiceState = currentState;
        if (oneTimeShot) {
            updateViewPager();
            oneTimeShot = false;
        }

        // Update status light indicating if syncthing is running.
        Button btnDisabled = (Button) findViewById(R.id.btnDisabled);
        Button btnStarting = (Button) findViewById(R.id.btnStarting);
        Button btnActive = (Button) findViewById(R.id.btnActive);
        if (btnDisabled != null && btnStarting != null && btnActive != null) {
            btnActive.setVisibility(currentState == SyncthingService.State.ACTIVE ? View.VISIBLE : View.GONE);
            btnStarting.setVisibility(currentState == SyncthingService.State.STARTING ? View.VISIBLE : View.GONE);
            btnDisabled.setVisibility(currentState != SyncthingService.State.ACTIVE && currentState != SyncthingService.State.STARTING ? View.VISIBLE : View.GONE);
        }

        switch (currentState) {
            case ACTIVE:
                // Check if the usage reporting minimum delay passed by.
                Boolean usageReportingDelayPassed = (new Date().getTime() > getFirstStartTime() + USAGE_REPORTING_DIALOG_DELAY);
                RestApi restApi = getApi();
                if (        (
                                DEBUG_FORCE_USAGE_REPORTING_DIALOG
                            ) || (
                                usageReportingDelayPassed &&
                                restApi != null &&
                                restApi.isConfigLoaded() &&
                                !restApi.isUsageReportingDecided()
                            )) {
                    showUsageReportingDialog(restApi);
                }
                
                // Check if important news notification should be shown
                // showImportantNewsNotificationIfNeeded();
                break;
            case ERROR:
                finish();
                break;
        }
        updateTotalSyncProgressBar();
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

    /**
     * Initializes tab navigation.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        if (ENABLE_VERBOSE_LOG) {
            Util.testPathEllipsis();
        }

        setContentView(R.layout.activity_main);
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mViewPager = findViewById(R.id.pager);
        
        // Initialize the exit FloatingActionButton
        mExitFab = findViewById(R.id.fabExit);
        mExitFab.setOnClickListener(v -> doExit());
        
        // Update FAB visibility based on service configuration
        updateExitFabVisibility();

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState != null) {
            mFolderListFragment = (FolderListFragment) fm.getFragment(
                    savedInstanceState, FolderListFragment.class.getName());
            mDeviceListFragment = (DeviceListFragment) fm.getFragment(
                    savedInstanceState, DeviceListFragment.class.getName());
            mStatusFragment = (StatusFragment) fm.getFragment(
                    savedInstanceState, StatusFragment.class.getName());
            mDrawerFragment = (DrawerFragment) fm.getFragment(
                    savedInstanceState, DrawerFragment.class.getName());
        }
        if (mFolderListFragment == null) {
            mFolderListFragment = new FolderListFragment();
        }
        if (mDeviceListFragment == null) {
            mDeviceListFragment = new DeviceListFragment();
        }
        if (mStatusFragment == null) {
            mStatusFragment = new StatusFragment();
        }
        if (mDrawerFragment == null) {
            mDrawerFragment = new DrawerFragment();
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(IS_SHOWING_RESTART_DIALOG)){
                showRestartDialog();
            }
            if (savedInstanceState.getBoolean(IS_QRCODE_DIALOG_DISPLAYED)) {
                showQrCodeDialog(savedInstanceState.getString(DEVICEID_KEY), savedInstanceState.getParcelable(QRCODE_BITMAP_KEY));
            }
        }

        fm.beginTransaction().replace(R.id.drawer, mDrawerFragment).commitAllowingStateLoss();
        mDrawerToggle = new Toggle(this, mDrawerLayout);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        setOptimalDrawerWidth(findViewById(R.id.drawer));

        /**
         * SyncthingService needs to be started from this activity as the user
         * can directly launch this activity from the recent activity switcher.
         */
        Intent serviceIntent = new Intent(this, SyncthingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        onNewIntent(getIntent());

        // Register OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }
    
    
    @Override
    protected void onNewIntent(Intent intent) {
        mLastIntent = intent;
        super.onNewIntent(intent);
    };


    /**
     * Updates the ViewPager to show tabs depending on the service state.
     */
    private void updateViewPager() {
        final int numPages = 3;
        FragmentStatePagerAdapter mSectionsPagerAdapter =
                new FragmentStatePagerAdapter(getSupportFragmentManager()) {

            @Override
            public Fragment getItem(int position) {
                switch (position) {
                    case FOLDER_FRAGMENT_ID:
                        return mFolderListFragment;
                    case DEVICE_FRAGMENT_ID:
                        return mDeviceListFragment;
                    case STATUS_FRAGMENT_ID:
                        return mStatusFragment;
                    default:
                        return null;
                }
            }

            @Override
            public int getItemPosition(Object object) {
                return this.POSITION_NONE;
            }

            @Override
            public int getCount() {
                return numPages;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position) {
                    case FOLDER_FRAGMENT_ID:
                        return getResources().getString(R.string.folders_fragment_title);
                    case DEVICE_FRAGMENT_ID:
                        return getResources().getString(R.string.devices_fragment_title);
                    case STATUS_FRAGMENT_ID:
                        return getResources().getString(R.string.status_fragment_title);
                    default:
                        return String.valueOf(position);
                }
            }
        };
        try {
            mViewPager.setAdapter(mSectionsPagerAdapter);
        } catch (IllegalStateException e) {
            /**
             * IllegalStateException happens due to a bug in FragmentStatePagerAdapter.
             * For more information see:
             * - https://issuetracker.google.com/issues/36956111
             */
            Log.e(TAG, "updateViewPager: IllegalStateException in setAdapter.", e);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.exception_known_bug_notice, getString(R.string.issue_tracker_url), "108"))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {})
                    .show();
        }
        TabLayout tabLayout = findViewById(R.id.tabContainer);
        tabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    public void onPause() {
        stopUIRefreshHandler();
        super.onPause();
    }

    @Override
    public void onResume() {
        // Check if storage permission has been revoked at runtime.
        if (!PermissionUtil.haveStoragePermission(this)) {
            startActivity(new Intent(this, FirstStartActivity.class));
            this.finish();
            super.onResume();
            return;
        }

        // Evaluate run conditions to detect changes made to the metered wifi flags.
        SyncthingService mSyncthingService = getService();
        if (mSyncthingService != null) {
            mSyncthingService.evaluateRunConditions();
        }

        startUIRefreshHandler();

        // Update FAB visibility in case settings changed
        updateExitFabVisibility();

        String action = mLastIntent.getAction();
        if (action != null) {
            if (ACTION_EXIT.equals(action)) {
                Log.i(TAG, "Exit app requested by notification action");
                stopService(new Intent(this, SyncthingService.class));
                finishAndRemoveTask();
            }
        }
        
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService mSyncthingService = getService();
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnServiceStateChangeListener(this);
            mSyncthingService.unregisterOnServiceStateChangeListener(mDrawerFragment);
            mSyncthingService.unregisterOnServiceStateChangeListener(mFolderListFragment);
            mSyncthingService.unregisterOnServiceStateChangeListener(mDeviceListFragment);
            mSyncthingService.unregisterOnServiceStateChangeListener(mStatusFragment);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder syncthingServiceBinder = (SyncthingServiceBinder) iBinder;
        SyncthingService syncthingService = syncthingServiceBinder.getService();
        syncthingService.registerOnServiceStateChangeListener(this);
        syncthingService.registerOnServiceStateChangeListener(mDrawerFragment);
        syncthingService.registerOnServiceStateChangeListener(mFolderListFragment);
        syncthingService.registerOnServiceStateChangeListener(mDeviceListFragment);
        syncthingService.registerOnServiceStateChangeListener(mStatusFragment);
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        FragmentManager fm = getSupportFragmentManager();
        fm.executePendingTransactions();
        Consumer<Fragment> putFragment = fragment -> {
            if (fragment != null && fragment.isAdded()) {
                fm.putFragment(outState, fragment.getClass().getName(), fragment);
            }
        };
        putFragment.accept(mFolderListFragment);
        putFragment.accept(mDeviceListFragment);
        putFragment.accept(mStatusFragment);

        outState.putBoolean(IS_SHOWING_RESTART_DIALOG, mRestartDialog != null && mRestartDialog.isShowing());
        if (mQrCodeDialog != null && mQrCodeDialog.isShowing()) {
            outState.putBoolean(IS_QRCODE_DIALOG_DISPLAYED, true);
            ImageView qrCode = mQrCodeDialog.findViewById(R.id.qrcode_image_view);
            TextView deviceID = mQrCodeDialog.findViewById(R.id.device_id);
            outState.putParcelable(QRCODE_BITMAP_KEY, ((BitmapDrawable) qrCode.getDrawable()).getBitmap());
            outState.putString(DEVICEID_KEY, deviceID.getText().toString());
        }
        Util.dismissDialogSafe(mRestartDialog, this);
        Util.dismissDialogSafe(mUsageReportingDialog, this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.btn_menu);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                    }
            });
        }

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

    private void startUIRefreshHandler() {
        LogV("startUIRefreshHandler");
        mUIRefreshHandler.removeCallbacks(mUIRefreshRunnable);
        mUIRefreshHandler.post(mUIRefreshRunnable);
    }

    private void stopUIRefreshHandler() {
        LogV("stopUIRefreshHandler");
        mUIRefreshHandler.removeCallbacks(mUIRefreshRunnable);
    }

    public void showRestartDialog(){
        mRestartDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_confirm_restart)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i1) -> this.startService(new Intent(this, SyncthingService.class)
                        .setAction(SyncthingService.ACTION_RESTART)))
                .setNegativeButton(android.R.string.no, null)
                .create();
        mRestartDialog.show();
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
        public Toggle(AppCompatActivity activity, DrawerLayout drawerLayout) {
            super(activity, drawerLayout, R.string.open_main_menu, R.string.close_main_menu);
            setDrawerIndicatorEnabled(false);
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
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.openDrawer(GravityCompat.START);
            } else {
                closeDrawer();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeDrawer();
            return true;
        }
        return super.onKeyDown(keyCode, e);
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
        Log.v(TAG, "showUsageReportingDialog triggered.");
        final DialogInterface.OnClickListener listener = (dialog, which) -> {
            try {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        restApi.setUsageReporting(true);
                        restApi.sendConfig();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        restApi.setUsageReporting(false);
                        restApi.sendConfig();
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        final Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                        intent.putExtra(WebViewActivity.EXTRA_WEB_URL, getString(R.string.syncthing_usage_stats_url));
                        startActivity(intent);
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
            Util.dismissDialogSafe(mUsageReportingDialog, MainActivity.this);
            mUsageReportingDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.usage_reporting_dialog_title)
                    .setView(v)
                    .setPositiveButton(R.string.yes, listener)
                    .setNegativeButton(R.string.no, listener)
                    .setNeutralButton(R.string.open_website, listener)
                    .show();
        });
    }

    // The timer is periodically triggering while the user is looking at the UI.
    private void onTimerEvent() {
        updateTotalSyncProgressBar();
    }

    @SuppressLint("SetTextI18n")
    private void updateTotalSyncProgressBar() {
        View topRelTotalSyncProgress = (View) findViewById(R.id.topRelTotalSyncProgress);
        ProgressBar pbTotalSyncComplete = (ProgressBar) findViewById(R.id.pbTotalSyncComplete);
        TextView tvTotalSyncComplete = (TextView) findViewById(R.id.tvTotalSyncComplete);
        if (topRelTotalSyncProgress == null ||
                pbTotalSyncComplete == null ||
                tvTotalSyncComplete == null) {
            return;
        }

        RestApi restApi = getApi();
        if (restApi == null || !restApi.isConfigLoaded()) {
            topRelTotalSyncProgress.setVisibility(View.GONE);
            return;
        }

        int totalSyncCompletePercent = restApi.getTotalSyncCompletion();
        Boolean showTotalSyncProgress = (
                (mSyncthingServiceState == SyncthingService.State.ACTIVE) &&
                (totalSyncCompletePercent != -1)
        );
        if (!showTotalSyncProgress) {
            topRelTotalSyncProgress.setVisibility(View.GONE);
            return;
        }
        topRelTotalSyncProgress.setVisibility(View.VISIBLE);
        pbTotalSyncComplete.setProgress(totalSyncCompletePercent);
        tvTotalSyncComplete.setText(Integer.toString(totalSyncCompletePercent));
    }

    /**
     * Shows important news notification if needed.
     * The notification is shown only if the app version has changed since the user last dismissed it
     * and the user hasn't chosen "remind later" in this session.
     */
    private void showImportantNewsNotificationIfNeeded() {
        String currentVersion = getCurrentAppVersion();
        String lastDismissedVersion = mPreferences.getString(Constants.PREF_IMPORTANT_NEWS_SHOWN_VERSION, "");
        
        // Show notification if version has changed since last dismissal and user hasn't chosen "remind later" in this session
        if (!currentVersion.equals(lastDismissedVersion) && !mImportantNewsRemindLaterThisSession) {
            showImportantNewsSnackbar();
        }
    }

    /**
     * Gets the current app version name.
     */
    private String getCurrentAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app version", e);
            return "";
        }
    }

    /**
     * Shows the important news notification Snackbar with an action to view options.
     */
    private void showImportantNewsSnackbar() {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) {
            Log.w(TAG, "Cannot show important news Snackbar: root view not found");
            return;
        }

        // Show only the title to prevent text truncation
        Snackbar snackbar = Snackbar.make(rootView, 
            getString(R.string.important_news_title), 
            Snackbar.LENGTH_INDEFINITE);

        // Apply proper theming for better visibility in both light and dark modes
        snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.accent));
        
        // Get the Snackbar's view and apply background styling
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
        
        // Set text color for better contrast
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        // Action button to view all options
        snackbar.setAction(getString(R.string.important_news_action_view_options), v -> {
            showImportantNewsActionsDialog();
            snackbar.dismiss();
        });

        snackbar.show();
    }

    /**
     * Shows a dialog with the three important news actions.
     */
    private void showImportantNewsActionsDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.important_news_title)
            .setMessage(getString(R.string.important_news_description, getString(R.string.important_news_url)))
            .setPositiveButton(R.string.important_news_action_open, (dialog, which) -> 
                handleImportantNewsAction("open"))
            .setNeutralButton(R.string.important_news_action_remind, (dialog, which) -> 
                handleImportantNewsAction("remind"))
            .setNegativeButton(R.string.important_news_action_no_remind, (dialog, which) -> 
                handleImportantNewsAction("no_remind"))
            .show();
    }

    /**
     * Handles the different important news actions.
     */
    private void handleImportantNewsAction(String action) {
        Log.v(TAG, "Important news action selected: " + action);
        
        switch (action) {
            case "open":
                // Open the important news URL in browser
                String url = getString(R.string.important_news_url);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                Log.i(TAG, "User chose to open important news information at: " + url);
                
                // Save current version so notification won't show again until next app update
                String currentVersion = getCurrentAppVersion();
                mPreferences.edit()
                    .putString(Constants.PREF_IMPORTANT_NEWS_SHOWN_VERSION, currentVersion)
                    .apply();
                Log.i(TAG, "Saved current version " + currentVersion + " to preferences after opening browser");
                break;
                
            case "remind":
                // User wants to be reminded later - set session flag to prevent showing again until app restart
                mImportantNewsRemindLaterThisSession = true;
                Log.i(TAG, "User chose to be reminded later about important news - will show again on next app start");
                break;
                
            case "no_remind":
                // User doesn't want to be reminded anymore for this version
                String currentVersionDismiss = getCurrentAppVersion();
                mPreferences.edit()
                    .putString(Constants.PREF_IMPORTANT_NEWS_SHOWN_VERSION, currentVersionDismiss)
                    .apply();
                Log.i(TAG, "User chose not to be reminded about important news for version " + currentVersionDismiss);
                break;
        }
    }

    /**
     * Updates the visibility of the exit FAB based on service configuration.
     * FAB is hidden when app is configured to run as a service to avoid confirmation dialogs.
     */
     private void updateExitFabVisibility() {
        if (mExitFab != null && mPreferences != null) {
            boolean runAsService = mPreferences.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false);
            mExitFab.setVisibility(runAsService ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Exits the application by stopping the service and finishing the activity.
     * This method is called directly from the FAB since it's only visible when safe to exit.
     */
    private void doExit() {
        if (isFinishing()) {
            return;
        }
        Log.i(TAG, "Exiting app on user request via FAB");
        stopService(new Intent(this, SyncthingService.class));
        finishAndRemoveTask();
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }

}
