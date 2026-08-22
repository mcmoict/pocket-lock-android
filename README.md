# Pocket Lock

근접 센서가 가려지면 Android의 시스템 잠금을 요청해 주머니 속 오작동을 줄이는 Android 앱입니다.

## 현재 기능

- 근접 센서가 가려지면 화면을 즉시 잠금
- 같은 가림 상태에서는 중복 잠금을 방지
- 포그라운드 서비스로 백그라운드에서 센서 감시
- Android 기기 관리자 앱으로 등록되어 `lockNow()` 사용
- Pocket Lock 전용 런처 아이콘 제공
- 앱 화면의 `앱 삭제 준비` 버튼으로 기기 관리자 앱 설정 화면으로 이동

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
2. 근접 센서가 가려지면 화면이 잠깁니다.
3. PIN, 패턴, 비밀번호 또는 생체 인증 등 휴대폰의 기본 방식으로 잠금을 해제합니다.
4. 센서가 다시 노출되면 다음 가림을 감지할 수 있습니다.

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

휴대폰이 `adb devices`에 표시되지 않으면 USB 디버깅, USB 케이블, 연결 모드 및 제조사 USB 드라이버를 확인합니다.

## 앱 삭제

기기 관리자 앱이 활성화된 동안에는 Android가 앱 삭제를 차단합니다.

1. Pocket Lock에서 `앱 삭제 준비`를 누릅니다.
2. 열리는 `기기 관리자 앱` 화면에서 Pocket Lock을 끕니다.
3. 설정의 앱 목록에서 Pocket Lock을 삭제합니다.

## Android 보안상 제한

- 근접 센서가 없는 기기에서는 동작하지 않습니다.
- 앱이 화면을 잠근 뒤에는 Android의 PIN, 패턴, 비밀번호 또는 생체 인증이 필요합니다.
- 일반 앱은 볼륨 키 두 번 같은 방식으로 시스템 잠금을 해제할 수 없습니다.
- 화면이 켜진 채로 주머니 속 터치만 전역 차단하는 기능은 일반 앱 권한으로 보장할 수 없습니다.
- 카카오톡 등 알림이 오면 잠금 화면이 잠시 켜질 수 있지만, 알림만으로 잠금이 해제되지는 않습니다.
- `두 번 탭하여 화면 켜기`, `들어서 화면 켜기`, 알림에 의한 화면 켜기, Smart Lock은 휴대폰 설정에 따라 별도로 조정해야 합니다.
- 런처 아이콘은 앱 목록에 등록됩니다. Android는 일반 앱 설치 시 홈 화면에 아이콘을 자동 배치하는 것을 보장하지 않으므로, 필요하면 앱 목록에서 아이콘을 홈 화면으로 추가합니다.

## 저장소 및 프로젝트 정보

- 프로젝트 식별자: `innoshift-consulting`
- Android 패키지 이름: `com.innoshift.consulting.pocketlock`
- 최소 Android 버전: API 26
- 컴파일 및 대상 SDK: API 35
- Android Gradle Plugin: `8.13.2`
- Gradle Wrapper: `9.3.0`
- GitHub: https://github.com/mcmoict/pocket-lock-android
