# Pages Redesign + Responsive Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply Figma-style design (glassmorphism cards, teracotta theme, pill shapes) to all remaining pages and add hybrid responsive layout — left sidebar on web (≥768px), bottom tabs on mobile.

**Architecture:** Styled-components media queries inside each component file. No new files created. Navigation.js converts from top horizontal bar to left vertical sidebar on web. App.js ContentArea shifts from `margin-top: 60px` to `margin-left: 240px` on web.

**Tech Stack:** React 19, styled-components, existing `theme.js` tokens only (no new tokens added)

---

## File Map

| File | Change Type | What Changes |
|------|-------------|--------------|
| `frontend/src/components/Layout/Navigation.js` | Modify | Top bar → left sidebar on web (≥769px) |
| `frontend/src/App.js` | Modify | ContentArea margin-left: 240px on web |
| `frontend/src/components/Auth/LoginForm.js` | Modify | Glass card, pill inputs, gradient button |
| `frontend/src/components/Auth/RegisterForm.js` | Modify | Same glass card pattern as LoginForm |
| `frontend/src/components/Community/CommunityBoard.js` | Modify | Card redesign, pill tabs, FAB, web 2-col grid |
| `frontend/src/components/MissingPet/MissingPetBoardPage.js` | Modify | Image card, status badge, web 3-col grid |
| `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js` | Modify | Pill tabs, glass info panels (minimal) |
| `frontend/src/components/Activity/ActivityPage.js` | Modify | Timeline cards, date headers, web centering |

---

## Task 0: Navigation.js — Left Sidebar on Web

**Files:**
- Modify: `frontend/src/components/Layout/Navigation.js`
- Modify: `frontend/src/App.js`

**Context:** Navigation.js currently renders a horizontal top bar on desktop (≥769px) and a bottom tab bar on mobile. We convert the top bar to a vertical left sidebar. The bottom tab bar stays unchanged. Dropdowns (notification, profile) must re-anchor from `top: 60px` to `left: 240px`.

- [ ] **Step 1: Convert `Sidebar` styled component to vertical left sidebar**

In `Navigation.js`, find the `Sidebar` styled component (around line 443) and replace its styles:

```js
const Sidebar = styled.nav`
  position: fixed;
  left: 0;
  top: 0;
  width: 240px;
  height: 100vh;
  background: ${props => props.theme.colors.surface};
  border-right: 1px solid ${props => props.theme.colors.border};
  z-index: 100;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: visible;

  @media (max-width: 768px) {
    display: none;
  }
`;
```

- [ ] **Step 2: Make LogoArea a top section with bottom border**

```js
const LogoArea = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 16px 16px;
  font-size: 18px;
  font-weight: 700;
  color: ${props => props.theme.colors.primary};
  cursor: pointer;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  flex-shrink: 0;

  .icon {
    font-size: 22px;
  }
`;
```

- [ ] **Step 3: Convert `MenuList` to vertical column**

```js
const MenuList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  flex: 1;
`;
```

- [ ] **Step 4: Update `MenuItem` for full-width vertical layout**

```js
const MenuItem = styled.button`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  width: 100%;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: ${props => props.theme.typography.body2.fontSize};
  background: ${props => props.$active ? props.theme.colors.primarySoft : 'transparent'};
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.text};
  font-weight: ${props => props.$active ? '600' : '400'};
  text-align: left;

  .menu-icon {
    font-size: 18px;
    flex-shrink: 0;
    width: 22px;
    text-align: center;
  }

  &:hover {
    background: ${props => props.$active ? props.theme.colors.primarySoft : props.theme.colors.surfaceHover};
  }
`;
```

- [ ] **Step 5: Convert `BottomSection` to vertical bottom controls**

```js
const BottomSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  flex-shrink: 0;
  border-top: 1px solid ${props => props.theme.colors.border};
`;
```

- [ ] **Step 6: Update `ProfileSection` border direction**

```js
const ProfileSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0;
  position: relative;
`;
```

- [ ] **Step 7: Re-anchor notification dropdown to open right of sidebar**

```js
const SidebarNotificationDropdown = styled.div`
  position: fixed;
  top: 0;
  left: 240px;
  bottom: auto;
  right: auto;
  width: 360px;
  max-width: calc(100vw - 256px);
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
    top: auto;
    left: 8px;
    right: 8px;
    bottom: calc(60px + env(safe-area-inset-bottom, 0px) + 8px);
    width: auto;
  }
`;
```

- [ ] **Step 8: Re-anchor profile dropdown to open right of sidebar**

```js
const SidebarProfileDropdown = styled.div`
  position: fixed;
  bottom: 8px;
  left: 248px;
  top: auto;
  right: auto;
  width: 220px;
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  border-radius: ${props => props.theme.borderRadius?.lg || '12px'};
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 200;
  overflow: hidden;
  animation: ${slideInDown} 0.2s ease-out;
`;
```

- [ ] **Step 9: Update App.js ContentArea for sidebar offset**

In `App.js`, find `ContentArea` (around line 278) and add web margin:

```js
const ContentArea = styled.main`
  flex: 1;
  min-height: 100vh;
  background: ${props => props.theme.colors.background};

  @media (min-width: 769px) {
    margin-left: 240px;
    margin-top: 0;
    min-height: 100vh;
  }

  @media (max-width: 768px) {
    margin-top: 0;
    padding-bottom: calc(60px + env(safe-area-inset-bottom, 0px));
    min-height: 100dvh;
  }
`;
```

- [ ] **Step 10: Build and visual check**

```bash
cd frontend && npm run build
```

Expected: build succeeds with no errors. Open `http://localhost:3000` — on web width ≥769px: left sidebar visible with logo + vertical menu items. On mobile: bottom tab bar. Check notification and profile dropdowns open correctly.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/components/Layout/Navigation.js frontend/src/App.js
git commit -m "feat(nav): convert web top bar to left sidebar, update ContentArea offset"
```

---

## Task 1: LoginForm.js — Glass Card Auth Design

**Files:**
- Modify: `frontend/src/components/Auth/LoginForm.js`

**Context:** LoginForm renders a form with id/password fields and a forgot password inline section. We add a gradient background, glass card, pill inputs, and gradient button. The forgot password modal is already inline — style it with the same glass card pattern.

- [ ] **Step 1: Add gradient background container**

Find the outermost container styled component in `LoginForm.js` (likely `LoginContainer` or similar) and replace/update its styles:

```js
const AuthPageWrapper = styled.div`
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 40%, #3D8B7A 100%);
  padding: 24px 16px;
`;
```

If the existing outermost container doesn't match, rename it to `AuthPageWrapper` and update the JSX return accordingly.

- [ ] **Step 2: Add glass card container**

```js
const GlassCard = styled.div`
  width: 100%;
  max-width: 440px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 40px 32px;
  box-shadow: 0 20px 60px rgba(28, 25, 23, 0.2);

  @media (prefers-color-scheme: dark) {
    background: rgba(29, 29, 29, 0.92);
  }
`;
```

Wrap the form content in `<GlassCard>` inside `AuthPageWrapper`.

- [ ] **Step 3: Add logo/brand header**

```js
const BrandHeader = styled.div`
  text-align: center;
  margin-bottom: 32px;
`;

const BrandIcon = styled.div`
  font-size: 48px;
  margin-bottom: 8px;
`;

const BrandTitle = styled.h1`
  font-size: 28px;
  font-weight: 700;
  color: ${props => props.theme.colors.primary};
  margin: 0 0 4px;
`;

const BrandSubtitle = styled.p`
  font-size: 14px;
  color: ${props => props.theme.colors.textSecondary};
  margin: 0;
`;
```

In the JSX, add above the form:

```jsx
<BrandHeader>
  <BrandIcon>🐾</BrandIcon>
  <BrandTitle>Petory</BrandTitle>
  <BrandSubtitle>반려동물과 함께하는 커뮤니티</BrandSubtitle>
</BrandHeader>
```

- [ ] **Step 4: Style input fields as pill shape with full border**

Find the existing input styled component and update:

```js
const StyledInput = styled.input`
  width: 100%;
  padding: 14px 18px;
  border: 1.5px solid ${props => props.theme.colors.border};
  border-radius: 50px;
  font-size: 15px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  outline: none;
  transition: border-color 0.2s ease;
  box-sizing: border-box;

  &:focus {
    border-color: ${props => props.theme.colors.primary};
  }

  &::placeholder {
    color: ${props => props.theme.colors.textMuted};
  }
`;
```

If the input is inside a label wrapper, keep the wrapper — only change the `input` styling.

- [ ] **Step 5: Style the submit button as gradient pill**

```js
const PrimaryButton = styled.button`
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%);
  color: white;
  border: none;
  border-radius: 50px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.1s ease;
  margin-top: 8px;

  &:hover {
    opacity: 0.9;
  }

  &:active {
    transform: scale(0.98);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;
```

- [ ] **Step 6: Style the secondary/switch link button**

```js
const SecondaryButton = styled.button`
  width: 100%;
  padding: 12px;
  background: transparent;
  color: ${props => props.theme.colors.primary};
  border: 1.5px solid ${props => props.theme.colors.primary};
  border-radius: 50px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: 8px;

  &:hover {
    background: ${props => props.theme.colors.primarySoft};
  }
`;
```

- [ ] **Step 7: Style the forgot password section**

The forgot password section is already inline (controlled by `showForgotPassword` state). Update its container to use the same pill/glass styling within the same card:

```js
const ForgotSection = styled.div`
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid ${props => props.theme.colors.border};
`;

const ForgotTitle = styled.h3`
  font-size: 16px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  margin: 0 0 16px;
`;
```

- [ ] **Step 8: Build and visual check**

```bash
cd frontend && npm run build
```

Expected: build succeeds. Open `http://localhost:3000` and log out. The login page should show gradient background + glass card + pill inputs + gradient submit button.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/Auth/LoginForm.js
git commit -m "feat(auth): redesign login page with glass card and pill inputs"
```

---

## Task 2: RegisterForm.js — Same Glass Card Pattern

**Files:**
- Modify: `frontend/src/components/Auth/RegisterForm.js`

**Context:** RegisterForm is 1241 lines with multiple steps (email, verification, profile, etc.). Apply the same `AuthPageWrapper` + `GlassCard` pattern. The inner form steps get the same pill inputs and gradient button. Do NOT change step logic or validation.

- [ ] **Step 1: Add `AuthPageWrapper` and `GlassCard` (same as LoginForm)**

Add at the top of styled components:

```js
const AuthPageWrapper = styled.div`
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 40%, #3D8B7A 100%);
  padding: 24px 16px;
`;

const GlassCard = styled.div`
  width: 100%;
  max-width: 440px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 40px 32px;
  box-shadow: 0 20px 60px rgba(28, 25, 23, 0.2);
`;
```

Wrap the existing outermost container's JSX children with `<AuthPageWrapper><GlassCard>...</GlassCard></AuthPageWrapper>`.

- [ ] **Step 2: Apply pill input style to all `<input>` styled components**

Find all styled `input` or `Input`-named components in the file and apply:

```js
border: 1.5px solid ${props => props.theme.colors.border};
border-radius: 50px;
padding: 14px 18px;
outline: none;
transition: border-color 0.2s ease;

&:focus {
  border-color: ${props => props.theme.colors.primary};
}
```

If there are multiple different input components (e.g., text vs password), apply to all of them.

- [ ] **Step 3: Apply gradient pill button to all submit/next buttons**

```js
background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%);
color: white;
border: none;
border-radius: 50px;
padding: 14px;
font-weight: 600;
cursor: pointer;
transition: opacity 0.2s ease;

&:hover { opacity: 0.9; }
&:disabled { opacity: 0.5; cursor: not-allowed; }
```

- [ ] **Step 4: Add brand header to first step only**

In the JSX for the first step (email entry step), add above the form title:

```jsx
<div style={{ textAlign: 'center', marginBottom: '24px' }}>
  <div style={{ fontSize: '40px', marginBottom: '8px' }}>🐾</div>
  <div style={{ fontSize: '24px', fontWeight: 700, color: '#E8714A' }}>Petory 가입</div>
</div>
```

- [ ] **Step 5: Build and visual check**

```bash
cd frontend && npm run build
```

Expected: build succeeds. Click "회원가입" on login page — should show same gradient background + glass card. All steps should show pill inputs.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/Auth/RegisterForm.js
git commit -m "feat(auth): apply glass card and pill inputs to register form"
```

---

## Task 3: CommunityBoard.js — Card Redesign + Web Grid

**Files:**
- Modify: `frontend/src/components/Community/CommunityBoard.js`

**Context:** 1929-line file. Find the post card styled component(s) and update border-radius, shadow, and hover. Add pill style to category tabs. Add FAB for write button. Add 2-col grid on web. Do NOT touch any API calls, modal logic, or pagination.

- [ ] **Step 1: Find and update the post card styled component**

Search for the card container (likely named `PostCard`, `BoardCard`, or similar). Update:

```js
const PostCard = styled.div`  /* use existing name */
  background: ${props => props.theme.colors.surfaceElevated};
  border-radius: 16px;
  padding: 20px;
  border: 1px solid ${props => props.theme.colors.borderLight};
  box-shadow: 0 2px 12px ${props => props.theme.colors.shadow};
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px ${props => props.theme.colors.shadowHover};
  }
`;
```

- [ ] **Step 2: Update category tab styled components to pill style**

Find the category tab/filter button (likely `CategoryButton`, `TabButton`, or similar):

```js
const CategoryButton = styled.button`  /* use existing name */
  padding: 8px 16px;
  border-radius: 50px;
  border: 1.5px solid ${props => props.$active ? props.theme.colors.primary : props.theme.colors.border};
  background: ${props => props.$active ? props.theme.colors.primary : 'transparent'};
  color: ${props => props.$active ? 'white' : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${props => props.$active ? '600' : '400'};
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  flex-shrink: 0;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.theme.colors.primary};
  }
`;
```

- [ ] **Step 3: Update post list container for web 2-col grid**

Find the container that wraps all post cards (likely `PostList`, `BoardList`, or similar):

```js
const PostList = styled.div`  /* use existing name */
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;

  @media (min-width: 769px) {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    padding: 24px;
    max-width: 1200px;
    margin: 0 auto;
  }
`;
```

- [ ] **Step 4: Convert write button to FAB (Floating Action Button)**

Find the write/create post button styled component and update it to a floating button:

```js
const WriteFAB = styled.button`  /* rename existing or add */
  position: fixed;
  bottom: calc(72px + env(safe-area-inset-bottom, 0px));
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%);
  color: white;
  border: none;
  font-size: 24px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(232, 113, 74, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  z-index: 50;

  &:hover {
    transform: scale(1.1);
    box-shadow: 0 6px 20px rgba(232, 113, 74, 0.5);
  }

  @media (min-width: 769px) {
    bottom: 32px;
    right: 32px;
  }
`;
```

Update the JSX to use `<WriteFAB>` instead of the existing write button. Change the button label to `+` (just the plus icon).

- [ ] **Step 5: Build and visual check**

```bash
cd frontend && npm run build
```

Expected: build succeeds. Open Community tab — cards have rounded corners + shadow + hover lift. Category tabs are pill shaped. FAB visible bottom-right. On web width, cards show in 2-column grid.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/Community/CommunityBoard.js
git commit -m "feat(community): glass cards, pill tabs, FAB, web 2-col grid"
```

---

## Task 4: MissingPetBoardPage.js — Image Cards + Status Badge + Web Grid

**Files:**
- Modify: `frontend/src/components/MissingPet/MissingPetBoardPage.js`

**Context:** 870-line file. Pet cards may or may not have images. Add image slot at top (aspect ratio 4:3), status badge overlay, and web 3-col grid. Same pill tab pattern for filters.

- [ ] **Step 1: Update pet card to image-first layout**

Find the card container (likely `PetCard`, `MissingCard`, or similar):

```js
const PetCard = styled.div`  /* use existing name */
  background: ${props => props.theme.colors.surfaceElevated};
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid ${props => props.theme.colors.borderLight};
  box-shadow: 0 2px 12px ${props => props.theme.colors.shadow};
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  position: relative;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px ${props => props.theme.colors.shadowHover};
  }
`;
```

- [ ] **Step 2: Add image area styled component**

```js
const CardImageArea = styled.div`
  width: 100%;
  aspect-ratio: 4 / 3;
  background: ${props => props.theme.colors.surfaceHover};
  overflow: hidden;
  position: relative;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

const CardImagePlaceholder = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  background: linear-gradient(135deg, ${props => props.theme.colors.domain?.missing || '#EF4444'}22, ${props => props.theme.colors.domain?.missing || '#EF4444'}44);
`;
```

In the card JSX, add above existing content:

```jsx
<CardImageArea>
  {petItem.imageUrl
    ? <img src={petItem.imageUrl} alt={petItem.petName} onError={e => { e.target.style.display = 'none'; }} />
    : <CardImagePlaceholder>🐾</CardImagePlaceholder>
  }
</CardImageArea>
```

Use the actual field name from the existing data (check what the card already renders for image — may be `imageUrl`, `photo`, `thumbnail`, etc.).

- [ ] **Step 3: Add status badge**

```js
const StatusBadge = styled.span`
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 10px;
  border-radius: 50px;
  font-size: 11px;
  font-weight: 700;
  background: ${props => props.$found
    ? props.theme.colors.successSoft
    : props.theme.colors.errorSoft};
  color: ${props => props.$found
    ? props.theme.colors.success
    : props.theme.colors.error};
  border: 1px solid ${props => props.$found
    ? props.theme.colors.success + '44'
    : props.theme.colors.error + '44'};
`;
```

In the `CardImageArea` JSX, add the badge (check the actual field name for found/missing status — may be `status`, `found`, `isFound`):

```jsx
<StatusBadge $found={petItem.status === 'FOUND'}>
  {petItem.status === 'FOUND' ? '발견됨' : '실종중'}
</StatusBadge>
```

- [ ] **Step 4: Add card info area**

```js
const CardInfo = styled.div`
  padding: 14px 16px;
`;

const CardPetName = styled.div`
  font-size: 16px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin-bottom: 4px;
`;

const CardMeta = styled.div`
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
`;
```

- [ ] **Step 5: Update list container for web 3-col grid**

Find the card list wrapper:

```js
const PetList = styled.div`  /* use existing name */
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;

  @media (min-width: 769px) {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    padding: 24px;
    max-width: 1200px;
    margin: 0 auto;
  }
`;
```

- [ ] **Step 6: Apply pill style to filter tabs (same pattern as CommunityBoard Task 3 Step 2)**

Find filter/status filter buttons and apply:

```js
padding: 8px 16px;
border-radius: 50px;
border: 1.5px solid ${props => props.$active ? props.theme.colors.domain.missing : props.theme.colors.border};
background: ${props => props.$active ? props.theme.colors.domain.missing : 'transparent'};
color: ${props => props.$active ? 'white' : props.theme.colors.textSecondary};
font-size: 13px;
font-weight: ${props => props.$active ? '600' : '400'};
cursor: pointer;
transition: all 0.2s ease;
```

- [ ] **Step 7: Add FAB for reporting (same pattern as CommunityBoard Task 3 Step 4)**

```js
const ReportFAB = styled.button`
  position: fixed;
  bottom: calc(72px + env(safe-area-inset-bottom, 0px));
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%);
  color: white;
  border: none;
  font-size: 24px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(239, 68, 68, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease;
  z-index: 50;

  &:hover { transform: scale(1.1); }

  @media (min-width: 769px) {
    bottom: 32px;
    right: 32px;
  }
`;
```

- [ ] **Step 8: Build and visual check**

```bash
cd frontend && npm run build
```

Expected: build succeeds. Open Missing Pet tab — cards show image area (placeholder if no image), status badge in top-right corner. Web width shows 3-col grid.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/MissingPet/MissingPetBoardPage.js
git commit -m "feat(missing-pet): image cards, status badges, FAB, web 3-col grid"
```

---

## Task 5: UnifiedPetMapPage.js — Glass UI Controls (Minimal)

**Files:**
- Modify: `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`
- Modify: `frontend/src/components/UnifiedMap/DomainTabHeader.js`
- Modify: `frontend/src/components/UnifiedMap/RadiusFilter.js`
- Modify: `frontend/src/components/UnifiedMap/shared/BaseInfoPanel.js`

**Context:** Map occupies full screen — layout stays unchanged. Only the floating UI control panels get the glass card treatment. Pill tabs for domain switching. Do NOT touch map rendering, Kakao Map API calls, or layer logic.

- [ ] **Step 1: Update DomainTabHeader tab buttons to pill style**

In `DomainTabHeader.js`, find the tab button styled component:

```js
const TabButton = styled.button`  /* use existing name */
  padding: 8px 16px;
  border-radius: 50px;
  border: 1.5px solid ${props => props.$active
    ? props.theme.colors.domain[props.$domain] || props.theme.colors.primary
    : props.theme.colors.border};
  background: ${props => props.$active
    ? props.theme.colors.domain[props.$domain] || props.theme.colors.primary
    : 'rgba(255,255,255,0.85)'};
  color: ${props => props.$active ? 'white' : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${props => props.$active ? '600' : '400'};
  cursor: pointer;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
  white-space: nowrap;
`;
```

Update the tab container to add glass background:

```js
const TabContainer = styled.div`  /* use existing name */
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 50px;
  box-shadow: 0 4px 16px rgba(28, 25, 23, 0.12);
`;
```

- [ ] **Step 2: Update RadiusFilter to pill chip style**

In `RadiusFilter.js`, find radius option buttons:

```js
const RadiusChip = styled.button`  /* use existing name */
  padding: 6px 14px;
  border-radius: 50px;
  border: 1.5px solid ${props => props.$active ? props.theme.colors.primary : props.theme.colors.border};
  background: ${props => props.$active ? props.theme.colors.primarySoft : 'rgba(255,255,255,0.85)'};
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.textSecondary};
  font-size: 12px;
  font-weight: ${props => props.$active ? '600' : '400'};
  cursor: pointer;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
  white-space: nowrap;
`;
```

- [ ] **Step 3: Apply glass card to BaseInfoPanel**

In `BaseInfoPanel.js`, find the panel container:

```js
const PanelContainer = styled.div`  /* use existing name */
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(28, 25, 23, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.5);
  overflow: hidden;
`;
```

- [ ] **Step 4: Build and visual check**

```bash
cd frontend && npm run build
```

Expected: build succeeds. Open Map tab — domain tabs are pill-shaped with glass container. Radius filter chips are pill-shaped. Info panels have glass card appearance.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/UnifiedMap/DomainTabHeader.js frontend/src/components/UnifiedMap/RadiusFilter.js frontend/src/components/UnifiedMap/shared/BaseInfoPanel.js
git commit -m "feat(map): glass UI controls, pill domain tabs, radius chip filters"
```

---

## Task 6: ActivityPage.js — Timeline Cards + Web Centering

**Files:**
- Modify: `frontend/src/components/Activity/ActivityPage.js`

**Context:** 598-line file. Activity items are rendered in a list. Add card styling, left-side icon, date section separators. On web, center the feed with max-width 680px.

- [ ] **Step 1: Update page wrapper for web centering**

Find the outermost page container (likely `ActivityContainer`, `PageWrapper`, or similar):

```js
const ActivityContainer = styled.div`  /* use existing name */
  padding: 16px;
  min-height: 100vh;
  background: ${props => props.theme.colors.background};

  @media (min-width: 769px) {
    max-width: 680px;
    margin: 0 auto;
    padding: 24px 16px;
  }
`;
```

- [ ] **Step 2: Update activity item card**

Find the item card component (likely `ActivityItem`, `ActivityCard`, or similar):

```js
const ActivityItem = styled.div`  /* use existing name */
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
  background: ${props => props.theme.colors.surfaceElevated};
  border-radius: 16px;
  border: 1px solid ${props => props.theme.colors.borderLight};
  box-shadow: 0 2px 8px ${props => props.theme.colors.shadow};
  transition: transform 0.15s ease;

  &:hover {
    transform: translateY(-1px);
  }
`;
```

- [ ] **Step 3: Add activity icon container**

```js
const ActivityIcon = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: ${props => props.theme.colors.primarySoft};
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
`;
```

In the JSX, wrap the leading icon/emoji in `<ActivityIcon>`.

- [ ] **Step 4: Add date section header**

```js
const DateSectionHeader = styled.div`
  font-size: 12px;
  font-weight: 600;
  color: ${props => props.theme.colors.textSecondary};
  padding: 8px 4px 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;
```

If activity items are grouped by date in the existing render logic, wrap each group with `<DateSectionHeader>{dateLabel}</DateSectionHeader>`. If not already grouped, add grouping in the render: group the array by date string (format: `YYYY-MM-DD`) before mapping.

- [ ] **Step 5: Update list container**

```js
const ActivityList = styled.div`  /* use existing name */
  display: flex;
  flex-direction: column;
  gap: 10px;
`;
```

- [ ] **Step 6: Build and visual check**

```bash
cd frontend && npm run build
```

Expected: build succeeds. Open Activity tab — items show as cards with icon on left. Date section headers separate groups. On web width, content is centered at max-width 680px.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/Activity/ActivityPage.js
git commit -m "feat(activity): timeline cards, date headers, web centering"
```

---

## Execution Order & Dependencies

```
Task 0 (Navigation + App.js)   ← must run first
    │
    ├── Task 1 (LoginForm)      ← parallel after Task 0
    ├── Task 2 (RegisterForm)   ← parallel after Task 0
    ├── Task 3 (Community)      ← parallel after Task 0
    ├── Task 4 (MissingPet)     ← parallel after Task 0
    ├── Task 5 (Map)            ← parallel after Task 0
    └── Task 6 (Activity)       ← parallel after Task 0
```

Tasks 1-6 are independent of each other — they can be dispatched as parallel subagents after Task 0 is merged/complete.
