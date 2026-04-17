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
  align-items: stretch;
  padding: 0 4px;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const TabsGroup = styled.div`
  display: flex;
  align-items: stretch;
  overflow-x: auto;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const TabButton = styled.button`
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 10px 14px;
  border: none;
  border-bottom: 2.5px solid ${props => props.$active
    ? props.theme.colors.domain[props.$domain]
    : 'transparent'};
  background: transparent;
  color: ${props => props.$active
    ? props.theme.colors.domain[props.$domain]
    : props.theme.colors.textSecondary};
  font-size: 14px;
  font-weight: ${props => props.$active ? 600 : 400};
  cursor: ${props => props.$disabled ? 'not-allowed' : 'pointer'};
  opacity: ${props => props.$disabled ? 0.4 : 1};
  transition: color 0.15s, border-color 0.15s;
  white-space: nowrap;
  margin-bottom: -1px;

  &:hover:not([disabled]) {
    color: ${props => props.theme.colors.domain[props.$domain]};
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

