package com.innoshiftconsult.pocketlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class PocketLockBootReceiver extends BroadcastReceiver {
    private static final String TAG = "PocketLockBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        boolean enabled = context.getSharedPreferences(
                ScreenLockManager.PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false);
        if (!enabled || !ScreenLockManager.hasPermissionConfigured(context)) {
            return;
        }

        Intent serviceIntent = new Intent(context, PocketLockService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (IllegalStateException exception) {
            Log.e(TAG, "Unable to restore Pocket Lock after boot.", exception);
        }
    }
}
