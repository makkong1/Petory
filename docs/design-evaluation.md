# Petory 프론트엔드 디자인 평가 보고서

## 작성일
2026-04-13

---

## 현재 스타일링 방식 요약

### 스타일링 기술 스택

Petory 프론트엔드는 **CSS 파일을 전혀 사용하지 않고**, `styled-components` 라이브러리를 통한 CSS-in-JS 방식으로 전체 스타일링을 구현한다. 전체 컴포넌트 파일(66개 중 66개)이 `import styled from 'styled-components'`를 포함하고 있으며, styled 컴포넌트 선언이 파일 전체에서 약 1,238개 이상 존재한다.

### theme.js 토큰 체계

`/frontend/src/styles/theme.js`에 라이트/다크 테마 객체 2개(`lightTheme`, `darkTheme`)가 정의되어 있다. 토큰 카테고리는 다음과 같다:

| 카테고리 | 토큰 수 | 내용 |
|---|---|---|
| colors | 25개 | primary, secondary, surface계열, text계열, 상태색, 도메인색, AI색, status색 등 |
| shadows | 4단계 | sm / md / lg / xl |
| spacing | 6단계 | xs(4px) ~ xxl(30px) |
| borderRadius | 5단계 | sm(4px) ~ full(50%) |
| typography | 7단계 | h1~h4, body1, body2, caption |

다크 테마는 shadows, spacing, borderRadius, typography를 라이트 테마와 공유하고 colors만 별도 정의한다.

### 인라인 스타일 사용 현황

`style={{ }}` 속성 직접 사용은 26건으로 상대적으로 적다. 주로 `flex: 1` 같은 레이아웃 보조나 긴급 수정 용도로 사용된다. 대부분의 스타일은 styled-components로 캡슐화되어 있다.

### 반응형 처리 현황

`@media` 쿼리는 총 69건이 존재하며, 주요 브레이크포인트는 `768px`(모바일)가 가장 많고, `1024px`(태블릿), `960px`(어드민), `600px`(맵 패널) 등이 산발적으로 사용된다.

---

## 도메인별 분석

### Layout / Navigation
styled-components 기반으로 잘 구성되어 있다. 테마 토큰을 충실히 사용하고, 모바일 메뉴 토글, 알림 드롭다운, 프로필 드롭다운 등 복합적인 UI를 처리한다. `keyframes`를 이용한 슬라이드다운 애니메이션도 포함된다. 단, 알림 버튼(`NotificationButton`)과 테마 토글(`ThemeToggle`)은 시각적으로 동일한 스타일임에도 별도의 styled 컴포넌트로 중복 정의되어 있다.

### Home / HomePage
Hero 섹션에 그라디언트 배경, 카드 그리드 등을 갖추고 있으며 테마 토큰을 광범위하게 사용한다. 그러나 Hero Title 폰트 크기(`48px`)나 Subtitle 크기(`20px`)가 테마 `typography` 토큰 범위를 벗어난 하드코딩이다. 두 개의 `margin-bottom` 선언이 동일 컴포넌트(`HeroSection`)에 중복 작성된 버그도 발견된다.

```js
// HomePage.js - HeroSection에 margin-bottom 중복 선언
const HeroSection = styled.div`
  margin-bottom: ${props => props.theme.spacing.xxl};  // 첫 번째
  ...
  margin-bottom: ${props => props.theme.spacing.xxl};  // 두 번째 (덮어씀)
`;
```

### Auth / LoginForm, RegisterForm
가장 정돈된 스타일링을 보이는 도메인이다. `Input`, `Button` 스타일이 테마 토큰을 충실히 따르고 `:focus`, `:hover`, `:disabled`, `:active` 상태를 모두 정의한다. `ForgotPasswordModal`은 `aria-hidden="true"` / `role="dialog" aria-modal="true"`로 접근성을 고려했다.

### Common / ui (Button, Input, Spinner, EmptyState)
공통 UI 컴포넌트가 존재하나 **실제 활용률이 낮다**. 대부분의 도메인에서 이 공통 Button, Input을 가져다 쓰지 않고 도메인별로 독립적인 Button, Input을 재정의한다.

```js
// LoginForm.js - 자체 Button 정의 (Common/ui/Button 미사용)
const Button = styled.button`
  padding: 11px 20px;
  background: ${({ theme }) => theme.colors.primary};
  ...
`;

// MissingPetBoardPage.js - 또 다른 CreateButton 정의
const CreateButton = styled.button`
  border: none;
  background: ${(props) => props.theme.colors.primary};
  ...
`;
```

### UnifiedMap / DomainTabHeader, MiniMapPicker
접근성 속성(`role="tablist"`, `role="tab"`, `aria-selected`)을 명시적으로 사용하며, 도메인별 색상 토큰(`domain.location`, `domain.meetup`, `domain.care`)을 적극 활용한다. 하드코딩 값(`border-radius: 20px`, `padding: 7px 12px`)이 일부 존재한다.

### Community / CommunityBoard
Magazine 스타일의 복잡한 카드 레이아웃(large/medium/small 분류)을 구현하며, 카테고리별 고유 색상을 props로 받는 방식을 사용한다. 그러나 이 카테고리 색상들은 `theme.js`가 아닌 컴포넌트 내부 상수 배열에 하드코딩되어 있다:

```js
// CommunityBoard.js - 카테고리 색상이 theme.js 밖에 정의됨
const categories = [
  { key: 'ALL', label: '전체', icon: '📋', color: '#6366F1' },
  { key: '일상', label: '일상', icon: '📖', color: '#EC4899' },
  { key: '질문', label: '질문', icon: '❓', color: '#3B82F6' },
  ...
];
```

### Chat / ChatRoom
Chat 도메인은 theme 토큰 사용이 가장 낮다. 하드코딩된 `font-size` 값이 30개 이상, `border-radius` 하드코딩이 20개 이상 존재한다. 메시지 버블, 입력창, 헤더 등 모든 요소가 임의 px 값으로 작성되어 있다.

```js
// ChatRoom.js - theme 토큰 없이 하드코딩된 스타일 다수
font-size: 14px;  // 30회 이상 반복
border-radius: 20px;
border-radius: 8px;
border-radius: 50%;
```

### MissingPet / MissingPetBoardPage
반응형 그리드(`grid-template-columns: repeat(4, 1fr)` → 2열 → 1열)와 테마 토큰을 잘 조합하여 사용한다. 상태 배지(StatusBadge)는 `status` props를 받아 색상을 동적으로 결정하나, `theme.colors.status` 토큰을 활용하지 않고 직접 색상 값을 조건부로 반환한다.

### Payment / PetCoinChargePage
이 컴포넌트는 `useTheme()` 훅으로 `theme` 객체를 가져온 뒤 styled-components의 ThemeProvider가 아닌 **직접 `theme` prop으로 스타일을 주입**하는 방식을 사용한다. 이는 프로젝트 전체 패턴과 불일치하며, `background: #fee`, `background: #efe` 같은 테마 미지원 하드코딩 색상도 포함한다.

```js
// PetCoinChargePage.js - 잘못된 theme 사용 패턴
const { theme } = useTheme();
// JSX에서 theme prop 직접 전달
<ModalOverlay onClick={onClose} theme={theme}>
<ModalContent onClick={(e) => e.stopPropagation()} theme={theme}>

// styled 컴포넌트에서 ThemeProvider가 아닌 직접 전달된 prop으로 읽기
const ModalContent = styled.div`
  background: ${props => props.theme.colors.surface || '#ffffff'};
`;

// 테마 미지원 색상 하드코딩
const ErrorMessage = styled.div`
  background: #fee;  // theme.colors.error 미사용
`;
```

### Admin / AdminPanel
어드민 레이아웃은 `@media (max-width: 960px)` 브레이크포인트를 사용하여 다른 페이지(768px)와 일치하지 않는다. 각 섹션(`UserManagementSection`, `ReportManagementSection` 등)은 별도 파일로 분리되어 있으나, 내부 스타일은 각자 독립적으로 정의된다.

---

## 항목별 평가

### 1. 디자인 일관성 [3.0 / 5]

**평가 근거:**

긍정 요소:
- `theme.js`에 색상, 간격, 폰트, 그림자, borderRadius 토큰이 체계적으로 정의됨
- 다크 모드 지원이 구조적으로 완성되어 있음
- 주요 도메인(Auth, Navigation, Home, MissingPet)에서 토큰 활용이 양호함

부정 요소:
- Chat 도메인 전체에서 theme 토큰 미사용 하드코딩이 대량 존재
- 커뮤니티 카테고리 색상, 활동 타입 색상 등이 theme.js 외부에 산재
- Payment 컴포넌트의 theme 주입 패턴이 전체 아키텍처와 불일치
- `HeroSection`의 `margin-bottom` 중복 선언 같은 실수가 존재
- spacing 토큰(`xs~xxl`)에 없는 값(예: `24px`, `32px`)이 자주 하드코딩됨

**코드 예시 (불일치):**
```js
// theme.js spacing에는 xxl=30px까지만 있으나 24px, 32px이 직접 사용됨
// PetCoinChargePage.js
const ModalHeader = styled.div`
  padding: 24px;  // theme.spacing 토큰 미사용
`;
```

---

### 2. 접근성 [2.5 / 5]

**평가 근거:**

긍정 요소:
- `DomainTabHeader`에서 `role="tablist"`, `role="tab"`, `aria-selected` 정확 사용
- `PageNavigation`에서 `aria-label="이전 페이지"`, `aria-label="다음 페이지"` 명시
- `LoginForm`의 비밀번호 찾기 모달에 `role="dialog" aria-modal="true"` 적용
- 입력 요소에 `id`/`htmlFor`를 연결한 `<label>` 태그 사용
- `:focus` 스타일이 주요 입력 요소에 구현됨

부정 요소:
- Navigation의 알림 드롭다운, 프로필 드롭다운에 `aria-expanded`, `aria-haspopup` 누락
- 키보드로 드롭다운 닫기 (Escape 키) 처리 없음
- 모달에서 포커스 트랩(focus trap) 구현 미비
- 색상 대비: `textLight` (#9E9E9E / white 배경)의 대비율 ≈ 2.85:1로 WCAG AA 기준(4.5:1) 미달
- Chat 인터페이스 전체에 접근성 속성 미적용
- 대부분의 이모지에 `aria-hidden` 처리가 불일치 (`aria-hidden="true"` 적용된 곳과 안 된 곳 혼재)
- `outline: none`을 `:focus` 상태에서 제거하면서 대체 포커스 스타일을 `box-shadow`로 제공하는 곳이 있으나, 일부 버튼은 포커스 스타일이 전혀 없음

**코드 예시 (문제):**
```js
// Navigation.js - 드롭다운 버튼에 접근성 속성 누락
<NotificationButton type="button" onClick={() => setIsNotificationOpen(!isNotificationOpen)}>
  🔔  // aria-hidden 없음
  {unreadCount > 0 && <NotificationBadge>{unreadCount}</NotificationBadge>}
  // aria-expanded, aria-label, aria-haspopup 모두 없음
</NotificationButton>
```

---

### 3. 반응형 디자인 [3.0 / 5]

**평가 근거:**

긍정 요소:
- 주요 페이지(Navigation, MissingPetBoardPage, LoginForm, CommunityBoard)에 모바일 미디어 쿼리 적용
- Navigation은 768px 이하에서 햄버거 메뉴로 전환
- MissingPetBoardPage는 4열→2열→1열 그리드로 전환
- 지도 InfoPanel은 600px에서 전체 너비로 전환

부정 요소:
- 브레이크포인트가 표준화되지 않음: `600px`, `720px`, `768px`, `960px`, `1024px` 혼용
- Chat 컴포넌트는 모바일 대응이 부실함
- Payment 컴포넌트에 반응형 처리 없음
- 태블릿 전용 레이아웃(768px~1024px)이 대부분 생략됨
- 하드코딩된 `max-width: 1350px`(Navigation)과 `max-width: 1200px`(Home, MissingPet) 불일치

**코드 예시 (혼재된 브레이크포인트):**
```js
// Layout/Navigation.js
@media (max-width: 768px) { ... }

// Admin/AdminLayout.js
@media (max-width: 960px) { ... }

// MissingPetBoardDetail.js
@media (max-width: 720px) { ... }

// UnifiedMap/shared/BaseInfoPanel.js
@media (max-width: 600px) { ... }
```

---

### 4. 컴포넌트 재사용성 [2.0 / 5]

**평가 근거:**

긍정 요소:
- `Common/ui/` 폴더에 Button, Input, Textarea, Select, Spinner, EmptyState 공통 컴포넌트가 존재
- `Common/PageNavigation.js`가 여러 페이지에서 공유됨
- `UnifiedMap/shared/BaseInfoPanel.js`에 맵 패널 공통 스타일이 추출됨

부정 요소:
- `Common/ui/Button.js`가 도메인별로 거의 사용되지 않음 - 각 컴포넌트가 자체 Button을 재정의
- 각 페이지에서 비슷한 패턴의 `LoadingContainer`, `LoadingSpinner`, `EmptyState`, `ErrorBanner`를 중복 정의
- 모달 오버레이/콘텐츠 구조가 PermissionDeniedModal, PetCoinChargePage, ForgotPasswordModal 등에서 각각 독립적으로 구현됨
- 상태 배지(StatusBadge)가 MissingPetBoardPage, CommunityBoard 등에서 별도로 구현됨

**중복 구현 현황 (추정):**
- Button 컴포넌트: 최소 6개 도메인에서 각자 정의
- 로딩 스피너: 최소 4개 도메인에서 재정의
- 모달 오버레이: 최소 3개 위치에서 재구현
- 빈 상태(Empty State): 최소 4개 위치에서 재구현

---

### 5. 코드 유지보수성 [2.5 / 5]

**평가 근거:**

긍정 요소:
- `styled-components`를 일관되게 채택하여 스타일 범위(scope)가 자동 격리됨
- ThemeProvider와 useTheme 훅을 통한 테마 시스템이 구축됨
- 대부분의 컴포넌트에서 스타일이 파일 하단에 모아 정의됨 (JSX와 스타일의 시각적 분리)

부정 요소:
- theme.js의 spacing 토큰이 6단계(xs~xxl)로 제한적이며, `16px`, `24px`, `32px` 등이 자주 하드코딩됨 (토큰 범위 부족)
- `PetCoinChargePage`의 잘못된 theme 주입 패턴은 ThemeProvider 이중화로 이어져 다크모드 버그 가능성이 있음
- typography 토큰의 최대 h1이 `20px`인데, HeroSection의 타이틀이 `48px`로 대폭 초과 — 토큰 범위가 애플리케이션의 실제 사용 범위를 커버하지 못함
- 컴포넌트당 styled 선언이 지나치게 많아 파일이 300~1000줄에 달하는 경우가 많음

**코드 예시 (typography 토큰 한계):**
```js
// theme.js
typography: {
  h1: { fontSize: '20px', fontWeight: '700' },  // 최대 20px
  ...
}

// HomePage.js - 토큰 범위를 벗어난 헤로 타이틀
const HeroTitle = styled.h1`
  font-size: 48px;  // theme.typography 범위를 2배 이상 초과
  font-weight: 700;
`;
```

---

### 6. 시각적 계층구조 [3.0 / 5]

**평가 근거:**

긍정 요소:
- 주 색상(primary #FF7E36)이 CTA 버튼, 활성 탭, 링크 등에 일관 적용됨
- 텍스트 계층이 `text`(#212121), `textSecondary`(#757575), `textLight`(#9E9E9E) 3단계로 구성됨
- 카드에 테두리+그림자 조합으로 배경과의 구분이 명확함
- 페이지별 타이틀과 부제목의 크기 차이가 존재함

부정 요소:
- 전체적인 폰트 크기 스케일이 좁음: body1이 13px, body2가 12px, caption이 10px — 가독성 낮음
- heading 토큰(h1=20px, h2=18px, h3=16px, h4=14px)의 폰트 크기 차이가 너무 작아 계층이 모호함
- 아이콘을 모두 이모지로 처리하여 일관된 아이콘 시스템이 없음 (SVG 아이콘 라이브러리 미도입)
- 정보 밀도가 높은 어드민 페이지에서 섹션 간 공백이 충분하지 않음
- 커뮤니티 카드에서 제목/내용/메타 정보의 폰트 크기 차이가 미미함

---

### 7. 인터랙션 피드백 [3.5 / 5]

**평가 근거:**

긍정 요소:
- 대부분의 버튼에 `:hover` 상태(배경색 변화, `transform: translateY(-1px/-2px)`) 정의
- `:active` 상태(`transform: translateY(0)`)로 누름 효과 제공
- `:disabled` 상태가 색상, 커서, transform 없음으로 명시적으로 처리됨
- 입력 필드에 `:focus` 시 `border-color` 변경과 `box-shadow` 글로우 효과 구현
- 카드 hover 시 `translateY(-4px)` + `box-shadow` 조합으로 부상 효과 제공
- Navigation 프로필 드롭다운에 `slideDownAnimation` keyframes 적용

부정 요소:
- `transition` 지속 시간이 `0.2s`(대부분), `0.3s`(일부)로 혼재 — 일관성 부족
- 로딩 상태 피드백이 단순 텍스트(`로그인 중...`, `검색 중...`)에 그침 — 시각적 스피너 활용 미흡
- 모달 열기 애니메이션이 일부만 구현됨 (`PermissionDeniedModal`에는 있으나 `PetCoinChargePage`에는 없음)
- focus-visible pseudo-class 미사용으로 키보드 탐색 시 시각적 피드백 불일치 가능성

**코드 예시 (양호한 인터랙션):**
```js
// LoginForm.js - 완성도 높은 인터랙션 상태 정의
const Input = styled.input`
  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: 0 0 0 3px ${({ theme }) => theme.colors.primary}25;
  }
  &:hover:not(:focus):not(:disabled) {
    border-color: ${({ theme }) => theme.colors.borderDark};
  }
  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    cursor: not-allowed;
  }
`;
```

---

### 8. 전체적 미적 완성도 [2.5 / 5]

**평가 근거:**

긍정 요소:
- 당근마켓 스타일의 주황색 계열 브랜드 컬러가 일관되게 적용됨
- 카드 기반 레이아웃이 깔끔하게 구성됨
- 라이트/다크 모드 전환이 구현됨
- 그라디언트 Hero 섹션이 시각적 포인트 역할을 함

부정 요소:
- 아이콘 체계가 이모지에 전적으로 의존하여 플랫폼별 렌더링 불일치 위험이 있음
- 전체 폰트 스케일이 소형(12~20px)에 몰려 있어 정보 계층의 시각적 임팩트가 약함
- borderRadius 토큰의 최대값이 `12px`(xl)로 현대적인 "둥근 UI" 트렌드에 비해 제한적
- 개발 중 남겨진 미완성 UI 요소가 존재 (`StatsSection`이 주석처리 된 채 더미 텍스트 포함)
- `PermissionDeniedModal`에 "당신 접근하면 안돼요!" 같은 비공식적 문구가 그대로 남아 있음
- 페이지간 레이아웃 최대 너비가 통일되지 않음(1200px, 1350px 혼재)
- 커뮤니티, 실종제보 등 도메인별 디자인 언어에 차이가 있어 앱 전체의 통일감이 낮음

---

## 종합 점수: 2.8 / 5.0

| 항목 | 점수 |
|---|---|
| 1. 디자인 일관성 | 3.0 |
| 2. 접근성 | 2.5 |
| 3. 반응형 디자인 | 3.0 |
| 4. 컴포넌트 재사용성 | 2.0 |
| 5. 코드 유지보수성 | 2.5 |
| 6. 시각적 계층구조 | 3.0 |
| 7. 인터랙션 피드백 | 3.5 |
| 8. 전체적 미적 완성도 | 2.5 |
| **평균** | **2.8** |

---

## 주요 문제점

### 문제 1: 공통 컴포넌트 활용 부재로 인한 대규모 코드 중복

`Common/ui/` 폴더에 Button, Input, Spinner, EmptyState가 정의되어 있으나, 실제 도메인 컴포넌트에서는 이를 사용하지 않고 각자 동일한 스타일의 컴포넌트를 재정의한다.

```js
// Common/ui/Button.js에 이미 정의된 Button이 있음에도
// LoginForm.js, MissingPetBoardPage.js, CommunityBoard.js, ActivityPage.js 등에서
// 각각 아래와 같이 독립적으로 Button을 재정의

const CreateButton = styled.button`  // MissingPetBoardPage
  border: none;
  background: ${(props) => props.theme.colors.primary};
  color: #ffffff;
  border-radius: ${(props) => props.theme.borderRadius.md};
  ...
`;

const WriteButton = styled.button`  // CommunityBoard
  background: ${props => props.theme.colors.primary};
  color: white;
  ...
`;
```

**영향:** 버튼 스타일을 변경하려면 66개 파일 중 수십 곳을 각각 수정해야 한다.

---

### 문제 2: Chat 도메인의 theme 토큰 미사용

ChatRoom.js에서 font-size가 30회 이상, border-radius가 20회 이상 하드코딩으로 사용된다. 다크모드로 전환해도 채팅 UI의 일부 색상은 변경되지 않을 가능성이 있다.

```js
// ChatRoom.js - 전체가 하드코딩 스타일
// (예시 일부)
font-size: 14px;
font-size: 12px;
font-size: 20px;
border-radius: 20px;
border-radius: 8px;
border-radius: 50%;
border-radius: 3px;
```

**영향:** 다크모드에서 채팅 UI가 비정상적으로 렌더링될 수 있으며, 전체 폰트 스케일을 조정할 때 Chat만 반영되지 않는다.

---

### 문제 3: PetCoinChargePage의 잘못된 theme 접근 패턴

이 컴포넌트는 ThemeProvider의 Context를 통해 theme을 받는 것이 아니라, `useTheme()` 훅으로 theme을 가져온 뒤 각 styled 컴포넌트에 prop으로 직접 주입한다. 이는 styled-components의 ThemeProvider 사용 방식과 충돌한다.

```js
// PetCoinChargePage.js - 잘못된 패턴
const { theme } = useTheme();

// JSX에서 theme prop 주입
<ModalContent onClick={(e) => e.stopPropagation()} theme={theme}>

// 또한 #fee, #efe 같은 테마 외 색상 하드코딩
const ErrorMessage = styled.div`
  background: #fee;  // theme.colors.error 미사용
  color: #c00;
`;
const SuccessMessage = styled.div`
  background: #efe;  // theme.colors.success 미사용
`;
```

**영향:** 다크모드 전환 시 Payment 화면이 밝은 배경색 그대로 유지될 수 있다.

---

### 문제 4: 접근성 기준 미달 색상 대비

`textLight` 컬러(`#9E9E9E`)가 흰 배경에서 사용될 때 대비율이 약 2.85:1로, WCAG 2.1 AA 기준인 4.5:1에 크게 못 미친다.

```js
// theme.js
textLight: '#9E9E9E',  // 흰 배경(#FFFFFF) 대비율 ≈ 2.85:1 (AA 기준 4.5:1 미달)

// 사용 예시 (LoginForm.js)
&::placeholder {
  color: ${({ theme }) => theme.colors.textLight};  // 접근성 기준 미달
}

// Navigation.js
const NotificationTime = styled.div`
  color: ${props => props.theme.colors.textLight};  // 타임스탬프 텍스트
`;
```

**영향:** 저시력 사용자가 placeholder 텍스트, 타임스탬프, 보조 정보를 읽기 어렵다.

---

### 문제 5: typography 토큰 범위가 실제 UI를 커버하지 못함

theme.js의 h1 최대 크기가 `20px`이지만, 실제 UI에서는 `48px`(Hero 타이틀), `36px`(반응형 Hero), `32px`(코인 잔액), `24px`(모달 제목) 등이 토큰 외부에서 하드코딩으로 사용된다. 이로 인해 토큰 시스템이 형식적으로만 존재하며 실제 주요 UI 요소를 관리하지 못한다.

```js
// theme.js - typography 최대값
h1: { fontSize: '20px', fontWeight: '700' },

// 실제 사용 (모두 theme.typography 외부)
// HomePage.js
font-size: 48px;  // Hero Title
font-size: 20px;  // Hero Subtitle

// PetCoinChargePage.js
font-size: 24px;  // 모달 Title
font-size: 32px;  // 잔액 표시

// MissingPetBoardPage.js
font-size: 2rem;  // 페이지 Title
```

**영향:** 폰트 스케일 전체 조정이 불가능하며, 디자인 시스템으로서 typography 토큰의 의미가 사실상 무력화된다.

---

## 개선 우선순위

### 1순위: typography 토큰 확장 및 재정립 (난이도: 중, 영향도: 최고)
현재 토큰(h1=20px)과 실제 사용(Hero=48px)의 괴리를 해소한다. display1(48px), display2(36px), h1(28px), h2(24px), h3(20px), h4(18px), h5(16px), body1(15px), body2(14px), caption(12px), micro(10px)의 11단계로 확장하고, 모든 하드코딩 font-size를 토큰으로 교체한다.

### 2순위: 공통 컴포넌트 활성화 및 도메인별 재사용 강제 (난이도: 중, 영향도: 높음)
`Common/ui/Button`, `Common/ui/Input`을 실제로 활용하도록 도메인별 중복 정의를 제거한다. 추가로 `Modal`, `StatusBadge`, `LoadingState`, `ErrorBanner` 공통 컴포넌트를 신설한다.

### 3순위: Chat 도메인 theme 토큰 마이그레이션 (난이도: 중, 영향도: 높음)
ChatRoom.js의 하드코딩 font-size(30건), border-radius(20건)를 theme 토큰으로 교체하여 다크모드 정합성과 전체 스케일 일관성을 확보한다.

### 4순위: PetCoinChargePage theme 패턴 수정 (난이도: 낮음, 영향도: 높음)
`useTheme()`으로 얻은 theme을 prop으로 넘기는 잘못된 패턴을 제거하고, ThemeProvider의 자동 주입 방식으로 통일한다. `#fee`, `#efe` 하드코딩 색상을 `theme.colors.error`, `theme.colors.success` 토큰으로 교체한다.

### 5순위: 접근성 기반 강화 (난이도: 중~높음, 영향도: 중간)
- `textLight` 색상을 `#757575` 이상으로 밝기 조정하여 WCAG AA 4.5:1 달성
- 모든 드롭다운/모달에 `aria-expanded`, `aria-haspopup`, Escape 키 처리 추가
- 포커스 트랩을 모달 컴포넌트에 통합
- `focus-visible` 사용으로 마우스/키보드 포커스 스타일 분리

---

## 디자이너에게 전달할 핵심 인사이트

### 현재 디자인 언어 수준

Petory는 "당근마켓 스타일"을 모티프로 삼았으나, 실제 구현은 당근마켓의 세련된 디자인에는 아직 미치지 못한다. 핵심 브랜드 컬러(주황색 #FF7E36)는 일관되게 적용되고 있으며 이는 강점이다.

### 새로운 디자인 방향 제안을 위한 맥락

**1. 아이콘 시스템 도입이 최우선 과제**

현재 전체 UI의 아이콘이 100% 이모지(🏠, 🗺️, 🐾, 💬 등)에 의존한다. 이모지는 OS/브라우저/기기마다 외형이 다르고, 크기 조절과 색상 변경이 불가능하며, 다크모드에서 시각적 일관성을 해친다. Lucide React, Phosphor Icons 같은 SVG 아이콘 라이브러리 도입이 필요하다.

**2. 폰트 스케일이 너무 작고 좁음**

현재 body1(13px)과 caption(10px)은 모바일 기기에서 가독성이 낮다. 최소 body1을 14~15px, caption을 12px로 상향하고, 전체 타입 스케일을 재정의해야 한다.

**3. 공백(White Space)이 부족**

spacing 토큰의 최대값이 `xxl=30px`이다. 실제 현대적인 UI 디자인에서 섹션 간 공백은 48px~80px에 달한다. `section`(48px), `page`(64px), `hero`(80px) 등 대형 spacing 토큰 추가가 필요하다.

**4. borderRadius 확장 필요**

현재 최대 `xl=12px`이다. 현대적인 "소프트 UI" 트렌드에서 카드는 16px, 모달은 20px~24px, 버튼은 8~12px를 주로 사용한다. `2xl=16px`, `3xl=24px` 토큰이 필요하다.

**5. 도메인 색상 활용 강화 가능성**

theme.js에 `domain.location`(파란), `domain.meetup`(초록), `domain.care`(노랑) 세 가지 도메인 색상이 이미 정의되어 있다. 이를 지도 탭 이외의 영역(케어 목록, 모임 목록)에서도 일관되게 적용하면 도메인별 브랜딩이 강화된다.

**6. 미완성 UI 정리 필요**

- `StatsSection`이 주석 처리된 채 더미 텍스트("아직없음")를 표시하는 코드가 HomePage에 남아 있다.
- `PermissionDeniedModal`의 "당신 접근하면 안돼요!" 문구를 공식 문구로 교체해야 한다.
- 카테고리 아이콘 중 `📢`가 `정보공유`와 `공지` 두 카테고리에 중복 사용된다.

**7. 반응형 전략 통일**

현재 `600px`, `720px`, `768px`, `960px`, `1024px`로 분산된 브레이크포인트를 다음 3단계로 통일하는 것을 권장한다:
- mobile: `< 640px`
- tablet: `640px ~ 1024px`
- desktop: `> 1024px`

이를 theme.js에 `breakpoints` 토큰으로 추가하여 전체 컴포넌트에서 참조하도록 해야 한다.

**8. 디자인 시스템 성숙도 로드맵**

현재 Petory의 디자인 시스템은 "토큰 기반 초기 단계"로 평가된다. 다음 단계로 발전하기 위한 우선 과제:

1. 토큰 완성: typography 확장, spacing 확장, breakpoints 추가
2. 컴포넌트 라이브러리 완성: 공통 Button/Input/Modal/Badge/Card 등 10개 내외 원자 컴포넌트 활성화
3. 패턴 가이드: 폼 레이아웃, 목록 카드, 상태 표시 패턴의 표준 정의
4. 아이콘 시스템: SVG 아이콘 라이브러리 도입 및 이모지 교체

현재 코드베이스는 styled-components + ThemeProvider 구조를 올바르게 채택했으므로, 위 개선 작업의 기술적 기반은 이미 갖춰져 있다.
