package com.innoshiftconsult.pocketlock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

public class DiagnosticsActivity extends Activity implements SensorEventListener {
    private static final int MAX_EVENT_LOG_SIZE = 200;
    private static final long ACCELEROMETER_LOG_INTERVAL_MILLIS = 400L;
    private static final long TEST_TIMER_INTERVAL_MILLIS = 1000L;
    private SensorManager sensorManager;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private Sensor proximitySensor;
    private Sensor accelerometer;
    private Sensor lightSensor;
    private TableLayout deviceInfoValues;
    private TableLayout adminValues;
    private TableLayout proximityValues;
    private TableLayout accelerometerValues;
    private TableLayout lightValues;
    private TextView lockTestStatus;
    private TextView sensorTestStatus;
    private TextView sensorTestElapsed;
    private TextView eventLogText;
    private ScrollView eventLogScrollView;
    private Button sensorTestButton;
    private boolean listenersRegistered;
    private boolean sensorTestRunning;
    private long sensorTestStartedAt;
    private long lastAccelerometerLogAt;
    private final Deque<String> eventLog = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable testTimer = new Runnable() {
        @Override
        public void run() {
            updateTestControls();
            if (sensorTestRunning) {
                handler.postDelayed(this, TEST_TIMER_INTERVAL_MILLIS);
            }
        }
    };
    private float proximityValue;
    private float accelerometerX;
    private float accelerometerY;
    private float accelerometerZ;
    private float lightValue;
    private int proximityEventCount;
    private int accelerometerEventCount;
    private int lightEventCount;
    private float proximityMinimum;
    private float proximityMaximumObserved;
    private float lightMinimum;
    private float lightMaximumObserved;
    private boolean hasProximityValue;
    private boolean hasLightValue;
    private String proximityLastEvent = "-";
    private String accelerometerLastEvent = "-";
    private String lightLastEvent = "-";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, PocketLockAdminReceiver.class);
        proximitySensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        accelerometer = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        lightSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        deviceInfoValues = findViewById(R.id.deviceInfoValues);
        adminValues = findViewById(R.id.adminValues);
        proximityValues = findViewById(R.id.proximityValues);
        accelerometerValues = findViewById(R.id.accelerometerValues);
        lightValues = findViewById(R.id.lightValues);
        lockTestStatus = findViewById(R.id.lockTestStatus);
        sensorTestStatus = findViewById(R.id.sensorTestStatus);
        sensorTestElapsed = findViewById(R.id.sensorTestElapsed);
        eventLogText = findViewById(R.id.eventLogText);
        eventLogScrollView = findViewById(R.id.eventLogScrollView);
        sensorTestButton = findViewById(R.id.sensorTestButton);

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        Button lockTestButton = findViewById(R.id.lockTestButton);
        lockTestButton.setOnClickListener(view -> testDeviceLock());
        View copyButton = findViewById(R.id.copyButton);
        copyButton.setOnClickListener(view -> copyDiagnostics());
        findViewById(R.id.resetMeasurementsButton).setOnClickListener(
            view -> resetMeasurements());
        sensorTestButton.setOnClickListener(view -> toggleSensorTest());
        updateAllViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensorListeners();
        updateAllViews();
    }

    @Override
    protected void onPause() {
        unregisterSensorListeners();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(testTimer);
        super.onDestroy();
    }

    private void registerSensorListeners() {
        if (listenersRegistered || sensorManager == null) {
            return;
        }
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        listenersRegistered = true;
    }

    private void unregisterSensorListeners() {
        if (listenersRegistered && sensorManager != null) {
            sensorManager.unregisterListener(this);
            listenersRegistered = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null || event.values == null
                || event.values.length == 0) {
            return;
        }
        if (!sensorTestRunning) {
            return;
        }
        String eventTime = formatEventTime();
        switch (event.sensor.getType()) {
            case Sensor.TYPE_PROXIMITY:
                proximityValue = event.values[0];
                proximityEventCount++;
                if (!hasProximityValue) {
                    proximityMinimum = proximityValue;
                    proximityMaximumObserved = proximityValue;
                    hasProximityValue = true;
                } else {
                    proximityMinimum = Math.min(proximityMinimum, proximityValue);
                    proximityMaximumObserved = Math.max(proximityMaximumObserved, proximityValue);
                }
                proximityLastEvent = eventTime;
                addEventLog(eventTime + "  PROXIMITY   " + format(proximityValue));
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (event.values.length >= 3) {
                    accelerometerX = event.values[0];
                    accelerometerY = event.values[1];
                    accelerometerZ = event.values[2];
                }
                accelerometerEventCount++;
                accelerometerLastEvent = eventTime;
                if (System.currentTimeMillis() - lastAccelerometerLogAt
                        >= ACCELEROMETER_LOG_INTERVAL_MILLIS) {
                    lastAccelerometerLogAt = System.currentTimeMillis();
                    addEventLog(eventTime + "  ACCEL       X=" + format(accelerometerX)
                            + " Y=" + format(accelerometerY) + " Z=" + format(accelerometerZ));
                }
                break;
            case Sensor.TYPE_LIGHT:
                lightValue = event.values[0];
                lightEventCount++;
                if (!hasLightValue) {
                    lightMinimum = lightValue;
                    lightMaximumObserved = lightValue;
                    hasLightValue = true;
                } else {
                    lightMinimum = Math.min(lightMinimum, lightValue);
                    lightMaximumObserved = Math.max(lightMaximumObserved, lightValue);
                }
                lightLastEvent = eventTime;
                addEventLog(eventTime + "  LIGHT       " + format(lightValue) + " lux");
                break;
            default:
                return;
        }
        updateSensorViews();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateAllViews() {
        clearTable(deviceInfoValues);
        addRow(deviceInfoValues, "제조사", Build.MANUFACTURER);
        addRow(deviceInfoValues, "모델", Build.MODEL);
        addRow(deviceInfoValues, "Android", Build.VERSION.RELEASE);
        addRow(deviceInfoValues, "SDK", String.valueOf(Build.VERSION.SDK_INT));
        updateAdminView();
        updateSensorViews();
        updateTestControls();
        updateEventLogView();
    }

    private void updateAdminView() {
        boolean active = isAdminActive();
        clearTable(adminValues);
        addRow(adminValues, "상태", active ? "정상 · 활성화" : "사용 불가 · 비활성화");
        lockTestStatus.setText("기기 관리자    " + (active ? "정상 · 활성화" : "사용 불가 · 비활성화"));
    }

    private void updateSensorViews() {
        updateProximityTable();
        updateMotionTable();
        updateLightTable();
    }

    private void updateProximityTable() {
        clearTable(proximityValues);
        if (proximitySensor == null) {
            addRow(proximityValues, "상태", "확인 필요 · 감지되지 않음");
            addRow(proximityValues, "이름", "-");
            addRow(proximityValues, "제조사", "-");
            addRow(proximityValues, "현재값", "-");
            addRow(proximityValues, "이벤트", "-");
            return;
        }
        String eventStatus = proximityEventCount == 0 ? "확인 필요 · 이벤트 없음" : "정상 · 이벤트 수신 중";
        String coverStatus = proximityEventCount == 0 ? "-"
                : (proximityValue < proximitySensor.getMaximumRange() ? "정상 · 가림 감지" : "열림");
        addRow(proximityValues, "상태", "정상 · 감지됨");
        addRow(proximityValues, "이벤트", eventStatus);
        addRow(proximityValues, "이름", valueOrDash(proximitySensor.getName()));
        addRow(proximityValues, "제조사", valueOrDash(proximitySensor.getVendor()));
        addRow(proximityValues, "최대 범위", format(proximitySensor.getMaximumRange()) + " cm");
        addRow(proximityValues, "해상도", format(proximitySensor.getResolution()));
        addRow(proximityValues, "전력", format(proximitySensor.getPower()) + " mA");
        addRow(proximityValues, "현재값", proximityEventCount == 0 ? "-" : format(proximityValue) + " cm");
        addRow(proximityValues, "최소값", proximityEventCount == 0 ? "-" : format(proximityMinimum) + " cm");
        addRow(proximityValues, "최대 관측값", proximityEventCount == 0 ? "-" : format(proximityMaximumObserved) + " cm");
        addRow(proximityValues, "상태 판정", coverStatus);
        addRow(proximityValues, "이벤트 횟수", String.valueOf(proximityEventCount));
        addRow(proximityValues, "최근 이벤트", proximityLastEvent);
        addRow(proximityValues, "Version", String.valueOf(proximitySensor.getVersion()));
        addRow(proximityValues, "Type", String.valueOf(proximitySensor.getType()));
        addRow(proximityValues, "StringType", valueOrDash(proximitySensor.getStringType()));
        addRow(proximityValues, "WakeUp", proximitySensor.isWakeUpSensor() ? "YES" : "NO");
        addRow(proximityValues, "Reporting", reportingMode(proximitySensor.getReportingMode()));
    }

    private void updateMotionTable() {
        clearTable(accelerometerValues);
        if (accelerometer == null) {
            addRow(accelerometerValues, "상태", "확인 필요 · 감지되지 않음");
            addRow(accelerometerValues, "이름", "-");
            addRow(accelerometerValues, "이벤트", "-");
            return;
        }
        addRow(accelerometerValues, "상태", accelerometerEventCount == 0
                ? "확인 필요 · 이벤트 없음" : "정상 · 감지됨");
        addRow(accelerometerValues, "이름", valueOrDash(accelerometer.getName()));
        addRow(accelerometerValues, "제조사", valueOrDash(accelerometer.getVendor()));
        addRow(accelerometerValues, "최대 범위", format(accelerometer.getMaximumRange()));
        addRow(accelerometerValues, "X", accelerometerEventCount == 0 ? "-" : format(accelerometerX));
        addRow(accelerometerValues, "Y", accelerometerEventCount == 0 ? "-" : format(accelerometerY));
        addRow(accelerometerValues, "Z", accelerometerEventCount == 0 ? "-" : format(accelerometerZ));
        addRow(accelerometerValues, "이벤트 횟수", String.valueOf(accelerometerEventCount));
        addRow(accelerometerValues, "최근 이벤트", accelerometerLastEvent);
    }

    private void updateLightTable() {
        clearTable(lightValues);
        if (lightSensor == null) {
            addRow(lightValues, "상태", "확인 필요 · 감지되지 않음");
            addRow(lightValues, "현재값", "-");
            return;
        }
        addRow(lightValues, "상태", lightEventCount == 0
                ? "확인 필요 · 이벤트 없음" : "정상 · 감지됨");
        addRow(lightValues, "이름", valueOrDash(lightSensor.getName()));
        addRow(lightValues, "제조사", valueOrDash(lightSensor.getVendor()));
        addRow(lightValues, "최대 범위", format(lightSensor.getMaximumRange()) + " lux");
        addRow(lightValues, "현재값", lightEventCount == 0 ? "-" : format(lightValue) + " lux");
        addRow(lightValues, "최소값", lightEventCount == 0 ? "-" : format(lightMinimum) + " lux");
        addRow(lightValues, "최대 관측값", lightEventCount == 0 ? "-" : format(lightMaximumObserved) + " lux");
        addRow(lightValues, "이벤트 횟수", String.valueOf(lightEventCount));
        addRow(lightValues, "최근 이벤트", lightLastEvent);
    }

    private void clearTable(TableLayout table) {
        table.removeAllViews();
    }

    private void toggleSensorTest() {
        if (sensorTestRunning) {
            sensorTestRunning = false;
            addEventLog(formatEventTime() + "  === TEST END ===");
            handler.removeCallbacks(testTimer);
        } else {
            sensorTestRunning = true;
            sensorTestStartedAt = System.currentTimeMillis();
            lastAccelerometerLogAt = 0L;
            addEventLog(formatEventTime() + "  === TEST START ===");
            handler.post(testTimer);
        }
        updateTestControls();
        updateEventLogView();
    }

    private void resetMeasurements() {
        sensorTestRunning = false;
        handler.removeCallbacks(testTimer);
        proximityEventCount = 0;
        accelerometerEventCount = 0;
        lightEventCount = 0;
        proximityValue = 0f;
        accelerometerX = 0f;
        accelerometerY = 0f;
        accelerometerZ = 0f;
        lightValue = 0f;
        proximityMinimum = 0f;
        proximityMaximumObserved = 0f;
        lightMinimum = 0f;
        lightMaximumObserved = 0f;
        hasProximityValue = false;
        hasLightValue = false;
        proximityLastEvent = "-";
        accelerometerLastEvent = "-";
        lightLastEvent = "-";
        eventLog.clear();
        updateAllViews();
    }

    private void addEventLog(String entry) {
        eventLog.addLast(entry);
        while (eventLog.size() > MAX_EVENT_LOG_SIZE) {
            eventLog.removeFirst();
        }
        updateEventLogView();
    }

    private void updateEventLogView() {
        if (eventLog.isEmpty()) {
            eventLogText.setText("기록된 센서 이벤트가 없습니다.");
            return;
        }
        StringBuilder logText = new StringBuilder();
        for (String entry : eventLog) {
            if (logText.length() > 0) {
                logText.append('\n');
            }
            logText.append(entry);
        }
        eventLogText.setText(logText.toString());
        eventLogScrollView.post(() -> eventLogScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void updateTestControls() {
        sensorTestStatus.setText("센서 테스트: " + (sensorTestRunning ? "RUNNING" : "STOPPED"));
        long elapsed = sensorTestRunning ? System.currentTimeMillis() - sensorTestStartedAt : 0L;
        sensorTestElapsed.setText("경과 시간: " + formatDuration(elapsed));
        sensorTestButton.setText(sensorTestRunning ? "센서 테스트 종료" : "센서 테스트 시작");
    }

    private String formatDuration(long durationMillis) {
        long totalSeconds = durationMillis / 1000L;
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private String formatEventTime() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    private void addRow(TableLayout table, String label, String value) {
        TableRow row = new TableRow(this);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(getColor(R.color.ink));
        labelView.setTextSize(14);
        labelView.setPadding(4, 3, 16, 3);
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(getColor(R.color.ink));
        valueView.setTextSize(14);
        valueView.setPadding(4, 3, 4, 3);
        row.addView(labelView, new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.27f));
        row.addView(valueView, new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.73f));
        table.addView(row);
    }

    private void testDeviceLock() {
        if (!isAdminActive()) {
            lockTestStatus.setText("기기 관리자    사용 불가 · 권한이 필요합니다.");
            Toast.makeText(this, "기기 관리자 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            devicePolicyManager.lockNow();
            lockTestStatus.setText("기기 관리자    정상 · 잠금 요청 전송됨");
        } catch (SecurityException exception) {
            lockTestStatus.setText("기기 관리자    사용 불가 · 잠금 요청 실패");
            Toast.makeText(this, "화면 잠금 요청에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyDiagnostics() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Lock Diagnostics", buildDiagnostics()));
            Toast.makeText(this, "진단 결과가 복사되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildDiagnostics() {
        return "Pocket Lock Diagnostics\n\n"
                + "App Version: " + BuildConfig.VERSION_NAME + "\n\n"
                + "Device\nManufacturer: " + Build.MANUFACTURER + "\nModel: " + Build.MODEL
                + "\nAndroid: " + Build.VERSION.RELEASE + "\nSDK: " + Build.VERSION.SDK_INT + "\n\n"
                + "Device Admin\nActive: " + (isAdminActive() ? "YES" : "NO") + "\n\n"
                + "Proximity Sensor\n" + sensorSummary(proximitySensor, proximityEventCount)
                + "Current Value: " + (proximityEventCount == 0 ? "-" : format(proximityValue)) + "\n\n"
                + "Minimum Value: " + (proximityEventCount == 0 ? "-" : format(proximityMinimum)) + "\n"
                + "Maximum Observed Value: " + (proximityEventCount == 0 ? "-" : format(proximityMaximumObserved)) + "\n"
                + "Last Event: " + proximityLastEvent + "\n\n"
                + "Accelerometer\n" + sensorSummary(accelerometer, accelerometerEventCount)
                + "Current Value: " + (accelerometerEventCount == 0 ? "-"
                : "X=" + format(accelerometerX) + ", Y=" + format(accelerometerY)
                + ", Z=" + format(accelerometerZ)) + "\n"
                + "Last Event: " + accelerometerLastEvent + "\n\n"
                + "Light Sensor\n" + sensorSummary(lightSensor, lightEventCount)
                + "Current Value: " + (lightEventCount == 0 ? "-" : format(lightValue)) + " lux\n"
                + "Minimum Value: " + (lightEventCount == 0 ? "-" : format(lightMinimum)) + " lux\n"
                + "Maximum Observed Value: " + (lightEventCount == 0 ? "-" : format(lightMaximumObserved)) + " lux\n"
                + "Last Event: " + lightLastEvent + "\n\n"
                + "Lock Test\nDevice Admin Available: " + (isAdminActive() ? "YES" : "NO") + "\n\n"
                + "Sensor Test\nStatus: " + (sensorTestRunning ? "RUNNING" : "STOPPED") + "\n"
                + "Duration: " + formatDuration(sensorTestRunning
                ? System.currentTimeMillis() - sensorTestStartedAt : 0L) + "\n\n"
                + "센서 이벤트 로그\n" + eventLogText.getText().toString();
    }

    private String sensorSummary(Sensor sensor, int eventCount) {
        if (sensor == null) {
            return "Available: NO\nEvents: 0\n";
        }
        return "Available: YES\nName: " + valueOrDash(sensor.getName())
                + "\nVendor: " + valueOrDash(sensor.getVendor())
                + "\nMaximum Range: " + format(sensor.getMaximumRange())
                + "\nEvents: " + eventCount + "\n";
    }

    private boolean isAdminActive() {
        return devicePolicyManager != null && devicePolicyManager.isAdminActive(adminComponent);
    }

    private String valueOrDash(String value) {
        return value == null || value.length() == 0 ? "-" : value;
    }

    private String format(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String reportingMode(int mode) {
        switch (mode) {
            case Sensor.REPORTING_MODE_CONTINUOUS:
                return "CONTINUOUS";
            case Sensor.REPORTING_MODE_ON_CHANGE:
                return "ON_CHANGE";
            case Sensor.REPORTING_MODE_ONE_SHOT:
                return "ONE_SHOT";
            case Sensor.REPORTING_MODE_SPECIAL_TRIGGER:
                return "SPECIAL_TRIGGER";
            default:
                return "UNKNOWN";
        }
    }
}
