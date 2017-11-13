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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Shows the log information from Syncthing.
 */
public class LogActivity extends SyncthingActivity {

    private final static String TAG = "LogActivity";

    private TextView mLog;
    private boolean mSyncthingLog = true;
    private AsyncTask mFetchLogTask;
    private ScrollView mScrollView;
    private Intent mShareIntent;

    /**
     * Initialize Log.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);
        setTitle(R.string.syncthing_log_title);

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

    private void scrollToBottom() {
        mScrollView.post(() -> mScrollView.scrollTo(0, mLog.getBottom()));
    }

    private void updateLog() {
        if (mFetchLogTask != null)
            mFetchLogTask.cancel(true);
        mLog.setText(R.string.retrieving_logs);
        mFetchLogTask = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                return getLog(mSyncthingLog);
            }
            @Override
            protected void onPostExecute(String log) {
                mLog.setText(log);
                if (mShareIntent != null)
                    mShareIntent.putExtra(android.content.Intent.EXTRA_TEXT, log);
                scrollToBottom();
            }
        }.execute();
    }

    /**
     * Queries logcat to obtain a log.
     *
     * @param syncthingLog Filter on Syncthing's native messages.
     */
    private String getLog(final boolean syncthingLog) {
        Process process = null;
        try {
            ProcessBuilder pb;
            if (syncthingLog) {
                pb = new ProcessBuilder("/system/bin/logcat", "-t", "300", "-v", "time", "-s", "SyncthingNativeCode");
            } else {
                pb = new ProcessBuilder("/system/bin/logcat", "-t", "300", "-v", "time", "*:i ps:s art:s");
            }
            pb.redirectErrorStream(true);
            process = pb.start();
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"), 8192);
            StringBuilder log = new StringBuilder();
            String line;
            String sep = System.getProperty("line.separator");
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line);
                log.append(sep);
            }
            return log.toString();
        } catch (IOException e) {
            Log.w(TAG, "Error reading Android log", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return "";
    }

}
