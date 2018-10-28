package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.net.Uri;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.DiskEvent;

import java.io.File;
import java.util.ArrayList;

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
                        break;
                    case "deleted":
                        break;
                    case "modified":
                        break;
                    default:
                }
                break;
            case "file":
                switch (diskEvent.data.action) {
                    case "added":
                        break;
                    case "deleted":
                        break;
                    case "modified":
                        break;
                    default:
                }
                break;
            default:
        }
        viewHolder.typeIcon.setImageResource(drawableId);

        // Fill text views.
        viewHolder.filename.setText(filename);
        viewHolder.folderPath.setText(diskEvent.data.label + File.separator + path);
        viewHolder.modifiedByDevice.setText(mResources.getString(R.string.modified_by_device, diskEvent.data.modifiedBy));
        viewHolder.dateTime.setText(mResources.getString(R.string.modification_time, diskEvent.time));
    }

    @Override
    public int getItemCount() {
        return mChangeData.size();
    }

    private String getPathFromFullFN(String fullFN) {
        int index = fullFN.lastIndexOf('/');
        if (index > 0) {
            return fullFN.substring(0, index);
        }
        return "/";
    }
}
