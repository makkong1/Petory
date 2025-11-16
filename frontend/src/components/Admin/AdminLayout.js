import React from 'react';
import styled from 'styled-components';

const AdminLayout = ({ menuItems, activeKey, onChange, children }) => {
  return (
    <Wrapper>
      <Sidebar>
        <SidebarHeader>관리자 메뉴</SidebarHeader>
        <MenuList>
          {menuItems.map(item => (
            <MenuItem
              key={item.key}
              $active={item.key === activeKey}
              onClick={() => onChange(item.key)}
            >
              <MenuTitle>{item.label}</MenuTitle>
              {item.description && (
                <MenuDescription>{item.description}</MenuDescription>
              )}
            </MenuItem>
          ))}
        </MenuList>
      </Sidebar>
      <Content>{children}</Content>
    </Wrapper>
  );
};

export default AdminLayout;

const Wrapper = styled.div`
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: ${props => props.theme.spacing.xl};
  padding: ${props => props.theme.spacing.xl} 0;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
`;

const Sidebar = styled.aside`
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.lg};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  box-shadow: 0 4px 10px ${props => props.theme.colors.shadow};
  position: sticky;
  top: 92px;

  @media (max-width: 960px) {
    position: static;
  }
`;

const SidebarHeader = styled.h2`
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin-bottom: ${props => props.theme.spacing.md};
  color: ${props => props.theme.colors.text};
`;

const MenuList = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0;
`;

const MenuItem = styled.li`
  border-radius: ${props => props.theme.borderRadius.md};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  margin-bottom: ${props => props.theme.spacing.xs};
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.1s ease;
  background: ${props =>
    props.$active ? props.theme.colors.primarySoft : 'transparent'};
  color: ${props =>
    props.$active ? props.theme.colors.primary : props.theme.colors.textSecondary};

  &:hover {
    background: ${props =>
      props.$active
        ? props.theme.colors.primarySoft
        : props.theme.colors.surfaceHover};
    transform: translateY(-1px);
  }
`;

const MenuTitle = styled.div`
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
`;

const MenuDescription = styled.div`
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  margin-top: 2px;
`;

const Content = styled.section`
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.lg};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.xl};
  box-shadow: 0 4px 10px ${props => props.theme.colors.shadow};
`;


