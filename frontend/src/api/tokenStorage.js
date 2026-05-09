/**
 * 토큰 저장소.
 * - 읽기: localStorage (동기, 기존 코드 호환)
 * - 쓰기: localStorage + @capacitor/preferences (비동기, 네이티브 앱 영구 보존)
 */

const isCapacitor = () =>
  typeof window !== 'undefined' && !!(window.Capacitor?.isNativePlatform?.());

const saveToPreferences = async (key, value) => {
  if (!isCapacitor()) return;
  try {
    const { Preferences } = await import('@capacitor/preferences');
    await Preferences.set({ key, value });
  } catch (error) {
    console.warn('Capacitor Preferences 저장 실패:', key, error);
  }
};

const removeFromPreferences = async (key) => {
  if (!isCapacitor()) return;
  try {
    const { Preferences } = await import('@capacitor/preferences');
    await Preferences.remove({ key });
  } catch (error) {
    console.warn('Capacitor Preferences 삭제 실패:', key, error);
  }
};

export const getToken = () =>
  localStorage.getItem('accessToken') || localStorage.getItem('token');

export const setToken = (token) => {
  localStorage.setItem('accessToken', token);
  if (localStorage.getItem('token')) localStorage.removeItem('token');
  saveToPreferences('accessToken', token);
};

export const removeToken = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('token');
  removeFromPreferences('accessToken');
};

export const getRefreshToken = () => localStorage.getItem('refreshToken');

export const setRefreshToken = (token) => {
  localStorage.setItem('refreshToken', token);
  saveToPreferences('refreshToken', token);
};

export const removeRefreshToken = () => {
  localStorage.removeItem('refreshToken');
  removeFromPreferences('refreshToken');
};

export const removeAllTokens = () => {
  removeToken();
  removeRefreshToken();
};
