# HomePage 리디자인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `HomePage.js` 를 Figma 모바일 UI 템플릿 스타일(글래스모피즘, 히어로 카드, 가로 스크롤)로 리디자인하되, App.js 탭 시스템은 건드리지 않는다.

**Architecture:** 단일 파일(`HomePage.js`) 내 styled-components. 상단 헤더/검색바/탭은 고정, 탭별 히어로+카드리스트만 교체. 각 탭 첫 진입 시 API 호출 후 useState 캐시.

**Tech Stack:** React 19, styled-components, existing API modules (locationServiceApi, meetupApi, missingPetApi, boardApi)

---

## 파일 매핑

| 파일 | 역할 |
|------|------|
| `frontend/src/components/Home/HomePage.js` | 전체 리디자인 (유일한 변경 파일) |

---

## Task 1: Layout Shell + 데이터 레이어

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: 기존 import + state 구조로 파일 상단 교체**

`HomePage.js` 상단을 다음으로 교체 (기존 import 3줄 대체):

```javascript
import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { locationServiceApi } from '../../api/locationServiceApi';
import { meetupApi } from '../../api/meetupApi';
import { missingPetApi } from '../../api/missingPetApi';
import { boardApi } from '../../api/boardApi';
```

- [ ] **Step 2: 컴포넌트 함수 상단에 state + fetch 로직 추가**

기존 `const services = [...]` 블록 전체를 지우고 아래로 교체:

```javascript
const TABS = [
  { key: 'service',   label: '주변서비스', domainColor: '#3B82F6' },
  { key: 'meetup',    label: '모임',       domainColor: '#10B981' },
  { key: 'missing',   label: '실종신고',   domainColor: '#EF4444' },
  { key: 'community', label: '커뮤니티',   domainColor: '#8B5CF6' },
];

const [activeTab, setActiveTabLocal] = useState('service');
const [tabData, setTabData] = useState({});
const [tabLoading, setTabLoading] = useState({});
const [tabError, setTabError] = useState({});

const fetchTabData = useCallback(async (tabKey) => {
  if (tabData[tabKey]) return; // 캐시 재사용
  setTabLoading(prev => ({ ...prev, [tabKey]: true }));
  setTabError(prev => ({ ...prev, [tabKey]: false }));
  try {
    let items = [];
    if (tabKey === 'service') {
      const res = await locationServiceApi.searchPlaces({ sort: 'rating', size: 6 });
      items = res.data?.results || res.data || [];
    } else if (tabKey === 'meetup') {
      const res = await meetupApi.getNearbyMeetups(37.5665, 126.9780, 50, 6);
      items = res.data || [];
    } else if (tabKey === 'missing') {
      const res = await missingPetApi.list({ page: 0, size: 6, status: 'MISSING' });
      items = res.data?.boards || res.data || [];
    } else if (tabKey === 'community') {
      const res = await boardApi.getPopularBoards('WEEKLY');
      items = res.data?.boards || res.data || [];
    }
    setTabData(prev => ({ ...prev, [tabKey]: items.slice(0, 6) }));
  } catch {
    setTabError(prev => ({ ...prev, [tabKey]: true }));
    setTabData(prev => ({ ...prev, [tabKey]: [] }));
  } finally {
    setTabLoading(prev => ({ ...prev, [tabKey]: false }));
  }
}, [tabData]);

useEffect(() => {
  fetchTabData(activeTab);
}, [activeTab, fetchTabData]);
```

- [ ] **Step 3: `PageContainer` 를 모바일 컨테이너로 교체**

기존 `PageContainer` styled-component를 찾아 아래로 교체:

```javascript
const PageWrapper = styled.div`
  min-height: 100vh;
  background: ${props => props.theme.colors.surfaceSoft};
  display: flex;
  justify-content: center;
`;

const PageContainer = styled.div`
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  background: ${props => props.theme.colors.background};
  overflow-x: hidden;
  padding-bottom: 24px;
`;
```

- [ ] **Step 4: return 문을 새 wrapper 구조로 교체 (아직 내용은 placeholder)**

```jsx
return (
  <PageWrapper>
    <PageContainer>
      {/* Task 2~8에서 채워짐 */}
    </PageContainer>
  </PageWrapper>
);
```

- [ ] **Step 5: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: 에러 없이 `Compiled successfully` 또는 warnings만

---

## Task 2: HomeHeader

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: `return` 문 안에 HomeHeader JSX 추가**

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
      {/* 이후 Task에서 추가 */}
    </PageContainer>
  </PageWrapper>
);
```

- [ ] **Step 2: Header styled-components 추가 (파일 하단)**

```javascript
const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 20px 12px;
`;

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`;

const Avatar = styled.div`
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: ${props => props.theme.colors.primary};
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
`;

const HeaderText = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const Greeting = styled.span`
  font-size: 16px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  line-height: 1.2;
`;

const SubGreeting = styled.span`
  font-size: 13px;
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.2;
`;

const NotificationBtn = styled.button`
  background: none;
  border: none;
  font-size: 22px;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
`;
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

Expected: `Compiled successfully`

---

## Task 3: SearchBar

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: Header 아래에 SearchBar JSX 추가**

```jsx
<SearchBarWrap>
  <SearchIcon>🔍</SearchIcon>
  <SearchInput placeholder="반려동물·케어·모임 검색..." readOnly />
  <FilterBtn>⚙️</FilterBtn>
</SearchBarWrap>
```

- [ ] **Step 2: SearchBar styled-components 추가**

```javascript
const SearchBarWrap = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 20px 20px;
  padding: 12px 16px;
  border: 1.5px solid ${props => props.theme.colors.border};
  border-radius: 9999px;
  background: ${props => props.theme.colors.surface};
`;

const SearchIcon = styled.span`
  font-size: 16px;
  flex-shrink: 0;
`;

const SearchInput = styled.input`
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font-size: 14px;
  color: ${props => props.theme.colors.textMuted};
  cursor: pointer;
  &::placeholder { color: ${props => props.theme.colors.textMuted}; }
`;

const FilterBtn = styled.button`
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
`;
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

---

## Task 4: CategoryTabs

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: SearchBar 아래에 CategoryTabs JSX 추가**

```jsx
<TabsWrap>
  {TABS.map(tab => (
    <TabBtn
      key={tab.key}
      $active={activeTab === tab.key}
      $color={tab.domainColor}
      onClick={() => setActiveTabLocal(tab.key)}
    >
      {tab.label}
    </TabBtn>
  ))}
</TabsWrap>
```

- [ ] **Step 2: Tab styled-components 추가**

```javascript
const TabsWrap = styled.div`
  display: flex;
  gap: 8px;
  padding: 0 20px 20px;
  overflow-x: auto;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const TabBtn = styled.button`
  flex-shrink: 0;
  padding: 8px 16px;
  border-radius: 9999px;
  border: 1.5px solid ${props => props.$active ? props.$color : props.theme.colors.border};
  background: ${props => props.$active ? props.$color : props.theme.colors.surface};
  color: ${props => props.$active ? '#fff' : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${props => props.$active ? 600 : 400};
  cursor: pointer;
  transition: all 150ms ease;
`;
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

---

## Task 5: HeroCard (글래스모피즘 대형 카드)

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: 탭 아래에 TabContent 영역 추가**

탭 JSX 아래에 추가:

```jsx
<TabContent
  tab={TABS.find(t => t.key === activeTab)}
  items={tabData[activeTab] || []}
  loading={tabLoading[activeTab]}
  error={tabError[activeTab]}
  onViewAll={() => {
    const tabToAppTab = {
      service: 'unified-map',
      meetup: 'unified-map',
      missing: 'missing-pets',
      community: 'community',
    };
    setActiveTab(tabToAppTab[activeTab]);
  }}
/>
```

- [ ] **Step 2: `getHeroItem` 헬퍼 함수 추가 (컴포넌트 함수 밖, TABS 상수 아래)**

```javascript
const getHeroItem = (tabKey, items) => {
  if (!items || items.length === 0) return null;
  const item = items[0];
  if (tabKey === 'service') {
    return {
      title: item.name,
      subtitle: item.category,
      badge: item.averageRating ? `⭐ ${item.averageRating}` : null,
      image: item.imageUrl || null,
    };
  }
  if (tabKey === 'meetup') {
    return {
      title: item.title,
      subtitle: `${item.location || ''} · ${item.currentParticipants || 0}/${item.maxParticipants || 0}명`,
      badge: item.status === 'RECRUITING' ? '모집중' : null,
      image: item.imageUrl || null,
    };
  }
  if (tabKey === 'missing') {
    return {
      title: item.petName || item.title,
      subtitle: `${item.breed || ''} · ${item.lostDate || ''}`,
      badge: '실종',
      image: item.imageUrl || null,
    };
  }
  if (tabKey === 'community') {
    return {
      title: item.title,
      subtitle: `❤️ ${item.likes || 0}  👁 ${item.views || 0}`,
      badge: item.category || null,
      image: item.boardFilePath || null,
    };
  }
  return null;
};
```

- [ ] **Step 3: `TabContent` 컴포넌트 추가 (컴포넌트 함수 밖)**

```javascript
const TabContent = ({ tab, items, loading, error, onViewAll }) => {
  const hero = getHeroItem(tab.key, items);

  if (loading) {
    return (
      <ContentArea>
        <SkeletonHero />
        <SectionHeader>
          <SkeletonText $w="120px" />
          <SkeletonText $w="60px" />
        </SectionHeader>
        <HorizontalScroll>
          {[1, 2, 3].map(i => <SkeletonSmallCard key={i} />)}
        </HorizontalScroll>
      </ContentArea>
    );
  }

  return (
    <ContentArea>
      {hero ? (
        <HeroCard $color={tab.domainColor} $image={hero.image}>
          <HeroOverlay />
          <HeroGlassPanel>
            <HeroTitle>{hero.title}</HeroTitle>
            <HeroSub>{hero.subtitle}</HeroSub>
            {hero.badge && <HeroBadge $color={tab.domainColor}>{hero.badge}</HeroBadge>}
          </HeroGlassPanel>
        </HeroCard>
      ) : (
        !error && <EmptyHero $color={tab.domainColor}>아직 등록된 항목이 없어요</EmptyHero>
      )}

      <SectionHeader>
        <SectionLabel>인기 {tab.label}</SectionLabel>
        <ViewAllBtn onClick={onViewAll}>전체보기 →</ViewAllBtn>
      </SectionHeader>

      {items.length > 1 ? (
        <HorizontalScroll>
          {items.slice(1).map((item, idx) => (
            <SmallCard key={idx} $color={tab.domainColor}>
              <SmallCardImg $color={tab.domainColor} />
              <SmallCardTitle>{
                tab.key === 'service' ? item.name :
                tab.key === 'meetup' ? item.title :
                tab.key === 'missing' ? (item.petName || item.title) :
                item.title
              }</SmallCardTitle>
              <SmallCardSub>{
                tab.key === 'service' ? item.category :
                tab.key === 'meetup' ? `${item.currentParticipants || 0}/${item.maxParticipants || 0}명` :
                tab.key === 'missing' ? item.breed || '' :
                item.category || ''
              }</SmallCardSub>
            </SmallCard>
          ))}
        </HorizontalScroll>
      ) : (
        !loading && <EmptyList>더 많은 항목을 불러오는 중이에요</EmptyList>
      )}
    </ContentArea>
  );
};
```

- [ ] **Step 4: HeroCard + TabContent styled-components 추가**

```javascript
const ContentArea = styled.div`
  padding: 0 20px;
`;

const HeroCard = styled.div`
  position: relative;
  width: 100%;
  height: 280px;
  border-radius: 24px;
  overflow: hidden;
  margin-bottom: 24px;
  background: ${props => props.$image
    ? `url(${props.$image}) center/cover no-repeat`
    : `linear-gradient(135deg, ${props.$color}cc 0%, ${props.$color}66 100%)`
  };
`;

const HeroOverlay = styled.div`
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, transparent 30%, rgba(0,0,0,0.55) 100%);
`;

const HeroGlassPanel = styled.div`
  position: absolute;
  bottom: 16px;
  left: 16px;
  right: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(29, 29, 29, 0.45);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
`;

const HeroTitle = styled.div`
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const HeroSub = styled.div`
  font-size: 13px;
  color: rgba(255,255,255,0.75);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const HeroBadge = styled.span`
  display: inline-block;
  margin-top: 8px;
  padding: 3px 10px;
  border-radius: 9999px;
  background: ${props => props.$color};
  color: #fff;
  font-size: 11px;
  font-weight: 600;
`;

const EmptyHero = styled.div`
  height: 100px;
  border-radius: 24px;
  background: ${props => props.$color}22;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${props => props.theme.colors.textMuted};
  font-size: 14px;
  margin-bottom: 24px;
`;

const SectionHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
`;

const SectionLabel = styled.span`
  font-size: 17px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
`;

const ViewAllBtn = styled.button`
  background: none;
  border: none;
  font-size: 13px;
  color: ${props => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: 0;
`;
```

- [ ] **Step 5: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

Expected: `Compiled successfully`

---

## Task 6: HorizontalCardList (가로 스크롤 소형 카드)

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: HorizontalScroll + SmallCard styled-components 추가**

```javascript
const HorizontalScroll = styled.div`
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
  margin: 0 -20px;
  padding-left: 20px;
  padding-right: 20px;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const SmallCard = styled.div`
  flex-shrink: 0;
  width: 150px;
  border-radius: 16px;
  overflow: hidden;
  background: ${props => props.theme.colors.surface};
  box-shadow: ${props => props.theme.shadows.sm};
  cursor: pointer;
  transition: transform 150ms ease;
  &:hover { transform: translateY(-2px); }
`;

const SmallCardImg = styled.div`
  height: 110px;
  background: linear-gradient(135deg, ${props => props.$color}99 0%, ${props => props.$color}44 100%);
`;

const SmallCardTitle = styled.div`
  font-size: 13px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  padding: 10px 10px 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const SmallCardSub = styled.div`
  font-size: 11px;
  color: ${props => props.theme.colors.textSecondary};
  padding: 0 10px 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const EmptyList = styled.div`
  font-size: 13px;
  color: ${props => props.theme.colors.textMuted};
  text-align: center;
  padding: 20px 0;
`;
```

- [ ] **Step 2: 스켈레톤 styled-components 추가**

```javascript
const shimmer = `
  @keyframes shimmer {
    0% { background-position: -200px 0; }
    100% { background-position: calc(200px + 100%) 0; }
  }
`;

const SkeletonBase = styled.div`
  background: linear-gradient(90deg,
    ${props => props.theme.colors.border} 25%,
    ${props => props.theme.colors.borderLight} 50%,
    ${props => props.theme.colors.border} 75%
  );
  background-size: 200px 100%;
  animation: shimmer 1.2s infinite;
  ${shimmer}
`;

const SkeletonHero = styled(SkeletonBase)`
  width: 100%;
  height: 280px;
  border-radius: 24px;
  margin-bottom: 24px;
`;

const SkeletonSmallCard = styled(SkeletonBase)`
  flex-shrink: 0;
  width: 150px;
  height: 160px;
  border-radius: 16px;
`;

const SkeletonText = styled(SkeletonBase)`
  height: 16px;
  border-radius: 8px;
  width: ${props => props.$w || '100px'};
`;
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

---

## Task 7: Admin 섹션 리스타일

**Files:**
- Modify: `frontend/src/components/Home/HomePage.js`

- [ ] **Step 1: TabContent JSX 아래에 Admin 섹션 JSX 추가**

(기존 관리자 로직 유지, 스타일만 교체)

```jsx
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
```

- [ ] **Step 2: Admin styled-components 교체**

기존 Admin 관련 styled-components 전체를 찾아 아래로 교체:

```javascript
const AdminSection = styled.div`
  margin: 32px 20px 0;
  padding-top: 24px;
  border-top: 1px solid ${props => props.theme.colors.border};
`;

const AdminSectionTitle = styled.div`
  font-size: 15px;
  font-weight: 700;
  color: ${props => props.theme.colors.textSecondary};
  margin-bottom: 14px;
`;

const AdminGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
`;

const AdminCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1.5px solid ${props => props.theme.colors.border};
  border-radius: 16px;
  padding: 16px;
  cursor: pointer;
  text-align: center;
  transition: border-color 150ms ease;
  &:hover { border-color: ${props => props.theme.colors.primary}; }
`;

const AdminCardIcon = styled.div`
  font-size: 24px;
  margin-bottom: 8px;
`;

const AdminCardName = styled.div`
  font-size: 13px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;
```

- [ ] **Step 3: 파일에서 더 이상 사용 안 하는 기존 styled-components 제거**

다음 styled-components들을 파일에서 찾아 삭제 (Task 1~7에서 새 이름으로 교체됨):
`WelcomeBanner`, `BannerContent`, `WelcomeTitle`, `WelcomeSub`, `DateLabel`, `SectionTitle`, `ServiceGrid`, `ServiceCard`, `ServiceIcon`, `ServiceTitle`, `ServiceDescription`, `AdminHeader`, `AdminTitle`, `AdminSubtitle`, `AdminCardGrid`, `AdminCardDescription`

- [ ] **Step 4: 최종 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: `Compiled successfully` (warnings 허용, errors 없음)

- [ ] **Step 5: 개발 서버 실행해서 육안 확인**

```bash
cd frontend && npm start
```

확인 항목:
- [ ] 헤더에 아바타 + 인사 + 알림 버튼 보임
- [ ] 검색바 pill 형태로 보임
- [ ] 4개 탭 버튼 가로 스크롤 가능
- [ ] 탭 클릭 시 히어로 카드 + 가로 카드리스트 렌더링
- [ ] 로딩 시 스켈레톤 애니메이션 작동
- [ ] 관리자 계정 로그인 시 Admin 섹션 노출
- [ ] 데스크탑에서 max-width 430px 중앙 정렬 확인

---

## 셀프 리뷰

**스펙 커버리지 체크:**
- [x] max-width 430px 모바일 컨테이너 → Task 1
- [x] 헤더 (아바타, 인사, 알림) → Task 2
- [x] 검색바 (더미 UI) → Task 3
- [x] 카테고리 탭 4개 → Task 4
- [x] 히어로 카드 (글래스모피즘) → Task 5
- [x] 가로 스크롤 카드 → Task 6
- [x] 탭별 API 연결 → Task 1 (데이터) + Task 5 (렌더링)
- [x] 로딩 스켈레톤 → Task 6
- [x] 에러 / 빈 상태 → Task 5
- [x] 이미지 없을 때 그라디언트 폴백 → Task 5
- [x] 관리자 섹션 유지 → Task 7
- [x] `setActiveTab` prop 불변 → Task 5 (onViewAll에서 기존 prop 사용)
- [x] styled-components 패턴 유지 → 전 태스크

**타입 일관성:** `getHeroItem`, `TabContent`, `TABS` 모두 Task 1~5에서 순서대로 정의됨. `setActiveTabLocal` (내부 탭 상태)과 `setActiveTab` prop (App.js 이동)이 명확히 구분됨.
