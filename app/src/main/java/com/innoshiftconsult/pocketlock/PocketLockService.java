package com.innoshiftconsult.pocketlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Deque;

public class PocketLockService extends Service implements SensorEventListener {
    private static final String CHANNEL_ID = "pocket_lock";
    private static final int NOTIFICATION_ID = 1;
    private static final long NORMAL_COVER_CONFIRMATION_MILLIS = 500L;
    private static final long SENSITIVE_COVER_CONFIRMATION_MILLIS = 300L;
    private static final long PROXIMITY_DEBOUNCE_MS = 400L;
    private static final String PREFS = "pocket_lock";
    private static final String SENSITIVE = "sensitive";
    public static final String ACTION_SENSOR_STATE = "com.innoshiftconsult.pocketlock.SENSOR_STATE";
    public static final String EXTRA_PROXIMITY = "proximity";
    public static final String EXTRA_UPSIDE_DOWN = "upside_down";
    private static final float UPSIDE_DOWN_GRAVITY_THRESHOLD = -4.5f;

    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private Sensor accelerometer;
    private Sensor lightSensor;
    private PocketSensorFusionDetector pocketSensorFusionDetector;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private boolean lockedForCurrentCover;
    private boolean lockPending;
    private boolean proximityCovered;
    private boolean isUpsideDown;
    private Boolean lastObservedNearState;
    private long lastProximityTransitionAt;
    private final Deque<Boolean> recentProximityStates = new ArrayDeque<>();
    private ProximityDetectionMode currentDetectionMode = ProximityDetectionMode.PROXIMITY;
    private Handler handler;
    private final Runnable lockAfterConfirmedCover = () -> {
        lockPending = false;
        if (proximityCovered && (isSensitiveMode() || isUpsideDown)
                && !lockedForCurrentCover && devicePolicyManager.isAdminActive(adminComponent)) {
            lockedForCurrentCover = true;
            devicePolicyManager.lockNow();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        proximitySensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        lightSensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        pocketSensorFusionDetector = new PocketSensorFusionDetector();
        isUpsideDown = accelerometer == null;
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, PocketLockAdminReceiver.class);
        handler = new Handler(Looper.getMainLooper());
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        updateDetectionMode();
        broadcastSensorState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_PROXIMITY:
                if (proximitySensor == null || event.values == null || event.values.length == 0) {
                    return;
                }
                updateDetectionMode();
                float threshold = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(SENSITIVE, false)
                        ? proximitySensor.getMaximumRange()
                        : Math.min(proximitySensor.getMaximumRange(), 1.0f);
                boolean currentlyNear = event.values[0] < threshold;

                if (currentDetectionMode == ProximityDetectionMode.PROXIMITY) {
                    long nowMs = System.currentTimeMillis();
                    if (lastObservedNearState != null && currentlyNear != lastObservedNearState
                            && nowMs - lastProximityTransitionAt >= PROXIMITY_DEBOUNCE_MS) {
                        recentProximityStates.addLast(currentlyNear);
                        while (recentProximityStates.size() > 6) {
                            recentProximityStates.removeFirst();
                        }
                        if (containsReliablePattern(recentProximityStates)) {
                            ProximityReliabilityManager.set(this, ProximityReliability.RELIABLE);
                        }
                        lastProximityTransitionAt = nowMs;
                    }
                    lastObservedNearState = currentlyNear;
                    proximityCovered = currentlyNear;
                }
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (event.values == null || event.values.length < 3) {
                    return;
                }
                isUpsideDown = event.values[1] < UPSIDE_DOWN_GRAVITY_THRESHOLD;
                if (currentDetectionMode == ProximityDetectionMode.SENSOR_FUSION) {
                    pocketSensorFusionDetector.onAccelerometer(
                            event.values[0], event.values[1], event.values[2],
                            System.currentTimeMillis());
                    proximityCovered = pocketSensorFusionDetector.shouldLock();
                }
                break;
            case Sensor.TYPE_LIGHT:
                if (event.values == null || event.values.length == 0) {
                    return;
                }
                if (currentDetectionMode == ProximityDetectionMode.SENSOR_FUSION) {
                    pocketSensorFusionDetector.onLight(event.values[0], System.currentTimeMillis());
                    proximityCovered = pocketSensorFusionDetector.shouldLock();
                }
                break;
            default:
                return;
        }

        if (!proximityCovered || (!isSensitiveMode() && !isUpsideDown)) {
            lockedForCurrentCover = false;
            lockPending = false;
            handler.removeCallbacks(lockAfterConfirmedCover);
            broadcastSensorState();
            return;
        }

        if (!lockPending && !lockedForCurrentCover) {
            lockPending = true;
            long confirmationMillis = isSensitiveMode()
                    ? SENSITIVE_COVER_CONFIRMATION_MILLIS
                    : NORMAL_COVER_CONFIRMATION_MILLIS;
            handler.postDelayed(lockAfterConfirmedCover, confirmationMillis);
        }
        broadcastSensorState();
    }

    private void updateDetectionMode() {
        ProximityReliability reliability = ProximityReliabilityManager.get(this);
        boolean proximityAvailable = proximitySensor != null;
        if (reliability == ProximityReliability.UNAVAILABLE || reliability == ProximityReliability.UNRELIABLE) {
            currentDetectionMode = ProximityDetectionMode.SENSOR_FUSION;
        } else if (!proximityAvailable) {
            ProximityReliabilityManager.set(this, ProximityReliability.UNAVAILABLE);
            currentDetectionMode = ProximityDetectionMode.SENSOR_FUSION;
        } else {
            currentDetectionMode = ProximityDetectionMode.PROXIMITY;
        }
    }

    private boolean containsReliablePattern(Deque<Boolean> states) {
        if (states.size() < 3) {
            return false;
        }
        boolean[] stateArray = new boolean[states.size()];
        int index = 0;
        for (Boolean state : states) {
            stateArray[index++] = Boolean.TRUE.equals(state);
        }
        for (int i = 0; i <= stateArray.length - 3; i++) {
            if (!stateArray[i] && stateArray[i + 1] && !stateArray[i + 2]) {
                return true;
            }
        }
        return false;
    }

    private boolean isSensitiveMode() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SENSITIVE, false);
    }

    private void broadcastSensorState() {
        Intent stateIntent = new Intent(ACTION_SENSOR_STATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_PROXIMITY, proximityCovered)
                .putExtra(EXTRA_UPSIDE_DOWN, isUpsideDown);
        sendBroadcast(stateIntent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacks(lockAfterConfirmedCover);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, launchIntent,
                pendingIntentFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Pocket Lock 활성화됨")
                .setContentText("근접 센서를 감시하고 있습니다")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Pocket Lock", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}
