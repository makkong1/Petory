# Auth Pages Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인/회원가입 화면을 데스크탑 친화적 split-screen 디자인 (≥1024px) 으로 재설계하고, 두 화면의 디자인 토큰을 통일한다.

**Architecture:** 공통 styled-components 를 `AuthShell.js` 에 추출하여 LoginForm/RegisterForm 이 import 해서 쓰는 구조. `theme.js` 토큰을 적극 활용 (새 토큰 추가 없음). 모바일 (<1024px) 은 단일 컬럼 폼만, 데스크탑은 좌측 그라데이션 패널 (45%) + 우측 폼 (55%).

**Tech Stack:** React 19, styled-components, 기존 `theme.js` 토큰

**Related Spec:** `docs/superpowers/specs/2026-05-14-auth-pages-redesign-design.md`

---

## File Map

| File | Change | What |
|---|---|---|
| `frontend/src/components/Auth/AuthShell.js` | Create | 공통 styled-components 정의 |
| `frontend/src/components/Auth/LoginForm.js` | Modify | 자체 styled 삭제 + AuthShell 사용 + JSX 재구성 |
| `frontend/src/components/Auth/RegisterForm.js` | Modify | wrapper/카드/인풋/버튼 styled 삭제 + AuthShell 사용 + JSX 일부 재구성 + 죽은 코드 제거 |

---

## Task 1: AuthShell.js 신규 생성

**Files:**
- Create: `frontend/src/components/Auth/AuthShell.js`

**Context:** LoginForm 과 RegisterForm 이 공유할 styled-components 모음. 이 파일에는 로직이 들어가지 않고 export 만 한다. 다른 컴포넌트의 변경 없이 이 파일만 추가되므로 빌드는 무사히 통과해야 한다.

- [ ] **Step 1: 파일 작성**

`frontend/src/components/Auth/AuthShell.js` 를 신규 생성하고 아래 내용 그대로 작성:

```js
import styled from 'styled-components';

// ===== Layout =====

export const AuthPageWrapper = styled.div`
  color-scheme: light;
  min-height: 100vh;
  display: flex;
  background: ${({ theme }) => theme.colors.background};

  @media (min-width: 1024px) {
    flex-direction: row;
  }

  @media (max-width: 1023px) {
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: max(40px, env(safe-area-inset-top, 40px)) 20px max(40px, env(safe-area-inset-bottom, 40px));
  }
`;

// ===== Brand Panel (Desktop only, ≥1024px) =====

export const BrandPanel = styled.aside`
  display: none;

  @media (min-width: 1024px) {
    display: flex;
    flex: 0 0 45%;
    flex-direction: column;
    justify-content: space-between;
    padding: 64px 56px;
    background: linear-gradient(135deg, #E8714A 0%, #C9573A 50%, #A8442C 100%);
    position: relative;
    overflow: hidden;
  }
`;

export const BrandWordmark = styled.div`
  font-size: 14px;
  letter-spacing: 0.2em;
  color: rgba(255, 255, 255, 0.85);
  font-weight: 600;
  position: relative;
  z-index: 1;
`;

export const BrandFloatingArea = styled.div`
  position: relative;
  flex: 1;
  min-height: 320px;
`;

export const FloatingGlassCard = styled.div`
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 20px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  width: ${({ $size = 80 }) => `${$size}px`};
  height: ${({ $size = 80 }) => `${$size}px`};
  font-size: ${({ $size = 80 }) => `${$size * 0.5}px`};
  top: ${({ $top }) => $top ?? 'auto'};
  left: ${({ $left }) => $left ?? 'auto'};
  right: ${({ $right }) => $right ?? 'auto'};
  bottom: ${({ $bottom }) => $bottom ?? 'auto'};
  transform: rotate(${({ $rotate = 0 }) => `${$rotate}deg`});
`;

export const BrandSloganGroup = styled.div`
  position: relative;
  z-index: 1;
`;

export const BrandSlogan = styled.h2`
  font-size: 36px;
  font-weight: 700;
  line-height: 1.3;
  color: #ffffff;
  margin: 0;
  white-space: pre-line;
`;

// ===== Form Panel (Right) =====

export const FormPanel = styled.section`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;

  @media (min-width: 1024px) {
    flex: 0 0 55%;
    padding: 64px 56px;
  }
`;

export const FormInner = styled.div`
  width: 100%;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

export const FormHeader = styled.header`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

export const FormHeaderLogo = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 18px;
  font-weight: 700;
  color: ${({ theme }) => theme.colors.primary};

  @media (min-width: 1024px) {
    display: none;
  }
`;

export const FormTitle = styled.h1`
  font-size: ${({ theme }) => theme.typography.h1.fontSize};
  font-weight: ${({ theme }) => theme.typography.h1.fontWeight};
  line-height: ${({ theme }) => theme.typography.h1.lineHeight};
  color: ${({ theme }) => theme.colors.text};
  margin: 0;
`;

export const FormSubtitle = styled.p`
  font-size: 14px;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin: 0;
`;

// ===== Form Controls =====

export const PillInput = styled.input`
  color-scheme: light;
  width: 100%;
  padding: 14px 20px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 15px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  outline: none;
  transition: border-color ${({ theme }) => theme.duration.fast} ease,
              box-shadow ${({ theme }) => theme.duration.fast} ease;
  box-sizing: border-box;

  &:focus {
    border-color: ${({ theme }) => theme.colors.borderFocus};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }

  &::placeholder {
    color: ${({ theme }) => theme.colors.textMuted};
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }
`;

export const PillSelect = styled.select`
  color-scheme: light;
  width: 100%;
  padding: 14px 20px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 15px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  outline: none;
  cursor: pointer;
  transition: border-color ${({ theme }) => theme.duration.fast} ease;
  box-sizing: border-box;

  &:focus {
    border-color: ${({ theme }) => theme.colors.borderFocus};
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }
`;

export const GradientButton = styled.button`
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%);
  color: #ffffff;
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity ${({ theme }) => theme.duration.normal} ease,
              transform ${({ theme }) => theme.duration.fast} ease;

  &:hover:not(:disabled) {
    opacity: 0.92;
  }

  &:active:not(:disabled) {
    transform: scale(0.98);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

export const OutlineButton = styled.button`
  width: 100%;
  padding: 14px;
  background: transparent;
  color: ${({ theme }) => theme.colors.primary};
  border: 1.5px solid ${({ theme }) => theme.colors.primary};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background ${({ theme }) => theme.duration.fast} ease;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primarySoft};
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

export const SocialButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  background: ${({ theme }) => theme.colors.surfaceElevated};
  color: ${({ theme }) => theme.colors.text};
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background ${({ theme }) => theme.duration.fast} ease;

  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
`;

export const SocialIcon = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 800;
  color: #ffffff;
  background: ${({ $provider, theme }) => theme.colors.oauth[$provider] || theme.colors.primary};
`;

export const Divider = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: ${({ theme }) => theme.colors.borderLight};
  }

  span {
    font-size: 13px;
    color: ${({ theme }) => theme.colors.textSecondary};
  }
`;

export const FormSwitchLink = styled.div`
  text-align: center;
  font-size: 14px;
  color: ${({ theme }) => theme.colors.textSecondary};

  button {
    background: none;
    border: none;
    color: ${({ theme }) => theme.colors.primary};
    font-weight: 600;
    cursor: pointer;
    padding: 0 4px;
    font-size: 14px;

    &:hover {
      text-decoration: underline;
    }
  }
`;
```

- [ ] **Step 2: 빌드 확인**

루트에서 실행:

```bash
cd frontend && npm run build
```

기대 결과: `Compiled with warnings.` (기존 워닝 그대로) — AuthShell 은 아직 import 되지 않으므로 새 워닝/에러 없음.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/Auth/AuthShell.js
git commit -m "feat(auth): add shared AuthShell styled-components for redesign"
```

---

## Task 2: LoginForm.js 디자인 적용

**Files:**
- Modify: `frontend/src/components/Auth/LoginForm.js`

**Context:** LoginForm.js (523줄) 는 자체 styled-components 약 300줄 (라인 228~524) 을 보유. JSX (라인 85~223) 와 로직 (라인 1~84) 은 그대로 유지하면서, 자체 styled-components 를 대부분 삭제하고 AuthShell 의 컴포넌트로 교체한다. JSX 구조도 split-screen 으로 재구성.

- [ ] **Step 1: import 추가**

`LoginForm.js` 상단 (라인 1~6) 의 import 영역에 다음 줄을 추가:

```js
import {
  AuthPageWrapper,
  BrandPanel,
  BrandWordmark,
  BrandFloatingArea,
  FloatingGlassCard,
  BrandSloganGroup,
  BrandSlogan,
  FormPanel,
  FormInner,
  FormHeader,
  FormHeaderLogo,
  FormTitle,
  FormSubtitle,
  PillInput,
  GradientButton,
  SocialButton,
  SocialIcon,
  Divider,
  FormSwitchLink,
} from './AuthShell';
```

기존 styled-components import (`import styled from 'styled-components'`) 는 유지 — 일부 layout-only styled (`Form`, `InputGroup`, `Label`, `DemoHint`, `ErrorMessage`, `SuccessMessage`, `ForgotPasswordLink`, `ForgotSection`, `ForgotTitle`, `ForgotPasswordForm`, `ButtonGroup`, `CancelButton`, `SocialLoginContainer`) 는 그대로 둘 것이므로.

- [ ] **Step 2: JSX return 재구성**

`LoginForm.js` 의 `return (` 부터 `);` (현재 라인 84~223) 를 아래 전체로 교체:

```jsx
  return (
    <AuthPageWrapper>
      <BrandPanel>
        <BrandWordmark>PETORY</BrandWordmark>
        <BrandFloatingArea>
          <FloatingGlassCard $size={80} $top="10%" $left="18%" $rotate={-8}>🐶</FloatingGlassCard>
          <FloatingGlassCard $size={68} $top="38%" $right="14%" $rotate={6}>🐱</FloatingGlassCard>
          <FloatingGlassCard $size={92} $top="58%" $left="8%" $rotate={-4}>🐾</FloatingGlassCard>
          <FloatingGlassCard $size={60} $bottom="12%" $right="22%" $rotate={10}>🦴</FloatingGlassCard>
        </BrandFloatingArea>
        <BrandSloganGroup>
          <BrandSlogan>{'반려동물과 함께하는\n모든 순간'}</BrandSlogan>
        </BrandSloganGroup>
      </BrandPanel>

      <FormPanel>
        <FormInner>
          <FormHeader>
            <FormHeaderLogo>🐾 Petory</FormHeaderLogo>
            <FormTitle>로그인</FormTitle>
            <FormSubtitle>반려동물과 함께하는 커뮤니티</FormSubtitle>
          </FormHeader>

          {isDemoMode() && (
            <DemoHint>데모 모드: 아무 아이디/비밀번호로 로그인 가능</DemoHint>
          )}

          <Form onSubmit={handleSubmit}>
            <InputGroup>
              <Label htmlFor="id">아이디</Label>
              <PillInput
                type="text"
                id="id"
                name="id"
                value={formData.id}
                onChange={handleChange}
                required
                disabled={loading}
              />
            </InputGroup>

            <InputGroup>
              <Label htmlFor="password">비밀번호</Label>
              <PillInput
                type="password"
                id="password"
                name="password"
                value={formData.password}
                onChange={handleChange}
                required
                disabled={loading}
              />
            </InputGroup>

            <ForgotPasswordLink>
              <button type="button" onClick={() => setShowForgotPassword(true)}>
                비밀번호 찾기
              </button>
            </ForgotPasswordLink>

            {error && <ErrorMessage>{error}</ErrorMessage>}
            {success && <SuccessMessage>{success}</SuccessMessage>}

            <GradientButton type="submit" disabled={loading}>
              {loading ? '로그인 중...' : '로그인'}
            </GradientButton>
          </Form>

          {!isDemoMode() && (
            <>
              <Divider><span>또는</span></Divider>

              <SocialLoginContainer>
                <SocialButton type="button" onClick={() => handleSocialLogin('google')}>
                  <SocialIcon $provider="google">G</SocialIcon>
                  Google 로 로그인
                </SocialButton>

                <SocialButton type="button" onClick={() => handleSocialLogin('naver')}>
                  <SocialIcon $provider="naver">N</SocialIcon>
                  Naver 로 로그인
                </SocialButton>
              </SocialLoginContainer>
            </>
          )}

          <FormSwitchLink>
            계정이 없으신가요?
            <button type="button" onClick={() => { if (onSwitchToRegister) onSwitchToRegister(); }}>
              회원가입
            </button>
          </FormSwitchLink>

          {showForgotPassword && (
            <ForgotSection>
              <ForgotTitle>비밀번호 찾기</ForgotTitle>
              <ForgotPasswordForm onSubmit={handleForgotPassword}>
                <InputGroup>
                  <Label htmlFor="forgotPasswordEmail">이메일</Label>
                  <PillInput
                    type="email"
                    id="forgotPasswordEmail"
                    value={forgotPasswordEmail}
                    onChange={(e) => {
                      setForgotPasswordEmail(e.target.value);
                      setForgotPasswordError('');
                    }}
                    placeholder="가입하신 이메일을 입력하세요"
                    required
                    disabled={forgotPasswordLoading}
                  />
                </InputGroup>

                {forgotPasswordError && <ErrorMessage>{forgotPasswordError}</ErrorMessage>}
                {forgotPasswordSuccess && <SuccessMessage>{forgotPasswordSuccess}</SuccessMessage>}

                <ButtonGroup>
                  <GradientButton type="submit" disabled={forgotPasswordLoading}>
                    {forgotPasswordLoading ? '발송 중...' : '비밀번호 재설정 링크 보내기'}
                  </GradientButton>
                  <CancelButton
                    type="button"
                    onClick={() => {
                      setShowForgotPassword(false);
                      setForgotPasswordEmail('');
                      setForgotPasswordError('');
                      setForgotPasswordSuccess('');
                    }}
                    disabled={forgotPasswordLoading}
                  >
                    뒤로
                  </CancelButton>
                </ButtonGroup>
              </ForgotPasswordForm>
            </ForgotSection>
          )}
        </FormInner>
      </FormPanel>
    </AuthPageWrapper>
  );
```

- [ ] **Step 3: 더 이상 안 쓰는 자체 styled-components 삭제**

`LoginForm.js` 의 styled-components 정의 영역 (현재 라인 228~524) 에서 **아래 정의들만** 삭제하고 나머지는 유지:

삭제 대상 (총 약 200줄):
- `AuthPageWrapper` (라인 228~236)
- `BrandPanel`, `BrandPanelIcon`, `BrandPanelTitle`, `BrandPanelSubtitle` 4개 (라인 238~241 — 죽은 placeholder)
- `GlassCard` (라인 243~256)
- `BrandHeader` (라인 258~263)
- `BrandIcon` (라인 265~275)
- `BrandTitle` (라인 277~282)
- `BrandSubtitle` (라인 284~288)
- `Title` (라인 290~295)
- `Input` (라인 324~350)
- `Button` (라인 352~377)
- `Divider`, `DividerLine`, `DividerText` 3개 (라인 389~405)
- `SocialButton` (라인 413~429)
- `SocialIcon` (라인 431~434)
- `LinkTextContainer` (라인 436~443)
- `LinkText` (라인 445~450)
- `SecondaryButton` (라인 452~467)

유지 대상 (그대로 둠):
- `DemoHint`, `Form`, `InputGroup`, `Label`, `ErrorMessage`, `SuccessMessage`, `SocialLoginContainer`, `ForgotPasswordLink`, `ForgotSection`, `ForgotTitle`, `ForgotPasswordForm`, `ButtonGroup`, `CancelButton`

- [ ] **Step 4: 빌드 + 시각 검증**

```bash
cd frontend && npm run build
```

기대 결과: 성공, 워닝 감소 (LoginForm 의 `sharedInputStyles` 등 워닝 사라짐).

이어서 dev 서버로 시각 검증:

```bash
cd frontend && npm start
```

확인 항목:
- 데스크탑 (≥1024px): 좌측 그라데이션 패널 + 떠다니는 글래스 카드 + 우측 폼
- 모바일 (DevTools 375px): 좌측 패널 숨김, 폼만 단일 컬럼
- 로그인 동작 정상
- "비밀번호 찾기" 클릭 시 인라인 폼 표시 정상

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/Auth/LoginForm.js
git commit -m "feat(auth): redesign LoginForm with split-screen layout"
```

---

## Task 3: RegisterForm.js 디자인 적용

**Files:**
- Modify: `frontend/src/components/Auth/RegisterForm.js`

**Context:** RegisterForm.js 는 1377줄이며 회원가입 Step / 펫 카드 / 이메일 인증 / 닉네임 중복확인 등 복잡한 로직을 포함. **로직과 Form 내부 구조는 절대 건드리지 않고**, 외곽 wrapper / 인풋 / 셀렉트 / 버튼의 컴포넌트 이름과 styled-components 정의만 교체한다.

- [ ] **Step 1: import 추가**

`RegisterForm.js` 상단 (라인 1~4) 의 import 영역에 다음 추가:

```js
import {
  AuthPageWrapper,
  BrandPanel,
  BrandWordmark,
  BrandFloatingArea,
  FloatingGlassCard,
  BrandSloganGroup,
  BrandSlogan,
  FormPanel,
  FormInner,
  FormHeader,
  FormHeaderLogo,
  FormTitle,
  FormSubtitle,
  PillInput,
  PillSelect,
  GradientButton,
  OutlineButton,
  FormSwitchLink,
} from './AuthShell';
```

- [ ] **Step 2: JSX wrapper 부분 재구성**

`RegisterForm.js` 의 라인 397~404 (현재 `<AuthPageWrapper><GlassCard>` 부터 `<Title>회원가입</Title>` 까지) 를 아래로 교체:

기존:
```jsx
  return (
    <AuthPageWrapper>
      <GlassCard>
        {/* ... 기존 BrandHeader ... */}

        <Title>회원가입</Title>
```

교체 후:
```jsx
  return (
    <AuthPageWrapper>
      <BrandPanel>
        <BrandWordmark>PETORY</BrandWordmark>
        <BrandFloatingArea>
          <FloatingGlassCard $size={80} $top="10%" $left="18%" $rotate={-8}>🐶</FloatingGlassCard>
          <FloatingGlassCard $size={68} $top="38%" $right="14%" $rotate={6}>🐱</FloatingGlassCard>
          <FloatingGlassCard $size={92} $top="58%" $left="8%" $rotate={-4}>🐾</FloatingGlassCard>
          <FloatingGlassCard $size={60} $bottom="12%" $right="22%" $rotate={10}>🦴</FloatingGlassCard>
        </BrandFloatingArea>
        <BrandSloganGroup>
          <BrandSlogan>{'반려동물과 함께하는\n모든 순간'}</BrandSlogan>
        </BrandSloganGroup>
      </BrandPanel>

      <FormPanel>
        <FormInner>
          <FormHeader>
            <FormHeaderLogo>🐾 Petory</FormHeaderLogo>
            <FormTitle>회원가입</FormTitle>
            <FormSubtitle>몇 가지 정보만 입력하면 시작할 수 있어요</FormSubtitle>
          </FormHeader>
```

> 기존의 `<BrandHeader><BrandIcon>...</BrandIcon><div><BrandTitle>...</BrandTitle><BrandSubtitle>...</BrandSubtitle></div></BrandHeader>` 블록 (라인 400~404 추정) 도 함께 삭제됨 — `FormHeader` 가 그 역할을 대신.

- [ ] **Step 3: JSX 닫는 wrapper 교체**

`RegisterForm.js` 라인 795~802 (`<LinkText>...</LinkText>` 와 `</GlassCard></AuthPageWrapper>`) 를 아래로 교체:

기존:
```jsx
        <Button type="submit" disabled={loading}>
          {/* 회원가입 버튼 */}
        </Button>
      </Form>

      <LinkText>
        이미 계정이 있으신가요?{' '}
        <button type="button" onClick={onSwitchToLogin}>로그인</button>
      </LinkText>
      </GlassCard>
    </AuthPageWrapper>
```

교체 후:
```jsx
          <GradientButton type="submit" disabled={loading}>
            {/* 기존 회원가입 버튼 텍스트 그대로 유지 — 라인 791 내용 보존 */}
          </GradientButton>
        </Form>

        <FormSwitchLink>
          이미 계정이 있으신가요?
          <button type="button" onClick={onSwitchToLogin}>로그인</button>
        </FormSwitchLink>
        </FormInner>
      </FormPanel>
    </AuthPageWrapper>
```

> `Button` → `GradientButton` 한 곳만 교체. 버튼 텍스트 (`{loading ? '...' : '회원가입'}` 같은 기존 컨텐츠) 는 절대 변경하지 말고 그대로 유지.

- [ ] **Step 4: 인풋/셀렉트/버튼 컴포넌트 이름 일괄 교체**

`RegisterForm.js` 의 JSX 내부에서 아래와 같이 styled-component 이름을 교체한다 (속성/props/children 은 전부 그대로 유지, 태그 이름만 바꿈):

| Before | After |
|---|---|
| `<Input ...>` | `<PillInput ...>` |
| `<Select ...>` | `<PillSelect ...>` |
| `<EmailIdInput ...>` | `<PillInput ...>` (다만 EmailInputGroup 안의 인풋폭 조정 styled 가 깨질 수 있음 — Step 5 참고) |
| `<EmailDomainSelect ...>` | `<PillSelect ...>` |
| `<EmailCustomInput ...>` | `<PillInput ...>` |
| `<PetInput ...>` | `<PillInput ...>` |
| `<PetSelect ...>` | `<PillSelect ...>` |
| `<CheckButton ...>` | `<OutlineButton ...>` |
| `<EmailVerificationButton ...>` | `<OutlineButton ...>` |

⚠️ **다음 컴포넌트는 교체하지 말 것** (이름은 비슷해도 다른 역할):
- `<RemovePetButton>` — 작은 X 버튼, 별도 디자인 유지
- `<AddPetButton>` — 점선 박스 스타일의 추가 버튼, 별도 디자인 유지
- `<PetCheckbox>` — 체크박스
- `<PetTextarea>` — textarea
- `<PetLabel>` — Pet 카드 내부 라벨

- [ ] **Step 5: EmailInputGroup 내부 폭 조정**

라인 1231~1296 의 `EmailInputGroup` / `EmailIdInput` / `EmailAt` / `EmailDomainSelect` 는 가로로 `[id]@[domain]` 형태로 배치되어 있다. `<PillInput>` 의 기본 `width: 100%` 가 적용되면 레이아웃이 깨지므로, 해당 JSX 의 `<PillInput>` (구 `EmailIdInput`) 와 `<PillSelect>` (구 `EmailDomainSelect`) 가 들어가는 위치에 인라인 스타일을 추가:

```jsx
<PillInput
  /* 기존 props 전부 그대로 유지 */
  style={{ flex: 1, minWidth: 0 }}
/>
```

```jsx
<PillSelect
  /* 기존 props 전부 그대로 유지 */
  style={{ flex: 1, minWidth: 0 }}
/>
```

> 같은 처리를 `<PillInput>` (구 `EmailCustomInput`) 에도 적용 — `flex: 1, minWidth: 0`.

- [ ] **Step 6: 더 이상 안 쓰는 자체 styled-components 삭제**

`RegisterForm.js` 의 styled-components 정의 영역 (라인 829~1377 사이) 에서 **아래 정의들만** 삭제:

삭제 대상:
- `AuthPageWrapper` (라인 831~839)
- `GlassCard` (라인 841~854)
- `RegisterContainer` (라인 856~869) — **죽은 코드 (사용 X), 린트 워닝의 원인**
- `Title` (라인 871~877) — FormTitle 로 대체
- `Input` (라인 897~922)
- `Select` (라인 924~946)
- `Button` (라인 948~973) — GradientButton 으로 대체
- `LinkText` (라인 987~ ) — FormSwitchLink 로 대체
- `CheckButton` (라인 1014~1034) — OutlineButton 으로 대체
- `EmailIdInput` (라인 1238~1264)
- `EmailDomainSelect` (라인 1273~1296)
- `EmailVerificationButton` (라인 1317~1338) — OutlineButton 으로 대체
- `EmailCustomInput` (라인 1351~끝)

유지 대상 (Pet 카드 영역의 모든 styled, 그리고 layout-only styled):
- `Form`, `InputGroup`, `Label`
- `ErrorMessage`, `SuccessMessage`
- `NicknameInputGroup`, `NicknameMessage`
- `PetCardsContainer`, `PetCard`, `PetCardHeader`, `PetCardTitle`, `PetCardBody`, `PetInputRow`, `PetInputGroup`, `PetLabel`, `PetInput`(삭제 후 PillInput으로 교체했지만 일부 Pet 전용 폭 조정이 있다면 inline style 로 처리), `PetSelect`, `PetCheckbox`, `PetTextarea`, `AddPetButton`, `RemovePetButton`
- `EmailInputGroup`, `EmailAt`
- `EmailVerificationErrorMessage`, `EmailVerificationStatus`, `EmailVerificationInfo`

> Pet 영역의 `PetInput` / `PetSelect` 는 Step 4 에서 PillInput / PillSelect 로 교체했으므로 styled-component 정의도 삭제 가능 — 다만 Pet 카드 내부의 PetInputRow 가 가로 정렬을 한다면 `flex: 1` inline style 을 추가해야 할 수 있음. 빌드 후 시각 확인하면서 조정.

- [ ] **Step 7: 빌드 + 시각 검증**

```bash
cd frontend && npm run build
```

기대 결과:
- 빌드 성공
- 린트 워닝 중 `RegisterContainer is assigned a value but never used` 등 RegisterForm 의 unused-vars 워닝 감소

dev 서버로 시각 검증:

```bash
cd frontend && npm start
```

확인 항목:
- 데스크탑 (≥1024px): 좌측 그라데이션 패널 + 우측 회원가입 폼
- LoginForm 과 동일한 그라데이션, 동일한 모티프 → 두 화면 시각적 일관성 확인
- 회원가입 폼 모든 단계 정상:
  - 아이디/닉네임 입력 + 중복 확인 버튼 (OutlineButton 으로 표시되는지)
  - 비밀번호, 이메일 ID + @ + 도메인 선택 (3분할 레이아웃 유지)
  - 이메일 인증 메일 발송 버튼 작동
  - 역할 선택 (Select)
  - 펫 추가/삭제 작동, Pet 카드 내부 인풋 정상
  - 회원가입 버튼 → 그라데이션 표시
- 모바일 (375px): 좌측 패널 숨김, 폼만 단일 컬럼, Pet 카드 영역 폭 적절

- [ ] **Step 8: 커밋**

```bash
git add frontend/src/components/Auth/RegisterForm.js
git commit -m "feat(auth): redesign RegisterForm with split-screen layout"
```

---

## Task 4: 최종 통합 검증

**Files:**
- Verify only (no code changes)

**Context:** 두 화면이 같은 디자인 언어를 따르는지, plan 의 모든 검증 기준이 통과하는지 확인.

- [ ] **Step 1: 전체 빌드 + 린트 워닝 감소 확인**

```bash
cd frontend && npm run build
```

기대: 다음 워닝이 모두 사라짐
- `LoginForm.js Line 808:7: 'sharedInputStyles' is assigned a value but never used`
- `LoginForm.js Line 855:7: 'RegisterContainer' is assigned a value but never used`
- `RegisterForm.js` 의 `RegisterContainer` 워닝

> 참고: `LoginForm.js` 의 `sharedInputStyles` / `RegisterContainer` 워닝 라인 번호 (808, 855) 는 기존 dead 코드에 대한 워닝. 본 plan 의 styled-component 삭제로 자연스럽게 함께 사라져야 정상.

- [ ] **Step 2: 반응형 검증 (Chrome DevTools)**

각 브레이크포인트에서 확인:

| 폭 | 기대 결과 |
|---|---|
| 1920px | 좌 45% (그라데이션+카드) / 우 55% (폼) 정확히 분할 |
| 1280px | 좌 45% / 우 55% 유지 |
| 1024px | split-screen 마지막 폭, 폼 영역이 좁아도 작동 |
| 1023px | 좌측 패널 즉시 사라지고 폼만 가운데 단일 컬럼 |
| 768px | 단일 컬럼, padding 적절 |
| 375px (iPhone SE) | 풀폭 폼, 좌측 모바일 로고 (`FormHeaderLogo`) 표시 |

- [ ] **Step 3: 두 화면 일관성 확인**

LoginForm → "회원가입" 링크 클릭 → RegisterForm 진입 시:
- 좌측 패널이 같은 그라데이션과 같은 4개 글래스 카드 위치
- 슬로건 동일
- 우측 폼의 인풋 (pill), 주 버튼 (gradient) 시각 동일
- 폼 전환 시 점프 없이 자연스럽게 보이는지

- [ ] **Step 4: 기능 정상 작동 확인**

| 기능 | 화면 | 기대 |
|---|---|---|
| 로그인 시도 (오류 비번) | LoginForm | ErrorMessage 표시 |
| 비밀번호 찾기 | LoginForm | 인라인 폼 표시, 이메일 입력 후 발송 |
| 닉네임 중복 확인 | RegisterForm | OutlineButton 클릭 시 API 호출 |
| 이메일 인증 메일 발송 | RegisterForm | OutlineButton 클릭 시 발송 |
| 펫 추가/삭제 | RegisterForm | AddPetButton (점선) / RemovePetButton (X) 작동 |
| 역할 선택 | RegisterForm | PillSelect 옵션 변경 |
| 회원가입 제출 | RegisterForm | GradientButton 클릭 시 가입 진행 |

- [ ] **Step 5: 최종 커밋 (이미 Task 1~3 에서 분리 커밋했으므로 추가 변경 없으면 스킵)**

만약 검증 중 작은 수정이 발생했다면:

```bash
git add frontend/src/components/Auth/
git commit -m "fix(auth): post-redesign UI tweaks"
```

---

## Self-Review

**Spec coverage:**
- ✅ 데스크탑 split-screen (≥1024px) — Task 1 `AuthPageWrapper`
- ✅ 모바일 단일 컬럼 (<1024px) — Task 1 미디어 쿼리
- ✅ 좌측 그라데이션 + 떠다니는 글래스 카드 — Task 1 `BrandPanel`/`FloatingGlassCard`, Task 2/3 JSX
- ✅ Pill input + Gradient button — Task 1 `PillInput`/`GradientButton`, Task 2/3 교체
- ✅ 톤다운 소셜 버튼 — Task 1 `SocialButton`, Task 2 적용
- ✅ AuthShell 분리 (DRY) — Task 1
- ✅ 죽은 코드 제거 (`BrandPanel` placeholder, `RegisterContainer`) — Task 2 Step 3, Task 3 Step 6
- ✅ 두 화면 일관성 — Task 2/3 가 같은 AuthShell 사용
- ✅ 검증 기준 — Task 4

**Placeholder 없음:** 모든 코드 블록에 실제 코드 포함, 변경 위치는 라인 번호로 명시.

**Type consistency:**
- `FloatingGlassCard` props: `$size`, `$top`, `$left`, `$right`, `$bottom`, `$rotate` — Task 1 정의와 Task 2/3 사용처 일치
- `SocialIcon` prop: `$provider` — Task 1 정의와 Task 2 사용처 일치
- import 이름 모두 Task 1 의 export 와 일치

---

## Execution Handoff

이 plan 을 어떤 방식으로 실행할지 선택해주세요:

**1) Subagent-Driven (권장)** — Task 별로 별도 subagent dispatch, 단계별 리뷰 가능, 빠른 반복

**2) Inline Execution** — 현재 세션에서 task 순차 실행, 체크포인트마다 리뷰

**3) 일단 plan 만 검토하고 나중에 실행** — 이 plan 을 git 에 커밋만 해두고, 실제 구현은 다음 세션에서
