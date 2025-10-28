import React, { useState } from 'react';
import styled, { createGlobalStyle } from 'styled-components';
import { ThemeProvider } from './contexts/ThemeContext';
import Navigation from './components/Layout/Navigation';
import HomePage from './components/Home/HomePage';
import UserList from './components/UserList';
import CareRequestList from './components/CareRequest/CareRequestList';
import CommunityBoard from './components/Community/CommunityBoard';

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

function App() {
  const [activeTab, setActiveTab] = useState('home');

  const renderContent = () => {
    switch(activeTab) {
      case 'home':
        return <HomePage setActiveTab={setActiveTab} />;
      case 'care-requests':
        return <CareRequestList />;
      case 'community':
        return <CommunityBoard />;
      case 'users':
        return <UserList />;
      default:
        return <HomePage setActiveTab={setActiveTab} />;
    }
  };

  return (
    <ThemeProvider>
      <AppContainer>
        <GlobalStyle />
        <Navigation activeTab={activeTab} setActiveTab={setActiveTab} />
        <MainContent>
          {renderContent()}
        </MainContent>
      </AppContainer>
    </ThemeProvider>
  );
}

export default App;
