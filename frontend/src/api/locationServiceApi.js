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
};
