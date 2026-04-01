import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_USER } from '../mock/demoData';
import { API_ROOT, createAuthAxios } from './apiClient';
import {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  removeAllTokens,
} from './tokenStorage';

const AUTH_BASE = `${API_ROOT}/auth`;
const DEMO_TOKEN = 'demo-access-token';
const DEMO_REFRESH_TOKEN = 'demo-refresh-token';

const api = createAuthAxios(AUTH_BASE);

export const authApi = {
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

  refreshAccessToken: async () => {
    if (isDemoMode()) {
      const rt = getRefreshToken();
      if (rt === DEMO_REFRESH_TOKEN) {
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

      const response = await axios.post(
        `${AUTH_BASE}/refresh`,
        { refreshToken },
        { headers: { 'Content-Type': 'application/json' } }
      );

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

  logout: async () => {
    if (isDemoMode()) {
      removeAllTokens();
      return;
    }
    try {
      const token = getToken();
      if (token) {
        await api.post('/logout', {}, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    } catch (error) {
      console.error('로그아웃 요청 실패:', error);
    } finally {
      removeAllTokens();
    }
  },

  forgotPassword: async (email) => {
    try {
      const response = await axios.post(`${AUTH_BASE}/forgot-password`, { email });
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  getToken,
  getRefreshToken,
  setToken,
  setRefreshToken,
  removeToken,
  removeAllTokens,
};

/** @deprecated 각 API는 createAuthAxios로 401 갱신 처리. 호출해도 동작만 유지(빈 함수). */
export const setupApiInterceptors = () => {};
