import React from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';

const HomePage = ({ setActiveTab }) => {
  const { user } = useAuth();
  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const features = [
    {
      icon: '🗺️',
      title: '위치 서비스',
      description: '주변 펫케어 서비스 위치를 지도에서 확인하고 찾아보세요.',
      action: () => setActiveTab('location-services')
    },
    {
      icon: '🚨',
      title: '실종 제보',
      description: '우리 동네 실종 동물 정보를 모아 신속하게 공유하세요.',
      action: () => setActiveTab('missing-pets')
    },
    {
      icon: '🐾',
      title: '펫케어 서비스',
      description: '믿을 수 있는 펫시터와 매칭하여 소중한 반려동물을 안전하게 맡기세요.',
      action: () => setActiveTab('care-requests')
    },
    {
      icon: '⭐',
      title: '리뷰 시스템',
      description: '실제 이용 후기를 통해 믿을 수 있는 펫케어 서비스를 선택하세요.',
      action: () => setActiveTab('care-requests')
    },
    {
      icon: '🐾',
      title: '산책 모임',
      description: '주변 반려동물 산책 모임을 찾아 함께 산책하며 친구를 만나보세요.',
      action: () => setActiveTab('meetup')
    },
    {
      icon: '💬',
      title: '커뮤니티',
      description: '반려동물 키우는 이웃들과 소통하고 유용한 정보를 나누세요.',
      action: () => setActiveTab('community')
    }
  ];

  return (
    <Container>
      <HeroSection>
        <HeroTitle>우리 동네 펫토리 서비스</HeroTitle>
        <HeroSubtitle>
          주변 펫케어 서비스 위치를 지도에서 확인하고<br />
          가장 가까운 펫케어 서비스를 찾아보세요
        </HeroSubtitle>
        <CTAButton onClick={() => setActiveTab('location-services')}>
          지도 서비스 시작하기
        </CTAButton>
      </HeroSection>

      <FeatureGrid>
        {features.map((feature, index) => (
          <FeatureCard key={index} onClick={feature.action}>
            <FeatureIcon>{feature.icon}</FeatureIcon>
            <FeatureTitle>{feature.title}</FeatureTitle>
            <FeatureDescription>{feature.description}</FeatureDescription>
          </FeatureCard>
        ))}
      </FeatureGrid>

      <StatsSection>
        <h2 style={{ marginBottom: '16px', color: 'inherit' }}>Petory와 함께하는 펫케어</h2>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '0' }}>
          많은 반려동물과 보호자들이 Petory를 통해 안전한 펫케어 서비스를 이용하고 있습니다.
        </p>
        <StatsGrid>
          <StatItem>
            <StatNumber>아직없음</StatNumber>
            <StatLabel>등록된 펫시터</StatLabel>
          </StatItem>
          <StatItem>
            <StatNumber>아직없음</StatNumber>
            <StatLabel>완료된 펫케어</StatLabel>
          </StatItem>
          <StatItem>
            <StatNumber>아직없음</StatNumber>
            <StatLabel>평균 만족도</StatLabel>
          </StatItem>
          <StatItem>
            <StatNumber>아직없음</StatNumber>
            <StatLabel>재이용율</StatLabel>
          </StatItem>
        </StatsGrid>
      </StatsSection>

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
    </Container>
  );
};

export default HomePage;

const Container = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing.xxl} ${props => props.theme.spacing.lg};
`;

const HeroSection = styled.div`
  text-align: center;
  margin-bottom: ${props => props.theme.spacing.xxl};
  padding: ${props => props.theme.spacing.xxl} 0;
  background: ${props => props.theme.colors.gradient};
  border-radius: ${props => props.theme.borderRadius.xl};
  color: white;
  margin-bottom: ${props => props.theme.spacing.xxl};
`;

const HeroTitle = styled.h1`
  font-size: 48px;
  font-weight: 700;
  margin-bottom: ${props => props.theme.spacing.lg};
  
  @media (max-width: 768px) {
    font-size: 36px;
  }
`;

const HeroSubtitle = styled.p`
  font-size: 20px;
  opacity: 0.9;
  margin-bottom: ${props => props.theme.spacing.xl};
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
`;

const FeatureGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: ${props => props.theme.spacing.xl};
  margin-bottom: ${props => props.theme.spacing.xxl};
`;

const FeatureCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.xl};
  text-align: center;
  transition: all 0.3s ease;
  cursor: pointer;
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px ${props => props.theme.colors.shadow};
    border-color: ${props => props.theme.colors.primary};
  }
`;

const FeatureIcon = styled.div`
  font-size: 48px;
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const FeatureTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  margin-bottom: ${props => props.theme.spacing.md};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
`;

const FeatureDescription = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.6;
`;

const StatsSection = styled.div`
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xxl};
  text-align: center;
  border: 1px solid ${props => props.theme.colors.border};
`;

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: ${props => props.theme.spacing.xl};
  margin-top: ${props => props.theme.spacing.xl};
`;

const StatItem = styled.div`
  padding: ${props => props.theme.spacing.lg};
`;

const StatNumber = styled.div`
  font-size: 36px;
  font-weight: 700;
  color: ${props => props.theme.colors.primary};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const StatLabel = styled.div`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const CTAButton = styled.button`
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: ${props => props.theme.spacing.lg};
  
  &:hover {
    background: ${props => props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }
`;

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
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px ${props => props.theme.colors.shadow};
    border-color: ${props => props.theme.colors.primaryDark};
  }
`;

const AdminCardIcon = styled.div`
  font-size: 48px;
  margin-bottom: ${props => props.theme.spacing.md};
`;

const AdminCardTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const AdminCardDescription = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.6;
  font-size: ${props => props.theme.typography.body2.fontSize};
`;
