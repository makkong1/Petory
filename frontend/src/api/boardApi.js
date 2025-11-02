import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/boards';

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

export const boardApi = {
  // 전체 게시글 조회
  getAllBoards: (params = {}) => api.get('', { params }),
  
  // 단일 게시글 조회
  getBoard: (id) => api.get(`/${id}`),
  
  // 게시글 생성
  createBoard: (data) => api.post('', data),
  
  // 게시글 수정
  updateBoard: (id, data) => api.put(`/${id}`, data),
  
  // 게시글 삭제
  deleteBoard: (id) => api.delete(`/${id}`),
  
  // 내 게시글 조회
  getMyBoards: (userId) => api.get('/my-posts', { params: { userId } }),
  
  // 게시글 검색
  searchBoards: (keyword) => api.get('/search', { params: { keyword } }),
};
