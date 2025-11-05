import { useCallback } from 'react';
import { useAuth } from '../contexts/AuthContext';

/**
 * 권한 관리를 위한 Hook
 * @returns {Object} 권한 확인 함수들
 */
export const usePermission = () => {
  const { user, isAuthenticated } = useAuth();

  /**
   * 로그인 여부 확인
   * @returns {boolean} 로그인 여부
   */
  const checkLogin = useCallback(() => {
    return isAuthenticated && user !== null;
  }, [isAuthenticated, user]);

  /**
   * 관리자 권한 확인 (ADMIN 또는 MASTER)
   * @returns {boolean} 관리자 권한 여부
   */
  const checkAdmin = useCallback(() => {
    if (!user) return false;
    return user.role === 'ADMIN' || user.role === 'MASTER';
  }, [user]);

  /**
   * 특정 권한 확인
   * @param {string|string[]} requiredRoles - 필요한 권한 (문자열 또는 배열)
   * @returns {boolean} 권한 여부
   */
  const checkRole = useCallback((requiredRoles) => {
    if (!user) return false;
    const roles = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles];
    return roles.includes(user.role);
  }, [user]);

  /**
   * 로그인 필요 여부 확인 및 리다이렉트 필요 여부 반환
   * @returns {Object} { isLoggedIn: boolean, requiresRedirect: boolean }
   */
  const requireLogin = useCallback(() => {
    const isLoggedIn = checkLogin();
    return {
      isLoggedIn,
      requiresRedirect: !isLoggedIn,
    };
  }, [checkLogin]);

  /**
   * 관리자 권한 필요 여부 확인
   * @returns {Object} { isAdmin: boolean, requiresModal: boolean }
   */
  const requireAdmin = useCallback(() => {
    const isAdmin = checkAdmin();
    return {
      isAdmin,
      requiresModal: !isAdmin,
    };
  }, [checkAdmin]);

  return {
    checkLogin,
    checkAdmin,
    checkRole,
    requireLogin,
    requireAdmin,
    user,
    isAuthenticated,
  };
};

