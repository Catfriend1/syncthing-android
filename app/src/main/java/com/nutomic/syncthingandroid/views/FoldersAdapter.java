package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.databinding.ItemFolderListBinding;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderStatus;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.util.HashMap;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Generates item views for folder items.
 */
public class FoldersAdapter extends ArrayAdapter<Folder> {

    private static final String TAG = "FoldersAdapter";

    private final HashMap<String, FolderStatus> mLocalFolderStatuses = new HashMap<>();

    public FoldersAdapter(Context context) {
        super(context, 0);
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ItemFolderListBinding binding = (convertView == null)
                ? DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.item_folder_list, parent, false)
                : DataBindingUtil.bind(convertView);

        Folder folder = getItem(position);
        binding.label.setText(TextUtils.isEmpty(folder.label) ? folder.id : folder.label);
        binding.directory.setText(folder.path);
        binding.openFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(folder.path)), "resource/folder");
            intent.putExtra("org.openintents.extra.ABSOLUTE_PATH", folder.path);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
            } else {
                // Try a second way to find a compatible file explorer app.
                Log.v(TAG, "openFolder: Fallback to application chooser to open folder.");
                intent.setDataAndType(Uri.parse(folder.path), "application/*");
                Intent chooserIntent = Intent.createChooser(intent, getContext().getString(R.string.open_file_manager));
                if (chooserIntent != null) {
                    getContext().startActivity(chooserIntent);
                } else {
                    Toast.makeText(getContext(), R.string.toast_no_file_manager, Toast.LENGTH_SHORT).show();
                }
            }
        });

        updateFolderStatusView(binding, folder);
        return binding.getRoot();
    }

    private void updateFolderStatusView(ItemFolderListBinding binding, Folder folder) {
        FolderStatus folderStatus = mLocalFolderStatuses.get(folder.id);
        if (folderStatus == null) {
            binding.items.setVisibility(GONE);
            binding.size.setVisibility(GONE);
            setTextOrHide(binding.invalid, folder.invalid);
            return;
        }

        int percentage = (folderStatus.globalBytes != 0)
                ? Math.round(100 * folderStatus.inSyncBytes / folderStatus.globalBytes)
                : 100;
        long neededItems = folderStatus.needFiles + folderStatus.needDirectories + folderStatus.needSymlinks + folderStatus.needDeletes;
        if (folderStatus.state.equals("idle") && neededItems > 0) {
            binding.state.setText(getContext().getString(R.string.status_outofsync));
            binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
        } else {
            if (folder.paused) {
                binding.state.setText(getContext().getString(R.string.state_paused));
                binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_black));
            } else {
                binding.state.setText(getLocalizedState(getContext(), folderStatus.state, percentage));
                switch(folderStatus.state) {
                    case "idle":
                        binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_green));
                        break;
                    case "scanning":
                    case "syncing":
                        binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
                        break;
                    default:
                        binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
                }
            }
        }
        binding.items.setVisibility(VISIBLE);
        binding.items.setText(getContext().getResources()
                .getQuantityString(R.plurals.files, (int) folderStatus.inSyncFiles, folderStatus.inSyncFiles, folderStatus.globalFiles));
        binding.size.setVisibility(VISIBLE);
        binding.size.setText(getContext().getString(R.string.folder_size_format,
                Util.readableFileSize(getContext(), folderStatus.inSyncBytes),
                Util.readableFileSize(getContext(), folderStatus.globalBytes)));
        setTextOrHide(binding.invalid, folderStatus.invalid);
    }

    /**
     * Returns the folder's state as a localized string.
     */
    private static String getLocalizedState(Context c, String state, int percentage) {
        switch (state) {
            case "idle":        return c.getString(R.string.state_idle);
            case "scanning":    return c.getString(R.string.state_scanning);
            case "syncing":     return c.getString(R.string.state_syncing, percentage);
            case "error":       return c.getString(R.string.state_error);
            case "unknown":     return c.getString(R.string.state_unknown);
            default:            return state;
        }
    }

    /**
     * Requests updated folder status from the api for all visible items.
     */
    public void updateFolderStatus(RestApi api) {
        for (int i = 0; i < getCount(); i++) {
            api.getFolderStatus(getItem(i).id, this::onReceiveFolderStatus);
        }
    }

    private void onReceiveFolderStatus(String folderId, FolderStatus folderStatus) {
        mLocalFolderStatuses.put(folderId, folderStatus);
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
