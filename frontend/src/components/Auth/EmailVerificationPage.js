import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { userProfileApi } from '../../api/userApi';

const EmailVerificationPage = () => {
  const [token, setToken] = useState(null);
  const [purpose, setPurpose] = useState(null);
  const [sendingEmail, setSendingEmail] = useState(false);

  const [status, setStatus] = useState('verifying'); // 'verifying', 'success', 'error', 'no-token'
  const [message, setMessage] = useState('');
  const [redirectUrl, setRedirectUrl] = useState('/');

  useEffect(() => {
    // URL에서 토큰 및 purpose 추출
    const urlParams = new URLSearchParams(window.location.search);
    const tokenFromUrl = urlParams.get('token');
    const purposeFromUrl = urlParams.get('purpose');
    const redirectFromUrl = urlParams.get('redirect');
    
    setToken(tokenFromUrl);
    setPurpose(purposeFromUrl);
    
    // URL 파라미터의 redirect를 우선 사용, 없으면 sessionStorage에서 확인, 둘 다 없으면 기본값 '/'
    let initialRedirectUrl = '/';
    if (redirectFromUrl) {
      initialRedirectUrl = decodeURIComponent(redirectFromUrl);
      // sessionStorage에도 저장 (이메일 링크 클릭 시 redirect 정보 유지)
      sessionStorage.setItem('emailVerificationRedirect', initialRedirectUrl);
    } else {
      // sessionStorage에서 redirect URL 확인
      const storedRedirect = sessionStorage.getItem('emailVerificationRedirect');
      if (storedRedirect) {
        initialRedirectUrl = storedRedirect;
      }
    }
    setRedirectUrl(initialRedirectUrl);

    // 토큰이 없으면 이메일 인증 메일 발송 안내 화면 표시
    if (!tokenFromUrl) {
      setStatus('no-token');
      setMessage('이메일 인증이 필요합니다. 아래 버튼을 클릭하여 인증 메일을 발송해주세요.');
      return;
    }

    // 이메일 인증 처리
    userProfileApi.verifyEmail(tokenFromUrl)
      .then((response) => {
        if (response.data.success) {
          setStatus('success');
          setMessage(response.data.message || '이메일 인증이 완료되었습니다.');

          // 회원가입 전 인증인 경우
          if (response.data.email && response.data.redirectUrl && response.data.redirectUrl.includes('/register')) {
            // 회원가입 페이지로 리다이렉트 (이메일 인증 완료 상태로)
            const redirectUrl = response.data.redirectUrl;
            setRedirectUrl(redirectUrl);
            setTimeout(() => {
              window.location.href = redirectUrl;
            }, 2000);
          } else {
            // URL 파라미터의 redirect가 있으면 우선 사용, 없으면 sessionStorage, 둘 다 없으면 API 응답의 redirectUrl 사용
            let finalRedirectUrl = '/';
            if (redirectFromUrl) {
              finalRedirectUrl = decodeURIComponent(redirectFromUrl);
            } else {
              const storedRedirect = sessionStorage.getItem('emailVerificationRedirect');
              if (storedRedirect) {
                finalRedirectUrl = storedRedirect;
              } else {
                finalRedirectUrl = response.data.redirectUrl || '/';
              }
            }
            setRedirectUrl(finalRedirectUrl);
            
            // sessionStorage에서 redirect 정보 제거
            sessionStorage.removeItem('emailVerificationRedirect');

            // 3초 후 리다이렉트
            setTimeout(() => {
              window.location.href = finalRedirectUrl;
            }, 3000);
          }
        } else {
          setStatus('error');
          setMessage(response.data.message || '이메일 인증에 실패했습니다.');
        }
      })
      .catch((error) => {
        setStatus('error');
        const errorMessage = error.response?.data?.message ||
          error.message ||
          '이메일 인증 처리 중 오류가 발생했습니다.';
        setMessage(errorMessage);
      });
  }, []);

  return (
    <Container>
      <Card>
        {status === 'verifying' && (
          <>
            <Icon>✉️</Icon>
            <Title>이메일 인증 중...</Title>
            <Message>잠시만 기다려주세요.</Message>
            <Spinner />
          </>
        )}

        {status === 'success' && (
          <>
            <SuccessIcon>✅</SuccessIcon>
            <Title>인증 완료!</Title>
            <Message>{message}</Message>
            <SubMessage>{redirectUrl}로 이동합니다...</SubMessage>
            <Button onClick={() => window.location.href = redirectUrl}>
              바로 이동하기
            </Button>
          </>
        )}

        {status === 'no-token' && (
          <>
            <Icon>✉️</Icon>
            <Title>이메일 인증이 필요합니다</Title>
            <Message>{message}</Message>
            <Button 
              onClick={async () => {
                setSendingEmail(true);
                try {
                  // 현재 redirect URL을 sessionStorage에 저장 (이메일 링크 클릭 시 유지)
                  if (redirectUrl && redirectUrl !== '/') {
                    sessionStorage.setItem('emailVerificationRedirect', redirectUrl);
                  }
                  
                  await userProfileApi.sendVerificationEmail(purpose || 'MEETUP');
                  setStatus('success');
                  setMessage('이메일 인증 메일이 발송되었습니다. 이메일을 확인해주세요.');
                } catch (error) {
                  setStatus('error');
                  setMessage(error.response?.data?.message || '이메일 발송에 실패했습니다. 다시 시도해주세요.');
                } finally {
                  setSendingEmail(false);
                }
              }}
              disabled={sendingEmail}
            >
              {sendingEmail ? '발송 중...' : '인증 메일 발송'}
            </Button>
            <Button 
              onClick={() => window.location.href = redirectUrl || '/'}
              style={{ marginTop: '10px', background: '#ccc' }}
            >
              나중에 하기
            </Button>
          </>
        )}

        {status === 'error' && (
          <>
            <ErrorIcon>❌</ErrorIcon>
            <Title>인증 실패</Title>
            <Message>{message}</Message>
            <Button onClick={() => window.location.href = redirectUrl || '/'}>
              {redirectUrl ? '이전 페이지로' : '홈으로 이동'}
            </Button>
          </>
        )}
      </Card>
    </Container>
  );
};

export default EmailVerificationPage;

const Container = styled.div`
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.xl};
  background: ${props => props.theme.colors.background};
`;

const Card = styled.div`
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xl};
  max-width: 500px;
  width: 100%;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
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

const Icon = styled.div`
  font-size: 64px;
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const SuccessIcon = styled(Icon)`
  color: #4caf50;
`;

const ErrorIcon = styled(Icon)`
  color: #f44336;
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const Message = styled.p`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body1.fontSize};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const SubMessage = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const Spinner = styled.div`
  border: 4px solid ${props => props.theme.colors.border};
  border-top: 4px solid ${props => props.theme.colors.primary};
  border-radius: 50%;
  width: 40px;
  height: 40px;
  animation: spin 1s linear infinite;
  margin: ${props => props.theme.spacing.lg} auto;
  
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
`;

const Button = styled.button`
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: ${props => props.theme.spacing.md};
  
  &:hover {
    background: ${props => props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }
`;
