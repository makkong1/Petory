# 나머지 페이지 리디자인 + 반응형(모바일/웹) 스펙

## 목표

- Auth, Community, MissingPet, Map, Activity 페이지에 Figma 스타일(테라코타 테마, 글래스모피즘 카드, 둥근 radius) 적용
- 웹(≥768px): 좌측 사이드바 네비 + 콘텐츠 영역 확장 (하이브리드)
- 모바일(<768px): 현재 방식 유지 — 하단 탭바, 430px 컨테이너
- 단일 Figma 소스 활용 (별도 웹 Figma 디자인 없음 — 컴포넌트 스타일은 Figma 기준, 웹 레이아웃은 Figma 스타일로 확장)

## 반응형 전략 (Hybrid Option C)

```
모바일 (<768px)              웹 (≥768px)
────────────────────         ────────────────────────────────
┌──────────────────┐         ┌──────────┬─────────────────┐
│  콘텐츠 영역      │         │  사이드바 │  콘텐츠 영역     │
│  (max-width 430) │         │  (240px) │  (flex: 1)      │
│                  │         │  아이콘+  │  max-width 1200 │
│                  │         │  라벨     │  카드 그리드     │
├──────────────────┤         │          │                 │
│  하단 탭바(60px)  │         └──────────┴─────────────────┘
└──────────────────┘
```

### 공통 CSS 패턴

각 컴포넌트에서 통일된 미디어쿼리 사용:

```js
// 모바일 우선, 웹에서 확장
@media (min-width: 768px) {
  /* 웹 전용 스타일 */
}
```

## 변경 범위 (파일별)

### 0. Navigation.js + App.js (선행 작업)

**Navigation.js** — 웹 nav 전환: top bar → left sidebar

- 현재: `position: fixed; top: 0; height: 60px; flex-direction: row` (top header)
- 변경: `@media (min-width: 768px)` 에서 `position: fixed; left: 0; top: 0; width: 240px; height: 100vh; flex-direction: column`
- 로고 상단, 메뉴 아이템 세로 스택, 하단에 프로필/설정
- 메뉴 아이템: 아이콘 + 라벨 세로 배치, active 시 primary 색상 하이라이트

**App.js** — ContentArea 좌측 여백 추가

- 현재: `margin-top: 60px` (top bar 보정)
- 변경: 웹에서 `margin-left: 240px; margin-top: 0`

### 1. Auth 페이지 (LoginForm.js, RegisterForm.js)

**디자인 방향**

- 모바일: 전체 화면 그라디언트 배경 + 중앙 글래스 카드 (현재 스타일 개선)
- 웹: 동일한 중앙 카드 (max-width: 440px) — 비율이 맞아서 웹에서도 자연스러움

**변경 사항**

- 배경: `theme.colors.gradient` 그라디언트 or 소프트 패턴 배경
- 카드: `backdrop-filter: blur(20px)`, `background: rgba(255,255,255,0.85)` 다크모드는 `rgba(29,29,29,0.85)`
- 입력창: pill shape (`border-radius: theme.borderRadius.pill`), 하단 border only → full border
- 버튼: `theme.colors.primary` 그라디언트, full-width, pill shape
- 비밀번호 찾기: 현재 인라인 모달 유지 (이미 구현됨), 스타일만 개선
- 소셜 로그인 버튼: 아이콘 + 라벨, 구분선 "또는"

**불변**

- `useAuth` hook, 폼 제출 로직, `onSwitchToRegister` prop

### 2. CommunityBoard.js

**디자인 방향**

- 모바일: 카드 리스트 (세로 스크롤), 글래스 스타일 카드
- 웹: 2열 카드 그리드

**변경 사항**

- 카드: `border-radius: 16px`, 그림자 `theme.colors.shadow`, hover lift 효과
- 카테고리 탭: pill 스타일 (HomePage CategoryTabs와 동일 패턴)
- 글쓰기 버튼: FAB(Floating Action Button) → 모바일 우측 하단 고정
- 웹: 게시글 카드 `display: grid; grid-template-columns: 1fr 1fr; gap: 16px`

**불변**

- 기존 게시글 CRUD 로직, 모달, 페이지네이션

### 3. MissingPetBoardPage.js

**디자인 방향**

- CommunityBoard와 동일한 패턴 (카드 리스트 → 웹 2열 그리드)
- 실종 동물 카드: 반려동물 이미지 + 이름, 견종, 실종 날짜, 위치 칩

**변경 사항**

- 카드: 이미지 상단 (aspect-ratio 4:3), 글래스 정보 패널
- 상태 배지: `실종중` (red), `발견됨` (green) pill badge
- 신고 버튼: FAB 스타일 (커뮤니티와 동일)
- 웹: 3열 그리드 (`grid-template-columns: repeat(3, 1fr)`)

**불변**

- 지도 선택기(MiniMapPicker), 상세 페이지, 폼 로직

### 4. UnifiedPetMapPage.js

**디자인 방향**

- 지도가 메인 콘텐츠 — 레이아웃 변경 최소화
- UI 컨트롤 패널만 Figma 스타일로 개선

**변경 사항**

- 탭 헤더(DomainTabHeader): pill 스타일 탭, 글래스 패널 배경
- 반경 필터(RadiusFilter): 슬라이더 + pill 칩 선택 UI
- 정보 패널(BaseInfoPanel): 글래스모피즘 카드 스타일
- 웹: 지도 풀스크린 + 좌측 컨트롤 패널 오버레이 (현재와 유사)

**불변**

- 지도 렌더링 로직, Kakao Map API 연동, 레이어/컨트롤 로직

### 5. ActivityPage.js

**디자인 방향**

- 모바일: 단일 컬럼 피드 (타임라인 스타일)
- 웹: max-width 680px 중앙 정렬 (트위터/인스타그램 피드 느낌)

**변경 사항**

- 활동 아이템 카드: 왼쪽 아이콘 + 오른쪽 텍스트/메타, 글래스 배경
- 타임라인 구분: 날짜별 섹션 헤더 (pill 라벨)
- 웹: `max-width: 680px; margin: 0 auto` (양쪽 여백)

**불변**

- 활동 조회 로직, 페이지네이션

## 공통 디자인 토큰 (기존 theme.js 기준)

| 역할 | 값 |
|------|-----|
| 카드 radius | `theme.borderRadius['2xl']` (24px) |
| 카드 그림자 | `0 4px 20px ${theme.colors.shadow}` |
| 글래스 배경 | `backdrop-filter: blur(12px); background: rgba(255,255,255,0.85)` |
| 활성 색상 | `theme.colors.primary` (#E8714A) |
| FAB | `theme.colors.primary`, 56px, 우측 하단 고정 |
| 웹 사이드바 폭 | `240px` |
| 웹 콘텐츠 max-width | `1200px` |

## 구현 순서 (서브에이전트 투입)

```
Phase 0 (선행 — 직렬):
  Navigation.js + App.js (사이드바 전환)

Phase 1 (병렬):
  LoginForm.js + RegisterForm.js

Phase 2 (병렬):
  CommunityBoard.js  /  MissingPetBoardPage.js

Phase 3 (직렬):
  UnifiedPetMapPage.js

Phase 4 (직렬):
  ActivityPage.js
```

## 제약 조건

- 모든 비즈니스 로직, API 연동, prop 인터페이스 불변
- `theme.js` 수정 없음 — 기존 토큰만 사용
- `styled-components` 패턴 유지 (CSS-in-JS)
- 새 파일 생성 없음 — 기존 파일 내에서 styled-components 수정
- `App.js` 탭 라우팅 불변 (`setActiveTab`, `renderContent` 구조 유지)
