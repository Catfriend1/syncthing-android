package com.nutomic.syncthingandroid.util;

import android.os.FileObserver;
import android.util.Log;

import com.annimon.stream.Stream;
import com.nutomic.syncthingandroid.model.Folder;

import java.io.File;
import java.util.ArrayList;

/**
 * Recursively watches a directory and all subfolders.
 */
public class FolderObserver extends FileObserver {

    private static final String TAG = "FolderObserver";

    private final OnFolderFileChangeListener mListener;

    private final Folder mFolder;

    private final String mPath;

    private final ArrayList<FolderObserver> mChilds = new ArrayList<>();

    public interface OnFolderFileChangeListener {
        void onFolderFileChange(String folderId, String relativePath);
    }

    public FolderObserver(OnFolderFileChangeListener listener, Folder folder)
            throws FolderNotExistingException {
        this(listener, folder, "");
        Log.i(TAG, "Observer created for (folder " + folder.id + ")");
    }

    public class FolderNotExistingException extends Exception {

        private final String mPath;

        public FolderNotExistingException(String path) {
            mPath = path;
        }

        @Override
        public String getMessage() {
            return "path " + mPath + " does not exist, aborting file observer";
        }
    }

    /**
     * Constructs watcher and starts watching the given directory recursively.
     *
     * @param listener The listener where changes should be sent to.
     * @param folder The folder where this folder belongs to.
     * @param path path to the monitored folder, relative to folder root.
     */
    private FolderObserver(OnFolderFileChangeListener listener, Folder folder, String path)
            throws FolderNotExistingException {
        super(folder.path + "/" + path,
                ATTRIB | CLOSE_WRITE | CREATE | DELETE | DELETE_SELF | MOVED_FROM |
                MOVED_TO | MOVE_SELF);
        mListener = listener;
        mFolder = folder;
        mPath = path;
        Log.v(TAG, "Observer created for " + new File(mFolder.path, mPath).toString() + " (folder " + folder.id + ")");
        startWatching();

        File currentFolder = new File(folder.path, path);
        if (!currentFolder.exists()) {
            throw new FolderNotExistingException(currentFolder.getAbsolutePath());
        }
        File[] directories = currentFolder.listFiles((current, name) -> new File(current, name).isDirectory());

        if (directories != null) {
            for (File f : directories) {
                mChilds.add(new FolderObserver(mListener, mFolder, path + "/" + f.getName()));
            }
        }
    }

    /**
     * Handles incoming events for changed files.
     */
    @Override
    public void onEvent(int event, String path) {
        // Ignore some weird events that we may receive.
        event &= FileObserver.ALL_EVENTS;
        if (event == 0)
            return;

        File fullPath = (path != null)
                ? new File(mPath, path)
                : new File(mPath);

        Log.v(TAG, "Received inotify event " + Integer.toHexString(event) + " at " +
                fullPath.getAbsolutePath());
        switch (event) {
            case MOVED_FROM:
                // fall through
            case DELETE_SELF:
                // fall through
            case DELETE:
                for (FolderObserver c : mChilds) {
                    if (c.mPath.equals(path)) {
                        mChilds.remove(c);
                        break;
                    }
                }
                mListener.onFolderFileChange(mFolder.id, fullPath.getPath());
                break;
            case MOVED_TO:
                // fall through
            case CREATE:
                if (fullPath.isDirectory()) {
                    try {
                        mChilds.add(new FolderObserver(mListener, mFolder, path));
                    } catch (FolderNotExistingException e) {
                        Log.w(TAG, "Failed to add listener for nonexisting folder", e);
                    }
                }
                // fall through
            default:
                mListener.onFolderFileChange(mFolder.id, fullPath.getPath());
        }
    }

    /**
     * Recursively stops watching the directory.
     */
    @Override
    public void stopWatching() {
        super.stopWatching();
        Stream.of(mChilds).forEach(FolderObserver::stopWatching);
    }
}
