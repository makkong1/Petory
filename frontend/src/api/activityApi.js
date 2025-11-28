import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/activities';

// 토큰을 가져오는 함수
const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

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

export const activityApi = {
  // 내 활동 조회 (기존 API - 하위 호환성 유지)
  getMyActivities: (userId) => api.get('/my', { params: { userId } }),
  
  // 내 활동 조회 (페이징 지원)
  getMyActivitiesWithPaging: (params = {}) => {
    const { userId, filter = 'ALL', page = 0, size = 20, ...otherParams } = params;
    const requestParams = {
      userId,
      filter,
      page,
      size,
      ...otherParams,
      _t: Date.now()
    };
    return api.get('/my/paging', {
      params: requestParams,
      headers: { 'Cache-Control': 'no-cache' }
    });
  },
};

