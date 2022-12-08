package com.nutomic.syncthingandroid.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nutomic.syncthingandroid.R;

public class PermissionUtil {

    private static final String TAG = "PermissionUtil";

    public static boolean haveStoragePermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            int permissionState = ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return permissionState == PackageManager.PERMISSION_GRANTED;
        }
        return Environment.isExternalStorageManager();
    }

    public static void requestStoragePermission(@NonNull Activity activity, final int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    requestCode);
            return;
        }
        Boolean intentFailed = false;
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        try {
            ComponentName componentName = intent.resolveActivity(activity.getPackageManager());
            if (componentName != null) {
                String className = componentName.getClassName();
                if (className != null) {
                    // Launch "Allow all files access?" dialog.
                    activity.startActivity(intent);
                    return;
                }
                intentFailed = true;
            } else {
                Log.w(TAG, "Request all files access not supported");
                intentFailed = true;
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Request all files access not supported", e);
            intentFailed = true;
        }
        if (intentFailed) {
            // Some devices don't support this request.
            Toast.makeText(activity, R.string.dialog_all_files_access_not_supported, Toast.LENGTH_LONG).show();
        }
    }

}
