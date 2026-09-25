package com.innoshiftconsult.pocketlock;

import android.hardware.SensorManager;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Deque;

public class PocketSensorFusionDetector {
    private static final long LIGHT_WINDOW_MS = 6000L;
    private static final long MOTION_WINDOW_MS = 5000L;
    private static final long EVENT_CORRELATION_WINDOW_MS = 5000L;
    private static final long POCKET_CONFIRMATION_MS = 600L;
    private static final long LOCK_COOLDOWN_MS = 3000L;

    private static final float DARK_LUX_THRESHOLD = 30.0f;
    private static final float MIN_BASELINE_LUX = 20.0f;
    private static final float LIGHT_DROP_RATIO = 0.50f;
    private static final float MOTION_THRESHOLD = 0.25f;
    private static final float ORIENTATION_VERTICAL_THRESHOLD = 3.0f;

    private final Deque<LuxSample> recentLuxSamples = new ArrayDeque<>();
    private final Deque<MotionSample> recentMotionSamples = new ArrayDeque<>();
    private long lastDetectedPocketAt = -1L;
    private long lastLockAt = -1L;
    private float currentX;
    private float currentY;
    private float currentZ;

    public synchronized void onLight(float lux, long timestampMs) {
        recentLuxSamples.addLast(new LuxSample(lux, timestampMs));
        pruneLuxSamples(timestampMs);
    }

    public synchronized void onAccelerometer(float x, float y, float z, long timestampMs) {
        currentX = x;
        currentY = y;
        currentZ = z;
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        float motionDelta = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);
        recentMotionSamples.addLast(new MotionSample(motionDelta, timestampMs));
        pruneMotionSamples(timestampMs);
    }

    public synchronized boolean shouldLock() {
        long nowMs = getLatestTimestamp();
        if (lastLockAt >= 0L && nowMs - lastLockAt < LOCK_COOLDOWN_MS) {
            return false;
        }
        if (hasPocketCandidate(nowMs)) {
            if (lastDetectedPocketAt < 0L) {
                lastDetectedPocketAt = nowMs;
                return false;
            }
            if (nowMs - lastDetectedPocketAt >= POCKET_CONFIRMATION_MS) {
                lastLockAt = nowMs;
                lastDetectedPocketAt = -1L;
                return true;
            }
            return false;
        }
        lastDetectedPocketAt = -1L;
        return false;
    }

    private boolean hasPocketCandidate(long nowMs) {
        if (recentLuxSamples.isEmpty() || recentMotionSamples.isEmpty()) {
            return false;
        }
        LuxSample latestLux = recentLuxSamples.peekLast();
        float recentMaxLux = recentMaxLux();
        float currentLux = latestLux != null ? latestLux.lux : 0f;
        boolean hasBrightBaseline = recentMaxLux >= MIN_BASELINE_LUX;
        boolean darkTransition = currentLux <= DARK_LUX_THRESHOLD
            && (!hasBrightBaseline || currentLux <= recentMaxLux * LIGHT_DROP_RATIO);
        boolean recentMotion = hasRecentMotion(nowMs);
        boolean pocketLikeOrientation = isPocketLikeOrientation();
        return darkTransition && recentMotion && pocketLikeOrientation;
    }

    private long getLatestTimestamp() {
        long latest = 0L;
        for (LuxSample sample : recentLuxSamples) {
            latest = Math.max(latest, sample.timestampMs);
        }
        for (MotionSample sample : recentMotionSamples) {
            latest = Math.max(latest, sample.timestampMs);
        }
        return latest == 0L ? SystemClock.elapsedRealtime() : latest;
    }

    private float recentMaxLux() {
        float max = 0f;
        for (LuxSample sample : recentLuxSamples) {
            max = Math.max(max, sample.lux);
        }
        return max;
    }

    private boolean hasRecentMotion(long nowMs) {
        for (MotionSample motion : recentMotionSamples) {
            if (nowMs - motion.timestampMs <= MOTION_WINDOW_MS && motion.delta > MOTION_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private boolean isPocketLikeOrientation() {
        float absX = Math.abs(currentX);
        float absY = Math.abs(currentY);
        float absZ = Math.abs(currentZ);
        return currentY <= -ORIENTATION_VERTICAL_THRESHOLD
            && absY >= Math.min(absX, absZ);
    }

    private void pruneLuxSamples(long nowMs) {
        while (!recentLuxSamples.isEmpty()) {
            LuxSample oldest = recentLuxSamples.peekFirst();
            if (nowMs - oldest.timestampMs <= LIGHT_WINDOW_MS) {
                break;
            }
            recentLuxSamples.removeFirst();
        }
    }

    private void pruneMotionSamples(long nowMs) {
        while (!recentMotionSamples.isEmpty()) {
            MotionSample oldest = recentMotionSamples.peekFirst();
            if (nowMs - oldest.timestampMs <= EVENT_CORRELATION_WINDOW_MS) {
                break;
            }
            recentMotionSamples.removeFirst();
        }
    }

    private static class LuxSample {
        final float lux;
        final long timestampMs;

        LuxSample(float lux, long timestampMs) {
            this.lux = lux;
            this.timestampMs = timestampMs;
        }
    }

    private static class MotionSample {
        final float delta;
        final long timestampMs;

        MotionSample(float delta, long timestampMs) {
            this.delta = delta;
            this.timestampMs = timestampMs;
        }
    }
}
