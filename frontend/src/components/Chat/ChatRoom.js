import React, { useState, useEffect, useRef, useCallback } from 'react';
import styled from 'styled-components';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from '../../contexts/AuthContext';
import { getMessages, sendMessage, markAsRead, getConversation, leaveConversation, deleteConversation, confirmCareDeal } from '../../api/chatApi';
import { careRequestApi } from '../../api/careRequestApi';
import { careReviewApi } from '../../api/careReviewApi';
import { uploadApi } from '../../api/uploadApi';
import { geocodingApi } from '../../api/geocodingApi';

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
  const [dealConfirmed, setDealConfirmed] = useState(false);
  const [confirmingDeal, setConfirmingDeal] = useState(false);
  const [careRequestStatus, setCareRequestStatus] = useState(null);
  const [careRequestData, setCareRequestData] = useState(null);
  const [isRequester, setIsRequester] = useState(false);
  const [isProvider, setIsProvider] = useState(false);
  const [completingCare, setCompletingCare] = useState(false);
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [submittingReview, setSubmittingReview] = useState(false);
  const [hasReview, setHasReview] = useState(false);
  const [gettingLocation, setGettingLocation] = useState(false);
  const [toast, setToast] = useState(null); // { message, type: 'success' | 'error' }
  const toastTimerRef = useRef(null);
  const messagesEndRef = useRef(null);
  const messagesContainerRef = useRef(null);
  const stompClientRef = useRef(null);
  const messageInputRef = useRef(null);
  const fileInputRef = useRef(null);
  const menuRef = useRef(null);

  /** 수신 메시지 읽음: REST 호출을 묶어 서버·네트워크 부하 감소 */
  const READ_DEBOUNCE_MS = 500;
  const readDebounceTimerRef = useRef(null);
  const pendingReadMessageIdxRef = useRef(null);

  const clearReadDebounce = useCallback(() => {
    if (readDebounceTimerRef.current) {
      clearTimeout(readDebounceTimerRef.current);
      readDebounceTimerRef.current = null;
    }
  }, []);

  const flushMarkAsRead = useCallback(
    async (lastMessageIdx) => {
      clearReadDebounce();
      pendingReadMessageIdxRef.current = null;
      if (!conversationIdx) return;
      try {
        await markAsRead(conversationIdx, lastMessageIdx);
      } catch (err) {
        console.error('읽음 처리 실패:', err);
      }
    },
    [conversationIdx, clearReadDebounce]
  );

  const scheduleIncomingMessageRead = useCallback(
    (messageIdx) => {
      if (!conversationIdx || messageIdx == null) return;
      const prev = pendingReadMessageIdxRef.current;
      pendingReadMessageIdxRef.current =
        prev == null ? messageIdx : Math.max(prev, messageIdx);

      clearReadDebounce();
      readDebounceTimerRef.current = setTimeout(() => {
        readDebounceTimerRef.current = null;
        const pending = pendingReadMessageIdxRef.current;
        pendingReadMessageIdxRef.current = null;
        if (pending != null) {
          markAsRead(conversationIdx, pending).catch((err) => {
            console.error('읽음 처리 실패:', err);
          });
        }
      }, READ_DEBOUNCE_MS);
    },
    [conversationIdx, clearReadDebounce]
  );

  const showToast = (message, type = 'error') => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    setToast({ message, type });
    toastTimerRef.current = setTimeout(() => setToast(null), 3500);
  };

  // 메시지 목록 조회
  const fetchMessages = async () => {
    if (!conversationIdx || !user?.idx) return;

    clearReadDebounce();
    pendingReadMessageIdxRef.current = null;

    setLoading(true);
    try {
      const data = await getMessages(conversationIdx, 0, 100);
      const messagesList = data.content || data || [];
      // 백엔드에서 DESC로 정렬되어 최신부터 오므로, reverse()로 오래된 것부터 최신 순서로 변경 (최신이 맨 아래)
      const sortedMessages = [...messagesList].reverse();
      setMessages(sortedMessages);

      // 읽음 처리 (초기 로드 — 디바운스 없이 즉시)
      if (sortedMessages.length > 0) {
        const lastMessage = sortedMessages[sortedMessages.length - 1];
        await flushMarkAsRead(lastMessage.idx);
      }
    } catch (error) {
      console.error('메시지 조회 실패:', error);
      showToast('메시지를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 채팅방 정보 조회
  const fetchConversation = async () => {
    if (!conversationIdx || !user?.idx) return;

    try {
      const data = await getConversation(conversationIdx);
      setConversation(data);
      // 내가 거래 확정했는지 확인
      const myParticipant = data?.participants?.find(p => p.userIdx === user.idx);
      setDealConfirmed(myParticipant?.dealConfirmed || false);

      // 펫케어 요청 상태 조회
      if (data?.relatedType === 'CARE_REQUEST' && data?.relatedIdx) {
        try {
          const careRequest = await careRequestApi.getCareRequest(data.relatedIdx);
          const careRequestInfo = careRequest.data;
          setCareRequestStatus(careRequestInfo?.status || null);
          setCareRequestData(careRequestInfo);

          // 요청자와 제공자 구분
          const requesterId = careRequestInfo?.userId;

          // 승인된 CareApplication에서 제공자 찾기
          const acceptedApplication = careRequestInfo?.applications?.find(
            app => app.status === 'ACCEPTED'
          );
          const providerId = acceptedApplication?.providerId || acceptedApplication?.provider?.idx;

          setIsRequester(user?.idx === requesterId);
          setIsProvider(user?.idx === providerId);

          // 이미 리뷰를 작성했는지 확인
          if (acceptedApplication && user?.idx === requesterId) {
            const hasExistingReview = acceptedApplication.reviews?.some(
              review => review.reviewerId === user.idx
            );
            setHasReview(hasExistingReview || false);
          } else {
            setHasReview(false);
          }
        } catch (error) {
          console.error('펫케어 요청 상태 조회 실패:', error);
        }
      }
    } catch (error) {
      console.error('채팅방 정보 조회 실패:', error);
    }
  };

  useEffect(() => {
    if (conversationIdx && user?.idx) {
      fetchConversation();
      fetchMessages();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
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

              // 읽음 처리 (상대 메시지) — 디바운스로 연속 수신 시 1회에 가깝게 병합
              if (messageData.senderIdx !== user.idx) {
                scheduleIncomingMessageRead(messageData.idx);
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
      clearReadDebounce();
      pendingReadMessageIdxRef.current = null;
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
    };
  }, [conversationIdx, user?.idx, clearReadDebounce, scheduleIncomingMessageRead]);

  // 이미지 업로드 및 전송
  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file || !conversationIdx || !user?.idx || uploadingImage) return;

    // 이미지 파일만 허용
    if (!file.type.startsWith('image/')) {
      showToast('이미지 파일만 업로드할 수 있습니다.');
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

        await flushMarkAsRead(null);
      } else {
        // HTTP API로 폴백
        const newMessage = await sendMessage(conversationIdx, imageUrl, 'IMAGE');
        setMessages(prev => [...prev, newMessage]);
        await flushMarkAsRead(newMessage.idx);
      }
    } catch (error) {
      console.error('이미지 업로드 실패:', error);
      showToast(error.response?.data?.error || '이미지 업로드에 실패했습니다.');
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
        await flushMarkAsRead(null);
      } else {
        // WebSocket이 연결되지 않은 경우 HTTP API로 폴백
        const newMessage = await sendMessage(conversationIdx, content);
        setMessages(prev => [...prev, newMessage]);
        await flushMarkAsRead(newMessage.idx);
      }
    } catch (error) {
      console.error('메시지 전송 실패:', error);
      showToast(error.response?.data?.error || '메시지 전송에 실패했습니다.');
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
      await leaveConversation(conversationIdx);
      showToast('채팅방에서 나갔습니다.', 'success');
      if (onAction) {
        onAction();
      } else if (onClose) {
        onClose();
      }
    } catch (error) {
      console.error('채팅방 나가기 실패:', error);
      showToast('채팅방 나가기에 실패했습니다.');
    }
  };

  // 채팅방 삭제
  const handleDeleteConversation = async () => {
    if (!conversationIdx || !user?.idx) return;

    if (!window.confirm('정말 채팅방을 삭제하시겠습니까? 삭제된 채팅방은 복구할 수 없습니다.')) {
      return;
    }

    try {
      await deleteConversation(conversationIdx);
      showToast('채팅방이 삭제되었습니다.', 'success');
      if (onAction) {
        onAction();
      } else if (onClose) {
        onClose();
      }
    } catch (error) {
      console.error('채팅방 삭제 실패:', error);
      showToast('채팅방 삭제에 실패했습니다.');
    }
  };

  // 거래 확정
  const handleConfirmDeal = async () => {
    if (!conversationIdx || !user?.idx || dealConfirmed) return;

    if (!window.confirm('거래를 확정하시겠습니까? 양쪽 모두 확정하면 펫케어 서비스가 시작됩니다.')) {
      return;
    }

    setConfirmingDeal(true);
    try {
      await confirmCareDeal(conversationIdx);
      setDealConfirmed(true);
      // 채팅방 정보 다시 조회
      await fetchConversation();
      showToast('거래 확정이 완료되었습니다. 상대방도 확정하면 서비스가 시작됩니다.', 'success');
    } catch (error) {
      console.error('거래 확정 실패:', error);
      showToast(error.response?.data?.error || '거래 확정에 실패했습니다.');
    } finally {
      setConfirmingDeal(false);
    }
  };

  // 펫케어 서비스 완료
  const handleCompleteCare = async () => {
    if (!conversation?.relatedIdx || !user?.idx || completingCare) return;

    if (!window.confirm('펫케어 서비스를 완료 처리하시겠습니까?')) {
      return;
    }

    setCompletingCare(true);
    try {
      await careRequestApi.updateStatus(conversation.relatedIdx, 'COMPLETED');
      setCareRequestStatus('COMPLETED');
      // 펫케어 요청 정보 다시 조회
      await fetchConversation();
      showToast('펫케어 서비스가 완료되었습니다.', 'success');
    } catch (error) {
      console.error('서비스 완료 실패:', error);
      showToast(error.response?.data?.error || '서비스 완료 처리에 실패했습니다.');
    } finally {
      setCompletingCare(false);
    }
  };

  // 리뷰 작성 모달 열기
  const handleOpenReviewModal = () => {
    setShowReviewModal(true);
  };

  // 리뷰 작성
  const handleSubmitReview = async () => {
    if (!careRequestData || !user?.idx) {
      showToast('리뷰 작성에 필요한 정보가 없습니다.');
      return;
    }

    // CareApplication 찾기
    const acceptedApplication = careRequestData.applications?.find(
      app => app.status === 'ACCEPTED'
    );

    if (!acceptedApplication) {
      showToast('승인된 펫케어 서비스를 찾을 수 없습니다.');
      return;
    }

    if (!reviewComment.trim()) {
      showToast('리뷰 내용을 입력해주세요.');
      return;
    }

    setSubmittingReview(true);
    try {
      await careReviewApi.createReview({
        careApplicationId: acceptedApplication.idx,
        reviewerId: user.idx,
        revieweeId: acceptedApplication.providerId,
        rating: reviewRating,
        comment: reviewComment.trim()
      });

      showToast('리뷰가 작성되었습니다.', 'success');
      setShowReviewModal(false);
      setReviewRating(5);
      setReviewComment('');
      setHasReview(true);
      // 리뷰 작성 후 리뷰 버튼 숨기기 위해 상태 업데이트
      await fetchConversation();
    } catch (error) {
      console.error('리뷰 작성 실패:', error);
      showToast(error.response?.data?.error || '리뷰 작성에 실패했습니다.');
    } finally {
      setSubmittingReview(false);
    }
  };

  // 내 위치 전송 (주소 텍스트 입력)
  const handleSendLocation = async () => {
    if (!navigator.geolocation) {
      showToast('브라우저가 위치 정보를 지원하지 않습니다.');
      return;
    }

    setGettingLocation(true);
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          const { latitude, longitude } = position.coords;
          // 역지오코딩 API 호출
          const addressData = await geocodingApi.coordinatesToAddress(latitude, longitude);
          
          if (addressData && addressData.address) {
            const locationText = `📍 내 위치: ${addressData.address}`;
            // 기존 입력값이 있으면 줄바꿈 후 추가
            setMessageInput(prev => prev ? `${prev}\n${locationText}` : locationText);
            // 입력창으로 포커스
            messageInputRef.current?.focus();
          } else {
            showToast('주소 정보를 가져오는데 실패했습니다.');
          }
        } catch (err) {
          console.error('위치 변환 실패:', err);
          showToast('위치 정보를 변환하는데 실패했습니다.');
        } finally {
          setGettingLocation(false);
        }
      },
      (error) => {
        console.error('위치 권한 에러:', error);
        showToast('위치 정보를 가져올 수 없습니다. 권한을 확인해주세요.');
        setGettingLocation(false);
      }
    );
  };

  // 펫케어 관련 채팅방인지 확인
  const isCareRequestChat = conversation?.relatedType === 'CARE_REQUEST' ||
    conversation?.relatedType === 'CARE_APPLICATION' ||
    conversation?.conversationType === 'CARE_REQUEST';

  // 양쪽 모두 거래 확정했는지 확인
  const allParticipantsConfirmed = conversation?.participants && conversation.participants.length > 0
    ? conversation.participants.every(p => p.dealConfirmed === true)
    : false;

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
      {toast && (
        <ToastNotification type={toast.type}>
          {toast.message}
        </ToastNotification>
      )}
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
            <ConnectionDot $connected={connected} />
            {connected ? '연결됨' : '연결 중...'}
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

      <MiddleColumn>
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
                      <MessageTime isMyMessage={isMyMessage}>{formatTime(message.createdAt)}</MessageTime>
                    )}
                  </MessageBubble>
                </MessageWrapper>
              );
            })
          )}
          <div ref={messagesEndRef} />
        </MessagesContainer>

        {/* 거래 확정 버튼 (펫케어 채팅방인 경우) */}
        {isCareRequestChat && !allParticipantsConfirmed && (
          <DealConfirmSection>
            {dealConfirmed ? (
              <DealConfirmStatus>
                ✓ 거래 확정 완료 (상대방 확정 대기 중)
              </DealConfirmStatus>
            ) : (
              <DealConfirmButton onClick={handleConfirmDeal} disabled={confirmingDeal}>
                {confirmingDeal ? '확정 중...' : '🤝 거래 확정'}
              </DealConfirmButton>
            )}
          </DealConfirmSection>
        )}

        {allParticipantsConfirmed && isCareRequestChat && (
          <DealConfirmedBanner>
            ✓ 양쪽 모두 거래 확정 완료! 펫케어 서비스가 시작되었습니다.
          </DealConfirmedBanner>
        )}

        {/* 서비스 완료 버튼 (IN_PROGRESS 상태이고 제공자일 때만 표시) */}
        {isCareRequestChat && careRequestStatus === 'IN_PROGRESS' && isProvider && (
          <CompleteCareSection>
            <CompleteCareButton onClick={handleCompleteCare} disabled={completingCare}>
              {completingCare ? '완료 처리 중...' : '✅ 서비스 완료'}
            </CompleteCareButton>
          </CompleteCareSection>
        )}

        {isCareRequestChat && careRequestStatus === 'COMPLETED' && (
          <CompletedBanner>
            ✓ 펫케어 서비스가 완료되었습니다.
          </CompletedBanner>
        )}

        {/* 리뷰 작성 버튼 (COMPLETED 상태이고 요청자이며 아직 리뷰를 작성하지 않았을 때만 표시) */}
        {isCareRequestChat && careRequestStatus === 'COMPLETED' && isRequester && !hasReview && (
          <ReviewSection>
            <ReviewButton onClick={handleOpenReviewModal}>
              ⭐ 리뷰 작성하기
            </ReviewButton>
          </ReviewSection>
        )}

        {/* 리뷰 작성 완료 메시지 */}
        {isCareRequestChat && careRequestStatus === 'COMPLETED' && isRequester && hasReview && (
          <ReviewCompletedBanner>
            ✓ 리뷰를 작성하셨습니다.
          </ReviewCompletedBanner>
        )}
      </MiddleColumn>

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
            <ImageButton
              type="button"
              onClick={handleSendLocation}
              disabled={gettingLocation || sending}
              title="내 위치 전송"
            >
              {gettingLocation ? '📡' : '📍'}
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

      {/* 리뷰 작성 모달 */}
      {showReviewModal && (
        <ReviewModal onClick={() => setShowReviewModal(false)}>
          <ReviewModalContent onClick={(e) => e.stopPropagation()}>
            <ReviewModalHeader>
              <ReviewModalTitle>리뷰 작성</ReviewModalTitle>
              <ReviewModalClose onClick={() => setShowReviewModal(false)}>✕</ReviewModalClose>
            </ReviewModalHeader>
            <ReviewModalBody>
              <ReviewRatingSection>
                <ReviewLabel>평점</ReviewLabel>
                <StarRating>
                  {[1, 2, 3, 4, 5].map((star) => (
                    <StarButton
                      key={star}
                      type="button"
                      onClick={() => setReviewRating(star)}
                      active={star <= reviewRating}
                    >
                      ⭐
                    </StarButton>
                  ))}
                  <RatingText>{reviewRating}점</RatingText>
                </StarRating>
              </ReviewRatingSection>
              <ReviewCommentSection>
                <ReviewLabel>리뷰 내용</ReviewLabel>
                <ReviewTextarea
                  value={reviewComment}
                  onChange={(e) => setReviewComment(e.target.value)}
                  placeholder="서비스에 대한 리뷰를 작성해주세요..."
                  rows={5}
                />
              </ReviewCommentSection>
            </ReviewModalBody>
            <ReviewModalFooter>
              <ReviewCancelButton onClick={() => setShowReviewModal(false)}>
                취소
              </ReviewCancelButton>
              <ReviewSubmitButton onClick={handleSubmitReview} disabled={submittingReview || !reviewComment.trim()}>
                {submittingReview ? '작성 중...' : '리뷰 작성'}
              </ReviewSubmitButton>
            </ReviewModalFooter>
          </ReviewModalContent>
        </ReviewModal>
      )}
    </Container>
  );
};

export default ChatRoom;

const Container = styled.div`
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  min-width: 0;
  position: relative;
  background: ${({ theme }) => theme.colors.background};
`;

const MiddleColumn = styled.div`
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  gap: 12px;
  flex-shrink: 0;
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
  border-radius: ${({ theme }) => theme.borderRadius.full};
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
`;

const HeaderInfo = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
`;

const HeaderTitle = styled.div`
  font-size: ${({ theme }) => theme.typography.h3.fontSize};
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const HeaderSubtitle = styled.div`
  font-size: ${({ theme }) => theme.typography.caption.fontSize};
  color: ${({ theme }) => theme.colors.textSecondary};
  display: flex;
  align-items: center;
  gap: 6px;
`;

const ConnectionDot = styled.span`
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: ${({ theme }) => theme.borderRadius.full};
  background: ${({ $connected, theme }) =>
    $connected ? theme.colors.success : theme.colors.error};
  flex-shrink: 0;
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
  border-radius: ${({ theme }) => theme.borderRadius.full};
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

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
  border-radius: ${({ theme }) => theme.borderRadius.md};
  box-shadow: ${({ theme }) => theme.shadows.md};
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
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  text-align: left;
  cursor: pointer;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

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
  border-radius: ${({ theme }) => theme.borderRadius.full};
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
    color: ${({ theme }) => theme.colors.text};
  }
`;

const MessagesContainer = styled.div`
  flex: 1;
  min-height: 0;
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
  font-size: ${({ theme }) => theme.typography.caption.fontSize};
  color: ${({ theme }) => theme.colors.textMuted};
  padding: 0 8px;
`;

const MessageBubble = styled.div`
  max-width: 70%;
  padding: 12px 16px;
  border-radius: ${({ isMyMessage, theme }) =>
    isMyMessage
      ? `${theme.borderRadius.lg} 0 ${theme.borderRadius.lg} ${theme.borderRadius.lg}`
      : `0 ${theme.borderRadius.lg} ${theme.borderRadius.lg} ${theme.borderRadius.lg}`};
  background: ${({ theme, isMyMessage }) =>
    isMyMessage
      ? theme.colors.primary
      : theme.colors.surfaceSoft};
  color: ${({ theme, isMyMessage }) =>
    isMyMessage
      ? theme.colors.textInverse
      : theme.colors.text};
  word-wrap: break-word;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: ${({ theme }) => theme.shadows.sm};
  border: ${({ theme, isMyMessage }) =>
    isMyMessage ? 'none' : `1px solid ${theme.colors.border}`};
  position: relative;
`;

const MessageContent = styled.div`
  font-size: ${({ theme }) => theme.typography.body1.fontSize};
  line-height: 1.5;
  word-wrap: break-word;
  font-weight: 400;
  letter-spacing: 0.01em;
`;

const MessageImage = styled.img`
  max-width: 100%;
  max-height: 300px;
  border-radius: ${({ theme }) => theme.borderRadius.md};
  object-fit: contain;
  cursor: pointer;

  &:hover {
    opacity: 0.9;
  }
`;

const MessageTime = styled.div`
  font-size: ${({ theme }) => theme.typography.caption.fontSize};
  color: ${({ theme, isMyMessage }) =>
    isMyMessage ? 'rgba(255,255,255,0.7)' : theme.colors.textMuted};
  align-self: flex-end;
`;

const InputContainer = styled.div`
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  padding-bottom: max(
    ${({ theme }) => theme.spacing.md},
    env(safe-area-inset-bottom, 0px)
  );
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  flex-shrink: 0;
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
  border-radius: ${({ theme }) => theme.borderRadius.full};
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};
  flex-shrink: 0;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.surfaceHover};
    transform: scale(1.05);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const MessageInput = styled.input`
  flex: 1;
  padding: 10px 14px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  transition: border-color ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &::placeholder {
    color: ${({ theme }) => theme.colors.textMuted};
  }

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
  border-radius: ${({ theme }) => theme.borderRadius.md};
  background: ${({ theme }) => theme.colors.primary};
  color: ${({ theme }) => theme.colors.textInverse};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primaryDark};
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const LoadingMessage = styled.div`
  padding: 40px 20px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
`;

const EmptyMessage = styled.div`
  padding: 60px 20px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textLight};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
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
  border-radius: ${({ theme }) => theme.borderRadius.full};
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover {
    background: rgba(255, 255, 255, 0.3);
  }
`;

const ImageModalImage = styled.img`
  max-width: 100%;
  max-height: 90vh;
  object-fit: contain;
  border-radius: ${({ theme }) => theme.borderRadius.md};
`;

const DealConfirmSection = styled.div`
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.surface};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  display: flex;
  justify-content: center;
  align-items: center;
`;

const DealConfirmButton = styled.button`
  padding: 10px 20px;
  background: ${({ theme }) => theme.colors.primary};
  color: ${({ theme }) => theme.colors.textInverse};
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primaryDark};
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const DealConfirmStatus = styled.div`
  padding: 10px 20px;
  background: ${({ theme }) => theme.colors.surfaceElevated};
  color: ${({ theme }) => theme.colors.primary};
  border: 1px solid ${({ theme }) => theme.colors.primary};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
`;

const DealConfirmedBanner = styled.div`
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.successSoft};
  color: ${({ theme }) => theme.colors.success};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  text-align: center;
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
`;

const CompleteCareSection = styled.div`
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.surface};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  display: flex;
  justify-content: center;
  align-items: center;
`;

const CompleteCareButton = styled.button`
  padding: 10px 20px;
  background: ${({ theme }) => theme.colors.success};
  color: ${({ theme }) => theme.colors.textInverse};
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.successDark};
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const CompletedBanner = styled.div`
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.successSoft};
  color: ${({ theme }) => theme.colors.success};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  text-align: center;
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
`;

const ReviewCompletedBanner = styled.div`
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.infoSoft};
  color: ${({ theme }) => theme.colors.info};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  text-align: center;
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
`;

const ReviewSection = styled.div`
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.surface};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  display: flex;
  justify-content: center;
  align-items: center;
`;

const ReviewButton = styled.button`
  padding: 10px 20px;
  background: ${({ theme }) => theme.colors.warning};
  color: ${({ theme }) => theme.colors.textInverse};
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.warningDark};
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const ReviewModal = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: ${({ theme }) => theme.colors.overlay};
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
`;

const ReviewModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: ${({ theme }) => theme.shadows.xl};
`;

const ReviewModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const ReviewModalTitle = styled.h2`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h3.fontSize};
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const ReviewModalClose = styled.button`
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
  border-radius: ${({ theme }) => theme.borderRadius.full};
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
`;

const ReviewModalBody = styled.div`
  padding: 20px;
`;

const ReviewRatingSection = styled.div`
  margin-bottom: 20px;
`;

const ReviewCommentSection = styled.div`
  margin-bottom: 20px;
`;

const ReviewLabel = styled.label`
  display: block;
  margin-bottom: 8px;
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const StarRating = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const StarButton = styled.button`
  background: transparent;
  border: none;
  font-size: 28px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
  filter: ${({ active }) => active ? 'none' : 'grayscale(100%) opacity(0.3)'};
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover {
    transform: scale(1.1);
  }
`;

const RatingText = styled.span`
  margin-left: 8px;
  font-size: ${({ theme }) => theme.typography.h3.fontSize};
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const ReviewTextarea = styled.textarea`
  width: 100%;
  padding: 12px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-family: inherit;
  resize: vertical;
  color: ${({ theme }) => theme.colors.text};
  background: ${({ theme }) => theme.colors.background};
  transition: border-color ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
  }
`;

const ReviewModalFooter = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px;
  border-top: 1px solid ${({ theme }) => theme.colors.border};
`;

const ReviewCancelButton = styled.button`
  padding: 10px 20px;
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
`;

const ReviewSubmitButton = styled.button`
  padding: 10px 20px;
  background: ${({ theme }) => theme.colors.primary};
  color: ${({ theme }) => theme.colors.textInverse};
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all ${({ theme }) => theme.duration?.normal || '200ms'} ${({ theme }) => theme.easing?.easeOut || 'ease'};

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primaryDark};
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;


const ToastNotification = styled.div`
  position: absolute;
  top: calc(56px + 8px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  padding: 10px 20px;
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 500;
  white-space: nowrap;
  box-shadow: ${({ theme }) => theme.shadows.md};
  background: ${({ theme, type }) =>
    type === 'success' ? theme.colors.success : theme.colors.error};
  color: ${({ theme }) => theme.colors.textInverse};
  animation: fadeInDown ${({ theme }) => theme.duration?.normal || '200ms'} ease;

  @keyframes fadeInDown {
    from { opacity: 0; transform: translateX(-50%) translateY(-8px); }
    to   { opacity: 1; transform: translateX(-50%) translateY(0); }
  }
`;
