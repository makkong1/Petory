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

const HomePage = ({ setActiveTab }) => {
  const { user } = useAuth();
  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const nickname = user?.nickname || '사용자';

  const [activeTab, setActiveTabLocal] = useState('service');
  const [tabData, setTabData] = useState({});
  const [tabLoading, setTabLoading] = useState({});
  const [tabError, setTabError] = useState({});

  const fetchedTabsRef = React.useRef(new Set());

  const fetchTabData = useCallback(async (tabKey) => {
    if (fetchedTabsRef.current.has(tabKey)) return;
    fetchedTabsRef.current.add(tabKey);
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
      fetchedTabsRef.current.delete(tabKey); // allow retry on error
      setTabError(prev => ({ ...prev, [tabKey]: true }));
      setTabData(prev => ({ ...prev, [tabKey]: [] }));
    } finally {
      setTabLoading(prev => ({ ...prev, [tabKey]: false }));
    }
  }, []); // empty dep array — stable reference

  useEffect(() => {
    fetchTabData(activeTab);
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
        {/* Content - Tasks 5-7 */}
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
