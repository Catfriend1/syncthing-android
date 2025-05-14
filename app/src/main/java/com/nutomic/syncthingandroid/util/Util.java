package com.nutomic.syncthingandroid.util;

import android.app.Dialog;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.common.base.Charsets;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

import eu.chainfire.libsuperuser.Shell;

public class Util {

    private static final String TAG = "Util";

    private Util() {
    }

    /**
     * Copies the given device ID to the clipboard (and shows a Toast telling about it).
     *
     * @param id The device ID to copy.
     */
    public static void copyDeviceId(Context context, String id) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(context.getString(R.string.device_id), id);
        clipboard.setPrimaryClip(clip);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.device_id_copied_to_clipboard, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    /**
     * Converts a number of bytes to a human readable file size (eg 3.5 GiB).
     * <p>
     * Based on http://stackoverflow.com/a/5599842
     */
    public static String readableFileSize(Context context, double bytes) {
        final String[] units = context.getResources().getStringArray(R.array.file_size_units);
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Converts a number of bytes to a human readable transfer rate in bytes per second
     * (eg 100 KiB/s).
     * <p>
     * Based on http://stackoverflow.com/a/5599842
     */
    public static String readableTransferRate(Context context, long bits) {
        final String[] units = context.getResources().getStringArray(R.array.transfer_rate_units);
        long bytes = bits / 8;
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * <<<<<<< HEAD
     * Normally an application's data directory is only accessible by the corresponding application.
     * Therefore, every file and directory is owned by an application's user and group. When running Syncthing as root,
     * it writes to the application's data directory. This leaves files and directories behind which are owned by root having 0600.
     * Moreover, those actions performed as root changes a file's type in terms of SELinux.
     * A subsequent start of Syncthing will fail due to insufficient permissions.
     * Hence, this method fixes the owner, group and the files' type of the data directory.
     *
     * @return true if the operation was successfully performed. False otherwise.
     */
    public static boolean fixAppDataPermissions(Context context) {
        // We can safely assume that root magic is somehow available, because readConfig and saveChanges check for
        // read and write access before calling us.
        // Be paranoid :) and check if root is available.
        // Ignore the 'use_root' preference, because we might want to fix the permission
        // just after the root option has been disabled.
        if (!Shell.SU.available()) {
            Log.e(TAG, "Root is not available. Cannot fix permissions.");
            return false;
        }

        String packageName;
        ApplicationInfo appInfo;
        try {
            packageName = context.getPackageName();
            appInfo = context.getPackageManager().getApplicationInfo(packageName, 0);

        } catch (NameNotFoundException e) {
            // This should not happen!
            // One should always be able to retrieve the application info for its own package.
            Log.w(TAG, "Error getting current package name", e);
            return false;
        }
        Log.d(TAG, "Uid of '" + packageName + "' is " + appInfo.uid);

        // Get private app's "files" dir residing in "/data/data/[packageName]".
        String dir = context.getFilesDir().getAbsolutePath();
        String cmd = "chown -R " + appInfo.uid + ":" + appInfo.uid + " " + dir + "; ";
        // Running Syncthing as root might change a file's or directories type in terms of SELinux.
        // Leaving them as they are, the Android service won't be able to access them.
        // At least for those files residing in an application's data folder.
        // Simply reverting the type to its default should do the trick.
        cmd += "restorecon -R " + dir + "\n";
        Log.d(TAG, "Running: '" + cmd);
        int exitCode = runShellCommand(cmd, true);
        if (exitCode == 0) {
            Log.i(TAG, "Fixed app data permissions on '" + dir + "'.");
        } else {
            Log.w(TAG, "Failed to fix app data permissions on '" + dir + "'. Result: " +
                Integer.toString(exitCode));
        }
        return exitCode == 0;
    }

    /**
     * Returns if the syncthing binary would be able to write a file into
     * the given folder given the configured access level.
     */
    public static boolean nativeBinaryCanWriteToPath(Context context, String absoluteFolderPath) {
        final String TOUCH_FILE_NAME = ".stwritetest";
        Boolean useRoot = false;
        Boolean prefUseRoot = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(Constants.PREF_USE_ROOT, false);
        if (prefUseRoot && Shell.SU.available()) {
            useRoot = true;
        }

        // Write permission test file.
        String touchFile = absoluteFolderPath + "/" + TOUCH_FILE_NAME;
        int exitCode = runShellCommand("echo \"\" > \"" + touchFile + "\"\n", useRoot);
        if (exitCode != 0) {
            String error;
            switch (exitCode) {
                case 1:
                    error = "Permission denied";
                    break;
                default:
                    error = "Shell execution failed";
            }
            Log.i(TAG, "Failed to write test file '" + touchFile +
                "', " + error);
            return false;
        }

        // Detected we have write permission.
        Log.i(TAG, "Successfully wrote test file '" + touchFile + "'");

        // Remove test file.
        if (runShellCommand("rm \"" + touchFile + "\"\n", useRoot) != 0) {
            // This is very unlikely to happen, so we have less error handling.
            Log.i(TAG, "Failed to remove test file");
        }
        return true;
    }

    /**
     * Run command in a shell and return the exit code.
     */
    public static int runShellCommand(String cmd, Boolean useRoot) {
        // Assume "failure" exit code if an error is caught.
        // Note: redirectErrorStream(true); System.getProperty("line.separator");
        int exitCode = 255;
        Process shellProc = null;
        DataOutputStream shellOut = null;
        try {
            shellProc = Runtime.getRuntime().exec((useRoot) ? "su" : "sh");
            shellOut = new DataOutputStream(shellProc.getOutputStream());
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(shellOut));
            Log.d(TAG, "runShellCommand: " + cmd);
            bufferedWriter.write(cmd);
            bufferedWriter.flush();
            shellOut.close();
            shellOut = null;
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(shellProc.getInputStream(), Charsets.UTF_8));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    Log.v(TAG, "runShellCommand: " + line);
                }
            } catch (IOException e) {
                Log.w(TAG, "runShellCommand: Failed to read output", e);
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
            exitCode = shellProc.waitFor();
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, "runShellCommand: Exception", e);
        } finally {
            try {
                if (shellOut != null) {
                    shellOut.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "runShellCommand: Failed to close stream", e);
            }
            if (shellProc != null) {
                shellProc.destroy();
            }
        }
        return exitCode;
    }

    public static String runShellCommandGetOutput(String cmd, Boolean useRoot) {
        // Note: redirectErrorStream(true); System.getProperty("line.separator");
        int exitCode = 255;
        String capturedStdOut = "";
        Process shellProc = null;
        DataOutputStream shellOut = null;
        try {
            shellProc = Runtime.getRuntime().exec((useRoot) ? "su" : "sh");
            shellOut = new DataOutputStream(shellProc.getOutputStream());
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(shellOut));
            Log.d(TAG, "runShellCommandGetOutput: " + cmd);
            bufferedWriter.write(cmd);
            bufferedWriter.flush();
            shellOut.close();
            shellOut = null;
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(shellProc.getInputStream(), Charsets.UTF_8));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    // Log.i(TAG, "runShellCommandGetOutput: " + line);
                    capturedStdOut = capturedStdOut + line + "\n";
                }
            } catch (IOException e) {
                Log.w(TAG, "runShellCommandGetOutput: Failed to read output", e);
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
            exitCode = shellProc.waitFor();
            if (exitCode != 0) {
                Log.i(TAG, "runShellCommandGetOutput: Exited with code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, "runShellCommandGetOutput: Exception", e);
        } finally {
            try {
                if (shellOut != null) {
                    shellOut.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "runShellCommandGetOutput: Failed to close shell stream", e);
            }
            if (shellProc != null) {
                shellProc.destroy();
            }
        }

        // Return captured command line output.
        return capturedStdOut;
    }

    /**
     * Check if a TCP is listening on the local device on a specific port.
     */
    public static Boolean isTcpPortListening(Integer port) {
        // t: tcp, l: listening, n: numeric
        String output = runShellCommandGetOutput("netstat -t -l -n", false);
        if (TextUtils.isEmpty(output)) {
            Log.w(TAG, "isTcpPortListening: Failed to run netstat. Returning false.");
            return false;
        }
        String[] results  = output.split("\n");
        for (String line : results) {
            if (TextUtils.isEmpty(output)) {
                continue;
            }
            String[] words = line.split("\\s+");
            if (words.length > 5) {
                String protocol = words[0];
                String localAddress = words[3];
                String connState = words[5];
                if (protocol.equals("tcp") || protocol.equals("tcp6")) {
                    if (localAddress.endsWith(":" + Integer.toString(port)) &&
                            connState.equalsIgnoreCase("LISTEN")) {
                        // Port is listening.
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Make sure that dialog is showing and activity is valid before dismissing dialog, to prevent
     * various crashes.
     */
    public static void dismissDialogSafe(Dialog dialog, AppCompatActivity activity) {
        if (dialog == null || !dialog.isShowing())
            return;

        if (activity.isFinishing())
            return;

        if (activity.isDestroyed())
            return;

        dialog.dismiss();
    }

    /**
     * Format a path properly.
     *
     * @param path String containing the path that needs formatting.
     * @return formatted file path as a string.
     */
    public static String formatPath(String path) {
        return new File(path).toURI().normalize().getPath();
    }

    /**
     * Shorten a path using ellipsis to display it on UI
     * where we have little space to display it.
     */
    public static final String getPathEllipsis(final String fullFN) {
        final boolean FUNC_LOG_D = false;
        final boolean FUNC_LOG_V = false;
        final int MAX_CHARS_SUBDIR = 15;
        final int MAX_CHARS_FILENAME = MAX_CHARS_SUBDIR * 2;

        int index;
        String part;
        String workIn = fullFN;
        String workOut = "";
        while(true) {
            index = workIn.indexOf('/');
            if (index < 0) {
                // Last part is the filename.
                if (FUNC_LOG_V) {
                    Log.v(TAG, "getPathEllipsis: workIn [" + workIn + "] @ index <= 0");
                }
                if (workIn.length() > MAX_CHARS_FILENAME) {
                    int indexFileExt = workIn.lastIndexOf(".");
                    if (indexFileExt > 0) {
                        // Filename with extension.
                        String fileName = workIn.substring(0, indexFileExt);
                        if (fileName.length() > MAX_CHARS_FILENAME) {
                            fileName = fileName.substring(0, MAX_CHARS_FILENAME) + "\u22ef";
                        }
                        workIn = fileName + workIn.substring(indexFileExt);
                    } else {
                        // Filename without extension
                        workIn = workIn.substring(0, MAX_CHARS_FILENAME) + "\u22ef";
                    }
                }
                workOut += workIn;
                break;
            }
            // Handle one directory from the path.
            part = workIn.substring(0, index);
            if (FUNC_LOG_V) {
                Log.v(TAG, "getPathEllipsis: part [" + part + "]");
            }
            if (part.length() > MAX_CHARS_SUBDIR) {
                part = part.substring(0, MAX_CHARS_SUBDIR) + "\u22ef";
            }
            workOut += part + "/";
            workIn = workIn.substring(index + 1);
            if (FUNC_LOG_V) {
                Log.v(TAG, "getPathEllipsis: workIn [" + workIn + "], workOut [" + workOut + "]");
            }
        }
        if (FUNC_LOG_D) {
            Log.v(TAG, "getPathEllipsis: INP [" + fullFN + "]");
            Log.v(TAG, "getPathEllipsis: OUT [" + workOut + "]");
        }
        return workOut;
    }

    public static void testPathEllipsis() {
        getPathEllipsis("");
        getPathEllipsis("/");
        getPathEllipsis("//");
        getPathEllipsis("go2sync.dll");
        getPathEllipsis("MY-LINK-/Cool 12345 Configuration Utility/sdk/bin/go2sync.dll");
        getPathEllipsis("MY-LINK-Iam-bin-sum-another-and-make-long/Cool 12345 Configuration Utility/sdk/bin/go2sync.dll");
        getPathEllipsis("MY-LINK-Iam-bin-sum-another-and-make-long-textfiles-are-cool.txt");
        getPathEllipsis("MY-LINK-/Cool 12345 Configuration Utility/sdk/bin-Iam-bin-sum-another-and-make-long/go2sync-long-textfiles-are-cool.dll");
        getPathEllipsis("MY-LINK-//Cool 12345 Configuration Utility/sdk/bin-Iam-bin-sum-another-and-make-long/go2sync-long-textfiles-are-cool.dll");
        getPathEllipsis("MY-LINK-//Cool 12345 Configuration Utility/sdk/bin-Iam-bin-sum-another-and-make-long//go2sync-long-textfiles-are-cool.dll");
        getPathEllipsis("MY-LINK-//Cool 12345 Configuration Utility/sdk/bin-Iam-bin-sum-another-and-make-long//go2sync-long-textfiles-are-cool");
        getPathEllipsis("MY-LINK-//Cool 12345 Configuration Utility/sdk/bin-Iam-bin-sum-another-and-make-long//go2sync-long-textfiles-are-cool.correctlongeextensionswassolldas-denn-bitte");
        getPathEllipsis("MY-LINK-Iam-bin-sum-another-and-make-long/Cool 12345 Configuration Utility/sdk/bin/go2sync.dllcorrectlongeextensionswassolldas-denn-bitte");
    }

    public static boolean containsIgnoreCase(String src, String what) {
        final int length = what.length();
        if (length == 0) {
            return true;
        }
        for (int i = src.length() - length; i >= 0; i--) {
            if (src.regionMatches(true, i, what, 0, length)) {
                return true;
            }
        }
        return false;
    }

    public static Boolean isRunningOnTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    /**
     * Converts dateTime to readable localized string.
     */
    public static String formatDateTime(String dateTime) {
        // Convert dateTime to readable localized string.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return dateTime;
        }

        ZonedDateTime parsedDateTime = ZonedDateTime.parse(dateTime);
        ZonedDateTime zonedDateTime = parsedDateTime.withZoneSameInstant(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());
        return formatter.format(zonedDateTime);
    }

    public static String formatTime(String dateTime) {
        // Convert dateTime to readable localized string.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return dateTime;
        }

        ZonedDateTime parsedDateTime = ZonedDateTime.parse(dateTime);
        ZonedDateTime zonedDateTime = parsedDateTime.withZoneSameInstant(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());
        return formatter.format(zonedDateTime);
    }

    /**
     * Converts local time to ZonedDateTime.
     */
    public static String getLocalZonedDateTime() {
        // Example: "2021-02-11T22:11:29.356Z"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "2021-02-11T22:11:29.356Z";
        }
        return ZonedDateTime.ofLocal(LocalDateTime.now(), ZoneId.of("UTC"), ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    
    /**
     * Called by RestApi/setRemoteCompletionInfo after folder completed.
     */
    public static void runScriptSet(final String absPath, final String[] scriptArgs) {
        File scriptFolder = new File(absPath);
        if (!scriptFolder.exists() || !scriptFolder.isDirectory()) {
            Log.w(TAG, "runScriptSet: Folder does not exist or is not of type folder: " + absPath);
            return;
        }

        // Find all script files within given folder path.
        File[] scriptFiles = scriptFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase(Locale.ROOT).endsWith(".sh");
            }
        });
        if (scriptFiles == null || scriptFiles.length == 0) {
            Log.v(TAG, "runScriptSet: No script files found within folder: " + absPath);
            return;
        }
        for (File scriptFile : scriptFiles) {
            // Build arguments using shell escape.
            StringBuilder cmdBuilder = new StringBuilder();
            cmdBuilder.append("cd \"").append(absPath).append("/..\";");
            cmdBuilder.append("sh \"").append(scriptFile.getAbsolutePath()).append("\"");
            if (scriptArgs != null) {
                for (String arg : scriptArgs) {
                    cmdBuilder.append(" \"").append(arg.replace("\"", "\\\"")).append("\"");
                }
            }

            // Execute script.
            String command = cmdBuilder.toString();
            // Log.d(TAG, "runScriptSet: Exec [" + command + "]");
            Log.v(TAG, "runScriptSet: Exec result [" + runShellCommandGetOutput(command, false) + "]");
        }
    }
    
    /**
     * Called by RestApi/setRemoteCompletionInfo after folder completed.
     */
    public static String[] getSyncConflictFiles(final String absPath) {
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("cd \"").append(absPath).append("/\";");
        // Unescaped:
        //  find -type f -name "*\.sync-conflict-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]*" -not -path "\.\/\.stversions\/*" -print | sed "s~\\.\/~~"
        cmdBuilder.append("find -type f -name \"*\\.sync-conflict-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-[a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9][a-zA-Z0-9]*\" -not -path \"\\.\\/\\" + Constants.FOLDER_NAME_STVERSIONS + "\\/*\" -print | sed \"s~\\\\.\\/~~\"");
        String command = cmdBuilder.toString();
        // Log.v(TAG, "getSyncConflictFileCount: Exec [" + command + "]");
        String output = runShellCommandGetOutput(command, false);
        // Log.v(TAG, "getSyncConflictFileCount: Exec result [" + output + "]");
        if (output == null || output.isEmpty()) {
            return new String[]{};
        }
        return output.split("\\n");
    }
}
