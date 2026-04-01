import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/notifications');

export const notificationApi = {
  // 사용자의 알림 목록 조회
  getUserNotifications: (userId) => api.get('', { params: { userId } }),
  
  // 읽지 않은 알림 목록 조회
  getUnreadNotifications: (userId) => api.get('/unread', { params: { userId } }),
  
  // 읽지 않은 알림 개수 조회
  getUnreadCount: (userId) => api.get('/unread/count', { params: { userId } }),
  
  // 알림 읽음 처리
  markAsRead: (notificationId, userId) => api.put(`/${notificationId}/read`, null, { params: { userId } }),
  
  // 모든 알림 읽음 처리
  markAllAsRead: (userId) => api.put('/read-all', null, { params: { userId } }),
};

