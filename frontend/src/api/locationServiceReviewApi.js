import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';

const BASE_URL = 'http://localhost:8080/api/location-service-reviews';

const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

const mockResolve = (data) => Promise.resolve({ data });

export const locationServiceReviewApi = {
  // 리뷰 작성
  createReview: (data) =>
    isDemoMode() ? mockResolve({ idx: 1, ...data }) : api.post('', data),

  // 리뷰 수정
  updateReview: (reviewIdx, data) =>
    isDemoMode() ? mockResolve({ idx: reviewIdx, ...data }) : api.put(`/${reviewIdx}`, data),

  // 리뷰 삭제
  deleteReview: (reviewIdx) =>
    isDemoMode() ? mockResolve({}) : api.delete(`/${reviewIdx}`),

  // 특정 서비스의 리뷰 목록 조회
  getReviewsByService: (serviceIdx) =>
    isDemoMode() ? mockResolve({ reviews: [] }) : api.get(`/service/${serviceIdx}`),

  // 특정 사용자의 리뷰 목록 조회
  getReviewsByUser: (userIdx) =>
    isDemoMode() ? mockResolve({ reviews: [] }) : api.get(`/user/${userIdx}`),
};
