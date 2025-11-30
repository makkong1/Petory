import React from 'react';
import styled from 'styled-components';

const CONVERSATION_TYPES = {
  ALL: 'ALL',
  CARE_REQUEST: 'CARE_REQUEST',
  MISSING_PET: 'MISSING_PET',
  DIRECT: 'DIRECT',
  GROUP: 'GROUP',
  ADMIN_SUPPORT: 'ADMIN_SUPPORT',
};

const CONVERSATION_LABELS = {
  [CONVERSATION_TYPES.ALL]: '전체',
  [CONVERSATION_TYPES.CARE_REQUEST]: '펫케어',
  [CONVERSATION_TYPES.MISSING_PET]: '실종',
  [CONVERSATION_TYPES.DIRECT]: '개인간',
  [CONVERSATION_TYPES.GROUP]: '단체간',
  [CONVERSATION_TYPES.ADMIN_SUPPORT]: '관리자',
};

const ChatTabs = ({ activeTab, onTabChange }) => {
  return (
    <TabsContainer>
      {Object.values(CONVERSATION_TYPES).map((type) => (
        <Tab
          key={type}
          active={activeTab === type}
          onClick={() => onTabChange(type)}
        >
          {CONVERSATION_LABELS[type]}
        </Tab>
      ))}
    </TabsContainer>
  );
};

export default ChatTabs;
export { CONVERSATION_TYPES, CONVERSATION_LABELS };

const TabsContainer = styled.div`
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.surface};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  overflow-x: auto;
  
  /* 스크롤바 숨기기 */
  scrollbar-width: none;
  -ms-overflow-style: none;
  &::-webkit-scrollbar {
    display: none;
  }
`;

const Tab = styled.button`
  padding: 8px 16px;
  border: none;
  border-radius: 20px;
  background: ${({ active, theme }) =>
    active ? theme.colors.primary : theme.colors.background};
  color: ${({ active, theme }) =>
    active ? 'white' : theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${({ active }) => (active ? 600 : 400)};
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
  
  &:hover {
    background: ${({ active, theme }) =>
      active ? theme.colors.primaryDark : theme.colors.surfaceHover};
  }
  
  @media (max-width: 768px) {
    padding: 6px 12px;
    font-size: 12px;
  }
`;

