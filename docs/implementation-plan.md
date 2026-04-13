# Petory 프론트엔드 디자인 개편 구현 계획

## 작성일
2026-04-13

## 분석 범위
- 대상: `frontend/src/` 전체 (54개 컴포넌트 파일)
- 스타일링 방식: styled-components 단일 사용
- 테마 구조: `styles/theme.js` → `ThemeContext.js` → `StyledThemeProvider`

---

## 1. 현재 스타일링 방식 완전 분석

### 1.1 아키텍처 구조

```
styles/theme.js
  └─ lightTheme / darkTheme 객체 export
       └─ contexts/ThemeContext.js
            └─ StyledThemeProvider (styled-components ThemeProvider 래핑)
                 └─ 모든 컴포넌트에서 props.theme.* 접근
```

### 1.2 theme 토큰 사용 현황

**정상 사용 (props.theme 방식):**
```js
// 예: Navigation.js
const NavContainer = styled.nav`
  background: ${props => props.theme.colors.background};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;
```

**혼재 패턴 (useTheme 직접 사용):**
```js
// Payment 도메인 3개 파일
const { theme } = useTheme();
<div style={{ color: theme.colors.primary }}>
```

**토큰 미사용 (하드코딩):**
```js
// CommunityBoard.js - 카테고리 색상
const CATEGORY_COLORS = {
  '자유': '#6366F1',
  '질문': '#EC4899',
  '정보': '#10B981',
  // ...8종
};

// LoginForm.js - OAuth 버튼
background: #4285F4;  // Google
background: #03C75A;  // Naver

// ChatRoom.js - 말풍선
background: #E8E8E8;
```

---

## 2. 컴포넌트 인벤토리

### Common/ui (공통 컴포넌트 - 실제 활용도 낮음)

| 컴포넌트 | 파일 | theme 사용 | 실제 사용 여부 | 수정 난이도 |
|---------|------|-----------|--------------|------------|
| Button | `Common/ui/Button.js` | O | **거의 미사용** | Low |
| Input | `Common/ui/Input.js` | O | **거의 미사용** | Low |
| Spinner | `Common/ui/Spinner.js` | O | 일부 사용 | Low |
| EmptyState | `Common/ui/EmptyState.js` | O | 일부 사용 | Low |

> **핵심 문제:** Common/ui 컴포넌트가 정의되어 있지만, 각 도메인에서 동일 기능을 중복으로 직접 구현하고 있음.

### Layout

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| Navigation | `Layout/Navigation.js` | O | Low |

### Auth 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| LoginForm | `Auth/LoginForm.js` | 부분 | Medium (OAuth 하드코딩) |
| RegisterForm | `Auth/RegisterForm.js` | O | Low |

### Home 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| Home 관련 | `Home/*.js` | O | Low |

### CareRequest 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| CareRequest 관련 | `CareRequest/*.js` | O | Low-Medium |

### Chat 도메인 (주의 필요)

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| ChatRoom | `Chat/ChatRoom.js` | **미사용** | **High** |
| 기타 Chat | `Chat/*.js` | 부분 | Medium |

> Chat 도메인은 font-size, border-radius 등 30개 이상 하드코딩. 별도 집중 작업 필요.

### Community 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| CommunityBoard | `Community/CommunityBoard.js` | 부분 | Medium (카테고리 색상) |
| 기타 Community | `Community/*.js` | O | Low |

### Meetup 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| Meetup 관련 | `Meetup/*.js` | O | Low |

### Payment 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| Payment 관련 | `Payment/*.js` | useTheme 혼재 | Medium |

### MissingPet 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| MissingPet 관련 | `MissingPet/*.js` | O | Low |

### UnifiedMap 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| UnifiedMap 관련 | `UnifiedMap/*.js` | O | Low-Medium |

### Admin 도메인

| 컴포넌트 | 파일 | theme 사용 | 수정 난이도 |
|---------|------|-----------|------------|
| SystemDashboardSection | `Admin/SystemDashboardSection.js` | 부분 | Medium (recharts 색상) |
| 기타 Admin | `Admin/*.js` | O | Low |

---

## 3. 의존성 맵

### theme.js를 직접 영향받는 파일 분류

```
theme.js 변경
  ├─ 자동 반영 (props.theme 사용) ← 전체 50개 파일 중 약 40개
  ├─ 수동 수정 필요 (useTheme 직접) ← Payment 3개 파일
  └─ 영향 없음 (하드코딩) ← Chat, 일부 Community, LoginForm
```

### 변경 파급 경로

```
styles/theme.js
  → contexts/ThemeContext.js (변경 불필요)
    → App.js (변경 불필요)
      → 모든 컴포넌트 자동 반영
```

---

## 4. 구현 전략 (Phase별)

### Phase 1: theme.js 토큰 개편 (핵심 - 2~3시간)

**목표:** 새 디자인 스펙의 색상/타이포/간격 토큰으로 교체

**작업 내용:**
```js
// 변경 전 (현재)
export const lightTheme = {
  colors: {
    primary: '#FF7E36',  // 당근 주황
    // ...
  },
  spacing: { xs: '4px', sm: '8px', md: '12px', lg: '16px', xl: '20px', xxl: '30px' },
  borderRadius: { sm: '4px', md: '6px', lg: '8px', xl: '12px', full: '50%' },
  typography: { /* h1: 20px 등 실제와 괴리 */ }
}

// 변경 후 (새 디자인 스펙 반영)
export const lightTheme = {
  colors: { /* 새 브랜드 컬러 */ },
  spacing: { /* 8pt 그리드 */ },
  borderRadius: { /* 새 기준 */ },
  typography: { /* 실제 사용 범위 커버 */ }
}
```

**영향 범위:** 토큰 키 이름 유지 시 → 자동 반영 (40개 파일 무수정)
**리스크:** 토큰 키 이름 변경 시 → 전체 파일 수정 필요

> **권장:** 기존 키 이름을 최대한 유지하고 값만 교체. 새 키 추가는 자유롭게.

**darkTheme도 동시 업데이트 필수** (라이트/다크 쌍으로 관리)

---

### Phase 2: Common/ui 컴포넌트 강화 (3~4시간)

**목표:** 실제 사용되지 않는 공통 컴포넌트를 디자인 스펙에 맞게 강화하고, 도메인 중복 코드 교체

**추가/강화할 컴포넌트:**
```
Common/ui/
  Button.js     - variant(primary/secondary/ghost/danger) × size(sm/md/lg)
  Input.js      - state(default/focus/error/success/disabled)
  Card.js       - elevation(flat/raised/floating/modal) [신규]
  Badge.js      - 카테고리별 컬러 코딩 [신규]
  Avatar.js     - 크기 시스템 [신규]
  Modal.js      - 공통 모달 [신규]
  Divider.js    - 구분선 [신규]
```

**도메인 교체 우선순위:**
1. Auth 도메인 (LoginForm, RegisterForm) - Button, Input 교체
2. Home 도메인 - Card 교체
3. Meetup, CareRequest - Card, Button, Badge 교체
4. Community - Badge(카테고리) 교체
5. Admin - 대시보드 컴포넌트

---

### Phase 3: 하드코딩 제거 (2~3시간)

**대상 파일 및 작업:**

#### ChatRoom.js (High 난이도)
```js
// 제거 대상 (추정 30개 이상)
font-size: 14px → ${props => props.theme.typography.body}
border-radius: 18px → ${props => props.theme.borderRadius.xl}
background: #E8E8E8 → ${props => props.theme.colors.surfaceSoft}
```

#### CommunityBoard.js
```js
// theme에 카테고리 색상 토큰 추가
const CATEGORY_COLORS = {
  '자유': props.theme.colors.category.free,
  '질문': props.theme.colors.category.question,
  // ...
}
```

#### LoginForm.js
```js
// OAuth 브랜드 컬러는 theme에 추가 (브랜드 정책상 변경 불가)
oauth: {
  google: '#4285F4',
  naver: '#03C75A',
  kakao: '#FEE500',
}
```

#### SystemDashboardSection.js (recharts)
```js
// theme.chart 토큰 실제 연결
const chartColors = theme.chart; // 기존 토큰 활용
```

---

### Phase 4: Payment 도메인 패턴 통일 (1시간)

```js
// 현재 혼재 패턴 → props.theme 방식으로 통일
// Before
const { theme } = useTheme();
<div style={{ color: theme.colors.primary }}>

// After
const PayDiv = styled.div`
  color: ${props => props.theme.colors.primary};
`;
```

---

## 5. 위험 요소 & 주의사항

### 5.1 ThemeProvider Flicker
```js
// App.js에서 body 직접 조작 + GlobalStyle 이중 처리
// → 테마 전환 시 깜빡임 가능
// 해결: CSS custom properties로 전환 또는 GlobalStyle 단일화
```

### 5.2 TypeScript 부재
- 토큰 키 오타를 컴파일 단계에서 감지 불가
- theme.js 변경 후 반드시 브라우저 전체 화면 확인 필요
- 추후 JSDoc 타입 정의 추가 권장

### 5.3 recharts 별도 처리
- recharts는 props.theme 접근 불가 → useTheme() 또는 chart props로 직접 전달
- `chart` 토큰 배열을 컴포넌트에 props로 명시 전달

### 5.4 다크모드 동기화
- lightTheme 수정 시 darkTheme 반드시 동시 수정
- darkTheme의 일부 컬러가 lightTheme와 동일한 경우 있음 → 검증 필요

### 5.5 PetCoin/에스크로 관련 페이지
- `PetCoinChargePage`가 ThemeProvider 우회 패턴 사용
- 수정 시 실제 동작 테스트 필수

---

## 6. 추천 마이그레이션 방법

### CSS Custom Properties 도입 (선택적)

현재 styled-components 방식을 유지하면서, 전역 CSS 변수를 병행 도입:

```js
// GlobalStyle에 추가
const GlobalStyle = createGlobalStyle`
  :root {
    --color-primary: ${({ theme }) => theme.colors.primary};
    --color-background: ${({ theme }) => theme.colors.background};
    /* ... */
  }
`;
```

**장점:** 테마 전환 시 flicker 해소, 일반 CSS에서도 토큰 사용 가능
**단점:** 중복 관리 포인트 추가

### 점진적 마이그레이션 권장

일괄 전환보다 **Phase 순서대로 단계별 배포** 권장:
- Phase 1 완료 → 브라우저 전체 확인
- Phase 2 완료 → 각 도메인 화면 확인
- Phase 3, 4 → 완료 후 회귀 테스트

---

## 7. 작업 체크리스트

### Phase 1: theme.js 개편
- [ ] 새 디자인 스펙 확인 (`docs/design-spec.md`)
- [ ] lightTheme 색상 토큰 교체
- [ ] darkTheme 색상 토큰 교체
- [ ] typography 토큰 확장 (실제 사용 범위 커버)
- [ ] spacing 8pt 그리드 재정의
- [ ] borderRadius 재정의
- [ ] 도메인 색상 토큰 추가 (care/meetup/location/missingPet/community)
- [ ] 카테고리 색상 토큰 추가
- [ ] OAuth 브랜드 색상 토큰 추가

### Phase 2: Common/ui 강화
- [ ] Button.js - variant × size × state 매트릭스 구현
- [ ] Input.js - 상태별 스타일 강화
- [ ] Card.js - elevation 시스템 신규 구현
- [ ] Badge.js - 카테고리 컬러 코딩 신규 구현
- [ ] Avatar.js - 크기 시스템 신규 구현
- [ ] Auth 도메인 - 공통 컴포넌트 교체
- [ ] Home 도메인 - Card 교체
- [ ] Meetup/CareRequest - 공통 컴포넌트 교체

### Phase 3: 하드코딩 제거
- [ ] ChatRoom.js - 전체 하드코딩 제거 (30개+)
- [ ] CommunityBoard.js - 카테고리 색상 토큰화
- [ ] LoginForm.js - OAuth 색상 토큰 이동
- [ ] SystemDashboardSection.js - recharts theme.chart 연결

### Phase 4: 패턴 통일
- [ ] Payment 도메인 - useTheme → props.theme 방식 통일
- [ ] PetCoinChargePage - ThemeProvider 우회 패턴 수정

### 최종 검증
- [ ] 라이트모드 전체 화면 확인
- [ ] 다크모드 전체 화면 확인
- [ ] 모바일 반응형 확인 (360px)
- [ ] 태블릿 반응형 확인 (768px)
- [ ] 접근성 색상 대비 확인 (WCAG AA 4.5:1)

---

## 8. 예상 작업 규모 요약

| Phase | 대상 | 예상 수정 파일 수 | 난이도 |
|-------|------|-----------------|--------|
| Phase 1 | theme.js (1개) | 1 | Low |
| Phase 2 | Common/ui + 도메인 교체 | 20~25 | Medium |
| Phase 3 | 하드코딩 파일 | 4~6 | Medium-High |
| Phase 4 | Payment 패턴 | 3~4 | Low |
| **합계** | | **약 28~36개** | |

---

*본 계획은 `docs/design-spec.md` 완성 후 구체적 수치로 업데이트 필요*
