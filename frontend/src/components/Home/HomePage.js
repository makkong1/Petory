import React from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';

const HomePage = ({ setActiveTab }) => {
  const { user } = useAuth();
  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const nickname = user?.nickname || '사용자';

  const today = new Date();
  const dateStr = `${today.getFullYear()}.${String(today.getMonth() + 1).padStart(2, '0')}.${String(today.getDate()).padStart(2, '0')}`;

  const services = [
    {
      icon: '🗺️',
      title: '지도 탐색',
      description: '주변 펫케어 시설, 모임, 케어 서비스를 한눈에',
      tab: 'unified-map',
    },
    {
      icon: '🚨',
      title: '실종 제보',
      description: '우리 동네 실종 동물 정보를 빠르게 확인',
      tab: 'missing-pets',
    },
    {
      icon: '🐾',
      title: '펫케어 서비스',
      description: '전문 펫시터와 안전한 케어 매칭',
      tab: 'unified-map',
    },
    {
      icon: '💬',
      title: '커뮤니티',
      description: '반려동물 이야기를 함께 나눠요',
      tab: 'community',
    },
  ];

  return (
    <PageContainer>
      {/* 섹션 1: 개인화 인사 배너 */}
      <WelcomeBanner>
        <BannerContent>
          <WelcomeTitle>안녕하세요, {nickname}님! 🐾</WelcomeTitle>
          <WelcomeSub>오늘도 반려동물과 좋은 하루 되세요</WelcomeSub>
        </BannerContent>
        <DateLabel>{dateStr}</DateLabel>
      </WelcomeBanner>

      {/* 섹션 2: 서비스 소개 카드 */}
      <SectionTitle>서비스</SectionTitle>
      <ServiceGrid>
        {services.map((service) => (
          <ServiceCard key={service.title} onClick={() => setActiveTab(service.tab)}>
            <ServiceIcon>{service.icon}</ServiceIcon>
            <ServiceTitle>{service.title}</ServiceTitle>
            <ServiceDescription>{service.description}</ServiceDescription>
          </ServiceCard>
        ))}
      </ServiceGrid>

      {/* 섹션 3: 관리자 섹션 */}
      {isAdmin && (
        <AdminSection>
          <AdminHeader>
            <AdminTitle>🔧 관리자 기능</AdminTitle>
            <AdminSubtitle>관리자 전용 기능을 이용하실 수 있습니다.</AdminSubtitle>
          </AdminHeader>
          <AdminCardGrid>
            <AdminCard onClick={() => setActiveTab('admin')}>
              <AdminCardIcon>📥</AdminCardIcon>
              <AdminCardTitle>초기 데이터 로딩</AdminCardTitle>
              <AdminCardDescription>
                카카오맵 API를 사용하여 LocationService 초기 데이터를 로드합니다.
              </AdminCardDescription>
            </AdminCard>
            <AdminCard onClick={() => setActiveTab('users')}>
              <AdminCardIcon>👥</AdminCardIcon>
              <AdminCardTitle>사용자 관리</AdminCardTitle>
              <AdminCardDescription>
                등록된 사용자 목록을 조회하고 관리할 수 있습니다.
              </AdminCardDescription>
            </AdminCard>
          </AdminCardGrid>
        </AdminSection>
      )}
    </PageContainer>
  );
};

export default HomePage;

/* ── Layout ─────────────────────────────────────────────────── */

const PageContainer = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing['3xl']} ${props => props.theme.spacing['4xl']};

  @media (max-width: 768px) {
    padding: ${props => props.theme.spacing.xl} ${props => props.theme.spacing.lg};
  }
`;

/* ── 섹션 타이틀 ─────────────────────────────────────────────── */

const SectionTitle = styled.h2`
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  margin-bottom: ${props => props.theme.spacing.lg};
  margin-top: 0;
`;

/* ── 섹션 1: 인사 배너 ───────────────────────────────────────── */

const WelcomeBanner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: ${props => props.theme.colors.primarySoft};
  border: 1px solid ${props => props.theme.colors.primaryLight};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xl} ${props => props.theme.spacing['3xl']};
  margin-bottom: ${props => props.theme.spacing['3xl']};

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
    gap: ${props => props.theme.spacing.sm};
    padding: ${props => props.theme.spacing.xl};
  }
`;

const BannerContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.xs};
`;

const WelcomeTitle = styled.h1`
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: 700;
  color: ${props => props.theme.colors.primary};
  margin: 0;
  line-height: ${props => props.theme.typography.h2.lineHeight};
`;

const WelcomeSub = styled.p`
  font-size: ${props => props.theme.typography.body1.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  margin: 0;
`;

const DateLabel = styled.span`
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.textMuted};
  white-space: nowrap;
  align-self: flex-start;

  @media (max-width: 768px) {
    align-self: auto;
  }
`;

/* ── 섹션 2: 서비스 카드 ─────────────────────────────────────── */

const ServiceGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: ${props => props.theme.spacing.lg};
  margin-bottom: ${props => props.theme.spacing['3xl']};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const ServiceCard = styled.div`
  background: ${props => props.theme.colors.surface};
  box-shadow: ${props => props.theme.shadows.sm};
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.xl};
  cursor: pointer;
  transition: transform 200ms ease, box-shadow 200ms ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: ${props => props.theme.shadows.md};
  }
`;

const ServiceIcon = styled.div`
  font-size: 32px;
  margin-bottom: ${props => props.theme.spacing.md};
  line-height: 1;
`;

const ServiceTitle = styled.h3`
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  color: ${props => props.theme.colors.text};
  margin: 0 0 ${props => props.theme.spacing.sm} 0;
`;

const ServiceDescription = styled.p`
  font-size: ${props => props.theme.typography.body2.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  margin: 0;
  line-height: 1.6;
`;

/* ── 섹션 3: 관리자 ─────────────────────────────────────────── */

const AdminSection = styled.div`
  margin-top: ${props => props.theme.spacing.xxl};
  padding-top: ${props => props.theme.spacing.xxl};
  border-top: 2px solid ${props => props.theme.colors.border};
`;

const AdminHeader = styled.div`
  text-align: center;
  margin-bottom: ${props => props.theme.spacing.xl};
`;

const AdminTitle = styled.h2`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const AdminSubtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
  margin: 0;
`;

const AdminCardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: ${props => props.theme.spacing.xl};
`;

const AdminCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 2px solid ${props => props.theme.colors.primary};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xl};
  text-align: center;
  cursor: pointer;
  transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease;
  box-shadow: ${props => props.theme.shadows.sm};

  &:hover {
    transform: translateY(-2px);
    box-shadow: ${props => props.theme.shadows.md};
    border-color: ${props => props.theme.colors.primaryDark};
  }
`;

const AdminCardIcon = styled.div`
  font-size: 32px;
  margin-bottom: ${props => props.theme.spacing.md};
  line-height: 1;
`;

const AdminCardTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin: 0 0 ${props => props.theme.spacing.sm} 0;
`;

const AdminCardDescription = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.6;
  font-size: ${props => props.theme.typography.body2.fontSize};
  margin: 0;
`;
