import React, { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { getMyConversations } from '../../api/chatApi';
import ChatFloatingButton from './ChatFloatingButton';
import ChatModal from './ChatModal';

const ChatWidget = () => {
  const { user, isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [totalUnreadCount, setTotalUnreadCount] = useState(0);

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
    // TODO: 채팅방 상세 페이지로 이동 또는 채팅방 모달 열기
    console.log('채팅방 클릭:', conversation);
    // 임시로 모달 닫기
    setIsOpen(false);
  };

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
      <ChatModal
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        conversations={conversations}
        onConversationClick={handleConversationClick}
        loading={loading}
      />
    </>
  );
};

export default ChatWidget;

