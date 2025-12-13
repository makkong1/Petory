import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { authApi } from '../../api/authApi';
import NicknameSetup from './NicknameSetup';

const OAuth2Callback = () => {
  const { updateUserProfile } = useAuth();
  const [status, setStatus] = useState('processing'); // 'processing', 'success', 'error', 'nickname-setup'
  const [message, setMessage] = useState('로그인 처리 중...');
  const [needsNickname, setNeedsNickname] = useState(false);

  useEffect(() => {
    const handleCallback = async () => {
      try {
        // URL 파라미터에서 값 추출
        const urlParams = new URLSearchParams(window.location.search);
        const accessToken = urlParams.get('accessToken');
        const refreshToken = urlParams.get('refreshToken');
        const success = urlParams.get('success');
        const error = urlParams.get('error');
        const needsNicknameParam = urlParams.get('needsNickname');

        if (error) {
          setStatus('error');
          setMessage(decodeURIComponent(error));
          setTimeout(() => {
            // 에러 발생 시 로그인 페이지로 리다이렉트
            window.location.href = '/';
          }, 3000);
          return;
        }

        if (success === 'true' && accessToken && refreshToken) {
          // 토큰 저장
          authApi.setToken(accessToken);
          authApi.setRefreshToken(refreshToken);

          // 닉네임 설정이 필요한지 확인
          if (needsNicknameParam === 'true') {
            setNeedsNickname(true);
            setStatus('nickname-setup');
            return;
          }

          // 사용자 정보 가져오기
          try {
            const response = await authApi.validateToken();
            if (response.valid && response.user) {
              // 닉네임이 없으면 닉네임 설정 페이지로
              if (!response.user.nickname || response.user.nickname.trim().length === 0) {
                setNeedsNickname(true);
                setStatus('nickname-setup');
                return;
              }

              // AuthContext의 상태를 직접 업데이트하기 위해 페이지 리로드
              setStatus('success');
              setMessage('로그인 성공! 잠시 후 메인 페이지로 이동합니다...');
              
              // 성공 시 페이지 리로드하여 AuthContext가 토큰을 확인하도록 함
              setTimeout(() => {
                window.location.href = window.location.origin;
              }, 1500);
            } else {
              throw new Error('사용자 정보를 가져올 수 없습니다.');
            }
          } catch (error) {
            console.error('사용자 정보 가져오기 실패:', error);
            setStatus('error');
            setMessage('사용자 정보를 가져오는 중 오류가 발생했습니다.');
            setTimeout(() => {
              window.location.href = window.location.origin;
            }, 3000);
          }
        } else {
          setStatus('error');
          setMessage('로그인 처리 중 오류가 발생했습니다.');
          setTimeout(() => {
            window.location.href = '/';
          }, 3000);
        }
      } catch (error) {
        console.error('OAuth2 콜백 처리 실패:', error);
        setStatus('error');
        setMessage('로그인 처리 중 오류가 발생했습니다.');
        setTimeout(() => {
          window.location.href = '/';
        }, 3000);
      }
    };

    handleCallback();
  }, [updateUserProfile]);

  // 닉네임 설정 완료 시 처리
  const handleNicknameComplete = () => {
    setStatus('success');
    setMessage('닉네임 설정 완료! 잠시 후 메인 페이지로 이동합니다...');
    
    setTimeout(() => {
      window.location.href = window.location.origin;
    }, 1500);
  };

  // 닉네임 설정 페이지 표시
  if (status === 'nickname-setup') {
    return <NicknameSetup onComplete={handleNicknameComplete} />;
  }

  return (
    <CallbackContainer>
      <StatusCard>
        {status === 'processing' && (
          <>
            <Spinner />
            <Message>{message}</Message>
          </>
        )}
        {status === 'success' && (
          <>
            <SuccessIcon>✓</SuccessIcon>
            <Message success>{message}</Message>
          </>
        )}
        {status === 'error' && (
          <>
            <ErrorIcon>✗</ErrorIcon>
            <Message error>{message}</Message>
          </>
        )}
      </StatusCard>
    </CallbackContainer>
  );
};

export default OAuth2Callback;

const CallbackContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 2rem;
`;

const StatusCard = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 3rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  min-width: 300px;
`;

const Spinner = styled.div`
  width: 50px;
  height: 50px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;

  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
`;

const SuccessIcon = styled.div`
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: #28a745;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: bold;
`;

const ErrorIcon = styled.div`
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: #dc3545;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: bold;
`;

const Message = styled.p`
  font-size: 1rem;
  text-align: center;
  color: ${props => {
    if (props.success) return '#28a745';
    if (props.error) return '#dc3545';
    return '#333';
  }};
`;

