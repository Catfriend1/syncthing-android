package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

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
        binding.override.setOnClickListener(v -> {
            // Send "Override changes" through our service to the REST API.
            Intent intent = new Intent(mContext, SyncthingService.class)
                    .putExtra(SyncthingService.EXTRA_FOLDER_ID, folder.id);
            intent.setAction(SyncthingService.ACTION_OVERRIDE_CHANGES);
            mContext.startService(intent);
        });
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
            binding.size.setVisibility(GONE);
            binding.state.setVisibility(GONE);
            setTextOrHide(binding.invalid, folder.invalid);
            return;
        }

        long neededItems = folderStatus.needFiles + folderStatus.needDirectories + folderStatus.needSymlinks + folderStatus.needDeletes;
        boolean outOfSync = folderStatus.state.equals("idle") && neededItems > 0;
        boolean overrideButtonVisible = folder.type.equals(Constants.FOLDER_TYPE_SEND_ONLY) && outOfSync;
        binding.override.setVisibility(overrideButtonVisible ? VISIBLE : GONE);
        binding.state.setVisibility(VISIBLE);
        if (outOfSync) {
            binding.state.setText(mContext.getString(R.string.status_outofsync));
            binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_red));
        } else {
            if (folder.paused) {
                binding.state.setText(mContext.getString(R.string.state_paused));
                binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_black));
            } else {
                binding.state.setText(getLocalizedState(mContext, folderStatus));
                switch(folderStatus.state) {
                    case "idle":
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_green));
                        break;
                    case "scanning":
                    case "syncing":
                        binding.state.setTextColor(ContextCompat.getColor(mContext, R.color.text_blue));
                        break;
                    case "error":
                    default:
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
     * Returns the folder's state as a localized string.
     */
    private static String getLocalizedState(Context c, FolderStatus folderStatus) {
        switch (folderStatus.state) {
            case "idle":
                return c.getString(R.string.state_idle);
            case "scanning":
                return c.getString(R.string.state_scanning);
            case "syncing":
                int percentage = (folderStatus.globalBytes != 0)
                        ? Math.round(100 * folderStatus.inSyncBytes / folderStatus.globalBytes)
                        : 100;
                return c.getString(R.string.state_syncing, percentage);
            case "error":
                if (TextUtils.isEmpty(folderStatus.error)) {
                    return c.getString(R.string.state_error);
                }
                return c.getString(R.string.state_error) + " (" + folderStatus.error + ")";
            case "unknown":
                return c.getString(R.string.state_unknown);
            default:
                return folderStatus.state;
        }
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


}
