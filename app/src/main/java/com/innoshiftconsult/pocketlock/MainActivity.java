package com.innoshiftconsult.pocketlock;

import com.innoshiftconsult.pocketlock.R;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.view.View;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int ADMIN_REQUEST = 100;
    private static final long PROXIMITY_DEBOUNCE_MS = 400L;
    private static final String PRIVACY_POLICY_URL =
            "https://mcmoict.github.io/pocket-lock-android/privacy-policy.html";
    private static final String PREFS = "pocket_lock";
    private static final String ENABLED = "enabled";
    private static final String SENSITIVE = "sensitive";
    private DevicePolicyManager devicePolicyManager;
    private SensorManager sensorManager;
    private ComponentName adminComponent;
    private Switch pocketSwitch;
    private TextView statusText;
    private TextView detectionModeValue;
    private TextView detectionModeGuide;
    private Button detectionModeButton;
    private boolean detectionTestRunning;
    private boolean detectionListenersRegistered;
    private boolean lastProximityNear;
    private int nearDetectedCount;
    private int farDetectedCount;
    private long lastProximityTransitionAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.background_top));
        }
        setContentView(R.layout.activity_main);

        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        adminComponent = new ComponentName(this, PocketLockAdminReceiver.class);
        statusText = findViewById(R.id.statusText);
        pocketSwitch = findViewById(R.id.pocketSwitch);
        detectionModeValue = findViewById(R.id.detectionModeValue);
        detectionModeGuide = findViewById(R.id.detectionModeGuide);
        detectionModeButton = findViewById(R.id.detectionModeButton);
        Button adminButton = findViewById(R.id.adminButton);
        Button uninstallButton = findViewById(R.id.uninstallButton);
        Button diagnosticsButton = findViewById(R.id.diagnosticsButton);
        RadioGroup sensitivityGroup = findViewById(R.id.sensitivityGroup);
        RadioButton normalRadio = findViewById(R.id.normalSensitivity);
        RadioButton sensitiveRadio = findViewById(R.id.sensitiveSensitivity);
        TextView privacyPolicyLink = findViewById(R.id.privacyPolicyLink);
        final boolean[] restoringSensitivity = {false};
        boolean sensitive = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SENSITIVE, false);
        sensitivityGroup.check(sensitive ? sensitiveRadio.getId() : normalRadio.getId());
        sensitivityGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (restoringSensitivity[0]) {
                return;
            }
            if (checkedId != sensitiveRadio.getId()) {
                saveSensitivity(false);
                return;
            }
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                    .setTitle("민감 모드 사용")
                    .setMessage("민감 모드에서는 근접센서에 의해 사용 중인 화면이 닫힐 수 있습니다. 설정하시겠습니까?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("설정", (ignoredDialog, which) -> saveSensitivity(true))
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                    .setOnClickListener(view -> {
                        restoringSensitivity[0] = true;
                        group.check(normalRadio.getId());
                        restoringSensitivity[0] = false;
                        saveSensitivity(false);
                        dialog.dismiss();
                    }));
            dialog.setOnCancelListener(ignored -> {
                restoringSensitivity[0] = true;
                group.check(normalRadio.getId());
                restoringSensitivity[0] = false;
                saveSensitivity(false);
            });
            dialog.show();
        });

        adminButton.setOnClickListener(view -> requestAdminAccess());
        uninstallButton.setOnClickListener(view -> prepareForUninstall());
        diagnosticsButton.setOnClickListener(view -> startActivity(
            new Intent(this, DiagnosticsActivity.class)));
        detectionModeButton.setOnClickListener(view -> toggleDetectionModeTest());
        privacyPolicyLink.setOnClickListener(view -> startActivity(
            new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))));
        pocketSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) {
                setPocketMode(checked);
            }
        });
        updateUi();
        requestNotificationPermission();
    }

    private void requestAdminAccess() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "주머니에서 화면이 눌리지 않도록 화면을 즉시 잠그는 권한입니다.");
            startActivityForResult(intent, ADMIN_REQUEST);
        }
    }

    private void setPocketMode(boolean enabled) {
        if (enabled && !devicePolicyManager.isAdminActive(adminComponent)) {
            pocketSwitch.setChecked(false);
            requestAdminAccess();
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply();
        Intent serviceIntent = new Intent(this, PocketLockService.class);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else {
            stopService(serviceIntent);
        }
        updateUi();
    }

    private void saveSensitivity(boolean sensitive) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(SENSITIVE, sensitive).apply();
        updateUi();
    }

    private void toggleDetectionModeTest() {
        if (detectionTestRunning) {
            finishDetectionModeTest();
        } else {
            beginDetectionModeTest();
        }
    }

    private void beginDetectionModeTest() {
        detectionTestRunning = true;
        lastProximityNear = false;
        nearDetectedCount = 0;
        farDetectedCount = 0;
        lastProximityTransitionAt = 0L;
        detectionModeGuide.setVisibility(View.VISIBLE);
        updateDetectionModeSummary();
        registerDetectionSensor();
    }

    private void finishDetectionModeTest() {
        detectionTestRunning = false;
        unregisterDetectionSensor();

        boolean proximityAvailable = sensorManager != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null;
        boolean validPattern = nearDetectedCount >= 2 && farDetectedCount >= 2;
        ProximityReliability reliability;
        if (!proximityAvailable) {
            reliability = ProximityReliability.UNAVAILABLE;
        } else {
            reliability = validPattern
                    ? ProximityReliability.RELIABLE : ProximityReliability.UNRELIABLE;
        }
        ProximityReliabilityManager.set(this, reliability);
        detectionModeGuide.setVisibility(View.GONE);
        updateDetectionModeSummary(reliability);
        updateUi();
    }

    private void registerDetectionSensor() {
        if (detectionListenersRegistered || sensorManager == null) {
            return;
        }
        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        detectionListenersRegistered = true;
    }

    private void unregisterDetectionSensor() {
        if (detectionListenersRegistered && sensorManager != null) {
            sensorManager.unregisterListener(this);
            detectionListenersRegistered = false;
        }
    }

    private void updateDetectionModeSummary() {
        updateDetectionModeSummary(ProximityReliabilityManager.get(this));
    }

    private void updateDetectionModeSummary(ProximityReliability reliability) {
        if (reliability == ProximityReliability.UNAVAILABLE
                || reliability == ProximityReliability.UNRELIABLE) {
            detectionModeValue.setText("복합 센서 방식");
        } else {
            detectionModeValue.setText("근접 센서 방식");
        }
        detectionModeButton.setText(detectionTestRunning ? "완료하기" : "판단하기");
    }

    private void prepareForUninstall() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ENABLED, false).apply();
        stopService(new Intent(this, PocketLockService.class));

        Intent adminSettingsIntent = new Intent();
        adminSettingsIntent.setComponent(new ComponentName(
            "com.android.settings",
            "com.android.settings.Settings$DeviceAdminSettingsActivity"));
        if (adminSettingsIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(adminSettingsIntent);
        } else {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }

    private void updateUi() {
        boolean adminActive = devicePolicyManager.isAdminActive(adminComponent);
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false);
        boolean sensitive = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SENSITIVE, false);
        boolean proximityAvailable = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null;
        ProximityReliability proximityReliability = ProximityReliabilityManager.get(this);

        if (!adminActive) {
            statusText.setText("먼저 기기 관리자 권한을 허용해 주세요.");
        } else if (!proximityAvailable && proximityReliability != ProximityReliability.UNAVAILABLE) {
            ProximityReliabilityManager.set(this, ProximityReliability.UNAVAILABLE);
            proximityReliability = ProximityReliability.UNAVAILABLE;
        }

        if (enabled) {
            if (proximityReliability == ProximityReliability.UNAVAILABLE || proximityReliability == ProximityReliability.UNRELIABLE) {
                statusText.setText("감시중 · 조도·가속도·자세 센서로 주머니 상태를 확인합니다.");
            } else if (proximityAvailable) {
                statusText.setText(sensitive
                        ? "감시중 · 근접 센서가 가려지면 화면을 잠급니다."
                        : "감시중 · 근접 센서로 주머니 상태를 확인합니다.");
            } else {
                statusText.setText("감시중 · 조도·가속도·자세 센서로 주머니 상태를 확인합니다.");
            }
        } else {
            statusText.setText("준비됨 · 주머니 잠금 켜기를 활성화해 주세요.");
        }
        pocketSwitch.setChecked(adminActive && enabled);
        updateDetectionModeSummary();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (detectionTestRunning) {
            registerDetectionSensor();
        }
        updateUi();
        boolean adminActive = devicePolicyManager.isAdminActive(adminComponent);
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false);
        if (adminActive && enabled) {
            Intent serviceIntent = new Intent(this, PocketLockService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    @Override
    protected void onPause() {
        unregisterDetectionSensor();
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!detectionTestRunning || event == null || event.sensor == null
                || event.sensor.getType() != Sensor.TYPE_PROXIMITY
                || event.values == null || event.values.length == 0) {
            return;
        }

        Sensor proximitySensor = event.sensor;
        float threshold = Math.max(1.0f, proximitySensor.getMaximumRange() * 0.5f);
        boolean isNear = event.values[0] < threshold;
        long now = System.currentTimeMillis();
        if (lastProximityTransitionAt == 0L) {
            lastProximityNear = isNear;
            lastProximityTransitionAt = now;
            return;
        }
        if (isNear != lastProximityNear
                && now - lastProximityTransitionAt >= PROXIMITY_DEBOUNCE_MS) {
            if (isNear) {
                nearDetectedCount++;
            } else {
                farDetectedCount++;
            }
            lastProximityNear = isNear;
            lastProximityTransitionAt = now;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}
