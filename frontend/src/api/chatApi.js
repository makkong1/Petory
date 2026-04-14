import { createAuthAxios } from './apiClient';
import { missingPetApi } from './missingPetApi';

const api = createAuthAxios('http://localhost:8080/api/chat');

// ==================== Conversation API ====================

/**
 * 내 채팅방 목록 조회 (로그인 사용자 = JWT principal)
 */
export const getMyConversations = async () => {
  const response = await api.get('/conversations');
  return response.data;
};

/**
 * 채팅방 상세 조회
 */
export const getConversation = async (conversationIdx) => {
  const response = await api.get(`/conversations/${conversationIdx}`);
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
export const createCareRequestConversation = async (careApplicationIdx) => {
  const response = await api.post('/conversations/care-request', null, {
    params: { careApplicationIdx },
  });
  return response.data;
};

/**
 * 1:1 일반 채팅방 생성 또는 조회 (상대방 user idx만 전달)
 */
export const getOrCreateDirectConversation = async (otherUserId) => {
  const response = await api.post('/conversations/direct', null, {
    params: { otherUserId },
  });
  return response.data;
};

/**
 * 채팅방 나가기
 */
export const leaveConversation = async (conversationIdx) => {
  await api.post(`/conversations/${conversationIdx}/leave`);
};

/**
 * 채팅방 삭제
 */
export const deleteConversation = async (conversationIdx) => {
  await api.delete(`/conversations/${conversationIdx}`);
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
export const confirmCareDeal = async (conversationIdx) => {
  await api.post(`/conversations/${conversationIdx}/confirm-deal`);
};

// ==================== Chat Message API ====================

/**
 * 메시지 전송
 */
export const sendMessage = async (conversationIdx, content, messageType = 'TEXT') => {
  const response = await api.post('/messages', {
    conversationIdx,
    content,
    messageType,
  });
  return response.data;
};

/**
 * 채팅방 메시지 조회 (페이징)
 */
export const getMessages = async (conversationIdx, page = 0, size = 50) => {
  const response = await api.get(`/messages/conversation/${conversationIdx}`, {
    params: { page, size },
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
export const markAsRead = async (conversationIdx, lastMessageIdx = null) => {
  const params = lastMessageIdx != null ? { lastMessageIdx } : {};
  await api.post(`/messages/conversation/${conversationIdx}/read`, null, { params });
};

/**
 * 메시지 삭제
 */
export const deleteMessage = async (messageIdx) => {
  await api.delete(`/messages/${messageIdx}`);
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
export const getUnreadCount = async (conversationIdx) => {
  const response = await api.get(`/messages/conversation/${conversationIdx}/unread-count`);
  return response.data;
};

// ==================== Meetup Chat API ====================

/**
 * 산책모임 채팅방 참여
 */
export const joinMeetupChat = async (meetupIdx) => {
  const response = await api.post(`/conversations/meetup/${meetupIdx}/join`);
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
export const startMissingPetChat = async (boardIdx) => {
  const response = await missingPetApi.startChat(boardIdx);
  return response.data;
};
