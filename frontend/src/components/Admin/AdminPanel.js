import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { usePermission } from '../../hooks/usePermission';
import PermissionDeniedModal from '../Common/PermissionDeniedModal';
import AdminLayout from './AdminLayout';
import UserManagementSection from './sections/UserManagementSection';
import ReportManagementSection from './sections/ReportManagementSection';
import CommunityManagementSection from './sections/CommunityManagementSection';
import MissingPetManagementSection from './sections/MissingPetManagementSection';
import CareServiceManagementSection from './sections/CareServiceManagementSection';
import LocationServiceManagementSection from './sections/LocationServiceManagementSection';
import MeetupManagementSection from './sections/MeetupManagementSection';
import FileManagementSection from './sections/FileManagementSection';
import SystemDashboardSection from './sections/SystemDashboardSection';

const AdminPanel = () => {
  const { requireAdmin } = usePermission();
  const [showPermissionModal, setShowPermissionModal] = useState(false);
  const [activeMenu, setActiveMenu] = useState('dashboard');
  // 권한 확인
  useEffect(() => {
    const { isAdmin, requiresModal } = requireAdmin();
    if (requiresModal) {
      setShowPermissionModal(true);
    }
  }, [requireAdmin]);

  const { isAdmin } = requireAdmin();
  
  // 권한이 없으면 모달만 표시하고 내용은 숨김
  if (!isAdmin) {
    return (
      <>
        <PermissionDeniedModal 
          isOpen={showPermissionModal}
          onClose={() => setShowPermissionModal(false)}
        />
        <PageContainer>
          <Header>
            <Title>🔧 관리자 패널</Title>
            <Subtitle>접근 권한이 없습니다.</Subtitle>
          </Header>
        </PageContainer>
      </>
    );
  }

  const menuItems = [
    {
      key: 'dashboard',
      label: '전체 대시보드',
      description: '오늘/이번 주 시스템 주요 지표 요약',
    },
    {
      key: 'users',
      label: '사용자 관리',
      description: '사용자 목록, 권한, 정지 관리',
    },
    {
      key: 'reports',
      label: '신고 관리',
      description: '신고 처리 및 조치 기록 관리',
    },
    {
      key: 'community',
      label: '커뮤니티 관리',
      description: '게시글/댓글/인기 게시글 모니터링',
    },
    {
      key: 'missing-pets',
      label: '실종/목격 관리',
      description: '실종/목격 게시글 및 댓글 관리',
    },
    {
      key: 'care',
      label: '케어 서비스 관리',
      description: '케어 요청/지원자/후기 관리',
    },
    {
      key: 'location-services',
      label: '지역 서비스 관리',
      description: '장소, 리뷰, 캐시 관리',
    },
    {
      key: 'meetups',
      label: '산책 모임 관리',
      description: '모임 및 참여자 관리',
    },
    {
      key: 'files',
      label: '파일 관리',
      description: '업로드 파일 및 연결 정보 관리',
    },
  ];

  const renderSection = () => {
    switch (activeMenu) {
      case 'users':
        return <UserManagementSection />;
      case 'reports':
        return <ReportManagementSection />;
      case 'community':
        return <CommunityManagementSection />;
      case 'missing-pets':
        return <MissingPetManagementSection />;
      case 'care':
        return <CareServiceManagementSection />;
      case 'location-services':
        return <LocationServiceManagementSection />;
      case 'meetups':
        return <MeetupManagementSection />;
      case 'files':
        return <FileManagementSection />;
      case 'dashboard':
      default:
        return <SystemDashboardSection />;
    }
  };

  return (
    <>
      <PageContainer>
        <Header>
          <Title>🔧 관리자 패널</Title>
          <Subtitle>Petory 운영을 위한 관리자 도구 모음</Subtitle>
        </Header>
        <AdminLayout
          menuItems={menuItems}
          activeKey={activeMenu}
          onChange={setActiveMenu}
        >
          {renderSection()}
        </AdminLayout>
      </PageContainer>
    </>
  );
};

export default AdminPanel;

const PageContainer = styled.div`
  max-width: 1400px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing.xxl} ${props => props.theme.spacing.lg};
`;

const Header = styled.div`
  margin-bottom: ${props => props.theme.spacing.xl};
  text-align: center;
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h1.fontSize};
  font-weight: ${props => props.theme.typography.h1.fontWeight};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const Subtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const Card = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xl};
  margin-bottom: ${props => props.theme.spacing.xl};
  box-shadow: 0 4px 6px ${props => props.theme.colors.shadow};
`;

const SectionTitle = styled.h2`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const InfoCard = styled(Card)`
  background: ${props => props.theme.colors.surface};
`;

const InfoTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const InfoList = styled.ul`
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.8;
  padding-left: ${props => props.theme.spacing.lg};
  
  li {
    margin-bottom: ${props => props.theme.spacing.sm};
  }
`;

