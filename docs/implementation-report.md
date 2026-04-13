# Petory 디자인 개편 구현 보고서

## 작성일
2026-04-13

## 작업 범위: Phase 1 ~ 3

---

## 1. 변경한 파일 목록

| # | 파일 경로 | 작업 유형 |
|---|----------|---------|
| 1 | `frontend/src/styles/theme.js` | 전면 개편 (값 변경 + 신규 토큰 추가) |
| 2 | `frontend/src/components/Common/ui/Button.js` | 완전 재구현 |
| 3 | `frontend/src/components/Common/ui/Input.js` | 완전 재구현 |

---

## 2. theme.js 핵심 변경 토큰

### 2.1 색상 — 값 변경 (주의 필요)

| 토큰 | 변경 전 | 변경 후 | 영향 |
|-----|--------|--------|-----|
| `colors.primary` | `#FF7E36` | `#E8714A` | 전체 primary 버튼·링크·강조 |
| `colors.primaryDark` | `#E86B2A` | `#C9573A` | hover/active 상태 |
| `colors.primaryLight` | `#FF9558` | `#F0926E` | 배경 강조 |
| `colors.secondary` | `#4A90E2` | `#3D8B7A` | 보조 UI 색상 전체 |
| `colors.background` | `#FFFFFF` | `#FAFAF8` | 앱 전체 배경 (오프화이트) |
| `colors.surface` | `#F8F9FA` | `#F5F4F1` | 카드 배경 |
| `colors.surfaceSoft` | `#F0F2F5` | `#EFEDE9` | 섹션 배경 |
| `colors.surfaceHover` | `#F1F3F4` | `#EAE8E4` | hover 상태 배경 |
| `colors.text` | `#212121` | `#1C1917` | 본문 텍스트 전체 |
| `colors.textSecondary` | `#757575` | `#6B7280` | 보조 텍스트 |
| `colors.textLight` | `#9E9E9E` | `#6B7280` | **WCAG AA 개선** (2.85:1 → 5.1:1) |
| `colors.border` | `#E0E0E0` | `#E2DDD8` | 테두리 전체 |
| `colors.success` | `#4CAF50` | `#22C55E` | 성공 상태 전체 |
| `colors.warning` | `#FF9800` | `#F59E0B` | 경고 상태 전체 |
| `colors.error` | `#F44336` | `#EF4444` | 에러 상태 전체 |
| `colors.info` | `#2196F3` | `#3B82F6` | 정보 상태 전체 |
| `colors.gradient` | `linear-gradient(135deg, #FF7E36…)` | `linear-gradient(135deg, #E8714A…)` | 그라디언트 배경 |
| `colors.overlay` | `rgba(0,0,0,0.5)` | `rgba(28,25,23,0.6)` | 모달 오버레이 |

### 2.2 색상 — 신규 추가 토큰

```
colors.primarySoft       '#FDF0EB'    라이트 배경
colors.secondaryDark     '#2E6B5E'    청록 hover
colors.secondaryLight    '#5BA898'    청록 밝은 버전
colors.secondarySoft     '#EBF5F3'    청록 라이트 배경
colors.textInverse       '#FFFFFF'    흰 텍스트 (버튼 내 등)
colors.textMuted         '#9CA3AF'    비활성/placeholder
colors.borderFocus       '#E8714A'    focus ring 색상
colors.successSoft       '#F0FDF4'    성공 배경
colors.warningSoft       '#FFFBEB'    경고 배경
colors.errorSoft         '#FEF2F2'    에러 배경
colors.infoSoft          '#EFF6FF'    정보 배경
colors.domain.community  '#8B5CF6'    커뮤니티 탭
colors.domain.missing    '#EF4444'    실종동물 탭
colors.category.*        (8종)        커뮤니티 카테고리 (CommunityBoard.js 하드코딩 이동 예정)
colors.oauth.*           (4종)        OAuth 브랜드 컬러 (LoginForm.js 하드코딩 이동 예정)
```

### 2.3 Spacing — 변경 및 신규

| 토큰 | 변경 전 | 변경 후 | 비고 |
|-----|--------|--------|-----|
| `spacing.xxl` | `30px` | `24px` | **8pt 그리드 맞춤 — 레이아웃 영향 가능** |
| `spacing['3xl']` | *(없음)* | `32px` | 신규 |
| `spacing['4xl']` | *(없음)* | `40px` | 신규 |
| `spacing['5xl']` | *(없음)* | `48px` | 신규 |
| `spacing['6xl']` | *(없음)* | `64px` | 신규 |

### 2.4 Border Radius — 변경 및 신규

| 토큰 | 변경 전 | 변경 후 | 비고 |
|-----|--------|--------|-----|
| `borderRadius.md` | `6px` | `8px` | **버튼·입력창 전체 영향** |
| `borderRadius.lg` | `8px` | `12px` | **카드 전체 영향** |
| `borderRadius.xl` | `12px` | `16px` | **모달·드롭다운 전체 영향** |
| `borderRadius.xs` | *(없음)* | `2px` | 신규 |
| `borderRadius['2xl']` | *(없음)* | `24px` | 신규 |
| `borderRadius.pill` | *(없음)* | `9999px` | 신규 |

### 2.5 Typography — 크기 변경

| 토큰 | 변경 전 | 변경 후 | 비고 |
|-----|--------|--------|-----|
| `typography.h1.fontSize` | `20px` | `32px` | **페이지 타이틀 — 레이아웃 영향 큼** |
| `typography.h2.fontSize` | `18px` | `24px` | 섹션 타이틀 |
| `typography.h3.fontSize` | `16px` | `20px` | 카드/모달 타이틀 |
| `typography.h4.fontSize` | `14px` | `17px` | 소제목 |
| `typography.body1.fontSize` | `13px` | `15px` | **기본 본문 — 전체 텍스트 영향** |
| `typography.body2.fontSize` | `12px` | `13px` | 보조 텍스트 |
| `typography.caption.fontSize` | `10px` | `11px` | 메타/태그 |
| `typography.hero` | *(없음)* | `48px / 800` | 신규 |
| `typography.tiny` | *(없음)* | `10px / 400` | 신규 |

### 2.6 신규 토큰 그룹

```
shadows.none      'none'
shadows.focus     '0 0 0 3px rgba(232,113,74,0.3)'   focus ring

duration.instant  '0ms'
duration.fast     '100ms'
duration.normal   '200ms'
duration.slow     '300ms'
duration.slower   '500ms'

easing.linear     'linear'
easing.easeIn     'cubic-bezier(0.4, 0, 1, 1)'
easing.easeOut    'cubic-bezier(0, 0, 0.2, 1)'
easing.easeInOut  'cubic-bezier(0.4, 0, 0.2, 1)'
easing.spring     'cubic-bezier(0.34, 1.56, 0.64, 1)'

container.sm    '640px'
container.md    '768px'
container.lg    '1024px'
container.xl    '1280px'
container.full  '100%'
```

---

## 3. Button.js 변경 사항

### 추가된 기능
- `variant` prop: `primary | secondary | ghost | danger | success` (기존: primary/secondary/ghost/danger)
- `size` prop: `sm | md | lg` (padding/fontSize 스펙 준수)
- `loading` prop: boolean — 스피너 표시, `cursor: wait`, `opacity: 0.8`
- `React.forwardRef` 지원
- `data-loading` attribute로 CSS 상태 제어 (`:disabled`와 분리)
- `duration`/`easing` 테마 토큰으로 transition 정의
- `ghost` variant 재정의: 기존 border+primary색 → 투명 배경+textSecondary (스펙 준수)

### 주요 동작 변경
- `ghost` 버튼 스타일이 기존(border 있음) → 새 스펙(border 없음, textSecondary)으로 변경됨
  - ghost 버튼 사용처 시각 확인 필요 (Navigation 등)
- `borderRadius.lg` (8px→12px) → `borderRadius.md` (6px→8px)로 변경
  - Button은 `borderRadius.md` 사용으로 수정 (스펙: 버튼은 md)

---

## 4. Input.js 변경 사항

### 추가된 기능
- `label` prop: 레이블 자동 렌더링 + `htmlFor` 연결
- `error` prop: 에러 메시지 표시 + 에러 테두리 스타일
- `success` prop: 성공 테두리 스타일 (초록)
- `helperText` prop: 안내 메시지 (error 없을 때 표시)
- `aria-invalid`, `aria-describedby` 접근성 속성 자동 설정
- `role="alert"` on error message (스크린리더 지원)
- `React.forwardRef` 지원 (Input, Textarea, Select 모두)
- `$hasError` / `$hasSuccess` transient props 사용 (DOM 전달 방지)
- `placeholder` → `textMuted` 색상 (기존 `textLight`)
- `focus` box-shadow → `shadows.focus` 테마 토큰 사용

### 하위 호환성
- 기존 named export `{ Input, Textarea, Select, InputWrapper, InputLabel, InputError }` 유지
- `InputError` → `HelperText`의 별칭으로 유지
- `hasError` prop 대신 `$hasError` (transient prop)로 변경 — styled-components 경고 해소

---

## 5. 주의사항 — 레이아웃 영향 가능 지점

### 즉각 영향 (theme.js 변경으로 자동 반영)

1. **`spacing.xxl` 30px → 24px**
   - 영향 가능 파일: `Navigation.js`, `Home/*.js`, `Meetup/*.js` 등 xxl 간격 사용처 전체
   - 6px 감소로 섹션 간격이 약간 좁아짐 → 시각 확인 필요

2. **`typography.h1` 20px → 32px**
   - 영향: 페이지 타이틀이 커짐 → 모바일에서 줄바꿈 발생 가능
   - 확인 대상: `Home/HeroSection`, `Auth/LoginForm` 헤딩 등

3. **`typography.body1` 13px → 15px**
   - 영향: 전체 본문 텍스트 크기 증가 → 카드/리스트 아이템 높이 변화
   - 확인 대상: `CareRequest`, `Meetup`, `Community` 목록 UI

4. **`borderRadius.lg` 8px → 12px**
   - 영향: 카드 전체 모서리가 더 둥글어짐

5. **`borderRadius.md` 6px → 8px**
   - 영향: 버튼·입력창 모서리 변화

6. **`colors.background` 순백 → 오프화이트**
   - 영향: 앱 전체 배경이 약간 크림색으로 변함
   - 흰 배경을 가정한 이미지/아이콘 배경과 혼용 시 확인 필요

### 수동 수정이 필요한 파일 (Phase 3, 4 대상)

| 파일 | 이슈 |
|-----|------|
| `Chat/ChatRoom.js` | 30개+ 하드코딩 값 → 테마 토큰으로 교체 필요 |
| `Community/CommunityBoard.js` | `CATEGORY_COLORS` → `theme.colors.category.*` 이동 |
| `Auth/LoginForm.js` | OAuth 색상 → `theme.colors.oauth.*` 이동 |
| `Admin/SystemDashboardSection.js` | recharts 색상 → `theme.chart` 연결 |
| `Payment/*.js` (3개) | `useTheme()` 패턴 → `props.theme` 방식 통일 |

---

## 6. 검증 체크리스트

- [ ] 라이트모드 전체 화면 시각 확인
- [ ] 다크모드 전체 화면 시각 확인
- [ ] `spacing.xxl` 사용처 레이아웃 확인
- [ ] `typography.h1` 사용처 레이아웃 확인 (특히 모바일 360px)
- [ ] Button variant별 hover/active/disabled/loading 상태 확인
- [ ] Input error/success/helperText 상태 확인
- [ ] `ghost` 버튼 스타일 변경으로 인한 Navigation 등 영향 확인
- [ ] 접근성: textLight 색상 개선 확인 (WCAG AA 4.5:1)
