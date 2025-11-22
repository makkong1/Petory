import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/admin/users';

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
// 401 에러는 전역 인터셉터에서 refresh token으로 자동 처리됨

export const userApi = {
  // 전체 유저 조회
  getAllUsers: () => api.get(''),
  
  // 단일 유저 조회
  getUser: (id) => api.get(`/${id}`),
  
  // 유저 생성
  createUser: (userData) => api.post('', userData),
  
  // 유저 수정
  updateUser: (id, userData) => api.put(`/${id}`, userData),
  
  // 유저 삭제
  deleteUser: (id) => api.delete(`/${id}`),
};

// MASTER 전용: ADMIN 계정 관리 API
const masterApi = axios.create({
  baseURL: 'http://localhost:8080/api/master/admin-users',
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 - 모든 요청에 토큰 자동 추가
masterApi.interceptors.request.use(
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

export const adminUserApi = {
  // 일반 사용자를 ADMIN으로 승격
  promoteToAdmin: (id) => masterApi.patch(`/${id}/promote-to-admin`),
};