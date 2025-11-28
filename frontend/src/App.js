import React, { useState, useEffect } from 'react';
import styled, { createGlobalStyle } from 'styled-components';
import { ThemeProvider } from './contexts/ThemeContext';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import Navigation from './components/Layout/Navigation';
import HomePage from './components/Home/HomePage';
import UserList from './components/User/UserList';
import CareRequestList from './components/CareRequest/CareRequestList';
import CommunityBoard from './components/Community/CommunityBoard';
import LocationServiceMap from './components/LocationService/LocationServiceMap';
import LoginForm from './components/Auth/LoginForm';
import RegisterForm from './components/Auth/RegisterForm';
import AdminPanel from './components/Admin/AdminPanel';
import PermissionDeniedModal from './components/Common/PermissionDeniedModal';
import ScrollToTopBottom from './components/Common/ScrollToTopBottom';
import MissingPetBoardPage from './components/MissingPet/MissingPetBoardPage';
import ActivityPage from './components/Activity/ActivityPage';
import MeetupPage from './components/Meetup/MeetupPage';
import { setupApiInterceptors } from './api/authApi';


function AppContent() {
  const { user, loading, isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState('home');
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'register'
  const [redirectToLogin, setRedirectToLogin] = useState(false);
  const [showGlobalPermissionModal, setShowGlobalPermissionModal] = useState(false);

  // 로그인 페이지로 리다이렉트
  useEffect(() => {
    if (redirectToLogin && !isAuthenticated) {
      setAuthMode('login');
      setRedirectToLogin(false);
    }
  }, [redirectToLogin, isAuthenticated]);

  // 전역 리다이렉트 함수 (window 객체에 등록)
  useEffect(() => {
    window.redirectToLogin = () => {
      setRedirectToLogin(true);
    };
    return () => {
      delete window.redirectToLogin;
    };
  }, []);

  // 전역 권한 모달 이벤트 리스너
  useEffect(() => {
    const handleShowPermissionModal = () => {
      setShowGlobalPermissionModal(true);
    };

    window.addEventListener('showPermissionModal', handleShowPermissionModal);

    return () => {
      window.removeEventListener('showPermissionModal', handleShowPermissionModal);
    };
  }, []);

  // 전역 탭 전환 함수 등록
  useEffect(() => {
    window.setActiveTab = (tab) => {
      setActiveTab(tab);
    };
    return () => {
      delete window.setActiveTab;
    };
  }, []);

  // API 인터셉터 설정 (앱 시작 시 한 번만)
  useEffect(() => {
    if (typeof window !== 'undefined') {
      setupApiInterceptors();
    }
  }, []);

  // 로딩 중일 때
  if (loading) {
    return (
      <LoadingContainer>
        로딩 중...
      </LoadingContainer>
    );
  }

  // 인증되지 않은 경우
  if (!isAuthenticated) {
    return (
      <AuthContainer>
        {authMode === 'login' ? (
          <LoginForm
            onSwitchToRegister={() => setAuthMode('register')}
          />
        ) : (
          <RegisterForm
            onRegisterSuccess={() => {
              // 회원가입 성공 시 로그인 모드로 전환
              setAuthMode('login');
            }}
            onSwitchToLogin={() => setAuthMode('login')}
          />
        )}
      </AuthContainer>
    );
  }

  const renderContent = () => {
    switch (activeTab) {
      case 'location-services':
        return <LocationServiceMap />;
      case 'care-requests':
        return <CareRequestList />;
      case 'community':
        return <CommunityBoard />;
      case 'missing-pets':
        return <MissingPetBoardPage />;
      case 'meetup':
        return <MeetupPage />;
      case 'users':
        return <UserList />;
      case 'admin':
        return <AdminPanel />;
      case 'activity':
        return <ActivityPage />;
      case 'home':
      default:
        return <HomePage setActiveTab={setActiveTab} />;
    }
  };

  return (
    <>
      <PermissionDeniedModal
        isOpen={showGlobalPermissionModal}
        onClose={() => setShowGlobalPermissionModal(false)}
      />
      <Navigation
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        user={user}
      />
      <MainContent>
        {renderContent()}
      </MainContent>
      <ScrollToTopBottom />
    </>
  );
}

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <AppContainer>
          <GlobalStyle />
          <AppContent />
        </AppContainer>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;

const GlobalStyle = createGlobalStyle`
  * {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
  }

  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
      'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
      sans-serif;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    transition: background-color 0.3s ease, color 0.3s ease;
  }

  code {
    font-family: source-code-pro, Menlo, Monaco, Consolas, 'Courier New',
      monospace;
  }

  /* 스크롤바 스타일링 */
  ::-webkit-scrollbar {
    width: 8px;
  }

  ::-webkit-scrollbar-track {
    background: ${props => props.theme?.colors?.surface || '#f1f1f1'};
  }

  ::-webkit-scrollbar-thumb {
    background: ${props => props.theme?.colors?.border || '#c1c1c1'};
    border-radius: 4px;
  }

  ::-webkit-scrollbar-thumb:hover {
    background: ${props => props.theme?.colors?.primary || '#FF7E36'};
  }
`;

const AppContainer = styled.div`
  min-height: 100vh;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  transition: all 0.3s ease;
`;

const MainContent = styled.main`
  min-height: calc(100vh - 80px);
`;

const AuthContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 80px);
  padding: 2rem;
`;

const LoadingContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 80px);
  font-size: 1.2rem;
  color: ${props => props.theme.colors.text};
`;
