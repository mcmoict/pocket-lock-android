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

public class MainActivity extends Activity {
    private static final int ADMIN_REQUEST = 100;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        adminComponent = new ComponentName(this, PocketLockAdminReceiver.class);
        statusText = findViewById(R.id.statusText);
        pocketSwitch = findViewById(R.id.pocketSwitch);
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
        if (!adminActive) {
            statusText.setText("먼저 기기 관리자 권한을 허용해 주세요.");
        } else if (!proximityAvailable) {
            statusText.setText("이 휴대폰에는 근접센서가 없어 사용할 수 없습니다.");
        } else if (enabled) {
            statusText.setText(sensitive
                    ? "감시 중 · 근접센서가 0.3초 이상 가려지면 화면을 잠급니다."
                    : "감시 중 · 휴대폰이 세로로 뒤집히고 근접센서가 0.5초 이상 가려지면 화면을 잠급니다.");
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

}
