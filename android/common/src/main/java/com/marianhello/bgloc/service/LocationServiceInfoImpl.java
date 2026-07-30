package com.marianhello.bgloc.service;

import android.app.ActivityManager;
import android.content.Context;

public class LocationServiceInfoImpl implements LocationServiceInfo {
    private Context mContext;

    public LocationServiceInfoImpl(Context context) {
        mContext = context;
    }

    /**
     * Whether LocationServiceImpl is currently started.
     *
     * <p>Trusts the service's own flag first. {@code ActivityManager.getRunningServices()} is
     * deprecated since API 26 and several OEM ROMs return incomplete lists, which mattered a lot
     * here: every command in {@code LocationServiceProxy} is gated on this method, so a false
     * negative silently swallowed {@code stop()} (leaving the service running forever) and a false
     * positive sent commands to a dead service. The static flag is authoritative within our own
     * process; getRunningServices() stays only as a cross-process fallback.
     */
    @Override
    public boolean isStarted() {
        if (LocationServiceImpl.isRunning()) {
            return true;
        }
        ActivityManager.RunningServiceInfo info = getRunningServiceInfo();
        if (info != null) {
            return info.started;
        }
        return false;
    }

    @Override
    public boolean isBound() {
        ActivityManager.RunningServiceInfo info = getRunningServiceInfo();
        if (info != null) {
            return info.clientCount > 0;
        }
        return false;
    }

    public ActivityManager.RunningServiceInfo getRunningServiceInfo() {
        String serviceName = LocationServiceImpl.class.getName();
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(info.service.getClassName())) {
                return info;
            }
        }
        return null;
    }
}
