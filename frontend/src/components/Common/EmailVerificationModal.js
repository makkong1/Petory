import React, { useState } from 'react';
import styled from 'styled-components';
import { userProfileApi } from '../../api/userApi';

const PURPOSE_LABELS = {
  REGISTRATION: '회원가입 완료',
  PASSWORD_RESET: '비밀번호 변경',
  PET_CARE: '펫케어 서비스 이용',
  MEETUP: '모임 서비스 이용',
  LOCATION_REVIEW: '리뷰 작성',
  BOARD_EDIT: '게시글 수정/삭제',
  COMMENT_EDIT: '댓글 수정/삭제',
  MISSING_PET: '실종 제보 작성',
};

const EmailVerificationModal = ({ isOpen, onClose, purpose, onSuccess }) => {
  const [status, setStatus] = useState('idle'); // 'idle', 'sending', 'success', 'error'
  const [message, setMessage] = useState('');

  if (!isOpen) return null;

  const handleSendEmail = async () => {
    if (!purpose) {
      setMessage('인증 용도를 지정해주세요.');
      setStatus('error');
      return;
    }

    setStatus('sending');
    setMessage('');

    try {
      await userProfileApi.sendVerificationEmail(purpose);
      setStatus('success');
      setMessage('이메일 인증 메일이 발송되었습니다. 이메일을 확인해주세요.');
    } catch (error) {
      setStatus('error');
      const errorMessage = error.response?.data?.message ||
        error.message ||
        '이메일 발송에 실패했습니다. 다시 시도해주세요.';
      setMessage(errorMessage);
    }
  };

  const handleClose = () => {
    setStatus('idle');
    setMessage('');
    onClose();
  };

  const handleSuccess = () => {
    if (onSuccess) {
      onSuccess();
    }
    handleClose();
  };

  return (
    <ModalOverlay onClick={handleClose}>
      <ModalContent onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <ModalIcon>✉️</ModalIcon>
          <ModalTitle>이메일 인증이 필요합니다</ModalTitle>
        </ModalHeader>
        <ModalBody>
          <Message>
            {purpose && PURPOSE_LABELS[purpose]
              ? `${PURPOSE_LABELS[purpose]}을(를) 위해 이메일 인증이 필요합니다.`
              : '이 기능을 이용하려면 이메일 인증이 필요합니다.'}
          </Message>

          {status === 'success' && (
            <SuccessMessage>
              {message}
              <br />
              <br />
              이메일의 링크를 클릭하여 인증을 완료해주세요.
            </SuccessMessage>
          )}

          {status === 'error' && (
            <ErrorMessage>{message}</ErrorMessage>
          )}
        </ModalBody>
        <ModalFooter>
          {status === 'success' ? (
            <Button onClick={handleSuccess}>확인</Button>
          ) : (
            <>
              <Button onClick={handleClose} variant="secondary">
                취소
              </Button>
              <Button
                onClick={handleSendEmail}
                disabled={status === 'sending'}
                variant="primary"
              >
                {status === 'sending' ? '발송 중...' : '인증 메일 발송'}
              </Button>
            </>
          )}
        </ModalFooter>
      </ModalContent>
    </ModalOverlay>
  );
};

export default EmailVerificationModal;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
  
  @keyframes fadeIn {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }
`;

const ModalContent = styled.div`
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xl};
  max-width: 500px;
  width: 90%;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  animation: slideUp 0.3s ease;
  
  @keyframes slideUp {
    from {
      transform: translateY(20px);
      opacity: 0;
    }
    to {
      transform: translateY(0);
      opacity: 1;
    }
  }
`;

const ModalHeader = styled.div`
  text-align: center;
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const ModalIcon = styled.div`
  font-size: 48px;
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const ModalTitle = styled.h2`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  margin: 0;
`;

const ModalBody = styled.div`
  text-align: center;
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const Message = styled.p`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body1.fontSize};
  margin-bottom: ${props => props.theme.spacing.md};
  line-height: 1.6;
`;

const SuccessMessage = styled(Message)`
  color: #4caf50;
  font-weight: 600;
`;

const ErrorMessage = styled(Message)`
  color: #f44336;
  font-weight: 600;
`;

const ModalFooter = styled.div`
  display: flex;
  justify-content: center;
  gap: ${props => props.theme.spacing.md};
`;

const Button = styled.button`
  background: ${props =>
    props.variant === 'secondary'
      ? props.theme.colors.border
      : props.theme.colors.primary};
  color: ${props =>
    props.variant === 'secondary'
      ? props.theme.colors.text
      : 'white'};
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: ${props => props.disabled ? 'not-allowed' : 'pointer'};
  transition: all 0.2s ease;
  min-width: 120px;
  opacity: ${props => props.disabled ? 0.6 : 1};
  
  &:hover:not(:disabled) {
    background: ${props =>
    props.variant === 'secondary'
      ? props.theme.colors.borderDark
      : props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }
`;
