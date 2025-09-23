package com.nutomic.syncthingandroid.service;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.R;

import static com.nutomic.syncthingandroid.service.RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED;
import static com.nutomic.syncthingandroid.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

@RequiresApi(api = Build.VERSION_CODES.N)
public class QuickSettingsTileSchedule extends TileService {
    public QuickSettingsTileSchedule() {

    }
    private Context mContext;
    private SharedPreferences mPreferences; // Manually initialized - no injection needed

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile != null) {
            mContext = getApplication().getApplicationContext();
            Resources res = mContext.getResources();
            mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

            // look through running services to see whether the app is currently running
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            boolean syncthingRunning = false;
            for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
                if (SyncthingService.class.getName().equals(service.service.getClassName())) {
                    syncthingRunning = true;
                    break;
                }
            }
            // disable tile if app is not running, schedule is off, or syncthing is force-started/stopped
            if (!syncthingRunning || !mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE,false)
                    || mPreferences.getInt(Constants.PREF_BTNSTATE_FORCE_START_STOP, Constants.BTNSTATE_NO_FORCE_START_STOP) != Constants.BTNSTATE_NO_FORCE_START_STOP) {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(res.getString(R.string.qs_schedule_disabled));
                tile.updateTile();
                return;
            }

            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(res.getString(
                    R.string.qs_schedule_label_minutes,
                    Integer.parseInt(mPreferences.getString(Constants.PREF_SYNC_DURATION_MINUTES, "5"))
            ));
            tile.updateTile();
        }
        super.onStartListening();
    }

    @Override
    public void onClick() {
        if (getQsTile().getState() == Tile.STATE_ACTIVE) {
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
            Intent intent = new Intent(ACTION_SYNC_TRIGGER_FIRED);
            intent.putExtra(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, true);
            localBroadcastManager.sendBroadcast(intent);
        }
    }
}
