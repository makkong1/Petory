import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/users';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

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
