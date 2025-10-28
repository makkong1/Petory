import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/care-requests';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const careRequestApi = {
  // 전체 케어 요청 조회
  getAllCareRequests: (params = {}) => api.get('', { params }),
  
  // 단일 케어 요청 조회
  getCareRequest: (id) => api.get(`/${id}`),
  
  // 케어 요청 생성
  createCareRequest: (data) => api.post('', data),
  
  // 케어 요청 수정
  updateCareRequest: (id, data) => api.put(`/${id}`, data),
  
  // 케어 요청 삭제
  deleteCareRequest: (id) => api.delete(`/${id}`),
  
  // 내 케어 요청 조회
  getMyCareRequests: (userId) => api.get('/my-requests', { params: { userId } }),
  
  // 상태 변경
  updateStatus: (id, status) => api.patch(`/${id}/status`, null, { params: { status } }),
};
