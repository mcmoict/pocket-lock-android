package com.innoshiftconsult.pocketlock;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProximityReliabilityManager {
    private static final String PREFS = "pocket_lock";
    private static final String KEY_PROXIMITY_RELIABILITY = "proximity_reliability";

    private ProximityReliabilityManager() {
    }

    public static ProximityReliability get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_PROXIMITY_RELIABILITY, ProximityReliability.UNKNOWN.name());
        return parseStoredValue(stored);
    }

    public static void set(Context context, ProximityReliability reliability) {
        if (context == null || reliability == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROXIMITY_RELIABILITY, reliability.name())
                .apply();
    }

    public static ProximityDetectionMode determineDetectionMode(ProximityReliability reliability) {
        if (reliability == ProximityReliability.UNRELIABLE
                || reliability == ProximityReliability.UNAVAILABLE) {
            return ProximityDetectionMode.SENSOR_FUSION;
        }
        return ProximityDetectionMode.PROXIMITY;
    }

    public static boolean shouldUseFusion(Context context, boolean proximityAvailable) {
        if (!proximityAvailable) {
            return true;
        }
        ProximityReliability reliability = get(context);
        return reliability == ProximityReliability.UNRELIABLE
                || reliability == ProximityReliability.UNAVAILABLE;
    }

    public static ProximityReliability parseStoredValue(String storedValue) {
        if (storedValue == null) {
            return ProximityReliability.UNKNOWN;
        }
        try {
            return ProximityReliability.valueOf(storedValue.trim());
        } catch (IllegalArgumentException ex) {
            return ProximityReliability.UNKNOWN;
        }
    }
}
