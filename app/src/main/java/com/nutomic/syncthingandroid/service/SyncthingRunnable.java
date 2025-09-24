package com.nutomic.syncthingandroid.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.Util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import eu.chainfire.libsuperuser.Shell;

import static com.nutomic.syncthingandroid.service.SyncthingService.EXTRA_STOP_AFTER_CRASHED_NATIVE;

/**
 * Runs the syncthing binary from command line, and prints its output to logcat.
 *
 * @see <a href="http://docs.syncthing.net/users/syncthing.html">Command Line Docs</a>
 */
public class SyncthingRunnable implements Runnable {

    private static final String TAG = "SyncthingRunnable";
    private static final String TAG_NATIVE = "SyncthingNativeCode";
    private static final String TAG_NICE = "SyncthingRunnableIoNice";

    private Boolean ENABLE_VERBOSE_LOG = false;
    // private Boolean IS_DEBUGGABLE = false;
    private static final int LOG_FILE_MAX_LINES = 200000;

    private static final AtomicReference<Process> mSyncthing = new AtomicReference<>();
    private final Context mContext;
    private final File mSyncthingBinary;
    private String[] mCommand;
    private final File mSyncthingLogFile;
    private final boolean mUseRoot;

    @Inject
    SharedPreferences mPreferences;

    @Inject
    NotificationHandler mNotificationHandler;

    public enum Command {
        deviceid,           // Output the device ID to the command line.
        generate,           // Generate keys, a config file and immediately exit.
        main,               // Run the main Syncthing application.
        resetdatabase,      // Reset Syncthing's database
        resetdeltas,        // Reset Syncthing's delta indexes
    }

    /**
     * Constructs instance.
     *
     * @param command Which type of Syncthing command to execute.
     */
    public SyncthingRunnable(Context context, Command command) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        // IS_DEBUGGABLE = Constants.isDebuggable(context);
        mContext = context;
        // Example: mSyncthingBinary="/data/app/${applicationId}-8HsN-IsVtZXc8GrE5-Hepw==/lib/x86/libsyncthingnative.so"
        mSyncthingBinary = Constants.getSyncthingBinary(mContext);
        mSyncthingLogFile = Constants.getSyncthingLogFile(mContext);

        // Get preferences relevant to starting syncthing core.
        mUseRoot = mPreferences.getBoolean(Constants.PREF_USE_ROOT, false) && Shell.SU.available();
        switch (command) {
            case deviceid:
                mCommand = new String[]{mSyncthingBinary.getPath(), "device-id"};
                break;
            case generate:
                mCommand = new String[]{mSyncthingBinary.getPath(), "generate"};
                break;
            case main:
                mCommand = new String[]{mSyncthingBinary.getPath(), "serve", "--no-browser"};
                break;
            case resetdatabase:
                mCommand = new String[]{mSyncthingBinary.getPath(), "debug", "reset-database"};
                break;
            case resetdeltas:
                mCommand = new String[]{mSyncthingBinary.getPath(), "serve", "--debug-reset-delta-idxs"};
                break;
            default:
                throw new InvalidParameterException("Unknown command option");
        }
    }

    @Override
    public void run() {
        try {
            run(false);
        } catch (ExecutableNotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @SuppressLint("WakelockTimeout")
    public String run(boolean returnStdOut) throws ExecutableNotFoundException {
        Boolean sendStopToService = false;
        Boolean restartSyncthingNative = false;
        int exitCode;
        String capturedStdOut = "";

        // Trim Syncthing log.
        trimSyncthingLogFile();

        /**
         * Keep the CPU running while native binary is running.
         * Only valid on Android 5 or lower.
         */
        PowerManager pm;
        PowerManager.WakeLock wakeLock = null;
        Boolean useWakeLock = mPreferences.getBoolean(Constants.PREF_USE_WAKE_LOCK, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && useWakeLock) {
            pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            /**
             * Since gradle 4.6, wakelock tags have to obey "app:component" naming convention.
             */
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    mContext.getString(R.string.app_name) + ":" + TAG
            );
        }

        MulticastLock multicastLock = null;
        Process process = null;
        try {
            if (wakeLock != null) {
                wakeLock.acquire();
            }

            // Android 11 blocks local discovery if we did not acquire MulticastLock.
            WifiManager wifi = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifi.createMulticastLock("multicastLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            /**
             * Setup and run a new syncthing instance
             */
            increaseInotifyWatches();
            HashMap<String, String> targetEnv = buildEnvironment();
            process = setupAndLaunch(targetEnv);

            mSyncthing.set(process);

            Thread lInfo = null;
            Thread lWarn = null;
            if (returnStdOut) {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(process.getInputStream(), Charsets.UTF_8));
                    String line;
                    while ((line = br.readLine()) != null) {
                        Log.i(TAG_NATIVE, line);
                        capturedStdOut = capturedStdOut + line + "\n";
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed to read Syncthing's command line output", e);
                } finally {
                    if (br != null)
                        br.close();
                }
            } else {
                lInfo = log(process.getInputStream(), Log.INFO);
                lWarn = log(process.getErrorStream(), Log.WARN);
            }

            niceSyncthing();

            exitCode = process.waitFor();
            LogV("Syncthing exited with code " + exitCode);
            mSyncthing.set(null);
            if (lInfo != null) {
                lInfo.join();
            }
            if (lWarn != null) {
                lWarn.join();
            }

            switch (exitCode) {
                case 0:
                case 137:
                    Log.i(TAG, "Syncthing was shut down normally via API or SIGKILL. Exit code = " + exitCode);
                    break;
                case 1:
                    Log.w(TAG, "exit reason = exitError. Another Syncthing instance may be already running.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                case 2:
                    // This should not happen as STNOUPGRADE is set.
                    Log.w(TAG, "exit reason = exitNoUpgradeAvailable. Another Syncthing instance may be already running.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                case 3:
                    // Restart was requested via Rest API call.
                    Log.i(TAG, "exit reason = exitRestarting. Restarting syncthing.");
                    restartSyncthingNative = true;
                    break;
                case 9:
                    // Native was force killed.
                    Log.w(TAG, "exit reason = exitForceKill.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                case 64:
                    Log.w(TAG, "exit reason = exitInvalidCommandLine.");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
                    break;
                default:
                    Log.w(TAG, "Syncthing exited unexpectedly. Exit code = " + exitCode);
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, Integer.toString(exitCode));
                    sendStopToService = true;
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to execute syncthing binary or read output", e);
        } finally {
            if (wakeLock != null) {
                wakeLock.release();
            }
            if (multicastLock != null) {
                multicastLock.release();
                multicastLock = null;
            }
            if (process != null) {
                process.destroy();
            }
        }

        // Restart syncthing if it exited unexpectedly while running on a separate thread.
        if (!returnStdOut && restartSyncthingNative) {
            mContext.startService(new Intent(mContext, SyncthingService.class)
                    .setAction(SyncthingService.ACTION_RESTART));
        }

        // Notify {@link SyncthingService} that service state State.ACTIVE is no longer valid.
        if (!returnStdOut && sendStopToService) {
            Intent intent = new Intent(mContext, SyncthingService.class);
            intent.setAction(SyncthingService.ACTION_STOP);
            intent.putExtra(EXTRA_STOP_AFTER_CRASHED_NATIVE, true);
            mContext.startService(intent);
        }

        // Return captured command line output.
        return capturedStdOut;
    }

    private void putCustomEnvironmentVariables(Map<String, String> environment, SharedPreferences sp) {
        String customEnvironment = sp.getString(Constants.PREF_ENVIRONMENT_VARIABLES, null);
        if (TextUtils.isEmpty(customEnvironment))
            return;

        for (String e : customEnvironment.split(" ")) {
            String[] e2 = e.split("=", 2);
            LogV("Setting env var: [" + e2[0] + "]=[" + e2[1] + "]");
            environment.put(e2[0], e2[1]);
        }
    }

    /**
     * Look for running libsyncthingnative.so processes and return an array
     * containing the PIDs of found instances.
     */
    private List<String> getSyncthingPIDs(Boolean enableLog) {
        List<String> syncthingPIDs = new ArrayList<String>();
        String output = Util.runShellCommandGetOutput("ps\n", mUseRoot);
        if (TextUtils.isEmpty(output)) {
            Log.w(TAG, "Failed to list SyncthingNative processes. ps command returned empty.");
            return syncthingPIDs;
        }

        String lines[] = output.split("\n");
        if (lines.length == 0) {
            Log.w(TAG, "Failed to list SyncthingNative processes. ps command returned no rows.");
            return syncthingPIDs;
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains(Constants.FILENAME_SYNCTHING_BINARY)) {
                String syncthingPID = line.trim().split("\\s+")[1];
                if (enableLog) {
                    Log.v(TAG, "getSyncthingPIDs: Found process PID [" + syncthingPID + "]");
                }
                syncthingPIDs.add(syncthingPID);
            }
        }
        return syncthingPIDs;
    }

    /**
     * Root-only: Temporarily increase "fs.inotify.max_user_watches"
     * as Android has a default limit of 8192 watches.
     * Manually run "sysctl fs.inotify" in a root shell terminal to check current limit.
     */
    private void increaseInotifyWatches() {
        if (!mUseRoot) {
            // Settings prohibit using root privileges. Cannot increase inotify limit.
            return;
        }
        if (!Shell.SU.available()) {
            Log.i(TAG, "increaseInotifyWatches: Root is not available. Cannot increase inotify limit.");
            return;
        }
        int exitCode = Util.runShellCommand("sysctl -n -w fs.inotify.max_user_watches=131072\n", true);
        Log.i(TAG, "increaseInotifyWatches: sysctl returned " + Integer.toString(exitCode));
    }

    /**
     * Look for a running libsyncthingnative.so process and nice its IO.
     */
    private void niceSyncthing() {
        if (!mUseRoot) {
            // Settings prohibit using root privileges. Cannot nice syncthing.
            return;
        }
        if (!Shell.SU.available()) {
            Log.i(TAG_NICE, "Root is not available. Cannot nice syncthing.");
            return;
        }

        List<String> syncthingPIDs = getSyncthingPIDs(false);
        if (syncthingPIDs.isEmpty()) {
            Log.i(TAG_NICE, "Found no running instances of " + Constants.FILENAME_SYNCTHING_BINARY);
            return;
        }

        // Ionice all running syncthing processes.
        for (String syncthingPID : syncthingPIDs) {
            // Set best-effort, low priority using ionice.
            int exitCode = Util.runShellCommand("/system/bin/ionice " + syncthingPID + " be 7\n", true);
            Log.i(TAG_NICE, "ionice returned " + Integer.toString(exitCode) +
                    " on " + Constants.FILENAME_SYNCTHING_BINARY);
        }
    }

    /**
     * Look for running libsyncthingnative.so processes and end them gracefully.
     */
    public void killSyncthing() {
        int exitCode;
        List<String> syncthingPIDs = getSyncthingPIDs(true);
        if (syncthingPIDs.isEmpty()) {
            LogV("killSyncthing: Found no running instances of " + Constants.FILENAME_SYNCTHING_BINARY);
            return;
        }
        for (String syncthingPID : syncthingPIDs) {
            exitCode = Util.runShellCommand("kill -SIGINT " + syncthingPID + "\n", mUseRoot);
            if (exitCode == 0) {
                LogV("Sent kill SIGINT to process " + syncthingPID);
            } else {
                Log.w(TAG, "Failed to send kill SIGINT to process " + syncthingPID +
                        " exit code " + Integer.toString(exitCode));
            }
        }

        /**
         * Wait for the syncthing instance to end.
         */
        LogV("Waiting for all syncthing instances to end ...");
        while (!getSyncthingPIDs(false).isEmpty()) {
            SystemClock.sleep(50);
        }
        Log.d(TAG, "killSyncthing: Complete.");
    }

    /**
     * Logs the outputs of a stream to logcat and mNativeLog.
     *
     * @param is       The stream to log.
     * @param priority The priority level.
     * @param saveLog  True if the log should be stored to {@link #mSyncthingLogFile}.
     */
    private Thread log(final InputStream is, final int priority) {
        Thread t = new Thread(() -> {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    /*
                    if (IS_DEBUGGABLE) {
                        String lineWithoutTimestamp = line.replaceFirst("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2} ?", "");
                        Log.println(priority, TAG_NATIVE, lineWithoutTimestamp);
                    }
                    */
                    // Always output SynchtingNative's output to "syncthing.log".
                    Files.append(line + "\n", mSyncthingLogFile, Charsets.UTF_8);
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to read Syncthing's command line output", e);
            }
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.w(TAG, "log: Failed to close bufferedReader", e);
                }
            }
        });
        t.start();
        return t;
    }

    /**
     * Only keep last {@link #LOG_FILE_MAX_LINES} lines in log file, to avoid bloat.
     */
    private void trimSyncthingLogFile() {
        if (!mSyncthingLogFile.exists()) {
            return;
        }

        try {
            LineNumberReader lnr = new LineNumberReader(new FileReader(mSyncthingLogFile));
            lnr.skip(Long.MAX_VALUE);

            int lineCount = lnr.getLineNumber();
            lnr.close();

            File tempFile = new File(mContext.getFilesDir().toString(), "syncthing.log.tmp");

            BufferedReader reader = new BufferedReader(new FileReader(mSyncthingLogFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String currentLine;
            int startFrom = lineCount - LOG_FILE_MAX_LINES;
            for (int i = 0; (currentLine = reader.readLine()) != null; i++) {
                if (i > startFrom) {
                    writer.write(currentLine + "\n");
                }
            }
            writer.close();
            reader.close();
            tempFile.renameTo(mSyncthingLogFile);
        } catch (IOException e) {
            Log.w(TAG, "Failed to trim log file", e);
        }
    }

    private HashMap<String, String> buildEnvironment() {
        HashMap<String, String> targetEnv = new HashMap<>();

        // Set home directory to data folder for web GUI folder picker.
        targetEnv.put("HOME", FileUtils.getSyncthingTildeAbsolutePath());

        // Set config, key and database directory.
        targetEnv.put("STHOMEDIR", mContext.getFilesDir().toString());
        targetEnv.put("STTRACE", TextUtils.join(" ",
                mPreferences.getStringSet(Constants.PREF_DEBUG_FACILITIES_ENABLED, new HashSet<>())));
        targetEnv.put("STMONITORED", "1");
        targetEnv.put("STNOUPGRADE", "1");

        // Database tuning against slowness.
        targetEnv.put("SQLITE_TMPDIR", mContext.getCacheDir().getAbsolutePath());

        // Workaround SyncthingNativeCode denied to read gatewayIP by Android 14+ restriction.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String gatewayIpV4 = getGatewayIpV4(mContext);
            if (gatewayIpV4 != null) {
                targetEnv.put("FALLBACK_NET_GATEWAY_IPV4", gatewayIpV4);
            }
        }

        if (mPreferences.getBoolean(Constants.PREF_USE_TOR, false)) {
            targetEnv.put("all_proxy", "socks5://localhost:9050");
            targetEnv.put("ALL_PROXY_NO_FALLBACK", "1");
        } else {
            String socksProxyAddress = mPreferences.getString(Constants.PREF_SOCKS_PROXY_ADDRESS, "");
            if (!socksProxyAddress.equals("")) {
                targetEnv.put("all_proxy", socksProxyAddress);
            }

            String httpProxyAddress = mPreferences.getString(Constants.PREF_HTTP_PROXY_ADDRESS, "");
            if (!httpProxyAddress.equals("")) {
                targetEnv.put("http_proxy", httpProxyAddress);
                targetEnv.put("https_proxy", httpProxyAddress);
            }
        }

        // Optimize memory usage for older devices.
        int gogc = 100;         // GO default
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            gogc = 50;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            gogc = 75;
        }
        LogV("Setting env var: [GOGC]=[" + Integer.toString(gogc) + "]");
        targetEnv.put("GOGC", Integer.toString(gogc));

        putCustomEnvironmentVariables(targetEnv, mPreferences);
        return targetEnv;
    }

    private Process setupAndLaunch(HashMap<String, String> env) throws IOException, ExecutableNotFoundException {
        // Check if "libsyncthingnative.so" exists.
        if (mCommand.length > 0) {
            File libSyncthing = new File(mCommand[0]);
            if (!libSyncthing.exists()) {
                Log.e(TAG, "CRITICAL - Syncthing core binary is missing in APK package location " + mCommand[0]);
                throw new ExecutableNotFoundException(mCommand[0]);
            }
        }

        if (mUseRoot) {
            ProcessBuilder pb = new ProcessBuilder("su");
            Process process = pb.start();
            // The su binary prohibits the inheritance of environment variables.
            // Even with --preserve-environment the environment gets messed up.
            // We therefore start a root shell, and set all the environment variables manually.
            DataOutputStream suOut = new DataOutputStream(process.getOutputStream());
            for (Map.Entry<String, String> entry : env.entrySet()) {
                suOut.writeBytes(String.format("export %s=\"%s\"\n", entry.getKey(), entry.getValue()));
            }
            suOut.flush();
            // Exec will replace the su process image by Syncthing as execlp in C does.
            // Without using exec, the process will drop to the root shell as soon as Syncthing terminates like a normal shell does.
            // If we did not use exec, we would wait infinitely for the process to terminate (ret = process.waitFor(); in run()).
            // With exec the whole process terminates when Syncthing exits.
            suOut.writeBytes("exec " + TextUtils.join(" ", mCommand) + "\n");
            suOut.flush();
            return process;
        } else {
            ProcessBuilder pb = new ProcessBuilder(mCommand);
            pb.environment().putAll(env);
            return pb.start();
        }
    }

    public class ExecutableNotFoundException extends Exception {

        public ExecutableNotFoundException(String message) {
            super(message);
        }

        public ExecutableNotFoundException(String message, Throwable throwable) {
            super(message, throwable);
        }

    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static String getGatewayIpV4(final Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return null;

        LinkProperties props = cm.getLinkProperties(activeNetwork);
        if (props == null) return null;

        for (RouteInfo route : props.getRoutes()) {
            InetAddress gateway = route.getGateway();
            if (route.isDefaultRoute() && gateway instanceof Inet4Address) {
                return gateway.getHostAddress();
            }
        }
        return null;
    }
}
