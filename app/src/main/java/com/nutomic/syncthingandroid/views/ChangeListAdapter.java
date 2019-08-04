package com.nutomic.syncthingandroid.views;

import android.annotation.TargetApi;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import androidx.recyclerview.widget.RecyclerView;
import android.net.Uri;
import android.os.Build;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.DiskEvent;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Locale;

public class ChangeListAdapter extends RecyclerView.Adapter<ChangeListAdapter.ViewHolder> {

    // private static final String TAG = "ChangeListAdapter";

    private final Context mContext;
    private final Resources mResources;
    private ArrayList<DiskEvent> mChangeData = new ArrayList<DiskEvent>();
    private ItemClickListener mOnClickListener;
    private LayoutInflater mLayoutInflater;

    public interface ItemClickListener {
        void onItemClick(DiskEvent diskEvent);
    }

    public ChangeListAdapter(Context context) {
        mContext = context;
        mResources = mContext.getResources();
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    public void clear() {
        mChangeData.clear();
    }

    public void add(DiskEvent diskEvent) {
        mChangeData.add(diskEvent);
    }

    public void setOnClickListener(ItemClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public ImageView typeIcon;
        public TextView filename;
        public TextView folderPath;
        public TextView modifiedByDevice;
        public TextView dateTime;
        public View layout;

        public ViewHolder(View view) {
            super(view);
            typeIcon = view.findViewById(R.id.typeIcon);
            filename = view.findViewById(R.id.filename);
            folderPath = view.findViewById(R.id.folderPath);
            modifiedByDevice = view.findViewById(R.id.modifiedByDevice);
            dateTime = view.findViewById(R.id.dateTime);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            DiskEvent diskEvent = mChangeData.get(position);
            if (mOnClickListener != null) {
                mOnClickListener.onItemClick(diskEvent);
            }
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mLayoutInflater.inflate(R.layout.item_recent_change, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        DiskEvent diskEvent = mChangeData.get(position);

        // Separate path and filename.
        Uri uri = Uri.parse(diskEvent.data.path);
        String filename = uri.getLastPathSegment();
        String path = getPathFromFullFN(diskEvent.data.path);

        // Decide which icon to show.
        int drawableId = R.drawable.ic_help_outline_black_24dp;
        switch (diskEvent.data.type) {
            case "dir":
                switch (diskEvent.data.action) {
                    case "added":
                        drawableId = R.drawable.ic_folder_add_black_24dp;
                        break;
                    case "deleted":
                        drawableId = R.drawable.ic_folder_delete_black_24dp;
                        break;
                    case "modified":
                        drawableId = R.drawable.ic_folder_edit_black_24dp;
                        break;
                    default:
                }
                break;
            case "file":
                switch (diskEvent.data.action) {
                    case "added":
                        drawableId = R.drawable.ic_file_add_black_24dp;
                        break;
                    case "deleted":
                        drawableId = R.drawable.ic_file_remove_black_24dp;
                        break;
                    case "modified":
                        drawableId = R.drawable.ic_file_edit_black_24dp;
                        break;
                    default:
                }
                break;
            default:
        }
        viewHolder.typeIcon.setImageResource(drawableId);

        // Fill text views.
        viewHolder.filename.setText(filename);
        viewHolder.folderPath.setText("[" + diskEvent.data.label + "]" + File.separator + path);
        viewHolder.modifiedByDevice.setText(mResources.getString(R.string.modified_by_device, diskEvent.data.modifiedBy));

        // Convert dateTime to readable localized string.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            viewHolder.dateTime.setText(mResources.getString(R.string.modification_time, diskEvent.time));
        } else {
            viewHolder.dateTime.setText(mResources.getString(R.string.modification_time, formatDateTime(diskEvent.time)));
        }
    }

    @Override
    public int getItemCount() {
        return mChangeData.size();
    }

    /**
     * Converts dateTime to readable localized string.
     */
    @TargetApi(26)
    private String formatDateTime(String dateTime) {
        ZonedDateTime parsedDateTime = ZonedDateTime.parse(dateTime);
        ZonedDateTime zonedDateTime = parsedDateTime.withZoneSameInstant(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());
        return formatter.format(zonedDateTime);
    }

    private String getPathFromFullFN(String fullFN) {
        int index = fullFN.lastIndexOf('/');
        if (index > 0) {
            return fullFN.substring(0, index);
        }
        return "";
    }
}
