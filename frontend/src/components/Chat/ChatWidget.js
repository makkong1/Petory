import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { getMyConversations } from '../../api/chatApi';
import ChatFloatingButton from './ChatFloatingButton';
import ChatModal from './ChatModal';
import ChatRoom from './ChatRoom';

const ChatWidget = () => {
  const { user, isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [totalUnreadCount, setTotalUnreadCount] = useState(0);
  const [selectedConversationIdx, setSelectedConversationIdx] = useState(null);

  // 채팅방 목록 조회
  const fetchConversations = async () => {
    if (!isAuthenticated || !user?.idx) return;

    setLoading(true);
    try {
      const data = await getMyConversations(user.idx);
      setConversations(data || []);
      
      // 전체 읽지 않은 메시지 수 계산
      const totalUnread = (data || []).reduce((sum, conv) => sum + (conv.unreadCount || 0), 0);
      setTotalUnreadCount(totalUnread);
    } catch (error) {
      console.error('채팅방 목록 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  // 초기 로드
  useEffect(() => {
    if (isAuthenticated && user?.idx) {
      fetchConversations();
    }
  }, [isAuthenticated, user?.idx]);

  // 주기적으로 새로고침 (30초마다)
  useEffect(() => {
    if (!isAuthenticated || !user?.idx) return;

    const interval = setInterval(() => {
      fetchConversations();
    }, 30000); // 30초

    return () => clearInterval(interval);
  }, [isAuthenticated, user?.id]);

  // 채팅방 클릭 핸들러
  const handleConversationClick = (conversation) => {
    setSelectedConversationIdx(conversation.idx);
    setIsOpen(false);
  };

  // 채팅방 닫기
  const handleCloseChatRoom = () => {
    setSelectedConversationIdx(null);
    // 채팅방 목록 새로고침
    if (isAuthenticated && user?.idx) {
      fetchConversations();
    }
  };

  // 채팅방 나가기/삭제 후 콜백
  const handleConversationAction = () => {
    setSelectedConversationIdx(null);
    // 채팅방 목록 새로고침
    if (isAuthenticated && user?.idx) {
      fetchConversations();
    }
  };

  // 전역 함수 등록: 다른 컴포넌트에서 채팅방 열기
  useEffect(() => {
    window.openChatWidget = (conversationIdx) => {
      setSelectedConversationIdx(conversationIdx);
      setIsOpen(false);
    };
    
    return () => {
      delete window.openChatWidget;
    };
  }, []);

  // 로그인하지 않은 사용자는 채팅 버튼 표시 안 함
  if (!isAuthenticated || !user) {
    return null;
  }

  return (
    <>
      <ChatFloatingButton
        onClick={() => setIsOpen(true)}
        unreadCount={totalUnreadCount}
      />
      {selectedConversationIdx ? (
        <>
          <ChatRoomOverlay onClick={handleCloseChatRoom} />
          <ChatRoomModal>
            <ChatRoom
              conversationIdx={selectedConversationIdx}
              onClose={handleCloseChatRoom}
              onBack={handleCloseChatRoom}
              onAction={handleConversationAction}
            />
          </ChatRoomModal>
        </>
      ) : (
        <ChatModal
          isOpen={isOpen}
          onClose={() => setIsOpen(false)}
          conversations={conversations}
          onConversationClick={handleConversationClick}
          loading={loading}
        />
      )}
    </>
  );
};

const ChatRoomOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 998;
  animation: fadeIn 0.3s ease;
  
  @keyframes fadeIn {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }
`;

const ChatRoomModal = styled.div`
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 80vh;
  max-height: 800px;
  background: ${({ theme }) => theme.colors.background};
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.15);
  z-index: 999;
  display: flex;
  flex-direction: column;
  animation: slideUp 0.3s ease;
  
  @keyframes slideUp {
    from {
      transform: translateY(100%);
    }
    to {
      transform: translateY(0);
    }
  }
  
  @media (min-width: 769px) {
    /* 데스크톱: 중앙 정렬 */
    top: 50%;
    left: 50%;
    right: auto;
    bottom: auto;
    transform: translate(-50%, -50%);
    width: 480px;
    max-width: 90vw;
    height: 600px;
    max-height: 90vh;
    border-radius: 16px;
    animation: slideUpDesktop 0.3s ease;
    
    @keyframes slideUpDesktop {
      from {
        transform: translate(-50%, -40%);
        opacity: 0;
      }
      to {
        transform: translate(-50%, -50%);
        opacity: 1;
      }
    }
  }
`;

export default ChatWidget;

