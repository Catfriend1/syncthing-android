package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.databinding.ItemFolderListBinding;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderStatus;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.Util;

import java.util.HashMap;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Generates item views for folder items.
 */
public class FoldersAdapter extends ArrayAdapter<Folder> {

    private static final String TAG = "FoldersAdapter";

    private final HashMap<String, FolderStatus> mLocalFolderStatuses = new HashMap<>();

    private final Context mContext;

    public FoldersAdapter(Context context) {
        super(context, 0);
        mContext = context;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ItemFolderListBinding binding = (convertView == null)
                ? DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.item_folder_list, parent, false)
                : DataBindingUtil.bind(convertView);

        Folder folder = getItem(position);
        binding.label.setText(TextUtils.isEmpty(folder.label) ? folder.id : folder.label);
        binding.directory.setText(folder.path);
        binding.override.setOnClickListener(view -> { onClickOverride(view, folder); } );
        binding.revert.setOnClickListener(view -> { onClickRevert(view, folder); } );
        binding.openFolder.setOnClickListener(view -> { FileUtils.openFolder(mContext, folder.path); } );

        // Update folder icon.
        int drawableId = R.drawable.ic_folder_black_24dp_active;
        switch (folder.type) {
            case Constants.FOLDER_TYPE_RECEIVE_ONLY:
                drawableId = R.drawable.ic_folder_receive_only;
                break;
            case Constants.FOLDER_TYPE_SEND_ONLY:
                drawableId = R.drawable.ic_folder_send_only;
                break;
            default:
        }
        binding.openFolder.setImageResource(drawableId);

        updateFolderStatusView(binding, folder);
        return binding.getRoot();
    }

    private void updateFolderStatusView(ItemFolderListBinding binding, Folder folder) {
        FolderStatus folderStatus = mLocalFolderStatuses.get(folder.id);
        if (folderStatus == null) {
            binding.items.setVisibility(GONE);
            binding.override.setVisibility(GONE);
            binding.revert.setVisibility(GONE);
            binding.size.setVisibility(GONE);
            binding.state.setVisibility(GONE);
            setTextOrHide(binding.invalid, folder.invalid);
            return;
        }

        long neededItems = folderStatus.needFiles + folderStatus.needDirectories + folderStatus.needSymlinks + folderStatus.needDeletes;
        boolean outOfSync = folderStatus.state.equals("idle") && neededItems > 0;
        boolean overrideButtonVisible = folder.type.equals(Constants.FOLDER_TYPE_SEND_ONLY) && outOfSync;
        binding.override.setVisibility(overrideButtonVisible ? VISIBLE : GONE);

        boolean revertButtonVisible = folder.type.equals(Constants.FOLDER_TYPE_RECEIVE_ONLY) && (folderStatus.receiveOnlyTotalItems > 0);
        binding.revert.setVisibility(revertButtonVisible ? VISIBLE : GONE);

        binding.state.setVisibility(VISIBLE);
        if (outOfSync) {
            binding.state.setText(mContext.getString(R.string.status_outofsync));
            binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
        } else {
            if (folder.paused) {
                binding.state.setText(mContext.getString(R.string.state_paused));
                binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_black));
            } else {
                switch(folderStatus.state) {
                    case "idle":
                        if (folder.getDeviceCount() <= 1) {
                            // Special case: The folder is IDLE and UNSHARED.
                            binding.state.setText(mContext.getString(R.string.state_unshared));
                            binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_orange));
                        } else if (revertButtonVisible) {
                            binding.state.setText(mContext.getString(R.string.state_local_additions));
                            binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_green));
                        } else {
                            binding.state.setText(mContext.getString(R.string.state_idle));
                            binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_green));
                        }
                        break;
                    case "scan-waiting":
                        binding.state.setText(mContext.getString(R.string.state_scan_waiting));
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_orange));
                        break;
                    case "scanning":
                        binding.state.setText(mContext.getString(R.string.state_scanning));
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "syncing":
                        binding.state.setText(
                                mContext.getString(
                                    R.string.state_syncing,
                                        (folderStatus.globalBytes != 0)
                                                ? Math.round(100 * folderStatus.inSyncBytes / folderStatus.globalBytes)
                                                : 100
                                )
                        );
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "sync-preparing":
                        binding.state.setText(mContext.getString(R.string.state_sync_preparing));
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "error":
                        if (TextUtils.isEmpty(folderStatus.error)) {
                            binding.state.setText(mContext.getString(R.string.state_error));
                        } else {
                            binding.state.setText(mContext.getString(R.string.state_error_message, folderStatus.error));
                        }
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
                        break;
                    case "unknown":
                        binding.state.setText(mContext.getString(R.string.state_unknown));
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
                        break;
                    default:
                        binding.state.setText(folderStatus.state);
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
                }
            }
        }
        binding.items.setVisibility(VISIBLE);
        binding.items.setText(mContext.getResources()
                .getQuantityString(R.plurals.files, (int) folderStatus.inSyncFiles, folderStatus.inSyncFiles, folderStatus.globalFiles));
        binding.size.setVisibility(VISIBLE);
        binding.size.setText(mContext.getString(R.string.folder_size_format,
                Util.readableFileSize(mContext, folderStatus.inSyncBytes),
                Util.readableFileSize(mContext, folderStatus.globalBytes)));
        setTextOrHide(binding.invalid, folderStatus.invalid);
    }

    /**
     * Requests updated folder status from the api for all visible items.
     */
    public void updateFolderStatus(RestApi restApi) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running. Clear last state.
            mLocalFolderStatuses.clear();
            return;
        }

        for (int i = 0; i < getCount(); i++) {
            Folder folder = getItem(i);
            if (folder != null) {
                restApi.getFolderStatus(folder.id, this::onReceiveFolderStatus);
            }
        }
    }

    private void onReceiveFolderStatus(String folderId, FolderStatus folderStatus) {
        mLocalFolderStatuses.put(folderId, folderStatus);
        // This will invoke "getView" for all elements.
        notifyDataSetChanged();
    }

    private void setTextOrHide(TextView view, String text) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(GONE);
        } else {
            view.setText(text);
            view.setVisibility(VISIBLE);
        }
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
