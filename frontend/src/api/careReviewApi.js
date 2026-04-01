import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/care-reviews');

export const careReviewApi = {
  // 리뷰 작성
  createReview: (data) => api.post('', data),

  // 특정 사용자(reviewee)에 대한 리뷰 목록 조회
  getReviewsByReviewee: (revieweeIdx) => api.get(`/reviewee/${revieweeIdx}`),

  // 특정 사용자(reviewer)가 작성한 리뷰 목록 조회
  getReviewsByReviewer: (reviewerIdx) => api.get(`/reviewer/${reviewerIdx}`),

  // 특정 사용자의 평균 평점 조회
  getAverageRating: (revieweeIdx) => api.get(`/average-rating/${revieweeIdx}`),
};
