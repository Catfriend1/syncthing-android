package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.databinding.ItemFolderListBinding;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.Model;
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

    private final HashMap<String, Model> mModels = new HashMap<>();

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
        Model model = mModels.get(folder.id);
        binding.label.setText(TextUtils.isEmpty(folder.label) ? folder.id : folder.label);
        binding.directory.setText(folder.path);
        binding.openFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(folder.path)), "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
            } else {
                Toast.makeText(getContext(), R.string.toast_no_file_manager, Toast.LENGTH_SHORT).show();
            }
        });

        if (model != null) {
            int percentage = (model.globalBytes != 0)
                    ? Math.round(100 * model.inSyncBytes / model.globalBytes)
                    : 100;
            long neededItems = model.needFiles + model.needDirectories + model.needSymlinks + model.needDeletes;
            if (model.state.equals("idle") && neededItems > 0) {
                binding.state.setText(getContext().getString(R.string.status_outofsync));
                binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
            } else {
                binding.state.setText(getLocalizedState(getContext(), model.state, percentage));
                switch(model.state) {
                    case "idle":
                        binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_green));
                        break;
                    case "scanning":
                    case "cleaning":
                    case "syncing":
                        binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
                        break;
                    default:
                        binding.state.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
                }
            }
            binding.items.setVisibility(VISIBLE);
            binding.items.setText(getContext().getResources()
                    .getQuantityString(R.plurals.files, (int) model.inSyncFiles, model.inSyncFiles, model.globalFiles));
            binding.size.setVisibility(VISIBLE);
            binding.size.setText(getContext().getString(R.string.folder_size_format,
                    Util.readableFileSize(getContext(), model.inSyncBytes),
                    Util.readableFileSize(getContext(), model.globalBytes)));
            setTextOrHide(binding.invalid, model.invalid);
        } else {
            binding.items.setVisibility(GONE);
            binding.size.setVisibility(GONE);
            setTextOrHide(binding.invalid, folder.invalid);
        }

        return binding.getRoot();
    }

    /**
     * Returns the folder's state as a localized string.
     */
    private static String getLocalizedState(Context c, String state, int percentage) {
        switch (state) {
            case "idle":     return c.getString(R.string.state_idle);
            case "scanning": return c.getString(R.string.state_scanning);
            case "cleaning": return c.getString(R.string.state_cleaning);
            case "syncing":  return c.getString(R.string.state_syncing, percentage);
            case "error":    return c.getString(R.string.state_error);
            case "unknown":  // Fallthrough
            case "":         return c.getString(R.string.state_unknown);
        }
        if (BuildConfig.DEBUG) {
            throw new AssertionError("Unexpected folder state " + state);
        }
        return "";
    }

    /**
     * Requests updated model info from the api for all visible items.
     */
    public void updateModel(RestApi api) {
        for (int i = 0; i < getCount(); i++) {
            api.getModel(getItem(i).id, this::onReceiveModel);
        }
    }

    private void onReceiveModel(String folderId, Model model) {
        mModels.put(folderId, model);
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
