import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/boards';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

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
