package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.CachedFolderStatus;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderStatus;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.Util;

import java.util.Map;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Generates item views for folder items.
 */
public class FoldersAdapter extends ArrayAdapter<Folder> {

    // private static final String TAG = "FoldersAdapter";

    private final Context mContext;

    private RestApi mRestApi;

    public FoldersAdapter(Context context) {
        super(context, 0);
        mContext = context;
    }

    public void setRestApi(RestApi restApi) {
        mRestApi = restApi;
    }

    static class ViewHolder {
        TextView label;
        TextView directory;
        TextView items;
        TextView state;
        TextView revert;
        TextView override;
        TextView invalid;
        TextView lastItemFinishedItem;
        TextView lastItemFinishedTime;
        TextView conflicts;
        ProgressBar progressBar;
        ImageView openFolder;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_folder_list, parent, false);

            holder = new ViewHolder();
            holder.label = convertView.findViewById(R.id.label);
            holder.directory = convertView.findViewById(R.id.directory);
            holder.items = convertView.findViewById(R.id.items);
            holder.state = convertView.findViewById(R.id.state);
            holder.revert = convertView.findViewById(R.id.revert);
            holder.override = convertView.findViewById(R.id.override);
            holder.invalid = convertView.findViewById(R.id.invalid);
            holder.lastItemFinishedItem = convertView.findViewById(R.id.lastItemFinishedItem);
            holder.lastItemFinishedTime = convertView.findViewById(R.id.lastItemFinishedTime);
            holder.conflicts = convertView.findViewById(R.id.conflicts);
            holder.progressBar = convertView.findViewById(R.id.progressBar);
            holder.openFolder = convertView.findViewById(R.id.openFolder);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Folder folder = getItem(position);
        holder.label.setText(TextUtils.isEmpty(folder.label) ? folder.id : folder.label);
        holder.directory.setText(getShortPathForUI(folder.path));
        holder.override.setOnClickListener(view -> { onClickOverride(view, folder); });
        holder.revert.setOnClickListener(view -> { onClickRevert(view, folder); });
        holder.openFolder.setOnClickListener(view -> { FileUtils.openFolder(mContext, folder.path); });

        // Update folder icon.
        int drawableId = R.drawable.baseline_folder_24;
        switch (folder.type) {
            case Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED:
                drawableId = R.drawable.outline_lock_24;
                break;
            case Constants.FOLDER_TYPE_RECEIVE_ONLY:
                drawableId = R.drawable.ic_folder_receive_only;
                break;
            case Constants.FOLDER_TYPE_SEND_ONLY:
                drawableId = R.drawable.ic_folder_send_only;
                break;
            default:
        }
        holder.openFolder.setImageResource(drawableId);

        updateFolderStatusView(holder, folder);
        return convertView;
    }

    private void updateFolderStatusView(ViewHolder holder, Folder folder) {
        if (mRestApi == null || !mRestApi.isConfigLoaded()) {
            holder.conflicts.setVisibility(GONE);
            holder.lastItemFinishedItem.setVisibility(GONE);
            holder.lastItemFinishedTime.setVisibility(GONE);
            holder.items.setVisibility(GONE);
            holder.override.setVisibility(GONE);
            holder.progressBar.setVisibility(GONE);
            holder.revert.setVisibility(GONE);
            holder.state.setVisibility(GONE);
            setTextOrHide(holder.invalid, folder.invalid);
            return;
        }

        // mRestApi is available.
        final Map.Entry<FolderStatus, CachedFolderStatus> folderEntry = mRestApi.getFolderStatus(folder.id);
        final FolderStatus folderStatus = folderEntry.getKey();
        final CachedFolderStatus cachedFolderStatus = folderEntry.getValue();

        boolean failedItems = folderStatus.errors > 0;

        long neededItems = folderStatus.needFiles + folderStatus.needDirectories + folderStatus.needSymlinks + folderStatus.needDeletes;
        boolean outOfSync = folderStatus.state.equals("idle") && neededItems > 0;
        boolean overrideButtonVisible = folder.type.equals(Constants.FOLDER_TYPE_SEND_ONLY) && outOfSync;
        holder.override.setVisibility(overrideButtonVisible ? VISIBLE : GONE);

        holder.progressBar.setVisibility(folderStatus.state.equals("syncing") ? VISIBLE : GONE);

        boolean revertButtonVisible = false;
        if (folder.type.equals(Constants.FOLDER_TYPE_RECEIVE_ONLY)) {
            revertButtonVisible = (folderStatus.receiveOnlyTotalItems > 0);
        } else if (folder.type.equals(Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED)) {
            revertButtonVisible = ((folderStatus.receiveOnlyTotalItems - folderStatus.receiveOnlyChangedDeletes) > 0);
        }
        holder.revert.setText(mContext.getString(folder.type.equals(Constants.FOLDER_TYPE_RECEIVE_ONLY) ?
                                    R.string.revert_local_changes :
                                    R.string.delete_unexpected_items
        ));
        holder.revert.setVisibility(revertButtonVisible ? VISIBLE : GONE);

        holder.state.setVisibility(VISIBLE);
        if (outOfSync) {
            holder.state.setText(mContext.getString(R.string.status_outofsync));
            holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
        } else if (failedItems) {
            holder.state.setText(mContext.getString(R.string.state_failed_items, folderStatus.errors));
            holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
        } else {
            if (folder.paused) {
                holder.state.setText(mContext.getString(R.string.state_paused));
                holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_purple));
            } else {
                switch(folderStatus.state) {
                    case "clean-waiting":
                        holder.state.setText(R.string.state_clean_waiting);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_orange));
                        break;
                    case "cleaning":
                        holder.state.setText(R.string.state_cleaning);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "idle":
                        if (folder.getDeviceCount() <= 1) {
                            // Special case: The folder is IDLE and UNSHARED.
                            holder.state.setText(R.string.state_unshared);
                            holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_orange));
                        } else if (revertButtonVisible) {
                            holder.state.setText(R.string.state_local_additions);
                            holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_green));
                        } else {
                            holder.state.setText(R.string.state_up_to_date);
                            holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_green));
                        }
                        break;
                    case "scan-waiting":
                        holder.state.setText(R.string.state_scan_waiting);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_orange));
                        break;
                    case "scanning":
                        holder.state.setText(R.string.state_scanning);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "sync-waiting":
                        holder.state.setText(R.string.state_sync_waiting);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_orange));
                        break;
                    case "syncing":
                        holder.progressBar.setProgress((int) cachedFolderStatus.completion);
                        holder.state.setText(mContext.getString(R.string.state_syncing, (int) cachedFolderStatus.completion));
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "sync-preparing":
                        holder.state.setText(R.string.state_sync_preparing);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "error":
                        if (TextUtils.isEmpty(folderStatus.error)) {
                            holder.state.setText(R.string.state_error);
                        } else {
                            holder.state.setText(mContext.getString(R.string.state_error_message, folderStatus.error));
                        }
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
                        break;
                    case "unknown":
                        holder.state.setText(R.string.state_unknown);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
                        break;
                    default:
                        holder.state.setText(folderStatus.state);
                        holder.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
                }
            }
        }

        showConflictsUI(holder.conflicts, cachedFolderStatus.discoveredConflictFiles);
        showLastItemFinishedUI(holder.lastItemFinishedItem, holder.lastItemFinishedTime, cachedFolderStatus);

        holder.items.setVisibility(folder.paused ? GONE : VISIBLE);
        String itemsAndSize = "\u2211 ";
        itemsAndSize += mContext.getResources()
                .getQuantityString(R.plurals.files, (int) folderStatus.inSyncFiles, folderStatus.inSyncFiles, folderStatus.globalFiles);
        itemsAndSize += " \u2022 ";
        itemsAndSize += mContext.getString(R.string.folder_size_format,
                Util.readableFileSize(mContext, folderStatus.inSyncBytes),
                Util.readableFileSize(mContext, folderStatus.globalBytes));
        holder.items.setText(itemsAndSize);

        setTextOrHide(holder.invalid, folderStatus.invalid);
    }

    private void showConflictsUI(TextView view, final String[] discoveredConflictFiles) {
        int conflictFileCount = discoveredConflictFiles.length;
        if (conflictFileCount == 0) {
            view.setVisibility(GONE);
            return;
        }

        String itemCountAndFirst = "\u26a0 ";
        itemCountAndFirst += mContext.getResources()
                        .getQuantityString(R.plurals.conflicts, conflictFileCount, conflictFileCount);
        itemCountAndFirst += "\n\u292e ";
        itemCountAndFirst += discoveredConflictFiles[0];
        if (conflictFileCount > 1) {
            itemCountAndFirst += "\n\u2026";
        }

        view.setText(itemCountAndFirst);
        view.setVisibility(VISIBLE);
    }

    private void showLastItemFinishedUI(TextView itemView, TextView timeView, final CachedFolderStatus cachedFolderStatus) {
        if (TextUtils.isEmpty(cachedFolderStatus.lastItemFinishedAction) ||
                TextUtils.isEmpty(cachedFolderStatus.lastItemFinishedItem) ||
                TextUtils.isEmpty(cachedFolderStatus.lastItemFinishedTime)) {
            itemView.setVisibility(GONE);
            timeView.setVisibility(GONE);
            return;
        }

        String finishedItemText = "\u21cc";
        switch (cachedFolderStatus.lastItemFinishedAction) {
            case "delete":
                // (x)
                finishedItemText += " \u2297";
                break;
            case "update":
                // (*)
                finishedItemText += " \u229b";
                break;
            default:
                // !?
                finishedItemText += " \u2049";
        }
        finishedItemText += " " + Util.getPathEllipsis(cachedFolderStatus.lastItemFinishedItem);
        itemView.setText(finishedItemText);
        itemView.setVisibility(VISIBLE);

        String finishedTimeText = "\u21cc\u231a";
        finishedTimeText += Util.formatTime(cachedFolderStatus.lastItemFinishedTime);
        timeView.setText(finishedTimeText);
        timeView.setVisibility(VISIBLE);
    }

    private void setTextOrHide(TextView view, String text) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(GONE);
        } else {
            view.setText(text);
            view.setVisibility(VISIBLE);
        }
    }

    private final String getShortPathForUI(final String path) {
        String shortenedPath = path.replaceFirst("/storage/emulated/0", "[int]");
        shortenedPath = shortenedPath.replaceFirst("/storage/[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}", "[ext]");
        shortenedPath = shortenedPath.replaceFirst("/" + mContext.getPackageName(), "/[app]");
        return "\u2756 " + Util.getPathEllipsis(shortenedPath);
    }

    private void onClickOverride(View view, Folder folder) {
        AlertDialog.Builder confirmDialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.override_changes)
                .setMessage(R.string.override_changes_question)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    // Send "Override changes" through our service to the REST API.
                    Intent intent = new Intent(mContext, SyncthingService.class)
                            .putExtra(SyncthingService.EXTRA_FOLDER_ID, folder.id);
                    intent.setAction(SyncthingService.ACTION_OVERRIDE_CHANGES);
                    mContext.startService(intent);
                })
                .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {});
        confirmDialog.show();
    }

    private void onClickRevert(View view, Folder folder) {
        AlertDialog.Builder confirmDialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.revert_local_changes)
                .setMessage(R.string.revert_local_changes_question)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    // Send "Revert local changes" through our service to the REST API.
                    Intent intent = new Intent(mContext, SyncthingService.class)
                            .putExtra(SyncthingService.EXTRA_FOLDER_ID, folder.id);
                    intent.setAction(SyncthingService.ACTION_REVERT_LOCAL_CHANGES);
                    mContext.startService(intent);
                })
                .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {});
        confirmDialog.show();
    }

}
