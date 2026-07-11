import { createAuthAxios } from './apiClient';
import { isDemoMode } from '../mock/isDemoMode';

const api = createAuthAxios('http://localhost:8080/api/location-service-reviews');

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

  // 특정 서비스의 리뷰 목록 조회 (서버 페이징: page/size)
  getReviewsByService: (serviceIdx, page = 0, size = 20) =>
    isDemoMode()
      ? mockResolve({ reviews: [], count: 0, averageRating: null, hasNext: false })
      : api.get(`/service/${serviceIdx}`, { params: { page, size } }),

  // 특정 사용자의 리뷰 목록 조회
  getReviewsByUser: (userIdx) =>
    isDemoMode() ? mockResolve({ reviews: [] }) : api.get(`/user/${userIdx}`),
};
