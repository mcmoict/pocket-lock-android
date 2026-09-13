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

public class PocketLockService extends Service implements SensorEventListener {
    private static final String CHANNEL_ID = "pocket_lock";
    private static final int NOTIFICATION_ID = 1;
    private static final long NORMAL_COVER_CONFIRMATION_MILLIS = 500L;
    private static final long SENSITIVE_COVER_CONFIRMATION_MILLIS = 300L;
    private static final String PREFS = "pocket_lock";
    private static final String SENSITIVE = "sensitive";
    public static final String ACTION_SENSOR_STATE = "com.innoshiftconsult.pocketlock.SENSOR_STATE";
    public static final String EXTRA_PROXIMITY = "proximity";
    public static final String EXTRA_UPSIDE_DOWN = "upside_down";
    private static final float UPSIDE_DOWN_GRAVITY_THRESHOLD = -4.5f;

    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private Sensor accelerometer;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private boolean lockedForCurrentCover;
    private boolean lockPending;
    private boolean proximityCovered;
    private boolean isUpsideDown;
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
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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
                float threshold = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(SENSITIVE, false)
                        ? proximitySensor.getMaximumRange()
                        : Math.min(proximitySensor.getMaximumRange(), 1.0f);
                proximityCovered = event.values[0] < threshold;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                isUpsideDown = event.values[1] < UPSIDE_DOWN_GRAVITY_THRESHOLD;
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
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
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
