import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/location-service-reviews';

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

export const locationServiceReviewApi = {
  // 리뷰 작성
  createReview: (data) => api.post('', data),

  // 리뷰 수정
  updateReview: (reviewIdx, data) => api.put(`/${reviewIdx}`, data),

  // 리뷰 삭제
  deleteReview: (reviewIdx) => api.delete(`/${reviewIdx}`),

  // 특정 서비스의 리뷰 목록 조회
  getReviewsByService: (serviceIdx) => api.get(`/service/${serviceIdx}`),

  // 특정 사용자의 리뷰 목록 조회
  getReviewsByUser: (userIdx) => api.get(`/user/${userIdx}`),
};
