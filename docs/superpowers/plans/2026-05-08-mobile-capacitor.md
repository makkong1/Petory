# Capacitor 모바일 전환 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 React 웹 앱을 Capacitor로 감싸 Android/iOS 네이티브 앱으로 배포 가능하게 만든다.

**Architecture:** PWA 기반 수정(터치 타겟, safe-area, manifest) → Capacitor 래핑 → API URL 환경분기 → Android APK 빌드. 기존 React 코드를 최대한 유지하며 WebView 환경에서 동작하지 않는 부분만 외과적으로 수정한다.

**Tech Stack:** React 19, styled-components 6, @capacitor/core 6, @capacitor/android 6, @capacitor/preferences 6, Gradle (Android)

---

## 파일 변경 맵

| 파일 | 타입 | 변경 내용 |
|------|------|----------|
| `frontend/public/manifest.json` | Modify | 이름·아이콘·색상 Petory 브랜딩 |
| `frontend/public/index.html` | Modify | iOS PWA 메타태그 추가 |
| `frontend/src/components/Common/ui/Button.js` | Modify | 터치 타겟 min-height: 44px |
| `frontend/src/components/Common/ui/Input.js` | Modify | 터치 타겟 min-height: 44px |
| `frontend/src/components/Layout/Navigation.js` | Modify | safe-area-inset-bottom, 드롭다운 모바일 수정 |
| `frontend/src/App.js` | Modify | ContentArea safe-area padding |
| `frontend/src/api/apiClient.js` | Modify | BASE_URL 환경분기 |
| `frontend/src/api/tokenStorage.js` | Modify | @capacitor/preferences 하이브리드 저장 |
| `capacitor.config.ts` | Create | Capacitor 프로젝트 설정 |
| `frontend/package.json` | Modify | Capacitor 의존성 추가 |

---

## Task 1: PWA Manifest & iOS 메타태그 수정

**Files:**
- Modify: `frontend/public/manifest.json`
- Modify: `frontend/public/index.html`

- [ ] **Step 1: manifest.json 교체**

`frontend/public/manifest.json` 전체를 아래로 교체한다:

```json
{
  "short_name": "Petory",
  "name": "Petory - 반려동물 케어 플랫폼",
  "description": "반려동물 케어 & 커뮤니티 통합 플랫폼",
  "icons": [
    {
      "src": "favicon.ico",
      "sizes": "64x64 32x32 24x24 16x16",
      "type": "image/x-icon"
    },
    {
      "src": "logo192.png",
      "type": "image/png",
      "sizes": "192x192",
      "purpose": "any maskable"
    },
    {
      "src": "logo512.png",
      "type": "image/png",
      "sizes": "512x512",
      "purpose": "any maskable"
    }
  ],
  "start_url": ".",
  "display": "standalone",
  "orientation": "portrait",
  "theme_color": "#E8714A",
  "background_color": "#FFF8F5",
  "categories": ["lifestyle", "social"]
}
```

- [ ] **Step 2: index.html iOS 메타태그 추가**

`frontend/public/index.html`의 `<meta name="theme-color" content="#000000" />` 줄을 아래로 교체한다:

```html
  <meta name="theme-color" content="#E8714A" />
  <meta name="apple-mobile-web-app-capable" content="yes" />
  <meta name="apple-mobile-web-app-status-bar-style" content="default" />
  <meta name="apple-mobile-web-app-title" content="Petory" />
  <meta name="format-detection" content="telephone=no" />
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: `The build folder is ready to be deployed.`

- [ ] **Step 4: 커밋**

```bash
git add frontend/public/manifest.json frontend/public/index.html
git commit -m "chore(mobile): PWA manifest Petory 브랜딩 및 iOS 메타태그 추가"
```

---

## Task 2: 터치 타겟 44px 수정 (Button, Input)

**Files:**
- Modify: `frontend/src/components/Common/ui/Button.js:23-37`
- Modify: `frontend/src/components/Common/ui/Input.js:6-8`

iOS/Android Human Interface Guideline 최소 터치 타겟은 44×44px. 현재 md 버튼은 padding 10px → 총 ~32px.

- [ ] **Step 1: Button.js sizeStyles 수정**

`frontend/src/components/Common/ui/Button.js`의 `const sizeStyles` 객체를 아래로 교체한다:

```js
const sizeStyles = {
  sm: css`
    padding: 10px 16px;
    min-height: 36px;
    font-size: ${({ theme }) => theme.typography.body2.fontSize};
    gap: 4px;
  `,
  md: css`
    padding: 12px 20px;
    min-height: 44px;
    font-size: ${({ theme }) => theme.typography.body1.fontSize};
    gap: 6px;
  `,
  lg: css`
    padding: 14px 24px;
    min-height: 48px;
    font-size: ${({ theme }) => theme.typography.body1.fontSize};
    gap: 8px;
  `,
};
```

- [ ] **Step 2: Input.js baseInputStyles padding 수정**

`frontend/src/components/Common/ui/Input.js`의 `baseInputStyles` 안에서
`padding: 10px 14px;` 를 아래로 교체한다:

```css
  padding: 12px 14px;
  min-height: 44px;
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | grep -E "error|warning|compiled" | head -10
```

Expected: `Compiled successfully.` (또는 warning만 있고 error 없음)

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/Common/ui/Button.js \
        frontend/src/components/Common/ui/Input.js
git commit -m "fix(mobile): 버튼·입력 터치 타겟 최소 44px 적용"
```

---

## Task 3: Safe Area & ContentArea 수정

**Files:**
- Modify: `frontend/src/components/Layout/Navigation.js:720-735`
- Modify: `frontend/src/App.js:269-281`

아이폰 홈 인디케이터(34px), 안드로이드 제스처 영역이 하단 네비게이션과 콘텐츠를 가리는 문제를 수정한다.

- [ ] **Step 1: BottomNav safe-area-inset-bottom 추가**

`frontend/src/components/Layout/Navigation.js`에서 `const BottomNav = styled.nav` 블록을 찾아 `@media (max-width: 768px)` 내부를 아래로 교체한다:

```js
const BottomNav = styled.nav`
  display: none;

  @media (max-width: 768px) {
    display: flex;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: calc(60px + env(safe-area-inset-bottom, 0px));
    padding-bottom: env(safe-area-inset-bottom, 0px);
    background: ${props => props.theme.colors.surface};
    border-top: 1px solid ${props => props.theme.colors.border};
    z-index: 100;
    align-items: stretch;
  }
`;
```

- [ ] **Step 2: ContentArea safe-area padding 추가**

`frontend/src/App.js`에서 `const ContentArea = styled.main` 블록의 `@media (max-width: 768px)` 내부를 아래로 교체한다:

```js
const ContentArea = styled.main`
  margin-left: 0;
  margin-top: 60px;
  flex: 1;
  min-height: calc(100vh - 60px);
  background: ${props => props.theme.colors.background};

  @media (max-width: 768px) {
    margin-top: 0;
    padding-bottom: calc(60px + env(safe-area-inset-bottom, 0px));
    min-height: 100dvh;
  }
`;
```

> `100dvh` = Dynamic Viewport Height. 모바일 브라우저 주소창 높이 변화에 대응.

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | grep -E "error|compiled" | head -5
```

Expected: `Compiled successfully.`

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/Layout/Navigation.js \
        frontend/src/App.js
git commit -m "fix(mobile): 하단 네비 및 콘텐츠 영역 safe-area-inset 적용"
```

---

## Task 4: 알림 드롭다운 모바일 위치 수정

**Files:**
- Modify: `frontend/src/components/Layout/Navigation.js:577-595` (`SidebarNotificationDropdown`)

현재 `width: 360px`, `right: 0` 고정으로 좁은 화면(iPhone SE = 375px)에서 드롭다운이 화면 너비를 초과한다.

- [ ] **Step 1: SidebarNotificationDropdown 모바일 대응 추가**

`frontend/src/components/Layout/Navigation.js`에서 `const SidebarNotificationDropdown = styled.div` 블록을 찾아, `animation:` 줄 아래에 다음 미디어 쿼리를 추가한다:

```js
const SidebarNotificationDropdown = styled.div`
  position: fixed;
  top: 60px;
  right: 0;
  left: auto;
  bottom: auto;
  width: 360px;
  max-width: calc(100vw - 16px);
  max-height: 450px;
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  border-radius: ${props => props.theme.borderRadius?.lg || '8px'};
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  z-index: 200;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: ${slideInDown} 0.2s ease-out;

  @media (max-width: 768px) {
    left: 8px;
    right: 8px;
    width: auto;
    top: 56px;
    border-radius: ${props => props.theme.borderRadius?.lg || '8px'};
  }
`;
```

- [ ] **Step 2: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | grep -E "error|compiled" | head -5
```

Expected: `Compiled successfully.`

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/Layout/Navigation.js
git commit -m "fix(mobile): 알림 드롭다운 모바일 화면 너비 초과 수정"
```

---

## Task 5: Capacitor 설치 및 설정

**Files:**
- Modify: `frontend/package.json`
- Create: `capacitor.config.ts` (프로젝트 루트)

- [ ] **Step 1: Capacitor 패키지 설치**

```bash
cd /Users/maknkkong/project/Petory/frontend
npm install @capacitor/core@latest @capacitor/preferences@latest
npm install -D @capacitor/cli@latest @capacitor/android@latest
```

Expected: `added N packages` — 에러 없음.

- [ ] **Step 2: Capacitor 초기화**

```bash
cd /Users/maknkkong/project/Petory
npx cap init Petory com.petory.app --web-dir frontend/build
```

`capacitor.config.ts`가 생성된다.

- [ ] **Step 3: capacitor.config.ts 내용 교체**

생성된 `capacitor.config.ts`를 아래 내용으로 교체한다:

```ts
import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.petory.app',
  appName: 'Petory',
  webDir: 'frontend/build',
  server: {
    // 개발 시: 로컬 서버 직접 가리킴 (핫리로드 가능)
    // 운영 배포 시: 이 블록 전체 제거하고 webDir 번들만 사용
    // url: 'http://192.168.1.XXX:3000',
    // cleartext: true,
  },
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    Preferences: {
      group: 'PetoryStorage',
    },
  },
};

export default config;
```

- [ ] **Step 4: Android 플랫폼 추가**

```bash
cd /Users/maknkkong/project/Petory
npx cap add android
```

Expected: `✔ Adding native android project in android/` 출력.

`android/` 디렉토리가 생성된다.

- [ ] **Step 5: android/ Git 트래킹 설정**

```bash
# android/ 는 커밋 대상 (네이티브 프로젝트)
# node_modules 등 불필요한 것만 제외
echo "android/.gradle/" >> .gitignore
echo "android/app/build/" >> .gitignore
```

- [ ] **Step 6: 커밋**

```bash
git add capacitor.config.ts .gitignore frontend/package.json frontend/package-lock.json android/
git commit -m "chore(mobile): Capacitor 초기 설정 및 Android 플랫폼 추가"
```

---

## Task 6: API Base URL WebView 환경 분기

**Files:**
- Modify: `frontend/src/api/apiClient.js:11`
- Modify: `frontend/src/api/tokenStorage.js`

Capacitor WebView에서 `localhost`는 **기기 자신**을 가리킴 → API 전부 실패. 환경변수로 실제 서버 URL을 주입해야 한다.

- [ ] **Step 1: apiClient.js BASE_URL 수정**

`frontend/src/api/apiClient.js`의 `export const API_ROOT` 줄을 아래로 교체한다:

```js
/** 백엔드 API 루트
 *  - 개발 웹:    REACT_APP_API_BASE_URL 없으면 프록시(localhost:8080) 사용
 *  - Capacitor:  반드시 REACT_APP_API_BASE_URL=https://your-server.com/api 설정 필요
 */
export const API_ROOT = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';
```

그리고 `frontend/.env.capacitor` 파일을 새로 만든다 (빌드 시 수동 복사):

```
REACT_APP_API_BASE_URL=https://your-api-server.com/api
REACT_APP_NAVER_MAPS_KEY_ID=ffce04pdvg
```

> `.env.capacitor`는 실제 서버 배포 후 도메인을 채운다. 로컬 테스트 시 `http://192.168.x.x:8080/api`로 설정.

- [ ] **Step 2: tokenStorage.js @capacitor/preferences 하이브리드 저장**

`frontend/src/api/tokenStorage.js` 전체를 아래로 교체한다.
- 동기 읽기(localStorage)는 그대로 유지해 기존 호출부 변경 없음
- 쓰기는 localStorage + Preferences 동시 저장 (Capacitor 환경에서 영구 보존)

```js
/**
 * 토큰 저장소.
 * - 읽기: localStorage (동기, 기존 코드 호환)
 * - 쓰기: localStorage + @capacitor/preferences (비동기, 네이티브 앱 영구 보존)
 */

const isCapacitor = () =>
  typeof window !== 'undefined' && !!(window.Capacitor?.isNativePlatform?.());

const saveToPreferences = async (key, value) => {
  if (!isCapacitor()) return;
  try {
    const { Preferences } = await import('@capacitor/preferences');
    await Preferences.set({ key, value });
  } catch (_) {}
};

const removeFromPreferences = async (key) => {
  if (!isCapacitor()) return;
  try {
    const { Preferences } = await import('@capacitor/preferences');
    await Preferences.remove({ key });
  } catch (_) {}
};

export const getToken = () =>
  localStorage.getItem('accessToken') || localStorage.getItem('token');

export const setToken = (token) => {
  localStorage.setItem('accessToken', token);
  if (localStorage.getItem('token')) localStorage.removeItem('token');
  saveToPreferences('accessToken', token);
};

export const removeToken = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('token');
  removeFromPreferences('accessToken');
};

export const getRefreshToken = () => localStorage.getItem('refreshToken');

export const setRefreshToken = (token) => {
  localStorage.setItem('refreshToken', token);
  saveToPreferences('refreshToken', token);
};

export const removeRefreshToken = () => {
  localStorage.removeItem('refreshToken');
  removeFromPreferences('refreshToken');
};

export const removeAllTokens = () => {
  removeToken();
  removeRefreshToken();
};
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | grep -E "error|compiled" | head -5
```

Expected: `Compiled successfully.`

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/api/apiClient.js \
        frontend/src/api/tokenStorage.js \
        frontend/.env.capacitor
git commit -m "fix(mobile): Capacitor WebView API URL 환경분기 및 토큰 저장소 하이브리드 적용"
```

---

## Task 7: Android 빌드 및 기기 검증

**Files:**
- 읽기 전용 (빌드/배포 작업)

- [ ] **Step 1: 프론트엔드 운영 빌드**

```bash
# Capacitor용 환경변수 적용 후 빌드
cd /Users/maknkkong/project/Petory/frontend
cp .env.capacitor .env.production.local
npm run build
```

Expected: `The build folder is ready to be deployed.`

- [ ] **Step 2: 네이티브 프로젝트에 웹 번들 동기화**

```bash
cd /Users/maknkkong/project/Petory
npx cap sync android
```

Expected:
```
✔ Copying web assets from frontend/build to android/app/src/main/assets/public
✔ Updating Android plugins
✔ update android
```

- [ ] **Step 3: Android Studio로 프로젝트 열기**

```bash
npx cap open android
```

Android Studio가 열린다. 초기 Gradle sync를 기다린다 (1~3분).

- [ ] **Step 4: 에뮬레이터 또는 기기에서 실행**

Android Studio에서:
1. `Run` 메뉴 → `Run 'app'` (▶ 버튼)
2. 에뮬레이터 선택 (API 30+ 권장) 또는 USB 연결 기기

앱이 실행되면 아래를 수동으로 확인:
- [ ] 하단 네비게이션 표시 여부
- [ ] 홈 인디케이터 영역 겹침 없음
- [ ] 버튼 터치 반응 (44px)
- [ ] API 호출 성공 (로그인 시도)
- [ ] 채팅 WebSocket 연결 여부

- [ ] **Step 5: APK 빌드 (테스트 배포용)**

Android Studio에서:
1. `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. 빌드 완료 후 `locate` 클릭 → APK 경로 확인

또는 터미널:
```bash
cd android
./gradlew assembleDebug
# APK 위치: android/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 6: 최종 커밋**

```bash
cd /Users/maknkkong/project/Petory
git add android/
git commit -m "chore(mobile): Android 네이티브 프로젝트 초기 빌드 설정"
```

---

## 완료 기준 체크리스트

```
[ ] manifest.json 이름이 "Petory"
[ ] iPhone Safari에서 홈 화면 추가 시 standalone 모드 동작
[ ] 버튼 터치 타겟 44px 이상
[ ] 아이폰 홈 인디케이터와 하단 네비 겹침 없음
[ ] 알림 드롭다운 좁은 화면(375px)에서 잘림 없음
[ ] Android APK 빌드 성공
[ ] APK 설치 후 로그인 → API 호출 성공
[ ] APK에서 채팅 WebSocket 연결 성공
```

---

## 이후 추가 작업 (이번 계획 범위 외)

| 작업 | 예상 공수 |
|------|----------|
| FCM 푸시 알림 (`@capacitor/push-notifications`) | 2~3일 |
| iOS 빌드 (Xcode, Mac 필요) | 1~2일 |
| Play Store 배포 (서명 키, 스토어 등록) | 0.5일 |
| App Store 배포 (Apple Developer 계정 필요) | 1일 |
| 알림 스트림 토큰 URL 노출 → 헤더 방식 전환 | 1일 |
