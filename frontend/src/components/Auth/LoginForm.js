import React, { useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { authApi } from '../../api/authApi';
import { isDemoMode } from '../../mock/isDemoMode';
import {
  FormHeader,
  FormHeaderLogo,
  FormTitle,
  FormSubtitle,
  PillInput,
  GradientButton,
  SocialButton,
  SocialIcon,
  Divider,
  FormSwitchLink,
} from './AuthShell';

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
    <>
      <FormHeader>
        <FormHeaderLogo>🐾 Petory</FormHeaderLogo>
        <FormTitle>로그인</FormTitle>
        <FormSubtitle>반려동물과 함께하는 커뮤니티</FormSubtitle>
      </FormHeader>

      {isDemoMode() && (
        <DemoHint>데모 모드: 아무 아이디/비밀번호로 로그인 가능</DemoHint>
      )}

      <Form onSubmit={handleSubmit}>
        <InputGroup>
          <Label htmlFor="id">아이디</Label>
          <PillInput
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
          <PillInput
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

        <GradientButton type="submit" disabled={loading}>
          {loading ? '로그인 중...' : '로그인'}
        </GradientButton>
      </Form>

      {!isDemoMode() && (
        <>
          <Divider><span>또는</span></Divider>

          <SocialLoginContainer>
            <SocialButton type="button" onClick={() => handleSocialLogin('google')}>
              <SocialIcon $provider="google">G</SocialIcon>
              Google 로 로그인
            </SocialButton>

            <SocialButton type="button" onClick={() => handleSocialLogin('naver')}>
              <SocialIcon $provider="naver">N</SocialIcon>
              Naver 로 로그인
            </SocialButton>
          </SocialLoginContainer>
        </>
      )}

      <FormSwitchLink>
        계정이 없으신가요?
        <button type="button" onClick={() => { if (onSwitchToRegister) onSwitchToRegister(); }}>
          회원가입
        </button>
      </FormSwitchLink>

      {showForgotPassword && (
        <ForgotSection>
          <ForgotTitle>비밀번호 찾기</ForgotTitle>
          <ForgotPasswordForm onSubmit={handleForgotPassword}>
            <InputGroup>
              <Label htmlFor="forgotPasswordEmail">이메일</Label>
              <PillInput
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
              <GradientButton type="submit" disabled={forgotPasswordLoading}>
                {forgotPasswordLoading ? '발송 중...' : '비밀번호 재설정 링크 보내기'}
              </GradientButton>
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
                뒤로
              </CancelButton>
            </ButtonGroup>
          </ForgotPasswordForm>
        </ForgotSection>
      )}
    </>
  );
};

export default LoginForm;

const DemoHint = styled.div`
  margin-bottom: 16px;
  padding: 10px 14px;
  background: #f0fdf4;
  color: #166534;
  border-radius: 8px;
  font-size: 13px;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const InputGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: #57534e;
`;

const ErrorMessage = styled.div`
  color: #dc2626;
  font-size: 13px;
`;

const SuccessMessage = styled.div`
  color: #16a34a;
  font-size: 13px;
`;

const SocialLoginContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
`;

const ForgotPasswordLink = styled.div`
  text-align: right;

  button {
    background: none;
    border: none;
    color: #a8a29e;
    font-size: 13px;
    cursor: pointer;
    padding: 0;

    &:hover {
      color: #E8714A;
    }
  }
`;

const ForgotSection = styled.div`
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #f0efed;
`;

const ForgotTitle = styled.h3`
  font-size: 16px;
  font-weight: 600;
  color: #1c1917;
  margin: 0 0 16px;
`;

const ForgotPasswordForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 10px;
`;

const CancelButton = styled.button`
  flex: 1;
  padding: 12px;
  background: #f5f5f4;
  color: #57534e;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover:not(:disabled) { background: #e7e5e4; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;