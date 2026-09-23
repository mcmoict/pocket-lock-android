# Pocket Lock

휴대폰이 세로로 뒤집힌 상태에서 근접 센서가 가려지면 Android의 시스템 잠금을 요청해 주머니 속 오작동을 줄이는 Android 앱입니다. 세로 방향은 약간 기울어진 상태까지 허용합니다.

## 현재 기능

- 기본 모드에서는 휴대폰이 세로로 뒤집힌 상태에서 근접 센서가 0.5초 이상 연속으로 가려지면 화면을 잠금
- 민감 모드에서는 방향과 관계없이 근접 센서가 0.3초 이상 연속으로 가려지면 화면을 잠금
- 근접 센서 민감도를 `보통`과 `민감` 중에서 선택
- `민감` 선택 시 사용 중인 화면이 근접 센서에 의해 닫힐 수 있다는 확인 메시지 표시
- 같은 가림 상태에서는 중복 잠금을 방지
- 포그라운드 서비스로 백그라운드에서 센서 감시
- Android 기기 관리자 앱으로 등록되어 `lockNow()` 사용
- `기기 진단` 화면에서 기기 관리자, 센서 및 화면 잠금 상태를 확인
- 진단 화면의 `화면 잠금 테스트` 버튼으로 `lockNow()` 동작을 별도로 확인
- 진단 결과를 클립보드에 복사
- Pocket Lock 전용 런처 아이콘 제공
- 앱 화면의 `앱 삭제 준비` 버튼으로 기기 관리자 앱 설정 화면으로 이동

## 기기 진단

메인 화면에서 `기기 진단`을 누르면 진단 화면으로 이동합니다. 진단 화면의 뒤로가기 버튼으로 메인 화면으로 돌아올 수 있습니다.

다음 정보를 표 형태로 확인할 수 있습니다.

- 기기 정보: 제조사, 모델, Android 버전, SDK 버전
- 기기 관리자: 현재 Device Admin 권한 활성화 여부
- 근접 센서: 센서 존재 여부, 이벤트 수신 여부, 이름, 제조사, 최대 범위, 해상도, 전력, 현재값, 이벤트 횟수, 최근 이벤트 시각, `StringType`, Wake-up 여부, Reporting Mode
- 가속도 센서: 센서 존재 여부, 이름, X/Y/Z 현재값, 이벤트 횟수, 최근 이벤트 시각
- 조도 센서: 센서 존재 여부, 이름, 현재 조도값, 이벤트 횟수, 최근 이벤트 시각

근접 센서가 존재하면 센서 값을 실시간으로 표시하고, 현재값이 센서의 `maximumRange`보다 작은 경우 가림으로 표시합니다. 센서가 없거나 센서 이벤트가 전달되지 않는 경우에도 앱이 종료되지 않고 `감지되지 않음` 또는 `이벤트 없음` 상태를 표시합니다.

진단 화면의 `화면 잠금 테스트`는 기존 주머니 잠금 알고리즘을 거치지 않고 현재 앱의 Device Admin 권한을 확인한 뒤 `DevicePolicyManager.lockNow()`를 직접 호출합니다. 따라서 다음 문제를 구분하는 데 사용할 수 있습니다.

1. Device Admin 권한이 정상인지
2. 근접 센서가 존재하는지
3. 근접 센서 이벤트가 실제로 전달되는지
4. 가속도 센서 이벤트가 전달되는지
5. Android의 화면 잠금 요청이 동작하는지

`진단 결과 복사`를 누르면 제조사, 모델, Android/SDK 버전, 센서 정보와 이벤트 상태가 클립보드에 복사됩니다. 전화번호, IMEI, Android ID, 광고 ID, 계정, 이메일, 위치, Wi-Fi, MAC 주소, IP 주소 등 개인정보나 식별 정보는 포함하지 않습니다.

센서 Listener는 진단 화면이 표시되는 동안에만 등록됩니다. 화면을 벗어나면 `onPause()`에서 해제되어 백그라운드에서 진단 센서를 계속 실행하지 않습니다.

Galaxy A33 5G(`SM-A336N`) 또는 Galaxy Note20 5G(`SM-N981N`)에서 문제가 발생하면 진단 결과의 근접 센서 이름, 제조사, `StringType`, 이벤트 횟수와 현재값, 가속도 이벤트 횟수, Device Admin 상태 및 화면 잠금 테스트 결과를 함께 확인합니다. 진단 기능은 원인 확인을 위한 기능이며, 기존 주머니 잠금 알고리즘의 호환성 문제를 자동으로 수정하지는 않습니다.

## 필요한 권한

근접 센서 자체에는 기기 관리자 권한이 필요하지 않습니다. 센서가 가려졌을 때 Android 시스템 화면을 잠그는 `DevicePolicyManager.lockNow()` 호출에 기기 관리자 권한이 필요합니다.

앱을 처음 사용할 때 다음 순서로 권한을 허용합니다.

1. 앱을 설치하고 실행합니다.
2. `기기 관리자 권한 허용`을 누릅니다.
3. Android 시스템 화면에서 Pocket Lock을 활성화합니다.
4. 앱으로 돌아와 `주머니 잠금 켜기`를 활성화합니다.

Android 13 이상에서 알림 권한을 요청하면 허용해야 포그라운드 서비스 알림을 정상적으로 표시할 수 있습니다.

## 사용 방법

1. 화면이 켜진 상태에서 휴대폰을 주머니에 넣습니다.
2. 기본 모드에서는 휴대폰을 세로로 뒤집어 주머니에 넣고 근접 센서가 가려지면 약 0.5초 후 화면이 잠깁니다.
3. 민감 모드에서는 방향과 관계없이 근접 센서가 가려지면 약 0.3초 후 화면이 잠깁니다.
4. PIN, 패턴, 비밀번호 또는 생체 인증 등 휴대폰의 기본 방식으로 잠금을 해제합니다.
5. 센서가 다시 노출되면 다음 가림을 감지할 수 있습니다.

화면 잠금 자체만 테스트하려면 메인 화면의 `기기 진단` → `화면 잠금 테스트`를 사용합니다. Device Admin 권한이 없으면 `기기 관리자 권한이 필요합니다.`라는 안내가 표시됩니다.

## 빌드 및 설치

### Android Studio

1. Android Studio에서 이 폴더를 엽니다.
2. Gradle Sync가 완료될 때까지 기다립니다.
3. USB 디버깅을 활성화한 Android 휴대폰을 연결합니다.
4. 휴대폰에서 USB 디버깅 허용을 승인합니다.
5. 상단 기기 목록에서 휴대폰을 선택하고 Run을 누릅니다.

### VS Code 터미널

JDK 17과 Android SDK가 설치되어 있고 `local.properties`가 올바른 SDK 경로를 가리키는지 확인합니다.

```powershell
$env:JAVA_HOME = "C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.8-hotspot"
.\gradlew.bat :app:assembleDebug
```

생성된 APK는 다음 위치에 있습니다.

```text
app/build/outputs/apk/debug/app-debug.apk
```

ADB로 연결된 휴대폰에 직접 설치할 수도 있습니다.

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## Google Play 출시용 AAB

Play Console에 업로드하려면 앱 번들을 릴리스 키로 서명해야 합니다. 업로드 키는 한 번 생성한 뒤 안전하게 백업하고 저장소에는 올리지 않습니다.

```powershell
keytool -genkeypair -v -keystore pocket-lock-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pocket-lock-upload
```

프로젝트 루트의 `keystore.properties`에 다음 값을 입력합니다. `storeFile`은 키 파일의 절대 경로를 사용합니다.

```properties
storeFile=C:/secure/path/pocket-lock-upload.jks
storePassword=KEYSTORE_PASSWORD
keyAlias=pocket-lock-upload
keyPassword=KEY_PASSWORD
```

그 다음 릴리스 번들을 생성합니다.

```powershell
.\gradlew.bat :app:bundleRelease
```

생성 파일은 `app/build/outputs/bundle/release/app-release.aab`입니다.

휴대폰이 `adb devices`에 표시되지 않으면 USB 디버깅, USB 케이블, 연결 모드 및 제조사 USB 드라이버를 확인합니다.

## 앱 삭제

기기 관리자 앱이 활성화된 동안에는 Android가 앱 삭제를 차단합니다.

1. Pocket Lock에서 `앱 삭제 준비`를 누릅니다.
2. 열리는 `기기 관리자 앱` 화면에서 Pocket Lock을 끕니다.
3. 설정의 앱 목록에서 Pocket Lock을 삭제합니다.

## Android 보안상 제한

- 근접 센서가 없는 기기에서는 동작하지 않습니다.
- 앱이 화면을 잠근 뒤에는 Android의 PIN, 패턴, 비밀번호 또는 생체 인증이 필요합니다.
- `lockNow()`가 실제 잠금 화면을 표시하려면 휴대폰 설정에서 PIN, 패턴 또는 비밀번호를 먼저 설정해야 합니다.
- 일반 앱은 볼륨 키 두 번 같은 방식으로 시스템 잠금을 해제할 수 없습니다.
- 화면이 켜진 채로 주머니 속 터치만 전역 차단하는 기능은 일반 앱 권한으로 보장할 수 없습니다.
- 카카오톡 등 알림이 오면 Android 설정에 따라 잠금 화면이 잠시 켜질 수 있습니다. 일반 앱은 다른 앱의 알림이 화면을 깨우는 것을 강제로 차단할 수 없으므로, 완전히 어둡게 유지하려면 휴대폰 설정에서 잠금 화면 알림 표시와 알림에 의한 화면 켜기를 끄거나 방해 금지 모드를 사용해야 합니다.
- `두 번 탭하여 화면 켜기`, `들어서 화면 켜기`, 알림에 의한 화면 켜기, Smart Lock은 휴대폰 설정에 따라 별도로 조정해야 합니다.
- 런처 아이콘은 앱 목록에 등록됩니다. Android는 일반 앱 설치 시 홈 화면에 아이콘을 자동 배치하는 것을 보장하지 않으므로, 필요하면 앱 목록에서 아이콘을 홈 화면으로 추가합니다.

## 저장소 및 프로젝트 정보

- Android 패키지 이름: `com.innoshiftconsult.pocketlock`
- 최소 Android 버전: API 26
- 컴파일 및 대상 SDK: API 36
- Android Gradle Plugin: `9.3.2`
- Gradle Wrapper: `9.5.0`
- GitHub: https://github.com/mcmoict/pocket-lock-android
