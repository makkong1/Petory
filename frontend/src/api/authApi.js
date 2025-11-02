import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/auth';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Access Token 관리
const getToken = () => {
  return localStorage.getItem('accessToken');
};

const setToken = (token) => {
  localStorage.setItem('accessToken', token);
};

const removeToken = () => {
  localStorage.removeItem('accessToken');
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
    try {
      const response = await api.post('/login', { id, password });
      const { accessToken, refreshToken, user } = response.data;
      
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
    try {
      const response = await api.post('/register', userData);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // Access Token 검증
  validateToken: async () => {
    try {
      const response = await api.post('/validate');
      return response.data;
    } catch (error) {
      // 401 에러는 인터셉터에서 처리됨
      throw error;
    }
  },

  // Refresh Token으로 Access Token 갱신
  refreshAccessToken: async () => {
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
    try {
      // 서버에 로그아웃 요청 (Refresh Token 제거)
      const token = getToken();
      if (token) {
        await api.post('/logout', {}, {
          headers: { Authorization: `Bearer ${token}` }
        });
      }
    } catch (error) {
      console.error('로그아웃 요청 실패:', error);
    } finally {
      // 클라이언트에서도 모든 토큰 제거
      removeAllTokens();
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
          // Refresh Token으로 Access Token 갱신
          const response = await axios.post('http://localhost:8080/api/auth/refresh', {
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
          processQueueGlobal(null, accessToken);
          isRefreshingGlobal = false;

          // 원래 요청 재시도
          return axios(originalRequest);
        } catch (refreshError) {
          // Refresh Token도 만료되었거나 유효하지 않은 경우
          removeAllTokens();
          processQueueGlobal(refreshError);
          isRefreshingGlobal = false;
          if (typeof window !== 'undefined' && window.redirectToLogin) {
            window.redirectToLogin();
          }
          return Promise.reject(refreshError);
        }
      } else if (error.response?.status === 403) {
        // 403 에러 시 권한 모달 표시 이벤트 발생
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('showPermissionModal'));
        }
      }

      return Promise.reject(error);
    }
  );
};
