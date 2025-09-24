package com.nutomic.syncthingandroid.util;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.nutomic.syncthingandroid.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Utils for dealing with Storage Access Framework URIs.
 */
public class FileUtils {

    private static final String TAG = "FileUtils";

    private FileUtils() {
        // Private constructor to enforce Singleton pattern.
    }

    private static final String DOWNLOADS_VOLUME_NAME = "downloads";
    private static final String PRIMARY_VOLUME_NAME = "primary";
    private static final String HOME_VOLUME_NAME = "home";

    public enum ExternalStorageDirType {
        DATA,
        EXT_MEDIA,
        INT_MEDIA
    }

    public static android.net.Uri convertFromDocumentUriToTreeUri(Uri documentUri) {
        // IN: content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fmedia%2F${applicationId}
        // OUT: content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia%2F${applicationId}
        String authority = documentUri.getAuthority();
        String documentId = DocumentsContract.getDocumentId(documentUri);
        return DocumentsContract.buildTreeDocumentUri(authority, documentId);
    }
    
    public static boolean directoryUriExists(final Context context, Uri documentUri) {
        Uri treeUri = convertFromDocumentUriToTreeUri(documentUri);
        String absPath = getAbsolutePathFromSAFUri(context, treeUri);
        return new File(absPath).exists();
    }
    
    @Nullable
    public static String getAbsolutePathFromSAFUri(final Context context, @Nullable final Uri safResultUri) {
        Uri treeUri = DocumentsContract.buildDocumentUriUsingTree(safResultUri,
            DocumentsContract.getTreeDocumentId(safResultUri));
        return getAbsolutePathFromTreeUri(context, treeUri);
    }

    @Nullable
    public static String getAbsolutePathFromTreeUri(final Context context, @Nullable final Uri treeUri) {
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

    private static List<String> getMountedStoragePaths() {
        List<String> mountPaths = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("/storage/") || line.contains("/mnt/media_rw/")) {
                    String[] parts = line.split(" ");
                    String mountPoint = parts[1];

                    // Filter
                    if ((mountPoint.startsWith("/storage/") || mountPoint.startsWith("/mnt/media_rw/"))
                            && !mountPoint.contains("emulated")
                            && new File(mountPoint).isDirectory()) {
                        mountPaths.add(mountPoint);
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "getMountedStoragePaths: Error reading /proc/mounts", e);
        }
        return mountPaths;
    }

    public static File[] getMountedStoragePathsAsFileArray() {
        List<String> paths = getMountedStoragePaths();
        List<File> files = new ArrayList<>();
        for (String path : paths) {
            File f = new File(path);
            if (f.canRead()) {
                files.add(f);
            }
        }
        return files.toArray(new File[0]);
    }

    @SuppressLint("ObsoleteSdkInt")
    private static String getVolumePath(final String volumeId, final Context context) {
        try {
            if (HOME_VOLUME_NAME.equals(volumeId)) {
                Log.v(TAG, "getVolumePath: isHomeVolume");
                // Reading the environment var avoids hard coding the case of the "documents" folder.
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
            }
            if (DOWNLOADS_VOLUME_NAME.equals(volumeId)) {
                Log.v(TAG, "getVolumePath: isDownloadsVolume");
                return getExternalStorageDownloadsDirectory();
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
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.w(TAG, "getVolumePath failed for volumeId='" + volumeId + "'");
            if (volumeId.equals("primary")) {
                Log.d(TAG, "volumeId == primary");
                return getInternalStorageRootAbsolutePath();
            }
            return "/storage/" + volumeId;
        // }
        // Log.e(TAG, "getVolumePath failed for volumeId='" + volumeId + "'");
        // return null;
    }

    public static File getExternalFilesDir(final Context context, String type) {
        return getExternalFilesDir(context, ExternalStorageDirType.DATA, type);
    }

    public static File getExternalFilesDir(final Context context, ExternalStorageDirType extDirType, String type) {
        /**
         * Determine the app's private data folder on external storage if present.
         * e.g. "/storage/abcd-efgh/Android/data/[PACKAGE_NAME]/files"
         * e.g. "/storage/abcd-efgh/Android/media/[PACKAGE_NAME]"
         */
        ArrayList<File> externalFilesDir = new ArrayList<>();
        switch(extDirType){
            case DATA:
                externalFilesDir.addAll(Arrays.asList(ContextCompat.getExternalFilesDirs(context, null)));
                if (externalFilesDir.size() > 1) {
                    // There is a bug on Huawei devices running Android 7, which returns the wrong external path.
                    // That's why we use ContextCompat here instead of context.
                    // See: https://stackoverflow.com/questions/39895579/fileprovider-error-onhuawei-devices
                    externalFilesDir.remove(context.getExternalFilesDir(null));
                }
                break;
            case INT_MEDIA:
                externalFilesDir.add(new File(Environment.getExternalStorageDirectory() + "/Android/media/" + context.getPackageName()));
                break;
            case EXT_MEDIA:
                externalFilesDir.addAll(Arrays.asList(context.getExternalMediaDirs()));
                if (!externalFilesDir.isEmpty()) {
                    externalFilesDir.remove(externalFilesDir.get(0));
                }
                break;
        }
        externalFilesDir.remove(null);      // getExternalFilesDirs may return null for an ejected SDcard.
        if (externalFilesDir.isEmpty()) {
            Log.w(TAG, "Could not determine app's private files directory on external storage.");
            return null;
        }
        if (type != null) {
            switch(extDirType) {
                case EXT_MEDIA:
                case INT_MEDIA:
                    if (type.equals(Environment.DIRECTORY_PICTURES)) {
                        return new File(externalFilesDir.get(0), Environment.DIRECTORY_PICTURES);
                    }
            }
        }
        return externalFilesDir.get(0);
    }

    /**
     * FileProvider does not support converting the absolute path from
     * getExternalFilesDir() to a "content://" Uri. As "file://" Uri
     * has been blocked since Android 7+, we need to build the Uri
     * manually after discovering the first external storage.
     * This is crucial to assist the user finding a writeable folder
     * to use Syncthing's two way sync feature.
     * API for getExternalFilesDirs(): 19+ (KITKAT+)
     * API for getExternalMediaDirs(): 21+ (LOLLIPOP+)
     */
    public static android.net.Uri getExternalFilesDirUri(final Context context, ExternalStorageDirType extDirType) {
        try {
            File externalFilesDir = getExternalFilesDir(context, extDirType, null);
            if (externalFilesDir == null) {
                Log.w(TAG, "Could not determine app's private files directory on external storage.");
                return null;
            }
            String absPath = externalFilesDir.getAbsolutePath();

            // Log.v(TAG, "getExternalFilesDirUri: absPath=" + absPath);
            String[] segments = absPath.split("/");
            if (segments.length < 2) {
                Log.w(TAG, "Could not extract volumeId from app's private files path '" + absPath + "'");
                return null;
            }
            // Extract the volumeId, e.g. "abcd-efgh"
            String volumeId = segments[2];
            switch(extDirType) {
                case DATA:
                    // Build the content Uri for our private ".../data/[PKG_NAME]/files" folder.
                    return android.net.Uri.parse(
                        "content://com.android.externalstorage.documents/document/" +
                        volumeId + "%3AAndroid%2Fdata%2F" +
                        context.getPackageName() + "%2Ffiles");
                case EXT_MEDIA:
                    // Build the content Uri for our private ".../media/[PKG_NAME]" folder.
                    return android.net.Uri.parse(
                        "content://com.android.externalstorage.documents/document/" +
                        volumeId + "%3AAndroid%2Fmedia%2F" +
                        context.getPackageName());
                case INT_MEDIA:
                    // Build the content Uri for our private ".../media/[PKG_NAME]" folder.
                    return android.net.Uri.parse(
                        "content://com.android.externalstorage.documents/document/" +
                        "primary" + "%3AAndroid%2Fmedia%2F" +
                        context.getPackageName());
            }
        } catch (Exception e) {
            Log.w(TAG, "getExternalFilesDirUri exception", e);
        }
        return null;
    }

    /**
     * FileProvider does not support converting absolute paths
     * to a "content://" Uri. As "file://" Uri has been blocked
     * since Android 7+, we need to build the Uri manually.
     */
    public static android.net.Uri getInternalStorageRootUri() {
        return android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
    }

    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) {
            return split[0];
        } else {
            return null;
        }
    }

    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }

    /**
     * Reading the environment var avoids hard coding the absolute path of the "/Download" folder.
     */
    public static String getExternalStorageDownloadsDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
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
     */
    public static boolean deleteDirectoryRecursively(File dir) throws IOException {
        if (dir == null || !dir.exists()) return false;
        if (dir.isFile()) return dir.delete();

        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                deleteDirectoryRecursively(entry);
            }
        }
        return dir.delete();
    }
    
    /**
     * Expands the "~" path.
     * Equals SyncthingRunnable env "HOME"
     * Result: e.g. /storage/emulated/0/syncthing
     */
    public static String getSyncthingTildeAbsolutePath() {
        return FileUtils.getInternalStorageRootAbsolutePath() + "/syncthing";
    }
    
    private static String getInternalStorageRootAbsolutePath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    /**
     * Derives the mime type from file extension.
     */
    public static String getMimeTypeFromFileExtension(String fileExtension) {
        HashMap<String, String> mimeTypes = new HashMap<String, String>();
        mimeTypes.put("323", "text/h323");
        mimeTypes.put("3g2", "video/3gpp2");
        mimeTypes.put("3gp", "video/3gpp");
        mimeTypes.put("3gp2", "video/3gpp2");
        mimeTypes.put("3gpp", "video/3gpp");
        mimeTypes.put("7z", "application/x-7z-compressed");
        mimeTypes.put("aa", "audio/audible");
        mimeTypes.put("aac", "audio/aac");
        mimeTypes.put("aaf", "application/octet-stream");
        mimeTypes.put("aax", "audio/vnd.audible.aax");
        mimeTypes.put("ac3", "audio/ac3");
        mimeTypes.put("aca", "application/octet-stream");
        mimeTypes.put("accda", "application/msaccess.addin");
        mimeTypes.put("accdb", "application/msaccess");
        mimeTypes.put("accdc", "application/msaccess.cab");
        mimeTypes.put("accde", "application/msaccess");
        mimeTypes.put("accdr", "application/msaccess.runtime");
        mimeTypes.put("accdt", "application/msaccess");
        mimeTypes.put("accdw", "application/msaccess.webapplication");
        mimeTypes.put("accft", "application/msaccess.ftemplate");
        mimeTypes.put("acx", "application/internet-property-stream");
        mimeTypes.put("addin", "text/xml");
        mimeTypes.put("ade", "application/msaccess");
        mimeTypes.put("adobebridge", "application/x-bridge-url");
        mimeTypes.put("adp", "application/msaccess");
        mimeTypes.put("adt", "audio/vnd.dlna.adts");
        mimeTypes.put("adts", "audio/aac");
        mimeTypes.put("afm", "application/octet-stream");
        mimeTypes.put("ai", "application/postscript");
        mimeTypes.put("aif", "audio/aiff");
        mimeTypes.put("aifc", "audio/aiff");
        mimeTypes.put("aiff", "audio/aiff");
        mimeTypes.put("air", "application/vnd.adobe.air-application-installer-package+zip");
        mimeTypes.put("amc", "application/mpeg");
        mimeTypes.put("anx", "application/annodex");
        mimeTypes.put("apk", "application/vnd.android.package-archive");
        mimeTypes.put("application", "application/x-ms-application");
        mimeTypes.put("art", "image/x-jg");
        mimeTypes.put("asa", "application/xml");
        mimeTypes.put("asax", "application/xml");
        mimeTypes.put("ascx", "application/xml");
        mimeTypes.put("asd", "application/octet-stream");
        mimeTypes.put("asf", "video/x-ms-asf");
        mimeTypes.put("ashx", "application/xml");
        mimeTypes.put("asi", "application/octet-stream");
        mimeTypes.put("asm", "text/plain");
        mimeTypes.put("asmx", "application/xml");
        mimeTypes.put("aspx", "application/xml");
        mimeTypes.put("asr", "video/x-ms-asf");
        mimeTypes.put("asx", "video/x-ms-asf");
        mimeTypes.put("atom", "application/atom+xml");
        mimeTypes.put("au", "audio/basic");
        mimeTypes.put("avi", "video/x-msvideo");
        mimeTypes.put("axa", "audio/annodex");
        mimeTypes.put("axs", "application/olescript");
        mimeTypes.put("axv", "video/annodex");
        mimeTypes.put("bas", "text/plain");
        mimeTypes.put("bcpio", "application/x-bcpio");
        mimeTypes.put("bin", "application/octet-stream");
        mimeTypes.put("bmp", "image/bmp");
        mimeTypes.put("c", "text/plain");
        mimeTypes.put("cab", "application/octet-stream");
        mimeTypes.put("caf", "audio/x-caf");
        mimeTypes.put("calx", "application/vnd.ms-office.calx");
        mimeTypes.put("cat", "application/vnd.ms-pki.seccat");
        mimeTypes.put("cc", "text/plain");
        mimeTypes.put("cd", "text/plain");
        mimeTypes.put("cdda", "audio/aiff");
        mimeTypes.put("cdf", "application/x-cdf");
        mimeTypes.put("cer", "application/x-x509-ca-cert");
        mimeTypes.put("cfg", "text/plain");
        mimeTypes.put("chm", "application/octet-stream");
        mimeTypes.put("class", "application/x-java-applet");
        mimeTypes.put("clp", "application/x-msclip");
        mimeTypes.put("cmd", "text/plain");
        mimeTypes.put("cmx", "image/x-cmx");
        mimeTypes.put("cnf", "text/plain");
        mimeTypes.put("cod", "image/cis-cod");
        mimeTypes.put("config", "application/xml");
        mimeTypes.put("contact", "text/x-ms-contact");
        mimeTypes.put("coverage", "application/xml");
        mimeTypes.put("cpio", "application/x-cpio");
        mimeTypes.put("cpp", "text/plain");
        mimeTypes.put("crd", "application/x-mscardfile");
        mimeTypes.put("crl", "application/pkix-crl");
        mimeTypes.put("crt", "application/x-x509-ca-cert");
        mimeTypes.put("cs", "text/plain");
        mimeTypes.put("csdproj", "text/plain");
        mimeTypes.put("csh", "application/x-csh");
        mimeTypes.put("csproj", "text/plain");
        mimeTypes.put("css", "text/css");
        mimeTypes.put("csv", "text/csv");
        mimeTypes.put("cur", "application/octet-stream");
        mimeTypes.put("cxx", "text/plain");
        mimeTypes.put("dat", "application/octet-stream");
        mimeTypes.put("datasource", "application/xml");
        mimeTypes.put("dbproj", "text/plain");
        mimeTypes.put("dcr", "application/x-director");
        mimeTypes.put("def", "text/plain");
        mimeTypes.put("deploy", "application/octet-stream");
        mimeTypes.put("der", "application/x-x509-ca-cert");
        mimeTypes.put("dgml", "application/xml");
        mimeTypes.put("dib", "image/bmp");
        mimeTypes.put("dif", "video/x-dv");
        mimeTypes.put("dir", "application/x-director");
        mimeTypes.put("disco", "text/xml");
        mimeTypes.put("divx", "video/divx");
        mimeTypes.put("dll", "application/x-msdownload");
        mimeTypes.put("dll.config", "text/xml");
        mimeTypes.put("dlm", "text/dlm");
        mimeTypes.put("dng", "image/x-adobe-dng");
        mimeTypes.put("doc", "application/msword");
        mimeTypes.put("docm", "application/vnd.ms-word.document.macroEnabled.12");
        mimeTypes.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypes.put("dot", "application/msword");
        mimeTypes.put("dotm", "application/vnd.ms-word.template.macroEnabled.12");
        mimeTypes.put("dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template");
        mimeTypes.put("dsp", "application/octet-stream");
        mimeTypes.put("dsw", "text/plain");
        mimeTypes.put("dtd", "text/xml");
        mimeTypes.put("dtsconfig", "text/xml");
        mimeTypes.put("dv", "video/x-dv");
        mimeTypes.put("dvi", "application/x-dvi");
        mimeTypes.put("dwf", "drawing/x-dwf");
        mimeTypes.put("dwp", "application/octet-stream");
        mimeTypes.put("dxr", "application/x-director");
        mimeTypes.put("eml", "message/rfc822");
        mimeTypes.put("emz", "application/octet-stream");
        mimeTypes.put("eot", "application/vnd.ms-fontobject");
        mimeTypes.put("eps", "application/postscript");
        mimeTypes.put("etl", "application/etl");
        mimeTypes.put("etx", "text/x-setext");
        mimeTypes.put("evy", "application/envoy");
        mimeTypes.put("exe", "application/octet-stream");
        mimeTypes.put("exe.config", "text/xml");
        mimeTypes.put("fdf", "application/vnd.fdf");
        mimeTypes.put("fif", "application/fractals");
        mimeTypes.put("filters", "application/xml");
        mimeTypes.put("fla", "application/octet-stream");
        mimeTypes.put("flac", "audio/flac");
        mimeTypes.put("flr", "x-world/x-vrml");
        mimeTypes.put("flv", "video/x-flv");
        mimeTypes.put("fsscript", "application/fsharp-script");
        mimeTypes.put("fsx", "application/fsharp-script");
        mimeTypes.put("generictest", "application/xml");
        mimeTypes.put("gif", "image/gif");
        mimeTypes.put("group", "text/x-ms-group");
        mimeTypes.put("gsm", "audio/x-gsm");
        mimeTypes.put("gtar", "application/x-gtar");
        mimeTypes.put("gz", "application/x-gzip");
        mimeTypes.put("h", "text/plain");
        mimeTypes.put("hdf", "application/x-hdf");
        mimeTypes.put("hdml", "text/x-hdml");
        mimeTypes.put("hhc", "application/x-oleobject");
        mimeTypes.put("hhk", "application/octet-stream");
        mimeTypes.put("hhp", "application/octet-stream");
        mimeTypes.put("hlp", "application/winhlp");
        mimeTypes.put("hpp", "text/plain");
        mimeTypes.put("hqx", "application/mac-binhex40");
        mimeTypes.put("hta", "application/hta");
        mimeTypes.put("htc", "text/x-component");
        mimeTypes.put("htm", "text/html");
        mimeTypes.put("html", "text/html");
        mimeTypes.put("htt", "text/webviewhtml");
        mimeTypes.put("hxa", "application/xml");
        mimeTypes.put("hxc", "application/xml");
        mimeTypes.put("hxd", "application/octet-stream");
        mimeTypes.put("hxe", "application/xml");
        mimeTypes.put("hxf", "application/xml");
        mimeTypes.put("hxh", "application/octet-stream");
        mimeTypes.put("hxi", "application/octet-stream");
        mimeTypes.put("hxk", "application/xml");
        mimeTypes.put("hxq", "application/octet-stream");
        mimeTypes.put("hxr", "application/octet-stream");
        mimeTypes.put("hxs", "application/octet-stream");
        mimeTypes.put("hxt", "text/html");
        mimeTypes.put("hxv", "application/xml");
        mimeTypes.put("hxw", "application/octet-stream");
        mimeTypes.put("hxx", "text/plain");
        mimeTypes.put("i", "text/plain");
        mimeTypes.put("ico", "image/x-icon");
        mimeTypes.put("ics", "text/calendar");
        mimeTypes.put("idl", "text/plain");
        mimeTypes.put("ief", "image/ief");
        mimeTypes.put("iii", "application/x-iphone");
        mimeTypes.put("inc", "text/plain");
        mimeTypes.put("inf", "application/octet-stream");
        mimeTypes.put("ini", "text/plain");
        mimeTypes.put("inl", "text/plain");
        mimeTypes.put("ins", "application/x-internet-signup");
        mimeTypes.put("ipa", "application/x-itunes-ipa");
        mimeTypes.put("ipg", "application/x-itunes-ipg");
        mimeTypes.put("ipproj", "text/plain");
        mimeTypes.put("ipsw", "application/x-itunes-ipsw");
        mimeTypes.put("iqy", "text/x-ms-iqy");
        mimeTypes.put("isp", "application/x-internet-signup");
        mimeTypes.put("ite", "application/x-itunes-ite");
        mimeTypes.put("itlp", "application/x-itunes-itlp");
        mimeTypes.put("itms", "application/x-itunes-itms");
        mimeTypes.put("itpc", "application/x-itunes-itpc");
        mimeTypes.put("ivf", "video/x-ivf");
        mimeTypes.put("jar", "application/java-archive");
        mimeTypes.put("java", "application/octet-stream");
        mimeTypes.put("jck", "application/liquidmotion");
        mimeTypes.put("jcz", "application/liquidmotion");
        mimeTypes.put("jfif", "image/pjpeg");
        mimeTypes.put("jnlp", "application/x-java-jnlp-file");
        mimeTypes.put("jpb", "application/octet-stream");
        mimeTypes.put("jpe", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("js", "application/javascript");
        mimeTypes.put("json", "application/json");
        mimeTypes.put("jsx", "text/jscript");
        mimeTypes.put("jsxbin", "text/plain");
        mimeTypes.put("latex", "application/x-latex");
        mimeTypes.put("library-ms", "application/windows-library+xml");
        mimeTypes.put("lit", "application/x-ms-reader");
        mimeTypes.put("loadtest", "application/xml");
        mimeTypes.put("lpk", "application/octet-stream");
        mimeTypes.put("lsf", "video/x-la-asf");
        mimeTypes.put("lst", "text/plain");
        mimeTypes.put("lsx", "video/x-la-asf");
        mimeTypes.put("lzh", "application/octet-stream");
        mimeTypes.put("m13", "application/x-msmediaview");
        mimeTypes.put("m14", "application/x-msmediaview");
        mimeTypes.put("m1v", "video/mpeg");
        mimeTypes.put("m2t", "video/vnd.dlna.mpeg-tts");
        mimeTypes.put("m2ts", "video/vnd.dlna.mpeg-tts");
        mimeTypes.put("m2v", "video/mpeg");
        mimeTypes.put("m3u", "audio/x-mpegurl");
        mimeTypes.put("m3u8", "audio/x-mpegurl");
        mimeTypes.put("m4a", "audio/m4a");
        mimeTypes.put("m4b", "audio/m4b");
        mimeTypes.put("m4p", "audio/m4p");
        mimeTypes.put("m4r", "audio/x-m4r");
        mimeTypes.put("m4v", "video/x-m4v");
        mimeTypes.put("mac", "image/x-macpaint");
        mimeTypes.put("mak", "text/plain");
        mimeTypes.put("man", "application/x-troff-man");
        mimeTypes.put("manifest", "application/x-ms-manifest");
        mimeTypes.put("map", "text/plain");
        mimeTypes.put("master", "application/xml");
        mimeTypes.put("mda", "application/msaccess");
        mimeTypes.put("mdb", "application/x-msaccess");
        mimeTypes.put("mde", "application/msaccess");
        mimeTypes.put("mdp", "application/octet-stream");
        mimeTypes.put("me", "application/x-troff-me");
        mimeTypes.put("mfp", "application/x-shockwave-flash");
        mimeTypes.put("mht", "message/rfc822");
        mimeTypes.put("mhtml", "message/rfc822");
        mimeTypes.put("mid", "audio/mid");
        mimeTypes.put("midi", "audio/mid");
        mimeTypes.put("mix", "application/octet-stream");
        mimeTypes.put("mk", "text/plain");
        mimeTypes.put("mkv", "video/x-matroska");
        mimeTypes.put("mmf", "application/x-smaf");
        mimeTypes.put("mno", "text/xml");
        mimeTypes.put("mny", "application/x-msmoney");
        mimeTypes.put("mod", "video/mpeg");
        mimeTypes.put("mov", "video/quicktime");
        mimeTypes.put("movie", "video/x-sgi-movie");
        mimeTypes.put("mp2", "video/mpeg");
        mimeTypes.put("mp2v", "video/mpeg");
        mimeTypes.put("mp3", "audio/mpeg");
        mimeTypes.put("mp4", "video/mp4");
        mimeTypes.put("mp4v", "video/mp4");
        mimeTypes.put("mpa", "video/mpeg");
        mimeTypes.put("mpe", "video/mpeg");
        mimeTypes.put("mpeg", "video/mpeg");
        mimeTypes.put("mpf", "application/vnd.ms-mediapackage");
        mimeTypes.put("mpg", "video/mpeg");
        mimeTypes.put("mpp", "application/vnd.ms-project");
        mimeTypes.put("mpv2", "video/mpeg");
        mimeTypes.put("mqv", "video/quicktime");
        mimeTypes.put("ms", "application/x-troff-ms");
        mimeTypes.put("msi", "application/octet-stream");
        mimeTypes.put("mso", "application/octet-stream");
        mimeTypes.put("mts", "video/vnd.dlna.mpeg-tts");
        mimeTypes.put("mtx", "application/xml");
        mimeTypes.put("mvb", "application/x-msmediaview");
        mimeTypes.put("mvc", "application/x-miva-compiled");
        mimeTypes.put("mxp", "application/x-mmxp");
        mimeTypes.put("nc", "application/x-netcdf");
        mimeTypes.put("nomedia", "application/octet-stream");
        mimeTypes.put("nsc", "video/x-ms-asf");
        mimeTypes.put("nws", "message/rfc822");
        mimeTypes.put("ocx", "application/octet-stream");
        mimeTypes.put("oda", "application/oda");
        mimeTypes.put("odb", "application/vnd.oasis.opendocument.database");
        mimeTypes.put("odc", "application/vnd.oasis.opendocument.chart");
        mimeTypes.put("odf", "application/vnd.oasis.opendocument.formula");
        mimeTypes.put("odg", "application/vnd.oasis.opendocument.graphics");
        mimeTypes.put("odh", "text/plain");
        mimeTypes.put("odi", "application/vnd.oasis.opendocument.image");
        mimeTypes.put("odl", "text/plain");
        mimeTypes.put("odm", "application/vnd.oasis.opendocument.text-master");
        mimeTypes.put("odp", "application/vnd.oasis.opendocument.presentation");
        mimeTypes.put("ods", "application/vnd.oasis.opendocument.spreadsheet");
        mimeTypes.put("odt", "application/vnd.oasis.opendocument.text");
        mimeTypes.put("oga", "audio/ogg");
        mimeTypes.put("ogg", "audio/ogg");
        mimeTypes.put("ogv", "video/ogg");
        mimeTypes.put("ogx", "application/ogg");
        mimeTypes.put("one", "application/onenote");
        mimeTypes.put("onea", "application/onenote");
        mimeTypes.put("onepkg", "application/onenote");
        mimeTypes.put("onetmp", "application/onenote");
        mimeTypes.put("onetoc", "application/onenote");
        mimeTypes.put("onetoc2", "application/onenote");
        mimeTypes.put("opus", "audio/ogg");
        mimeTypes.put("orderedtest", "application/xml");
        mimeTypes.put("osdx", "application/opensearchdescription+xml");
        mimeTypes.put("otf", "application/font-sfnt");
        mimeTypes.put("otg", "application/vnd.oasis.opendocument.graphics-template");
        mimeTypes.put("oth", "application/vnd.oasis.opendocument.text-web");
        mimeTypes.put("otp", "application/vnd.oasis.opendocument.presentation-template");
        mimeTypes.put("ots", "application/vnd.oasis.opendocument.spreadsheet-template");
        mimeTypes.put("ott", "application/vnd.oasis.opendocument.text-template");
        mimeTypes.put("oxt", "application/vnd.openofficeorg.extension");
        mimeTypes.put("p10", "application/pkcs10");
        mimeTypes.put("p12", "application/x-pkcs12");
        mimeTypes.put("p7b", "application/x-pkcs7-certificates");
        mimeTypes.put("p7c", "application/pkcs7-mime");
        mimeTypes.put("p7m", "application/pkcs7-mime");
        mimeTypes.put("p7r", "application/x-pkcs7-certreqresp");
        mimeTypes.put("p7s", "application/pkcs7-signature");
        mimeTypes.put("pbm", "image/x-portable-bitmap");
        mimeTypes.put("pcast", "application/x-podcast");
        mimeTypes.put("pct", "image/pict");
        mimeTypes.put("pcx", "application/octet-stream");
        mimeTypes.put("pcz", "application/octet-stream");
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("pfb", "application/octet-stream");
        mimeTypes.put("pfm", "application/octet-stream");
        mimeTypes.put("pfx", "application/x-pkcs12");
        mimeTypes.put("pgm", "image/x-portable-graymap");
        mimeTypes.put("php", "text/plain");
        mimeTypes.put("pic", "image/pict");
        mimeTypes.put("pict", "image/pict");
        mimeTypes.put("pkgdef", "text/plain");
        mimeTypes.put("pkgundef", "text/plain");
        mimeTypes.put("pko", "application/vnd.ms-pki.pko");
        mimeTypes.put("pls", "audio/scpls");
        mimeTypes.put("pma", "application/x-perfmon");
        mimeTypes.put("pmc", "application/x-perfmon");
        mimeTypes.put("pml", "application/x-perfmon");
        mimeTypes.put("pmr", "application/x-perfmon");
        mimeTypes.put("pmw", "application/x-perfmon");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("pnm", "image/x-portable-anymap");
        mimeTypes.put("pnt", "image/x-macpaint");
        mimeTypes.put("pntg", "image/x-macpaint");
        mimeTypes.put("pnz", "image/png");
        mimeTypes.put("pot", "application/vnd.ms-powerpoint");
        mimeTypes.put("potm", "application/vnd.ms-powerpoint.template.macroEnabled.12");
        mimeTypes.put("potx", "application/vnd.openxmlformats-officedocument.presentationml.template");
        mimeTypes.put("ppa", "application/vnd.ms-powerpoint");
        mimeTypes.put("ppam", "application/vnd.ms-powerpoint.addin.macroEnabled.12");
        mimeTypes.put("ppm", "image/x-portable-pixmap");
        mimeTypes.put("pps", "application/vnd.ms-powerpoint");
        mimeTypes.put("ppsm", "application/vnd.ms-powerpoint.slideshow.macroEnabled.12");
        mimeTypes.put("ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        mimeTypes.put("ppt", "application/vnd.ms-powerpoint");
        mimeTypes.put("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12");
        mimeTypes.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        mimeTypes.put("prf", "application/pics-rules");
        mimeTypes.put("prm", "application/octet-stream");
        mimeTypes.put("prx", "application/octet-stream");
        mimeTypes.put("ps", "application/postscript");
        mimeTypes.put("psc1", "application/PowerShell");
        mimeTypes.put("psd", "application/octet-stream");
        mimeTypes.put("psess", "application/xml");
        mimeTypes.put("psm", "application/octet-stream");
        mimeTypes.put("psp", "application/octet-stream");
        mimeTypes.put("pub", "application/x-mspublisher");
        mimeTypes.put("pwz", "application/vnd.ms-powerpoint");
        mimeTypes.put("py", "text/plain");
        mimeTypes.put("qht", "text/x-html-insertion");
        mimeTypes.put("qhtm", "text/x-html-insertion");
        mimeTypes.put("qt", "video/quicktime");
        mimeTypes.put("qti", "image/x-quicktime");
        mimeTypes.put("qtif", "image/x-quicktime");
        mimeTypes.put("qtl", "application/x-quicktimeplayer");
        mimeTypes.put("qxd", "application/octet-stream");
        mimeTypes.put("ra", "audio/x-pn-realaudio");
        mimeTypes.put("ram", "audio/x-pn-realaudio");
        mimeTypes.put("rar", "application/x-rar-compressed");
        mimeTypes.put("ras", "image/x-cmu-raster");
        mimeTypes.put("rat", "application/rat-file");
        mimeTypes.put("rb", "text/plain");
        mimeTypes.put("rc", "text/plain");
        mimeTypes.put("rc2", "text/plain");
        mimeTypes.put("rct", "text/plain");
        mimeTypes.put("rdlc", "application/xml");
        mimeTypes.put("reg", "text/plain");
        mimeTypes.put("resx", "application/xml");
        mimeTypes.put("rf", "image/vnd.rn-realflash");
        mimeTypes.put("rgb", "image/x-rgb");
        mimeTypes.put("rgs", "text/plain");
        mimeTypes.put("rm", "application/vnd.rn-realmedia");
        mimeTypes.put("rmi", "audio/mid");
        mimeTypes.put("rmp", "application/vnd.rn-rn_music_package");
        mimeTypes.put("roff", "application/x-troff");
        mimeTypes.put("rpm", "audio/x-pn-realaudio-plugin");
        mimeTypes.put("rqy", "text/x-ms-rqy");
        mimeTypes.put("rtf", "application/rtf");
        mimeTypes.put("rtx", "text/richtext");
        mimeTypes.put("ruleset", "application/xml");
        mimeTypes.put("s", "text/plain");
        mimeTypes.put("safariextz", "application/x-safari-safariextz");
        mimeTypes.put("scd", "application/x-msschedule");
        mimeTypes.put("scr", "text/plain");
        mimeTypes.put("sct", "text/scriptlet");
        mimeTypes.put("sd2", "audio/x-sd2");
        mimeTypes.put("sdp", "application/sdp");
        mimeTypes.put("sea", "application/octet-stream");
        mimeTypes.put("searchConnector-ms", "application/windows-search-connector+xml");
        mimeTypes.put("setpay", "application/set-payment-initiation");
        mimeTypes.put("setreg", "application/set-registration-initiation");
        mimeTypes.put("settings", "application/xml");
        mimeTypes.put("sgimb", "application/x-sgimb");
        mimeTypes.put("sgml", "text/sgml");
        mimeTypes.put("sh", "application/x-sh");
        mimeTypes.put("shar", "application/x-shar");
        mimeTypes.put("shtml", "text/html");
        mimeTypes.put("sit", "application/x-stuffit");
        mimeTypes.put("sitemap", "application/xml");
        mimeTypes.put("skin", "application/xml");
        mimeTypes.put("sldm", "application/vnd.ms-powerpoint.slide.macroEnabled.12");
        mimeTypes.put("sldx", "application/vnd.openxmlformats-officedocument.presentationml.slide");
        mimeTypes.put("slk", "application/vnd.ms-excel");
        mimeTypes.put("sln", "text/plain");
        mimeTypes.put("slupkg-ms", "application/x-ms-license");
        mimeTypes.put("smd", "audio/x-smd");
        mimeTypes.put("smi", "application/octet-stream");
        mimeTypes.put("smx", "audio/x-smd");
        mimeTypes.put("smz", "audio/x-smd");
        mimeTypes.put("snd", "audio/basic");
        mimeTypes.put("snippet", "application/xml");
        mimeTypes.put("snp", "application/octet-stream");
        mimeTypes.put("sol", "text/plain");
        mimeTypes.put("sor", "text/plain");
        mimeTypes.put("spc", "application/x-pkcs7-certificates");
        mimeTypes.put("spl", "application/futuresplash");
        mimeTypes.put("spx", "audio/ogg");
        mimeTypes.put("src", "application/x-wais-source");
        mimeTypes.put("srf", "text/plain");
        mimeTypes.put("ssisdeploymentmanifest", "text/xml");
        mimeTypes.put("ssm", "application/streamingmedia");
        mimeTypes.put("sst", "application/vnd.ms-pki.certstore");
        mimeTypes.put("stl", "application/vnd.ms-pki.stl");
        mimeTypes.put("sv4cpio", "application/x-sv4cpio");
        mimeTypes.put("sv4crc", "application/x-sv4crc");
        mimeTypes.put("svc", "application/xml");
        mimeTypes.put("svg", "image/svg+xml");
        mimeTypes.put("swf", "application/x-shockwave-flash");
        mimeTypes.put("t", "application/x-troff");
        mimeTypes.put("tar", "application/x-tar");
        mimeTypes.put("tcl", "application/x-tcl");
        mimeTypes.put("testrunconfig", "application/xml");
        mimeTypes.put("testsettings", "application/xml");
        mimeTypes.put("tex", "application/x-tex");
        mimeTypes.put("texi", "application/x-texinfo");
        mimeTypes.put("texinfo", "application/x-texinfo");
        mimeTypes.put("tgz", "application/x-compressed");
        mimeTypes.put("thmx", "application/vnd.ms-officetheme");
        mimeTypes.put("thn", "application/octet-stream");
        mimeTypes.put("tif", "image/tiff");
        mimeTypes.put("tiff", "image/tiff");
        mimeTypes.put("tlh", "text/plain");
        mimeTypes.put("tli", "text/plain");
        mimeTypes.put("toc", "application/octet-stream");
        mimeTypes.put("tr", "application/x-troff");
        mimeTypes.put("trm", "application/x-msterminal");
        mimeTypes.put("trx", "application/xml");
        mimeTypes.put("ts", "video/vnd.dlna.mpeg-tts");
        mimeTypes.put("tsv", "text/tab-separated-values");
        mimeTypes.put("ttf", "application/font-sfnt");
        mimeTypes.put("tts", "video/vnd.dlna.mpeg-tts");
        mimeTypes.put("txt", "text/plain");
        mimeTypes.put("u32", "application/octet-stream");
        mimeTypes.put("uls", "text/iuls");
        mimeTypes.put("user", "text/plain");
        mimeTypes.put("ustar", "application/x-ustar");
        mimeTypes.put("vb", "text/plain");
        mimeTypes.put("vbdproj", "text/plain");
        mimeTypes.put("vbk", "video/mpeg");
        mimeTypes.put("vbproj", "text/plain");
        mimeTypes.put("vbs", "text/vbscript");
        mimeTypes.put("vcf", "text/x-vcard");
        mimeTypes.put("vcproj", "application/xml");
        mimeTypes.put("vcs", "text/plain");
        mimeTypes.put("vcxproj", "application/xml");
        mimeTypes.put("vddproj", "text/plain");
        mimeTypes.put("vdp", "text/plain");
        mimeTypes.put("vdproj", "text/plain");
        mimeTypes.put("vdx", "application/vnd.ms-visio.viewer");
        mimeTypes.put("vml", "text/xml");
        mimeTypes.put("vscontent", "application/xml");
        mimeTypes.put("vsct", "text/xml");
        mimeTypes.put("vsd", "application/vnd.visio");
        mimeTypes.put("vsi", "application/ms-vsi");
        mimeTypes.put("vsix", "application/vsix");
        mimeTypes.put("vsixlangpack", "text/xml");
        mimeTypes.put("vsixmanifest", "text/xml");
        mimeTypes.put("vsmdi", "application/xml");
        mimeTypes.put("vspscc", "text/plain");
        mimeTypes.put("vss", "application/vnd.visio");
        mimeTypes.put("vsscc", "text/plain");
        mimeTypes.put("vssettings", "text/xml");
        mimeTypes.put("vssscc", "text/plain");
        mimeTypes.put("vst", "application/vnd.visio");
        mimeTypes.put("vstemplate", "text/xml");
        mimeTypes.put("vsto", "application/x-ms-vsto");
        mimeTypes.put("vsw", "application/vnd.visio");
        mimeTypes.put("vsx", "application/vnd.visio");
        mimeTypes.put("vtx", "application/vnd.visio");
        mimeTypes.put("wav", "audio/wav");
        mimeTypes.put("wave", "audio/wav");
        mimeTypes.put("wax", "audio/x-ms-wax");
        mimeTypes.put("wbk", "application/msword");
        mimeTypes.put("wbmp", "image/vnd.wap.wbmp");
        mimeTypes.put("wcm", "application/vnd.ms-works");
        mimeTypes.put("wdb", "application/vnd.ms-works");
        mimeTypes.put("wdp", "image/vnd.ms-photo");
        mimeTypes.put("webarchive", "application/x-safari-webarchive");
        mimeTypes.put("webm", "video/webm");
        mimeTypes.put("webp", "image/webp");
        mimeTypes.put("webtest", "application/xml");
        mimeTypes.put("wiq", "application/xml");
        mimeTypes.put("wiz", "application/msword");
        mimeTypes.put("wks", "application/vnd.ms-works");
        mimeTypes.put("wlmp", "application/wlmoviemaker");
        mimeTypes.put("wlpginstall", "application/x-wlpg-detect");
        mimeTypes.put("wlpginstall3", "application/x-wlpg3-detect");
        mimeTypes.put("wm", "video/x-ms-wm");
        mimeTypes.put("wma", "audio/x-ms-wma");
        mimeTypes.put("wmd", "application/x-ms-wmd");
        mimeTypes.put("wmf", "application/x-msmetafile");
        mimeTypes.put("wml", "text/vnd.wap.wml");
        mimeTypes.put("wmlc", "application/vnd.wap.wmlc");
        mimeTypes.put("wmls", "text/vnd.wap.wmlscript");
        mimeTypes.put("wmlsc", "application/vnd.wap.wmlscriptc");
        mimeTypes.put("wmp", "video/x-ms-wmp");
        mimeTypes.put("wmv", "video/x-ms-wmv");
        mimeTypes.put("wmx", "video/x-ms-wmx");
        mimeTypes.put("wmz", "application/x-ms-wmz");
        mimeTypes.put("woff", "application/font-woff");
        mimeTypes.put("wpl", "application/vnd.ms-wpl");
        mimeTypes.put("wps", "application/vnd.ms-works");
        mimeTypes.put("wri", "application/x-mswrite");
        mimeTypes.put("wrl", "x-world/x-vrml");
        mimeTypes.put("wrz", "x-world/x-vrml");
        mimeTypes.put("wsc", "text/scriptlet");
        mimeTypes.put("wsdl", "text/xml");
        mimeTypes.put("wvx", "video/x-ms-wvx");
        mimeTypes.put("x", "application/directx");
        mimeTypes.put("xaf", "x-world/x-vrml");
        mimeTypes.put("xaml", "application/xaml+xml");
        mimeTypes.put("xap", "application/x-silverlight-app");
        mimeTypes.put("xbap", "application/x-ms-xbap");
        mimeTypes.put("xbm", "image/x-xbitmap");
        mimeTypes.put("xdr", "text/plain");
        mimeTypes.put("xht", "application/xhtml+xml");
        mimeTypes.put("xhtml", "application/xhtml+xml");
        mimeTypes.put("xla", "application/vnd.ms-excel");
        mimeTypes.put("xlam", "application/vnd.ms-excel.addin.macroEnabled.12");
        mimeTypes.put("xlc", "application/vnd.ms-excel");
        mimeTypes.put("xld", "application/vnd.ms-excel");
        mimeTypes.put("xlk", "application/vnd.ms-excel");
        mimeTypes.put("xll", "application/vnd.ms-excel");
        mimeTypes.put("xlm", "application/vnd.ms-excel");
        mimeTypes.put("xls", "application/vnd.ms-excel");
        mimeTypes.put("xlsb", "application/vnd.ms-excel.sheet.binary.macroEnabled.12");
        mimeTypes.put("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12");
        mimeTypes.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypes.put("xlt", "application/vnd.ms-excel");
        mimeTypes.put("xltm", "application/vnd.ms-excel.template.macroEnabled.12");
        mimeTypes.put("xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template");
        mimeTypes.put("xlw", "application/vnd.ms-excel");
        mimeTypes.put("xml", "text/xml");
        mimeTypes.put("xmta", "application/xml");
        mimeTypes.put("xof", "x-world/x-vrml");
        mimeTypes.put("xoml", "text/plain");
        mimeTypes.put("xpm", "image/x-xpixmap");
        mimeTypes.put("xps", "application/vnd.ms-xpsdocument");
        mimeTypes.put("xrm-ms", "text/xml");
        mimeTypes.put("xsc", "application/xml");
        mimeTypes.put("xsd", "text/xml");
        mimeTypes.put("xsf", "text/xml");
        mimeTypes.put("xsl", "text/xml");
        mimeTypes.put("xslt", "text/xml");
        mimeTypes.put("xsn", "application/octet-stream");
        mimeTypes.put("xss", "application/xml");
        mimeTypes.put("xspf", "application/xspf+xml");
        mimeTypes.put("xtp", "application/octet-stream");
        mimeTypes.put("xwd", "image/x-xwindowdump");
        mimeTypes.put("z", "application/x-compress");
        mimeTypes.put("zip", "application/zip");
        String fileMimeType = mimeTypes.get(fileExtension.toLowerCase(Locale.ROOT));
        return (fileMimeType == null) ? "" : fileMimeType;
    }

    public static final DocumentFile safCreateDirectory(final DocumentFile parentFolder,
                                                                final String folderName) {
        if (parentFolder == null) {
            Log.w(TAG, "safCreateDirectory: parentFolder == null");
            return null;
        }
        DocumentFile dfNewFolder = null;
        for (DocumentFile file : parentFolder.listFiles()) {
            if (file.isDirectory() && file.getName().equals(folderName)) {
                Log.v(TAG, "safCreateDirectory: Directory already exists '" + folderName + "'");
                return file;
            }
        }
        dfNewFolder = parentFolder.createDirectory(folderName);
        if (dfNewFolder == null) {
            Log.w(TAG, "safCreateDirectory: Failed to create directory '" + folderName + "'");
            return null;
        }
        Log.v(TAG, "safCreateDirectory: Created directory '" + folderName + "'");
        return dfNewFolder;
    }

    public static final boolean safCreateFile(final Context context,
                                                    final DocumentFile parentFolder,
                                                    final String fileNameAndExtension,
                                                    final String content) {
        for (DocumentFile file : parentFolder.listFiles()) {
            if (file.isFile() && file.getName().equals(fileNameAndExtension)) {
                Log.v(TAG, "safCreateFile: File already exists '" + fileNameAndExtension + "'");
                return true;
            }
        }

        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(fileNameAndExtension);
        final String fileMimeType = FileUtils.getMimeTypeFromFileExtension(fileExtension);

        String fileName = fileNameAndExtension;
        int dotIndex = fileNameAndExtension.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileNameAndExtension.substring(0, dotIndex);
        }

        boolean failSuccess = false;
        OutputStream outputStream = null;
        try {
            Uri fileUri = DocumentsContract.createDocument(
                    context.getContentResolver(),
                    parentFolder.getUri(),
                    fileMimeType,
                    fileName
            );
            if (fileUri == null) {
                Log.e(TAG, "safCreateFile: Failed to create file '" + fileNameAndExtension + "' #1");
                return false;
            }
            outputStream = context.getContentResolver().openOutputStream(fileUri);
            if (!content.isEmpty()) {
                outputStream.write(content.getBytes(StandardCharsets.ISO_8859_1));
            }
            outputStream.flush();
            Log.v(TAG, "safCreateFile: Created file '" + fileNameAndExtension + "', type '" + fileMimeType + "'");
            failSuccess = true;
        } catch (Exception e) {
            Log.e(TAG, "safCreateFile: Failed to create file '" + fileNameAndExtension + "' #2", e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "safCreateFile: Failed to create file '" + fileNameAndExtension + "' #3", e);
            }
        }
        return failSuccess;
    }

    /**
     * Open file in compatible app.
     */
    public static void openFile(final Context context, String fullPathAndFilename) {
        Uri fileUri = Uri.parse(fullPathAndFilename);
        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(fileUri.toString());
        String mimeType = FileUtils.getMimeTypeFromFileExtension(fileExtension);
        Log.v(TAG, "openFile: Detected mime type \'" + mimeType + "\' for file \'" + fullPathAndFilename + "\'");
        Intent intent;
        switch(fileExtension) {
            case "apk":
                // Requires permission in AndroidManifest.xml
                // We've dropped this as this is rarely used, mainly during development.
                // <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
                intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                break;
            default:
                intent = new Intent(Intent.ACTION_VIEW);
        }
        fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", new File(fullPathAndFilename));
        intent.setDataAndType(fileUri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException anfe) {
            Log.w(TAG, "openFile: ActivityNotFoundException. Falling back to app chooser...");
            intent.setDataAndType(Uri.parse(fullPathAndFilename), "application/*");
            Intent chooserIntent = Intent.createChooser(intent, context.getString(R.string.open_file_with));
            try {
                context.startActivity(chooserIntent);
            } catch (Exception ex) {
                Log.e(TAG, "openFile:", ex);
                Toast.makeText(context, R.string.open_file_no_compatible_app, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Open folder in compatible file manager app.
     */
    public static void openFolder(final Context context, String folderPath) {
        PackageManager pm = context.getPackageManager();

        // Try to find a compatible file manager app supporting the "resource/folder" Uri type.
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(new File(folderPath)), "resource/folder");
        intent.putExtra("org.openintents.extra.ABSOLUTE_PATH", folderPath);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // Launch file manager.
            context.startActivity(intent);
            return;
        } catch (android.content.ActivityNotFoundException anfe) {
            Log.w(TAG, "openFolder: No compatible file manager app not found (stage #1)");
        }

        // Try to open the folder with "Root Explorer" if it is installed.
        intent = pm.getLaunchIntentForPackage("com.speedsoftware.rootexplorer");
        if (intent != null) {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(folderPath));
            try {
                context.startActivity(intent);
                return;
            } catch (android.content.ActivityNotFoundException anfe) {
                Log.w(TAG, "openFolder: Failed to launch Root Explorer (stage #2)");
            }
        }
        Log.w(TAG, "openFolder: Root Explorer file manager app not found (stage #2)");

        // No compatible file manager app found.
        suggestFileManagerApp(context);
    }

    private static void suggestFileManagerApp(final Context context) {
        AlertDialog mSuggestFileManagerAppDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.suggest_file_manager_app_dialog_title)
                .setMessage(R.string.suggest_file_manager_app_dialog_text)
                .setPositiveButton(R.string.yes, (d, i) -> {
                    final String appPackageName = "me.zhanghai.android.files";
                    try {
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                    } catch (android.content.ActivityNotFoundException anfe) {
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                    }
                })
                .setNegativeButton(R.string.no, (d, i) -> {})
                .show();
    }
}
