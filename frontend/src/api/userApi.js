import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/users';

// 토큰을 가져오는 함수
const getToken = () => {
  return localStorage.getItem('token');
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
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

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
