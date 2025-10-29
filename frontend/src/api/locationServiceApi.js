import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/location-services';

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
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const locationServiceApi = {
  // 전체 서비스 조회
  getAllServices: () => api.get(''),
  
  // 특정 서비스 조회
  getServiceById: (id) => api.get(`/${id}`),
  
  // 카테고리별 서비스 조회
  getServicesByCategory: (category) => api.get(`/category/${category}`),
  
  // 지역별 서비스 조회
  getServicesByLocation: (minLat, maxLat, minLng, maxLng) => 
    api.get('/location', { params: { minLat, maxLat, minLng, maxLng } }),
  
  // 키워드로 서비스 검색
  searchServicesByKeyword: (keyword) => api.get('/search', { params: { keyword } }),
  
  // 평점순 서비스 조회
  getTopRatedServices: () => api.get('/top-rated'),
  
  // 특정 평점 이상의 서비스 조회
  getServicesByMinRating: (minRating) => api.get('/rating', { params: { minRating } }),
  
  // 서비스 생성
  createService: (data) => api.post('', data),
  
  // 서비스 수정
  updateService: (id, data) => api.put(`/${id}`, data),
  
  // 서비스 삭제
  deleteService: (id) => api.delete(`/${id}`),
};
