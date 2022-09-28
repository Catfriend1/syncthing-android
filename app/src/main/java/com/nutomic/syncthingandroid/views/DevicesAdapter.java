package com.nutomic.syncthingandroid.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.databinding.ItemDeviceListBinding;
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.Util;

import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Generates item views for device items.
 */
public class DevicesAdapter extends ArrayAdapter<Device> {

    private static final String TAG = "DevicesAdapter";

    /**
     * If (incoming_bits_per_second + outgoing_bits_per_second) top the threshold,
     * we'll assume syncing state for the device reporting that data throughput.
     */
    private static final long ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD = 50 * 1024 * 8;

    private final Context mContext;

    private ConfigRouter mConfigRouter;
    private RestApi mRestApi;

    public DevicesAdapter(Context context) {
        super(context, 0);
        mContext = context;
    }

    public void setRestApi(ConfigRouter configRouter, RestApi restApi) {
        mConfigRouter = configRouter;
        mRestApi = restApi;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ItemDeviceListBinding binding = (convertView == null)
                ? DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.item_device_list, parent, false)
                : DataBindingUtil.bind(convertView);

        Device device = getItem(position);
        binding.name.setText(getItem(position).getDisplayName());

        updateDeviceStatusView(binding, device);
        return binding.getRoot();
    }

    @SuppressLint("SetTextI18n")
    private void updateDeviceStatusView(ItemDeviceListBinding binding, Device device) {
        View rateInOutView = binding.getRoot().findViewById(R.id.rateInOutContainer);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String deviceLastSeen = sharedPreferences.getString(
            Constants.PREF_CACHE_DEVICE_LASTSEEN_PREFIX + device.deviceID, ""
        );
        final String TIMESTAMP_NEVER_SEEN = "1970-01-01T00:00:00Z";
        binding.lastSeen.setText(mContext.getString(R.string.device_last_seen,
                TextUtils.isEmpty(deviceLastSeen) || deviceLastSeen.equals(TIMESTAMP_NEVER_SEEN) ?
                        mContext.getString(R.string.device_last_seen_never) : Util.formatDateTime(deviceLastSeen))
        );

        List<Folder> sharedFolders = mConfigRouter.getSharedFolders(device.deviceID);
        if (sharedFolders.size() == 0) {
            binding.sharedFoldersTitle.setText(R.string.device_state_unused);
            binding.sharedFolders.setVisibility(GONE);
        } else {
            binding.sharedFoldersTitle.setText(R.string.shared_folders_title_colon);
            binding.sharedFolders.setVisibility(VISIBLE);
            binding.sharedFolders.setText("\u2022 " + TextUtils.join("\n\u2022 ", sharedFolders));
        }

        if (device.paused) {
            binding.progressBar.setVisibility(GONE);
            rateInOutView.setVisibility(GONE);
            binding.status.setVisibility(VISIBLE);
            binding.status.setText(R.string.device_paused);
            binding.status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_purple));
            return;
        }

        if  (mRestApi == null || !mRestApi.isConfigLoaded()) {
            // Syncthing is not running.
            binding.progressBar.setVisibility(GONE);
            rateInOutView.setVisibility(GONE);
            binding.status.setText(R.string.device_disconnected);
            binding.status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
            return;
        }

        final Connection conn = mRestApi.getRemoteDeviceStatus(device.deviceID);
        final int completion = mRestApi.getRemoteDeviceCompletion(device.deviceID);
        final double needBytes = mRestApi.getRemoteDeviceNeedBytes(device.deviceID);

        if (conn.connected) {
            binding.status.setVisibility(VISIBLE);

            String bandwidthUpDownText = "\u21f5 ";     // down+up arrow
            bandwidthUpDownText += mContext.getString(R.string.download_title);
            bandwidthUpDownText += " \u02c5 ";          // down arrow
            bandwidthUpDownText += Util.readableTransferRate(getContext(), conn.inBits);
            bandwidthUpDownText += " \u2022 ";          // dot
            bandwidthUpDownText += mContext.getString(R.string.upload_title);
            bandwidthUpDownText += " \u02c4 ";          // up arrow
            bandwidthUpDownText += Util.readableTransferRate(getContext(), conn.outBits);
            binding.bandwidthUpDown.setText(bandwidthUpDownText);
            rateInOutView.setVisibility(VISIBLE);

            Boolean syncingState = !(completion == 100);
            binding.progressBar.setVisibility(syncingState ? VISIBLE : GONE);
            if (!syncingState) {
                /**
                 * UI polish - We distinguish the following cases:
                 * a) conn.completion == 100 because of the model init assignment, data transmission ongoing.
                 * b) conn.completion == 100 because of a finished sync, no data transmission.
                 */
                if ((conn.inBits + conn.outBits) >= ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD) {
                    // case a) device_syncing
                    binding.status.setText(R.string.state_syncing_general);
                    binding.status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
                } else {
                    // case b) device_up_to_date
                    binding.status.setText(R.string.device_up_to_date);
                    binding.status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_green));
                }
            } else {
                binding.progressBar.setProgress(completion);
                binding.status.setText(
                        mContext.getString(R.string.device_syncing_percent_bytes,
                                completion,
                                Util.readableFileSize(getContext(), needBytes)
                        )
                );
                binding.status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
            }
            return;
        }

        // !conn.connected
        binding.progressBar.setVisibility(GONE);
        rateInOutView.setVisibility(GONE);
        binding.status.setVisibility(VISIBLE);
        if (needBytes == 0) {
            binding.status.setText(R.string.device_disconnected);
        } else {
            binding.status.setText(
                    mContext.getString(R.string.device_disconnected_not_synced,
                            Util.readableFileSize(getContext(), needBytes)
                    )
            );
        }
        binding.status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
        return;
    }
}
