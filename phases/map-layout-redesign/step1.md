# Step 1: Navigation.js — 데스크톱 사이드바 → 상단 TopBar 전환

## 목표

`frontend/src/components/Layout/Navigation.js` 에서 데스크톱 전용 `Sidebar` (width: 240px, 세로 배치)를 상단 `TopBar` (height: 60px, 가로 배치)로 전환한다. 모바일 `BottomNav`는 완전히 그대로 유지한다.

## 변경 대상 파일

- **Modify**: `frontend/src/components/Layout/Navigation.js`

## 배경

App.js의 `AppLayout`은 `display: flex; flex-direction: row`이고 `ContentArea`는 현재 `margin-left: 240px`로 사이드바 폭을 피한다. Step 2에서 ContentArea를 `margin-top: 60px`로 바꾸기 때문에 이 Step에서는 Navigation만 수정한다.

현재 Sidebar는 `position: fixed; left: 0; top: 0; width: 240px; height: 100vh; flex-direction: column`이다.

---

## 변경 내용

### 1. `slideInLeft` keyframes → `slideInDown` 으로 교체

기존:
```js
const slideInLeft = keyframes`
  from { opacity: 0; transform: translateX(-8px); }
  to   { opacity: 1; transform: translateX(0); }
`;
```

교체:
```js
const slideInDown = keyframes`
  from { opacity: 0; transform: translateY(-8px); }
  to   { opacity: 1; transform: translateY(0); }
`;
```

파일 내 `slideInLeft` 참조 두 곳(`SidebarNotificationDropdown`, `SidebarProfileDropdown`)도 `slideInDown`으로 교체한다.

---

### 2. `Sidebar` styled component 교체

기존:
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
  overflow-y: auto;
  overflow-x: hidden;

  @media (max-width: 768px) {
    display: none;
  }
`;
```

교체:
```js
const Sidebar = styled.nav`
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  height: 60px;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  z-index: 100;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 16px;
  gap: 8px;
  overflow: visible;

  @media (max-width: 768px) {
    display: none;
  }
`;
```

---

### 3. `LogoArea` — 컴팩트하게

기존:
```js
const LogoArea = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  padding: 24px 20px 16px;
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  color: ${props => props.theme.colors.primary};
  cursor: pointer;
  flex-shrink: 0;

  .icon { font-size: 22px; }
`;
```

교체:
```js
const LogoArea = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px 0 0;
  font-size: 17px;
  font-weight: 700;
  color: ${props => props.theme.colors.primary};
  cursor: pointer;
  flex-shrink: 0;
  white-space: nowrap;

  .icon { font-size: 20px; }
`;
```

---

### 4. `MenuList` — 가로 배치로

기존:
```js
const MenuList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 12px;
  flex: 1;
`;
```

교체:
```js
const MenuList = styled.div`
  display: flex;
  flex-direction: row;
  gap: 4px;
  padding: 0;
  flex: 1;
  justify-content: center;
  align-items: center;
`;
```

---

### 5. `MenuItem` — 가로 버튼으로

기존:
```js
const MenuItem = styled.button`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  width: 100%;
  padding: 10px 16px;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
  font-size: ${props => props.theme.typography.body1.fontSize};
  border-left: 3px solid ${props => props.$active ? props.theme.colors.primary : 'transparent'};
  background: ${props => props.$active ? props.theme.colors.primarySoft : 'transparent'};
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.text};
  font-weight: ${props => props.$active ? '600' : '400'};

  .menu-icon { font-size: 18px; flex-shrink: 0; }

  &:hover {
    background: ${props => props.$active ? props.theme.colors.primarySoft : props.theme.colors.surfaceHover};
  }
`;
```

교체:
```js
const MenuItem = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: ${props => props.theme.typography.body2.fontSize};
  background: ${props => props.$active ? props.theme.colors.primarySoft : 'transparent'};
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.text};
  font-weight: ${props => props.$active ? '600' : '400'};
  white-space: nowrap;

  .menu-icon { font-size: 16px; flex-shrink: 0; }

  &:hover {
    background: ${props => props.$active ? props.theme.colors.primarySoft : props.theme.colors.surfaceHover};
  }
`;
```

---

### 6. `BottomSection` — 우측 액션 그룹으로

기존:
```js
const BottomSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px 16px;
  flex-shrink: 0;
`;
```

교체:
```js
const BottomSection = styled.div`
  display: flex;
  flex-direction: row;
  gap: 4px;
  padding: 0;
  flex-shrink: 0;
  align-items: center;
`;
```

---

### 7. `SidebarActionButton` — 컴팩트 아이콘+텍스트

기존:
```js
const SidebarActionButton = styled.button`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  width: 100%;
  padding: 10px 16px;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  background: transparent;
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body1.fontSize};
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
  position: relative;

  &:hover { background: ${props => props.theme.colors.surfaceHover}; }
`;
```

교체:
```js
const SidebarActionButton = styled.button`
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  background: transparent;
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  white-space: nowrap;

  &:hover { background: ${props => props.theme.colors.surfaceHover}; }
`;
```

---

### 8. `SidebarBadge` — 위치 조정 (top-right of button)

기존:
```js
const SidebarBadge = styled.span`
  position: absolute;
  top: 6px;
  left: 26px;
  ...
`;
```

교체:
```js
const SidebarBadge = styled.span`
  position: absolute;
  top: 4px;
  right: 4px;
  background: ${props => props.theme.colors.error || '#ef4444'};
  color: white;
  border-radius: ${props => props.theme.borderRadius.full};
  min-width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  padding: 0 4px;
`;
```

---

### 9. `SidebarNotificationDropdown` — 드롭다운 위치 수정

기존 (left: 240px 기준, 하단):
```js
const SidebarNotificationDropdown = styled.div`
  position: fixed;
  left: 240px;
  top: auto;
  width: 360px;
  max-width: calc(100vw - 240px);
  max-height: 450px;
  ...
  animation: ${slideInLeft} 0.2s ease-out;
  bottom: 120px;
`;
```

교체 (top: 60px 기준, 우측 끝):
```js
const SidebarNotificationDropdown = styled.div`
  position: fixed;
  top: 60px;
  right: 0;
  left: auto;
  bottom: auto;
  width: 360px;
  max-width: calc(100vw - 16px);
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
`;
```

---

### 10. `SidebarProfileDropdown` — 드롭다운 위치 수정

기존 (left: 240px 기준, 하단):
```js
const SidebarProfileDropdown = styled.div`
  position: fixed;
  left: 240px;
  width: 220px;
  ...
  animation: ${slideInLeft} 0.2s ease-out;
  bottom: 60px;
`;
```

교체:
```js
const SidebarProfileDropdown = styled.div`
  position: fixed;
  top: 60px;
  right: 0;
  left: auto;
  bottom: auto;
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

---

### 11. `ProfileSection` — 가로 배치, border-left 구분선

기존:
```js
const ProfileSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-top: 1px solid ${props => props.theme.colors.border};
  padding-top: 8px;
  position: relative;
`;
```

교체:
```js
const ProfileSection = styled.div`
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 0;
  border-left: 1px solid ${props => props.theme.colors.border};
  padding-left: 8px;
  position: relative;
`;
```

---

### 12. `ProfileButton` — 컴팩트

기존:
```js
const ProfileButton = styled.button`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  width: 100%;
  padding: 10px 16px;
  ...
`;
```

교체:
```js
const ProfileButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  background: ${props => props.$active ? props.theme.colors.surfaceHover : 'transparent'};
  color: ${props => props.theme.colors.text};
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;

  &:hover { background: ${props => props.theme.colors.surfaceHover}; }
`;
```

---

### 13. `ProfileInfo` — 닉네임만 표시, 최대폭 제한

기존:
```js
const ProfileInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
`;
```

교체:
```js
const ProfileInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
  max-width: 90px;
`;
```

---

### 14. `LogoutButton` — 프로필 드롭다운 안으로 이동

**JSX 변경:** `ProfileSection` 안에 독립적으로 렌더링되던 `<LogoutButton>` 버튼을 제거하고, `SidebarProfileDropdown` 내부 마지막 `ProfileMenuItem`으로 추가한다.

기존 JSX (`ProfileSection` 내부):
```jsx
<ProfileSection ref={profileRef}>
  <Divider />
  <ProfileButton ...>...</ProfileButton>
  {isProfileDropdownOpen && (
    <SidebarProfileDropdown>
      <ProfileMenuItem onClick={...}>👤 프로필보기</ProfileMenuItem>
      <ProfileMenuItem onClick={...}>💰 코인충전</ProfileMenuItem>
      <ProfileMenuItem onClick={...}>📋 거래내역</ProfileMenuItem>
      <ProfileMenuItem onClick={...}>📌 내활동보기</ProfileMenuItem>
    </SidebarProfileDropdown>
  )}
  <LogoutButton type="button" onClick={logout}>로그아웃</LogoutButton>
</ProfileSection>
```

교체 JSX:
```jsx
<ProfileSection ref={profileRef}>
  <ProfileButton
    type="button"
    onClick={() => {
      setIsProfileDropdownOpen(prev => !prev);
      setIsNotificationOpen(false);
    }}
    $active={isProfileDropdownOpen}
  >
    <span>👤</span>
    <ProfileInfo>
      <ProfileNicknameText>{user.nickname || '내 정보'}</ProfileNicknameText>
      <ProfileCoinText>💰 {(user.petCoinBalance ?? 0).toLocaleString()}</ProfileCoinText>
    </ProfileInfo>
  </ProfileButton>
  {isProfileDropdownOpen && (
    <SidebarProfileDropdown>
      <ProfileMenuItem onClick={() => { setIsProfileDropdownOpen(false); setIsProfileOpen(true); }}>
        👤 프로필보기
      </ProfileMenuItem>
      <ProfileMenuItem onClick={() => { setIsProfileDropdownOpen(false); setIsChargePageOpen(true); }}>
        💰 코인충전
      </ProfileMenuItem>
      <ProfileMenuItem onClick={() => { setIsProfileDropdownOpen(false); setIsTransactionListOpen(true); }}>
        📋 거래내역
      </ProfileMenuItem>
      <ProfileMenuItem onClick={() => { setIsProfileDropdownOpen(false); setActiveTab('activity'); }}>
        📌 내활동보기
      </ProfileMenuItem>
      <ProfileMenuItem onClick={logout} style={{ color: 'inherit', borderTop: '1px solid rgba(120,113,108,0.2)', marginTop: 4, paddingTop: 8 }}>
        ↩ 로그아웃
      </ProfileMenuItem>
    </SidebarProfileDropdown>
  )}
</ProfileSection>
```

`<Divider />` 컴포넌트는 TopBar에서 불필요하므로 `ProfileSection` 내부에서 제거한다 (`Divider` styled component 정의 자체는 유지해도 무방).

---

## 검증

```bash
cd /Users/maknkkong/project/Petory/frontend
npm start
```

브라우저에서 확인:
- 상단에 수평 네비게이션 바 (60px)가 표시됨
- 로고(좌) | 메뉴항목(중앙) | 알림·다크모드·프로필(우) 배치 확인
- 알림 클릭 시 드롭다운이 TopBar 바로 아래(top: 60px) 우측에 열림
- 프로필 클릭 시 드롭다운에 "로그아웃" 항목 포함 확인
- 모바일(< 768px)에서 TopBar 숨김, BottomNav 표시 확인
