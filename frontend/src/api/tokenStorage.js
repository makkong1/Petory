/**
 * Access / Refresh 토큰 localStorage 접근 (apiClient, authApi 공통)
 */
export const getToken = () =>
  localStorage.getItem('accessToken') || localStorage.getItem('token');

export const setToken = (token) => {
  localStorage.setItem('accessToken', token);
  if (localStorage.getItem('token')) {
    localStorage.removeItem('token');
  }
};

export const removeToken = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('token');
};

export const getRefreshToken = () => localStorage.getItem('refreshToken');

export const setRefreshToken = (token) => {
  localStorage.setItem('refreshToken', token);
};

export const removeRefreshToken = () => {
  localStorage.removeItem('refreshToken');
};

export const removeAllTokens = () => {
  removeToken();
  removeRefreshToken();
};
