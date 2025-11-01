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
      if (token) {
        try {
          const response = await authApi.validateToken();
          if (response.valid) {
            setUser(response.user);
            setIsAuthenticated(true);
          } else {
            authApi.logout();
          }
        } catch (error) {
          console.error('토큰 검증 실패:', error);
          authApi.logout();
        }
      }
      setLoading(false);
    };

    checkAuth();
  }, []);

  const login = async (username, password) => {
    try {
      const response = await authApi.login(username, password);
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

  const logout = () => {
    authApi.logout();
    setUser(null);
    setIsAuthenticated(false);
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
