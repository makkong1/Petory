import React, { useState, useEffect, useCallback, useRef } from 'react';
import styled, { keyframes } from 'styled-components';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import UserProfileModal from '../User/UserProfileModal';
import PetCoinChargePage from '../Payment/PetCoinChargePage';
import PetCoinTransactionListModal from '../Payment/PetCoinTransactionListModal';
import { notificationApi } from '../../api/notificationApi';

const Navigation = ({ activeTab, setActiveTab, user, onNavigateToBoard }) => {
  const { isDarkMode, toggleTheme } = useTheme();
  const { logout, updateUserProfile } = useAuth();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isProfileDropdownOpen, setIsProfileDropdownOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [isChargePageOpen, setIsChargePageOpen] = useState(false);
  const [isTransactionListOpen, setIsTransactionListOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loadingNotifications, setLoadingNotifications] = useState(false);

  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'MASTER');
  const profileRef = useRef(null);

  // 프로필 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (profileRef.current && !profileRef.current.contains(e.target)) {
        setIsProfileDropdownOpen(false);
      }
    };
    if (isProfileDropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isProfileDropdownOpen]);

  // 알림 조회
  const fetchNotifications = useCallback(async () => {
    const userId = user?.idx || user?.id;
    if (!userId) {
      console.warn('알림 조회 실패: user 정보가 없습니다.', user);
      return;
    }
    try {
      setLoadingNotifications(true);
      const [notificationsRes, countRes] = await Promise.all([
        notificationApi.getUserNotifications(userId),
        notificationApi.getUnreadCount(userId),
      ]);
      setNotifications(notificationsRes.data || []);
      setUnreadCount(countRes.data || 0);
    } catch (err) {
      if (err.response?.status !== 401) {
        console.error('알림 조회 실패:', {
          error: err,
          message: err.message,
          response: err.response?.data,
          status: err.response?.status,
          url: err.config?.url
        });
      }
    } finally {
      setLoadingNotifications(false);
    }
  }, [user]);

  // 알림 목록 열 때 조회
  useEffect(() => {
    const userId = user?.idx || user?.id;
    if (isNotificationOpen && userId) {
      fetchNotifications();
    }
  }, [isNotificationOpen, user, fetchNotifications]);
  // 알림 개수 업데이트 함수
  const updateUnreadCount = useCallback(async () => {
    const userId = user?.idx || user?.id;
    if (!userId) return;
    try {
      const res = await notificationApi.getUnreadCount(userId);
      setUnreadCount(res.data || 0);
    } catch (err) {
      if (err.response?.status !== 401) {
        console.error('알림 개수 조회 실패:', err);
      }
    }
  }, [user]);

  // Server-Sent Events를 통한 실시간 알림 구독
  useEffect(() => {
    const userId = user?.idx || user?.id;
    if (!userId) return;

    // 토큰 가져오기
    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (!token) return;

    let eventSource = null;
    let fallbackInterval = null;
    let isConnected = false;

    // SSE 연결 함수
    const connectSSE = () => {
      if (eventSource) {
        eventSource.close();
      }

      // SSE 연결 (토큰을 쿼리 파라미터로 전달)
      eventSource = new EventSource(
        `http://localhost:8080/api/notifications/stream?userId=${userId}&token=${encodeURIComponent(token)}`,
        { withCredentials: true }
      );

      // 연결 성공
      eventSource.onopen = () => {
        console.log('SSE 연결 성공');
        isConnected = true;
        // 연결 성공 시 폴백 폴링 중지
        if (fallbackInterval) {
          clearInterval(fallbackInterval);
          fallbackInterval = null;
        }
      };

      // 알림 수신 시 처리
      eventSource.addEventListener('notification', (event) => {
        try {
          const notification = JSON.parse(event.data);
          console.log('새 알림 수신 (SSE):', notification);

          // 알림 목록에 추가
          setNotifications((prev) => {
            // 중복 제거
            const exists = prev.some(n => n.idx === notification.idx);
            if (exists) return prev;
            return [notification, ...prev];
          });

          // 읽지 않은 알림 개수 증가
          setUnreadCount((prev) => prev + 1);
        } catch (err) {
          console.error('알림 파싱 실패:', err);
        }
      });

      // 읽지 않은 알림 개수 업데이트
      eventSource.addEventListener('unreadCount', (event) => {
        try {
          const count = parseInt(event.data, 10);
          setUnreadCount(count);
        } catch (err) {
          console.error('알림 개수 파싱 실패:', err);
        }
      });

      // 연결 오류 처리
      eventSource.onerror = (error) => {
        console.error('SSE 연결 오류:', error);
        isConnected = false;

        // 연결이 끊어지면 폴백 폴링 시작 (5분마다)
        if (!fallbackInterval) {
          console.log('SSE 연결 실패, 폴백 폴링 시작');
          fallbackInterval = setInterval(() => {
            updateUnreadCount();
          }, 300000); // 5분마다
        }

        // EventSource가 자동으로 재연결을 시도하지만, 
        // 재연결이 실패하면 폴백 폴링이 작동함
      };
    };

    // 초기 연결
    connectSSE();

    // 초기 알림 개수 로드
    updateUnreadCount();

    return () => {
      if (eventSource) {
        eventSource.close();
      }
      if (fallbackInterval) {
        clearInterval(fallbackInterval);
      }
    };
  }, [user, updateUnreadCount]);

  // 알림 읽음 처리
  const handleMarkAsRead = async (notificationId) => {
    const userId = user?.idx || user?.id;
    if (!userId) return;
    try {
      await notificationApi.markAsRead(notificationId, userId);
      setNotifications(prev =>
        prev.map(n => n.idx === notificationId ? { ...n, isRead: true } : n)
      );
      setUnreadCount(prev => Math.max(0, prev - 1));
    } catch (err) {
      console.error('알림 읽음 처리 실패:', err);
    }
  };

  // 모든 알림 읽음 처리
  const handleMarkAllAsRead = async () => {
    const userId = user?.idx || user?.id;
    if (!userId) return;
    try {
      await notificationApi.markAllAsRead(userId);
      setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
      setUnreadCount(0);
    } catch (err) {
      console.error('모든 알림 읽음 처리 실패:', err);
    }
  };

  const menuItems = [
    { id: 'home', label: '홈', icon: '🏠' },
    { id: 'location-services', label: '주변 서비스', icon: '📍' },
    { id: 'care-requests', label: '펫케어 요청', icon: '🐾' },
    { id: 'missing-pets', label: '실종 제보', icon: '🚨' },
    { id: 'meetup', label: '산책 모임', icon: '🐾' },
    { id: 'community', label: '커뮤니티', icon: '💬' },
    ...(isAdmin ? [
      { id: 'admin', label: '관리자', icon: '🔧' },
    ] : []),
  ];

  return (
    <>
      <NavContainer>
        <NavContent>
          <Logo onClick={() => setActiveTab('home')}>
            <span className="icon">🦴</span>
            <span>Petory</span>
          </Logo>

          <NavMenu isOpen={isMobileMenuOpen}>
            {menuItems.map(item => (
              <NavItem
                key={item.id}
                className={activeTab === item.id ? 'active' : ''}
                onClick={() => {
                  setActiveTab(item.id);
                  setIsMobileMenuOpen(false);
                }}
              >
                <span style={{ marginRight: '8px' }}>{item.icon}</span>
                {item.label}
              </NavItem>
            ))}
          </NavMenu>

          <RightSection>
            {user && (
              <>
                <div style={{ position: 'relative' }}>
                  <NotificationButton type="button" onClick={() => setIsNotificationOpen(!isNotificationOpen)}>
                    🔔
                    {unreadCount > 0 && <NotificationBadge>{unreadCount}</NotificationBadge>}
                  </NotificationButton>
                  {isNotificationOpen && (
                    <NotificationDropdown>
                      <NotificationHeader>
                        <NotificationTitle>알림</NotificationTitle>
                        {unreadCount > 0 && (
                          <MarkAllReadButton onClick={handleMarkAllAsRead}>
                            모두 읽음
                          </MarkAllReadButton>
                        )}
                      </NotificationHeader>
                      <NotificationList>
                        {loadingNotifications ? (
                          <NotificationEmpty>알림을 불러오는 중...</NotificationEmpty>
                        ) : notifications.length === 0 ? (
                          <NotificationEmpty>알림이 없습니다</NotificationEmpty>
                        ) : (
                          notifications.map((notification) => (
                            <NotificationItem
                              key={notification.idx}
                              unread={!notification.isRead}
                              onClick={() => {
                                if (!notification.isRead) {
                                  handleMarkAsRead(notification.idx);
                                }
                                setIsNotificationOpen(false);
                                // 관련 게시글로 이동
                                if (notification.relatedType === 'BOARD' && notification.relatedId) {
                                  // 커뮤니티 탭으로 이동
                                  setActiveTab('community');
                                  // 게시글 상세 페이지 열기 (전역 이벤트 사용)
                                  setTimeout(() => {
                                    window.dispatchEvent(new CustomEvent('openBoardDetail', {
                                      detail: { boardId: notification.relatedId }
                                    }));
                                  }, 100); // 탭 전환 후 실행
                                } else if (notification.relatedType === 'MISSING_PET' && notification.relatedId) {
                                  // 실종제보 탭으로 이동
                                  setActiveTab('missing-pets');
                                  // 실종제보 게시글 상세 페이지 열기 (전역 이벤트 사용)
                                  setTimeout(() => {
                                    window.dispatchEvent(new CustomEvent('openMissingPetDetail', {
                                      detail: { boardId: notification.relatedId }
                                    }));
                                  }, 100); // 탭 전환 후 실행
                                } else if (notification.relatedType === 'CARE_REQUEST' && notification.relatedId) {
                                  // 펫케어 요청 탭으로 이동
                                  setActiveTab('care-requests');
                                  // 펫케어 요청 상세 페이지 열기 (전역 이벤트 사용)
                                  setTimeout(() => {
                                    window.dispatchEvent(new CustomEvent('openCareRequestDetail', {
                                      detail: { careRequestId: notification.relatedId }
                                    }));
                                  }, 100); // 탭 전환 후 실행
                                }
                              }}
                            >
                              <NotificationContent>
                                <NotificationTitleText>{notification.title || '알림'}</NotificationTitleText>
                                <NotificationText>{notification.content || ''}</NotificationText>
                                <NotificationTime>
                                  {notification.createdAt
                                    ? new Date(notification.createdAt).toLocaleString('ko-KR')
                                    : '시간 정보 없음'}
                                </NotificationTime>
                              </NotificationContent>
                              {!notification.isRead && <UnreadDot />}
                            </NotificationItem>
                          ))
                        )}
                      </NotificationList>
                    </NotificationDropdown>
                  )}
                </div>
                <ProfileWrapper ref={profileRef}>
                  <UserInfo
                    type="button"
                    onClick={() => {
                      setIsProfileDropdownOpen(prev => !prev);
                      setIsNotificationOpen(false);
                    }}
                    $active={isProfileDropdownOpen}
                  >
                    <span role="img" aria-label="user">👤</span>
                    {user.nickname || '내 정보'}
                  </UserInfo>
                  {isProfileDropdownOpen && (
                    <ProfileDropdown>
                      <ProfileDropdownHeader>
                        <ProfileNickname>{user.nickname || user.username || '내 정보'}</ProfileNickname>
                        <ProfileCoinBalance>
                          💰 {(user.petCoinBalance ?? 0).toLocaleString()} 코인
                        </ProfileCoinBalance>
                      </ProfileDropdownHeader>
                      <ProfileDropdownMenu>
                        <ProfileMenuItem
                          onClick={() => {
                            setIsProfileDropdownOpen(false);
                            setIsProfileOpen(true);
                          }}
                        >
                          👤 프로필보기
                        </ProfileMenuItem>
                        <ProfileMenuItem
                          onClick={() => {
                            setIsProfileDropdownOpen(false);
                            setIsChargePageOpen(true);
                          }}
                        >
                          💰 코인충전
                        </ProfileMenuItem>
                        <ProfileMenuItem
                          onClick={() => {
                            setIsProfileDropdownOpen(false);
                            setIsTransactionListOpen(true);
                          }}
                        >
                          📋 거래내역
                        </ProfileMenuItem>
                        <ProfileMenuItem
                          onClick={() => {
                            setIsProfileDropdownOpen(false);
                            setActiveTab('activity');
                          }}
                        >
                          📌 내활동보기
                        </ProfileMenuItem>
                      </ProfileDropdownMenu>
                    </ProfileDropdown>
                  )}
                </ProfileWrapper>
              </>
            )}

            <ThemeToggle onClick={toggleTheme}>
              {isDarkMode ? '🌙' : '☀️'}
            </ThemeToggle>

            {user && (
              <LogoutButton onClick={logout}>
                로그아웃
              </LogoutButton>
            )}

            <MobileMenuButton onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}>
              ☰
            </MobileMenuButton>
          </RightSection>
        </NavContent>
      </NavContainer>
      {user && (
        <>
          <UserProfileModal
            isOpen={isProfileOpen}
            userId={user.idx}
            onClose={() => setIsProfileOpen(false)}
            onUpdated={(updated) => {
              updateUserProfile?.(updated);
            }}
          />
          {isTransactionListOpen && (
            <PetCoinTransactionListModal onClose={() => setIsTransactionListOpen(false)} />
          )}
          {isChargePageOpen && (
            <PetCoinChargePage
              onClose={() => setIsChargePageOpen(false)}
              onChargeSuccess={(newBalance) => updateUserProfile?.({ petCoinBalance: newBalance })}
            />
          )}
        </>
      )}
    </>
  );
};

export default Navigation;


const NavContainer = styled.nav`
  background: ${props => props.theme.colors.background};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.md} 0;
  position: sticky;
  top: 0;
  z-index: 1000;
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px ${props => props.theme.colors.shadow};
`;

const NavContent = styled.div`
  max-width: 1350px;
  margin: 0 auto;
  padding: 0 ${props => props.theme.spacing.lg};
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const Logo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  color: ${props => props.theme.colors.primary};
  cursor: pointer;
  
  .icon {
    font-size: 22px;
  }
`;

const NavMenu = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'isOpen',
})`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.lg};
  
  @media (max-width: 768px) {
    display: ${props => props.isOpen ? 'flex' : 'none'};
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    background: ${props => props.theme.colors.background};
    flex-direction: column;
    padding: ${props => props.theme.spacing.lg};
    border-top: 1px solid ${props => props.theme.colors.border};
    box-shadow: 0 4px 12px ${props => props.theme.colors.shadow};
  }
`;

const NavItem = styled.a`
  color: ${props => props.theme.colors.text};
  text-decoration: none;
  font-weight: 400;
  font-size: ${props => props.theme.typography.body1.fontSize};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.md};
  transition: all 0.2s ease;
  cursor: pointer;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
  
  &.active {
    background: ${props => props.theme.colors.primary};
    color: white;
  }
`;

const ThemeToggle = styled.button`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
  width: 32px;
  height: 32px;
  border-radius: ${props => props.theme.borderRadius.full};
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 15px;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    transform: scale(1.05);
  }
`;

const MobileMenuButton = styled.button`
  display: none;
  background: none;
  border: none;
  color: ${props => props.theme.colors.text};
  font-size: 16px;
  cursor: pointer;
  padding: ${props => props.theme.spacing.sm};
  
  @media (max-width: 768px) {
    display: block;
  }
`;

const RightSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
  position: relative;
`;

const CoinChargeButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  color: white;
  font-size: ${props => props.theme.typography.body2.fontSize};
  background: ${props => props.theme.colors.primary || '#FF7E36'};
  border: none;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  transition: all 0.2s ease;
  font-weight: 500;

  &:hover {
    background: ${props => props.theme.colors.primaryDark || '#e66a2b'};
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(255, 126, 54, 0.3);
  }

  @media (max-width: 768px) {
    font-size: 12px;
    padding: ${props => props.theme.spacing.xs};
  }
`;

const ProfileWrapper = styled.div`
  position: relative;
`;

const UserInfo = styled.button`
  display: inline-flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  background: ${props => props.$active ? props.theme.colors.surfaceHover : props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
`;

const slideDownAnimation = keyframes`
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

const ProfileDropdown = styled.div`
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 220px;
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  border-radius: ${props => props.theme.borderRadius?.lg || '12px'};
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 1001;
  overflow: hidden;
  animation: ${slideDownAnimation} 0.25s ease-out;
`;

const ProfileDropdownHeader = styled.div`
  padding: ${props => props.theme.spacing.md};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surfaceElevated || props.theme.colors.surface};
`;

const ProfileNickname = styled.div`
  font-weight: 600;
  font-size: ${props => props.theme.typography.body1.fontSize};
  color: ${props => props.theme.colors.text};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const ProfileCoinBalance = styled.div`
  font-size: ${props => props.theme.typography.body2.fontSize};
  color: ${props => props.theme.colors.primary};
  font-weight: 500;
`;

const ProfileDropdownMenu = styled.div`
  padding: ${props => props.theme.spacing.xs};
`;

const ProfileMenuItem = styled.button`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  width: 100%;
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border: none;
  background: none;
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  text-align: left;
  cursor: pointer;
  border-radius: ${props => props.theme.borderRadius.sm};
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
`;

const LogoutButton = styled.button`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: ${props => props.theme.typography.body2.fontSize};
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
`;

const NotificationButton = styled.button`
  position: relative;
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
  width: 32px;
  height: 32px;
  border-radius: ${props => props.theme.borderRadius.full};
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 15px;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    transform: scale(1.05);
  }
`;

const NotificationBadge = styled.span`
  position: absolute;
  top: -4px;
  right: -4px;
  background: ${props => props.theme.colors.error || '#ef4444'};
  color: white;
  border-radius: ${props => props.theme.borderRadius.full};
  min-width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  padding: 0 5px;
`;

const NotificationDropdown = styled.div`
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 360px;
  max-width: 90vw;
  max-height: 450px;
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  border-radius: ${props => props.theme.borderRadius?.lg || '8px'};
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  z-index: 1001;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-top: 4px;
`;

const NotificationHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${props => props.theme.spacing.md};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const NotificationTitle = styled.h3`
  margin: 0;
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const MarkAllReadButton = styled.button`
  background: none;
  border: none;
  color: ${props => props.theme.colors.primary};
  font-size: 12px;
  cursor: pointer;
  padding: ${props => props.theme.spacing.xs};
  
  &:hover {
    text-decoration: underline;
  }
`;

const NotificationList = styled.div`
  overflow-y: auto;
  max-height: 360px;
`;

const NotificationItem = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${props => props.theme.spacing.sm};
  padding: ${props => props.theme.spacing.md};
  border-bottom: 1px solid ${props => props.theme.colors.borderLight};
  cursor: pointer;
  transition: background 0.2s ease;
  background: ${props => props.unread ? props.theme.colors.surfaceElevated || props.theme.colors.surface : props.theme.colors.surface};
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
  
  &:last-child {
    border-bottom: none;
  }
`;

const NotificationContent = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.xs};
`;

const NotificationTitleText = styled.div`
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const NotificationText = styled.div`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  line-height: 1.5;
`;

const NotificationTime = styled.div`
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.caption.fontSize};
  margin-top: ${props => props.theme.spacing.xs};
`;

const UnreadDot = styled.div`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${props => props.theme.colors.primary};
  margin-top: ${props => props.theme.spacing.xs};
  flex-shrink: 0;
`;

const NotificationEmpty = styled.div`
  padding: ${props => props.theme.spacing.xl};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
`;
