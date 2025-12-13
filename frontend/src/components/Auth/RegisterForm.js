import React, { useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { userProfileApi } from '../../api/userApi';

const RegisterForm = ({ onRegisterSuccess, onSwitchToLogin }) => {
  const { register } = useAuth();
  const [formData, setFormData] = useState({
    id: '',
    username: '',
    nickname: '',
    password: '',
    email: '',
    role: 'USER',
    location: '',
    petInfo: ''
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [nicknameCheck, setNicknameCheck] = useState({ checking: false, available: null, message: '' });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
    // 에러 메시지 초기화
    if (error) setError('');
    // 닉네임 변경 시 중복 검사 상태 초기화
    if (name === 'nickname') {
      setNicknameCheck({ checking: false, available: null, message: '' });
    }
  };

  // 닉네임 중복 검사
  const handleNicknameCheck = async () => {
    if (!formData.nickname || formData.nickname.trim().length === 0) {
      setNicknameCheck({ checking: false, available: false, message: '닉네임을 입력해주세요.' });
      return;
    }

    if (formData.nickname.length > 50) {
      setNicknameCheck({ checking: false, available: false, message: '닉네임은 50자 이하여야 합니다.' });
      return;
    }

    setNicknameCheck({ checking: true, available: null, message: '확인 중...' });

    try {
      const response = await userProfileApi.checkNicknameAvailability(formData.nickname);
      setNicknameCheck({
        checking: false,
        available: response.data.available,
        message: response.data.message
      });
    } catch (error) {
      console.error('닉네임 중복 검사 실패:', error);
      setNicknameCheck({
        checking: false,
        available: false,
        message: '닉네임 중복 검사 중 오류가 발생했습니다.'
      });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // 닉네임 필수 검증
    if (!formData.nickname || formData.nickname.trim().length === 0) {
      setError('닉네임은 필수입니다.');
      return;
    }

    // 닉네임 중복 검사 확인
    if (nicknameCheck.available === null) {
      setError('닉네임 중복 검사를 먼저 해주세요.');
      return;
    }

    if (!nicknameCheck.available) {
      setError('사용할 수 없는 닉네임입니다.');
      return;
    }

    setLoading(true);
    setError('');
    setSuccess('');

    try {
      const response = await register(formData);
      
      setSuccess('회원가입 성공! 로그인해주세요.');
      
      // 회원가입 성공 시 콜백 호출
      if (onRegisterSuccess) {
        onRegisterSuccess(response.user);
      }
      
    } catch (error) {
      console.error('회원가입 실패:', error);
      setError(error.response?.data?.error || '회원가입 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <RegisterContainer>
      <Title>회원가입</Title>
      
      <Form onSubmit={handleSubmit}>
        <InputGroup>
          <Label htmlFor="id">아이디 *</Label>
          <Input
            type="text"
            id="id"
            name="id"
            value={formData.id}
            onChange={handleChange}
            required
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label htmlFor="username">사용자명 *</Label>
          <Input
            type="text"
            id="username"
            name="username"
            value={formData.username}
            onChange={handleChange}
            required
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label htmlFor="nickname">닉네임 *</Label>
          <NicknameInputGroup>
            <Input
              type="text"
              id="nickname"
              name="nickname"
              value={formData.nickname}
              onChange={handleChange}
              required
              disabled={loading}
              placeholder="닉네임을 입력하세요"
              maxLength={50}
            />
            <CheckButton 
              type="button" 
              onClick={handleNicknameCheck}
              disabled={loading || nicknameCheck.checking || !formData.nickname}
            >
              {nicknameCheck.checking ? '확인 중...' : '중복 확인'}
            </CheckButton>
          </NicknameInputGroup>
          {nicknameCheck.message && (
            <NicknameMessage available={nicknameCheck.available}>
              {nicknameCheck.message}
            </NicknameMessage>
          )}
        </InputGroup>

        <InputGroup>
          <Label htmlFor="password">비밀번호 *</Label>
          <Input
            type="password"
            id="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            required
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label htmlFor="email">이메일 *</Label>
          <Input
            type="email"
            id="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            required
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label htmlFor="role">역할 *</Label>
          <Select
            id="role"
            name="role"
            value={formData.role}
            onChange={handleChange}
            disabled={loading}
            required
          >
            <option value="USER">🐾 일반 사용자 (펫케어 서비스 이용)</option>
            <option value="SERVICE_PROVIDER">🏥 서비스 제공자 (펫케어 서비스 제공)</option>
          </Select>
        </InputGroup>

        <InputGroup>
          <Label htmlFor="location">지역</Label>
          <Input
            type="text"
            id="location"
            name="location"
            value={formData.location}
            onChange={handleChange}
            placeholder="예: 서울시 강남구"
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label htmlFor="petInfo">반려동물 정보</Label>
          <Input
            type="text"
            id="petInfo"
            name="petInfo"
            value={formData.petInfo}
            onChange={handleChange}
            placeholder="예: 강아지, 고양이"
            disabled={loading}
          />
        </InputGroup>

        {error && <ErrorMessage>{error}</ErrorMessage>}
        {success && <SuccessMessage>{success}</SuccessMessage>}

        <Button type="submit" disabled={loading}>
          {loading ? '회원가입 중...' : '회원가입'}
        </Button>
      </Form>

      <LinkText>
        이미 계정이 있으신가요?{' '}
        <a href="#" onClick={(e) => {
          e.preventDefault();
          if (onSwitchToLogin) onSwitchToLogin();
        }}>
          로그인
        </a>
      </LinkText>
    </RegisterContainer>
  );
};

export default RegisterForm;

const RegisterContainer = styled.div`
  max-width: 600px;
  width: 100%;
  margin: 0 auto;
  padding: 2.5rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  
  @media (max-width: 768px) {
    max-width: 90%;
    padding: 2rem;
  }
`;

const Title = styled.h2`
  text-align: center;
  margin-bottom: 2rem;
  color: #333;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 1rem;
`;

const InputGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

const Label = styled.label`
  font-weight: 500;
  color: #555;
`;

const Input = styled.input`
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
    transform: translateY(-1px);
  }
  
  &:hover {
    border-color: #007bff;
  }
`;

const Select = styled.select`
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  background: white;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
    transform: translateY(-1px);
  }
  
  &:hover {
    border-color: #007bff;
  }
`;

const Button = styled.button`
  padding: 0.75rem 1.5rem;
  background: #28a745;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    background: #218838;
    transform: translateY(-1px);
    box-shadow: 0 3px 8px rgba(40, 167, 69, 0.3);
  }
  
  &:disabled {
    background: #6c757d;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }
`;

const ErrorMessage = styled.div`
  color: #dc3545;
  font-size: 0.875rem;
  margin-top: 0.5rem;
`;

const SuccessMessage = styled.div`
  color: #28a745;
  font-size: 0.875rem;
  margin-top: 0.5rem;
`;

const LinkText = styled.p`
  text-align: center;
  margin-top: 1rem;
  color: #666;
  
  a {
    color: #007bff;
    text-decoration: none;
    
    &:hover {
      text-decoration: underline;
    }
  }
`;

const NicknameInputGroup = styled.div`
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
`;

const CheckButton = styled.button`
  padding: 0.75rem 1rem;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
  
  &:hover:not(:disabled) {
    background: #0056b3;
    transform: translateY(-1px);
  }
  
  &:disabled {
    background: #6c757d;
    cursor: not-allowed;
    transform: none;
  }
`;

const NicknameMessage = styled.div`
  font-size: 0.875rem;
  margin-top: 0.25rem;
  color: ${props => props.available ? '#28a745' : '#dc3545'};
`;
