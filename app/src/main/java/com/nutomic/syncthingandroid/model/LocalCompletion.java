package com.nutomic.syncthingandroid.model;

import android.util.Log;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.text.TextUtils;

/**
 * This class caches local folder synchronization
 * completion indicators defined in {@link CachedFolderStatus}
 * according to Syncthing's "FolderSummary" event JSON result schema.
 * Completion model of Syncthing's web UI is completion[folderId]
 */
public class LocalCompletion {

    private static final String TAG = "LocalCompletion";

    private Boolean ENABLE_VERBOSE_LOG = false;

    HashMap<String, Map.Entry<FolderStatus, CachedFolderStatus>> mFolderMap =
        new HashMap<String, Map.Entry<FolderStatus, CachedFolderStatus>>();

    /**
     * Object that must be locked upon accessing mFolderMapLock.
     */
    private final Object mFolderMapLock = new Object();

    public LocalCompletion(Boolean enableVerboseLog) {
        ENABLE_VERBOSE_LOG = enableVerboseLog;
    }

    /**
     * Updates folder information in the cache model
     * after a config update.
     */
    public void updateFromConfig(final List<Folder> newFolders) {
        synchronized(mFolderMapLock) {
            // Handle folders that were removed from the config.
            List<String> removedFolders = new ArrayList<>();
            Boolean folderFound;
            for (String folderId : mFolderMap.keySet()) {
                folderFound = false;
                for (Folder folder : newFolders) {
                    if (folder.id.equals(folderId)) {
                        folderFound = true;
                        break;
                    }
                }
                if (!folderFound) {
                    removedFolders.add(folderId);
                }
            }
            for (String folderId : removedFolders) {
                LogV("updateFromConfig: Remove folder '" + folderId + "' from cache model");
                if (mFolderMap.containsKey(folderId)) {
                    mFolderMap.remove(folderId);
                }
            }

            // Handle folders that were added to the config.
            for (Folder folder : newFolders) {
                if (!mFolderMap.containsKey(folder.id)) {
                    LogV("updateFromConfig: Add folder '" + folder.id + "' to cache model.");
                    mFolderMap.put(
                            folder.id,
                            new SimpleEntry(
                                    new FolderStatus(),
                                    new CachedFolderStatus()
                            )
                    );
                }
            }
        }
    }

    /**
     * Calculates local folder sync completion percentage across all folders.
     */
    public int getTotalFolderCompletion() {
        synchronized(mFolderMapLock) {
            int folderCount = 0;
            double sumCompletion = 0;
            for (Map.Entry<String, Map.Entry<FolderStatus, CachedFolderStatus>> folder : mFolderMap.entrySet()) {
                CachedFolderStatus cachedFolderStatus = folder.getValue().getValue();

                // Filter invalid percentage values we may have got from the REST API.
                if (cachedFolderStatus.completion < 0) {
                    cachedFolderStatus.completion = 0;
                } else if (cachedFolderStatus.completion > 100) {
                    cachedFolderStatus.completion = 100;
                }

                if (!cachedFolderStatus.paused &&
                        cachedFolderStatus.completion != 100) {
                    sumCompletion += cachedFolderStatus.completion;
                    folderCount++;
                }
            }
            if (folderCount == 0) {
                return 100;
            }
            int totalFolderCompletion = (int) Math.floor(sumCompletion / folderCount);
            if (totalFolderCompletion < 0) {
                totalFolderCompletion = 0;
            } else if (totalFolderCompletion > 100) {
                totalFolderCompletion = 100;
            }
            return totalFolderCompletion;
        }
    }

    /**
     * Returns local folder status including completion info.
     */
    public final Map.Entry<FolderStatus, CachedFolderStatus> getFolderStatus (final String folderId) {
        synchronized(mFolderMapLock) {
            if (!mFolderMap.containsKey(folderId)) {
                return new SimpleEntry(
                        new FolderStatus(),
                        new CachedFolderStatus()
                );
            }
            Map.Entry<FolderStatus, CachedFolderStatus> folderEntry = mFolderMap.get(folderId);
            return new SimpleEntry(
                    deepCopy(folderEntry.getKey(), new TypeToken<FolderStatus>(){}.getType()),
                    deepCopy(folderEntry.getValue(), new TypeToken<CachedFolderStatus>(){}.getType())
            );
        }
    }

    /**
     * Store folderStatus for later when we need info for the UI.
     * Calculate cachedFolderStatus within the completion[folderId] model.
     */
    public void setFolderStatus(final String folderId,
                                    final Boolean folderPaused,
                                    final FolderStatus folderStatus) {
        synchronized(mFolderMapLock) {
            final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = getFolderStatus(folderId);
            CachedFolderStatus cachedFolderStatus = cacheEntry.getValue();
            cachedFolderStatus.paused = folderPaused;
            if (folderStatus.globalBytes == 0 ||
                    (folderStatus.inSyncBytes > folderStatus.globalBytes)) {
                cachedFolderStatus.completion = 100;
            } else {
                cachedFolderStatus.completion = (int) Math.floor(((double) folderStatus.inSyncBytes / folderStatus.globalBytes) * 100);
            }
            if (folderStatus.state.equals("idle")) {
                cachedFolderStatus.completion = 100;
            }
            if (ENABLE_VERBOSE_LOG) {
                LogV("setFolderStatus: folderId=\"" + folderId + "\"" +
                        ", state=\"" + folderStatus.state + "\"" +
                        ", paused=" + Boolean.toString(cachedFolderStatus.paused) +
                        ", completion=" + (int) cachedFolderStatus.completion + "%");
            }

            // Add folder or update existing folder entry.
            mFolderMap.put(folderId, new SimpleEntry(folderStatus, cachedFolderStatus));
        }
    }

    public void setFolderStatus(final String folderId,
                                    final FolderStatus folderStatus) {
        synchronized(mFolderMapLock) {
            // Persist cachedFolderStatus.paused from the previous entry.
            final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = getFolderStatus(folderId);
            setFolderStatus(folderId, cacheEntry.getValue().paused, folderStatus);
        }
    }

    /**
     * Setters of additionally stored information
     * e.g. "ItemFinished" event details arriving through {@link EventProcessor} > {@link RestApi}
     */
    public void setLastItemFinished(final String folderId,
                                        final String lastItemFinishedAction,
                                        final String lastItemFinishedItem,
                                        final String lastItemFinishedTime) {
        synchronized(mFolderMapLock) {
            final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = getFolderStatus(folderId);
            CachedFolderStatus cachedFolderStatus = cacheEntry.getValue();
            cachedFolderStatus.lastItemFinishedAction = lastItemFinishedAction;
            cachedFolderStatus.lastItemFinishedItem = lastItemFinishedItem;
            cachedFolderStatus.lastItemFinishedTime = lastItemFinishedTime;

            // Add folder or update existing folder entry.
            mFolderMap.put(folderId, new SimpleEntry(cacheEntry.getKey(), cachedFolderStatus));
        }
    }

    public void setRemoteIndexUpdated(final String folderId,
                                            final boolean remoteIndexUpdated) {
        synchronized(mFolderMapLock) {
            final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = getFolderStatus(folderId);
            CachedFolderStatus cachedFolderStatus = cacheEntry.getValue();
            cachedFolderStatus.remoteIndexUpdated = remoteIndexUpdated;

            // Add folder or update existing folder entry.
            mFolderMap.put(folderId, new SimpleEntry(cacheEntry.getKey(), cachedFolderStatus));
        }
    }

    public void setDiscoveredConflictFiles(final String folderId,
                                            final String[] discoveredConflictFiles) {
        synchronized(mFolderMapLock) {
            final Map.Entry<FolderStatus, CachedFolderStatus> cacheEntry = getFolderStatus(folderId);
            CachedFolderStatus cachedFolderStatus = cacheEntry.getValue();
            cachedFolderStatus.discoveredConflictFiles = discoveredConflictFiles;

            // Add folder or update existing folder entry.
            mFolderMap.put(folderId, new SimpleEntry(cacheEntry.getKey(), cachedFolderStatus));
        }
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    private <T> T deepCopy(T object, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
