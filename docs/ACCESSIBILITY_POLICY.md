# Pocket Lock - Google Play AccessibilityService API Submission Notes

This document describes the implementation in Pocket Lock 1.2.0 (`versionCode` 32) and prepares the information needed for a Google Play AccessibilityService API declaration. It is not a guarantee of Play approval. Review the current [Google Play AccessibilityService API policy](https://support.google.com/googleplay/android-developer/answer/10964491) before submitting.

## 1. API use and core app function

Pocket Lock detects when the phone is placed in a pocket using the existing proximity or sensor-fusion logic. On Android 9 (API 28) and later, after the user has reviewed and accepted the in-app disclosure and manually enabled the service in Android Settings, the app calls:

```java
performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
```

The service performs only the deterministic action of requesting a screen lock after Pocket Lock's existing pocket-detection condition is met. It does not automate other apps or make decisions based on screen contents.

On Android 8.1 (API 27) and earlier, the existing Device Admin `DevicePolicyManager.lockNow()` path remains in use and AccessibilityService is not enabled.

## 2. Why AccessibilityService is used

On Android 9 and later, Pocket Lock uses the system lock-screen global action to lock the device after pocket detection. This is the app's core automatic-lock feature. The app does not fall back to Device Admin when the accessibility service is unavailable, disconnected, or rejects a lock request.

## 3. API scope and data handling

The service configuration and implementation were checked:

- `accessibilityEventTypes` is not configured; the service does not subscribe to accessibility events.
- `onAccessibilityEvent()` does not inspect or retain an event.
- `canRetrieveWindowContent` is `false`; no window hierarchy or text is read.
- Gesture injection, touch exploration, key-event filtering, and fingerprint gesture requests are disabled.
- Accessibility screenshot capability is explicitly `false` for API 33+ and is not requested on earlier versions.
- `isAccessibilityTool` is explicitly `false` for API 31+; Pocket Lock is not primarily an assistive-technology app.
- No accessibility-derived data is persisted, uploaded, shared, or included in diagnostics.
- The service contains no network calls.

The separate diagnostics feature can copy device model, Android version, sensor metadata, sensor values, and sensor-event logs to the system clipboard when the user asks. That diagnostic flow is independent of AccessibilityService and is described in the app's [privacy policy](./privacy-policy.html).

## 4. User enablement and disablement

1. The user taps **접근성 설정 열기** on Android 9+.
2. On the first request, Pocket Lock presents a separate in-app disclosure titled **Pocket Lock 접근성 서비스 사용 안내**.
3. The user may tap **동의하고 설정 열기** or **취소**. Consent is stored locally only after the affirmative action. Cancel returns without opening Settings.
4. After consent, Android's official `Settings.ACTION_ACCESSIBILITY_SETTINGS` screen opens. If it cannot be opened, Pocket Lock attempts the general Android Settings screen and explains the manual path if that also cannot be opened.
5. The user locates Pocket Lock and enables it themselves. Menu labels vary by device; for example, Samsung devices may group services under **설치된 앱**, while other devices may use **다운로드한 앱** or **설치된 서비스**.
6. When the user returns to Pocket Lock, the app rechecks enabled and connected status and refreshes the UI. Consent, enabled state, and actual service connection are treated as separate states.
7. The user can disable the service at any time in Android Accessibility Settings. Pocket Lock does not automate Settings or enable/disable the service on the user's behalf.

On Android 8.1 and earlier, the service is disabled in the manifest and no accessibility consent or setting is requested.

## 5. App uninstall and legacy Device Admin

The **앱 삭제 준비** button first disables Pocket Lock's monitoring setting and stops the sensor service.

- If Device Admin is still active (including an upgraded installation on Android 9+), Pocket Lock opens the Device admin apps screen directly (falling back to Android Security Settings), then checks the state again when the user returns. It reports whether deactivation succeeded and offers a retry if it is still active.
- Once Device Admin is inactive, the user can open the app-info screen and uninstall Pocket Lock.
- On Android 9+, AccessibilityService does not have to be disabled before uninstalling. Pocket Lock may offer the Accessibility Settings screen as an optional destination.
- On Android 8.1 and earlier, an active Device Admin must be deactivated before Android permits uninstall.

No permission is revoked automatically, and AccessibilityService is never used to manipulate the uninstall flow.

## 6. Suggested Play Console declaration text

### Korean

Pocket Lock은 사용자가 설정한 주머니 감지 조건이 충족되면 휴대폰 화면을 자동으로 잠그는 앱입니다. Android 9 이상에서 화면 잠금 기능을 실행하기 위해 AccessibilityService의 `GLOBAL_ACTION_LOCK_SCREEN` 동작만 사용합니다. 접근성 이벤트를 구독하거나 화면 내용·텍스트·입력 정보를 읽지 않습니다. 제스처, 키 입력, 화면 캡처 기능을 사용하지 않으며 접근성 관련 데이터를 수집·저장·전송·공유하지 않습니다. 사용자는 앱 내 별도 안내에 동의한 뒤 Android 설정에서 서비스를 직접 활성화하며 언제든지 해제할 수 있습니다.

### English

Pocket Lock detects when a phone is placed in a pocket and automatically locks the screen when the user's configured pocket-detection condition is met. On Android 9 and later, the app uses only the AccessibilityService `GLOBAL_ACTION_LOCK_SCREEN` action to invoke the system screen lock. It does not subscribe to accessibility events or read screen content, text, or user input. It does not use gestures, key-event monitoring, or screenshot capabilities, and it does not collect, store, transmit, or share AccessibilityService data. Users review a separate in-app disclosure and affirmatively consent before they are sent to Android Settings to enable the service themselves. They can disable it at any time.

Use only statements that remain accurate for the exact submitted build and declaration form. Google Play may request further information or reject the declaration.

## 7. Review video storyboard

Record the following on a supported Android 9+ device. Include captions or narration so the limited use of AccessibilityService is clear.

1. Launch Pocket Lock and show the main screen.
2. Tap **접근성 설정 열기** and show the full, scrollable in-app disclosure.
3. Show the **취소** path: return to the app without opening Android Settings, then tap the button again to show that the user can restart the flow.
4. Show **동의하고 설정 열기**, then the official Android Accessibility Settings screen.
5. Manually locate Pocket Lock and enable it; do not automate the Settings interaction.
6. Return to Pocket Lock and show the enabled/connected state.
7. Demonstrate pocket detection followed by the screen-lock action.
8. Reopen Android Accessibility Settings and manually turn the service off, then return and show the updated status.
9. If legacy Device Admin is active on the test installation, show **앱 삭제 준비**, the Device Admin setting, the returned-state check, and app-info/uninstall guidance. Do not uninstall the recording device unless desired.

## 8. Pre-submission checklist

- Verify the disclosure, consent, refusal, enablement, and disablement flows on at least one Samsung Galaxy and one Google Pixel device if available.
- Verify the accessibility service does not receive event callbacks and cannot retrieve window content.
- Verify actual lock and biometric unlock behavior on target test devices; manufacturer and Android security policies can differ.
- Complete the current Play Console AccessibilityService declaration and provide the review video.
- Confirm the declaration, app listing, privacy policy, and Data safety answers match the exact build.
- Do not claim that Google Play approval, biometric unlock, or the absence of OEM-specific screen-capture behavior is guaranteed.

## 9. Relevant implementation files

- [AndroidManifest.xml](../app/src/main/AndroidManifest.xml)
- [PocketLockAccessibilityService.java](../app/src/main/java/com/innoshiftconsult/pocketlock/PocketLockAccessibilityService.java)
- [ScreenLockManager.java](../app/src/main/java/com/innoshiftconsult/pocketlock/ScreenLockManager.java)
- [AccessibilityConsentActivity.java](../app/src/main/java/com/innoshiftconsult/pocketlock/AccessibilityConsentActivity.java)
- [accessibility_service_config.xml](../app/src/main/res/xml/accessibility_service_config.xml)
- [activity_accessibility_consent.xml](../app/src/main/res/layout/activity_accessibility_consent.xml)
