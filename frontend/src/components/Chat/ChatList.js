import React from 'react';
import styled from 'styled-components';
import ConversationItem from './ConversationItem';
import ChatTabs, { CONVERSATION_TYPES } from './ChatTabs';

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
          <EmptyMessage>
            {activeTab === CONVERSATION_TYPES.ALL 
              ? '채팅방이 없습니다'
              : '해당 카테고리의 채팅방이 없습니다'}
          </EmptyMessage>
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
  background: ${({ theme }) => theme.colors.background};
  overflow: hidden;
`;

const ConversationList = styled.div`
  flex: 1;
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

const EmptyMessage = styled.div`
  padding: 60px 20px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textLight};
  font-size: 14px;
`;

