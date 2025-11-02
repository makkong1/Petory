import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../api/authApi';

const AuthContext = createContext();

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  // 앱 시작 시 토큰 검증
  useEffect(() => {
    const checkAuth = async () => {
      const token = authApi.getToken();
      const refreshToken = authApi.getRefreshToken();
      
      // Access Token이 없고 Refresh Token이 있으면 갱신 시도
      if (!token && refreshToken) {
        try {
          const response = await authApi.refreshAccessToken();
          if (response.accessToken) {
            setUser(response.user);
            setIsAuthenticated(true);
            setLoading(false);
            return;
          }
        } catch (error) {
          console.error('Refresh Token 갱신 실패:', error);
          authApi.removeAllTokens();
        }
      } else if (token) {
        // Access Token이 있으면 검증
        try {
          const response = await authApi.validateToken();
          if (response.valid) {
            setUser(response.user);
            setIsAuthenticated(true);
          } else {
            // Access Token이 유효하지 않으면 Refresh Token으로 갱신 시도
            if (refreshToken) {
              try {
                const refreshResponse = await authApi.refreshAccessToken();
                if (refreshResponse.accessToken) {
                  setUser(refreshResponse.user);
                  setIsAuthenticated(true);
                  setLoading(false);
                  return;
                }
              } catch (refreshError) {
                console.error('Refresh Token 갱신 실패:', refreshError);
                authApi.removeAllTokens();
              }
            } else {
              authApi.removeAllTokens();
            }
          }
        } catch (error) {
          console.error('토큰 검증 실패:', error);
          // Refresh Token으로 갱신 시도
          if (refreshToken) {
            try {
              const refreshResponse = await authApi.refreshAccessToken();
              if (refreshResponse.accessToken) {
                setUser(refreshResponse.user);
                setIsAuthenticated(true);
                setLoading(false);
                return;
              }
            } catch (refreshError) {
              console.error('Refresh Token 갱신 실패:', refreshError);
              authApi.removeAllTokens();
            }
          } else {
            authApi.removeAllTokens();
          }
        }
      }
      setLoading(false);
    };

    checkAuth();
  }, []);

  const login = async (username, password) => {
    try {
      const response = await authApi.login(username, password);
      // accessToken과 refreshToken은 authApi.login에서 자동 저장됨
      setUser(response.user);
      setIsAuthenticated(true);
      return response;
    } catch (error) {
      throw error;
    }
  };

  const register = async (userData) => {
    try {
      const response = await authApi.register(userData);
      return response;
    } catch (error) {
      throw error;
    }
  };

  const logout = async () => {
    try {
      await authApi.logout(); // 서버에 로그아웃 요청 및 토큰 제거
    } catch (error) {
      console.error('로그아웃 중 오류:', error);
      // 오류가 있어도 클라이언트 상태는 초기화
      authApi.removeAllTokens();
    } finally {
      setUser(null);
      setIsAuthenticated(false);
    }
  };

  // 로그인 페이지로 리다이렉트하는 함수
  const redirectToLogin = () => {
    if (typeof window !== 'undefined' && window.redirectToLogin) {
      window.redirectToLogin();
    }
  };

  const value = {
    user,
    loading,
    isAuthenticated,
    login,
    register,
    logout,
    redirectToLogin,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};
