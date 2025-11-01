import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api';

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

export const geocodingApi = {
  // 주소를 위도/경도로 변환
  addressToCoordinates: async (address) => {
    const response = await api.get('/geocoding/address', { params: { address } });
    return response.data;
  },
};

