package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;

/**
 * Shows the log information from Syncthing.
 */
public class LogActivity extends SyncthingActivity {

    private final static String TAG = "LogActivity";

    /**
     * Show Android Log by default.
     */
    private boolean mSyncthingLog = false;

    private TextView mLog;
    private AsyncTask mFetchLogTask = null;
    private ScrollView mScrollView;
    private Intent mShareIntent;

    /**
     * Initialize Log.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);
        setTitle(R.string.android_log_title);

        if (savedInstanceState != null) {
            mSyncthingLog = savedInstanceState.getBoolean("syncthingLog");
            invalidateOptionsMenu();
        }

        mLog = findViewById(R.id.log);
        mScrollView = findViewById(R.id.scroller);

        updateLog();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("syncthingLog", mSyncthingLog);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_list, menu);

        MenuItem switchLog = menu.findItem(R.id.switch_logs);
        switchLog.setTitle(mSyncthingLog ? R.string.view_android_log : R.string.view_syncthing_log);

        // Add the share button
        MenuItem shareItem = menu.findItem(R.id.menu_share);
        ShareActionProvider actionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
        mShareIntent = new Intent();
        mShareIntent.setAction(Intent.ACTION_SEND);
        mShareIntent.setType("text/plain");
        mShareIntent.putExtra(android.content.Intent.EXTRA_TEXT, mLog.getText());
        actionProvider.setShareIntent(mShareIntent);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.switch_logs:
                mSyncthingLog = !mSyncthingLog;
                if (mSyncthingLog) {
                    item.setTitle(R.string.view_android_log);
                    setTitle(R.string.syncthing_log_title);
                } else {
                    item.setTitle(R.string.view_syncthing_log);
                    setTitle(R.string.android_log_title);
                }
                updateLog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateLog() {
        if (mFetchLogTask != null) {
            mFetchLogTask.cancel(true);
        }
        mLog.setText(R.string.retrieving_logs);
        mFetchLogTask = new UpdateLogTask(this).execute();
    }

    private static class UpdateLogTask extends AsyncTask<Void, Void, String> {
        private WeakReference<LogActivity> refLogActivity;

        UpdateLogTask(LogActivity context) {
            refLogActivity = new WeakReference<>(context);
        }

        protected String doInBackground(Void... params) {
            // Get a reference to the activity if it is still there.
            LogActivity logActivity = refLogActivity.get();
            if (logActivity == null || logActivity.isFinishing()) {
                cancel(true);
                return "";
            }
            return getLog(logActivity.mSyncthingLog);
        }

        protected void onPostExecute(String log) {
            // Get a reference to the activity if it is still there.
            LogActivity logActivity = refLogActivity.get();
            if (logActivity == null || logActivity.isFinishing()) {
                return;
            }
            logActivity.mLog.setText(log);
            if (logActivity.mShareIntent != null) {
                logActivity.mShareIntent.putExtra(android.content.Intent.EXTRA_TEXT, log);
            }
            // Scroll to bottom
            logActivity.mScrollView.post(() -> logActivity.mScrollView.scrollTo(0, logActivity.mLog.getBottom()));
        }

        /**
         * Queries logcat to obtain a log.
         *
         * @param syncthingLog Filter on Syncthing's native messages.
         */
        private String getLog(final boolean syncthingLog) {
            if (syncthingLog) {
                String output = Util.runShellCommandGetOutput("/system/bin/logcat -t 300 -v time -s SyncthingNativeCode", false);
                return output.replaceAll("SyncthingNativeCode", "");
            } else {
                return Util.runShellCommandGetOutput("/system/bin/logcat -t 300 -v time *:i ps:s art:s", false);
            }
        }
    }

}
