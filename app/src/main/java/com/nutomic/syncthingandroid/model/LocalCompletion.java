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
 * completion indicators defined in {@link CompletionInfo}
 * according to Syncthing's "FolderSummary" event JSON result schema.
 * Completion model of Syncthing's web UI is completion[folderId]
 */
public class LocalCompletion {

    private static final String TAG = "LocalCompletion";

    private Boolean ENABLE_VERBOSE_LOG = false;

    HashMap<String, Map.Entry<FolderStatus, CompletionInfo>> folderMap =
        new HashMap<String, Map.Entry<FolderStatus, CompletionInfo>>();

    public LocalCompletion(Boolean enableVerboseLog) {
        ENABLE_VERBOSE_LOG = enableVerboseLog;
    }

    /**
     * Updates folder information in the cache model
     * after a config update.
     */
    public void updateFromConfig(final List<Folder> newFolders) {
        // Handle folders that were removed from the config.
        List<String> removedFolders = new ArrayList<>();
        Boolean folderFound;
        for (String folderId : folderMap.keySet()) {
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
            if (ENABLE_VERBOSE_LOG) {
                Log.v(TAG, "updateFromConfig: Remove folder '" + folderId + "' from cache model");
            }
            if (folderMap.containsKey(folderId)) {
                folderMap.remove(folderId);
            }
        }

        // Handle folders that were added to the config.
        for (Folder folder : newFolders) {
            if (!folderMap.containsKey(folder.id)) {
                if (ENABLE_VERBOSE_LOG) {
                    Log.v(TAG, "updateFromConfig: Add folder '" + folder.id + "' to cache model.");
                }
                folderMap.put(
                        folder.id,
                        new SimpleEntry(
                                new FolderStatus(),
                                new CompletionInfo()
                        )
                );
            }
        }
    }

    /**
     * Calculates local folder sync completion percentage across all folders.
     */
    public int getTotalFolderCompletion() {
        int folderCount = 0;
        double sumCompletion = 0;
        for (Map.Entry<String, Map.Entry<FolderStatus, CompletionInfo>> folder : folderMap.entrySet()) {
            CompletionInfo completionInfo  = folder.getValue().getValue();
            if (!completionInfo.paused) {
                sumCompletion += completionInfo.completion;
                folderCount++;
            }
        }
        if (folderCount == 0) {
            return 100;
        }
        return (int) Math.floor(sumCompletion / folderCount);
    }

    /**
     * Returns local folder status including completion info.
     */
    public final Map.Entry<FolderStatus, CompletionInfo> getFolderStatus (final String folderId) {
        if (!folderMap.containsKey(folderId)) {
            return new SimpleEntry(
                    new FolderStatus(),
                    new CompletionInfo()
            );
        }
        Map.Entry<FolderStatus, CompletionInfo> folderEntry = folderMap.get(folderId);
        return new SimpleEntry(
                deepCopy(folderEntry.getKey(), new TypeToken<FolderStatus>(){}.getType()),
                deepCopy(folderEntry.getValue(), new TypeToken<CompletionInfo>(){}.getType())
        );
    }

    /**
     * Store folderStatus for later when we need info for the UI.
     * Calculate completionInfo within the completion[folderId] model.
     */
    public void setFolderStatus(final String folderId,
                                    final Boolean folderPaused,
                                    final FolderStatus folderStatus) {
        CompletionInfo completionInfo = new CompletionInfo();
        completionInfo.paused = folderPaused;
        if (folderStatus.globalBytes == 0 ||
                (folderStatus.inSyncBytes > folderStatus.globalBytes)) {
            completionInfo.completion = 100;
        } else {
            completionInfo.completion = (int) Math.floor(((double) folderStatus.inSyncBytes / folderStatus.globalBytes) * 100);
        }
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, "setFolderStatus: folderId=\"" + folderId + "\"" +
                    ", state=\"" + folderStatus.state + "\"" +
                    ", paused=" + Boolean.toString(completionInfo.paused) +
                    ", completion=" + (int) completionInfo.completion + "%");
        }

        // Add folder or update existing folder entry.
        folderMap.put(folderId, new SimpleEntry(folderStatus, completionInfo));
    }

    public void setFolderStatus(final String folderId,
                                    final FolderStatus folderStatus) {
        // Persist completionInfo.paused from the previous entry.
        final Map.Entry<FolderStatus, CompletionInfo> cacheEntry = getFolderStatus(folderId);
        setFolderStatus(folderId, cacheEntry.getValue().paused, folderStatus);
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
}
