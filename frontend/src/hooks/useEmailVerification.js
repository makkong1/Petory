import { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import EmailVerificationModal from '../components/Common/EmailVerificationModal';

/**
 * 이메일 인증 체크 및 모달 표시 훅
 * 
 * @param {string} purpose - 이메일 인증 용도 (PASSWORD_RESET, PET_CARE, MEETUP, etc.)
 * @returns {Object} { checkAndShowModal, EmailVerificationModalComponent }
 * 
 * @example
 * const { checkAndShowModal, EmailVerificationModalComponent } = useEmailVerification('PET_CARE');
 * 
 * const handleCreateCareRequest = async () => {
 *   if (!checkAndShowModal()) {
 *     return; // 이메일 인증이 필요하면 모달이 표시되고 함수 종료
 *   }
 *   // 이메일 인증 완료된 경우에만 실행
 *   await createCareRequest();
 * };
 * 
 * return (
 *   <>
 *     <EmailVerificationModalComponent />
 *     {/* 나머지 컴포넌트 */}
 *   </>
 * );
 */
export const useEmailVerification = (purpose) => {
  const { user } = useAuth();
  const [showModal, setShowModal] = useState(false);

  /**
   * 이메일 인증 여부 확인 및 필요시 모달 표시
   * @returns {boolean} 이메일 인증 완료 여부
   */
  const checkAndShowModal = () => {
    if (!user) {
      // 로그인하지 않은 경우
      return false;
    }

    if (user.emailVerified === null || !user.emailVerified) {
      // 이메일 인증이 필요한 경우 모달 표시
      setShowModal(true);
      return false;
    }

    // 이메일 인증 완료
    return true;
  };

  const EmailVerificationModalComponent = () => (
    <EmailVerificationModal
      isOpen={showModal}
      onClose={() => setShowModal(false)}
      purpose={purpose}
      onSuccess={() => {
        // 인증 성공 후 사용자 정보 갱신 (AuthContext에서 처리)
        setShowModal(false);
        // 페이지 새로고침 또는 사용자 정보 재조회
        window.location.reload();
      }}
    />
  );

  return {
    checkAndShowModal,
    EmailVerificationModalComponent,
    showModal,
    setShowModal,
  };
};

export default useEmailVerification;
