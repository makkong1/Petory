import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/care-requests';

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

// 응답 인터셉터 - 401 에러 시 토큰 제거 및 로그인 페이지로 리다이렉트
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      // 로그인 페이지로 리다이렉트 (필요시)
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

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
