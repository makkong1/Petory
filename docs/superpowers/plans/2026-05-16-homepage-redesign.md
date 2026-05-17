# 홈페이지 리디자인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 탭 전환 구조 홈을 도메인 4개 섹션(실종·서비스·모임·커뮤니티) 세로 배치 + 가로 스크롤 카드로 교체한다.

**Architecture:** `HomePage.js` 단일 파일 전면 교체. 마운트 시 4개 API 병렬 fetch. `SectionRow` 재사용 컴포넌트로 4개 섹션을 동일 패턴으로 렌더링. 카드·섹션 헤더 클릭 시 `setActiveTab()` 호출로 해당 탭 이동.

**Tech Stack:** React 19, styled-components, 기존 API 모듈 (missingPetApi, locationServiceApi, meetupApi, boardApi)

---

### Task 1: 병렬 fetch 로직으로 교체

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

현재 `tabData` / `tabLoading` / `fetchedTabsRef` / `fetchTabData` 등 탭 기반 lazy fetch 로직을 제거하고, 마운트 시 4개 섹션 데이터를 한 번에 병렬 fetch하는 구조로 교체한다.

- [ ] **Step 1: state 선언부 교체**

`useState` / `useEffect` / `useCallback` import는 유지. 아래 state로 교체:

```jsx
const [sections, setSections] = useState({
  missing:   { items: [], loading: true, error: false },
  service:   { items: [], loading: true, error: false },
  meetup:    { items: [], loading: true, error: false },
  community: { items: [], loading: true, error: false },
});
const [userCoords, setUserCoords] = useState(null);
```

기존 `activeTab`, `tabData`, `tabLoading`, `tabError`, `fetchedTabsRef` state/ref 전부 삭제.

- [ ] **Step 2: 위치 useEffect는 그대로 유지**

```jsx
useEffect(() => {
  if (!navigator.geolocation) return;
  navigator.geolocation.getCurrentPosition(
    (pos) => setUserCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
    () => setUserCoords(null),
    { timeout: 5000, maximumAge: 60000 }
  );
}, []);
```

- [ ] **Step 3: 병렬 fetch useEffect 추가**

```jsx
useEffect(() => {
  const toArr = (v) => (Array.isArray(v) ? v : []);
  const setSection = (key, items) =>
    setSections((prev) => ({ ...prev, [key]: { items: items.slice(0, 4), loading: false, error: false } }));
  const setError = (key) =>
    setSections((prev) => ({ ...prev, [key]: { items: [], loading: false, error: true } }));

  // 실종신고
  missingPetApi
    .getHomeMissing(userCoords?.lat ?? null, userCoords?.lng ?? null, 6)
    .then((res) => setSection('missing', toArr(res.data?.boards ?? res.data)))
    .catch(() => setError('missing'));

  // 주변 서비스
  const serviceParams = { sort: 'score', size: 6 };
  if (userCoords) {
    serviceParams.latitude = userCoords.lat;
    serviceParams.longitude = userCoords.lng;
    serviceParams.radius = 10000;
  }
  locationServiceApi
    .searchPlaces(serviceParams)
    .then((res) => setSection('service', toArr(res.data?.services ?? res.data?.results ?? res.data)))
    .catch(() => setError('service'));

  // 모임
  meetupApi
    .getHomeMeetups(userCoords?.lat ?? null, userCoords?.lng ?? null, 6)
    .then((res) => setSection('meetup', toArr(res.data?.meetups ?? res.data?.content ?? res.data)))
    .catch(() => setError('meetup'));

  // 커뮤니티
  boardApi
    .getPopularBoards('WEEKLY')
    .then((res) => setSection('community', toArr(res.data?.boards ?? res.data?.content ?? res.data)))
    .catch(() => setError('community'));
}, [userCoords]);
```

- [ ] **Step 4: 빌드 확인**

```bash
cd frontend && npx react-scripts build --quiet 2>&1 | grep -E "^src.*error" | head -20
```

에러 없으면 통과.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/Home/HomePage.js
git commit -m "refactor(home): 탭 lazy fetch → 4개 병렬 fetch"
```

---

### Task 2: SectionRow 컴포넌트 + 가로 스크롤 카드

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: SectionRow 컴포넌트 작성**

기존 `TabContent` 함수 제거 후 아래로 교체:

```jsx
const SectionRow = ({ title, emoji, color, items, loading, onViewAll, getLabel }) => (
  <SectionWrap>
    <SectionHeader>
      <SectionLabel $color={color}>{emoji} {title}</SectionLabel>
      <ViewAllBtn onClick={onViewAll}>전체보기 →</ViewAllBtn>
    </SectionHeader>
    {loading ? (
      <HScroll>
        {[1, 2, 3, 4].map((i) => <SkeletonCard key={i} />)}
      </HScroll>
    ) : items.length === 0 ? (
      <EmptyRow>등록된 항목이 없어요</EmptyRow>
    ) : (
      <HScroll>
        {items.map((item, idx) => {
          const { title: cardTitle, sub } = getLabel(item);
          return (
            <HCard key={idx} $color={color} onClick={onViewAll}>
              <HCardImg $color={color} />
              <HCardBody>
                <HCardTitle>{cardTitle}</HCardTitle>
                <HCardSub>{sub}</HCardSub>
              </HCardBody>
            </HCard>
          );
        })}
      </HScroll>
    )}
  </SectionWrap>
);
```

- [ ] **Step 2: JSX return 교체**

기존 `TabsWrap` / `TabContent` 렌더 부분을 아래로 교체:

```jsx
return (
  <PageWrapper>
    <PageContainer>
      <Header>
        <HeaderLeft>
          <Avatar>{nickname.charAt(0)}</Avatar>
          <HeaderText>
            <Greeting>안녕하세요, {nickname}님! 🐾</Greeting>
            <SubGreeting>오늘도 함께해서 행복해요</SubGreeting>
          </HeaderText>
        </HeaderLeft>
        <NotificationBtn>🔔</NotificationBtn>
      </Header>

      <SectionRow
        title="실종신고" emoji="🔴" color="#EF4444"
        items={sections.missing.items}
        loading={sections.missing.loading}
        onViewAll={() => setActiveTab('missing-pets')}
        getLabel={(item) => ({
          title: item.petName || item.title || '',
          sub: [item.breed, item.lostDate].filter(Boolean).join(' · '),
        })}
      />
      <SectionRow
        title="주변 서비스" emoji="📍" color="#3B82F6"
        items={sections.service.items}
        loading={sections.service.loading}
        onViewAll={() => setActiveTab('unified-map')}
        getLabel={(item) => ({
          title: item.name || '',
          sub: item.category || '',
        })}
      />
      <SectionRow
        title="모임" emoji="👥" color="#10B981"
        items={sections.meetup.items}
        loading={sections.meetup.loading}
        onViewAll={() => setActiveTab('unified-map')}
        getLabel={(item) => ({
          title: item.title || '',
          sub: `${item.currentParticipants ?? 0}/${item.maxParticipants ?? 0}명`,
        })}
      />
      <SectionRow
        title="커뮤니티" emoji="💬" color="#8B5CF6"
        items={sections.community.items}
        loading={sections.community.loading}
        onViewAll={() => setActiveTab('community')}
        getLabel={(item) => ({
          title: item.boardTitle || item.title || '',
          sub: `❤️ ${item.likeCount ?? 0}  👁 ${item.viewCount ?? 0}`,
        })}
      />

      {isAdmin && (
        <AdminSection>
          <AdminSectionTitle>🔧 관리자 기능</AdminSectionTitle>
          <AdminGrid>
            <AdminCard onClick={() => setActiveTab('admin')}>
              <AdminCardIcon>📥</AdminCardIcon>
              <AdminCardName>초기 데이터 로딩</AdminCardName>
            </AdminCard>
            <AdminCard onClick={() => setActiveTab('users')}>
              <AdminCardIcon>👥</AdminCardIcon>
              <AdminCardName>사용자 관리</AdminCardName>
            </AdminCard>
          </AdminGrid>
        </AdminSection>
      )}
    </PageContainer>
  </PageWrapper>
);
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npx react-scripts build --quiet 2>&1 | grep -E "^src.*error" | head -20
```

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/Home/HomePage.js
git commit -m "feat(home): SectionRow 컴포넌트 + 4개 섹션 JSX"
```

---

### Task 3: styled-components 교체

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

기존 `CardGrid`, `GridCard`, `GridCardImg`, `GridCardBody`, `GridCardTitle`, `GridCardSub`, `TabsWrap`, `TabBtn`, `HorizontalScroll`, `SmallCard` 등 더 이상 쓰지 않는 스타일 컴포넌트를 제거하고, 새 컴포넌트용 스타일을 추가한다.

- [ ] **Step 1: 기존 불필요 스타일 삭제**

아래 styled-components 전부 삭제:
- `CardGrid`, `GridCard`, `GridCardImg`, `GridCardBody`, `GridCardTitle`, `GridCardSub`
- `TabsWrap`, `TabBtn`
- `ContentArea`
- `SkeletonHero` (이미 없음)

- [ ] **Step 2: 새 스타일 추가**

파일 하단 styled-components 섹션에 추가:

```jsx
/* ── SectionRow ─────────────────────────────────────────────── */

const SectionWrap = styled.div`
  margin: 0 0 8px;
  background: ${(p) => p.theme.colors.surface};
  border-radius: 16px;
  margin: 8px 16px;
  padding: 14px 0 14px;
  box-shadow: ${(p) => p.theme.shadows.sm};
`;

const SectionHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px 10px;
`;

const SectionLabel = styled.span`
  font-size: 13px;
  font-weight: 800;
  color: ${(p) => p.$color};
`;

const ViewAllBtn = styled.button`
  background: none;
  border: none;
  font-size: 12px;
  color: ${(p) => p.theme.colors.textSecondary};
  cursor: pointer;
  padding: 0;
`;

const HScroll = styled.div`
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 0 14px;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const HCard = styled.div`
  flex-shrink: 0;
  width: 120px;
  border-radius: 12px;
  overflow: hidden;
  background: ${(p) => p.theme.colors.background};
  border: 1px solid ${(p) => p.theme.colors.border};
  cursor: pointer;
  transition: transform 150ms ease;
  &:hover { transform: translateY(-2px); }
`;

const HCardImg = styled.div`
  height: 80px;
  background: linear-gradient(
    135deg,
    ${(p) => p.$color}99 0%,
    ${(p) => p.$color}33 100%
  );
`;

const HCardBody = styled.div`
  padding: 8px 9px 9px;
`;

const HCardTitle = styled.div`
  font-size: 12px;
  font-weight: 600;
  color: ${(p) => p.theme.colors.text};
  margin-bottom: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const HCardSub = styled.div`
  font-size: 10px;
  color: ${(p) => p.theme.colors.textSecondary};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const EmptyRow = styled.div`
  font-size: 13px;
  color: ${(p) => p.theme.colors.textMuted};
  padding: 16px 14px;
`;

const SkeletonCard = styled.div`
  flex-shrink: 0;
  width: 120px;
  height: 130px;
  border-radius: 12px;
  background: linear-gradient(
    90deg,
    ${(p) => p.theme.colors.border} 25%,
    ${(p) => p.theme.colors.borderLight} 50%,
    ${(p) => p.theme.colors.border} 75%
  );
  background-size: 200px 100%;
  animation: shimmer 1.2s infinite;
  @keyframes shimmer {
    0%   { background-position: -200px 0; }
    100% { background-position: calc(200px + 100%) 0; }
  }
`;
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npx react-scripts build --quiet 2>&1 | grep -E "^src.*error" | head -20
```

에러 없으면 통과.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/Home/HomePage.js
git commit -m "style(home): SectionRow 스타일 추가, 미사용 컴포넌트 제거"
```

---

### Task 4: 최종 검증 및 푸시

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: 전체 빌드**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

`Compiled successfully` 확인.

- [ ] **Step 2: 동작 확인 체크리스트**

로컬 서버 `npm start` 후:
- [ ] 헤더 닉네임 표시됨
- [ ] 실종신고 섹션 로딩 스켈레톤 → 데이터 로드
- [ ] 주변 서비스 섹션 로딩 → 데이터 로드
- [ ] 모임 섹션 로딩 → 데이터 로드
- [ ] 커뮤니티 섹션 로딩 → 데이터 로드
- [ ] 카드 클릭 → 해당 탭 이동
- [ ] "전체보기 →" 클릭 → 해당 탭 이동
- [ ] 탭 버튼 UI 사라짐

- [ ] **Step 3: 푸시**

```bash
git push
```
