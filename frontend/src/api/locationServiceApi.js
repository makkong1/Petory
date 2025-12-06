import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/location-services';

// Access Token 가져오기
const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
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

export const locationServiceApi = {
  /**
   * DB에서 위치 서비스 검색
   * 지역 계층별 검색만 수행 (내 위치는 거리 계산/길찾기용으로만 사용)
   * 
   * @param {Object} params 검색 파라미터
   * @param {string} params.sido 시도 (선택, 예: "서울특별시", "경기도")
   * @param {string} params.sigungu 시군구 (선택, 예: "노원구", "고양시 덕양구")
   * @param {string} params.eupmyeondong 읍면동 (선택, 예: "상계동", "동산동")
   * @param {string} params.roadName 도로명 (선택, 예: "상계로", "동세로")
   * @param {string} params.category 카테고리 (선택, 예: "동물약국", "미술관")
   * @param {number} params.size 최대 결과 수 (선택, 기본값: 500)
   * @returns {Promise} 검색 결과
   */
  searchPlaces: ({ 
    sido, 
    sigungu, 
    eupmyeondong, 
    roadName, 
    category, 
    size 
  } = {}) =>
    api.get('/search', {
      params: {
        ...(sido && { sido }),
        ...(sigungu && { sigungu }),
        ...(eupmyeondong && { eupmyeondong }),
        ...(roadName && { roadName }),
        ...(category && { category }),
        ...(typeof size === 'number' && { size }),
      },
    }),
};
