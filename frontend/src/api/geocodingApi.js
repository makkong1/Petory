import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api';

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

export const geocodingApi = {
  // 주소를 위도/경도로 변환
  addressToCoordinates: async (address) => {
    const response = await api.get('/geocoding/address', { params: { address } });
    return response.data; // response.data에 실제 데이터가 있음
  },
  
  // 위도/경도를 주소로 변환 (역지오코딩)
  coordinatesToAddress: async (lat, lng) => {
    const response = await api.get('/geocoding/coordinates', { params: { lat, lng } });
    return response.data;
  },
  
  // 네이버맵 길찾기 (Directions API)
  getDirections: async (startLat, startLng, endLat, endLng, option = 'traoptimal') => {
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

