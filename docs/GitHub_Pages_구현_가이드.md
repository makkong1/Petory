# GitHub Pages 포트폴리오 사이트 구현 가이드

## 📍 위치 선택

### 옵션 1: 별도 폴더에 만들기 (추천 ⭐⭐⭐⭐⭐)
**위치**: 현재 프로젝트와 같은 레벨에 새 폴더 생성
```
D:\Petory\                    (현재 프로젝트)
D:\Petory-Portfolio\          (새 포트폴리오 프로젝트)
```

**장점**:
- 현재 프로젝트와 완전히 분리
- 독립적인 Git 저장소 관리 가능
- 깔끔한 구조

**단점**:
- 별도 저장소 관리 필요

---

### 옵션 2: 현재 프로젝트 안에 만들기
**위치**: 현재 프로젝트 루트에 `portfolio` 폴더 생성
```
D:\Petory\
├── backend\
├── frontend\
├── docs\
└── portfolio\                (새로 만들 폴더)
```

**장점**:
- 한 곳에서 관리
- 같은 저장소에 포함

**단점**:
- 프로젝트 저장소가 커짐
- 배포 설정이 복잡할 수 있음

---

## 🎯 추천: 옵션 1 (별도 폴더)

---

## 📋 구현 순서

### Step 1: 폴더 및 프로젝트 생성

1. **새 폴더 생성**
   ```bash
   # D:\Petory와 같은 레벨에 생성
   D:\Petory-Portfolio
   ```

2. **해당 폴더로 이동**
   ```bash
   cd D:\Petory-Portfolio
   ```

3. **React 프로젝트 생성**
   ```bash
   npx create-react-app . --yes
   ```
   또는
   ```bash
   npm create vite@latest . -- --template react
   ```

---

### Step 2: 필요한 패키지 설치

1. **React Router 설치** (페이지 라우팅용)
   ```bash
   npm install react-router-dom
   ```

2. **gh-pages 설치** (배포용)
   ```bash
   npm install --save-dev gh-pages
   ```

3. **스타일링 라이브러리** (선택)
   ```bash
   npm install styled-components
   ```
   또는
   ```bash
   npm install @emotion/react @emotion/styled
   ```

---

### Step 3: package.json 설정

`package.json` 파일에 다음 추가:

```json
{
  "name": "petory-portfolio",
  "version": "1.0.0",
  "homepage": "https://[your-username].github.io/petory-portfolio",
  "scripts": {
    "start": "react-scripts start",
    "build": "react-scripts build",
    "predeploy": "npm run build",
    "deploy": "gh-pages -d build"
  }
}
```

**중요**: `homepage` 필드에 본인의 GitHub username으로 수정!

---

### Step 4: GitHub 저장소 생성

1. **GitHub에서 새 저장소 생성**
   - 저장소 이름: `petory-portfolio` (또는 원하는 이름)
   - Public으로 설정
   - README, .gitignore, license는 선택사항

2. **로컬 저장소 초기화 및 연결**
   ```bash
   cd D:\Petory-Portfolio
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/[your-username]/petory-portfolio.git
   git push -u origin main
   ```

---

### Step 5: 프로젝트 구조 설정

```
Petory-Portfolio/
├── public/
│   └── index.html
├── src/
│   ├── components/
│   │   ├── Layout/
│   │   │   ├── Navigation.js
│   │   │   └── Footer.js
│   │   └── Common/
│   │       └── DomainCard.js
│   ├── pages/
│   │   ├── HomePage.js
│   │   ├── PortfolioPage.js
│   │   ├── domains/
│   │   │   ├── UserDomain.js
│   │   │   ├── BoardDomain.js
│   │   │   ├── CareDomain.js
│   │   │   ├── MissingPetDomain.js
│   │   │   ├── LocationDomain.js
│   │   │   ├── MeetupDomain.js
│   │   │   └── ChatDomain.js
│   │   ├── PerformancePage.js
│   │   └── MCPFilesPage.js
│   ├── styles/
│   │   └── global.css
│   ├── App.js
│   └── index.js
├── package.json
└── README.md
```

---

### Step 6: 기본 컴포넌트 구현

1. **App.js에 라우팅 설정**
   ```jsx
   import { BrowserRouter, Routes, Route } from 'react-router-dom';
   import HomePage from './pages/HomePage';
   import PortfolioPage from './pages/PortfolioPage';
   // ... 기타 import

   function App() {
     return (
       <BrowserRouter basename="/petory-portfolio">
         <Routes>
           <Route path="/" element={<HomePage />} />
           <Route path="/portfolio" element={<PortfolioPage />} />
           {/* ... 기타 라우트 */}
         </Routes>
       </BrowserRouter>
     );
   }
   ```

2. **각 페이지 컴포넌트 생성**
   - 빈 컴포넌트라도 먼저 만들기
   - 나중에 컨텐츠 추가

---

### Step 7: GitHub Pages 설정

1. **GitHub 저장소로 이동**
   - `https://github.com/[your-username]/petory-portfolio`

2. **Settings → Pages 메뉴 클릭**

3. **Source 설정**
   - Branch: `gh-pages` 선택
   - Folder: `/ (root)` 선택
   - Save 클릭

---

### Step 8: 첫 배포

1. **프로젝트 빌드 및 배포**
   ```bash
   npm run deploy
   ```

2. **배포 확인**
   - 몇 분 후 `https://[your-username].github.io/petory-portfolio` 접속
   - 사이트가 보이면 성공!

---

### Step 9: 컨텐츠 작성

1. **메인 페이지부터 시작**
   - Hero Section
   - 프로젝트 개요
   - 도메인 미리보기

2. **도메인 페이지 하나씩 작성**
   - User → Board → Care → ... 순서로

3. **성능 개선 페이지 작성**

4. **MCP 파일 링크 페이지 작성**

5. **포트폴리오 페이지 작성**

---

### Step 10: 반복 배포

컨텐츠를 추가하거나 수정할 때마다:

```bash
git add .
git commit -m "컨텐츠 추가"
git push origin main
npm run deploy
```

---

## 🔧 문제 해결

### 문제 1: 빌드 후 404 에러
**해결**: `package.json`의 `homepage` 필드 확인
- `https://[username].github.io/[repo-name]` 형식으로 설정

### 문제 2: 라우팅이 안 됨
**해결**: `BrowserRouter`에 `basename` 추가
```jsx
<BrowserRouter basename="/petory-portfolio">
```

### 문제 3: 이미지가 안 보임
**해결**: `public` 폴더에 이미지 넣고 `/image.png` 형식으로 참조

---

## 📝 체크리스트

- [ ] 새 폴더 생성 (D:\Petory-Portfolio)
- [ ] React 프로젝트 생성
- [ ] 필요한 패키지 설치 (react-router-dom, gh-pages)
- [ ] package.json 설정 (homepage, deploy 스크립트)
- [ ] GitHub 저장소 생성 및 연결
- [ ] 기본 프로젝트 구조 생성
- [ ] App.js에 라우팅 설정
- [ ] GitHub Pages 설정 (Settings → Pages)
- [ ] 첫 배포 (`npm run deploy`)
- [ ] 사이트 접속 확인
- [ ] 컨텐츠 작성 시작

---

## 💡 팁

1. **개발 중에는**: `npm start`로 로컬에서 확인
2. **배포 전에는**: `npm run build`로 빌드 테스트
3. **배포 후에는**: GitHub Pages URL로 접속해서 확인
4. **문서 링크**: MCP 파일은 GitHub Raw 링크 사용
   - 예: `https://github.com/[username]/Petory/blob/main/docs/architecture/채팅%20시스템%20설계.md`

---

## 🚀 빠른 시작 명령어

```bash
# 1. 폴더 생성 및 이동
mkdir D:\Petory-Portfolio
cd D:\Petory-Portfolio

# 2. React 프로젝트 생성
npx create-react-app . --yes

# 3. 패키지 설치
npm install react-router-dom
npm install --save-dev gh-pages

# 4. Git 초기화 (GitHub 저장소 생성 후)
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/[username]/petory-portfolio.git
git push -u origin main

# 5. 배포
npm run deploy
```

---

이제 시작하면 됩니다! 🎉

