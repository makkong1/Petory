import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/care-requests';

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

export const careRequestApi = {
  // 전체 케어 요청 조회 (페이징 지원)
  getAllCareRequests: (params = {}) => {
    const { page = 0, size = 20, ...rest } = params;
    return api.get('', { params: { page, size, ...rest } });
  },
  
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

  // 댓글 관련
  getComments: (careRequestId) => api.get(`/${careRequestId}/comments`),
  createComment: (careRequestId, payload) => api.post(`/${careRequestId}/comments`, payload),
  deleteComment: (careRequestId, commentId) => api.delete(`/${careRequestId}/comments/${commentId}`),

  // 검색 (페이징 지원)
  searchCareRequests: (keyword, page = 0, size = 20) =>
    api.get('/search', { params: { keyword, page, size } }),
};
