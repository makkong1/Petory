import React, { useState } from 'react';
import styled from 'styled-components';
import { userProfileApi } from '../../api/userApi';
import { useAuth } from '../../contexts/AuthContext';

const NicknameSetup = ({ onComplete }) => {
  const { updateUserProfile } = useAuth();
  const [nickname, setNickname] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [nicknameCheck, setNicknameCheck] = useState({ checking: false, available: null, message: '' });

  const handleNicknameChange = (e) => {
    const value = e.target.value;
    setNickname(value);
    setError('');
    setNicknameCheck({ checking: false, available: null, message: '' });
  };

  const handleNicknameCheck = async () => {
    if (!nickname || nickname.trim().length === 0) {
      setNicknameCheck({ checking: false, available: false, message: '닉네임을 입력해주세요.' });
      return;
    }

    if (nickname.length > 50) {
      setNicknameCheck({ checking: false, available: false, message: '닉네임은 50자 이하여야 합니다.' });
      return;
    }

    setNicknameCheck({ checking: true, available: null, message: '확인 중...' });

    try {
      const response = await userProfileApi.checkNicknameAvailability(nickname);
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

    if (!nickname || nickname.trim().length === 0) {
      setError('닉네임을 입력해주세요.');
      return;
    }

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

    try {
      await userProfileApi.setNickname(nickname);
      
      // 사용자 정보 업데이트
      if (updateUserProfile) {
        await updateUserProfile();
      }
      
      // 완료 콜백 호출
      if (onComplete) {
        onComplete();
      }
    } catch (error) {
      console.error('닉네임 설정 실패:', error);
      setError(error.response?.data?.error || '닉네임 설정 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container>
      <Card>
        <Title>닉네임 설정</Title>
        <Description>
          서비스를 이용하기 위해 닉네임을 설정해주세요.
        </Description>

        <Form onSubmit={handleSubmit}>
          <InputGroup>
            <Label htmlFor="nickname">닉네임 *</Label>
            <NicknameInputGroup>
              <Input
                type="text"
                id="nickname"
                value={nickname}
                onChange={handleNicknameChange}
                placeholder="닉네임을 입력하세요"
                maxLength={50}
                disabled={loading}
                required
              />
              <CheckButton 
                type="button" 
                onClick={handleNicknameCheck}
                disabled={loading || nicknameCheck.checking || !nickname}
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

          {error && <ErrorMessage>{error}</ErrorMessage>}

          <Button type="submit" disabled={loading || !nicknameCheck.available}>
            {loading ? '설정 중...' : '닉네임 설정 완료'}
          </Button>
        </Form>
      </Card>
    </Container>
  );
};

export default NicknameSetup;

const Container = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 2rem;
  background: ${props => props.theme?.colors?.background || '#f5f5f5'};
`;

const Card = styled.div`
  max-width: 500px;
  width: 100%;
  padding: 2.5rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
`;

const Title = styled.h2`
  text-align: center;
  margin-bottom: 0.5rem;
  color: #333;
`;

const Description = styled.p`
  text-align: center;
  margin-bottom: 2rem;
  color: #666;
  font-size: 0.95rem;
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

const NicknameInputGroup = styled.div`
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
`;

const Input = styled.input`
  flex: 1;
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
  }
  
  &:disabled {
    background: #f5f5f5;
    cursor: not-allowed;
  }
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
  margin-top: 0.5rem;
  
  &:hover:not(:disabled) {
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

