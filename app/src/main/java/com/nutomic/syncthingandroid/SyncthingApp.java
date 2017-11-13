package com.nutomic.syncthingandroid;

import android.app.Application;
import android.os.StrictMode;

import com.nutomic.syncthingandroid.util.Languages;

import javax.inject.Inject;

public class SyncthingApp extends Application {

    @Inject DaggerComponent mComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        DaggerDaggerComponent.builder()
                .syncthingModule(new SyncthingModule(this))
                .build()
                .inject(this);

        new Languages(this).setLanguage(this);

        // Set VM policy to avoid crash when sending folder URI to file manager.
        StrictMode.VmPolicy policy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(policy);
    }

    public DaggerComponent component() {
        return mComponent;
    }
}
