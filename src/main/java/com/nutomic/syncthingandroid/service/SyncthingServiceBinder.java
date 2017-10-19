package com.nutomic.syncthingandroid.service;

import android.os.Binder;

public class SyncthingServiceBinder extends Binder {

    private final SyncthingService mService;

    public SyncthingServiceBinder(SyncthingService service) {
        mService = service;
    }

    public SyncthingService getService() {
        return mService;
    }

}
