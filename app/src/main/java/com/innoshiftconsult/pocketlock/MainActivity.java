package com.innoshiftconsult.pocketlock;

import com.innoshiftconsult.pocketlock.R;
import com.innoshiftconsult.pocketlock.BuildConfig;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.view.View;

public class MainActivity extends Activity {
    private static final int ADMIN_REQUEST = 100;
    private static final String PREFS = "pocket_lock";
    private static final String ENABLED = "enabled";
    private static final String SENSITIVE = "sensitive";
    private static final String REQUIRE_DARKNESS = "require_darkness";

    private DevicePolicyManager devicePolicyManager;
    private SensorManager sensorManager;
    private ComponentName adminComponent;
    private Switch pocketSwitch;
    private TextView statusText;
    private TextView sensorStatusText;
    private final BroadcastReceiver sensorStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSensorStatus(
                    intent.getBooleanExtra(PocketLockService.EXTRA_PROXIMITY, false),
                    intent.getBooleanExtra(PocketLockService.EXTRA_UPSIDE_DOWN, false),
                    intent.getBooleanExtra(PocketLockService.EXTRA_DARK, false));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        adminComponent = new ComponentName(this, PocketLockAdminReceiver.class);
        statusText = findViewById(R.id.statusText);
        sensorStatusText = findViewById(R.id.sensorStatusText);
        TextView conditionDescriptionText = findViewById(R.id.conditionDescriptionText);
        Button lockTestButton = findViewById(R.id.lockTestButton);
        updateDebugVisibility(sensorStatusText, conditionDescriptionText, lockTestButton);
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> updateDebugVisibility(sensorStatusText, conditionDescriptionText, lockTestButton),
            1000L);
        pocketSwitch = findViewById(R.id.pocketSwitch);
        Button adminButton = findViewById(R.id.adminButton);
        Button uninstallButton = findViewById(R.id.uninstallButton);
        RadioGroup sensitivityGroup = findViewById(R.id.sensitivityGroup);
        RadioButton normalRadio = findViewById(R.id.normalSensitivity);
        RadioButton sensitiveRadio = findViewById(R.id.sensitiveSensitivity);
        RadioGroup lightGroup = findViewById(R.id.lightGroup);
        RadioButton anyLightRadio = findViewById(R.id.anyLight);
        RadioButton darkOnlyRadio = findViewById(R.id.darkOnly);

        boolean sensitive = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SENSITIVE, false);
        sensitivityGroup.check(sensitive ? sensitiveRadio.getId() : normalRadio.getId());
        sensitivityGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != sensitiveRadio.getId()) {
                saveSensitivity(false);
                return;
            }
            new android.app.AlertDialog.Builder(this)
                    .setTitle("민감 모드 사용")
                    .setMessage("민감 모드에서는 근접센서에 의해 사용 중인 화면이 닫힐 수 있습니다. 설정하시겠습니까?")
                    .setNegativeButton("취소", (dialog, which) -> group.check(normalRadio.getId()))
                    .setPositiveButton("설정", (dialog, which) -> saveSensitivity(true))
                    .show();
        });

                boolean requireDarkness = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(REQUIRE_DARKNESS, false);
                lightGroup.check(requireDarkness ? darkOnlyRadio.getId() : anyLightRadio.getId());
                lightGroup.setOnCheckedChangeListener((group, checkedId) ->
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(REQUIRE_DARKNESS, checkedId == darkOnlyRadio.getId())
                        .apply());

        adminButton.setOnClickListener(view -> requestAdminAccess());
        lockTestButton.setOnClickListener(view -> testDeviceLock());
        uninstallButton.setOnClickListener(view -> prepareForUninstall());
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

    private void testDeviceLock() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            statusText.setText("먼저 기기 관리자 권한을 허용해 주세요.");
            return;
        }
        devicePolicyManager.lockNow();
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
        boolean proximityAvailable = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null;
        if (!adminActive) {
            statusText.setText("먼저 기기 관리자 권한을 허용해 주세요.");
        } else if (!proximityAvailable) {
            statusText.setText("이 휴대폰에는 근접센서가 없어 사용할 수 없습니다.");
        } else if (enabled) {
            statusText.setText("감시 중 · 근접센서가 0.5초 가려지면 화면을 잠급니다.");
        } else {
            statusText.setText("준비됨 · 주머니 잠금 켜기를 활성화해 주세요.");
        }
        pocketSwitch.setChecked(adminActive && enabled);
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
        updateDebugVisibility(sensorStatusText, findViewById(R.id.conditionDescriptionText),
            findViewById(R.id.lockTestButton));
        IntentFilter sensorFilter = new IntentFilter(PocketLockService.ACTION_SENSOR_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sensorStateReceiver, sensorFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sensorStateReceiver, sensorFilter);
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
        unregisterReceiver(sensorStateReceiver);
        super.onPause();
    }

    private void updateSensorStatus(boolean proximityCovered, boolean upsideDown, boolean dark) {
        boolean adminActive = devicePolicyManager.isAdminActive(adminComponent);
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false);
        sensorStatusText.setText("근접센서: " + (proximityCovered ? "가려짐" : "노출됨")
                + "\n방향: " + (upsideDown ? "세로 뒤집힘" : "일반")
                + "\n조도: " + (dark ? "어두움" : "밝음")
                + "\n관리자: " + (adminActive ? "허용됨" : "허용 필요")
                + "\n서비스: " + (enabled ? "활성화" : "비활성화"));
    }

        private void updateDebugVisibility(
            TextView sensorStatus, TextView conditionDescription, Button lockTestButton) {
        int visibility = BuildConfig.DEBUG && Debug.isDebuggerConnected()
                ? View.VISIBLE : View.GONE;
        sensorStatus.setVisibility(visibility);
        conditionDescription.setVisibility(visibility);
        lockTestButton.setVisibility(visibility);
    }
}
