package com.nutomic.syncthingandroid.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;

import java.io.DataOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

import eu.chainfire.libsuperuser.Shell;

public class Util {

    private static final String TAG = "SyncthingUtil";

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
        Toast.makeText(context, R.string.device_id_copied_to_clipboard, Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * Converts a number of bytes to a human readable file size (eg 3.5 GiB).
     *
     * Based on http://stackoverflow.com/a/5599842
     */
    public static String readableFileSize(Context context, long bytes) {
        final String[] units = context.getResources().getStringArray(R.array.file_size_units);
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Converts a number of bytes to a human readable transfer rate in bytes per second
     * (eg 100 KiB/s).
     *
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
     * Normally an application's data directory is only accessible by the corresponding application.
     * Therefore, every file and directory is owned by an application's user and group. When running Syncthing as root,
     * it writes to the application's data directory. This leaves files and directories behind which are owned by root having 0600.
     * Moreover, those acitons performed as root changes a file's type in terms of SELinux.
     * A subsequent start of Syncthing will fail due to insufficient permissions.
     * Hence, this method fixes the owner, group and the files' type of the data directory.
     * 
     * @return true if the operation was successfully performed. False otherwise.
     */
    public static boolean fixAppDataPermissions(Context context) {
        // We can safely assume that root magic is somehow available, because readConfig and saveChanges check for
        // read and write access before calling us.
        // Be paranoid :) and check if root is available.
        // Ignore the 'use_root' preference, because we might want to fix ther permission
        // just after the root option has been disabled.
        if (!Shell.SU.available()) {
            Log.e(TAG, "Root is not available. Cannot fix permssions.");
            return false;
        }

        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            Log.d(TAG, "Uid of '" + context.getPackageName() + "' is " + appInfo.uid);
            Process fixPerm = Runtime.getRuntime().exec("su");
            DataOutputStream fixPermOut = new DataOutputStream(fixPerm.getOutputStream());
            String dir = context.getFilesDir().getAbsolutePath();
            String cmd = "chown -R " + appInfo.uid + ":" + appInfo.uid + " " + dir + "\n";
            Log.d(TAG, "Running: '" + cmd);
            fixPermOut.writeBytes(cmd);
            // Running Syncthing as root might change a file's or directories type in terms of SELinux.
            // Leaving them as they are, the Android service won't be able to access them.
            // At least for those files residing in an application's data folder.
            // Simply reverting the type to its default should do the trick.
            cmd = "restorecon -R " + dir + "\n";
            Log.d(TAG, "Running: '" + cmd);
            fixPermOut.writeBytes(cmd);
            fixPermOut.flush();
            fixPermOut.close();
            int ret = fixPerm.waitFor();
            Log.i(TAG, "Changed the owner, the group and the SELinux context of '" + dir + "'. Result: " + ret);
            return ret == 0;
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, "Cannot chown data directory", e);
        } catch (NameNotFoundException e) {
            // This should not happen!
            // One should always be able to retrieve the application info for its own package.
            Log.w(TAG, "This should not happen", e);
        }
        return false;
    }

    /**
     * Make sure that dialog is showing and activity is valid before dismissing dialog, to prevent
     * various crashes.
     */
    public static void dismissDialogSafe(Dialog dialog, Activity activity) {
        if (dialog == null || !dialog.isShowing())
            return;

        if (activity.isFinishing())
            return;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN && activity.isDestroyed())
            return;

        dialog.dismiss();
    }
}
