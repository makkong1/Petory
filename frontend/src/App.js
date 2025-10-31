import React, { useState } from 'react';
import styled, { createGlobalStyle } from 'styled-components';
import { ThemeProvider } from './contexts/ThemeContext';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import Navigation from './components/Layout/Navigation';
import HomePage from './components/Home/HomePage';
import UserList from './components/UserList';
import CareRequestList from './components/CareRequest/CareRequestList';
import CommunityBoard from './components/Community/CommunityBoard';
import LocationServiceMap from './components/LocationService/LocationServiceMap';
import LoginForm from './components/Auth/LoginForm';
import RegisterForm from './components/Auth/RegisterForm';
import AdminPanel from './components/Admin/AdminPanel';


function AppContent() {
  const { user, loading, isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState('home');
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'register'

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
    switch(activeTab) {
      case 'location-services':
        return <LocationServiceMap />;
      case 'care-requests':
        return <CareRequestList />;
      case 'community':
        return <CommunityBoard />;
      case 'users':
        return <UserList />;
      case 'admin':
        return <AdminPanel />;
      case 'home':
      default:
        return <HomePage setActiveTab={setActiveTab} />;
    }
  };

  return (
    <>
      <Navigation 
        activeTab={activeTab} 
        setActiveTab={setActiveTab} 
        user={user}
      />
      <MainContent>
        {renderContent()}
      </MainContent>
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
