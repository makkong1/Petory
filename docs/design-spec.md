# Petory 새 디자인 시스템 스펙

## 작성일
2026-04-13

## 설계 배경

평가 보고서(`docs/design-evaluation.md`) 기준 종합 점수 **2.8/5**. 핵심 문제:
1. typography 토큰 최대값(h1=20px)이 실제 UI(Hero=48px)와 괴리
2. spacing 토큰 6단계(4px~30px)에 없는 값(24, 32, 40, 48px)이 난무
3. `textLight(#9E9E9E)` 흰 배경 대비율 2.85:1 → WCAG AA 미달
4. 브레이크포인트 혼재 (768/960/1024px)
5. 공통 컴포넌트(Button/Input) 미사용 → 도메인별 중복 구현

---

## 1. 브랜드 컬러 리디자인

### 기본 방향
기존 '당근마켓 주황(#FF7E36)' 정체성에서 벗어나,  
**반려동물 앱 특유의 따뜻하고 신뢰감 있는 톤**으로 전환.

- Primary: 따뜻한 테라코타 계열 (자연/생명감)
- Secondary: 차분한 청록 계열 (신뢰/안정)
- Accent: 밝은 앰버 (주의/강조)
- Neutral: 따뜻한 그레이 (차가운 회색 대신)

---

## 2. 색상 팔레트 (라이트 모드)

### 2.1 Primary — 테라코타 피치

| 토큰명 | 현재 값 | 새 값 | 비고 |
|-------|--------|-------|-----|
| `primary` | `#FF7E36` | `#E8714A` | 더 차분한 테라코타 |
| `primaryDark` | `#E86B2A` | `#C9573A` | hover/active |
| `primaryLight` | `#FF9558` | `#F0926E` | 배경 강조용 |
| `primarySoft` | *(없음)* | `#FDF0EB` | 라이트 배경 (신규) |

### 2.2 Secondary — 청록 슬레이트

| 토큰명 | 현재 값 | 새 값 | 비고 |
|-------|--------|-------|-----|
| `secondary` | `#4A90E2` | `#3D8B7A` | 청록 (신뢰감) |
| `secondaryDark` | *(없음)* | `#2E6B5E` | hover |
| `secondaryLight` | *(없음)* | `#5BA898` | 밝은 버전 |
| `secondarySoft` | *(없음)* | `#EBF5F3` | 라이트 배경 (신규) |

### 2.3 Neutral — 따뜻한 그레이 스케일

| 토큰명 | 현재 값 | 새 값 | 비고 |
|-------|--------|-------|-----|
| `background` | `#FFFFFF` | `#FAFAF8` | 순백 대신 따뜻한 오프화이트 |
| `surface` | `#F8F9FA` | `#F5F4F1` | 카드 배경 |
| `surfaceSoft` | `#F0F2F5` | `#EFEDE9` | 섹션 배경 |
| `surfaceElevated` | `#F8F9FA` | `#FFFFFF` | 모달/팝업 (순백 유지) |
| `surfaceHover` | `#F1F3F4` | `#EAE8E4` | hover 배경 |

### 2.4 텍스트 색상 (WCAG AA 대비율 보장)

| 토큰명 | 현재 값 | 새 값 | 대비율(흰 배경 기준) |
|-------|--------|-------|-------------------|
| `text` | `#212121` | `#1C1917` | 18.1:1 ✅ |
| `textSecondary` | `#757575` | `#6B7280` | 5.1:1 ✅ |
| `textLight` | `#9E9E9E` | **`#6B7280`** | **5.1:1 ✅ (기존 2.85:1 → 개선)** |
| `textInverse` | *(없음)* | `#FFFFFF` | 흰 텍스트 (신규) |
| `textMuted` | *(없음)* | `#9CA3AF` | 비활성, placeholder (신규) |

> ⚠️ `textLight` 변경이 핵심: 기존 `#9E9E9E`는 WCAG AA 미달. `#6B7280`으로 통일.

### 2.5 Border

| 토큰명 | 현재 값 | 새 값 |
|-------|--------|-------|
| `border` | `#E0E0E0` | `#E2DDD8` |
| `borderLight` | `#F5F5F5` | `#F0EDE8` |
| `borderDark` | `#BDBDBD` | `#B5AFA8` |
| `borderFocus` | *(없음)* | `#E8714A` | focus ring (신규) |

### 2.6 시맨틱 색상

| 토큰명 | 현재 값 | 새 값 | 비고 |
|-------|--------|-------|-----|
| `success` | `#4CAF50` | `#22C55E` | 더 생동감 있는 그린 |
| `successDark` | `#388E3C` | `#16A34A` | |
| `successSoft` | *(없음)* | `#F0FDF4` | 배경용 (신규) |
| `warning` | `#FF9800` | `#F59E0B` | 앰버 계열 |
| `warningDark` | `#F57C00` | `#D97706` | |
| `warningSoft` | *(없음)* | `#FFFBEB` | 배경용 (신규) |
| `error` | `#F44336` | `#EF4444` | |
| `errorDark` | `#D32F2F` | `#DC2626` | |
| `errorSoft` | *(없음)* | `#FEF2F2` | 배경용 (신규) |
| `info` | `#2196F3` | `#3B82F6` | |
| `infoDark` | `#1565C0` | `#2563EB` | |
| `infoSoft` | *(없음)* | `#EFF6FF` | 배경용 (신규) |

### 2.7 도메인 색상 (탐색 탭)

| 도메인 | 현재 값 | 새 값 | 의미 |
|-------|--------|-------|-----|
| `domain.location` | `#4A90D9` | `#3B82F6` | 파랑 (지도/위치) |
| `domain.meetup` | `#52C41A` | `#10B981` | 에메랄드 (모임/만남) |
| `domain.care` | `#FAAD14` | `#F59E0B` | 앰버 (케어/돌봄) |
| `domain.community` | *(없음)* | `#8B5CF6` | 보라 (커뮤니티) (신규) |
| `domain.missing` | *(없음)* | `#EF4444` | 빨강 (실종동물) (신규) |

### 2.8 커뮤니티 카테고리 색상 (신규 — theme.js에 추가)

```js
// 현재 CommunityBoard.js 하드코딩 → theme.js로 이동
category: {
  all:       '#6B7280',  // 전체 (중립 그레이)
  daily:     '#EC4899',  // 일상 (핑크)
  question:  '#3B82F6',  // 질문 (블루)
  info:      '#10B981',  // 정보 (에메랄드)
  review:    '#F59E0B',  // 후기 (앰버)
  missing:   '#EF4444',  // 실종 (레드)
  adoption:  '#8B5CF6',  // 입양 (퍼플)
  free:      '#6366F1',  // 나눔 (인디고)
},
```

### 2.9 OAuth 브랜드 색상 (신규 — theme.js에 추가)

```js
// 브랜드 정책상 변경 불가 — theme.js로 이동만
oauth: {
  google:  '#4285F4',
  naver:   '#03C75A',
  kakao:   '#FEE500',
  kakaoText: '#3C1E1E',
},
```

### 2.10 기타

| 토큰명 | 현재 값 | 새 값 |
|-------|--------|-------|
| `gradient` | `linear-gradient(135deg, #FF7E36, #FF9558)` | `linear-gradient(135deg, #E8714A 0%, #F0926E 100%)` |
| `overlay` | `rgba(0,0,0,0.5)` | `rgba(28,25,23,0.6)` |
| `shadow` | `rgba(0,0,0,0.1)` | `rgba(28,25,23,0.08)` |
| `shadowHover` | `rgba(0,0,0,0.15)` | `rgba(28,25,23,0.14)` |

---

## 3. 다크 모드 색상

| 토큰명 | 새 값 | 비고 |
|-------|-------|-----|
| `background` | `#171412` | 따뜻한 다크 |
| `surface` | `#242220` | |
| `surfaceSoft` | `#1E1C1A` | |
| `surfaceElevated` | `#2E2C2A` | |
| `surfaceHover` | `#322F2C` | |
| `text` | `#F5F0EC` | 따뜻한 오프화이트 |
| `textSecondary` | `#C4BDB7` | |
| `textLight` | `#9C958E` | |
| `textMuted` | `#7A7470` | |
| `border` | `#3D3A37` | |
| `borderLight` | `#302D2A` | |
| `borderDark` | `#524E4A` | |
| `primary` | `#F0926E` | 다크에서 밝게 |
| `primaryDark` | `#E8714A` | |
| `primaryLight` | `#F5AE92` | |

---

## 4. 타이포그래피 시스템 (완전 재설계)

### 4.1 폰트 패밀리

```js
typography: {
  fontFamily: {
    korean: "'Pretendard Variable', 'Pretendard', -apple-system, BlinkMacSystemFont, 'Noto Sans KR', sans-serif",
    english: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
    mono: "'JetBrains Mono', 'Fira Code', monospace",
  }
}
```

> Pretendard: 한국어 최적화, Inter와 같은 비율 — 혼용 시 자연스러움

### 4.2 폰트 사이즈 스케일 (현재 → 새)

| 토큰명 | 현재 값 | 새 값 | 사용 사례 |
|-------|--------|-------|---------|
| `hero` | *(없음)* | `48px` | Hero 섹션 타이틀 (신규) |
| `h1` | `20px` | `32px` | 페이지 타이틀 |
| `h2` | `18px` | `24px` | 섹션 타이틀 |
| `h3` | `16px` | `20px` | 카드 타이틀, 모달 타이틀 |
| `h4` | `14px` | `17px` | 소제목 |
| `body1` | `13px` | `15px` | 기본 본문 |
| `body2` | `12px` | `13px` | 보조 텍스트 |
| `caption` | `10px` | `11px` | 메타 정보, 태그 |
| `tiny` | *(없음)* | `10px` | 극소 라벨 (신규) |

### 4.3 폰트 웨이트

```js
fontWeight: {
  regular:   400,
  medium:    500,
  semibold:  600,
  bold:      700,
  extrabold: 800,
}
```

### 4.4 줄간격 (Line-height)

```js
lineHeight: {
  tight:   1.2,   // 헤딩용
  normal:  1.5,   // 본문용
  relaxed: 1.75,  // 읽기 편한 긴 텍스트
}
```

### 4.5 실제 사용 사례별 매핑

| 용도 | fontSize | fontWeight | lineHeight |
|-----|---------|-----------|-----------|
| Hero 타이틀 | `hero (48px)` | `extrabold (800)` | `tight (1.2)` |
| 페이지 타이틀 | `h1 (32px)` | `bold (700)` | `tight (1.2)` |
| 섹션 타이틀 | `h2 (24px)` | `semibold (600)` | `tight (1.2)` |
| 카드 타이틀 | `h3 (20px)` | `semibold (600)` | `normal (1.5)` |
| 본문 | `body1 (15px)` | `regular (400)` | `normal (1.5)` |
| 보조 텍스트 | `body2 (13px)` | `regular (400)` | `normal (1.5)` |
| 버튼 텍스트 | `body1 (15px)` | `semibold (600)` | `tight (1.2)` |
| 입력 레이블 | `body2 (13px)` | `medium (500)` | `normal (1.5)` |
| 배지/태그 | `caption (11px)` | `semibold (600)` | `tight (1.2)` |

---

## 5. 간격 시스템 (8pt 그리드 — 완전 재설계)

### 현재 vs 새 spacing 토큰

| 토큰명 | 현재 값 | 새 값 | 비고 |
|-------|--------|-------|-----|
| `xs` | `4px` | `4px` | 유지 |
| `sm` | `8px` | `8px` | 유지 |
| `md` | `12px` | `12px` | 유지 |
| `lg` | `16px` | `16px` | 유지 |
| `xl` | `20px` | `20px` | 유지 |
| `xxl` | `30px` | `24px` | **변경** (30px → 24px, 8pt 맞춤) |
| `3xl` | *(없음)* | `32px` | **신규** |
| `4xl` | *(없음)* | `40px` | **신규** |
| `5xl` | *(없음)* | `48px` | **신규** |
| `6xl` | *(없음)* | `64px` | **신규** |

> `xxl` 값이 30px → 24px 변경됨. 기존 사용처 검토 필요.

### 컨테이너 max-width

```js
container: {
  sm:   '640px',
  md:   '768px',
  lg:   '1024px',
  xl:   '1280px',
  full: '100%',
}
```

### 컴포넌트 내부 패딩 규칙

| 컴포넌트 | 내부 패딩 |
|---------|---------|
| 버튼 (small) | `8px 16px` |
| 버튼 (medium) | `10px 20px` |
| 버튼 (large) | `12px 24px` |
| 카드 | `20px 20px` |
| 모달 | `24px 24px` |
| 입력 필드 | `10px 14px` |
| 네비게이션 | `0 24px` |
| 페이지 | `24px 16px (mobile) / 40px 24px (desktop)` |

---

## 6. Border Radius 시스템

| 토큰명 | 현재 값 | 새 값 | 사용 사례 |
|-------|--------|-------|---------|
| `xs` | *(없음)* | `2px` | 작은 배지 (신규) |
| `sm` | `4px` | `4px` | 태그, 소형 배지 |
| `md` | `6px` | `8px` | 버튼, 입력창 |
| `lg` | `8px` | `12px` | 카드 |
| `xl` | `12px` | `16px` | 모달, 드롭다운 |
| `2xl` | *(없음)* | `24px` | 큰 카드, 패널 (신규) |
| `pill` | *(없음)* | `9999px` | 알약형 버튼, 배지 (신규) |
| `full` | `50%` | `50%` | 아바타, 원형 버튼 |

---

## 7. 그림자 토큰

| 토큰명 | 현재 값 | 새 값 |
|-------|--------|-------|
| `none` | *(없음)* | `none` |
| `sm` | `0 2px 4px rgba(0,0,0,0.08)` | `0 1px 3px rgba(28,25,23,0.08), 0 1px 2px rgba(28,25,23,0.04)` |
| `md` | `0 4px 12px rgba(0,0,0,0.12)` | `0 4px 8px rgba(28,25,23,0.08), 0 2px 4px rgba(28,25,23,0.04)` |
| `lg` | `0 8px 24px rgba(0,0,0,0.15)` | `0 10px 24px rgba(28,25,23,0.10), 0 4px 8px rgba(28,25,23,0.06)` |
| `xl` | `0 12px 40px rgba(15,23,42,0.2)` | `0 20px 40px rgba(28,25,23,0.14), 0 8px 16px rgba(28,25,23,0.08)` |
| `focus` | *(없음)* | `0 0 0 3px rgba(232,113,74,0.3)` | focus ring (신규) |

---

## 8. 컴포넌트 디자인 규칙

### 8.1 Button

#### Variant × Size 매트릭스

```
variant: primary | secondary | ghost | danger | success
size:    sm | md | lg
state:   default | hover | active | disabled | loading
```

**Primary Button**
```js
// Default
background: colors.primary (#E8714A)
color: colors.textInverse (#FFFFFF)
border: none
borderRadius: borderRadius.md (8px)
padding: size별 (sm: 8px 16px / md: 10px 20px / lg: 12px 24px)
fontSize: sm=body2 / md=body1 / lg=body1
fontWeight: semibold (600)
transition: all 0.15s ease

// Hover
background: colors.primaryDark (#C9573A)
transform: translateY(-1px)
shadow: shadows.md

// Active
background: colors.primaryDark
transform: translateY(0)
shadow: shadows.sm

// Disabled
opacity: 0.4
cursor: not-allowed
transform: none

// Loading
cursor: wait
opacity: 0.8
// 스피너 아이콘 표시
```

**Secondary Button**
```js
background: colors.surface
color: colors.text
border: 1.5px solid colors.border
borderRadius: borderRadius.md

// Hover
border-color: colors.primary
color: colors.primary
background: colors.primarySoft
```

**Ghost Button**
```js
background: transparent
color: colors.textSecondary
border: none

// Hover
background: colors.surfaceHover
color: colors.text
```

**Danger Button**
```js
background: colors.error
color: colors.textInverse
// hover: colors.errorDark
```

### 8.2 Input / Form

```js
// Default
background: colors.background
border: 1.5px solid colors.border
borderRadius: borderRadius.md (8px)
padding: 10px 14px
fontSize: body1 (15px)
color: colors.text
transition: border-color 0.15s, box-shadow 0.15s

// Focus
border-color: colors.primary
box-shadow: shadows.focus (0 0 0 3px rgba(232,113,74,0.3))
outline: none

// Error
border-color: colors.error
box-shadow: 0 0 0 3px rgba(239,68,68,0.2)

// Success
border-color: colors.success
box-shadow: 0 0 0 3px rgba(34,197,94,0.2)

// Disabled
background: colors.surfaceSoft
color: colors.textMuted
cursor: not-allowed
opacity: 0.7

// Label
fontSize: body2 (13px)
fontWeight: medium (500)
color: colors.textSecondary
marginBottom: spacing.xs (4px)

// Helper text / Error message
fontSize: caption (11px)
color: state-dependent (error: colors.error / default: colors.textMuted)
marginTop: spacing.xs
```

### 8.3 Card

```js
// Elevation 단계
flat:     background colors.surface,    shadow: none,       border: 1px solid colors.border
raised:   background colors.surface,    shadow: shadows.sm, border: none
floating: background colors.background, shadow: shadows.md, border: none
modal:    background colors.surfaceElevated, shadow: shadows.xl, border: none

// 공통
borderRadius: borderRadius.lg (12px)
padding: spacing.xl (20px)
transition: box-shadow 0.2s, transform 0.2s

// hover (raised, floating)
transform: translateY(-2px)
shadow: 한 단계 위 (raised → shadows.md, floating → shadows.lg)
```

### 8.4 Badge / Tag

```js
// 기본
display: inline-flex
alignItems: center
padding: 2px 8px
borderRadius: borderRadius.pill (9999px)
fontSize: caption (11px)
fontWeight: semibold (600)

// variant: filled
background: 도메인/카테고리 색상
color: white (또는 어두운 색 — 자동 대비 계산)

// variant: soft
background: 색상 + 15% opacity
color: 색상 (darken 30%)
border: none

// variant: outline
background: transparent
border: 1.5px solid 색상
color: 색상
```

### 8.5 Avatar

```js
// Size 시스템
xs:  24px (댓글 등)
sm:  32px (목록 아이템)
md:  40px (기본)
lg:  56px (프로필 카드)
xl:  80px (프로필 페이지)
2xl: 120px (프로필 상세)

// 공통
borderRadius: borderRadius.full (50%)
objectFit: cover
border: 2px solid colors.border (선택)

// 기본 이미지 배경
background: colors.surfaceSoft
color: colors.textMuted
```

---

## 9. 레이아웃 & 반응형

### 9.1 브레이크포인트 (통일 — 현재 혼재 해소)

| 이름 | 값 | 설명 |
|-----|---|-----|
| `mobile` | `360px` | 소형 모바일 |
| `sm` | `640px` | 일반 모바일 |
| `md` | `768px` | 태블릿 (현재 주력) |
| `lg` | `1024px` | 소형 데스크탑 |
| `xl` | `1280px` | 일반 데스크탑 |
| `2xl` | `1536px` | 대형 화면 |

> Admin 패널의 `960px` → `lg (1024px)`로 통일

### 9.2 그리드 시스템

```js
// 모바일 (< 768px)
columns: 4
gutter: 16px
margin: 16px

// 태블릿 (768px ~ 1024px)
columns: 8
gutter: 20px
margin: 24px

// 데스크탑 (> 1024px)
columns: 12
gutter: 24px
margin: 40px
```

### 9.3 네비게이션 구조

```
모바일 (< 768px):
  상단: 로고 + 검색 + 프로필
  하단: 탭바 (홈 / 탐색 / 모임 / 채팅 / 프로필)

태블릿/데스크탑 (≥ 768px):
  상단 고정 네비게이션:
    좌: 로고
    중: 메뉴 링크
    우: 알림 / 다크모드 / 프로필
```

---

## 10. 애니메이션 & 트랜지션

### 10.1 Duration 토큰

```js
duration: {
  instant:  '0ms',     // 즉각 피드백
  fast:     '100ms',   // 마이크로 인터랙션 (hover 색상 변화)
  normal:   '200ms',   // 기본 트랜지션 (대부분 여기)
  slow:     '300ms',   // 모달 오픈/클로즈
  slower:   '500ms',   // 페이지 전환, 로딩
}
```

### 10.2 Easing 함수

```js
easing: {
  linear:   'linear',
  easeIn:   'cubic-bezier(0.4, 0, 1, 1)',
  easeOut:  'cubic-bezier(0, 0, 0.2, 1)',   // 기본 (들어올 때)
  easeInOut:'cubic-bezier(0.4, 0, 0.2, 1)', // 이동 (대부분 여기)
  spring:   'cubic-bezier(0.34, 1.56, 0.64, 1)', // 튀는 효과 (버튼 press)
}
```

### 10.3 인터랙션 피드백 원칙

| 인터랙션 | duration | easing | 효과 |
|---------|---------|--------|-----|
| 버튼 hover | fast (100ms) | easeOut | 배경색 변화 + 살짝 올라감 |
| 버튼 click | instant (0ms) | linear | 즉각 눌림 |
| 카드 hover | normal (200ms) | easeOut | shadow 강화 + 올라감 |
| 모달 오픈 | slow (300ms) | easeOut | 페이드인 + 슬라이드업 |
| 모달 클로즈 | normal (200ms) | easeIn | 페이드아웃 |
| 드롭다운 | normal (200ms) | easeOut | 슬라이드다운 |
| 탭 전환 | fast (150ms) | easeInOut | 언더라인 이동 |

---

## 11. 차트 색상 팔레트 (업데이트)

```js
// Before
chart: ['#FF7E36', '#4A90E2', '#4CAF50', '#FF9800', '#9C27B0', '#00BCD4', '#F44336', '#FFB74D']

// After (새 브랜드 컬러 반영)
chart: ['#E8714A', '#3D8B7A', '#22C55E', '#F59E0B', '#8B5CF6', '#06B6D4', '#EF4444', '#F97316']
```

---

## 12. 전체 theme.js 변경 요약 (Before → After)

### 추가되는 토큰 (신규)

```js
colors: {
  primarySoft:    '#FDF0EB',  // 신규
  secondaryDark:  '#2E6B5E',  // 신규
  secondaryLight: '#5BA898',  // 신규
  secondarySoft:  '#EBF5F3',  // 신규
  textInverse:    '#FFFFFF',  // 신규
  textMuted:      '#9CA3AF',  // 신규
  borderFocus:    '#E8714A',  // 신규
  successSoft:    '#F0FDF4',  // 신규
  warningSoft:    '#FFFBEB',  // 신규
  errorSoft:      '#FEF2F2',  // 신규
  infoSoft:       '#EFF6FF',  // 신규
  domain: {
    community:    '#8B5CF6',  // 신규
    missing:      '#EF4444',  // 신규
  },
  category: { ... },  // 전체 신규
  oauth: { ... },     // 전체 신규
}

typography: {
  fontFamily: { ... },  // 전체 신규
  fontWeight: { ... },  // 전체 신규
  lineHeight: { ... },  // 전체 신규
  hero: { fontSize: '48px', fontWeight: '800' },  // 신규
  // h1~caption: 크기 변경
}

spacing: {
  '3xl': '32px',  // 신규
  '4xl': '40px',  // 신규
  '5xl': '48px',  // 신규
  '6xl': '64px',  // 신규
}

borderRadius: {
  xs:   '2px',     // 신규
  pill: '9999px',  // 신규
  // md: 6px → 8px, lg: 8px → 12px, xl: 12px → 16px
}

duration: { ... }  // 전체 신규
easing:   { ... }  // 전체 신규
container: { ... } // 전체 신규
```

### 값이 변경되는 토큰 (주의 필요)

| 토큰 | 변경 전 | 변경 후 | 영향 |
|-----|--------|--------|-----|
| `colors.primary` | `#FF7E36` | `#E8714A` | 전체 primary 사용처 |
| `colors.textLight` | `#9E9E9E` | `#6B7280` | 텍스트 색상 전체 |
| `colors.background` | `#FFFFFF` | `#FAFAF8` | 전체 배경 |
| `colors.success` | `#4CAF50` | `#22C55E` | 성공 상태 전체 |
| `spacing.xxl` | `30px` | `24px` | xxl 사용처 검토 필요 |
| `borderRadius.md` | `6px` | `8px` | 버튼/입력창 전체 |
| `borderRadius.lg` | `8px` | `12px` | 카드 전체 |
| `borderRadius.xl` | `12px` | `16px` | 모달 전체 |
| `typography.h1` | `20px` | `32px` | 페이지 타이틀 전체 |
| `typography.h2` | `18px` | `24px` | 섹션 타이틀 전체 |
| `typography.body1` | `13px` | `15px` | 기본 본문 전체 |

---

## 13. 구현 우선순위 (구현자 참고)

1. **theme.js 토큰 교체** — 모든 것의 기반. 여기서 80%가 자동 반영
2. **typography 검증** — h1이 20→32px로 커지므로 레이아웃 깨짐 확인
3. **spacing.xxl 검증** — 30→24px 변경 영향 확인
4. **Common/ui/Button 강화** — variant × size × state 매트릭스
5. **Common/ui/Input 강화** — 상태별 스타일
6. **Chat 도메인 하드코딩 제거** — 가장 공수 큰 작업
7. **카테고리 색상 토큰 이동** — CommunityBoard.js → theme.js
8. **MissingPet StatusBadge** — theme.colors.status 활용

---

*구현 시 `docs/implementation-plan.md`와 함께 참고*
