package com.nutomic.syncthingandroid;

import com.nutomic.syncthingandroid.activities.DeviceActivity;
import com.nutomic.syncthingandroid.activities.FirstStartActivity;
import com.nutomic.syncthingandroid.activities.FolderActivity;
import com.nutomic.syncthingandroid.activities.FolderPickerActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.PhotoShootActivity;
import com.nutomic.syncthingandroid.activities.RecentChangesActivity;
import com.nutomic.syncthingandroid.activities.ShareActivity;
import com.nutomic.syncthingandroid.activities.SyncConditionsActivity;
import com.nutomic.syncthingandroid.fragments.DeviceListFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.fragments.StatusFragment;
import com.nutomic.syncthingandroid.receiver.AppConfigReceiver;
import com.nutomic.syncthingandroid.service.RunConditionMonitor;
import com.nutomic.syncthingandroid.service.EventProcessor;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;
import com.nutomic.syncthingandroid.service.SyncthingService;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {SyncthingModule.class})
public interface DaggerComponent {
    void inject(AppConfigReceiver appConfigReceiver);
    void inject(DeviceActivity activity);
    void inject(DeviceListFragment fragment);
    void inject(EventProcessor eventProcessor);
    void inject(FirstStartActivity activity);
    void inject(FolderActivity activity);
    void inject(FolderListFragment fragment);
    void inject(MainActivity activity);
    void inject(PhotoShootActivity photoShootActivity);
    void inject(RestApi restApi);
    void inject(RecentChangesActivity recentChangesActivity);
    void inject(RunConditionMonitor runConditionMonitor);
    void inject(SettingsActivity.SettingsFragment fragment);
    void inject(ShareActivity activity);
    void inject(StatusFragment fragment);
    void inject(SyncConditionsActivity activity);
    void inject(SyncthingApp app);
    void inject(SyncthingRunnable syncthingRunnable);
    void inject(SyncthingService service);

}
