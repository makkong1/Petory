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
  background: ${({ theme }) => theme.colors.surfaceSoft};
`;

const Card = styled.div`
  max-width: 500px;
  width: 100%;
  padding: 2.5rem;
  background: ${({ theme }) => theme.colors.background};
  border-radius: ${({ theme }) => theme.borderRadius.xl};
  box-shadow: ${({ theme }) => theme.shadows.lg};

  @media (max-width: 768px) {
    padding: 1.5rem;
  }
`;

const Title = styled.h2`
  text-align: center;
  margin-bottom: ${({ theme }) => theme.spacing.sm};
  color: ${({ theme }) => theme.colors.text};
  font-size: ${({ theme }) => theme.typography.h2.fontSize};
  font-weight: ${({ theme }) => theme.typography.h2.fontWeight};
`;

const Description = styled.p`
  text-align: center;
  margin-bottom: 2rem;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 14px;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.lg};
`;

const InputGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const Label = styled.label`
  font-weight: 600;
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text};
`;

const NicknameInputGroup = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  align-items: flex-start;
`;

const Input = styled.input`
  flex: 1;
  padding: 10px 14px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  font-size: 14px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  outline: none;

  &::placeholder { color: ${({ theme }) => theme.colors.textLight}; }

  &:focus {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: 0 0 0 3px ${({ theme }) => theme.colors.primary}25;
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    cursor: not-allowed;
  }
`;

const CheckButton = styled.button`
  padding: 10px 14px;
  background: ${({ theme }) => theme.colors.primary};
  color: #fff;
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primaryDark};
    transform: translateY(-1px);
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceHover};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
    transform: none;
  }
`;

const NicknameMessage = styled.div`
  font-size: 13px;
  margin-top: 4px;
  color: ${({ theme, available }) => available ? theme.colors.success : theme.colors.error};
`;

const Button = styled.button`
  padding: 11px 20px;
  background: ${({ theme }) => theme.colors.primary};
  color: #fff;
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: ${({ theme }) => theme.spacing.sm};

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primaryDark};
    transform: translateY(-1px);
    box-shadow: 0 4px 12px ${({ theme }) => theme.colors.primary}40;
  }

  &:active:not(:disabled) { transform: translateY(0); }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceHover};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }
`;

const ErrorMessage = styled.div`
  color: ${({ theme }) => theme.colors.error};
  font-size: 13px;
  margin-top: 4px;
`;

