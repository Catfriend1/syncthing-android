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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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

    private static final int ANDROID_LOG_FILE_MAX_LINES = 2000;

    /**
     * Show Android Log by default.
     */
    private boolean mShowSyncthingLog = false;

    private TextView mLog;
    private AsyncTask mFetchLogTask = null;
    private ScrollView mScrollView;
    private Intent mShareIntent;
   
    private String androidLogContent = "";   
    private String syncthingLogContent = "";

    /**
     * Initialize Log.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);
        setTitle(R.string.android_log_title);

        if (savedInstanceState != null) {
            mShowSyncthingLog = savedInstanceState.getBoolean("showSyncthingLog");
            invalidateOptionsMenu();
        }

        mLog = findViewById(R.id.log);
        mScrollView = findViewById(R.id.scroller);

        fetchAndViewLog();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("showSyncthingLog", mShowSyncthingLog);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_list, menu);

        MenuItem switchLog = menu.findItem(R.id.switch_logs);
        switchLog.setTitle(mShowSyncthingLog ? R.string.view_android_log : R.string.view_syncthing_log);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.switch_logs) {
            mShowSyncthingLog = !mShowSyncthingLog;
            if (mShowSyncthingLog) {
                item.setTitle(R.string.view_android_log);
                setTitle(R.string.syncthing_log_title);
            } else {
                item.setTitle(R.string.view_syncthing_log);
                setTitle(R.string.android_log_title);
            }
            fetchAndViewLog();
            return true;
        } else if (itemId == R.id.menu_share_log_file) {
            if (mShowSyncthingLog) {
                File syncthingLog = Constants.getSyncthingLogFile(this);
                shareLogFile(syncthingLog);
            } else {
                File androidLog = Constants.getAndroidLogFile(this);
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

    private void fetchAndViewLog() {
        if (mFetchLogTask != null) {
            mFetchLogTask.cancel(true);
        }
        mLog.setText(R.string.retrieving_logs);
        mFetchLogTask = new UpdateLogTask(this).execute();
    }

    private static class UpdateLogTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<LogActivity> refLogActivity;

        UpdateLogTask(LogActivity context) {
            refLogActivity = new WeakReference<>(context);
        }

        protected Void doInBackground(Void... voids) {
            // Get a reference to the activity if it is still there.
            LogActivity logActivity = refLogActivity.get();
            if (logActivity == null || logActivity.isFinishing()) {
                cancel(true);
                return null;
            }

            // Get Android log.
            logActivity.androidLogContent = getAndroidLog();
            writeLogFile(Constants.getAndroidLogFile(logActivity), logActivity.androidLogContent);

            // Get SyncthingNative log.
            logActivity.syncthingLogContent = readLogFile(Constants.getSyncthingLogFile(logActivity));
            return null;
        }

        protected void onPostExecute(Void aVoid) {
            // Get a reference to the activity if it is still there.
            LogActivity logActivity = refLogActivity.get();
            if (logActivity == null || logActivity.isFinishing()) {
                return;
            }

            // Show one of the two logs available.
            logActivity.mLog.setText(logActivity.mShowSyncthingLog ? logActivity.syncthingLogContent : logActivity.androidLogContent);

            // Scroll to bottom
            logActivity.mScrollView.post(() -> logActivity.mScrollView.scrollTo(0, logActivity.mLog.getBottom()));
        }

        /**
         * Queries logcat to obtain a log.
         *
         * @param syncthingLog Filter on Syncthing's native messages.
         */
        private String getAndroidLog() {
            String output = Util.runShellCommandGetOutput("/system/bin/logcat -t " + Integer.toString(ANDROID_LOG_FILE_MAX_LINES) + " -v time *:i ps:s art:s", false);

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
                            logline.contains("/AbsListViewStubImpl") ||
                            logline.contains("W/ActionBarDrawerToggle") ||
                            logline.contains("/ActivityThread") ||
                            logline.contains("/Adreno") ||
                            logline.contains("/AdrenoUtils") ||
                            logline.contains("/AiRecognition") ||
                            logline.contains("/androidtc") ||
                            logline.contains("/AssistStructure") ||
                            logline.contains("/AudioCapabilities") ||
                            logline.contains("/BLASTBufferQueue_Java") ||
                            logline.contains("/BinderMonitor") ||
                            logline.contains("I/chatty") ||
                            logline.contains("/CameraExtImplXiaoMi") ||
                            logline.contains("/CameraManagerGlobal") ||
                            logline.contains("/Choreographer") ||
                            logline.contains("W/chmod") ||
                            logline.contains("/chromium") ||
                            logline.contains("/ContentCaptureHelper") ||
                            logline.contains("/ContentCatcher") ||
                            logline.contains("/cr_AwContents") ||
                            logline.contains("/cr_base") ||
                            logline.contains("/cr_BrowserStartup") ||
                            logline.contains("/cr_CachingUmaRecorder") ||
                            logline.contains("/cr_ChildProcessConn") ||
                            logline.contains("/cr_ChildProcLH") ||
                            logline.contains("/cr_CombinedPProvider") ||
                            logline.contains("/cr_CrashFileManager") ||
                            logline.contains("/cr_LibraryLoader") ||
                            logline.contains("/cr_media") ||
                            logline.contains("/cr_MediaCodecUtil") ||
                            logline.contains("/cr_PolicyProvider") ||
                            logline.contains("/cr_VAUtil") ||
                            logline.contains("/cr_WVCFactoryProvider") ||
                            logline.contains("I/ConfigStore") ||
                            logline.contains("/dalvikvm") ||
                            logline.contains("/DecorView") ||
                            logline.contains("/DpmTcmClient") ||
                            logline.contains("/EditorStubImpl") ||
                            logline.contains("/EGL") ||
                            logline.contains("/EpFrameworkFactory") ||
                            logline.contains("/eglCodecCommon") ||
                            logline.contains("/FeatureParser") ||
                            logline.contains("/FenceTime") ||
                            logline.contains("E/FileUtils err") ||
                            logline.contains("/FloatingActionMode") ||
                            logline.contains("/ForceDarkHelperStubImpl") ||
                            logline.contains("/FrameEvents") ||
                            logline.contains("/FramePredict") ||
                            logline.contains("/FrameTracker") ||
                            logline.contains("/Gralloc") ||
                            logline.contains("/HandWritingStubImpl") ||
                            logline.contains("/HWUI") ||
                            logline.contains("/IInputConnectionWrapper") ||
                            logline.contains("/ImeTracker") ||
                            logline.contains("/ImeBackDispatcher") ||
                            logline.contains("/ImeFocusController") ||
                            logline.contains("/InputEventReceiver") ||
                            logline.contains("/InputMethodManager") ||
                            logline.contains("/InsetsController") ||
                            logline.contains("/InsetsSourceConsumer") ||
                            logline.contains("/JavaheapMonitor") ||
                            logline.contains("E/LB") ||
                            logline.contains("W/Looper") ||
                            logline.contains("/libEGL") ||
                            logline.contains("/libMiGL") ||
                            logline.contains("E/libc") ||
                            logline.contains("W/libc") ||
                            logline.contains("/MessageMonitor") ||
                            logline.contains("/MIUI") ||
                            logline.contains("/MiInputConsumer") ||
                            logline.contains("/Miui") ||
                            logline.contains("MiuiBoosterUtils") ||
                            logline.contains("/NativeTurboSchedManager") ||
                            logline.contains("W/netstat") ||
                            logline.contains("/ngandroid.debu") ||
                            logline.contains("/OpenGLRenderer") ||
                            logline.contains("/PacProxySelector") ||
                            logline.contains("/Perf") ||
                            logline.contains("/re-initialized") ||
                            logline.contains("/RemoteInputConnectionImpl") ||
                            logline.contains("/RenderInspector") ||
                            logline.contains("/RenderThread") ||
                            logline.contains("/ResourceType") ||
                            logline.contains("W/sh") ||
                            logline.contains("W/Settings") ||
                            logline.contains("/SplineOverScroller") ||
                            logline.contains("/StrictMode") ||
                            logline.contains("/studio.deploy") ||
                            logline.contains("/SurfaceComposerClient") ||
                            logline.contains("/SurfaceSyncGroup") ||
                            logline.contains("I/System.out") ||
                            logline.contains("W/TextView") ||
                            logline.contains("I/Timeline") ||
                            logline.contains("I/VRI") ||
                            logline.contains("/VideoCapabilities") ||
                            logline.contains("/ViewRootImpl") ||
                            logline.contains("I/WebViewFactory") ||
                            logline.contains("WindowOnBackDispatcher") ||
                            logline.contains("I/X509Util") ||
                            logline.contains("/ziparchive") ||
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
     * Read or write log file.
     */
    private static String readLogFile(final File file) {
        String content = "";
        FileInputStream fileInputStream = null;
        try {
            if (file.exists()) {
                fileInputStream = new FileInputStream(file);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
                byte[] data = new byte[(int) file.length()];
                fileInputStream.read(data);
                content = new String(data, StandardCharsets.UTF_8);
            } else {
                // File not found.
                Log.e(TAG, "readLogFile: File missing '" + file.toString() + "'");
            }
        } catch (IOException e) {
            Log.e(TAG, "readLogFile: Failed to read '" + file.toString() + "' #1", e);
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "readLogFile: Failed to read '" + file.toString() + "' #2", e);
            }
        }
        return content;
    }

    private static void writeLogFile(final File file, String logContent) {
        FileOutputStream fileOutputStream = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(logContent.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.flush();
        } catch (IOException e) {
            Log.w(TAG, "writeLogFile: Failed to write '" + file.toString() + "' #1", e);
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "writeLogFile: Failed to write '" + file.toString() + "' #2", e);
            }
        }
    }

}
