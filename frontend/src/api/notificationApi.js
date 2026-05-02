import { API_ROOT, createAuthAxios } from './apiClient';

const api = createAuthAxios(`${API_ROOT}/notifications`);

export const notificationApi = {
  // 사용자의 알림 목록 조회 (PK는 JWT에서만 해석)
  getUserNotifications: () => api.get(''),

  // 읽지 않은 알림 목록 조회
  getUnreadNotifications: () => api.get('/unread'),

  // 읽지 않은 알림 개수 조회
  getUnreadCount: () => api.get('/unread/count'),

  // 알림 읽음 처리
  markAsRead: (notificationId) => api.put(`/${notificationId}/read`),

  // 모든 알림 읽음 처리
  markAllAsRead: () => api.put('/read-all'),
};
