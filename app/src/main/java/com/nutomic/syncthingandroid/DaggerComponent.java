package com.nutomic.syncthingandroid;

import com.nutomic.syncthingandroid.activities.FirstStartActivity;
import com.nutomic.syncthingandroid.activities.FolderPickerActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.receiver.AppConfigReceiver;
import com.nutomic.syncthingandroid.service.RunConditionMonitor;
import com.nutomic.syncthingandroid.service.EventProcessor;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Languages;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {SyncthingModule.class})
public interface DaggerComponent {

    void inject(SyncthingApp app);
    void inject(MainActivity activity);
    void inject(FirstStartActivity activity);
    void inject(FolderPickerActivity activity);
    void inject(Languages languages);
    void inject(SyncthingService service);
    void inject(RunConditionMonitor runConditionMonitor);
    void inject(EventProcessor eventProcessor);
    void inject(SyncthingRunnable syncthingRunnable);
    void inject(NotificationHandler notificationHandler);
    void inject(AppConfigReceiver appConfigReceiver);
    void inject(RestApi restApi);
    void inject(SettingsActivity.SettingsFragment fragment);
}
