import React, { useState, useEffect } from 'react';
import styled, { createGlobalStyle } from 'styled-components';
import { ThemeProvider } from './contexts/ThemeContext';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import Navigation from './components/Layout/Navigation';
import HomePage from './components/Home/HomePage';
import UserList from './components/User/UserList';
import CommunityBoard from './components/Community/CommunityBoard';
import LoginForm from './components/Auth/LoginForm';
import RegisterForm from './components/Auth/RegisterForm';
import AuthLayout from './components/Auth/AuthLayout';
import OAuth2Callback from './components/Auth/OAuth2Callback';
import AdminPanel from './components/Admin/AdminPanel';
import PermissionDeniedModal from './components/Common/PermissionDeniedModal';
import ScrollToTopBottom from './components/Common/ScrollToTopBottom';
import MissingPetBoardPage from './components/MissingPet/MissingPetBoardPage';
import ActivityPage from './components/Activity/ActivityPage';
import UnifiedPetMapPage from './components/UnifiedMap/UnifiedPetMapPage';
import TrendCategoryPage from './components/Recommendation/TrendCategoryPage';
import ChatWidget from './components/Chat/ChatWidget';
import EmailVerificationPage from './components/Auth/EmailVerificationPage';
import EmailVerificationPrompt from './components/Common/EmailVerificationPrompt.js';
import { initPushNotifications } from './api/pushNotifications';

function AppContent() {
  const { user, loading, isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState('home');
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'register'
  const [redirectToLogin, setRedirectToLogin] = useState(false);
  const [showGlobalPermissionModal, setShowGlobalPermissionModal] = useState(false);
  const [showGlobalEmailVerificationPrompt, setShowGlobalEmailVerificationPrompt] = useState(false);
  const [emailVerificationPurpose, setEmailVerificationPurpose] = useState(null);

  // 로그인 시 FCM 푸시 알림 초기화 (Capacitor 앱 전용, 웹은 no-op)
  useEffect(() => {
    if (isAuthenticated) {
      initPushNotifications();
    }
  }, [isAuthenticated]);

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

  // 전역 이메일 인증 필요 이벤트 리스너 (서버 예외 발생 시 백업용)
  useEffect(() => {
    const handleEmailVerificationRequired = (event) => {
      const { purpose } = event.detail;
      setEmailVerificationPurpose(purpose);
      setShowGlobalEmailVerificationPrompt(true);
    };

    window.addEventListener('emailVerificationRequired', handleEmailVerificationRequired);

    return () => {
      window.removeEventListener('emailVerificationRequired', handleEmailVerificationRequired);
    };
  }, []);

  // 전역 이메일 인증 확인 다이얼로그 핸들러
  const handleEmailVerificationConfirm = () => {
    const currentUrl = window.location.pathname + window.location.search;
    const redirectUrl = `/email-verification?redirect=${encodeURIComponent(currentUrl)}${emailVerificationPurpose ? `&purpose=${emailVerificationPurpose}` : ''}`;
    window.location.href = redirectUrl;
  };

  const handleEmailVerificationCancel = () => {
    setShowGlobalEmailVerificationPrompt(false);
    setEmailVerificationPurpose(null);
  };

  // 전역 탭 전환 함수 등록
  useEffect(() => {
    window.setActiveTab = (tab) => {
      setActiveTab(tab);
    };
    return () => {
      delete window.setActiveTab;
    };
  }, []);

  // OAuth2 콜백 페이지 체크 (useState로 관리)
  const [isOAuth2Callback] = useState(() => {
    if (typeof window === 'undefined') return false;
    const urlParams = new URLSearchParams(window.location.search);
    return window.location.pathname.includes('oauth2/callback') ||
      urlParams.has('accessToken') ||
      urlParams.has('error');
  });

  // 이메일 인증 페이지 체크
  const [isEmailVerificationPage] = useState(() => {
    if (typeof window === 'undefined') return false;
    const urlParams = new URLSearchParams(window.location.search);
    return window.location.pathname.includes('email-verification') ||
      window.location.pathname.includes('email-verify') ||
      urlParams.has('token');
  });

  // 로딩 중일 때
  if (loading) {
    return (
      <LoadingContainer>
        로딩 중...
      </LoadingContainer>
    );
  }

  // 이메일 인증 페이지 처리 (인증 여부와 관계없이 접근 가능)
  if (isEmailVerificationPage) {
    return <EmailVerificationPage />;
  }

  // OAuth2 콜백 처리
  if (isOAuth2Callback) {
    return <OAuth2Callback />;
  }

  // 인증되지 않은 경우
  if (!isAuthenticated) {
    return (
      <AuthContainer>
        <AuthLayout
          mode={authMode}
          loginContent={
            <LoginForm onSwitchToRegister={() => setAuthMode('register')} />
          }
          registerContent={
            <RegisterForm
              onRegisterSuccess={() => setAuthMode('login')}
              onSwitchToLogin={() => setAuthMode('login')}
            />
          }
        />
      </AuthContainer>
    );
  }

  const renderContent = () => {
    switch (activeTab) {
      case 'community':
        return <CommunityBoard />;
      case 'missing-pets':
        return <MissingPetBoardPage />;
      case 'unified-map':
        return <UnifiedPetMapPage />;
      case 'trends':
        return <TrendCategoryPage />;
      case 'users':
        return <UserList />;
      case 'admin':
        return <AdminPanel />;
      case 'activity':
        return <ActivityPage />;
      case 'home':
      default:
        return <HomePage setActiveTab={setActiveTab} user={user} />;
    }
  };

  return (
    <AppContainer>
      <PermissionDeniedModal
        isOpen={showGlobalPermissionModal}
        onClose={() => setShowGlobalPermissionModal(false)}
      />
      <EmailVerificationPrompt
        isOpen={showGlobalEmailVerificationPrompt}
        onConfirm={handleEmailVerificationConfirm}
        onCancel={handleEmailVerificationCancel}
        purpose={emailVerificationPurpose}
      />
      <AppLayout>
        <Navigation
          activeTab={activeTab}
          setActiveTab={setActiveTab}
          user={user}
        />
        <ContentArea>
          {renderContent()}
        </ContentArea>
      </AppLayout>
      <ScrollToTopBottom />
      <ChatWidget />
    </AppContainer>
  );
}

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <GlobalStyle />
        <AppContent />
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

const AppLayout = styled.div`
  display: flex;
  min-height: 100vh;
`;

const ContentArea = styled.main`
  flex: 1;
  min-height: 100vh;
  background: ${props => props.theme.colors.background};

  @media (min-width: 769px) {
    margin-left: 240px;
    margin-top: 0;
    min-height: 100vh;
  }

  @media (max-width: 768px) {
    margin-top: 0;
    padding-top: env(safe-area-inset-top, 0px);
    padding-bottom: calc(60px + env(safe-area-inset-bottom, 0px));
    min-height: 100dvh;
  }
`;

const AuthContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 2rem;
`;

const LoadingContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  font-size: 1.2rem;
  color: ${props => props.theme.colors.text};
`;
