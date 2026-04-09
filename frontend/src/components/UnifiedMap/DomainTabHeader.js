import React from 'react';
import styled from 'styled-components';
import { LAYER_CONFIG } from '../../api/unifiedMapApi';

const TABS = [
  { id: 'location', ...LAYER_CONFIG.location, disabled: false },
  { id: 'meetup', ...LAYER_CONFIG.meetup, disabled: false },
  { id: 'care', ...LAYER_CONFIG.care, disabled: false },
];

const DomainTabHeader = ({ activeLayer, onTabChange, controlsCollapsed, onToggleControls }) => {
  return (
    <TabBar>
      <TabsGroup>
        {TABS.map(tab => (
          <TabButton
            key={tab.id}
            $active={activeLayer === tab.id}
            $color={tab.color}
            $disabled={tab.disabled}
            onClick={() => !tab.disabled && onTabChange(tab.id)}
            title={tab.disabled ? '준비 중' : tab.label}
          >
            <TabIcon>{tab.icon}</TabIcon>
            <TabLabel>{tab.label}</TabLabel>
            {tab.disabled && <ComingSoon>준비중</ComingSoon>}
          </TabButton>
        ))}
      </TabsGroup>
      <FoldButton
        type="button"
        onClick={onToggleControls}
        title={controlsCollapsed ? '옵션 펼치기' : '옵션 접기'}
      >
        {controlsCollapsed ? '옵션 열기 ▾' : '옵션 접기 ▴'}
      </FoldButton>
    </TabBar>
  );
};

export default DomainTabHeader;

const TabBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const TabsGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const TabButton = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 20px;
  border: 2px solid ${props => props.$active ? props.$color : props.theme.colors.border};
  background: ${props => props.$active ? props.$color : props.theme.colors.background};
  color: ${props => props.$active ? 'white' : props.theme.colors.text};
  font-size: 13px;
  font-weight: ${props => props.$active ? 600 : 400};
  cursor: ${props => props.$disabled ? 'not-allowed' : 'pointer'};
  opacity: ${props => props.$disabled ? 0.45 : 1};
  transition: all 0.2s ease;
  position: relative;

  &:hover:not(:disabled) {
    border-color: ${props => props.$color};
    color: ${props => props.$active ? 'white' : props.$color};
  }
`;

const TabIcon = styled.span`
  font-size: 14px;
`;

const TabLabel = styled.span``;

const ComingSoon = styled.span`
  font-size: 10px;
  background: ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.textSecondary};
  padding: 1px 5px;
  border-radius: 8px;
  margin-left: 2px;
`;

const FoldButton = styled.button`
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.textSecondary};
  border-radius: 14px;
  padding: 6px 10px;
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.theme.colors.primary};
  }
`;
