import React from 'react';
import styled from 'styled-components';
import { LAYER_CONFIG } from '../../api/unifiedMapApi';

const TABS = [
  { id: 'location', ...LAYER_CONFIG.location, disabled: false },
  { id: 'meetup', ...LAYER_CONFIG.meetup, disabled: false },
  { id: 'care', ...LAYER_CONFIG.care, disabled: false },
];

const DomainTabHeader = ({ activeLayer, onTabChange }) => {
  return (
    <TabBar role="tablist">
      <TabsGroup>
        {TABS.map(tab => (
          <TabButton
            key={tab.id}
            role="tab"
            aria-selected={activeLayer === tab.id}
            $active={activeLayer === tab.id}
            $domain={tab.id}
            $disabled={tab.disabled}
            onClick={() => !tab.disabled && onTabChange(tab.id)}
            title={tab.disabled ? '준비 중' : tab.label}
          >
            <TabIcon aria-hidden="true">{tab.icon}</TabIcon>
            <TabLabel>{tab.label}</TabLabel>
            {tab.disabled && <ComingSoon>준비중</ComingSoon>}
          </TabButton>
        ))}
      </TabsGroup>
    </TabBar>
  );
};

export default DomainTabHeader;

const TabBar = styled.div`
  display: flex;
  align-items: center;
  padding: 10px 16px;
  background: ${props => props.theme.colors.surfaceElevated + 'D9'};
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 50px;
  box-shadow: ${props => props.theme.shadows.md};
`;

const TabsGroup = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
  overflow-x: auto;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const TabButton = styled.button`
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 16px;
  border-radius: 50px;
  border: 1.5px solid ${props => props.$active
    ? props.theme.colors.domain[props.$domain] || props.theme.colors.primary
    : props.theme.colors.border};
  background: ${props => props.$active
    ? props.theme.colors.domain[props.$domain] || props.theme.colors.primary
    : props.theme.colors.surfaceElevated + 'D9'};
  color: ${props => props.$active ? props.theme.colors.textInverse : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${props => props.$active ? '600' : '400'};
  cursor: ${props => props.$disabled ? 'not-allowed' : 'pointer'};
  opacity: ${props => props.$disabled ? 0.4 : 1};
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
  white-space: nowrap;

  &:hover:not([disabled]) {
    border-color: ${props => props.theme.colors.domain[props.$domain] || props.theme.colors.primary};
  }
`;

const TabIcon = styled.span`
  font-size: 15px;
`;

const TabLabel = styled.span``;

const ComingSoon = styled.span`
  font-size: 10px;
  background: ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.textMuted};
  padding: 1px 5px;
  border-radius: 999px;
  margin-left: 2px;
`;

