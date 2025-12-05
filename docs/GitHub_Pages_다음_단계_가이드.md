# GitHub Pages 포트폴리오 사이트 - 다음 단계 가이드

## ✅ 현재까지 완료된 것
- [x] 프로젝트 생성 (Vite + React)
- [x] GitHub 저장소 생성 및 push
- [x] GitHub Pages 배포 완료
- [x] 사이트 접속 확인

---

## 🎯 다음 단계: 라우팅 및 기본 구조 설정

### Step 1: react-router-dom 설치

```bash
npm install react-router-dom
```

---

### Step 2: 프로젝트 폴더 구조 생성

터미널에서 다음 명령어 실행:

**Windows PowerShell:**
```powershell
# pages 폴더 및 하위 폴더 생성
mkdir src\pages
mkdir src\pages\domains
mkdir src\components
mkdir src\components\Layout
mkdir src\components\Common
mkdir src\styles
```

**또는 수동으로 폴더 생성:**
```
src/
├── components/
│   ├── Layout/
│   └── Common/
├── pages/
│   └── domains/
└── styles/
```

---

### Step 3: App.js 라우팅 설정

`src/App.js` 파일을 다음과 같이 수정:

```jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import PortfolioPage from './pages/PortfolioPage';
import PerformancePage from './pages/PerformancePage';
import MCPFilesPage from './pages/MCPFilesPage';

// 도메인 페이지들
import UserDomain from './pages/domains/UserDomain';
import BoardDomain from './pages/domains/BoardDomain';
import CareDomain from './pages/domains/CareDomain';
import MissingPetDomain from './pages/domains/MissingPetDomain';
import LocationDomain from './pages/domains/LocationDomain';
import MeetupDomain from './pages/domains/MeetupDomain';
import ChatDomain from './pages/domains/ChatDomain';

import './styles/global.css';

function App() {
  return (
    <BrowserRouter basename="/makkong1-github.io">
      <Routes>
        {/* 메인 페이지 */}
        <Route path="/" element={<HomePage />} />
        
        {/* 포트폴리오 페이지 */}
        <Route path="/portfolio" element={<PortfolioPage />} />
        
        {/* 도메인 페이지들 */}
        <Route path="/domains/user" element={<UserDomain />} />
        <Route path="/domains/board" element={<BoardDomain />} />
        <Route path="/domains/care" element={<CareDomain />} />
        <Route path="/domains/missing-pet" element={<MissingPetDomain />} />
        <Route path="/domains/location" element={<LocationDomain />} />
        <Route path="/domains/meetup" element={<MeetupDomain />} />
        <Route path="/domains/chat" element={<ChatDomain />} />
        
        {/* 성능 개선 페이지 */}
        <Route path="/performance" element={<PerformancePage />} />
        
        {/* MCP 파일 링크 페이지 */}
        <Route path="/docs" element={<MCPFilesPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

**중요**: `basename="/makkong1-github.io"`는 저장소 이름에 맞게 수정!

---

### Step 4: 기본 페이지 컴포넌트 생성

각 페이지를 빈 컴포넌트로 먼저 생성합니다.

#### 4-1. `src/pages/HomePage.js`

```jsx
function HomePage() {
  return (
    <div>
      <h1>Petory</h1>
      <p>반려동물 커뮤니티 플랫폼</p>
      {/* 나중에 컨텐츠 추가 */}
    </div>
  );
}

export default HomePage;
```

#### 4-2. `src/pages/PortfolioPage.js`

```jsx
function PortfolioPage() {
  return (
    <div>
      <h1>포트폴리오</h1>
      {/* 나중에 컨텐츠 추가 */}
    </div>
  );
}

export default PortfolioPage;
```

#### 4-3. `src/pages/PerformancePage.js`

```jsx
function PerformancePage() {
  return (
    <div>
      <h1>성능 개선 & 트러블슈팅</h1>
      {/* 나중에 컨텐츠 추가 */}
    </div>
  );
}

export default PerformancePage;
```

#### 4-4. `src/pages/MCPFilesPage.js`

```jsx
function MCPFilesPage() {
  return (
    <div>
      <h1>MCP 파일 링크</h1>
      {/* 나중에 컨텐츠 추가 */}
    </div>
  );
}

export default MCPFilesPage;
```

#### 4-5. 도메인 페이지들 (`src/pages/domains/`)

**UserDomain.js:**
```jsx
function UserDomain() {
  return (
    <div>
      <h1>유저 도메인</h1>
      {/* 나중에 컨텐츠 추가 */}
    </div>
  );
}

export default UserDomain;
```

**BoardDomain.js, CareDomain.js, MissingPetDomain.js, LocationDomain.js, MeetupDomain.js, ChatDomain.js**도 동일한 패턴으로 생성.

---

### Step 5: 기본 스타일 파일 생성

`src/styles/global.css`:

```css
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

code {
  font-family: source-code-pro, Menlo, Monaco, Consolas, 'Courier New',
    monospace;
}
```

---

### Step 6: 테스트

1. **로컬에서 확인**
   ```bash
   npm run dev
   ```
   - `http://localhost:5173` 접속
   - 각 라우트가 정상 작동하는지 확인

2. **빌드 테스트**
   ```bash
   npm run build
   ```
   - 에러가 없으면 성공

3. **배포**
   ```bash
   npm run deploy
   ```

---

## 🎨 다음 단계: 컨텐츠 작성

기본 구조가 완성되면 다음 순서로 컨텐츠를 작성합니다:

1. **메인 페이지 (HomePage)**
   - Hero Section
   - 프로젝트 개요
   - 도메인 미리보기 카드
   - 기술 스택

2. **네비게이션 컴포넌트**
   - `src/components/Layout/Navigation.js`
   - 각 페이지로 이동하는 메뉴

3. **도메인 페이지들**
   - 하나씩 상세 컨텐츠 작성

4. **성능 개선 페이지**
   - 개선 사례 정리

5. **MCP 파일 링크 페이지**
   - 문서 링크 모음

6. **포트폴리오 페이지**
   - 개인 소개 및 경험

---

## 📝 체크리스트

- [ ] react-router-dom 설치
- [ ] 폴더 구조 생성
- [ ] App.js 라우팅 설정
- [ ] 모든 페이지 컴포넌트 생성 (빈 컴포넌트라도)
- [ ] global.css 생성
- [ ] 로컬 테스트 (`npm run dev`)
- [ ] 빌드 테스트 (`npm run build`)
- [ ] 배포 (`npm run deploy`)
- [ ] 사이트에서 라우팅 확인

---

## 💡 팁

- 각 페이지를 빈 컴포넌트로 먼저 만들고, 나중에 하나씩 컨텐츠를 채워넣는 방식이 효율적입니다.
- 라우팅이 제대로 작동하는지 먼저 확인한 후 컨텐츠 작성에 집중하세요.
- 개발 중에는 `npm run dev`로 실시간 확인하면서 작업하세요.

---

이제 기본 구조를 잡고 컨텐츠 작성 단계로 넘어갈 준비가 되었습니다! 🚀

