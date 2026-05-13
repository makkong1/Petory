import React, { useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { authApi } from '../../api/authApi';
import { isDemoMode } from '../../mock/isDemoMode';

const LoginForm = ({ onSwitchToRegister }) => {
  const { login } = useAuth();
  const [formData, setFormData] = useState({
    id: '',
    password: ''
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [forgotPasswordEmail, setForgotPasswordEmail] = useState('');
  const [forgotPasswordLoading, setForgotPasswordLoading] = useState(false);
  const [forgotPasswordError, setForgotPasswordError] = useState('');
  const [forgotPasswordSuccess, setForgotPasswordSuccess] = useState('');

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
    // 에러 메시지 초기화
    if (error) setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await login(formData.id, formData.password);

      setSuccess('로그인 성공!');
      
    } catch (error) {
      console.error('로그인 실패:', error);
      setError(error.response?.data?.error || '로그인 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleSocialLogin = (provider) => {
    // OAuth2 로그인 시작 - Spring Boot 서버로 리다이렉트
    window.location.href = `http://localhost:8080/oauth2/authorization/${provider}`;
  };

  const handleForgotPassword = async (e) => {
    e.preventDefault();
    if (!forgotPasswordEmail || forgotPasswordEmail.trim().length === 0) {
      setForgotPasswordError('이메일을 입력해주세요.');
      return;
    }

    setForgotPasswordLoading(true);
    setForgotPasswordError('');
    setForgotPasswordSuccess('');

    try {
      // 비밀번호 재설정 이메일 발송
      const forgotResponse = await authApi.forgotPassword(forgotPasswordEmail);

      if (forgotResponse.success) {
        setForgotPasswordSuccess(forgotResponse.message || '비밀번호 재설정 링크가 이메일로 발송되었습니다. 이메일을 확인해주세요.');
        setForgotPasswordEmail('');
      } else {
        setForgotPasswordError(forgotResponse.message || '비밀번호 재설정 이메일 발송에 실패했습니다.');
      }
    } catch (error) {
      console.error('비밀번호 찾기 실패:', error);
      setForgotPasswordError(error.response?.data?.message || '비밀번호 재설정 이메일 발송에 실패했습니다.');
    } finally {
      setForgotPasswordLoading(false);
    }
  };

  return (
    <AuthPageWrapper>
      <GlassCard>
        <BrandHeader>
          <BrandIcon>🐾</BrandIcon>
          <BrandTitle>Petory</BrandTitle>
          <BrandSubtitle>반려동물과 함께하는 커뮤니티</BrandSubtitle>
        </BrandHeader>

        <Title>로그인</Title>
        {isDemoMode() && (
          <DemoHint>데모 모드: 아무 아이디/비밀번호로 로그인 가능</DemoHint>
        )}
        <Form onSubmit={handleSubmit}>
        <InputGroup>
          <Label htmlFor="id">아이디</Label>
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
          <Label htmlFor="password">비밀번호</Label>
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

        <ForgotPasswordLink>
          <button type="button" onClick={() => setShowForgotPassword(true)}>
            비밀번호 찾기
          </button>
        </ForgotPasswordLink>

        {error && <ErrorMessage>{error}</ErrorMessage>}
        {success && <SuccessMessage>{success}</SuccessMessage>}

        <Button type="submit" disabled={loading}>
          {loading ? '로그인 중...' : '로그인'}
        </Button>
      </Form>

      {!isDemoMode() && (
        <>
          <Divider>
            <DividerLine />
            <DividerText>또는</DividerText>
            <DividerLine />
          </Divider>

          <SocialLoginContainer>
            <SocialButton 
              type="button"
              onClick={() => handleSocialLogin('google')}
              google
            >
              <SocialIcon>G</SocialIcon>
              Google로 로그인
            </SocialButton>
            
            <SocialButton 
              type="button"
              onClick={() => handleSocialLogin('naver')}
              naver
            >
              <SocialIcon>N</SocialIcon>
              Naver로 로그인
            </SocialButton>
          </SocialLoginContainer>
        </>
      )}

      <LinkTextContainer>
        <LinkText>계정이 없으신가요?</LinkText>
        <SecondaryButton type="button" onClick={() => { if (onSwitchToRegister) onSwitchToRegister(); }}>
          회원가입
        </SecondaryButton>
      </LinkTextContainer>
      </GlassCard>

      {showForgotPassword && (
        <ForgotPasswordModal aria-hidden="true">
          <GlassCard as="div" role="dialog" aria-modal="true" aria-label="비밀번호 찾기">
            <ForgotPasswordTitle>비밀번호 찾기</ForgotPasswordTitle>
            <ForgotPasswordForm onSubmit={handleForgotPassword}>
              <InputGroup>
                <Label htmlFor="forgotPasswordEmail">이메일</Label>
                <Input
                  type="email"
                  id="forgotPasswordEmail"
                  value={forgotPasswordEmail}
                  onChange={(e) => {
                    setForgotPasswordEmail(e.target.value);
                    setForgotPasswordError('');
                  }}
                  placeholder="가입하신 이메일을 입력하세요"
                  required
                  disabled={forgotPasswordLoading}
                />
              </InputGroup>

              {forgotPasswordError && <ErrorMessage>{forgotPasswordError}</ErrorMessage>}
              {forgotPasswordSuccess && <SuccessMessage>{forgotPasswordSuccess}</SuccessMessage>}

              <ButtonGroup>
                <Button type="submit" disabled={forgotPasswordLoading}>
                  {forgotPasswordLoading ? '발송 중...' : '비밀번호 재설정 링크 보내기'}
                </Button>
                <CancelButton
                  type="button"
                  onClick={() => {
                    setShowForgotPassword(false);
                    setForgotPasswordEmail('');
                    setForgotPasswordError('');
                    setForgotPasswordSuccess('');
                  }}
                  disabled={forgotPasswordLoading}
                >
                  취소
                </CancelButton>
              </ButtonGroup>
            </ForgotPasswordForm>
          </GlassCard>
        </ForgotPasswordModal>
      )}
    </AuthPageWrapper>
  );
};

export default LoginForm;

const AuthPageWrapper = styled.div`
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 40%, #3D8B7A 100%);
  padding: 24px 16px;
`;

const GlassCard = styled.div`
  width: 100%;
  max-width: 440px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 40px 32px;
  box-shadow: 0 20px 60px rgba(28, 25, 23, 0.2);

  @media (prefers-color-scheme: dark) {
    background: rgba(29, 29, 29, 0.92);
  }
`;

const BrandHeader = styled.div`
  text-align: center;
  margin-bottom: 32px;
`;

const BrandIcon = styled.div`
  font-size: 48px;
  margin-bottom: 8px;
`;

const BrandTitle = styled.h1`
  font-size: 28px;
  font-weight: 700;
  color: ${props => props.theme.colors.primary};
  margin: 0 0 4px;
`;

const BrandSubtitle = styled.p`
  font-size: 14px;
  color: ${props => props.theme.colors.textSecondary};
  margin: 0;
`;

const Title = styled.h2`
  text-align: center;
  margin-bottom: 2rem;
  color: ${({ theme }) => theme.colors.text};
  font-size: ${({ theme }) => theme.typography.h2.fontSize};
  font-weight: ${({ theme }) => theme.typography.h2.fontWeight};
`;

const DemoHint = styled.div`
  text-align: center;
  margin-bottom: 1rem;
  padding: 0.5rem;
  background: ${({ theme }) => theme.colors.success}18;
  color: ${({ theme }) => theme.colors.successDark};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: 0.875rem;
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

const Input = styled.input`
  width: 100%;
  padding: 14px 18px;
  border: 1.5px solid ${props => props.theme.colors.border};
  border-radius: 50px;
  font-size: 15px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  outline: none;
  transition: border-color 0.2s ease;
  box-sizing: border-box;

  &:focus {
    border-color: ${props => props.theme.colors.primary};
  }

  &::placeholder {
    color: ${props => props.theme.colors.textMuted};
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }
`;

const Button = styled.button`
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%);
  color: white;
  border: none;
  border-radius: 50px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.1s ease;
  margin-top: 8px;

  &:hover {
    opacity: 0.9;
  }

  &:active {
    transform: scale(0.98);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const ErrorMessage = styled.div`
  color: ${({ theme }) => theme.colors.error};
  font-size: 13px;
  margin-top: 4px;
`;

const SuccessMessage = styled.div`
  color: ${({ theme }) => theme.colors.success};
  font-size: 13px;
  margin-top: 4px;
`;

const Divider = styled.div`
  display: flex;
  align-items: center;
  margin: ${({ theme }) => theme.spacing.xl} 0;
  gap: ${({ theme }) => theme.spacing.md};
`;

const DividerLine = styled.div`
  flex: 1;
  height: 1px;
  background: ${({ theme }) => theme.colors.border};
`;

const DividerText = styled.span`
  color: ${({ theme }) => theme.colors.textLight};
  font-size: 13px;
`;

const SocialLoginContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
  margin-top: ${({ theme }) => theme.spacing.md};
`;

const SocialButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 11px 20px;
  border: 2px solid ${props => {
    if (props.google) return '#4285F4';
    if (props.naver) return '#03C75A';
    return props.theme.colors.border;
  }};
  background: ${props => {
    if (props.google) return '#4285F4';
    if (props.naver) return '#03C75A';
    return props.theme.colors.background;
  }};
  color: #fff;
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: ${({ theme }) => theme.shadows.md};
    opacity: 0.9;
  }

  &:active {
    transform: translateY(0);
  }
`;

const SocialIcon = styled.span`
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  border-radius: ${({ theme }) => theme.borderRadius.sm};
  font-weight: bold;
  font-size: 13px;
`;

const LinkTextContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: ${({ theme }) => theme.spacing.lg};
  gap: 8px;
`;

const LinkText = styled.p`
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 14px;
  margin: 0;
`;

const SecondaryButton = styled.button`
  width: 100%;
  padding: 12px;
  background: transparent;
  color: ${props => props.theme.colors.primary};
  border: 1.5px solid ${props => props.theme.colors.primary};
  border-radius: 50px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.primarySoft};
  }
`;

const ForgotPasswordLink = styled.div`
  text-align: right;
  margin-top: -4px;
  margin-bottom: 4px;

  button {
    background: none;
    border: none;
    color: ${({ theme }) => theme.colors.textSecondary};
    font-size: 13px;
    text-decoration: none;
    cursor: pointer;
    padding: 0;

    &:hover {
      color: ${({ theme }) => theme.colors.primary};
      text-decoration: underline;
    }
  }
`;

const ForgotPasswordModal = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ForgotPasswordTitle = styled.h3`
  text-align: center;
  margin-bottom: 1.5rem;
  color: ${({ theme }) => theme.colors.text};
  font-size: ${({ theme }) => theme.typography.h3.fontSize};
  font-weight: ${({ theme }) => theme.typography.h3.fontWeight};
`;

const ForgotPasswordForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.lg};
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.md};
  margin-top: 4px;
`;

const CancelButton = styled.button`
  padding: 11px 20px;
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  flex: 1;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.surfaceHover};
    border-color: ${({ theme }) => theme.colors.borderDark};
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
  }
`;