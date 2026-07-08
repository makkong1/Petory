import React from 'react';
import styled from 'styled-components';
import ConversationItem from './ConversationItem';
import ChatTabs, { CONVERSATION_TYPES } from './ChatTabs';
import EmptyState from '../Common/ui/EmptyState';

const ChatList = ({ 
  conversations = [], 
  activeTab, 
  onTabChange, 
  onConversationClick,
  loading = false 
}) => {
  // 탭별 필터링
  const filteredConversations = activeTab === CONVERSATION_TYPES.ALL
    ? conversations
    : conversations.filter(conv => conv.conversationType === activeTab);

  return (
    <ListContainer>
      <ChatTabs activeTab={activeTab} onTabChange={onTabChange} />
      
      <ConversationList>
        {loading ? (
          <LoadingMessage>로딩 중...</LoadingMessage>
        ) : filteredConversations.length === 0 ? (
          <EmptyState
            icon="💬"
            title={activeTab === CONVERSATION_TYPES.ALL
              ? '아직 대화가 없어요'
              : '해당 카테고리의 채팅방이 없어요'}
            description={activeTab === CONVERSATION_TYPES.ALL
              ? '케어 요청이나 모임에 참여하면 채팅방이 생겨요'
              : undefined}
          />
        ) : (
          filteredConversations.map((conversation) => (
            <ConversationItem
              key={conversation.idx}
              conversation={conversation}
              onClick={() => onConversationClick(conversation)}
            />
          ))
        )}
      </ConversationList>
    </ListContainer>
  );
};

export default ChatList;

const ListContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: ${({ theme }) => theme.colors.background};
  overflow: hidden;
`;

const ConversationList = styled.div`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  
  /* 스크롤바 스타일 */
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-track {
    background: ${({ theme }) => theme.colors.surface};
  }
  
  &::-webkit-scrollbar-thumb {
    background: ${({ theme }) => theme.colors.border};
    border-radius: 3px;
    
    &:hover {
      background: ${({ theme }) => theme.colors.textLight};
    }
  }
`;

const LoadingMessage = styled.div`
  padding: 40px 20px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 14px;
`;

