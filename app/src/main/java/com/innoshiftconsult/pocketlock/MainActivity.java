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
import android.os.Handler;
import android.os.Looper;
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
    private static final int ACCESSIBILITY_CONSENT_REQUEST = 101;
    private static final long PROXIMITY_DEBOUNCE_MS = 400L;
    private static final long USAGE_GUIDE_PAUSE_MILLIS = 1500L;
    private static final long USAGE_GUIDE_SCROLL_HOLD_MILLIS = 5500L;
    private static final long USAGE_GUIDE_FADE_MILLIS = 450L;
    private static final String PRIVACY_POLICY_URL =
            "https://mcmoict.github.io/pocket-lock-android/privacy-policy.html";
    private static final String PREFS = "pocket_lock";
    private static final String ENABLED = "enabled";
    private static final String SENSITIVE = "sensitive";
    private static final String FIRST_RUN_GUIDE_SHOWN = "first_run_guide_shown";
    private DevicePolicyManager devicePolicyManager;
    private SensorManager sensorManager;
    private ComponentName adminComponent;
    private Switch pocketSwitch;
    private TextView statusText;
    private TextView detectionModeValue;
    private TextView detectionModeGuide;
    private GuideMarqueeTextView usageGuideText;
    private Button detectionModeButton;
    private Button adminButton;
    private boolean detectionTestRunning;
    private boolean detectionListenersRegistered;
    private boolean lastProximityNear;
    private int nearDetectedCount;
    private int farDetectedCount;
    private long lastProximityTransitionAt;
    private int usageGuideMessageIndex;
    private boolean waitingForAdminDeactivation;
    private boolean activityResumed;
    private int accessibilityStatusRefreshAttempts;
    private final Handler usageGuideHandler = new Handler(Looper.getMainLooper());
    private final Runnable usageGuideMarqueeStarter = this::startUsageGuideMarquee;
    private final Runnable usageGuideMarqueeCompletion = this::finishUsageGuideMessage;
    private final Runnable usageGuideFadeOut = this::fadeOutUsageGuide;
    private final Runnable accessibilityStatusRefresh = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return;
            }
            updateUi();
            boolean serviceNeedsRefresh = ScreenLockManager.hasAccessibilityConsent(MainActivity.this)
                    && PocketLockAccessibilityService.isEnabled(MainActivity.this)
                    && !PocketLockAccessibilityService.isConnected();
            if (serviceNeedsRefresh && accessibilityStatusRefreshAttempts++ < 8) {
                usageGuideHandler.postDelayed(this, 500L);
            }
        }
    };

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
        usageGuideText = findViewById(R.id.usageGuideText);
        usageGuideText.setSelected(false);
        tintCompoundDrawables(usageGuideText, R.color.white);
        detectionModeButton = findViewById(R.id.detectionModeButton);
        adminButton = findViewById(R.id.adminButton);
        tintCompoundDrawables(adminButton, R.color.icon_background);
        Button manualButton = findViewById(R.id.manualButton);
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

        adminButton.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                requestAccessibilityActivation();
            } else {
                requestAdminAccess();
            }
        });
        manualButton.setOnClickListener(view -> startActivity(
                new Intent(this, ManualActivity.class)));
        uninstallButton.setOnClickListener(view -> prepareForUninstall());
        diagnosticsButton.setOnClickListener(view -> startActivity(
            new Intent(this, DiagnosticsActivity.class)));
        detectionModeButton.setOnClickListener(view -> toggleDetectionModeTest());
        usageGuideText.setOnClickListener(view -> showUsageGuideDialog());
        privacyPolicyLink.setOnClickListener(view -> startActivity(
            new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))));
        pocketSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) {
                setPocketMode(checked);
            }
        });
        updateUi();
        showFirstRunGuideIfNeeded(savedInstanceState);
        requestNotificationPermission();
    }

    private void showFirstRunGuideIfNeeded(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (savedInstanceState != null || prefs.getBoolean(FIRST_RUN_GUIDE_SHOWN, false)) {
            return;
        }
        prefs.edit().putBoolean(FIRST_RUN_GUIDE_SHOWN, true).apply();
        String guide = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? "① 자동 화면 잠금 권한 안내를 확인하고 동의합니다.\n\n"
                        + "② Android 접근성 설정에서 Pocket Lock 서비스를 직접 켭니다.\n\n"
                        + "③ 앱으로 돌아와 [주머니 잠금 켜기]를 켭니다."
                : "① [기기 관리자 권한 허용]을 누릅니다.\n\n"
                        + "② [이 기기 관리자 앱 활성화]를 선택합니다.\n\n"
                        + "③ [주머니 잠금 켜기]를 켭니다.";
        new android.app.AlertDialog.Builder(this)
                .setTitle("처음 사용하시나요?")
                .setMessage(guide + "\n\n감지 방식은 [내 휴대폰에 맞게 자동 설정]에서 확인할 수 있어요.")
                .setPositiveButton("확인", null)
                .setNeutralButton("자세한 사용 방법", (dialog, which) -> startActivity(
                        new Intent(this, ManualActivity.class)))
                .show();
    }

    // android:drawableTint is ignored below API 23, so tint compound drawables in code instead.
    private void tintCompoundDrawables(TextView view, int colorResId) {
        int color = getResources().getColor(colorResId);
        for (android.graphics.drawable.Drawable drawable : view.getCompoundDrawablesRelative()) {
            if (drawable != null) {
                drawable.mutate().setTint(color);
            }
        }
    }

    private void requestAdminAccess() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            showLegacyLockNotice(() -> {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "주머니에서 화면이 눌리지 않도록 화면을 즉시 잠그는 권한입니다.");
                startActivityForResult(intent, ADMIN_REQUEST);
            });
        } else {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("이미 활성화되어 있어요")
                    .setMessage("기기 관리자 권한이 이미 활성화되어 있습니다.\n\n"
                            + "권한을 해제하려면 [앱 삭제 준비]를 누른 뒤 기기 관리자 설정에서 Pocket Lock을 끄세요.\n\n"
                            + getLegacyBiometricNotice())
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    private String getLegacyBiometricNotice() {
        return "기기 관리자 방식은 Android 보안 정책에 따라 잠금 후 지문·얼굴 대신 "
                + "PIN·패턴·비밀번호를 요구할 수 있습니다. Pocket Lock 설정으로 바꿀 수 없습니다.";
    }

    private void showLegacyLockNotice(Runnable continueAction) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("구형 Android 화면 잠금 안내")
                .setMessage("현재 Android 버전은 기기 관리자 권한으로 화면을 잠급니다.\n\n"
                        + "계속을 누르면 Android의 Pocket Lock 권한 확인 화면이 열립니다.\n\n"
                        + getLegacyBiometricNotice())
                .setNegativeButton("취소", null)
                .setPositiveButton("설정 열기", (dialog, which) -> continueAction.run())
                .show();
    }

    private void requestAccessibilityActivation() {
        if (!ScreenLockManager.hasAccessibilityConsent(this)) {
            startActivityForResult(
                    new Intent(this, AccessibilityConsentActivity.class),
                    ACCESSIBILITY_CONSENT_REQUEST);
            return;
        }
        openAccessibilitySettings();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (android.content.ActivityNotFoundException | SecurityException exception) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (android.content.ActivityNotFoundException | SecurityException fallbackException) {
                showSettingsUnavailableDialog();
            }
        }
    }

    private void showSettingsUnavailableDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("설정을 열 수 없어요")
                .setMessage("기기 설정에서 접근성 메뉴를 열고 Pocket Lock 서비스를 직접 켜 주세요.")
                .setPositiveButton("확인", null)
                .show();
    }

    private void continueAfterAccessibilityConsent() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(ScreenLockManager.ACCESSIBILITY_CONSENT, true)
                .apply();
        openAccessibilitySettings();
        updateUi();
    }

    private void setPocketMode(boolean enabled) {
        if (enabled && !ScreenLockManager.hasPermissionConfigured(this)) {
            pocketSwitch.setChecked(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                requestAccessibilityActivation();
            } else {
                requestAdminAccess();
            }
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
        startUsageGuideRotation();
        updateUi();
    }

    private void startUsageGuideRotation() {
        usageGuideHandler.removeCallbacks(usageGuideMarqueeStarter);
        usageGuideHandler.removeCallbacks(usageGuideMarqueeCompletion);
        usageGuideHandler.removeCallbacks(usageGuideFadeOut);
        usageGuideMessageIndex = ProximityReliabilityManager.get(this) == ProximityReliability.UNKNOWN ? 2 : 0;
        showUsageGuideMessage(usageGuideMessageIndex);
    }

    private void showNextUsageGuideMessage() {
        if (ProximityReliabilityManager.get(this) == ProximityReliability.UNKNOWN) {
            usageGuideMessageIndex = 2;
        } else {
            usageGuideMessageIndex = (usageGuideMessageIndex + 1) % 3;
        }
        showUsageGuideMessage(usageGuideMessageIndex);
    }

    private void showUsageGuideMessage(int messageIndex) {
        String message;
        if (messageIndex == 0) {
            message = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? "앱을 삭제하려면 [앱 삭제 준비]에서 앱 정보 화면을 여세요."
                    : "앱을 삭제 하려면 [앱 삭제 준비]에서 기기 관리자 권한을 해제해 주세요.";
        } else if (messageIndex == 1) {
            message = "잠금이 안 되면 [기기 진단]에서 근접 센서를 테스트해 주세요.";
        } else {
            message = "앱을 사용하기 전에 [내 휴대폰에 맞게 자동 설정]으로 감지 방식을 먼저 설정해 주세요.";
        }

        usageGuideHandler.removeCallbacks(usageGuideMarqueeStarter);
        usageGuideHandler.removeCallbacks(usageGuideMarqueeCompletion);
        usageGuideHandler.removeCallbacks(usageGuideFadeOut);
        usageGuideText.animate().cancel();
        usageGuideText.stopWatchingEndMarker();
        usageGuideText.setSelected(false);
        usageGuideText.setScrollX(0);
        usageGuideText.animate()
                .alpha(0f)
                .setDuration(USAGE_GUIDE_FADE_MILLIS)
                .withEndAction(() -> {
                    usageGuideText.setGuideText(message);
                    usageGuideText.setScrollX(0);
                    usageGuideText.animate()
                            .alpha(1f)
                            .setDuration(USAGE_GUIDE_FADE_MILLIS)
                            .withEndAction(() -> usageGuideHandler.postDelayed(
                                    usageGuideMarqueeStarter, USAGE_GUIDE_PAUSE_MILLIS))
                            .start();
                })
                .start();
    }

    private void startUsageGuideMarquee() {
        usageGuideText.startWatchingEndMarker(usageGuideMarqueeCompletion);
        usageGuideText.setSelected(true);
    }

    private void finishUsageGuideMessage() {
        usageGuideText.stopWatchingEndMarker();
        usageGuideText.setSelected(false);
        usageGuideHandler.removeCallbacks(usageGuideFadeOut);
        usageGuideHandler.postDelayed(usageGuideFadeOut, USAGE_GUIDE_SCROLL_HOLD_MILLIS);
    }

    private void fadeOutUsageGuide() {
        usageGuideText.animate()
                .alpha(0f)
                .setDuration(USAGE_GUIDE_FADE_MILLIS)
                .withEndAction(this::showNextUsageGuideMessage)
                .start();
    }

    private void showUsageGuideDialog() {
        String permissionGuide = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? "Android 9 이상에서는 자동 잠금을 위해 앱 내 안내에 동의하고 접근성 설정에서 "
                        + "Pocket Lock 서비스를 직접 켜야 합니다."
                : "기기 관리자 권한이 자동 화면 잠금에 필요합니다. 잠금 후 생체 인식 대신 "
                        + "PIN·패턴·비밀번호를 요구할 수 있습니다.";
        new android.app.AlertDialog.Builder(this)
                .setTitle("사용 안내")
                .setMessage("처음 사용하신다면 먼저 권한을 설정해 주세요.\n"
                        + permissionGuide + "\n\n"
                        + "[내 휴대폰에 맞게 자동 설정]으로 감지 방식을 확인해 주세요.\n\n"
                        + "자동 잠금이 정상적으로 작동하지 않으면 [기기 진단]에서 근접 센서를 테스트하고 진단 결과를 개발자에게 보내주세요.\n\n"
                        + "앱 삭제와 권한 해제 방법은 [앱 삭제 준비]를 눌러 확인할 수 있습니다.")
                .setPositiveButton("확인", null)
                .show();
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
        detectionModeButton.setText(detectionTestRunning ? "완료하기" : "내 휴대폰에 맞게 자동 설정");
    }

    private void prepareForUninstall() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(ENABLED, false).apply();
        stopService(new Intent(this, PocketLockService.class));

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            openDeviceAdminSettings();
            return;
        }

        showUninstallOptions();
    }

    private void openDeviceAdminSettings() {
        waitingForAdminDeactivation = true;
        Intent adminSettingsIntent = new Intent();
        adminSettingsIntent.setComponent(new ComponentName(
                "com.android.settings",
                "com.android.settings.Settings$DeviceAdminSettingsActivity"));
        try {
            if (adminSettingsIntent.resolveActivity(getPackageManager()) == null) {
                throw new android.content.ActivityNotFoundException();
            }
            startActivity(adminSettingsIntent);
            return;
        } catch (android.content.ActivityNotFoundException | SecurityException exception) {
            // fall through to security settings
        }
        try {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        } catch (android.content.ActivityNotFoundException | SecurityException exception) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (android.content.ActivityNotFoundException | SecurityException fallbackException) {
                waitingForAdminDeactivation = false;
                showSettingsUnavailableDialog();
            }
        }
    }

    private void showUninstallOptions() {
        String message;
        android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Pocket Lock 앱 삭제 안내");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            message = "휴대폰 설정에서 Pocket Lock 앱 정보를 열고 삭제할 수 있습니다.\n\n"
                    + "접근성 서비스가 켜져 있어도 앱 삭제 전에 반드시 끌 필요는 없습니다.";
            if (PocketLockAccessibilityService.isEnabled(this)) {
                message += "\n\n원하면 [접근성 설정]에서 서비스를 직접 끌 수 있습니다.";
                dialog.setNegativeButton("접근성 설정", (ignored, which) ->
                        openAccessibilitySettings());
            }
        } else {
            message = "기기 관리자 권한이 해제되었습니다. 휴대폰 설정의 앱 목록에서 "
                    + "Pocket Lock을 찾아 삭제할 수 있습니다.";
        }
        dialog.setMessage(message)
                .setNeutralButton("확인", null)
                .setPositiveButton("앱 정보 열기", (ignored, which) -> openAppDetails())
                .show();
    }

    private void openAppDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException exception) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("앱 정보를 열 수 없어요")
                    .setMessage("Android 설정의 앱 목록에서 Pocket Lock을 선택해 삭제해 주세요.")
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    private void showAdminDeactivatedConfirmation() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("기기 관리자 권한이 해제되었습니다")
                .setMessage("이제 휴대폰 설정에서 Pocket Lock 앱 정보를 열어 삭제할 수 있습니다.")
                .setNegativeButton("나중에", null)
                .setPositiveButton("앱 정보 열기", (dialog, which) -> openAppDetails())
                .show();
    }

    private void updateUi() {
        boolean adminActive = devicePolicyManager.isAdminActive(adminComponent);
        boolean accessibilityEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && PocketLockAccessibilityService.isEnabled(this);
        boolean permissionConfigured = ScreenLockManager.hasPermissionConfigured(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            adminButton.setText("접근성 설정 열기");
            adminButton.setAlpha(1f);
        } else {
            adminButton.setText("기기 관리자 설정 열기");
            adminButton.setAlpha(adminActive ? 0.45f : 1f);
        }
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false);
        boolean sensitive = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SENSITIVE, false);
        boolean proximityAvailable = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null;
        ProximityReliability proximityReliability = ProximityReliabilityManager.get(this);

        String permissionMessage = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && !ScreenLockManager.hasAccessibilityConsent(this)) {
            permissionMessage = "자동 화면 잠금을 사용하려면 권한 안내를 확인해 주세요.";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !accessibilityEnabled) {
            permissionMessage = "자동 잠금을 사용하려면 접근성 설정에서 Pocket Lock을 켜 주세요.";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && !PocketLockAccessibilityService.isConnected()) {
            permissionMessage = "접근성 서비스 연결을 기다리는 중입니다.";
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !adminActive) {
            permissionMessage = "먼저 기기 관리자 권한을 허용해 주세요.";
        }

        if (permissionMessage != null) {
            statusText.setText(permissionMessage);
        } else if (!proximityAvailable && proximityReliability != ProximityReliability.UNAVAILABLE) {
            ProximityReliabilityManager.set(this, ProximityReliability.UNAVAILABLE);
            proximityReliability = ProximityReliability.UNAVAILABLE;
        }
        if (permissionMessage == null && enabled) {
            if (proximityReliability == ProximityReliability.UNAVAILABLE || proximityReliability == ProximityReliability.UNRELIABLE) {
                statusText.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? "감시중 · 접근성 서비스 사용 중 · 조도·가속도·자세 센서로 확인합니다."
                        : "감시중 · 조도·가속도·자세 센서로 주머니 상태를 확인합니다.");
            } else if (proximityAvailable) {
                statusText.setText(sensitive
                        ? Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                ? "감시중 · 접근성 서비스 사용 중 · 근접 센서가 가려지면 잠급니다."
                                : "감시중 · 근접 센서가 가려지면 화면을 잠급니다."
                        : Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                ? "감시중 · 접근성 서비스 사용 중 · 근접 센서로 확인합니다."
                                : "감시중 · 근접 센서로 주머니 상태를 확인합니다.");
            } else {
                statusText.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? "감시중 · 접근성 서비스 사용 중 · 조도·가속도·자세 센서로 확인합니다."
                        : "감시중 · 조도·가속도·자세 센서로 주머니 상태를 확인합니다.");
            }
        } else if (permissionMessage == null) {
            statusText.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? "접근성 서비스 사용 중 · 주머니 잠금을 켜면 자동 잠금합니다."
                    : "준비됨 · 주머니 잠금 켜기를 활성화해 주세요.");
        }
        pocketSwitch.setChecked(permissionConfigured && enabled);
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
        activityResumed = true;
        accessibilityStatusRefreshAttempts = 0;
        usageGuideHandler.removeCallbacks(accessibilityStatusRefresh);
        startUsageGuideRotation();
        if (detectionTestRunning) {
            registerDetectionSensor();
        }
        updateUi();
        if (waitingForAdminDeactivation && !devicePolicyManager.isAdminActive(adminComponent)) {
            waitingForAdminDeactivation = false;
            showAdminDeactivatedConfirmation();
        } else if (waitingForAdminDeactivation) {
            waitingForAdminDeactivation = false;
            new android.app.AlertDialog.Builder(this)
                    .setTitle("기기 관리자 권한이 아직 활성화되어 있어요")
                    .setMessage("앱을 삭제하려면 기기 관리자 설정에서 Pocket Lock을 선택해 권한을 해제해 주세요.")
                    .setNegativeButton("확인", null)
                    .setPositiveButton("설정 다시 열기", (dialog, which) -> {
                        openDeviceAdminSettings();
                    })
                    .show();
        }
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false);
        boolean permissionConfigured = ScreenLockManager.hasPermissionConfigured(this);
        if (permissionConfigured && enabled) {
            Intent serviceIntent = new Intent(this, PocketLockService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            stopService(new Intent(this, PocketLockService.class));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            usageGuideHandler.postDelayed(accessibilityStatusRefresh, 500L);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACCESSIBILITY_CONSENT_REQUEST) {
            if (resultCode == RESULT_OK) {
                continueAfterAccessibilityConsent();
            } else {
                updateUi();
            }
        } else if (requestCode == ADMIN_REQUEST) {
            updateUi();
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        usageGuideHandler.removeCallbacks(accessibilityStatusRefresh);
        usageGuideHandler.removeCallbacks(usageGuideMarqueeStarter);
        usageGuideHandler.removeCallbacks(usageGuideMarqueeCompletion);
        usageGuideHandler.removeCallbacks(usageGuideFadeOut);
        usageGuideText.animate().cancel();
        usageGuideText.setSelected(false);
        unregisterDetectionSensor();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        usageGuideHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
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
