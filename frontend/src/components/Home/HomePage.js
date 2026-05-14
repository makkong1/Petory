import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { locationServiceApi } from '../../api/locationServiceApi';
import { meetupApi } from '../../api/meetupApi';
import { missingPetApi } from '../../api/missingPetApi';
import { boardApi } from '../../api/boardApi';

const TABS = [
  { key: 'service',   label: '주변서비스', domainColor: '#3B82F6' },
  { key: 'meetup',    label: '모임',       domainColor: '#10B981' },
  { key: 'missing',   label: '실종신고',   domainColor: '#EF4444' },
  { key: 'community', label: '커뮤니티',   domainColor: '#8B5CF6' },
];

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
      title: item.boardTitle || item.title,
      subtitle: `❤️ ${item.likeCount ?? item.likes ?? 0}  👁 ${item.viewCount ?? item.views ?? 0}`,
      badge: item.boardCategory || item.category || null,
      image: item.boardFilePath || null,
    };
  }
  return null;
};

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
                (item.boardTitle || item.title)
              }</SmallCardTitle>
              <SmallCardSub>{
                tab.key === 'service' ? item.category :
                tab.key === 'meetup' ? `${item.currentParticipants || 0}/${item.maxParticipants || 0}명` :
                tab.key === 'missing' ? (item.breed || '') :
                (item.boardCategory || item.category || '')
              }</SmallCardSub>
            </SmallCard>
          ))}
        </HorizontalScroll>
      ) : (
        <EmptyList>등록된 항목이 없어요</EmptyList>
      )}
    </ContentArea>
  );
};

const HomePage = ({ setActiveTab }) => {
  const { user } = useAuth();
  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const nickname = user?.nickname || '사용자';

  const [activeTab, setActiveTabLocal] = useState('service');
  const [tabData, setTabData] = useState({});
  const [tabLoading, setTabLoading] = useState({ service: true });
  const [tabError, setTabError] = useState({});
  const [userCoords, setUserCoords] = useState(null);

  const fetchedTabsRef = React.useRef(new Set());

  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => setUserCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => setUserCoords(null),
      { timeout: 5000, maximumAge: 60000 }
    );
  }, []);

  const fetchTabData = useCallback(async (tabKey) => {
    if (fetchedTabsRef.current.has(tabKey)) return;
    fetchedTabsRef.current.add(tabKey);
    setTabLoading(prev => ({ ...prev, [tabKey]: true }));
    setTabError(prev => ({ ...prev, [tabKey]: false }));
    const toArr = (v) => Array.isArray(v) ? v : [];
    try {
      let items = [];
      if (tabKey === 'service') {
        const params = { sort: 'score', size: 6 };
        if (userCoords) {
          params.latitude = userCoords.lat;
          params.longitude = userCoords.lng;
          params.radius = 10000;
        }
        const res = await locationServiceApi.searchPlaces(params);
        items = toArr(res.data?.services ?? res.data?.results ?? res.data);
      } else if (tabKey === 'meetup') {
        const res = await meetupApi.getHomeMeetups(
          userCoords?.lat ?? null,
          userCoords?.lng ?? null,
          6
        );
        items = toArr(res.data?.meetups ?? res.data?.content ?? res.data);
      } else if (tabKey === 'missing') {
        const res = await missingPetApi.getHomeMissing(
          userCoords?.lat ?? null,
          userCoords?.lng ?? null,
          6
        );
        items = toArr(res.data?.boards ?? res.data);
      } else if (tabKey === 'community') {
        const res = await boardApi.getPopularBoards('WEEKLY');
        items = toArr(res.data?.boards ?? res.data?.content ?? res.data);
      }
      setTabData(prev => ({ ...prev, [tabKey]: items.slice(0, 6) }));
    } catch {
      fetchedTabsRef.current.delete(tabKey); // allow retry on error
      setTabError(prev => ({ ...prev, [tabKey]: true }));
      setTabData(prev => ({ ...prev, [tabKey]: [] }));
    } finally {
      setTabLoading(prev => ({ ...prev, [tabKey]: false }));
    }
  }, [userCoords]);

  useEffect(() => {
    fetchedTabsRef.current = new Set();
    fetchTabData('service');
  }, [userCoords, fetchTabData]);

  useEffect(() => {
    if (activeTab !== 'service') fetchTabData(activeTab);
  }, [activeTab, fetchTabData]);

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
        <SearchBarWrap>
          <SearchIcon>🔍</SearchIcon>
          <SearchInput placeholder="반려동물·케어·모임 검색..." readOnly />
          <FilterBtn>⚙️</FilterBtn>
        </SearchBarWrap>
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
};

export default HomePage;

/* ── Layout ─────────────────────────────────────────────────── */

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

  @media (min-width: 769px) {
    max-width: 860px;
  }
`;

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

/* ── SearchBar ───────────────────────────────────────────────── */

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

/* ── CategoryTabs ────────────────────────────────────────────── */

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

/* ── ContentArea & HeroCard ──────────────────────────────────── */

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
    : `linear-gradient(135deg, ${props.$color}cc 0%, ${props.$color}44 100%)`
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
  background: ${props => `${props.$color}22`};
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

  @media (min-width: 769px) {
    flex-wrap: wrap;
    overflow-x: visible;
    margin: 0;
    padding-left: 0;
    padding-right: 0;
  }
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

  @media (min-width: 769px) {
    width: calc(25% - 9px);
  }
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

/* ── Skeletons ───────────────────────────────────────────────── */

const SkeletonHero = styled.div`
  width: 100%;
  height: 280px;
  border-radius: 24px;
  margin-bottom: 24px;
  background: linear-gradient(90deg,
    ${props => props.theme.colors.border} 25%,
    ${props => props.theme.colors.borderLight} 50%,
    ${props => props.theme.colors.border} 75%
  );
  background-size: 200px 100%;
  animation: shimmer 1.2s infinite;

  @keyframes shimmer {
    0% { background-position: -200px 0; }
    100% { background-position: calc(200px + 100%) 0; }
  }
`;

const SkeletonSmallCard = styled.div`
  flex-shrink: 0;
  width: 150px;
  height: 160px;
  border-radius: 16px;
  background: linear-gradient(90deg,
    ${props => props.theme.colors.border} 25%,
    ${props => props.theme.colors.borderLight} 50%,
    ${props => props.theme.colors.border} 75%
  );
  background-size: 200px 100%;
  animation: shimmer 1.2s infinite;
`;

const SkeletonText = styled.div`
  height: 16px;
  border-radius: 8px;
  width: ${props => props.$w || '100px'};
  background: linear-gradient(90deg,
    ${props => props.theme.colors.border} 25%,
    ${props => props.theme.colors.borderLight} 50%,
    ${props => props.theme.colors.border} 75%
  );
  background-size: 200px 100%;
  animation: shimmer 1.2s infinite;
`;

/* ── Admin Section ───────────────────────────────────────────── */

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
