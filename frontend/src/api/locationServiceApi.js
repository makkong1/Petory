import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/location-services';
const ADMIN_BASE_URL = 'http://localhost:8080/api/admin/location-services';

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

const adminApi = axios.create({
  baseURL: ADMIN_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const addAuthToken = (config) => {
  const token = getToken();
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
};

api.interceptors.request.use(addAuthToken, (error) => Promise.reject(error));
adminApi.interceptors.request.use(addAuthToken, (error) => Promise.reject(error));

export const locationServiceApi = {
  searchPlaces: ({ keyword, region, latitude, longitude, radius, size, categoryType } = {}) =>
    api.get('/search', {
      params: {
        ...(keyword && { keyword }),
        ...(region && { region }),
        ...(typeof latitude === 'number' && { latitude }),
        ...(typeof longitude === 'number' && { longitude }),
        ...(typeof radius === 'number' && { radius }),
        ...(typeof size === 'number' && { size }),
        ...(categoryType && { categoryType }),
      },
    }),
  
  // 공공데이터 CSV 파일 업로드 임포트 (관리자용)
  importPublicData: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return adminApi.post('/import-public-data', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
  
  // 카카오맵 데이터 로드 (관리자용)
  loadKakaoData: ({ region, maxResultsPerKeyword, customKeywords } = {}) =>
    adminApi.post('/load-data', null, {
      params: {
        ...(region && { region }),
        ...(maxResultsPerKeyword && { maxResultsPerKeyword }),
        ...(customKeywords && { customKeywords })
      }
    }),
};
