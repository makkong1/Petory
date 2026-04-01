import axios from 'axios';
import {
  getToken,
  setToken,
  getRefreshToken,
  setRefreshToken,
  removeAllTokens,
} from './tokenStorage';

/** 백엔드 API 루트 (환경변수 없으면 로컬) */
export const API_ROOT = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

function isAuthRefreshRequest(config) {
  if (!config) return false;
  const url = config.url || '';
  const base = config.baseURL || '';
  const full = `${base}${url}`;
  return url.includes('/auth/refresh') || full.includes('/auth/refresh');
}

function handle403Branch(error) {
  if (error.response?.status === 403) {
    const data = error.response?.data;
    if (data?.errorCode === 'EMAIL_VERIFICATION_REQUIRED') {
      if (typeof window !== 'undefined') {
        const currentUrl = window.location.pathname + window.location.search;
        const purpose = data.purpose || '';
        window.dispatchEvent(
          new CustomEvent('emailVerificationRequired', {
            detail: {
              purpose,
              currentUrl,
              message: data.message,
            },
          })
        );
      }
      return Promise.reject(error);
    }
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('showPermissionModal'));
    }
  }
  return Promise.reject(error);
}

/**
 * 401 시 Refresh → 원요청 재시도, 동시 요청은 큐 처리.
 * 각 axios 인스턴스에 붙이되, 갱신 상태는 모듈 단일 공유.
 */
export function attachAuthInterceptors(instance) {
  instance.interceptors.request.use(
    (config) => {
      const token = getToken();
      if (token && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  instance.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config;

      if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
        if (isAuthRefreshRequest(originalRequest)) {
          removeAllTokens();
          if (typeof window !== 'undefined' && window.redirectToLogin) {
            window.redirectToLogin();
          }
          return Promise.reject(error);
        }

        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject });
          })
            .then((token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              return instance(originalRequest);
            })
            .catch((err) => Promise.reject(err));
        }

        originalRequest._retry = true;
        isRefreshing = true;

        const refreshToken = getRefreshToken();
        if (!refreshToken) {
          removeAllTokens();
          processQueue(error);
          isRefreshing = false;
          if (typeof window !== 'undefined' && window.redirectToLogin) {
            window.redirectToLogin();
          }
          return Promise.reject(error);
        }

        try {
          const response = await axios.post(
            `${API_ROOT}/auth/refresh`,
            { refreshToken },
            { headers: { 'Content-Type': 'application/json' } }
          );
          const { accessToken, refreshToken: newRefreshToken } = response.data;
          setToken(accessToken);
          if (newRefreshToken) {
            setRefreshToken(newRefreshToken);
          }
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          processQueue(null, accessToken);
          isRefreshing = false;
          return instance(originalRequest);
        } catch (refreshError) {
          removeAllTokens();
          processQueue(refreshError);
          isRefreshing = false;
          if (typeof window !== 'undefined' && window.redirectToLogin) {
            window.redirectToLogin();
          }
          return Promise.reject(refreshError);
        }
      }

      if (error.response?.status === 403) {
        return handle403Branch(error);
      }

      return Promise.reject(error);
    }
  );
}

/**
 * baseURL 기준으로 axios 인스턴스 생성 + 인증·401 갱신 인터셉터 적용
 */
export function createAuthAxios(baseURL, extraConfig = {}) {
  const { headers: extraHeaders, ...rest } = extraConfig;
  const instance = axios.create({
    baseURL,
    headers: {
      'Content-Type': 'application/json',
      ...(extraHeaders || {}),
    },
    ...rest,
  });
  attachAuthInterceptors(instance);
  return instance;
}
