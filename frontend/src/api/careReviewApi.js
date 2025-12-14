import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/care-reviews';

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
