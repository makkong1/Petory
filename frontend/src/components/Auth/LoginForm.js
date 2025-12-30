import React, { useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { authApi } from '../../api/authApi';

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
      const response = await login(formData.id, formData.password);
      
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
      const response = await authApi.forgotPassword(forgotPasswordEmail);
      
      if (response.success) {
        setForgotPasswordSuccess(response.message || '비밀번호 재설정 링크가 이메일로 발송되었습니다. 이메일을 확인해주세요.');
        setForgotPasswordEmail('');
      } else {
        setForgotPasswordError(response.message || '비밀번호 재설정 이메일 발송에 실패했습니다.');
      }
    } catch (error) {
      console.error('비밀번호 찾기 실패:', error);
      setForgotPasswordError(error.response?.data?.message || '비밀번호 재설정 이메일 발송에 실패했습니다.');
    } finally {
      setForgotPasswordLoading(false);
    }
  };

  return (
    <LoginContainer>
      <Title>로그인</Title>
      
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
          <a href="#" onClick={(e) => {
            e.preventDefault();
            setShowForgotPassword(true);
          }}>
            비밀번호 찾기
          </a>
        </ForgotPasswordLink>

        {error && <ErrorMessage>{error}</ErrorMessage>}
        {success && <SuccessMessage>{success}</SuccessMessage>}

        <Button type="submit" disabled={loading}>
          {loading ? '로그인 중...' : '로그인'}
        </Button>
      </Form>

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

      <LinkText>
        계정이 없으신가요?{' '}
        <a href="#" onClick={(e) => {
          e.preventDefault();
          if (onSwitchToRegister) onSwitchToRegister();
        }}>
          회원가입
        </a>
      </LinkText>

      {showForgotPassword && (
        <ForgotPasswordModal>
          <ForgotPasswordContent>
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
          </ForgotPasswordContent>
        </ForgotPasswordModal>
      )}
    </LoginContainer>
  );
};

export default LoginForm;

const LoginContainer = styled.div`
  max-width: 500px;
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

const Button = styled.button`
  padding: 0.75rem 1.5rem;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    background: #0056b3;
    transform: translateY(-1px);
    box-shadow: 0 3px 8px rgba(0, 123, 255, 0.3);
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

const Divider = styled.div`
  display: flex;
  align-items: center;
  margin: 1.5rem 0;
  gap: 1rem;
`;

const DividerLine = styled.div`
  flex: 1;
  height: 1px;
  background: #e1e5e9;
`;

const DividerText = styled.span`
  color: #666;
  font-size: 0.875rem;
`;

const SocialLoginContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 1rem;
`;

const SocialButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  border: 2px solid ${props => {
    if (props.google) return '#4285F4';
    if (props.naver) return '#03C75A';
    return '#e1e5e9';
  }};
  background: ${props => {
    if (props.google) return '#4285F4';
    if (props.naver) return '#03C75A';
    return 'white';
  }};
  color: white;
  border-radius: 6px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 3px 8px rgba(0, 0, 0, 0.2);
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
  border-radius: 4px;
  font-weight: bold;
  font-size: 0.875rem;
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

const ForgotPasswordLink = styled.div`
  text-align: right;
  margin-top: -0.5rem;
  margin-bottom: 0.5rem;
  
  a {
    color: #666;
    font-size: 0.875rem;
    text-decoration: none;
    
    &:hover {
      color: #007bff;
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
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
`;

const ForgotPasswordContent = styled.div`
  background: white;
  border-radius: 12px;
  padding: 2rem;
  max-width: 500px;
  width: 90%;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
`;

const ForgotPasswordTitle = styled.h3`
  text-align: center;
  margin-bottom: 1.5rem;
  color: #333;
`;

const ForgotPasswordForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: 1rem;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 0.75rem;
  margin-top: 0.5rem;
`;

const CancelButton = styled.button`
  padding: 0.75rem 1.5rem;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  flex: 1;
  
  &:hover:not(:disabled) {
    background: #5a6268;
    transform: translateY(-1px);
    box-shadow: 0 3px 8px rgba(108, 117, 125, 0.3);
  }
  
  &:disabled {
    background: #6c757d;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
    opacity: 0.6;
  }
`;