import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { locationServiceApi } from '../../api/locationServiceApi';
import { meetupApi } from '../../api/meetupApi';
import { missingPetApi } from '../../api/missingPetApi';
import { boardApi } from '../../api/boardApi';
import HomeMap from './HomeMap';

// 날짜 기반으로 매일 바뀌는 반려 케어 팁 (서버 데이터 없이 로테이션)
const CARE_TIPS = [
  { icon: '☀️', text: '산책하기 좋은 날이에요. 우리 아이와 나가볼까요?' },
  { icon: '💧', text: '물그릇, 신선한 물로 갈아주는 거 잊지 마세요.' },
  { icon: '🦴', text: '오늘도 간식은 적당히! 건강한 하루 되세요.' },
  { icon: '🐾', text: '발바닥 패드 상태도 가끔 확인해 주세요.' },
  { icon: '🧸', text: '짧은 놀이 시간이 아이의 스트레스를 줄여줘요.' },
];

const HomePage = ({ setActiveTab }) => {
  const { user } = useAuth();
  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const nickname = user?.nickname || '사용자';
  const careTip = CARE_TIPS[new Date().getDate() % CARE_TIPS.length];

  const [sections, setSections] = useState({
    missing:   { items: [], loading: true, error: false },
    service:   { items: [], loading: true, error: false },
    meetup:    { items: [], loading: true, error: false },
    community: { items: [], loading: true, error: false },
  });
  const [geo, setGeo] = useState({ coords: null, ready: false });

  useEffect(() => {
    if (!navigator.geolocation) {
      setGeo({ coords: null, ready: true });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => setGeo({ coords: { lat: pos.coords.latitude, lng: pos.coords.longitude }, ready: true }),
      () => setGeo({ coords: null, ready: true }),
      { timeout: 5000, maximumAge: 60000 }
    );
  }, []);

  useEffect(() => {
    if (!geo.ready) return;

    const toArr = (v) => (Array.isArray(v) ? v : []);
    const setSection = (key, items) =>
      setSections((prev) => ({ ...prev, [key]: { items: items.slice(0, 4), loading: false, error: false } }));
    const setError = (key) =>
      setSections((prev) => ({ ...prev, [key]: { items: [], loading: false, error: true } }));

    const { coords } = geo;

    missingPetApi
      .getHomeMissing(coords?.lat ?? null, coords?.lng ?? null, 6)
      .then((res) => setSection('missing', toArr(res.data?.boards ?? res.data)))
      .catch(() => setError('missing'));

    const serviceParams = { sort: 'score', size: 6 };
    if (coords) {
      serviceParams.latitude = coords.lat;
      serviceParams.longitude = coords.lng;
      serviceParams.radius = 10000;
    }
    locationServiceApi
      .searchPlaces(serviceParams)
      .then((res) => setSection('service', toArr(res.data?.services ?? res.data?.results ?? res.data)))
      .catch(() => setError('service'));

    meetupApi
      .getHomeMeetups(coords?.lat ?? null, coords?.lng ?? null, 6)
      .then((res) => setSection('meetup', toArr(res.data?.meetups ?? res.data?.content ?? res.data)))
      .catch(() => setError('meetup'));

    boardApi
      .getPopularBoards('WEEKLY')
      .then((res) => setSection('community', toArr(res.data?.boards ?? res.data?.content ?? res.data)))
      .catch(() => setError('community'));
  }, [geo]);

  const missing = sections.missing;
  const community = sections.community;
  const meetupCount = sections.meetup.items.length;

  const renderRows = (state, mapRow, emptyText) => {
    if (state.loading) {
      return [1, 2, 3].map((i) => <SkeletonRow key={i} />);
    }
    if (state.items.length === 0) {
      return <TileEmpty>{emptyText}</TileEmpty>;
    }
    return state.items.slice(0, 3).map(mapRow);
  };

  return (
    <PageWrapper>
      <Dashboard>
        <TopBar>
          <Greeting>
            안녕하세요, <strong>{nickname}</strong>님 👋
            <SubGreeting>오늘도 반려동물과 함께하는 하루 되세요</SubGreeting>
          </Greeting>
          <TopRight>
            <Bell>🔔</Bell>
            <Avatar>{nickname.charAt(0)}</Avatar>
          </TopRight>
        </TopBar>

        <Bento>
          <MapTile onClick={() => setActiveTab('unified-map')}>
            <HomeMap coords={geo.coords} services={sections.service.items} />
            <MapLabel>📍 내 주변</MapLabel>
            <MapCta>주변 서비스 보기 →</MapCta>
          </MapTile>

          <CareTile>
            <TileLabel>오늘의 케어 팁</TileLabel>
            <CareBig><span>{careTip.icon}</span>{careTip.text}</CareBig>
          </CareTile>

          <StatTile>
            <TileLabel>펫코인</TileLabel>
            <StatNum>{(user?.petCoinBalance ?? 0).toLocaleString()}<small> C</small></StatNum>
          </StatTile>

          <StatTile onClick={() => setActiveTab('unified-map')}>
            <TileLabel>모임</TileLabel>
            <StatNum>{meetupCount}<small> 개</small></StatNum>
            <StatHint>내 주변 모집 중</StatHint>
          </StatTile>

          <ListTile>
            <TileHead>
              <TileHeadTitle><TileDot $c="#EF4444" />실종신고</TileHeadTitle>
              <More onClick={() => setActiveTab('missing-pets')}>전체 →</More>
            </TileHead>
            {renderRows(
              missing,
              (item, idx) => (
                <MiniRow key={idx} onClick={() => setActiveTab('missing-pets')}>
                  <MiniMain>
                    <MiniTitle>{item.petName || item.title || '이름 없음'}</MiniTitle>
                    <MiniSub>{[item.breed, item.region].filter(Boolean).join(' · ') || '실종 신고'}</MiniSub>
                  </MiniMain>
                  <MiniMeta>{item.lostDate || ''}</MiniMeta>
                </MiniRow>
              ),
              '주변 실종 신고가 없어요'
            )}
          </ListTile>

          <ListTile>
            <TileHead>
              <TileHeadTitle><TileDot $c="#8B5CF6" />인기 커뮤니티</TileHeadTitle>
              <More onClick={() => setActiveTab('community')}>전체 →</More>
            </TileHead>
            {renderRows(
              community,
              (item, idx) => (
                <MiniRow key={idx} onClick={() => setActiveTab('community')}>
                  <MiniMain>
                    <MiniTitle>{item.boardTitle || item.title || '제목 없음'}</MiniTitle>
                  </MiniMain>
                  <MiniMeta>❤ {item.likeCount ?? 0}</MiniMeta>
                </MiniRow>
              ),
              '인기 글이 아직 없어요'
            )}
          </ListTile>
        </Bento>

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
      </Dashboard>
    </PageWrapper>
  );
};

export default HomePage;

/* ── Layout ─────────────────────────────────────────────────── */

const PageWrapper = styled.div`
  min-height: 100vh;
  background: ${(p) => p.theme.colors.background};
`;

const Dashboard = styled.div`
  max-width: 1160px;
  margin: 0 auto;
  padding: 24px 22px 40px;

  @media (max-width: 600px) {
    padding: 20px 16px 28px;
  }
`;

const TopBar = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
`;

const Greeting = styled.h1`
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: ${(p) => p.theme.colors.text};
  line-height: 1.3;

  strong { color: ${(p) => p.theme.colors.primary}; font-weight: 800; }
`;

const SubGreeting = styled.div`
  font-size: 13px;
  font-weight: 400;
  color: ${(p) => p.theme.colors.textSecondary};
  margin-top: 5px;
`;

const TopRight = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
`;

const Bell = styled.button`
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
`;

const Avatar = styled.div`
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: ${(p) => p.theme.colors.primary};
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
`;

/* ── Bento grid ─────────────────────────────────────────────── */

const Bento = styled.div`
  display: grid;
  gap: 14px;
  grid-template-columns: 1fr;

  @media (min-width: 600px) {
    grid-template-columns: repeat(2, 1fr);
    grid-auto-rows: 130px;
  }
  @media (min-width: 1000px) {
    grid-template-columns: repeat(4, 1fr);
    grid-auto-rows: 132px;
  }
`;

const Tile = styled.div`
  background: ${(p) => p.theme.colors.surfaceElevated};
  border: 1px solid ${(p) => p.theme.colors.border};
  border-radius: 18px;
  padding: 16px 17px;
  overflow: hidden;
`;

const TileLabel = styled.div`
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: ${(p) => p.theme.colors.textSecondary};
  margin-bottom: 9px;
`;

const MapTile = styled(Tile)`
  position: relative;
  padding: 0;
  min-height: 210px;
  cursor: pointer;
  background: linear-gradient(135deg, #dfe7e6, #eef1ec);
  transition: transform 200ms ${(p) => p.theme.easing?.spring || 'ease'}, box-shadow 200ms ease;

  &:hover { transform: translateY(-2px); box-shadow: ${(p) => p.theme.shadows.md}; }

  @media (min-width: 600px) { grid-column: span 2; grid-row: span 2; min-height: 0; }
`;

const MapLabel = styled.div`
  position: absolute;
  top: 15px;
  left: 16px;
  font-size: 12px;
  font-weight: 800;
  color: #2d3b39;
  background: #ffffffd9;
  padding: 6px 11px;
  border-radius: 999px;
`;

const MapCta = styled.div`
  position: absolute;
  bottom: 15px;
  left: 16px;
  font-size: 12.5px;
  font-weight: 700;
  color: #2d3b39;
  background: #ffffffd9;
  padding: 7px 13px;
  border-radius: 999px;
`;

const CareTile = styled(Tile)`
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: ${(p) => p.theme.colors.primarySoft};
  border-color: transparent;

  @media (min-width: 600px) { grid-column: span 2; }
`;

const CareBig = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 9px;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.01em;
  color: ${(p) => p.theme.colors.primaryDark};

  span { font-size: 19px; line-height: 1.2; flex-shrink: 0; }
`;

const StatTile = styled(Tile).attrs((p) => ({ as: p.onClick ? 'button' : 'div' }))`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  font-family: inherit;
  cursor: ${(p) => (p.onClick ? 'pointer' : 'default')};
  transition: border-color 200ms ease, transform 200ms ease;

  ${(p) => p.onClick && `&:hover { border-color: ${p.theme.colors.primaryLight}; transform: translateY(-2px); }`}
`;

const StatNum = styled.div`
  font-size: 27px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: ${(p) => p.theme.colors.text};
  font-variant-numeric: tabular-nums;
  line-height: 1.1;

  small { font-size: 14px; font-weight: 700; color: ${(p) => p.theme.colors.textSecondary}; }
`;

const StatHint = styled.div`
  font-size: 11.5px;
  color: ${(p) => p.theme.colors.textSecondary};
  margin-top: 6px;
`;

const ListTile = styled(Tile)`
  display: flex;
  flex-direction: column;

  @media (min-width: 600px) { grid-column: span 2; grid-row: span 2; }
`;

const TileHead = styled.div`
  display: flex;
  align-items: center;
  margin-bottom: 6px;
`;

const TileHeadTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: ${(p) => p.theme.colors.text};
`;

const TileDot = styled.span`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${(p) => p.$c || p.theme.colors.primary};
  flex-shrink: 0;
`;

const More = styled.button`
  margin-left: auto;
  background: none;
  border: none;
  font-size: 12px;
  color: ${(p) => p.theme.colors.textSecondary};
  cursor: pointer;
  padding: 2px;
`;

const MiniRow = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid ${(p) => p.theme.colors.borderLight};
  cursor: pointer;

  &:last-child { border-bottom: none; }
  &:hover .mini-title { color: ${(p) => p.theme.colors.primary}; }
`;

const MiniMain = styled.div`
  min-width: 0;
  flex: 1;
`;

const MiniTitle = styled.div.attrs({ className: 'mini-title' })`
  font-size: 13.5px;
  font-weight: 600;
  color: ${(p) => p.theme.colors.text};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 150ms ease;
`;

const MiniSub = styled.div`
  font-size: 11.5px;
  color: ${(p) => p.theme.colors.textSecondary};
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const MiniMeta = styled.div`
  font-size: 11.5px;
  color: ${(p) => p.theme.colors.textSecondary};
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
`;

const TileEmpty = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px 0;
  font-size: 12.5px;
  color: ${(p) => p.theme.colors.textMuted};
`;

const SkeletonRow = styled.div`
  height: 14px;
  margin: 13px 0;
  border-radius: 6px;
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

/* ── Admin Section ───────────────────────────────────────────── */

const AdminSection = styled.div`
  margin: 30px 0 0;
  padding-top: 24px;
  border-top: 1px solid ${(p) => p.theme.colors.border};
`;

const AdminSectionTitle = styled.div`
  font-size: 15px;
  font-weight: 700;
  color: ${(p) => p.theme.colors.textSecondary};
  margin-bottom: 14px;
`;

const AdminGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 420px;
`;

const AdminCard = styled.div`
  background: ${(p) => p.theme.colors.surface};
  border: 1.5px solid ${(p) => p.theme.colors.border};
  border-radius: 16px;
  padding: 16px;
  cursor: pointer;
  text-align: center;
  transition: border-color 150ms ease;
  &:hover { border-color: ${(p) => p.theme.colors.primary}; }
`;

const AdminCardIcon = styled.div`
  font-size: 24px;
  margin-bottom: 8px;
`;

const AdminCardName = styled.div`
  font-size: 13px;
  font-weight: 600;
  color: ${(p) => p.theme.colors.text};
`;
