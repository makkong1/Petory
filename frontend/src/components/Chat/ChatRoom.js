import React, { useState, useEffect, useRef } from 'react';
import styled from 'styled-components';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from '../../contexts/AuthContext';
import { getMessages, sendMessage, markAsRead, getConversation } from '../../api/chatApi';

const ChatRoom = ({ conversationIdx, onClose, onBack }) => {
  const { user } = useAuth();
  const [messages, setMessages] = useState([]);
  const [conversation, setConversation] = useState(null);
  const [messageInput, setMessageInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [connected, setConnected] = useState(false);
  const messagesEndRef = useRef(null);
  const messagesContainerRef = useRef(null);
  const stompClientRef = useRef(null);

  // 메시지 목록 조회
  const fetchMessages = async () => {
    if (!conversationIdx || !user?.idx) return;

    setLoading(true);
    try {
      const data = await getMessages(conversationIdx, user.idx, 0, 100);
      setMessages(data.content || data || []);

      // 읽음 처리
      if (data.content && data.content.length > 0) {
        const lastMessage = data.content[data.content.length - 1];
        await markAsRead(conversationIdx, user.idx, lastMessage.idx);
      }
    } catch (error) {
      console.error('메시지 조회 실패:', error);
      alert('메시지를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 채팅방 정보 조회
  const fetchConversation = async () => {
    if (!conversationIdx || !user?.idx) return;

    try {
      const data = await getConversation(conversationIdx, user.idx);
      setConversation(data);
    } catch (error) {
      console.error('채팅방 정보 조회 실패:', error);
    }
  };

  useEffect(() => {
    if (conversationIdx && user?.idx) {
      fetchConversation();
      fetchMessages();
    }
  }, [conversationIdx, user?.idx]);

  // WebSocket 연결 및 구독
  useEffect(() => {
    if (!conversationIdx || !user?.idx) return;

    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (!token) {
      console.error('WebSocket 연결 실패: 토큰이 없습니다.');
      return;
    }

    // SockJS와 STOMP 클라이언트 생성
    // SockJS는 쿼리 파라미터로 토큰을 전달해야 함
    const socket = new SockJS(`http://localhost:8080/ws?token=${encodeURIComponent(token)}`);
    const stompClient = new Client({
      webSocketFactory: () => socket,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        console.log('STOMP:', str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('WebSocket 연결 성공');
        setConnected(true);

        // 채팅방 메시지 구독
        stompClient.subscribe(
          `/topic/conversation/${conversationIdx}`,
          (message) => {
            try {
              const messageData = JSON.parse(message.body);
              console.log('새 메시지 수신:', messageData);

              // 중복 방지: 이미 있는 메시지는 추가하지 않음
              setMessages(prev => {
                const exists = prev.some(msg => msg.idx === messageData.idx);
                if (exists) return prev;
                return [...prev, messageData];
              });

              // 읽음 처리 (내가 보낸 메시지가 아닌 경우)
              if (messageData.senderIdx !== user.idx) {
                markAsRead(conversationIdx, user.idx, messageData.idx).catch(err => {
                  console.error('읽음 처리 실패:', err);
                });
              }
            } catch (error) {
              console.error('메시지 파싱 실패:', error);
            }
          },
          {
            Authorization: `Bearer ${token}`,
          }
        );
      },
      onStompError: (frame) => {
        console.error('STOMP 오류:', frame);
        setConnected(false);
      },
      onDisconnect: () => {
        console.log('WebSocket 연결 해제');
        setConnected(false);
      },
    });

    stompClient.activate();
    stompClientRef.current = stompClient;

    // cleanup
    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
    };
  }, [conversationIdx, user?.idx]);

  // 메시지 전송 (WebSocket 사용)
  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!messageInput.trim() || sending || !conversationIdx || !user?.idx || !connected) return;

    const content = messageInput.trim();
    setMessageInput('');
    setSending(true);

    try {
      // WebSocket으로 메시지 전송
      if (stompClientRef.current && stompClientRef.current.connected) {
        stompClientRef.current.publish({
          destination: `/app/chat.send`,
          body: JSON.stringify({
            conversationIdx: conversationIdx,
            content: content,
            messageType: 'TEXT',
          }),
          headers: {
            Authorization: `Bearer ${localStorage.getItem('accessToken') || localStorage.getItem('token')}`,
          },
        });

        // 읽음 처리 (내가 보낸 메시지)
        await markAsRead(conversationIdx, user.idx, null);
      } else {
        // WebSocket이 연결되지 않은 경우 HTTP API로 폴백
        const newMessage = await sendMessage(conversationIdx, user.idx, content);
        setMessages(prev => [...prev, newMessage]);
        await markAsRead(conversationIdx, user.idx, newMessage.idx);
      }
    } catch (error) {
      console.error('메시지 전송 실패:', error);
      alert(error.response?.data?.error || '메시지 전송에 실패했습니다.');
      setMessageInput(content); // 실패 시 입력 내용 복원
    } finally {
      setSending(false);
    }
  };

  // 스크롤을 맨 아래로
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // 날짜 포맷팅
  const formatTime = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    const hours = date.getHours();
    const minutes = date.getMinutes();
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
  };

  // 상대방 정보 가져오기
  const getOtherParticipant = () => {
    if (!conversation?.participants) return null;
    return conversation.participants.find(p => p.userIdx !== user?.idx);
  };

  const otherParticipant = getOtherParticipant();

  return (
    <Container>
      <Header>
        {onBack && (
          <BackButton onClick={onBack}>←</BackButton>
        )}
        <HeaderInfo>
          <HeaderTitle>
            {conversation?.conversationType === 'MISSING_PET'
              ? '실종제보 채팅'
              : conversation?.conversationType === 'CARE_REQUEST'
                ? '케어 요청 채팅'
                : conversation?.conversationType === 'MEETUP'
                  ? '산책모임 채팅'
                  : otherParticipant?.username || '채팅방'}
          </HeaderTitle>
          <HeaderSubtitle>
            {otherParticipant && `${otherParticipant.username} • `}
            {connected ? '🟢 연결됨' : '🔴 연결 중...'}
          </HeaderSubtitle>
        </HeaderInfo>
        {onClose && (
          <CloseButton onClick={onClose}>✕</CloseButton>
        )}
      </Header>

      <MessagesContainer ref={messagesContainerRef}>
        {loading ? (
          <LoadingMessage>메시지를 불러오는 중...</LoadingMessage>
        ) : messages.length === 0 ? (
          <EmptyMessage>메시지가 없습니다. 첫 메시지를 보내보세요!</EmptyMessage>
        ) : (
          messages.map((message, index) => {
            const isMyMessage = message.senderIdx === user?.idx;
            const showTime = index === 0 ||
              new Date(message.createdAt).getTime() - new Date(messages[index - 1].createdAt).getTime() > 60000;

            return (
              <MessageWrapper key={message.idx || index} isMyMessage={isMyMessage}>
                {!isMyMessage && (
                  <SenderName>{message.senderName || '알 수 없음'}</SenderName>
                )}
                <MessageBubble isMyMessage={isMyMessage}>
                  <MessageContent>{message.content}</MessageContent>
                  {showTime && (
                    <MessageTime>{formatTime(message.createdAt)}</MessageTime>
                  )}
                </MessageBubble>
              </MessageWrapper>
            );
          })
        )}
        <div ref={messagesEndRef} />
      </MessagesContainer>

      <InputContainer>
        <MessageForm onSubmit={handleSendMessage}>
          <MessageInput
            type="text"
            value={messageInput}
            onChange={(e) => setMessageInput(e.target.value)}
            placeholder="메시지를 입력하세요..."
            disabled={sending}
          />
          <SendButton type="submit" disabled={sending || !messageInput.trim()}>
            {sending ? '전송 중...' : '전송'}
          </SendButton>
        </MessageForm>
      </InputContainer>
    </Container>
  );
};

export default ChatRoom;

const Container = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  background: ${({ theme }) => theme.colors.background};
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  gap: 12px;
`;

const BackButton = styled.button`
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: ${({ theme }) => theme.colors.text};
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
  
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
`;

const HeaderInfo = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const HeaderTitle = styled.div`
  font-size: 16px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const HeaderSubtitle = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const CloseButton = styled.button`
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
  
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
    color: ${({ theme }) => theme.colors.text};
  }
`;

const MessagesContainer = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  
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

const MessageWrapper = styled.div`
  display: flex;
  flex-direction: column;
  align-items: ${({ isMyMessage }) => isMyMessage ? 'flex-end' : 'flex-start'};
  gap: 4px;
`;

const SenderName = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.textSecondary};
  padding: 0 8px;
`;

const MessageBubble = styled.div`
  max-width: 70%;
  padding: 10px 14px;
  border-radius: ${({ isMyMessage }) =>
    isMyMessage ? '16px 16px 4px 16px' : '16px 16px 16px 4px'};
  background: ${({ theme, isMyMessage }) =>
    isMyMessage ? theme.colors.primary : theme.colors.surfaceElevated};
  color: ${({ theme, isMyMessage }) =>
    isMyMessage ? '#ffffff' : theme.colors.text};
  word-wrap: break-word;
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const MessageContent = styled.div`
  font-size: 14px;
  line-height: 1.4;
`;

const MessageTime = styled.div`
  font-size: 11px;
  opacity: 0.7;
  align-self: flex-end;
`;

const InputContainer = styled.div`
  padding: 12px 16px;
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
`;

const MessageForm = styled.form`
  display: flex;
  gap: 8px;
  align-items: center;
`;

const MessageInput = styled.input`
  flex: 1;
  padding: 10px 14px;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: 20px;
  font-size: 14px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  
  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const SendButton = styled.button`
  padding: 10px 20px;
  border: none;
  border-radius: 20px;
  background: ${({ theme }) => theme.colors.primary};
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primaryDark};
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
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

