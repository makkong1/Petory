import React from 'react';
import styled from 'styled-components';

const PermissionDeniedModal = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  return (
    <ModalOverlay onClick={onClose}>
      <ModalContent onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <ModalIcon>⚠️</ModalIcon>
          <ModalTitle>권한 없음</ModalTitle>
        </ModalHeader>
        <ModalBody>
          <Message>당신 권한 없어요!</Message>
          <SubMessage>이 기능을 사용하려면 적절한 권한이 필요합니다.</SubMessage>
        </ModalBody>
        <ModalFooter>
          <CloseButton onClick={onClose}>확인</CloseButton>
        </ModalFooter>
      </ModalContent>
    </ModalOverlay>
  );
};

export default PermissionDeniedModal;

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
  max-width: 400px;
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
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: 600;
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const SubMessage = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  margin: 0;
`;

const ModalFooter = styled.div`
  display: flex;
  justify-content: center;
  gap: ${props => props.theme.spacing.md};
`;

const CloseButton = styled.button`
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 120px;
  
  &:hover {
    background: ${props => props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }
`;

