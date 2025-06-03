package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.List;

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

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.switch_logs) {
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
        } else if (itemId == R.id.menu_share_log_file) {
            if (mSyncthingLog) {
                File syncthingLog = Constants.getSyncthingLogFile(this);
                shareLogFile(syncthingLog);
            } else {
                File androidLog = Constants.getAndroidLogFile(this);
                // ToDo Overwrite with logcat output.
                shareLogFile(androidLog);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean shareLogFile(final File logFile) {
        if (!logFile.exists()) {
            Toast.makeText(this, getString(R.string.share_log_file_missing), Toast.LENGTH_SHORT).show();
            return false;
        }

        Uri contentUri = FileProvider.getUriForFile(
            this,
            getPackageName() + ".provider",
            logFile
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_log_file)));
        return true;
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
            String output;
            if (syncthingLog) {
                output = Util.runShellCommandGetOutput("/system/bin/logcat -t 200000 -v time -s SyncthingNativeCode", false);
            } else {
                // Get Android log.
                output = Util.runShellCommandGetOutput("/system/bin/logcat -t 900 -v time *:i ps:s art:s", false);
            }

            // Filter Android log.
            output = output.replaceAll("I/SyncthingNativeCode", "");
            // Remove PID.
            output = output.replaceAll("\\(\\s?[0-9]+\\):", "");
            String[] lines = output.split("\n");
            List<String> list = new ArrayList<String>(Arrays.asList(lines));
            ListIterator<String> it = list.listIterator();
            while (it.hasNext()) {
                String logline = it.next();
                if (
                            logline.contains("--- beginning of ") ||
                            logline.contains("W/ActionBarDrawerToggle") ||
                            logline.contains("W/ActivityThread") ||
                            logline.contains("I/Adreno") ||
                            logline.contains("/AssistStructure") ||
                            logline.contains("I/chatty") ||
                            logline.contains("/Choreographer") ||
                            logline.contains("W/chmod") ||
                            logline.contains("/chromium") ||
                            logline.contains("/ContentCaptureHelper") ||
                            logline.contains("/ContentCatcher") ||
                            logline.contains("/cr_AwContents") ||
                            logline.contains("/cr_base") ||
                            logline.contains("/cr_BrowserStartup") ||
                            logline.contains("/cr_ChildProcessConn") ||
                            logline.contains("/cr_ChildProcLH") ||
                            logline.contains("/cr_CrashFileManager") ||
                            logline.contains("/cr_LibraryLoader") ||
                            logline.contains("/cr_media") ||
                            logline.contains("/cr_MediaCodecUtil") ||
                            logline.contains("I/ConfigStore") ||
                            logline.contains("/dalvikvm") ||
                            logline.contains("/DecorView") ||
                            logline.contains("/EGL") ||                      
                            logline.contains("/eglCodecCommon") ||
                            logline.contains("W/Gralloc") ||
                            logline.contains("W/HWUI") ||
                            logline.contains("/InputEventReceiver") ||
                            logline.contains("/InputMethodManager") ||
                            logline.contains("/libEGL") ||
                            logline.contains("W/libsyncthingnat") ||
                            logline.contains("W/netstat") ||
                            logline.contains("/ngandroid.debu") ||
                            logline.contains("/OpenGLRenderer") ||
                            logline.contains("/PacProxySelector") ||
                            logline.contains("I/Perf") ||
                            logline.contains("/RenderThread") ||
                            logline.contains("/ResourceType") ||
                            logline.contains("W/sh") ||
                            logline.contains("/StrictMode") ||
                            logline.contains("I/System.out") ||
                            logline.contains("W/TextView") ||
                            logline.contains("I/Timeline") ||
                            logline.contains("/VideoCapabilities") ||
                            logline.contains("I/WebViewFactory") ||
                            logline.contains("I/X509Util") ||
                            logline.contains("/zygote") ||
                            logline.contains("/zygote64")
                        ) {
                    it.remove();
                    continue;
                }
                // Remove date.
                logline = logline.replaceFirst("^[0-9]{2}-[0-9]{2}\\s", "");
                // Remove milliseconds.
                logline = logline.replaceFirst("^([0-9]{2}:[0-9]{2}:[0-9]{2})\\.[0-9]{3}\\s", "$1");
                it.set(logline);
            }
            return TextUtils.join("\n", list.toArray(new String[0]));
        }
    }

    /**
     * Stores ignore list for given folder.
     */
    public void writeLogFile(final String absoluteFn, String logContent) {
        File file;
        FileOutputStream fileOutputStream = null;
        try {
            file = new File(absoluteFn);
            if (!file.exists()) {
                file.createNewFile();
            }
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(logContent.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.flush();
        } catch (IOException e) {
            Log.w(TAG, "writeLogFile: Failed to write '" + absoluteFn + "' #1", e);
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "writeLogFile: Failed to write '" + absoluteFn + "' #2", e);
            }
        }
    }

}
