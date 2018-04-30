package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.util.Util;

/**
 * Generates item views for device items.
 */
public class DevicesAdapter extends ArrayAdapter<Device> {

    private Connections mConnections;

    public DevicesAdapter(Context context) {
        super(context, R.layout.item_device_list);
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_device_list, parent, false);
        }

        TextView name = convertView.findViewById(R.id.name);
        TextView status = convertView.findViewById(R.id.status);
        TextView download = convertView.findViewById(R.id.download);
        TextView upload = convertView.findViewById(R.id.upload);

        String deviceId = getItem(position).deviceID;

        name.setText(getItem(position).getDisplayName());
        Resources r = getContext().getResources();

        Connections.Connection conn = null;
        if (mConnections != null && mConnections.connections.containsKey(deviceId)) {
            conn = mConnections.connections.get(deviceId);
        }

        if (conn == null) {
            download.setText(Util.readableTransferRate(getContext(), 0));
            upload.setText(Util.readableTransferRate(getContext(), 0));
            status.setText(r.getString(R.string.device_state_unknown));
            status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
            return convertView;
        }

        if (conn.paused) {
            download.setText(Util.readableTransferRate(getContext(), 0));
            upload.setText(Util.readableTransferRate(getContext(), 0));
            status.setText(r.getString(R.string.device_paused));
            status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_black));
            return convertView;
        }

        if (conn.connected) {
            download.setText(Util.readableTransferRate(getContext(), conn.inBits));
            upload.setText(Util.readableTransferRate(getContext(), conn.outBits));
            if (conn.completion == 100) {
                status.setText(r.getString(R.string.device_up_to_date));
                status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_green));
            } else {
                status.setText(r.getString(R.string.device_syncing, conn.completion));
                status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
            }
            return convertView;
        }

        // !conn.connected
        download.setText(Util.readableTransferRate(getContext(), 0));
        upload.setText(Util.readableTransferRate(getContext(), 0));
        status.setText(r.getString(R.string.device_disconnected));
        status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
        return convertView;
    }

    /**
     * Requests new connection info for all devices visible in listView.
     */
    public void updateConnections(RestApi api) {
        for (int i = 0; i < getCount(); i++) {
            api.getConnections(this::onReceiveConnections);
        }
    }

    private void onReceiveConnections(Connections connections) {
        mConnections = connections;
        notifyDataSetChanged();
    }
}
