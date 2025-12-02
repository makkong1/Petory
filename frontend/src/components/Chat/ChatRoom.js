import React, { useState, useEffect, useRef } from 'react';
import styled from 'styled-components';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from '../../contexts/AuthContext';
import { getMessages, sendMessage, markAsRead, getConversation, leaveConversation, deleteConversation } from '../../api/chatApi';
import { uploadApi } from '../../api/uploadApi';

const ChatRoom = ({ conversationIdx, onClose, onBack, onAction }) => {
  const { user } = useAuth();
  const [messages, setMessages] = useState([]);
  const [conversation, setConversation] = useState(null);
  const [messageInput, setMessageInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [connected, setConnected] = useState(false);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [selectedImage, setSelectedImage] = useState(null);
  const [showMenu, setShowMenu] = useState(false);
  const messagesEndRef = useRef(null);
  const messagesContainerRef = useRef(null);
  const stompClientRef = useRef(null);
  const messageInputRef = useRef(null);
  const fileInputRef = useRef(null);
  const menuRef = useRef(null);

  // 메시지 목록 조회
  const fetchMessages = async () => {
    if (!conversationIdx || !user?.idx) return;

    setLoading(true);
    try {
      const data = await getMessages(conversationIdx, user.idx, 0, 100);
      const messagesList = data.content || data || [];
      // 백엔드에서 DESC로 정렬되어 최신부터 오므로, reverse()로 오래된 것부터 최신 순서로 변경 (최신이 맨 아래)
      const sortedMessages = [...messagesList].reverse();
      setMessages(sortedMessages);

      // 읽음 처리
      if (sortedMessages.length > 0) {
        const lastMessage = sortedMessages[sortedMessages.length - 1];
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
                const newMessages = [...prev, messageData];
                // 시간순으로 정렬 (오래된 것부터 최신 순서 - 최신이 맨 아래)
                return newMessages.sort((a, b) => {
                  const timeA = new Date(a.createdAt).getTime();
                  const timeB = new Date(b.createdAt).getTime();
                  return timeA - timeB;
                });
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

  // 이미지 업로드 및 전송
  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file || !conversationIdx || !user?.idx || uploadingImage) return;

    // 이미지 파일만 허용
    if (!file.type.startsWith('image/')) {
      alert('이미지 파일만 업로드할 수 있습니다.');
      return;
    }

    setUploadingImage(true);

    try {
      // 이미지 업로드
      const uploadData = await uploadApi.uploadImage(file, {
        category: 'chat',
        ownerType: 'user',
        ownerId: user.idx,
        entityId: conversationIdx,
      });

      const imageUrl = uploadData.url;

      // 이미지 메시지 전송
      if (stompClientRef.current && stompClientRef.current.connected) {
        stompClientRef.current.publish({
          destination: `/app/chat.send`,
          body: JSON.stringify({
            conversationIdx: conversationIdx,
            content: imageUrl,
            messageType: 'IMAGE',
          }),
          headers: {
            Authorization: `Bearer ${localStorage.getItem('accessToken') || localStorage.getItem('token')}`,
          },
        });

        await markAsRead(conversationIdx, user.idx, null);
      } else {
        // HTTP API로 폴백
        const newMessage = await sendMessage(conversationIdx, user.idx, imageUrl, 'IMAGE');
        setMessages(prev => [...prev, newMessage]);
        await markAsRead(conversationIdx, user.idx, newMessage.idx);
      }
    } catch (error) {
      console.error('이미지 업로드 실패:', error);
      alert(error.response?.data?.error || '이미지 업로드에 실패했습니다.');
    } finally {
      setUploadingImage(false);
      // 파일 입력 초기화
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

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
      // 전송 후 다시 포커스
      messageInputRef.current?.focus();
    }
  };

  // 스크롤을 맨 아래로
  const scrollToBottom = () => {
    setTimeout(() => {
      if (messagesContainerRef.current) {
        messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
      }
      if (messagesEndRef.current) {
        messagesEndRef.current.scrollIntoView({ behavior: 'auto' });
      }
    }, 100);
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // 메시지 입력창 자동 포커스
  useEffect(() => {
    if (messageInputRef.current && !loading) {
      messageInputRef.current.focus();
    }
  }, [conversationIdx, loading]);

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

  // 채팅방 나가기
  const handleLeaveConversation = async () => {
    if (!conversationIdx || !user?.idx) return;

    if (!window.confirm('정말 채팅방을 나가시겠습니까?')) {
      return;
    }

    try {
      await leaveConversation(conversationIdx, user.idx);
      alert('채팅방에서 나갔습니다.');
      if (onAction) {
        onAction();
      } else if (onClose) {
        onClose();
      }
    } catch (error) {
      console.error('채팅방 나가기 실패:', error);
      alert('채팅방 나가기에 실패했습니다.');
    }
  };

  // 채팅방 삭제
  const handleDeleteConversation = async () => {
    if (!conversationIdx || !user?.idx) return;

    if (!window.confirm('정말 채팅방을 삭제하시겠습니까? 삭제된 채팅방은 복구할 수 없습니다.')) {
      return;
    }

    try {
      await deleteConversation(conversationIdx, user.idx);
      alert('채팅방이 삭제되었습니다.');
      if (onAction) {
        onAction();
      } else if (onClose) {
        onClose();
      }
    } catch (error) {
      console.error('채팅방 삭제 실패:', error);
      alert('채팅방 삭제에 실패했습니다.');
    }
  };

  // 메뉴 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setShowMenu(false);
      }
    };

    if (showMenu) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showMenu]);

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
        <HeaderActions>
          <MenuButton onClick={() => setShowMenu(!showMenu)}>⋮</MenuButton>
          {showMenu && (
            <MenuDropdown ref={menuRef}>
              <MenuItem onClick={handleLeaveConversation}>나가기</MenuItem>
              <MenuItem onClick={handleDeleteConversation} danger>삭제</MenuItem>
            </MenuDropdown>
          )}
          {onClose && (
            <CloseButton onClick={onClose}>✕</CloseButton>
          )}
        </HeaderActions>
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
                  <SenderName>{message.senderUsername || otherParticipant?.username || '알 수 없음'}</SenderName>
                )}
                <MessageBubble isMyMessage={isMyMessage}>
                  {message.messageType === 'IMAGE' ? (
                    <MessageImage
                      src={message.content}
                      alt="이미지"
                      onClick={() => setSelectedImage(message.content)}
                    />
                  ) : (
                    <MessageContent>{message.content}</MessageContent>
                  )}
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
          <HiddenFileInput
            ref={fileInputRef}
            type="file"
            accept="image/*"
            onChange={handleImageUpload}
            disabled={uploadingImage}
          />
          <InputRow>
            <ImageButton
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadingImage}
              title="이미지 업로드"
            >
              {uploadingImage ? '📤' : '📷'}
            </ImageButton>
            <MessageInput
              ref={messageInputRef}
              type="text"
              value={messageInput}
              onChange={(e) => setMessageInput(e.target.value)}
              placeholder="메시지를 입력하세요..."
              disabled={sending || uploadingImage}
            />
            <SendButton type="submit" disabled={sending || uploadingImage || !messageInput.trim()}>
              {sending ? '전송 중...' : '전송'}
            </SendButton>
          </InputRow>
        </MessageForm>
      </InputContainer>

      {/* 이미지 확대 보기 모달 */}
      {selectedImage && (
        <ImageModal onClick={() => setSelectedImage(null)}>
          <ImageModalContent onClick={(e) => e.stopPropagation()}>
            <ImageModalClose onClick={() => setSelectedImage(null)}>✕</ImageModalClose>
            <ImageModalImage src={selectedImage} alt="확대 이미지" />
          </ImageModalContent>
        </ImageModal>
      )}
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

const HeaderActions = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
`;

const MenuButton = styled.button`
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

const MenuDropdown = styled.div`
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  min-width: 120px;
  overflow: hidden;
`;

const MenuItem = styled.button`
  width: 100%;
  padding: 12px 16px;
  border: none;
  background: transparent;
  color: ${({ theme, danger }) => danger ? theme.colors.error : theme.colors.text};
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
  
  &:not(:last-child) {
    border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  }
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
  padding: 12px 16px;
  border-radius: ${({ isMyMessage }) =>
    isMyMessage ? '18px 18px 4px 18px' : '18px 18px 18px 4px'};
  background: ${({ theme, isMyMessage }) =>
    isMyMessage
      ? theme.colors.primary
      : theme.colors.surface || '#E8E8E8'};
  color: ${({ theme, isMyMessage }) =>
    isMyMessage
      ? '#ffffff'
      : theme.colors.text || '#212121'};
  word-wrap: break-word;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: ${({ isMyMessage }) =>
    isMyMessage
      ? '0 2px 8px rgba(0, 0, 0, 0.15)'
      : '0 2px 8px rgba(0, 0, 0, 0.1)'};
  border: ${({ theme, isMyMessage }) =>
    isMyMessage ? 'none' : `1px solid ${theme.colors.border}`};
  position: relative;
  
  /* 말풍선 꼬리 효과 */
  &::after {
    content: '';
    position: absolute;
    width: 0;
    height: 0;
    ${({ isMyMessage, theme }) => isMyMessage
    ? `
        right: -8px;
        bottom: 12px;
        border-top: 8px solid transparent;
        border-bottom: 8px solid transparent;
        border-left: 8px solid ${theme.colors.primary};
      `
    : `
        left: -8px;
        bottom: 12px;
        border-top: 8px solid transparent;
        border-bottom: 8px solid transparent;
        border-right: 8px solid ${theme.colors.surface || '#E8E8E8'};
      `
  }
  }
`;

const MessageContent = styled.div`
  font-size: 15px;
  line-height: 1.5;
  word-wrap: break-word;
  font-weight: 400;
  letter-spacing: 0.01em;
`;

const MessageImage = styled.img`
  max-width: 100%;
  max-height: 300px;
  border-radius: 8px;
  object-fit: contain;
  cursor: pointer;
  
  &:hover {
    opacity: 0.9;
  }
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
  flex-direction: column;
  gap: 8px;
`;

const HiddenFileInput = styled.input`
  display: none;
`;

const InputRow = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
`;

const ImageButton = styled.button`
  width: 40px;
  height: 40px;
  border: none;
  background: ${({ theme }) => theme.colors.surfaceElevated};
  color: ${({ theme }) => theme.colors.text};
  font-size: 20px;
  cursor: pointer;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
  
  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.surfaceHover};
    transform: scale(1.05);
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
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

const ImageModal = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.9);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  cursor: pointer;
`;

const ImageModalContent = styled.div`
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const ImageModalClose = styled.button`
  position: absolute;
  top: -40px;
  right: 0;
  width: 32px;
  height: 32px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  font-size: 20px;
  cursor: pointer;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  
  &:hover {
    background: rgba(255, 255, 255, 0.3);
  }
`;

const ImageModalImage = styled.img`
  max-width: 100%;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
`;

