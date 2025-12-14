import axios from 'axios';
import { missingPetApi } from './missingPetApi';

const BASE_URL = 'http://localhost:8080/api/chat';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 토큰 가져오기
const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

// 요청 인터셉터 - 모든 요청에 토큰 자동 추가
api.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 인증 실패 시 로그인 페이지로 리다이렉트
      if (window.redirectToLogin) {
        window.redirectToLogin();
      }
    }
    return Promise.reject(error);
  }
);

// ==================== Conversation API ====================

/**
 * 내 채팅방 목록 조회
 */
export const getMyConversations = async (userId) => {
  const response = await api.get('/conversations', {
    params: { userId },
  });
  return response.data;
};

/**
 * 채팅방 상세 조회
 */
export const getConversation = async (conversationIdx, userId) => {
  const response = await api.get(`/conversations/${conversationIdx}`, {
    params: { userId },
  });
  return response.data;
};

/**
 * 채팅방 생성
 */
export const createConversation = async (conversationData) => {
  const response = await api.post('/conversations', conversationData);
  return response.data;
};

/**
 * 펫케어 요청 채팅방 생성
 */
export const createCareRequestConversation = async (careApplicationIdx, requesterId, providerId) => {
  const response = await api.post('/conversations/care-request', null, {
    params: { careApplicationIdx, requesterId, providerId },
  });
  return response.data;
};

/**
 * 1:1 일반 채팅방 생성 또는 조회
 */
export const getOrCreateDirectConversation = async (user1Id, user2Id) => {
  const response = await api.post('/conversations/direct', null, {
    params: { user1Id, user2Id },
  });
  return response.data;
};

/**
 * 채팅방 나가기
 */
export const leaveConversation = async (conversationIdx, userId) => {
  await api.post(`/conversations/${conversationIdx}/leave`, null, {
    params: { userId },
  });
};

/**
 * 채팅방 삭제
 */
export const deleteConversation = async (conversationIdx, userId) => {
  await api.delete(`/conversations/${conversationIdx}`, {
    params: { userId },
  });
};

/**
 * 채팅방 상태 변경
 */
export const updateConversationStatus = async (conversationIdx, status) => {
  const response = await api.patch(`/conversations/${conversationIdx}/status`, null, {
    params: { status },
  });
  return response.data;
};

/**
 * 펫케어 거래 확정
 */
export const confirmCareDeal = async (conversationIdx, userId) => {
  await api.post(`/conversations/${conversationIdx}/confirm-deal`, null, {
    params: { userId },
  });
};

// ==================== Chat Message API ====================

/**
 * 메시지 전송
 */
export const sendMessage = async (conversationIdx, senderIdx, content, messageType = 'TEXT') => {
  const response = await api.post(
    '/messages',
    {
      conversationIdx,
      content,
      messageType,
    },
    {
      params: { senderIdx },
    }
  );
  return response.data;
};

/**
 * 채팅방 메시지 조회 (페이징)
 * 재참여한 경우 joinedAt 이후 메시지만 조회
 */
export const getMessages = async (conversationIdx, userId, page = 0, size = 50) => {
  const response = await api.get(`/messages/conversation/${conversationIdx}`, {
    params: { userId, page, size },
  });
  return response.data;
};

/**
 * 채팅방 메시지 조회 (커서 기반 페이징)
 */
export const getMessagesBefore = async (conversationIdx, beforeDate, size = 50) => {
  const response = await api.get(`/messages/conversation/${conversationIdx}/before`, {
    params: { beforeDate, size },
  });
  return response.data;
};

/**
 * 메시지 읽음 처리
 */
export const markAsRead = async (conversationIdx, userId, lastMessageIdx = null) => {
  await api.post(`/messages/conversation/${conversationIdx}/read`, null, {
    params: { userId, lastMessageIdx },
  });
};

/**
 * 메시지 삭제
 */
export const deleteMessage = async (messageIdx, userId) => {
  await api.delete(`/messages/${messageIdx}`, {
    params: { userId },
  });
};

/**
 * 메시지 검색
 */
export const searchMessages = async (conversationIdx, keyword) => {
  const response = await api.get(`/messages/conversation/${conversationIdx}/search`, {
    params: { keyword },
  });
  return response.data;
};

/**
 * 읽지 않은 메시지 수 조회
 */
export const getUnreadCount = async (conversationIdx, userId) => {
  const response = await api.get(`/messages/conversation/${conversationIdx}/unread-count`, {
    params: { userId },
  });
  return response.data;
};

// ==================== Meetup Chat API ====================

/**
 * 산책모임 채팅방 참여
 */
export const joinMeetupChat = async (meetupIdx, userId) => {
  const response = await api.post(`/conversations/meetup/${meetupIdx}/join`, null, {
    params: { userId },
  });
  return response.data;
};

/**
 * 산책모임 채팅방 참여 인원 수 조회
 */
export const getMeetupChatParticipantCount = async (meetupIdx) => {
  const response = await api.get(`/conversations/meetup/${meetupIdx}/participant-count`);
  return response.data;
};

// ==================== Missing Pet Chat API ====================

/**
 * 실종제보 채팅 시작 ("목격했어요" 버튼 클릭)
 */
export const startMissingPetChat = async (boardIdx, witnessId) => {
  const response = await missingPetApi.startChat(boardIdx, witnessId);
  return response.data;
};

