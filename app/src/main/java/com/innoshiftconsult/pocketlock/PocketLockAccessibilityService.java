package com.innoshiftconsult.pocketlock;

import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class PocketLockAccessibilityService extends AccessibilityService {
    private static final String TAG = "PocketLockAccessibility";
    private static volatile PocketLockAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean connected;

    public static boolean isEnabled(Context context) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        ComponentName target = new ComponentName(context, PocketLockAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        for (String flattenedComponent : splitter) {
            ComponentName enabled = ComponentName.unflattenFromString(flattenedComponent);
            if (target.equals(enabled)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConnected() {
        PocketLockAccessibilityService service = instance;
        return service != null && service.connected;
    }

    public static boolean requestScreenLock() {
        PocketLockAccessibilityService service = instance;
        if (service == null || !service.connected) {
            Log.w(TAG, "Screen lock requested while the accessibility service is disconnected.");
            return false;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return service.performScreenLock();
        }
        boolean posted = service.mainHandler.post(() -> {
            if (instance != service || !service.connected) {
                Log.w(TAG, "Queued screen lock was dropped after service disconnection.");
                return;
            }
            service.performScreenLock();
        });
        if (!posted) {
            Log.e(TAG, "Unable to queue screen-lock action on the main thread.");
        }
        return posted;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        instance = this;
        Log.i(TAG, "Accessibility service connected.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        connected = false;
        if (instance == this) {
            instance = null;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        if (instance == this) {
            instance = null;
        }
        Log.i(TAG, "Accessibility service disconnected.");
        super.onDestroy();
    }

    @SuppressLint("InlinedApi")
    private boolean performScreenLock() {
        if (!connected || instance != this
                || !ScreenLockManager.hasAccessibilityConsent(this)
                || !isEnabled(this)) {
            Log.w(TAG, "Screen lock skipped because the service or user consent is unavailable.");
            return false;
        }
        try {
            boolean accepted = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            if (!accepted) {
                Log.w(TAG, "Android rejected the global screen-lock action.");
            }
            return accepted;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Global screen-lock action failed.", exception);
            return false;
        }
    }
}
