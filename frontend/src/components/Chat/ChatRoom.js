import React, { useState, useEffect, useRef, useCallback } from 'react';
import styled from 'styled-components';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from '../../contexts/AuthContext';
import { getMessages, sendMessage, markAsRead, getConversation, leaveConversation, deleteConversation } from '../../api/chatApi';
import { careOfferApi } from '../../api/careOfferApi';
import { careRequestApi } from '../../api/careRequestApi';
import { careReviewApi } from '../../api/careReviewApi';
import { uploadApi } from '../../api/uploadApi';
import { geocodingApi } from '../../api/geocodingApi';
import ChatSearchPanel from './ChatSearchPanel';

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
  const [showSearch, setShowSearch] = useState(false);
  // 살아 있는 케어 제안(상대와 나 사이). 채팅방은 계약을 모르므로 방이 아니라 상대로 찾는다.
  // 배열인 이유: 같은 두 사람 사이에 계약이 여러 건 살아 있을 수 있다. UNIQUE 는 (요청, 제공자)
  // 조합이라 요청이 다르면 같은 사람에게 또 맡길 수 있고, 방은 두 사람당 하나뿐이다.
  const [offers, setOffers] = useState([]);
  const [processingOfferIdx, setProcessingOfferIdx] = useState(null);
  const [myOpenRequests, setMyOpenRequests] = useState([]);
  const [reviewTarget, setReviewTarget] = useState(null);
  const [completingCareIdx, setCompletingCareIdx] = useState(null);
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [submittingReview, setSubmittingReview] = useState(false);
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
      // 계약은 채팅방이 아니라 care 에 있다. 방 번호가 아니라 "상대가 누구인가"로 찾는다.
      // 예전엔 방의 relatedType/relatedIdx 로 케어 요청을 찾았는데, 지도에서 만들어지는 방은
      // DIRECT(relatedType=null)라 이 블록이 한 번도 실행되지 않았다 — 확정·완료·리뷰 UI가
      // 통째로 죽어 있던 이유다.
      const other = data?.participants?.find(p => p.userIdx !== user.idx);
      if (!other?.userIdx) {
        setOffers([]);
        setMyOpenRequests([]);
        return;
      }

      try {
        const { data: list } = await careOfferApi.between(other.userIdx);
        const live = Array.isArray(list) ? list : [];
        setOffers(live);

        // 내 모집 중 요청 — 아직 이 사람에게 안 보낸 것이 있어야 "맡기기"를 띄운다.
        try {
          const { data: mine } = await careRequestApi.getMyCareRequests();
          const mineList = Array.isArray(mine) ? mine : (mine?.careRequests || []);
          setMyOpenRequests(mineList.filter(r => r.status === 'OPEN'));
        } catch (e) {
          setMyOpenRequests([]);
        }

      } catch (error) {
        console.error('케어 제안 조회 실패:', error);
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

  // 요청자가 이 사람에게 케어를 제안한다. 아직 안 보낸 모집 중 요청이 여러 개면 하나를 고른다.
  const handleOffer = async () => {
    const otherUserIdx = getOtherParticipant()?.userIdx;
    if (!otherUserIdx || offerableRequests.length === 0 || processingOfferIdx) return;

    let target = offerableRequests[0];
    if (offerableRequests.length > 1) {
      const list = offerableRequests.map((r, i) => `${i + 1}. ${r.title}`).join('\n');
      const picked = window.prompt(`어느 케어를 맡기시겠습니까?\n${list}\n\n번호 입력:`, '1');
      const n = Number(picked);
      if (!n || n < 1 || n > offerableRequests.length) return;
      target = offerableRequests[n - 1];
    }

    const amount = target.offeredCoins != null ? `${target.offeredCoins.toLocaleString()} 코인으로 ` : '';
    if (!window.confirm(`"${target.title}"을(를) ${amount}이 분께 맡기시겠습니까?\n제공자가 수락하면 케어가 시작됩니다.`)) {
      return;
    }

    setProcessingOfferIdx('new');
    try {
      await careOfferApi.offer(target.idx, otherUserIdx);
      await fetchConversation();
      showToast('제안을 보냈습니다. 제공자가 수락하면 케어가 시작됩니다.', 'success');
    } catch (error) {
      console.error('케어 제안 실패:', error);
      showToast(error.response?.data?.error || error.response?.data?.message || '제안에 실패했습니다.');
    } finally {
      setProcessingOfferIdx(null);
    }
  };

  // 제공자가 제안을 수락한다. 여기서 계약이 성립하고 에스크로 지급 대상이 배정된다.
  const handleAcceptOffer = async (offer) => {
    if (!offer?.idx || processingOfferIdx) return;

    const amount = offer.offeredCoins != null ? `${offer.offeredCoins.toLocaleString()} 코인으로 ` : '';
    if (!window.confirm(`"${offer.careRequestTitle}" 케어를 ${amount}맡으시겠습니까?\n수락하면 바로 케어가 시작됩니다.`)) {
      return;
    }

    setProcessingOfferIdx(offer.idx);
    try {
      await careOfferApi.accept(offer.idx);
      await fetchConversation();
      showToast('제안을 수락했습니다. 케어가 시작되었습니다.', 'success');
    } catch (error) {
      console.error('제안 수락 실패:', error);
      // 409 = 제안 이후 금액이 바뀌었거나 이미 다른 제공자와 확정된 경우. 최신 상태를 다시 받는다.
      if (error.response?.status === 409) {
        await fetchConversation();
      }
      showToast(error.response?.data?.error || error.response?.data?.message || '수락에 실패했습니다.');
    } finally {
      setProcessingOfferIdx(null);
    }
  };

  const handleRejectOffer = async (offer) => {
    if (!offer?.idx || processingOfferIdx) return;
    if (!window.confirm(`"${offer.careRequestTitle}" 제안을 거절하시겠습니까?`)) return;

    setProcessingOfferIdx(offer.idx);
    try {
      await careOfferApi.reject(offer.idx);
      await fetchConversation();
      showToast('제안을 거절했습니다.', 'success');
    } catch (error) {
      console.error('제안 거절 실패:', error);
      showToast(error.response?.data?.error || error.response?.data?.message || '거절에 실패했습니다.');
    } finally {
      setProcessingOfferIdx(null);
    }
  };

  // 펫케어 서비스 이행 완료 확인
  // 요청자·제공자가 각자 눌러야 하고, 양쪽이 다 확인해야 COMPLETED 가 되며 코인이 정산된다.
  const handleCompleteCare = async (offer) => {
    if (!offer?.careRequestId || !user?.idx || completingCareIdx) return;

    // 요청자의 클릭은 그 순간 지급이다. 마지막 클릭의 무게에 맞게 금액과 상대를 박는다.
    const iAmProvider = offer.providerId === user?.idx;
    const amount = (offer.offeredCoins ?? 0).toLocaleString();
    const message = iAmProvider
      ? `"${offer.careRequestTitle}" 이행을 마쳤다고 알릴까요?\n요청자가 확인하면 ${amount} 코인을 받습니다.`
      : `"${offer.careRequestTitle}" 이행을 확인하시겠습니까?\n\n${offer.providerName || '제공자'}님에게 ${amount} 코인이 지금 지급됩니다.\n이 작업은 되돌릴 수 없습니다.`;
    if (!window.confirm(message)) {
      return;
    }

    setCompletingCareIdx(offer.idx);
    try {
      const response = await careRequestApi.confirmCompletion(offer.careRequestId);
      const updated = response?.data;
      await fetchConversation();
      showToast(
        updated?.status === 'COMPLETED'
          ? '양쪽 확인이 끝나 펫케어 서비스가 완료되었습니다.'
          : '완료 확인했습니다. 상대방 확인을 기다리는 중입니다.',
        'success'
      );
    } catch (error) {
      console.error('완료 확인 실패:', error);
      showToast(error.response?.data?.error || '완료 확인에 실패했습니다.');
    } finally {
      setCompletingCareIdx(null);
    }
  };

  // 제공자가 이행 완료 알림을 되돌린다 (실수 클릭 복구)
  const handleCancelCompletion = async (offer) => {
    if (!offer?.careRequestId || completingCareIdx) return;
    if (!window.confirm('이행 완료 알림을 취소하시겠습니까?')) return;

    setCompletingCareIdx(offer.idx);
    try {
      await careRequestApi.cancelCompletion(offer.careRequestId);
      await fetchConversation();
      showToast('이행 완료 알림을 취소했습니다.', 'success');
    } catch (error) {
      console.error('확인 취소 실패:', error);
      showToast(error.response?.data?.error || '취소에 실패했습니다.');
    } finally {
      setCompletingCareIdx(null);
    }
  };

  // 리뷰 작성 모달 열기 — 어느 계약에 대한 리뷰인지 함께 들고 있는다.
  const handleOpenReviewModal = (offer) => {
    setReviewTarget(offer);
    setShowReviewModal(true);
  };

  // 리뷰 작성
  const handleSubmitReview = async () => {
    if (!reviewTarget || !user?.idx) {
      showToast('리뷰 작성에 필요한 정보가 없습니다.');
      return;
    }

    if (!reviewComment.trim()) {
      showToast('리뷰 내용을 입력해주세요.');
      return;
    }

    setSubmittingReview(true);
    try {
      await careReviewApi.createReview({
        careApplicationId: reviewTarget.idx,
        reviewerId: user.idx,
        revieweeId: reviewTarget.providerId,
        rating: reviewRating,
        comment: reviewComment.trim()
      });

      showToast('리뷰가 작성되었습니다.', 'success');
      setShowReviewModal(false);
      setReviewTarget(null);
      setReviewRating(5);
      setReviewComment('');
      await fetchConversation();
    } catch (error) {
      console.error('리뷰 작성 실패:', error);
      const msg = error.response?.data?.error || error.response?.data?.message || '';
      const status = error.response?.status;
      if (status === 409 || (typeof msg === 'string' && msg.includes('이미 해당 서비스에 리뷰'))) {
        // 이미 썼다면 다시 받아오면 카드가 사라진다(조회가 리뷰 남은 것만 돌려준다).
        setShowReviewModal(false);
        await fetchConversation();
        showToast(typeof msg === 'string' ? msg : '이미 해당 서비스에 리뷰를 작성하셨습니다.');
      } else {
        showToast(msg || '리뷰 작성에 실패했습니다.');
      }
    } finally {
      setSubmittingReview(false);
    }
  };

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

  // 계약 UI 를 띄울지는 방의 종류가 아니라 "이 상대와 살아 있는 계약이 있는가"로 정한다.
  // 방 타입으로 판정하던 예전 코드는 실제로 만들어지는 방과 어긋나 한 번도 참이 되지 않았다.
  // 이미 제안을 보낸 요청은 후보에서 뺀다 — 같은 요청을 또 보내는 건 의미가 없다.
  const offeredRequestIds = new Set(offers.map(o => o.careRequestId));
  const offerableRequests = myOpenRequests.filter(r => !offeredRequestIds.has(r.idx));
  const canOffer = offerableRequests.length > 0
    && conversation?.conversationType === 'DIRECT';

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
          <MenuButton onClick={() => setShowSearch(true)} aria-label="메시지 검색">🔍</MenuButton>
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

      {showSearch && (
        <ChatSearchPanel
          conversationIdx={conversationIdx}
          onClose={() => setShowSearch(false)}
        />
      )}

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

        {/* 케어 제안 — 요청자가 이 사람에게 맡기겠다고 보낸다 */}
        {canOffer && (
          <DealConfirmSection>
            <DealConfirmButton onClick={handleOffer} disabled={Boolean(processingOfferIdx)}>
              {processingOfferIdx === 'new' ? '보내는 중...' : '🤝 이 분께 케어 맡기기'}
            </DealConfirmButton>
          </DealConfirmSection>
        )}

        {/* 살아 있는 계약마다 카드 하나. 한 방에 여러 건이 매달릴 수 있어 목록으로 그린다 */}
        {offers.map((o) => {
          const iAmProvider = o.providerId === user?.idx;
          const busy = processingOfferIdx === o.idx;

          return (
            <OfferCard key={o.idx}>
              <OfferTitle>
                {o.careRequestTitle}
                {o.offeredCoins != null && ` · ${o.offeredCoins.toLocaleString()} 코인`}
              </OfferTitle>

              {o.status === 'PENDING' && iAmProvider && (
                <OfferActions>
                  <DealConfirmButton onClick={() => handleAcceptOffer(o)} disabled={busy}>
                    {busy ? '처리 중...' : '✅ 이 케어 맡기'}
                  </DealConfirmButton>
                  <RejectOfferButton onClick={() => handleRejectOffer(o)} disabled={busy}>
                    거절
                  </RejectOfferButton>
                </OfferActions>
              )}

              {o.status === 'PENDING' && !iAmProvider && (
                <DealConfirmStatus>✓ 제안을 보냈습니다 (제공자 수락 대기 중)</DealConfirmStatus>
              )}

              {/* 순서를 강제한다 — 제공자가 이행을 알리고, 요청자가 승인한다.
                  아무나 먼저 누를 수 있으면 실수 클릭 두 번으로 이행 없이 정산된다. */}
              {o.status === 'ACCEPTED' && o.careRequestStatus === 'IN_PROGRESS' && iAmProvider && (
                <OfferActions>
                  {o.providerCompletedAt ? (
                    <>
                      <DealConfirmStatus>⏳ 요청자 확인 대기 중</DealConfirmStatus>
                      <RejectOfferButton
                        onClick={() => handleCancelCompletion(o)}
                        disabled={completingCareIdx === o.idx}
                      >
                        취소
                      </RejectOfferButton>
                    </>
                  ) : (
                    <CompleteCareButton
                      onClick={() => handleCompleteCare(o)}
                      disabled={completingCareIdx === o.idx}
                    >
                      {completingCareIdx === o.idx ? '알리는 중...' : '✅ 이행 완료 알리기'}
                    </CompleteCareButton>
                  )}
                </OfferActions>
              )}

              {o.status === 'ACCEPTED' && o.careRequestStatus === 'IN_PROGRESS' && !iAmProvider && (
                <OfferActions>
                  {o.providerCompletedAt ? (
                    <CompleteCareButton
                      onClick={() => handleCompleteCare(o)}
                      disabled={completingCareIdx === o.idx}
                    >
                      {completingCareIdx === o.idx
                        ? '확인 중...'
                        : `✅ 이행 확인하고 ${(o.offeredCoins ?? 0).toLocaleString()} 코인 지급`}
                    </CompleteCareButton>
                  ) : (
                    <DealConfirmStatus>제공자의 이행 완료를 기다리는 중입니다</DealConfirmStatus>
                  )}
                </OfferActions>
              )}

              {/* 완료 카드는 리뷰가 남았을 때만 내려온다 — 쓰고 나면 카드째 사라진다 */}
              {o.careRequestStatus === 'COMPLETED' && (
                <>
                  <CompletedBanner>✓ 펫케어 서비스가 완료되었습니다.</CompletedBanner>
                  <OfferActions>
                    <ReviewButton onClick={() => handleOpenReviewModal(o)}>
                      ⭐ 리뷰 작성하기
                    </ReviewButton>
                  </OfferActions>
                </>
              )}
            </OfferCard>
          );
        })}
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
  gap: 8px;
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

const OfferCard = styled.div`
  padding: 12px 16px;
  background: ${({ theme }) => theme.colors.surface};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const OfferTitle = styled.div`
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const OfferActions = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
`;

const RejectOfferButton = styled.button`
  padding: 10px 16px;
  background: transparent;
  color: ${({ theme }) => theme.colors.textSecondary};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  cursor: pointer;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.surfaceElevated};
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
`;

const DealConfirmStatus = styled.div`
  margin: 12px 16px;
  text-align: center;
  padding: 10px 20px;
  background: ${({ theme }) => theme.colors.surfaceElevated};
  color: ${({ theme }) => theme.colors.primary};
  border: 1px solid ${({ theme }) => theme.colors.primary};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: 600;
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
