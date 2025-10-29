import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/auth';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 토큰을 가져오는 함수
const getToken = () => {
  return localStorage.getItem('token');
};

// 토큰을 저장하는 함수
const setToken = (token) => {
  localStorage.setItem('token', token);
};

// 토큰을 제거하는 함수
const removeToken = () => {
  localStorage.removeItem('token');
};

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
      removeToken();
      // 로그인 페이지로 리다이렉트 (필요시)
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  // 로그인
  login: async (id, password) => {
    try {
      const response = await api.post('/login', { id, password });
      const { token, user } = response.data;
      
      if (token) {
        setToken(token);
      }
      
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // 회원가입
  register: async (userData) => {
    try {
      const response = await api.post('/register', userData);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // 토큰 검증
  validateToken: async () => {
    try {
      const response = await api.post('/validate');
      return response.data;
    } catch (error) {
      removeToken();
      throw error;
    }
  },

  // 로그아웃
  logout: () => {
    removeToken();
  },

  // 토큰 가져오기
  getToken: getToken,

  // 토큰 설정
  setToken: setToken,

  // 토큰 제거
  removeToken: removeToken,
};

// 다른 API들도 토큰을 자동으로 포함하도록 설정
export const setupApiInterceptors = () => {
  // 모든 axios 인스턴스에 토큰 인터셉터 적용
  axios.interceptors.request.use(
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

  axios.interceptors.response.use(
    (response) => {
      return response;
    },
    (error) => {
      if (error.response?.status === 401) {
        removeToken();
        window.location.href = '/login';
      }
      return Promise.reject(error);
    }
  );
};
