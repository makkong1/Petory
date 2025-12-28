import { useCallback } from 'react';
import { useAuth } from '../contexts/AuthContext';

/**
 * 이메일 인증이 필요한 기능에서 사용하는 훅
 * @param {string} purpose - 이메일 인증 목적 (MEETUP, MISSING_PET, PET_CARE 등)
 * @returns {object} { checkAndRedirect, EmailVerificationPromptComponent }
 */
export const useEmailVerification = (purpose) => {
  const { user } = useAuth();

  /**
   * 이메일 인증 상태를 확인하고, 인증되지 않았으면 이메일 인증 페이지로 바로 이동
   * @returns {boolean} true: 인증 완료 또는 계속 진행 가능, false: 인증 필요 (이미 리다이렉트됨)
   */
  const checkAndRedirect = useCallback(() => {
    // user가 없으면 false 반환 (인증 필요)
    if (!user) {
      return false;
    }

    // 이메일 인증 상태 확인 (여러 가능한 필드명 지원)
    // emailVerified 또는 email_verified 필드가 true인 경우에만 인증 완료로 간주
    const isEmailVerified = user.emailVerified === true || 
                           user.email_verified === true ||
                           user.verified === true;

    // 이메일 인증이 완료되었으면 true 반환
    if (isEmailVerified) {
      return true;
    }

    // 이메일 인증이 필요하면 바로 이메일 인증 페이지로 이동
    const currentUrl = window.location.pathname + window.location.search;
    const redirectUrl = `/email-verification?redirect=${encodeURIComponent(currentUrl)}${purpose ? `&purpose=${purpose}` : ''}`;
    window.location.href = redirectUrl;
    
    return false; // 이동했으므로 false 반환
  }, [user, purpose]);

  /**
   * EmailVerificationPrompt 컴포넌트 (사용하지 않음 - 바로 리다이렉트하므로)
   */
  const EmailVerificationPromptComponent = () => null;

  return {
    checkAndRedirect,
    EmailVerificationPromptComponent,
  };
};

