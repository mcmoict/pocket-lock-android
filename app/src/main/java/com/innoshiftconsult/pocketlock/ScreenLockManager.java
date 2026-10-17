package com.innoshiftconsult.pocketlock;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

public final class ScreenLockManager {
    public static final String PREFS = "pocket_lock";
    public static final String ACCESSIBILITY_CONSENT = "accessibility_lock_consent";
    public static final String ACCESSIBILITY_DISCLOSURE_VERSION = "accessibility_disclosure_version";
    public static final String ACCESSIBILITY_DISCLOSURE_TIMESTAMP = "accessibility_disclosure_timestamp";
    // Raise this only when the disclosure content or data-handling purpose changes materially.
    public static final int CURRENT_DISCLOSURE_VERSION = 1;
    private static final String TAG = "PocketLockScreenLock";

    private ScreenLockManager() {
    }

    public static boolean hasPermissionConfigured(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return hasAccessibilityConsent(context)
                    && PocketLockAccessibilityService.isEnabled(context);
        }
        DevicePolicyManager manager = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, PocketLockAdminReceiver.class);
        return manager != null && manager.isAdminActive(admin);
    }

    public static boolean hasAccessibilityConsent(Context context) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Consent recorded before versioning existed is treated as version 1.
        return prefs.getBoolean(ACCESSIBILITY_CONSENT, false)
                && prefs.getInt(ACCESSIBILITY_DISCLOSURE_VERSION, 1) >= CURRENT_DISCLOSURE_VERSION;
    }

    public static void recordAccessibilityConsent(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ACCESSIBILITY_CONSENT, true)
                .putInt(ACCESSIBILITY_DISCLOSURE_VERSION, CURRENT_DISCLOSURE_VERSION)
                .putLong(ACCESSIBILITY_DISCLOSURE_TIMESTAMP, System.currentTimeMillis())
                .apply();
    }

    public static String getPermissionStatus(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return hasPermissionConfigured(context)
                    ? "기기 관리자 권한 활성화"
                    : "기기 관리자 권한 비활성화";
        }
        if (!hasAccessibilityConsent(context)) {
            return "앱 내 자동 잠금 안내에 동의하지 않음";
        }
        if (!PocketLockAccessibilityService.isEnabled(context)) {
            return "접근성 서비스 비활성화";
        }
        return PocketLockAccessibilityService.isConnected()
                ? "접근성 서비스 연결됨"
                : "접근성 서비스 연결 대기 중";
    }

    public static boolean requestLock(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!hasAccessibilityConsent(context)
                    || !PocketLockAccessibilityService.isEnabled(context)) {
                Log.w(TAG, "Accessibility lock permission is not configured.");
                return false;
            }
            return PocketLockAccessibilityService.requestScreenLock();
        }

        DevicePolicyManager manager = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, PocketLockAdminReceiver.class);
        if (manager == null || !manager.isAdminActive(admin)) {
            Log.w(TAG, "Device Admin lock permission is not active.");
            return false;
        }
        try {
            manager.lockNow();
            return true;
        } catch (SecurityException exception) {
            Log.e(TAG, "Device Admin screen-lock request was rejected.", exception);
            return false;
        }
    }
}
