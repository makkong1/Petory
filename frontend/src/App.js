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
import OAuth2Callback from './components/Auth/OAuth2Callback';
import AdminPanel from './components/Admin/AdminPanel';
import PermissionDeniedModal from './components/Common/PermissionDeniedModal';
import ScrollToTopBottom from './components/Common/ScrollToTopBottom';
import MissingPetBoardPage from './components/MissingPet/MissingPetBoardPage';
import ActivityPage from './components/Activity/ActivityPage';
import MeetupPage from './components/Meetup/MeetupPage';
import ChatWidget from './components/Chat/ChatWidget';
import EmailVerificationPage from './components/Auth/EmailVerificationPage';
import EmailVerificationPrompt from './components/Common/EmailVerificationPrompt';
import { setupApiInterceptors } from './api/authApi';


function AppContent() {
  const { user, loading, isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState('home');
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'register'
  const [redirectToLogin, setRedirectToLogin] = useState(false);
  const [showGlobalPermissionModal, setShowGlobalPermissionModal] = useState(false);
  const [showGlobalEmailVerificationPrompt, setShowGlobalEmailVerificationPrompt] = useState(false);
  const [emailVerificationPurpose, setEmailVerificationPurpose] = useState(null);

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
      console.log('🎯 전역 이벤트 리스너: emailVerificationRequired 수신', event.detail);
      const { purpose, currentUrl } = event.detail;
      console.log('🎯 프롬프트 표시 설정:', { purpose, currentUrl });
      setEmailVerificationPurpose(purpose);
      setShowGlobalEmailVerificationPrompt(true);
      console.log('🎯 showGlobalEmailVerificationPrompt를 true로 설정');
    };

    console.log('🎯 전역 이벤트 리스너 등록: emailVerificationRequired');
    window.addEventListener('emailVerificationRequired', handleEmailVerificationRequired);

    return () => {
      console.log('🎯 전역 이벤트 리스너 제거: emailVerificationRequired');
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

  // API 인터셉터 설정 (앱 시작 시 한 번만)
  useEffect(() => {
    if (typeof window !== 'undefined') {
      setupApiInterceptors();
    }
  }, []);

  // OAuth2 콜백 페이지 체크 (useState로 관리)
  const [isOAuth2Callback, setIsOAuth2Callback] = useState(() => {
    if (typeof window === 'undefined') return false;
    const urlParams = new URLSearchParams(window.location.search);
    return window.location.pathname.includes('oauth2/callback') || 
           urlParams.has('accessToken') ||
           urlParams.has('error');
  });

  // 이메일 인증 페이지 체크
  const [isEmailVerificationPage, setIsEmailVerificationPage] = useState(() => {
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
      <EmailVerificationPrompt
        isOpen={showGlobalEmailVerificationPrompt}
        onConfirm={handleEmailVerificationConfirm}
        onCancel={handleEmailVerificationCancel}
        purpose={emailVerificationPurpose}
      />
      {console.log('🔍 App.js 렌더링:', { 
        showGlobalEmailVerificationPrompt, 
        emailVerificationPurpose 
      })}
      <Navigation
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        user={user}
      />
      <MainContent>
        {renderContent()}
      </MainContent>
      <ScrollToTopBottom />
      <ChatWidget />
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
