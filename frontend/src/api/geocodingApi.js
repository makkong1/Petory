import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';

const BASE_URL = 'http://localhost:8080/api';

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

const DEMO_LAT = 37.5665;
const DEMO_LNG = 126.978;
const DEMO_ADDRESS = '서울특별시 중구 세종대로 110';

export const geocodingApi = {
  // 주소를 위도/경도로 변환
  addressToCoordinates: async (address) => {
    if (isDemoMode()) {
      return {
        success: true,
        latitude: DEMO_LAT,
        longitude: DEMO_LNG,
        address: address || DEMO_ADDRESS,
      };
    }
    const response = await api.get('/geocoding/address', { params: { address } });
    return response.data;
  },

  // 위도/경도를 주소로 변환 (역지오코딩)
  coordinatesToAddress: async (lat, lng) => {
    if (isDemoMode()) {
      return { success: true, address: DEMO_ADDRESS, roadAddress: DEMO_ADDRESS };
    }
    const response = await api.get('/geocoding/coordinates', { params: { lat, lng } });
    return response.data;
  },

  // 네이버맵 길찾기 (Directions API)
  getDirections: async (startLat, startLng, endLat, endLng, option = 'traoptimal') => {
    if (isDemoMode()) {
      return { success: true, data: null };
    }
    const response = await api.get('/geocoding/directions', {
      params: {
        start: `${startLng},${startLat}`, // 경도,위도 순서
        goal: `${endLng},${endLat}`,
        option: option // traoptimal=최적, trafast=최단, tracomfort=편한길
      }
    });
    return response.data;
  },
};

