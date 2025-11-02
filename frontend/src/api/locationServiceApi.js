import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/location-services';

// Access Token 가져오기 (전역 인터셉터에서 처리되지만 호환성을 위해)
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
  
  // 키워드로 서비스 검색 (이름 또는 설명)
  searchServicesByKeyword: (keyword) => api.get('/search', { params: { keyword } }),
  
  // 지역(주소)으로 서비스 검색
  searchServicesByAddress: (address) => api.get('/search/address', { params: { address } }),
  
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
