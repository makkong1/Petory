import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { userApi, adminUserApi } from '../../api/userApi';

const UserModal = ({ user, onClose }) => {
  const [formData, setFormData] = useState({
    id: '',
    username: '',
    email: '',
    password: '',
    role: 'USER',
    location: '',
    petInfo: ''
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (user) {
      setFormData({
        id: user.id || '',
        username: user.username || '',
        email: user.email || '',
        password: '', // 비밀번호는 수정시에도 새로 입력받기
        role: user.role || 'USER',
        location: user.location || '',
        petInfo: user.petInfo || ''
      });
    }
  }, [user]);

  const validateForm = () => {
    const newErrors = {};

    if (!formData.id.trim()) {
      newErrors.id = 'ID는 필수입니다.';
    }

    if (!formData.username.trim()) {
      newErrors.username = '사용자명은 필수입니다.';
    }

    if (!formData.email.trim()) {
      newErrors.email = '이메일은 필수입니다.';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = '올바른 이메일 형식이 아닙니다.';
    }

    if (!user && !formData.password.trim()) {
      newErrors.password = '비밀번호는 필수입니다.';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));

    // 해당 필드의 에러 메시지 제거
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      if (user) {
        // 수정
        const originalRole = user.role;
        const newRole = formData.role;

        // 역할이 ADMIN으로 변경되는 경우 (일반 사용자 -> ADMIN)
        if (originalRole !== 'ADMIN' && originalRole !== 'MASTER' && newRole === 'ADMIN') {
          // 일반 사용자를 ADMIN으로 승격
          await adminUserApi.promoteToAdmin(user.idx);
          // 나머지 정보는 일반 업데이트로 처리 (역할 제외)
          const { role, ...updateData } = formData;
          if (Object.keys(updateData).length > 0) {
            await userApi.updateUser(user.idx, updateData);
          }
          alert('유저가 ADMIN으로 승격되었습니다.');
        } else if (newRole === 'ADMIN' || newRole === 'MASTER') {
          // ADMIN/MASTER 역할 변경 시도 시 에러
          alert('관리자 역할 변경은 별도 엔드포인트를 사용해주세요.');
          setLoading(false);
          return;
        } else {
          // 일반 정보 수정 (역할 변경 없음)
          await userApi.updateUser(user.idx, formData);
          alert('유저가 성공적으로 수정되었습니다.');
        }
      } else {
        // 생성 - ADMIN/MASTER 생성 불가
        if (formData.role === 'ADMIN' || formData.role === 'MASTER') {
          alert('관리자 계정은 별도 엔드포인트를 사용해주세요.');
          setLoading(false);
          return;
        }
        await userApi.createUser(formData);
        alert('유저가 성공적으로 생성되었습니다.');
      }
      onClose();
    } catch (error) {
      console.error('Error saving user:', error);
      const errorMessage = error.response?.data?.message || error.message || '유저 저장에 실패했습니다.';
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
          <ModalTitle>
            {user ? '유저 수정' : '새 유저 추가'}
          </ModalTitle>
          <CloseButton onClick={onClose}>×</CloseButton>
        </ModalHeader>

        <Form onSubmit={handleSubmit}>
          <FormGroup>
            <Label>로그인 ID *</Label>
            <Input
              type="text"
              name="id"
              value={formData.id}
              onChange={handleChange}
              placeholder="로그인용 ID를 입력하세요"
            />
            {errors.id && <ErrorMessage>{errors.id}</ErrorMessage>}
          </FormGroup>

          <FormGroup>
            <Label>이름 *</Label>
            <Input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              placeholder="사용자명을 입력하세요"
            />
            {errors.username && <ErrorMessage>{errors.username}</ErrorMessage>}
          </FormGroup>

          <FormGroup>
            <Label>이메일 *</Label>
            <Input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="이메일을 입력하세요"
            />
            {errors.email && <ErrorMessage>{errors.email}</ErrorMessage>}
          </FormGroup>

          <FormGroup>
            <Label>비밀번호 {!user && '*'}</Label>
            <Input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              placeholder={user ? "변경할 비밀번호 (선택사항)" : "비밀번호를 입력하세요"}
            />
            {errors.password && <ErrorMessage>{errors.password}</ErrorMessage>}
          </FormGroup>

          <FormGroup>
            <Label>역할</Label>
            <Select
              name="role"
              value={formData.role}
              onChange={handleChange}
              disabled={user && (user.role === 'ADMIN' || user.role === 'MASTER')}
            >
              <option value="USER">일반 사용자</option>
              <option value="SERVICE_PROVIDER">서비스 제공자</option>
              <option value="ADMIN">관리자 (승격 가능)</option>
              {user && (user.role === 'ADMIN' || user.role === 'MASTER') && (
                <option value={user.role} disabled>{user.role === 'MASTER' ? '마스터 (변경 불가)' : '관리자 (변경 불가)'}</option>
              )}
            </Select>
            {user && (user.role === 'ADMIN' || user.role === 'MASTER') && (
              <HelperText>관리자 권한은 변경할 수 없습니다.</HelperText>
            )}
            {user && user.role !== 'ADMIN' && user.role !== 'MASTER' && formData.role === 'ADMIN' && (
              <HelperText>일반 사용자를 ADMIN으로 승격합니다.</HelperText>
            )}
          </FormGroup>

          <FormGroup>
            <Label>위치</Label>
            <Input
              type="text"
              name="location"
              value={formData.location}
              onChange={handleChange}
              placeholder="위치를 입력하세요"
            />
          </FormGroup>

          <FormGroup>
            <Label>펫 정보</Label>
            <TextArea
              name="petInfo"
              value={formData.petInfo}
              onChange={handleChange}
              placeholder="반려동물 정보를 입력하세요"
            />
          </FormGroup>

          <ButtonGroup>
            <Button type="button" variant="secondary" onClick={onClose}>
              취소
            </Button>
            <Button type="submit" variant="primary" disabled={loading}>
              {loading ? '저장 중...' : (user ? '수정' : '생성')}
            </Button>
          </ButtonGroup>
        </Form>
      </ModalContent>
    </ModalOverlay>
  );
};

export default UserModal;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContent = styled.div`
  background: white;
  padding: 30px;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow-y: auto;
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
`;

const ModalTitle = styled.h2`
  color: #333;
  margin: 0;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
  
  &:hover {
    color: #333;
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
  color: #333;
  margin-bottom: 4px;
  font-weight: 500;
`;

const Input = styled.input`
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  
  &:focus {
    outline: none;
    border-color: #4a90e2;
    box-shadow: 0 0 0 2px rgba(74, 144, 226, 0.2);
  }
`;

const Select = styled.select`
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  background: white;
  
  &:focus {
    outline: none;
    border-color: #4a90e2;
    box-shadow: 0 0 0 2px rgba(74, 144, 226, 0.2);
  }
`;

const TextArea = styled.textarea`
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  min-height: 80px;
  resize: vertical;
  font-family: inherit;
  
  &:focus {
    outline: none;
    border-color: #4a90e2;
    box-shadow: 0 0 0 2px rgba(74, 144, 226, 0.2);
  }
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
    background: #4a90e2;
    color: white;
    
    &:hover {
      background: #357abd;
    }
    
    &:disabled {
      background: #ccc;
      cursor: not-allowed;
    }
  `}
  
  ${props => props.variant === 'secondary' && `
    background: #f5f5f5;
    color: #333;
    
    &:hover {
      background: #e0e0e0;
    }
  `}
`;

const ErrorMessage = styled.div`
  color: #e74c3c;
  font-size: 14px;
  margin-top: 4px;
`;

const HelperText = styled.div`
  color: #666;
  font-size: 12px;
  margin-top: 4px;
  font-style: italic;
`;