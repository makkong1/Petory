# 모바일 앱 (Capacitor) 가이드

## 개요

기존 React 19 웹 앱을 **Capacitor**로 감싸 Android / iOS 네이티브 앱으로 빌드한다.  
웹 앱은 그대로 유지되며, 같은 코드베이스에서 3개 타겟(웹, Android APK, iOS IPA)을 빌드한다.

```
브라우저          → React 앱 (웹, 기존 그대로)
Android 앱        → WebView 안에 React 앱 실행
iOS 앱            → WKWebView 안에 React 앱 실행
```

---

## 아키텍처

```
frontend/src/          ← React 코드 (공유)
frontend/build/        ← npm run build 결과물
       ↓ npx cap sync
android/               ← Android 네이티브 프로젝트 (Gradle)
ios/                   ← iOS 네이티브 프로젝트 (Xcode)
capacitor.config.ts    ← Capacitor 공통 설정
```

---

## 환경 설정 파일 (민감 정보 — gitignore)

| 파일                            | 용도                      | 위치                      |
| ------------------------------- | ------------------------- | ------------------------- |
| `firebase-service-account.json` | 백엔드 FCM 발송 인증      | `backend/main/resources/` |
| `google-services.json`          | Android Firebase 연동     | `android/app/`            |
| `GoogleService-Info.plist`      | iOS Firebase 연동         | `ios/App/App/`            |
| `frontend/.env.capacitor`       | Capacitor 빌드용 환경변수 | `frontend/`               |

> **절대 git에 커밋하면 안 됨.** 모두 `.gitignore`에 등록되어 있음.

---

## FCM 푸시 알림 동작 방식

```
사용자 로그인
    └→ initPushNotifications() (App.js)
           └→ PushNotifications.requestPermissions()
                  └→ 기기 FCM 토큰 발급
                         └→ POST /api/fcm/token (백엔드 저장)

알림 이벤트 발생 (댓글, 케어 요청 등)
    └→ NotificationService.createNotification()
           ├→ SSE 발송 (앱 열려있을 때)
           └→ FcmService.sendToUser() (앱 꺼져있을 때)
                  └→ Firebase → Android/iOS 기기에 푸시
```

### 관련 코드

| 역할               | 파일                                                     |
| ------------------ | -------------------------------------------------------- |
| Firebase 초기화    | `global/config/FirebaseConfig.java`                      |
| FCM 토큰 저장/발송 | `domain/notification/service/FcmService.java`            |
| 알림 생성 훅       | `domain/notification/service/NotificationService.java`   |
| 토큰 등록 API      | `domain/notification/controller/FcmTokenController.java` |
| 프론트 FCM 초기화  | `frontend/src/api/pushNotifications.js`                  |
| iOS AppDelegate    | `ios/App/App/AppDelegate.swift`                          |

### API 엔드포인트

```
POST   /api/fcm/token   { token: string, deviceType: "ANDROID"|"IOS" }
DELETE /api/fcm/token   { token: string }
```

---

## 개발 워크플로우

### 코드 수정 후 앱 반영

루트 `package.json` 에 정의된 npm 스크립트를 권장 (내부적으로 `cd frontend && npx cap ...` 실행 → cwd 혼동 방지).

```bash
# 모두 프로젝트 루트(Petory/)에서 실행

# Android: React 빌드 + sync
npm run build:android
npm run cap:open:android   # Android Studio 열기

# iOS: React 빌드 + sync
npm run build:ios
npm run cap:open:ios       # Xcode 열기 (App.xcworkspace)
```

> `npx cap ...` 직접 실행은 반드시 `frontend/` 디렉토리에서. `Petory/` 루트에서 실행하면 `frontend/frontend/...` 로 경로가 꼬임.

### Android 실행

```bash
npm run cap:open:android
# Android Studio 열림 → ▶ Run
```

### iOS 실행

```bash
npm run build:ios          # React 빌드 + cap sync (CocoaPods 포함)
npm run cap:open:ios       # Xcode 가 App.xcworkspace 를 엶
# → Signing & Capabilities → Team 설정 → ▶ Run
```

> **중요**: Xcode 는 반드시 `App.xcworkspace` 로 열어야 함. `App.xcodeproj` 를 직접 열면 CocoaPods 가 링크되지 않아 `Unable to resolve module dependency: 'FirebaseCore'` 같은 import 에러 발생.

#### iOS 트러블슈팅

**증상**: Xcode 에서 `Unable to resolve module dependency: 'FirebaseCore'` (또는 다른 Pod 의존성 미해결)

**원인**: `Podfile.lock` 은 있는데 `frontend/ios/App/Pods/` 폴더가 실제로는 없음 (sync 중단 / 다른 머신에서 lock 만 커밋된 상태).

**복구**:

```bash
cd /Users/maknkkong/project/Petory/frontend/ios/App
pod --version              # 없으면: brew install cocoapods
pod install
open /Users/maknkkong/project/Petory/frontend/ios/App/App.xcworkspace
```

Xcode 메뉴 → Product → Clean Build Folder (`⇧⌘K`) 후 다시 빌드.

### 핫리로드 개발 (선택)

`capacitor.config.ts`의 `server` 블록 주석 해제:

```ts
server: {
  url: 'http://192.168.x.x:3000',  // Mac IP + React dev server 포트
  cleartext: true,
},
```

로컬 IP 확인: `ipconfig getifaddr en0`

> 사용 후 반드시 다시 주석 처리할 것.

---

## Firebase 신규 환경 설정 방법

1. [console.firebase.google.com](https://console.firebase.google.com) → 프로젝트 선택
2. **Android 앱 등록** → `google-services.json` 다운로드 → `android/app/` 복사
3. **iOS 앱 등록** → `GoogleService-Info.plist` 다운로드 → `ios/App/App/` 복사
4. **서비스 계정** (⚙️ → 서비스 계정 탭) → 새 비공개 키 생성 → `backend/main/resources/firebase-service-account.json` 복사
5. `application.properties`에 아래 줄 활성화:
   ```properties
   firebase.service-account.path=classpath:firebase-service-account.json
   ```
6. 서버 재시작 → 로그에 `Firebase Admin SDK 초기화 완료` 확인

---

## 릴리즈 전 필수 체크리스트

```
[ ] capacitor.config.ts  webContentsDebuggingEnabled: false 로 변경
[ ] capacitor.config.ts  server 블록 완전히 제거 (주석 아닌 삭제)
[ ] frontend/.env.capacitor  실제 서버 URL 입력 후 재빌드
[ ] android/app/google-services.json  배포용 파일로 교체
[ ] ios/App/App/GoogleService-Info.plist  배포용 파일로 교체
[ ] iOS APNs 키 → Firebase에 등록 (iOS 푸시 알림 필수)
[ ] Android: Play Store 서명 키 생성 (keystore)
```

---

## 알려진 한계 (후속 작업)

| 항목                        | 내용                                                                                            |
| --------------------------- | ----------------------------------------------------------------------------------------------- |
| iOS APNs                    | Apple Developer 계정 + APNs 키 등록 필요                                                        |
| API URL 하드코딩            | `chatApi.js` 등 일부 모듈이 `http://localhost:8080` 직접 참조 — `API_ROOT` 로 마이그레이션 필요 |
| SSE 알림                    | 앱 백그라운드 시 SSE 연결 끊김 — FCM으로 대체됨                                                 |
| Play Store / App Store 배포 | 서명 키, 개발자 계정 별도 필요                                                                  |
