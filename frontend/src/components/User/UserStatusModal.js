import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { userApi, adminUserApi } from '../../api/userApi';

const UserStatusModal = ({ user, onClose }) => {
  const [formData, setFormData] = useState({
    status: 'ACTIVE',
    warningCount: 0,
    suspendedUntil: '',
    role: 'USER'
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (user) {
      setFormData({
        status: user.status || 'ACTIVE',
        warningCount: user.warningCount || 0,
        suspendedUntil: user.suspendedUntil ? new Date(user.suspendedUntil).toISOString().slice(0, 16) : '',
        role: user.role || 'USER'
      });
    }
  }, [user]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));

    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    setLoading(true);

    try {
      const updateData = {
        status: formData.status,
        warningCount: parseInt(formData.warningCount) || 0,
        suspendedUntil: formData.suspendedUntil ? new Date(formData.suspendedUntil).toISOString() : null,
      };

      // 역할이 ADMIN으로 변경되는 경우 (일반 사용자 -> ADMIN)
      const originalRole = user.role;
      const newRole = formData.role;
      
      if (originalRole !== 'ADMIN' && originalRole !== 'MASTER' && newRole === 'ADMIN') {
        // 일반 사용자를 ADMIN으로 승격
        await adminUserApi.promoteToAdmin(user.idx);
        alert('유저가 ADMIN으로 승격되었습니다.');
      } else if (newRole === 'ADMIN' || newRole === 'MASTER') {
        // ADMIN/MASTER 역할 변경 시도 시 에러
        alert('관리자 역할 변경은 별도 엔드포인트를 사용해주세요.');
        setLoading(false);
        return;
      }

      // 상태 관리 업데이트
      await userApi.updateUserStatus(user.idx, updateData);
      alert('상태가 성공적으로 업데이트되었습니다.');
      onClose();
    } catch (error) {
      console.error('Error updating user status:', error);
      const errorMessage = error.response?.data?.message || error.message || '상태 업데이트에 실패했습니다.';
      alert(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <ModalOverlay onClick={handleOverlayClick}>
      <ModalContent>
        <ModalHeader>
          <ModalTitle>사용자 상태 관리</ModalTitle>
          <CloseButton onClick={onClose}>×</CloseButton>
        </ModalHeader>

        <UserInfo>
          <InfoRow><strong>사용자명:</strong> {user?.username}</InfoRow>
          <InfoRow><strong>이메일:</strong> {user?.email}</InfoRow>
          <InfoRow><strong>현재 역할:</strong> {user?.role}</InfoRow>
        </UserInfo>

        <Form onSubmit={handleSubmit}>
          <FormGroup>
            <Label>계정 상태 *</Label>
            <Select
              name="status"
              value={formData.status}
              onChange={handleChange}
            >
              <option value="ACTIVE">정상 (ACTIVE)</option>
              <option value="SUSPENDED">이용제한 중 (SUSPENDED)</option>
              <option value="BANNED">영구 차단 (BANNED)</option>
            </Select>
          </FormGroup>

          <FormGroup>
            <Label>경고 횟수</Label>
            <Input
              type="number"
              name="warningCount"
              value={formData.warningCount}
              onChange={handleChange}
              min="0"
              placeholder="경고 횟수를 입력하세요"
            />
          </FormGroup>

          <FormGroup>
            <Label>정지 기간 (SUSPENDED일 때만 적용)</Label>
            <Input
              type="datetime-local"
              name="suspendedUntil"
              value={formData.suspendedUntil}
              onChange={handleChange}
              disabled={formData.status !== 'SUSPENDED'}
            />
            {formData.status !== 'SUSPENDED' && (
              <HelperText>정지 기간은 이용제한 중 상태일 때만 설정할 수 있습니다.</HelperText>
            )}
          </FormGroup>

          {user && user.role !== 'ADMIN' && user.role !== 'MASTER' && (
            <FormGroup>
              <Label>역할 승격</Label>
              <Select
                name="role"
                value={formData.role}
                onChange={handleChange}
              >
                <option value="USER">일반 사용자</option>
                <option value="SERVICE_PROVIDER">서비스 제공자</option>
                <option value="ADMIN">관리자 (승격)</option>
              </Select>
              {formData.role === 'ADMIN' && (
                <HelperText>일반 사용자를 ADMIN으로 승격합니다.</HelperText>
              )}
            </FormGroup>
          )}

          <ButtonGroup>
            <Button type="button" variant="secondary" onClick={onClose}>
              취소
            </Button>
            <Button type="submit" variant="primary" disabled={loading}>
              {loading ? '저장 중...' : '저장'}
            </Button>
          </ButtonGroup>
        </Form>
      </ModalContent>
    </ModalOverlay>
  );
};

export default UserStatusModal;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: ${({ theme }) => theme.colors.overlay};
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContent = styled.div`
  background: ${({ theme }) => theme.colors.surfaceElevated};
  padding: 30px;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: ${({ theme }) => theme.shadows.xl};
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
`;

const ModalTitle = styled.h2`
  color: ${({ theme }) => theme.colors.text};
  margin: 0;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: ${({ theme }) => theme.colors.textSecondary};

  &:hover {
    color: ${({ theme }) => theme.colors.text};
  }
`;

const UserInfo = styled.div`
  background: ${({ theme }) => theme.colors.surfaceSoft};
  padding: 15px;
  border-radius: 8px;
  margin-bottom: 20px;
`;

const InfoRow = styled.div`
  margin-bottom: 8px;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 14px;

  strong {
    color: ${({ theme }) => theme.colors.text};
    margin-right: 8px;
  }
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
`;

const Label = styled.label`
  color: ${({ theme }) => theme.colors.text};
  margin-bottom: 4px;
  font-weight: 500;
`;

const Input = styled.input`
  padding: 10px;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: 6px;
  font-size: 16px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.borderFocus};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }
`;

const Select = styled.select`
  padding: 10px;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: 6px;
  font-size: 16px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.borderFocus};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const HelperText = styled.div`
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 12px;
  margin-top: 4px;
  font-style: italic;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 12px;
  margin-top: 20px;
`;

const Button = styled.button`
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s ease;

  ${props => props.variant === 'primary' && `
    background: ${props.theme.colors.primary};
    color: ${props.theme.colors.textInverse};

    &:hover {
      background: ${props.theme.colors.primaryDark};
    }

    &:disabled {
      background: ${props.theme.colors.borderDark};
      cursor: not-allowed;
    }
  `}

  ${props => props.variant === 'secondary' && `
    background: ${props.theme.colors.surfaceSoft};
    color: ${props.theme.colors.text};

    &:hover {
      background: ${props.theme.colors.surfaceHover};
    }
  `}
`;

