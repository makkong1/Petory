import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/admin';

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

export const adminApi = {
  // 초기 데이터 로딩
  loadInitialData: async (region = '서울특별시', maxResultsPerKeyword = 10, customKeywords = null) => {
    let url = `/location-services/load-data?region=${encodeURIComponent(region)}&maxResultsPerKeyword=${maxResultsPerKeyword}`;
    if (customKeywords) {
      url += `&customKeywords=${encodeURIComponent(customKeywords)}`;
    }
    const response = await api.post(url);
    return response.data;
  },

  // 일별 통계 조회
  fetchDailyStatistics: async (startDate, endDate) => {
    const params = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    
    const response = await api.get('/statistics/daily', { params });
    return response.data;
  },
};

