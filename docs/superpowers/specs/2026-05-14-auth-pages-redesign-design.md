# Auth Pages Redesign — Design Spec

> **Status**: Approved (2026-05-14) — ready for implementation plan
> **Scope**: 로그인 / 회원가입 화면을 데스크탑 친화적 split-screen 으로 재설계
> **Related**: `docs/superpowers/plans/2026-05-13-pages-redesign-responsive.md` (Task 1 · Task 2 를 본 spec 으로 대체)

## 1. 배경 (Why)

현재 `LoginForm.js` / `RegisterForm.js` 는 모바일 카드 디자인을 데스크탑에 그대로 표시하고 있어 **PC 화면에서 좌우 1300px 이상이 비어 있는** 상태. 또한 두 화면의 인풋/버튼 디자인이 어긋나 (Login: 사각형 + 단색, Register: pill + gradient) 일관성도 깨져 있음.

→ 데스크탑 전용 레이아웃을 추가하고, 두 화면의 디자인 토큰을 통일한다.

## 2. 목표 (Goal)

- 데스크탑 (≥1024px) 에서 가로 공간을 활용한 split-screen 적용
- 모바일/태블릿 (<1024px) 은 단일 컬럼 폼 유지
- Login / Register 두 화면이 같은 디자인 언어 사용 (pill input + gradient button + teracotta theme)
- 로직 / Step / 검증 코드는 무변경 (외과적 변경)

## 3. 디자인 결정 (What)

### 3.1 레이아웃

| 브레이크포인트 | 레이아웃 | 좌측 패널 | 비고 |
|---|---|---|---|
| ≥1024px | Split-screen 45% / 55% | 표시 | 데스크탑 |
| 768~1023px | 단일 컬럼 폼 | 숨김 | 태블릿 |
| <768px | 단일 컬럼 폼 (풀폭) | 숨김 | 모바일 |

폼 영역 max-width: 420px, 가운데 정렬.

### 3.2 좌측 브랜드 패널 (BrandPanel)

- **배경**: `linear-gradient(135deg, #E8714A 0%, #C9573A 50%, #A8442C 100%)`
- **모티프**: 작은 글래스 카드 여러 개가 패널 곳곳에 흩어져 떠 있는 형태. 각 카드 안에 펫 관련 이모지/아이콘 (🐶 🐱 🐾 🦴 등) 1개씩.
  - 카드 스타일: `background: rgba(255,255,255,0.15)`, `backdrop-filter: blur(12px)`, `border-radius: 20px`, `border: 1px solid rgba(255,255,255,0.25)`
  - 카드 4~6개, 절대 배치, 살짝 다른 크기 (60~100px)
  - 미세한 그림자 + transform rotate (-8deg ~ 8deg) 으로 떠다니는 느낌
- **타이포그래피**: 패널 중앙 하단에 슬로건
  - 메인 (한국어): "반려동물과 함께하는 / 모든 순간" — `font-size: 36px`, `font-weight: 700`, `color: #ffffff`
  - 서브 (영문 로고): "Petory" — `font-size: 14px`, `letter-spacing: 0.2em`, `opacity: 0.8`
- **모바일**: `display: none`

### 3.3 우측 폼 패널 (FormPanel)

- **배경**: `theme.colors.background` (흰색/연베이지)
- **카드 없음** — 폼이 panel 안에 직접 놓임 (현재의 흰색 카드 안에 흰색 폼 중첩이 어색했음)
- **헤더**: 작은 로고 (🐾 + Petory) → 페이지 제목 ("로그인" / "회원가입")
- **인풋 (PillInput)**:
  - `border-radius: 50px`, `border: 1.5px solid theme.colors.border`, `padding: 14px 20px`, `font-size: 15px`
  - `:focus { border-color: theme.colors.primary }`
- **주 버튼 (GradientButton)**:
  - `background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%)`
  - `border-radius: 50px`, `padding: 14px`, `font-weight: 600`, `color: #fff`
  - `:hover { opacity: 0.9 }`, `:active { transform: scale(0.98) }`
- **소셜 로그인 (SocialButton)**:
  - 톤다운: `background: #fff`, `border: 1.5px solid theme.colors.border`, `color: theme.colors.text`
  - 좌측에 컬러 아이콘 (Google G 파랑, Naver N 초록) — 버튼 자체는 컬러풀하지 않음
- **부 링크 (SecondaryLink)**:
  - "회원가입" / "로그인" 전환 링크는 폼 하단에 텍스트 링크 + 작은 글씨

### 3.4 공통 디자인 토큰

- **Primary**: `#E8714A` (teracotta)
- **Gradient**: `linear-gradient(135deg, #E8714A 0%, #C9573A 100%)`
- **Pill radius**: `50px` (input, button)
- **Card radius**: `20px` (좌측 패널 글래스 카드)
- **Glass card**: `rgba(255,255,255,0.15)` + `backdrop-filter: blur(12px)` + `border: 1px solid rgba(255,255,255,0.25)`

## 4. 변경 범위 (Surgical)

### 4.1 신규 파일

```
frontend/src/components/Auth/AuthShell.js
```

공통 styled-components 만 export. 로직 없음.

| Export | 역할 |
|---|---|
| `AuthPageWrapper` | 최외곽 split-screen 컨테이너 |
| `BrandPanel` | 좌측 그라데이션 패널 (≥1024px 표시) |
| `FloatingGlassCard` | BrandPanel 내부 떠다니는 글래스 카드 |
| `BrandSlogan` | 좌측 패널 슬로건 텍스트 |
| `FormPanel` | 우측 폼 영역 |
| `FormHeader` | 로고 + 제목 헤더 |
| `PillInput` | 알약 모양 인풋 (`<input>` 용) |
| `PillSelect` | 알약 모양 셀렉트 (`<select>` 용, PillInput 과 동일 스타일) |
| `GradientButton` | 그라데이션 주 버튼 |
| `OutlineButton` | 윤곽선 부 버튼 (이메일 인증, 중복확인 등) |
| `SocialButton` | 톤다운 소셜 로그인 버튼 |
| `Divider` | "또는" 구분선 |

### 4.2 수정 파일

#### `frontend/src/components/Auth/LoginForm.js`

- 자체 styled-components 정의 (대략 ~300줄, 라인 228~524) **전부 삭제**
- `AuthShell.js` 에서 필요한 것만 import 해서 사용
- JSX 구조: `<AuthPageWrapper><BrandPanel>...</BrandPanel><FormPanel>...</FormPanel></AuthPageWrapper>`
- 비밀번호 찾기 인라인 폼은 `FormPanel` 내부에서 같은 PillInput / GradientButton 사용
- 로직 / 상태 / API 호출 모두 무변경
- 죽은 코드 제거: 라인 238~241 의 `BrandPanel` / `BrandPanelIcon` / `BrandPanelTitle` / `BrandPanelSubtitle` (전부 `display: none` 인 placeholder. 본 spec 의 새 `BrandPanel` 과는 무관한 동명 placeholder 임)

#### `frontend/src/components/Auth/RegisterForm.js`

- 자체 styled-components 중 wrapper · 카드 · 인풋 · 버튼 부분만 `AuthShell` import 로 교체:
  - `AuthPageWrapper`, `GlassCard` → `AuthPageWrapper` + `BrandPanel` + `FormPanel` 로 변경
  - `Input`, `Select`, `EmailIdInput`, `EmailCustomInput`, `PetInput`, `PetSelect`, `EmailDomainSelect` → `PillInput`, `PillSelect`
  - `Button`, `CheckButton`, `EmailVerificationButton`, `AddPetButton`, `RemovePetButton` → `GradientButton` / `OutlineButton`
- Pet 카드, Step 분기, 이메일 인증 로직, 닉네임 중복 확인 등 **모든 비-시각 로직 무변경**
- 죽은 코드 제거: `RegisterContainer` (라인 856~869, 사용되지 않음)
- `Title`, `Form`, `InputGroup`, `Label` 등 layout-only styled-components 는 그대로 유지 (값을 `theme.*` 토큰으로 미세 조정만)

#### 변경하지 않는 파일

- `EmailVerificationPage.js`, `NicknameSetup.js`, `OAuth2Callback.js` — 본 spec 범위 외 (필요시 후속 spec)
- `theme.js` — 기존 토큰 사용, 새 토큰 추가 없음

## 5. 비목표 (Non-Goals)

- 인증 로직 / 검증 로직 변경 X
- 회원가입 Step 흐름 변경 X
- 새로운 디자인 토큰 (theme.js) 추가 X
- OAuth2 콜백 페이지 / 이메일 인증 페이지 / 닉네임 셋업 페이지 디자인 X (후속)
- 다크모드 별도 대응 X (`color-scheme: light` 유지)

## 6. 검증 기준 (Success Criteria)

- [ ] `npm run build` 성공, 린트 워닝 감소 (죽은 코드 제거로 4~5개 워닝 사라짐)
- [ ] 1920×1080 데스크탑: 좌측 그라데이션 패널 + 우측 폼이 45:55 로 표시
- [ ] 1024px 정확히: split-screen 마지막 폭 (좁아도 작동)
- [ ] 1023px: 좌측 패널 사라지고 폼만 가운데 단일 컬럼
- [ ] 375px (모바일): 우측 폼이 풀폭, padding 적절
- [ ] LoginForm ↔ RegisterForm 전환 시 시각적 일관성 (같은 패널, 같은 인풋, 같은 버튼)
- [ ] 회원가입 모든 단계 (이메일 인증, 펫 추가, 닉네임 중복확인 등) 정상 작동
- [ ] 비밀번호 찾기 인라인 폼 정상 표시 및 작동

## 7. 후속 작업 (Out of Scope, 후속 spec)

- `EmailVerificationPage.js` 디자인 통일
- `NicknameSetup.js` 디자인 통일
- 다크모드 지원
- 좌측 패널에 실제 SVG 일러스트 도입 (현재 이모지 → 일러스트로 교체)
- OAuth2 콜백 페이지 로딩 인디케이터 디자인
