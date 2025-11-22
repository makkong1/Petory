import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/notifications';

// 토큰을 가져오는 함수 (전역 인터셉터와 동일한 방식)
const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 - 모든 요청에 토큰 자동 추가 (전역 인터셉터와 중복되지만 안전을 위해 유지)
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

// 응답 인터셉터 제거 - 전역 인터셉터가 처리 (setupApiInterceptors)

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

