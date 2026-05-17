import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { locationServiceApi } from '../../api/locationServiceApi';
import { meetupApi } from '../../api/meetupApi';
import { missingPetApi } from '../../api/missingPetApi';
import { boardApi } from '../../api/boardApi';

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

const HomePage = ({ setActiveTab }) => {
  const { user } = useAuth();
  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const nickname = user?.nickname || '사용자';

  const [sections, setSections] = useState({
    missing:   { items: [], loading: true, error: false },
    service:   { items: [], loading: true, error: false },
    meetup:    { items: [], loading: true, error: false },
    community: { items: [], loading: true, error: false },
  });
  const [userCoords, setUserCoords] = useState(null);

  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => setUserCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => setUserCoords(null),
      { timeout: 5000, maximumAge: 60000 }
    );
  }, []);

  useEffect(() => {
    const toArr = (v) => (Array.isArray(v) ? v : []);
    const setSection = (key, items) =>
      setSections((prev) => ({ ...prev, [key]: { items: items.slice(0, 4), loading: false, error: false } }));
    const setError = (key) =>
      setSections((prev) => ({ ...prev, [key]: { items: [], loading: false, error: true } }));

    missingPetApi
      .getHomeMissing(userCoords?.lat ?? null, userCoords?.lng ?? null, 6)
      .then((res) => setSection('missing', toArr(res.data?.boards ?? res.data)))
      .catch(() => setError('missing'));

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

    meetupApi
      .getHomeMeetups(userCoords?.lat ?? null, userCoords?.lng ?? null, 6)
      .then((res) => setSection('meetup', toArr(res.data?.meetups ?? res.data?.content ?? res.data)))
      .catch(() => setError('meetup'));

    boardApi
      .getPopularBoards('WEEKLY')
      .then((res) => setSection('community', toArr(res.data?.boards ?? res.data?.content ?? res.data)))
      .catch(() => setError('community'));
  }, [userCoords]);

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


/* ── SectionRow header ───────────────────────────────────────── */

const SectionHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  margin-bottom: 10px;
`;

const SectionLabel = styled.span`
  font-size: 15px;
  font-weight: 700;
  color: ${(p) => p.$color || p.theme.colors.text};
`;

const ViewAllBtn = styled.button`
  background: none;
  border: none;
  font-size: 13px;
  color: ${(p) => p.theme.colors.textSecondary};
  cursor: pointer;
  padding: 0;
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

/* ── SectionRow ─────────────────────────────────────────────── */

const SectionWrap = styled.div`
  background: ${(p) => p.theme.colors.surface};
  border-radius: 16px;
  margin: 8px 16px;
  padding: 14px 0 14px;
  box-shadow: ${(p) => p.theme.shadows.sm};
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
