import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_USER } from '../mock/demoData';

const BASE_URL = 'http://localhost:8080/api/auth';
const DEMO_TOKEN = 'demo-access-token';
const DEMO_REFRESH_TOKEN = 'demo-refresh-token';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Access Token 관리
const getToken = () => {
  // 기존 'token' 키도 확인 (하위 호환성)
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const setToken = (token) => {
  localStorage.setItem('accessToken', token);
  // 기존 'token' 키도 제거 (마이그레이션)
  if (localStorage.getItem('token')) {
    localStorage.removeItem('token');
  }
};

const removeToken = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('token'); // 기존 키도 제거
};

// Refresh Token 관리
const getRefreshToken = () => {
  return localStorage.getItem('refreshToken');
};

const setRefreshToken = (token) => {
  localStorage.setItem('refreshToken', token);
};

const removeRefreshToken = () => {
  localStorage.removeItem('refreshToken');
};

// 모든 토큰 제거
const removeAllTokens = () => {
  removeToken();
  removeRefreshToken();
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

// 응답 인터셉터 - 401 에러 시 Refresh Token으로 자동 갱신
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    // 401 에러이고, 아직 재시도하지 않은 요청인 경우
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // 이미 갱신 중이면 큐에 추가
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then(token => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch(err => {
            return Promise.reject(err);
          });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = getRefreshToken();

      if (!refreshToken) {
        // Refresh Token이 없으면 로그인 페이지로
        removeAllTokens();
        processQueue(error);
        isRefreshing = false;
        if (window.redirectToLogin) {
          window.redirectToLogin();
        }
        return Promise.reject(error);
      }

      try {
        // Refresh Token으로 Access Token 갱신
        const response = await axios.post(`${BASE_URL}/refresh`, {
          refreshToken: refreshToken
        });

        const { accessToken, refreshToken: newRefreshToken } = response.data;

        // 새 토큰 저장
        setToken(accessToken);
        if (newRefreshToken) {
          setRefreshToken(newRefreshToken);
        }

        // 원래 요청의 헤더에 새 토큰 추가
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;

        // 큐에 있는 요청들 처리
        processQueue(null, accessToken);
        isRefreshing = false;

        // 원래 요청 재시도
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh Token도 만료되었거나 유효하지 않은 경우
        removeAllTokens();
        processQueue(refreshError);
        isRefreshing = false;
        if (window.redirectToLogin) {
          window.redirectToLogin();
        }
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export const authApi = {
  // 로그인 - Access Token과 Refresh Token 모두 저장
  login: async (id, password) => {
    if (isDemoMode()) {
      setToken(DEMO_TOKEN);
      setRefreshToken(DEMO_REFRESH_TOKEN);
      return { accessToken: DEMO_TOKEN, refreshToken: DEMO_REFRESH_TOKEN, user: DEMO_USER };
    }
    try {
      const response = await api.post('/login', { id, password });
      const { accessToken, refreshToken } = response.data;

      if (accessToken) {
        setToken(accessToken);
      }
      if (refreshToken) {
        setRefreshToken(refreshToken);
      }

      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // 회원가입
  register: async (userData) => {
    if (isDemoMode()) {
      return { success: true, message: '데모 모드에서는 회원가입이 제한됩니다.' };
    }
    try {
      const response = await api.post('/register', userData);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // Access Token 검증
  validateToken: async () => {
    if (isDemoMode()) {
      const token = getToken();
      if (token === DEMO_TOKEN) {
        return { valid: true, user: DEMO_USER };
      }
      return { valid: false };
    }
    try {
      const response = await api.post('/validate');
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // Refresh Token으로 Access Token 갱신
  refreshAccessToken: async () => {
    if (isDemoMode()) {
      const refreshToken = getRefreshToken();
      if (refreshToken === DEMO_REFRESH_TOKEN) {
        setToken(DEMO_TOKEN);
        setRefreshToken(DEMO_REFRESH_TOKEN);
        return { accessToken: DEMO_TOKEN, refreshToken: DEMO_REFRESH_TOKEN, user: DEMO_USER };
      }
      removeAllTokens();
      throw new Error('Refresh Token이 없습니다.');
    }
    try {
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        throw new Error('Refresh Token이 없습니다.');
      }

      const response = await axios.post(`${BASE_URL}/refresh`, {
        refreshToken: refreshToken
      });

      const { accessToken, refreshToken: newRefreshToken } = response.data;

      if (accessToken) {
        setToken(accessToken);
      }
      if (newRefreshToken) {
        setRefreshToken(newRefreshToken);
      }

      return response.data;
    } catch (error) {
      removeAllTokens();
      throw error;
    }
  },

  // 로그아웃 - 서버에 로그아웃 요청 및 모든 토큰 제거
  logout: async () => {
    if (isDemoMode()) {
      removeAllTokens();
      return;
    }
    try {
      const token = getToken();
      if (token) {
        await api.post('/logout', {}, {
          headers: { Authorization: `Bearer ${token}` }
        });
      }
    } catch (error) {
      console.error('로그아웃 요청 실패:', error);
    } finally {
      removeAllTokens();
    }
  },

  // 비밀번호 찾기 - 비밀번호 재설정 이메일 발송 (인증 불필요)
  forgotPassword: async (email) => {
    try {
      const response = await axios.post(`${BASE_URL}/forgot-password`, { email });
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // 토큰 가져오기
  getToken: getToken,
  getRefreshToken: getRefreshToken,

  // 토큰 설정
  setToken: setToken,
  setRefreshToken: setRefreshToken,

  // 토큰 제거
  removeToken: removeToken,
  removeAllTokens: removeAllTokens,
};

// 다른 API들도 토큰을 자동으로 포함하도록 설정
let isRefreshingGlobal = false;
let failedQueueGlobal = [];
let interceptorsSetup = false; // 중복 설정 방지

const processQueueGlobal = (error, token = null) => {
  failedQueueGlobal.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueueGlobal = [];
};

export const setupApiInterceptors = () => {
  // 이미 설정되었으면 중복 설정 방지
  if (interceptorsSetup) {
    return;
  }
  interceptorsSetup = true;

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
    async (error) => {
      const originalRequest = error.config;

      // 401 에러이고, 아직 재시도하지 않은 요청인 경우
      if (error.response?.status === 401 && !originalRequest._retry) {
        // /api/auth/refresh 요청 자체는 제외 (무한 루프 방지)
        if (originalRequest.url?.includes('/api/auth/refresh')) {
          removeAllTokens();
          if (typeof window !== 'undefined' && window.redirectToLogin) {
            window.redirectToLogin();
          }
          return Promise.reject(error);
        }

        if (isRefreshingGlobal) {
          // 이미 갱신 중이면 큐에 추가
          return new Promise((resolve, reject) => {
            failedQueueGlobal.push({ resolve, reject });
          })
            .then(token => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              return axios(originalRequest);
            })
            .catch(err => {
              return Promise.reject(err);
            });
        }

        originalRequest._retry = true;
        isRefreshingGlobal = true;

        const refreshToken = getRefreshToken();

        if (!refreshToken) {
          removeAllTokens();
          processQueueGlobal(error);
          isRefreshingGlobal = false;
          if (typeof window !== 'undefined' && window.redirectToLogin) {
            window.redirectToLogin();
          }
          return Promise.reject(error);
        }

        try {
          console.log('🔄 Access Token 재발급 시도 중...');

          // Refresh Token으로 Access Token 갱신
          const response = await axios.post('http://localhost:8080/api/auth/refresh', {
            refreshToken: refreshToken
          });

          const { accessToken, refreshToken: newRefreshToken } = response.data;

          console.log('✅ Access Token 재발급 성공:', {
            timestamp: new Date().toISOString(),
            hasNewAccessToken: !!accessToken,
            hasNewRefreshToken: !!newRefreshToken
          });

          // 새 토큰 저장
          setToken(accessToken);
          if (newRefreshToken) {
            setRefreshToken(newRefreshToken);
          }

          // 원래 요청의 헤더에 새 토큰 추가
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;

          // 큐에 있는 요청들 처리
          processQueueGlobal(null, accessToken);
          isRefreshingGlobal = false;

          // 원래 요청 재시도
          return axios(originalRequest);
        } catch (refreshError) {
          // Refresh Token도 만료되었거나 유효하지 않은 경우
          console.error('❌ Access Token 재발급 실패:', {
            error: refreshError.response?.data?.error || refreshError.message,
            timestamp: new Date().toISOString()
          });

          removeAllTokens();
          processQueueGlobal(refreshError);
          isRefreshingGlobal = false;

          if (typeof window !== 'undefined' && window.redirectToLogin) {
            console.log('🔐 Refresh Token 만료로 인한 로그인 페이지 리다이렉트');
            window.redirectToLogin();
          }
          return Promise.reject(refreshError);
        }
      } else if (error.response?.status === 403) {
        console.log('🔍 403 에러 발생 - 응답 데이터 확인:', {
          errorCode: error.response?.data?.errorCode,
          message: error.response?.data?.message,
          purpose: error.response?.data?.purpose,
          fullResponse: error.response?.data
        });
        
        // 이메일 인증 필요 예외 체크
        if (error.response?.data?.errorCode === 'EMAIL_VERIFICATION_REQUIRED') {
          console.warn('📧 이메일 인증 필요:', {
            message: error.response?.data?.message,
            purpose: error.response?.data?.purpose,
            redirectUrl: error.response?.data?.redirectUrl,
            timestamp: new Date().toISOString()
          });
          
          // 전역 이벤트 발생 (각 페이지에서 처리하도록)
          if (typeof window !== 'undefined') {
            const currentUrl = window.location.pathname + window.location.search;
            const purpose = error.response?.data?.purpose || '';
            const event = new CustomEvent('emailVerificationRequired', {
              detail: {
                purpose,
                currentUrl,
                message: error.response?.data?.message
              }
            });
            window.dispatchEvent(event);
          }
          return Promise.reject(error);
        }
        
        // 일반 403 에러 시 권한 모달 표시 이벤트 발생
        console.warn('🚫 403 Forbidden 에러 발생:', {
          url: originalRequest?.url,
          method: originalRequest?.method,
          timestamp: new Date().toISOString()
        });
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('showPermissionModal'));
        }
      }

      return Promise.reject(error);
    }
  );
};
