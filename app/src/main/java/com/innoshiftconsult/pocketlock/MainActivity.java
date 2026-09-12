package com.innoshiftconsult.pocketlock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int ADMIN_REQUEST = 100;
    private static final String PREFS = "pocket_lock";
    private static final String ENABLED = "enabled";
    private static final String SENSITIVE = "sensitive";

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private Switch pocketSwitch;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, PocketLockAdminReceiver.class);
        statusText = findViewById(R.id.statusText);
        pocketSwitch = findViewById(R.id.pocketSwitch);
        Button adminButton = findViewById(R.id.adminButton);
        Button uninstallButton = findViewById(R.id.uninstallButton);
        RadioGroup sensitivityGroup = findViewById(R.id.sensitivityGroup);
        RadioButton normalRadio = findViewById(R.id.normalSensitivity);
        RadioButton sensitiveRadio = findViewById(R.id.sensitiveSensitivity);

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

        adminButton.setOnClickListener(view -> requestAdminAccess());
        uninstallButton.setOnClickListener(view -> prepareForUninstall());
        pocketSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) {
                setPocketMode(checked);
            }
        });
        updateUi();
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
        statusText.setText(adminActive
                ? "준비됨 · 근접 센서를 감시할 수 있습니다."
                : "먼저 기기 관리자 권한을 허용해 주세요.");
        pocketSwitch.setChecked(adminActive && enabled);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }
}
