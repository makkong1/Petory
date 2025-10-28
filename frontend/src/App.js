import React from 'react';
import styled, { createGlobalStyle } from 'styled-components';
import UserList from './components/UserList';

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
    background-color: #f8f9fa;
    color: #333;
  }

  code {
    font-family: source-code-pro, Menlo, Monaco, Consolas, 'Courier New',
      monospace;
  }
`;

const AppContainer = styled.div`
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
`;

const Header = styled.header`
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 20px 0;
  margin-bottom: 20px;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
`;

const HeaderContent = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
`;

const Logo = styled.h1`
  color: #4a90e2;
  font-size: 32px;
  font-weight: bold;
  text-align: center;
  margin: 0;
`;

const MainContent = styled.main`
  min-height: calc(100vh - 120px);
`;

function App() {
  return (
    <AppContainer>
      <GlobalStyle />
      <Header>
        <HeaderContent>
          <Logo>🐾 Petory</Logo>
        </HeaderContent>
      </Header>
      <MainContent>
        <UserList />
      </MainContent>
    </AppContainer>
  );
}

export default App;
