# 개발·운영 및 개선 가이드

이 문서는 Pocket Lock을 수정하고, 기기별 동작을 확인하고, 릴리스하는 절차를 정리합니다. 현재 앱 버전은 `1.0.5` (`versionCode` 26)입니다.

## 주요 코드 위치

| 작업 | 파일 | 담당 내용 |
| --- | --- | --- |
| 메인 화면, 설정, 서비스 시작/중지, 감지 방식 테스트 | [MainActivity.java](../app/src/main/java/com/innoshiftconsult/pocketlock/MainActivity.java), [activity_main.xml](../app/src/main/res/layout/activity_main.xml) | Device Admin 안내, 민감도 저장, `판단하기`, 상태 표시 |
| 센서 구독, 감지 방식 선택, 실제 화면 잠금 | [PocketLockService.java](../app/src/main/java/com/innoshiftconsult/pocketlock/PocketLockService.java) | 센서 listener 수명, 서비스 잠금 확인, `lockNow()` |
| 복합 센서 판단 기준 | [PocketSensorFusionDetector.java](../app/src/main/java/com/innoshiftconsult/pocketlock/PocketSensorFusionDetector.java) | 조도·가속도·자세 융합, 확인 시간, 조도 센서 없는 기기의 대체 경로 |
| 근접 센서 신뢰도와 모드 | [ProximityReliabilityManager.java](../app/src/main/java/com/innoshiftconsult/pocketlock/ProximityReliabilityManager.java), [ProximityReliability.java](../app/src/main/java/com/innoshiftconsult/pocketlock/ProximityReliability.java), [ProximityDetectionMode.java](../app/src/main/java/com/innoshiftconsult/pocketlock/ProximityDetectionMode.java) | 저장된 신뢰도와 근접/복합 감지 방식 연결 |
| 기기·센서 진단과 결과 복사 | [DiagnosticsActivity.java](../app/src/main/java/com/innoshiftconsult/pocketlock/DiagnosticsActivity.java), [activity_diagnostics.xml](../app/src/main/res/layout/activity_diagnostics.xml) | 센서 정보, 수동 테스트, 이벤트 로그, 잠금 테스트, 클립보드 복사 |
| 앱 표시 문구와 버전 | [strings.xml](../app/src/main/res/values/strings.xml), [app/build.gradle.kts](../app/build.gradle.kts) | 앱 표시 버전, Device Admin 설명, 빌드 버전 |
| 사용자·개인정보 안내 | [README.md](../README.md), [privacy-policy.html](privacy-policy.html) | 사용법, 제한사항, 데이터 처리 안내 |

근접 센서 판단 코드를 바꿀 때는 복합 센서 감지기만으로 충분한지 먼저 확인하세요. 근접 센서 경로와 복합 센서 경로는 같은 서비스 안에서 분기되므로, 한 경로의 변경이 다른 경로까지 바꾸지 않도록 검증합니다.

## 감지 방식과 조정 지점

앱을 처음 설정할 때 메인 화면의 `판단하기`를 실행해 근접 센서를 가렸다가 노출합니다. `NEAR`와 `FAR` 상태 변화가 각각 2회 이상 확인되면 근접 센서를 신뢰 가능한 것으로 기록합니다. 센서가 없으면 `UNAVAILABLE`, 센서가 있지만 정상 변화가 확인되지 않으면 `UNRELIABLE`로 기록하고 복합 센서 방식을 사용합니다. 진단 화면의 근접 센서 테스트도 신뢰도 기록을 갱신합니다.

근접 센서 방식의 현재 동작은 `PocketLockService`에 있습니다.

- 보통 모드는 센서값이 `min(maximumRange, 1 cm)`보다 작으면 가림으로 판단하고, 기기가 뒤집힌 자세일 때 잠금을 허용합니다.
- 민감 모드는 센서 최대 범위를 가림 기준으로 사용하고, 방향 제한 없이 잠금을 허용합니다. 사용 중인 화면도 닫힐 수 있습니다.
- 잠금 요청 전 추가 확인 시간은 보통 500ms, 민감 모드 300ms입니다.

복합 센서 방식의 현재 기준은 `PocketSensorFusionDetector`에 있습니다.

- 조도 센서가 있으면 조도가 30 lux 이하로 내려갔는지, 직전 6초 구간의 밝은 기준값에서 충분히 감소했는지, 최근 움직임과 세로 방향 자세가 함께 있는지 판단합니다.
- 조도 센서가 없으면 먼저 평상시 자세를 관찰한 뒤, Y축이 -7 이하로 바뀌고 가속도 샘플 간 변화가 1.5를 넘었는지 확인합니다. 확정된 상태는 평상시 자세로 돌아올 때까지 유지합니다.
- 복합 센서 후보는 600ms 확인을 거친 뒤 서비스의 추가 잠금 확인을 통과해야 `lockNow()`를 호출합니다.

임계값을 바꿀 때는 실제 진단 로그의 기기별 센서 단위와 샘플 주기를 확인하고, 임계값 경계·센서 미지원·잡음·자세 유지·자세 복귀 사례를 테스트에 포함합니다. 상수만 조정하고 끝내지 말고 아래 단위 테스트와 실제 기기 검증을 함께 갱신합니다.

## 수정 및 검증 절차

1. 기능이 메인 화면, 서비스, 감지기, 진단 중 어느 계층에 속하는지 위 표에서 확인하고 해당 책임에만 변경을 둡니다.
2. 감지 로직 변경은 [ProximityReliabilityManagerTest.java](../app/src/test/java/com/innoshiftconsult/pocketlock/ProximityReliabilityManagerTest.java)에 회귀 테스트를 추가합니다. 테스트는 센서 이벤트를 재현 가능한 타임스탬프로 입력하고 성공뿐 아니라 오탐 방지와 감지 해제도 확인합니다.
3. 단위 테스트와 앱 빌드를 실행합니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

테스트 실행이 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`으로 실패하면 assertion 실행 전 Gradle 테스트 worker 시작에 실패한 것입니다. 테스트 코드 컴파일 성공을 단위 테스트 통과로 간주하지 말고, Gradle/JDK 실행 환경을 점검한 뒤 테스트를 다시 실행합니다.

## 실제 기기 운영·진단

1. 앱에서 Device Admin을 활성화하고 `판단하기`를 실행합니다. 근접 센서가 안정적으로 반응하지 않는 기기는 복합 센서 방식인지 확인합니다.
2. 복합 센서 방식에서는 실제 사용 자세로 휴대폰을 주머니에 넣고, 조도 센서가 없는 경우에도 가속도계가 있는지 확인합니다. 가속도계가 없으면 이 대체 방식으로 주머니 상태를 판단할 수 없습니다.
3. 여러 차례 반복하고, 화면 잠금 실패가 감지 문제인지 권한 문제인지 나눠 봅니다. 진단 화면의 `화면 잠금 테스트`는 감지 알고리즘을 거치지 않고 Device Admin과 `lockNow()`만 확인합니다.
4. `센서 테스트 시작`으로 센서 이벤트를 기록하고 종료 후 센서 이름, 이벤트 횟수, 현재값과 로그를 확인합니다. 로그는 메모리에서 최대 200개 항목을 유지합니다.

`진단 결과 복사`에는 제조사·모델·Android/SDK 버전, 센서 정보와 이벤트 로그가 포함되어 시스템 클립보드로 복사됩니다. 전화번호, IMEI, Android ID, 계정, 위치 등은 포함하지 않지만 기기 모델과 센서 조합은 기기를 식별하는 단서가 될 수 있으므로 사용자가 공유 대상을 확인하도록 안내합니다. 개인정보 처리 방식이 바뀌면 [개인정보처리방침](privacy-policy.html)과 시행일도 함께 갱신합니다.

## 버전 및 릴리스

앱을 배포할 때 아래 버전 표기를 서로 맞춥니다.

- `app/build.gradle.kts`: `versionCode`를 이전 값보다 1 올리고 `versionName`의 마지막 숫자를 1 올립니다.
- `app/src/main/res/values/strings.xml`: `app_version`을 같은 `versionName`으로 갱신합니다.
- `README.md`: 프로젝트 정보의 앱 버전을 갱신합니다.
- 개인정보 처리 관행에 변경이 있는 릴리스는 `docs/privacy-policy.html`의 내용을 검토하고 시행일을 갱신합니다.

디버그 APK 생성:

```powershell
.\gradlew.bat :app:assembleDebug
```

산출물: `app/build/outputs/apk/debug/app-debug.apk`

Play Console용 릴리스 AAB 생성:

1. `keystore.properties.example`을 참고해 저장소 루트에 로컬 `keystore.properties`를 만듭니다. `storeFile`은 키스토어 파일의 절대 경로를 권장합니다.
2. 실제 키스토어와 비밀번호를 안전한 비밀 관리 위치에 보관합니다. `keystore.properties`와 `*.jks`/`*.keystore`는 `.gitignore`에 포함되어 있으므로 저장소에 추가하거나 로그에 출력하지 않습니다. 업로드 키는 별도로 백업합니다.
3. 릴리스 번들을 빌드합니다.

```powershell
.\gradlew.bat :app:bundleRelease
```

산출물: `app/build/outputs/bundle/release/app-release.aab`

현재 저장소는 Gradle Wrapper 9.5.0, Android Gradle Plugin 9.3.2, compile/target SDK 36을 사용합니다. Gradle 실행에는 JDK 17 이상이 필요합니다. 현재 작업 사본의 `gradle/gradle-daemon-jvm.properties`는 JDK 25를 지정하지만 이 파일은 `.gitignore` 대상이므로 다른 개발 환경에 포함된다고 가정하지 말고 `.\gradlew.bat --version`으로 실제 JVM을 확인합니다. Android SDK 경로는 로컬 `local.properties`에 설정하며 이 파일도 커밋하지 않습니다.

## 앱 아이콘 및 개인정보 페이지

Android 런처 아이콘 원본은 `app/src/main/res/drawable/pocket_lock_icon.png`입니다. 개인정보 페이지는 GitHub Pages에서 `docs` 폴더를 게시하므로 아이콘을 변경할 때 `docs/pocket_lock_icon.png`도 같은 이미지로 교체하고 상대 경로를 확인합니다. 개인정보 페이지의 공개 URL은 `MainActivity.java`의 `PRIVACY_POLICY_URL`과 일치해야 합니다.