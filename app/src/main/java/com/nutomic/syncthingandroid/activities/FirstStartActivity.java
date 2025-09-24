package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.Manifest;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;

import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingRunnable.ExecutableNotFoundException;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.FileUtils.ExternalStorageDirType;
import com.nutomic.syncthingandroid.util.PermissionUtil;
import com.nutomic.syncthingandroid.util.Util;
import com.nutomic.syncthingandroid.views.CustomViewPager;

import java.io.IOException;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

import javax.inject.Inject;

public class FirstStartActivity extends ThemedAppCompatActivity {

    private static String TAG = "FirstStartActivity";
    private static final int REQUEST_COARSE_LOCATION = 141;
    private static final int REQUEST_BACKGROUND_LOCATION = 142;
    private static final int REQUEST_FINE_LOCATION = 144;
    private static final int REQUEST_NOTIFICATION = 145;
    private static final int REQUEST_WRITE_STORAGE = 143;

    private static class Slide {
        public int layout;
        public int dotColorActive;
        public int dotColorInActive;

        Slide(int layout, int dotColorActive, int dotColorInActive) {
            this.layout = layout;
            this.dotColorActive = dotColorActive;
            this.dotColorInActive = dotColorInActive;
        }
    }

    private Slide[] mSlides;
    /**
     * Initialize the slide's ViewPager position to "-1" so it will never
     * trigger any action in {@link #onBtnNextClick} if the slide is not
     * shown.
     */
    private int mSlidePosStoragePermission = -1;
    private int mSlidePosIgnoreDozePermission = -1;
    private int mSlideLocationPermission = -1;
    private int mSlideNotificationPermission = -1;
    private int mSlidePosKeyGeneration = -1;

    private CustomViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private LinearLayout mDotsLayout;
    private TextView[] mDots;
    private Button mBackButton;
    private Button mNextButton;

    @Inject
    SharedPreferences mPreferences;

    private Boolean mRunningOnTV = false;
    private Boolean mUserDecisionIgnoreDozePermission = false;

    /**
     * Handles activity behaviour depending on prerequisites.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        mRunningOnTV = Util.isRunningOnTV(this);
        Log.d(TAG, mRunningOnTV ? "Running on a TV Device" : "Running on a non-TV Device");

        /**
         * Check if prerequisites to run the app are still in place.
         * If anything mandatory is missing, the according welcome slide(s) will be shown.
         */
        Boolean showSlideStoragePermission = !PermissionUtil.haveStoragePermission(FirstStartActivity.this);
        Boolean showSlideIgnoreDozePermission = !haveIgnoreDozePermission();
        Boolean showSlideLocationPermission = !haveLocationPermission();
        Boolean showSlideNotificationPermission = !haveNotificationPermission();
        Boolean showSlideKeyGeneration = !checkForParseableConfig();

        /**
         * If we don't have to show slides for mandatory prerequisites,
         * start directly into MainActivity.
         */
        if (!showSlideStoragePermission &&
                !showSlideNotificationPermission &&
                !showSlideKeyGeneration) {
            startApp();
            return;
        }

        // Log what's missing and preventing us from directly starting into MainActivity.
        if (showSlideStoragePermission) {
            Log.d(TAG, "We (no longer?) have storage permission and will politely ask for it.");
        }
        if (showSlideIgnoreDozePermission) {
            Log.d(TAG, "We (no longer?) have ignore doze permission and will politely ask for it on phones.");
        }
        if (showSlideLocationPermission) {
            Log.d(TAG, "We (no longer?) have location permission and will politely ask for it.");
        }
        if (showSlideNotificationPermission) {
            Log.d(TAG, "We (no longer?) have notification permission and will politely ask for it.");
        }
        if (showSlideKeyGeneration) {
            Log.d(TAG, "We (no longer?) have a valid Syncthing config and will attempt to generate a fresh config.");
        }

        // Show first start welcome wizard UI.
        setContentView(R.layout.activity_first_start);
        mViewPager = (CustomViewPager) findViewById(R.id.view_pager);
        mDotsLayout = (LinearLayout) findViewById(R.id.layoutDots);
        mBackButton = (Button) findViewById(R.id.btn_back);
        mNextButton = (Button) findViewById(R.id.btn_next);

        mViewPager.setPagingEnabled(false);

        // Add welcome slides to be shown.
        int[] colorsActive = getResources().getIntArray(R.array.array_dot_active);
        int[] colorsInactive = getResources().getIntArray(R.array.array_dot_inactive);
        int slideIndex = 0;
        mSlides = new Slide[
                1 +
                        (showSlideStoragePermission ? 1 : 0) +
                        (showSlideIgnoreDozePermission ? 1 : 0) +
                        (showSlideLocationPermission ? 1 : 0) +
                        (showSlideNotificationPermission ? 1 : 0) +
                        (showSlideKeyGeneration ? 1 : 0)
                ];
        mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_intro, colorsActive[0], colorsInactive[0]);
        if (showSlideStoragePermission) {
            mSlidePosStoragePermission = slideIndex;
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_storage_permission, colorsActive[1], colorsInactive[1]);
        }
        if (showSlideIgnoreDozePermission) {
            mSlidePosIgnoreDozePermission = slideIndex;
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_ignore_doze_permission, colorsActive[4], colorsInactive[4]);
        }
        if (showSlideLocationPermission) {
            mSlideLocationPermission = slideIndex;
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_location_permission, colorsActive[2], colorsInactive[2]);
        }
        if (showSlideNotificationPermission) {
            mSlideNotificationPermission = slideIndex;
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_notification_permission, colorsActive[0], colorsInactive[0]);
        }
        if (showSlideKeyGeneration) {
            mSlidePosKeyGeneration = slideIndex;
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_key_generation, colorsActive[3], colorsInactive[3]);
        }

        // Add bottom dots
        addBottomDots(0);

        mViewPagerAdapter = new ViewPagerAdapter();
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.addOnPageChangeListener(mViewPagerPageChangeListener);

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnBackClick();
            }
        });

        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnNextClick();
            }
        });

        if (mRunningOnTV) {
            mNextButton.setFocusableInTouchMode(true);
        }
        if (savedInstanceState != null) {
            mBackButton.setVisibility(savedInstanceState.getBoolean("mBackButton") ? View.VISIBLE : View.GONE);
            mNextButton.setVisibility(savedInstanceState.getBoolean("mNextButton") ? View.VISIBLE : View.GONE);
        }
        if (mNextButton.getVisibility() == View.VISIBLE) {
            mNextButton.requestFocus();
        } else if (mBackButton.getVisibility() == View.VISIBLE) {
            mBackButton.requestFocus();
        }
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean("mBackButton", mBackButton.getVisibility() == View.VISIBLE);
        outState.putBoolean("mNextButton", mNextButton.getVisibility() == View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mNextButton == null || mViewPager == null) {
            return;
        }
        if (mViewPager.getCurrentItem() == mSlidePosStoragePermission ||
                mViewPager.getCurrentItem() == mSlideNotificationPermission ||
                mViewPager.getCurrentItem() == mSlidePosIgnoreDozePermission) {
            mNextButton.performClick();
        }
    }

    public void onBtnBackClick() {
        int current = getItem(-1);
        if (current >= 0) {
            // Move to previous slider.
            mViewPager.setCurrentItem(current);
            if (current == 0) {
                mNextButton.requestFocus();
            }
        } else if (current == -1) {
            // "Back" on first slide quits the app.
            finish();
        }
    }

    public void onBtnNextClick() {
        // Check if we are allowed to advance to the next slide.
        if (mViewPager.getCurrentItem() == mSlidePosStoragePermission) {
            // As the storage permission is a prerequisite to run syncthing, refuse to continue without it.
            Boolean storagePermissionsGranted = PermissionUtil.haveStoragePermission(FirstStartActivity.this);
            if (!storagePermissionsGranted) {
                Toast.makeText(this, R.string.toast_write_storage_permission_required,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (mViewPager.getCurrentItem() == mSlidePosIgnoreDozePermission) {
            // As the ignore doze permission is a prerequisite to run syncthing, refuse to continue without it.
            if (!haveIgnoreDozePermission()) {
                /**
                 * a) Phones, tablets: The ignore doze permission is recommended.
                 * b) TVs: The ignore doze permission is optional as it can only set by ADB on Android 8+.
                 */
                if (!mUserDecisionIgnoreDozePermission && !mRunningOnTV) {
                    new AlertDialog.Builder(FirstStartActivity.this)
                            .setMessage(R.string.dialog_confirm_skip_ignore_doze_permission)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                    mUserDecisionIgnoreDozePermission = true;
                                    onBtnNextClick();
                            })
                            .setNegativeButton(android.R.string.no, (dialog, which) -> {
                                    mUserDecisionIgnoreDozePermission = false;
                            })
                            .show();

                    // Case a) - Prevent user moving on with the slides.
                    return;
                }
            }
        }

        if (mViewPager.getCurrentItem() == mSlideNotificationPermission) {
            // As the notification permission is a prerequisite to run the syncthing service permanently, refuse to continue without it.
            if (!haveNotificationPermission()) {
                Toast.makeText(this, R.string.toast_notification_permission_required,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        int current = getItem(+1);
        if (current < mSlides.length) {
            // Move to next slide.
            mViewPager.setCurrentItem(current);
            mBackButton.setVisibility(View.VISIBLE);
            if (current == mSlidePosIgnoreDozePermission) {
                if (mRunningOnTV) {
                    /**
                     * Display workaround notice: Without workaround SyncthingNative can't run reliably on TV's running Android 8+.
                     */
                    TextView ignoreDozeOsNotice = (TextView) findViewById(R.id.tvIgnoreDozePermissionOsNotice);
                    ignoreDozeOsNotice.setText(getString(R.string.ignore_doze_permission_os_notice, getString(R.string.wiki_url), "Android-TV-preparations"));
                    ignoreDozeOsNotice.setVisibility(View.VISIBLE);
                }
            } else if (current == mSlideLocationPermission) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    TextView locationPermissionTipApi29 = (TextView) findViewById(R.id.locationPermissionTipApi29);
                    locationPermissionTipApi29.setVisibility(View.VISIBLE);
                }
            } else if (current == mSlidePosKeyGeneration) {
                onKeyGenerationSlideShown();
            }
        } else {
            // Start the app after "mNextButton" was hit on the last slide.
            Log.v(TAG, "User completed first start UI.");
            startApp();
        }
    }

    private void addBottomDots(int currentPage) {
        mDots = new TextView[mSlides.length];

        mDotsLayout.removeAllViews();
        for (int i = 0; i < mDots.length; i++) {
            mDots[i] = new TextView(this);
            mDots[i].setText(Html.fromHtml("&#8226;"));
            mDots[i].setTextSize(35);
            mDots[i].setTextColor(mSlides[currentPage].dotColorInActive);

            // Prevent TalkBack from announcing a decorative TextView.
            mDots[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mDots[i].setContentDescription(getString(R.string.page_x_of_y, Integer.toString(i), Integer.toString(mDots.length)));
            mDotsLayout.addView(mDots[i]);
        }

        if (mDots.length > 0)
            mDots[currentPage].setTextColor(mSlides[currentPage].dotColorActive);
    }

    private int getItem(int i) {
        return mViewPager.getCurrentItem() + i;
    }

    //  ViewPager change listener
    CustomViewPager.OnPageChangeListener mViewPagerPageChangeListener = new CustomViewPager.OnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            addBottomDots(position);

            // Change the next button text from next to finish on last slide.
            mNextButton.setText(getString((position == mSlides.length - 1) ? R.string.finish : R.string.cont));
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {

        }

        @Override
        public void onPageScrollStateChanged(int arg0) {

        }
    };

    /**
     * View pager adapter
     */
    public class ViewPagerAdapter extends PagerAdapter {
        private LayoutInflater layoutInflater;

        public ViewPagerAdapter() {
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View view = layoutInflater.inflate(mSlides[position].layout, container, false);

            /* Slide: storage permission */
            Button btnGrantStoragePerm = (Button) view.findViewById(R.id.btnGrantStoragePerm);
            if (btnGrantStoragePerm != null) {
                btnGrantStoragePerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PermissionUtil.requestStoragePermission(FirstStartActivity.this, REQUEST_WRITE_STORAGE);
                    }
                });
            }

            /* Slide: ignore doze permission */
            Button btnGrantIgnoreDozePerm = (Button) view.findViewById(R.id.btnGrantIgnoreDozePerm);
            if (btnGrantIgnoreDozePerm != null) {
                btnGrantIgnoreDozePerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                            // We will never reach here, but fix lint.
                            return;
                        }
                        requestIgnoreDozePermission();
                    }
                });
            }

            /* Slide: location permission */
            Button btnGrantLocationPerm = (Button) view.findViewById(R.id.btnGrantLocationPerm);
            if (btnGrantLocationPerm != null) {
                btnGrantLocationPerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestLocationPermission();
                    }
                });
            }

            /* Slide: notification permission */
            Button btnGrantNotificationPerm = (Button) view.findViewById(R.id.btnGrantNotificationPerm);
            if (btnGrantNotificationPerm != null) {
                btnGrantNotificationPerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestNotificationPermission();
                    }
                });
            }

            container.addView(view);
            return view;
        }

        @Override
        public int getCount() {
            return mSlides.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object obj) {
            return view == obj;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View view = (View) object;
            container.removeView(view);
        }
    }

    /**
     * Preconditions:
     * Storage permission has been granted.
     */
    private void startApp() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        /**
          * In case start_into_web_gui option is enabled, start both activities
          * so that back navigation works as expected.
          */
         if (mPreferences.getBoolean(Constants.PREF_START_INTO_WEB_GUI, false)) {
             startActivities(new Intent[]{mainIntent, new Intent(this, WebGuiActivity.class)});
         } else {
             startActivity(mainIntent);
         }
        finish();
    }

    private boolean haveIgnoreDozePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Older android version don't have the doze feature so we'll assume having the anti-doze permission.
            return true;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    @SuppressLint("InlinedApi")
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestIgnoreDozePermission() {
        Boolean intentFailed = false;
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            ComponentName componentName = intent.resolveActivity(getPackageManager());
            if (componentName != null) {
                String className = componentName.getClassName();
                if (className != null && !className.equalsIgnoreCase("com.android.tv.settings.EmptyStubActivity")) {
                    // Launch "Exempt from doze mode?" dialog.
                    startActivity(intent);
                    return;
                }
                intentFailed = true;
            } else {
                Log.w(TAG, "Request ignore battery optimizations not supported");
                intentFailed = true;
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Request ignore battery optimizations not supported", e);
            intentFailed = true;
        }
        if (intentFailed) {
            // Some devices don't support this request.
            Toast.makeText(this, R.string.dialog_disable_battery_optimizations_not_supported, Toast.LENGTH_LONG).show();
        }
    }

    private boolean haveLocationPermission() {
        Boolean coarseLocationGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Boolean backgroundLocationGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return coarseLocationGranted && backgroundLocationGranted;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_FINE_LOCATION
            );
            return;
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    },
                    REQUEST_BACKGROUND_LOCATION
            );
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_COARSE_LOCATION);
    }

    private boolean haveNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_COARSE_LOCATION:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied ACCESS_COARSE_LOCATION permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted ACCESS_COARSE_LOCATION permission.");
                    mNextButton.requestFocus();
                    onBtnNextClick();
                }
                break;
            case REQUEST_BACKGROUND_LOCATION:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied ACCESS_BACKGROUND_LOCATION permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted ACCESS_BACKGROUND_LOCATION permission.");
                    mNextButton.requestFocus();
                    onBtnNextClick();
                }
                break;
            case REQUEST_FINE_LOCATION:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied ACCESS_FINE_LOCATION permission.");
                    return;
                }
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "User granted ACCESS_FINE_LOCATION permission.");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            },
                            REQUEST_BACKGROUND_LOCATION
                    );
                    return;
                }
                break;
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied WRITE_EXTERNAL_STORAGE permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted WRITE_EXTERNAL_STORAGE permission.");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mNextButton.requestFocus();
                        return;
                    }
                    onBtnNextClick();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Perform secure key generation in an AsyncTask.
     */
    private void onKeyGenerationSlideShown() {
        mBackButton.setVisibility(View.GONE);
        mNextButton.setVisibility(View.GONE);
        KeyGenerationTask keyGenerationTask = new KeyGenerationTask(this);
        keyGenerationTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Sets up the initial configuration and generates secure keys.
     */
    private static class KeyGenerationTask extends AsyncTask<Void, String, Void> {
        private WeakReference<FirstStartActivity> refFirstStartActivity;
        ConfigXml configXml;

        KeyGenerationTask(FirstStartActivity context) {
            refFirstStartActivity = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            FirstStartActivity firstStartActivity = refFirstStartActivity.get();
            if (firstStartActivity == null) {
                cancel(true);
                return null;
            }
            configXml = new ConfigXml(firstStartActivity);
            try {
                // Create new secure keys and config.
                configXml.generateConfig();
            } catch (ExecutableNotFoundException e) {
                publishProgress(firstStartActivity.getString(R.string.executable_not_found, e.getMessage()));
                cancel(true);
            } catch (ConfigXml.OpenConfigException e) {
                publishProgress(firstStartActivity.getString(R.string.config_create_failed));
                cancel(true);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if (values != null && values.length > 0) {
                FirstStartActivity firstStartActivity = refFirstStartActivity.get();
                if (firstStartActivity == null) {
                    return;
                }
                TextView keygenStatus = firstStartActivity.findViewById(R.id.key_generation_status);
                keygenStatus.setText(values[0]);
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // Get a reference to the activity if it is still there.
            FirstStartActivity firstStartActivity = refFirstStartActivity.get();
            if (firstStartActivity == null) {
                return;
            }
            TextView keygenStatus = (TextView) firstStartActivity.findViewById(R.id.key_generation_status);
            if (!firstStartActivity.checkForParseableConfig()) {
                keygenStatus.setText(firstStartActivity.getString(R.string.config_read_failed));
                return;
            }
            keygenStatus.setText(firstStartActivity.getString(R.string.key_generation_success));
            Button nextButton = (Button) firstStartActivity.findViewById(R.id.btn_next);
            nextButton.setVisibility(View.VISIBLE);
            nextButton.requestFocus();
            nextButton.performClick();
        }
    }

    private Boolean checkForParseableConfig() {
        /**
         * Check if a valid config exists that can be read and parsed.
         */
        Boolean configExists = Constants.getConfigFile(this).exists();
        if (!configExists) {
            return false;
        }
        Boolean configParseable = false;
        ConfigXml configParseTest = new ConfigXml(this);
        try {
            configParseTest.loadConfig();
            configParseable = true;
        } catch (ConfigXml.OpenConfigException e) {
            Log.d(TAG, "Failed to parse existing config. Will show key generation slide ...");
        }
        return configParseable;
    }

}
