import React, { useState } from 'react';
import styled from 'styled-components';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import UserProfileModal from '../User/UserProfileModal';

const NavContainer = styled.nav`
  background: ${props => props.theme.colors.background};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.md} 0;
  position: sticky;
  top: 0;
  z-index: 1000;
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px ${props => props.theme.colors.shadow};
`;

const NavContent = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 ${props => props.theme.spacing.lg};
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const Logo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  color: ${props => props.theme.colors.primary};
  cursor: pointer;
  
  .icon {
    font-size: 28px;
  }
`;

const NavMenu = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'isOpen',
})`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xl};
  
  @media (max-width: 768px) {
    display: ${props => props.isOpen ? 'flex' : 'none'};
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    background: ${props => props.theme.colors.background};
    flex-direction: column;
    padding: ${props => props.theme.spacing.lg};
    border-top: 1px solid ${props => props.theme.colors.border};
    box-shadow: 0 4px 12px ${props => props.theme.colors.shadow};
  }
`;

const NavItem = styled.a`
  color: ${props => props.theme.colors.text};
  text-decoration: none;
  font-weight: 500;
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.md};
  transition: all 0.2s ease;
  cursor: pointer;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
  
  &.active {
    background: ${props => props.theme.colors.primary};
    color: white;
  }
`;

const ThemeToggle = styled.button`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
  width: 44px;
  height: 44px;
  border-radius: ${props => props.theme.borderRadius.full};
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 20px;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    transform: scale(1.05);
  }
`;

const MobileMenuButton = styled.button`
  display: none;
  background: none;
  border: none;
  color: ${props => props.theme.colors.text};
  font-size: 24px;
  cursor: pointer;
  padding: ${props => props.theme.spacing.sm};
  
  @media (max-width: 768px) {
    display: block;
  }
`;

const RightSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
`;

const UserInfo = styled.button`
  display: inline-flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  color: ${props => props.theme.colors.text};
  font-size: 14px;
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
`;

const LogoutButton = styled.button`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
`;

const Navigation = ({ activeTab, setActiveTab, user }) => {
  const { isDarkMode, toggleTheme } = useTheme();
  const { logout, updateUserProfile } = useAuth();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);

  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  
  const menuItems = [
    { id: 'home', label: '홈', icon: '🏠' },
    { id: 'location-services', label: '주변 서비스', icon: '📍' },
    { id: 'care-requests', label: '펫케어 요청', icon: '🐾' },
    { id: 'missing-pets', label: '실종 제보', icon: '🚨' },
    { id: 'community', label: '커뮤니티', icon: '💬' },
    ...(isAdmin ? [
      { id: 'admin', label: '관리자', icon: '🔧' },
      { id: 'users', label: '사용자 관리', icon: '👥' },
    ] : []),
  ];

  return (
    <>
      <NavContainer>
        <NavContent>
          <Logo onClick={() => setActiveTab('home')}>
            <span className="icon">🦴</span>
            <span>Petory</span>
          </Logo>
          
          <NavMenu isOpen={isMobileMenuOpen}>
            {menuItems.map(item => (
              <NavItem
                key={item.id}
                className={activeTab === item.id ? 'active' : ''}
                onClick={() => {
                  setActiveTab(item.id);
                  setIsMobileMenuOpen(false);
                }}
              >
                <span style={{ marginRight: '8px' }}>{item.icon}</span>
                {item.label}
              </NavItem>
            ))}
          </NavMenu>
          
          <RightSection>
            {user && (
              <UserInfo type="button" onClick={() => setIsProfileOpen(true)}>
                <span role="img" aria-label="user">
                  👤
                </span>
                {user.username || '내 정보'}
              </UserInfo>
            )}
            
            <ThemeToggle onClick={toggleTheme}>
              {isDarkMode ? '🌙' : '☀️'}
            </ThemeToggle>
            
            {user && (
              <LogoutButton onClick={logout}>
                로그아웃
              </LogoutButton>
            )}
            
            <MobileMenuButton onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}>
              ☰
            </MobileMenuButton>
          </RightSection>
        </NavContent>
      </NavContainer>
      {user && (
        <UserProfileModal
          isOpen={isProfileOpen}
          userId={user.idx}
          onClose={() => setIsProfileOpen(false)}
          onUpdated={(updated) => {
            updateUserProfile?.(updated);
          }}
        />
      )}
    </>
  );
};

export default Navigation;
