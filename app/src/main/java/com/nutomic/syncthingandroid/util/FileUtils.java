package com.nutomic.syncthingandroid.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Utils for dealing with Storage Access Framework URIs.
 */
public class FileUtils {

    private static final String TAG = "FileUtils";

    // TargetApi(21)
    private static final Boolean isCompatible = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

    private FileUtils() {
        // Private constructor to enforce Singleton pattern.
    }

    private static final String DOWNLOADS_VOLUME_NAME = "downloads";
    private static final String PRIMARY_VOLUME_NAME = "primary";
    private static final String HOME_VOLUME_NAME = "home";

    @Nullable
    @TargetApi(21)
    public static String getAbsolutePathFromSAFUri(Context context, @Nullable final Uri safResultUri) {
        Uri treeUri = DocumentsContract.buildDocumentUriUsingTree(safResultUri,
            DocumentsContract.getTreeDocumentId(safResultUri));
        return getAbsolutePathFromTreeUri(context, treeUri);
    }

    @Nullable
    public static String getAbsolutePathFromTreeUri(Context context, @Nullable final Uri treeUri) {
        if (!isCompatible) {
            Log.e(TAG, "getAbsolutePathFromTreeUri: called on unsupported API level");
            return null;
        }
        if (treeUri == null) {
            Log.w(TAG, "getAbsolutePathFromTreeUri: called with treeUri == null");
            return null;
        }

        // Determine volumeId, e.g. "home", "documents"
        String volumeId = getVolumeIdFromTreeUri(treeUri);
        if (volumeId == null) {
            return null;
        }

        // Handle Uri referring to internal or external storage.
        String volumePath = getVolumePath(volumeId, context);
        if (volumePath == null) {
            return File.separator;
        }
        if (volumePath.endsWith(File.separator)) {
            volumePath = volumePath.substring(0, volumePath.length() - 1);
        }
        String documentPath = getDocumentPathFromTreeUri(treeUri);
        if (documentPath.endsWith(File.separator)) {
            documentPath = documentPath.substring(0, documentPath.length() - 1);
        }
        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator)) {
                return volumePath + documentPath;
            } else {
                return volumePath + File.separator + documentPath;
            }
        } else {
            return volumePath;
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @TargetApi(21)
    private static String getVolumePath(final String volumeId, Context context) {
        if (!isCompatible) {
            Log.e(TAG, "getVolumePath called on unsupported API level");
            return null;
        }
        try {
            if (HOME_VOLUME_NAME.equals(volumeId)) {
                Log.v(TAG, "getVolumePath: isHomeVolume");
                // Reading the environment var avoids hard coding the case of the "documents" folder.
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
            }
            if (DOWNLOADS_VOLUME_NAME.equals(volumeId)) {
                Log.v(TAG, "getVolumePath: isDownloadsVolume");
                // Reading the environment var avoids hard coding the case of the "downloads" folder.
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            }

            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);
                Boolean isPrimaryVolume = (primary && PRIMARY_VOLUME_NAME.equals(volumeId));
                Boolean isExternalVolume = ((uuid != null) && uuid.equals(volumeId));
                Log.d(TAG, "Found volume with uuid='" + uuid +
                    "', volumeId='" + volumeId +
                    "', primary=" + primary +
                    ", isPrimaryVolume=" + isPrimaryVolume +
                    ", isExternalVolume=" + isExternalVolume
                );
                if (isPrimaryVolume || isExternalVolume) {
                    Log.v(TAG, "getVolumePath: isPrimaryVolume || isExternalVolume");
                    // Return path if the correct volume corresponding to volumeId was found.
                    return (String) getPath.invoke(storageVolumeElement);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getVolumePath exception", e);
        }
        Log.e(TAG, "getVolumePath failed for volumeId='" + volumeId + "'");
        return null;
    }

    /**
     * FileProvider does not support converting the absolute path from
     * getExternalFilesDir() to a "content://" Uri. As "file://" Uri
     * has been blocked since Android 7+, we need to build the Uri
     * manually after discovering the first external storage.
     * This is crucial to assist the user finding a writeable folder
     * to use syncthing's two way sync feature.
     */
    @TargetApi(19)
    public static android.net.Uri getExternalFilesDirUri(Context context) {
        try {
            /**
             * Determine the app's private data folder on external storage if present.
             * e.g. "/storage/abcd-efgh/Android/[PACKAGE_NAME]/files"
             */
            ArrayList<File> externalFilesDir = new ArrayList<>();
            externalFilesDir.addAll(Arrays.asList(context.getExternalFilesDirs(null)));
            externalFilesDir.remove(context.getExternalFilesDir(null));
            if (externalFilesDir.size() == 0) {
                Log.w(TAG, "Could not determine app's private files directory on external storage.");
                return null;
            }
            String absPath = externalFilesDir.get(0).getAbsolutePath();
            String[] segments = absPath.split("/");
            if (segments.length < 2) {
                Log.w(TAG, "Could not extract volumeId from app's private files path '" + absPath + "'");
                return null;
            }
            // Extract the volumeId, e.g. "abcd-efgh"
            String volumeId = segments[2];
            // Build the content Uri for our private "files" folder.
            return android.net.Uri.parse(
                "content://com.android.externalstorage.documents/document/" +
                volumeId + "%3AAndroid%2Fdata%2F" +
                context.getPackageName() + "%2Ffiles");
        } catch (Exception e) {
            Log.w(TAG, "getExternalFilesDirUri exception", e);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) {
            return split[0];
        } else {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }

    @Nullable
    public static String cutTrailingSlash(final String path) {
        if (path.endsWith(File.separator)) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Deletes a directory recursively.
     * java.nio.file library is available since API level 26, see
     * https://developer.android.com/reference/java/nio/file/package-summary
     */
    @TargetApi(26)
    public static void deleteDirectoryRecursively(java.nio.file.Path pathToDelete) throws IOException {
        java.nio.file.Files.walk(pathToDelete)
            .sorted(Comparator.reverseOrder())
            .map(java.nio.file.Path::toFile)
            .forEach(File::delete);
    }
}
