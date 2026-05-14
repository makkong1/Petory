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
  const notificationRef = useRef(null);

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

  // 알림 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (notificationRef.current && !notificationRef.current.contains(e.target)) {
        setIsNotificationOpen(false);
      }
    };
    if (isNotificationOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isNotificationOpen]);

  // 알림 조회
  const fetchNotifications = useCallback(async () => {
    if (!user) {
      console.warn('알림 조회 실패: user 정보가 없습니다.');
      return;
    }
    try {
      setLoadingNotifications(true);
      const [notificationsRes, countRes] = await Promise.all([
        notificationApi.getUserNotifications(),
        notificationApi.getUnreadCount(),
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
    if (isNotificationOpen && user) {
      fetchNotifications();
    }
  }, [isNotificationOpen, user, fetchNotifications]);

  // 알림 개수 업데이트 함수
  const updateUnreadCount = useCallback(async () => {
    if (!user) return;
    try {
      const res = await notificationApi.getUnreadCount();
      setUnreadCount(res.data || 0);
    } catch (err) {
      if (err.response?.status !== 401) {
        console.error('알림 개수 조회 실패:', err);
      }
    }
  }, [user]);

  // Server-Sent Events를 통한 실시간 알림 구독
  useEffect(() => {
    if (!user) return;

    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (!token) return;

    let eventSource = null;
    let fallbackInterval = null;

    const connectSSE = () => {
      if (eventSource) {
        eventSource.close();
      }

      eventSource = new EventSource(
        `/api/notifications/stream?token=${encodeURIComponent(token)}`,
        { withCredentials: true }
      );

      eventSource.onopen = () => {
        console.log('SSE 연결 성공');
        if (fallbackInterval) {
          clearInterval(fallbackInterval);
          fallbackInterval = null;
        }
      };

      eventSource.addEventListener('notification', (event) => {
        try {
          const notification = JSON.parse(event.data);
          console.log('새 알림 수신 (SSE):', notification);

          setNotifications((prev) => {
            const exists = prev.some(n => n.idx === notification.idx);
            if (exists) return prev;
            return [notification, ...prev];
          });

          setUnreadCount((prev) => prev + 1);
        } catch (err) {
          console.error('알림 파싱 실패:', err);
        }
      });

      eventSource.addEventListener('unreadCount', (event) => {
        try {
          const count = parseInt(event.data, 10);
          setUnreadCount(count);
        } catch (err) {
          console.error('알림 개수 파싱 실패:', err);
        }
      });

      eventSource.onerror = (error) => {
        console.error('SSE 연결 오류:', error);
        if (!fallbackInterval) {
          console.log('SSE 연결 실패, 폴백 폴링 시작');
          fallbackInterval = setInterval(() => {
            updateUnreadCount();
          }, 300000);
        }
      };
    };

    connectSSE();
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
    if (!user) return;
    try {
      await notificationApi.markAsRead(notificationId);
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
    if (!user) return;
    try {
      await notificationApi.markAllAsRead();
      setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
      setUnreadCount(0);
    } catch (err) {
      console.error('모든 알림 읽음 처리 실패:', err);
    }
  };

  const menuItems = [
    { id: 'home', label: '홈', icon: '🏠' },
    { id: 'unified-map', label: '탐색', icon: '🗺️' },
    { id: 'community', label: '커뮤니티', icon: '💬' },
    { id: 'missing-pets', label: '실종 제보', icon: '🚨' },
    ...(isAdmin ? [
      { id: 'admin', label: '관리자', icon: '🔧' },
    ] : []),
  ];

  const handleNotificationClick = (notification) => {
    if (!notification.isRead) {
      handleMarkAsRead(notification.idx);
    }
    setIsNotificationOpen(false);
    if (notification.relatedType === 'BOARD' && notification.relatedId) {
      setActiveTab('community');
      setTimeout(() => {
        window.dispatchEvent(new CustomEvent('openBoardDetail', {
          detail: { boardId: notification.relatedId }
        }));
      }, 100);
    } else if (notification.relatedType === 'MISSING_PET' && notification.relatedId) {
      setActiveTab('missing-pets');
      setTimeout(() => {
        window.dispatchEvent(new CustomEvent('openMissingPetDetail', {
          detail: { boardId: notification.relatedId }
        }));
      }, 100);
    } else if (notification.relatedType === 'CARE_REQUEST' && notification.relatedId) {
      setActiveTab('unified-map');
    }
  };

  return (
    <>
      {/* 데스크탑 사이드바 */}
      <Sidebar>
        {/* 로고 */}
        <LogoArea onClick={() => setActiveTab('home')}>
          <span className="icon">🐾</span>
          <span>Petory</span>
        </LogoArea>

        {/* 메인 메뉴 */}
        <MenuList>
          {menuItems.map(item => (
            <MenuItem
              key={item.id}
              $active={activeTab === item.id}
              onClick={() => setActiveTab(item.id)}
            >
              <span className="menu-icon">{item.icon}</span>
              <span>{item.label}</span>
            </MenuItem>
          ))}
        </MenuList>

        {/* 하단 영역 */}
        <BottomSection>
          {/* 알림 버튼 */}
          {user && (
            <div ref={notificationRef} style={{ position: 'relative' }}>
              <SidebarActionButton
                type="button"
                onClick={() => {
                  setIsNotificationOpen(prev => !prev);
                  setIsProfileDropdownOpen(false);
                }}
              >
                <span>🔔</span>
                <span>알림</span>
                {unreadCount > 0 && <SidebarBadge>{unreadCount}</SidebarBadge>}
              </SidebarActionButton>
              {isNotificationOpen && (
                <SidebarNotificationDropdown>
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
                          $unread={!notification.isRead}
                          onClick={() => handleNotificationClick(notification)}
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
                </SidebarNotificationDropdown>
              )}
            </div>
          )}

          {/* 다크모드 토글 */}
          <SidebarActionButton type="button" onClick={toggleTheme}>
            <span>{isDarkMode ? '🌙' : '☀️'}</span>
            <span>{isDarkMode ? '다크모드' : '라이트모드'}</span>
          </SidebarActionButton>

          {/* 프로필 영역 */}
          {user && (
            <ProfileSection ref={profileRef}>
              <ProfileButton
                type="button"
                onClick={() => {
                  setIsProfileDropdownOpen(prev => !prev);
                  setIsNotificationOpen(false);
                }}
                $active={isProfileDropdownOpen}
              >
                <span>👤</span>
                <ProfileInfo>
                  <ProfileNicknameText>{user.nickname || '내 정보'}</ProfileNicknameText>
                  <ProfileCoinText>💰 {(user.petCoinBalance ?? 0).toLocaleString()}</ProfileCoinText>
                </ProfileInfo>
              </ProfileButton>
              {isProfileDropdownOpen && (
                <SidebarProfileDropdown>
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
                  <ProfileMenuItem
                    onClick={logout}
                    style={{ borderTop: '1px solid rgba(120,113,108,0.2)', marginTop: 4, paddingTop: 8 }}
                  >
                    ↩ 로그아웃
                  </ProfileMenuItem>
                </SidebarProfileDropdown>
              )}
            </ProfileSection>
          )}
        </BottomSection>
      </Sidebar>

      {/* 모바일 하단 탭바 */}
      <BottomNav>
        {menuItems.slice(0, 4).map(item => (
          <BottomNavItem
            key={item.id}
            $active={activeTab === item.id}
            onClick={() => setActiveTab(item.id)}
          >
            <span>{item.icon}</span>
            <span>{item.label}</span>
          </BottomNavItem>
        ))}
        <BottomNavItem
          $active={false}
          onClick={() => setIsProfileDropdownOpen(prev => !prev)}
        >
          <span>👤</span>
          <span>프로필</span>
        </BottomNavItem>
      </BottomNav>

      {/* 모달들 */}
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

/* ─────────────────────────────────────────────
   사이드바 스타일
───────────────────────────────────────────── */

const Sidebar = styled.nav`
  position: fixed;
  left: 0;
  top: 0;
  width: 240px;
  height: 100vh;
  padding-top: env(safe-area-inset-top, 0px);
  background: ${props => props.theme.colors.surface};
  border-right: 1px solid ${props => props.theme.colors.border};
  z-index: 100;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: visible;

  @media (max-width: 768px) {
    display: none;
  }
`;

const LogoArea = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 16px 16px;
  font-size: 18px;
  font-weight: 700;
  color: ${props => props.theme.colors.primary};
  cursor: pointer;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  flex-shrink: 0;

  .icon {
    font-size: 22px;
  }
`;

const MenuList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  flex: 1;
`;

const MenuItem = styled.button`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  width: 100%;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: ${props => props.theme.typography.body2.fontSize};
  background: ${props => props.$active ? props.theme.colors.primarySoft : 'transparent'};
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.text};
  font-weight: ${props => props.$active ? '600' : '400'};
  text-align: left;

  .menu-icon {
    font-size: 18px;
    flex-shrink: 0;
    width: 22px;
    text-align: center;
  }

  &:hover {
    background: ${props => props.$active ? props.theme.colors.primarySoft : props.theme.colors.surfaceHover};
  }
`;

const BottomSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  flex-shrink: 0;
  border-top: 1px solid ${props => props.theme.colors.border};
`;

const SidebarActionButton = styled.button`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  width: 100%;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  background: transparent;
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  text-align: left;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const SidebarBadge = styled.span`
  position: absolute;
  top: 4px;
  right: 4px;
  background: ${props => props.theme.colors.error || '#ef4444'};
  color: white;
  border-radius: ${props => props.theme.borderRadius.full};
  min-width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  padding: 0 4px;
`;

const slideInDown = keyframes`
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
`;

const SidebarNotificationDropdown = styled.div`
  position: fixed;
  top: 0;
  left: 240px;
  bottom: auto;
  right: auto;
  width: 360px;
  max-width: calc(100vw - 256px);
  max-height: 450px;
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  border-radius: ${props => props.theme.borderRadius?.lg || '8px'};
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  z-index: 200;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: ${slideInDown} 0.2s ease-out;

  @media (max-width: 768px) {
    top: auto;
    left: 8px;
    right: 8px;
    bottom: calc(60px + env(safe-area-inset-bottom, 0px) + 8px);
    width: auto;
  }
`;

const ProfileSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0;
  position: relative;
`;

const ProfileButton = styled.button`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  width: 100%;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  background: ${props => props.$active ? props.theme.colors.surfaceHover : 'transparent'};
  color: ${props => props.theme.colors.text};
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const ProfileInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
  flex: 1;
`;

const ProfileNicknameText = styled.div`
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const ProfileCoinText = styled.div`
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.primary};
`;

const SidebarProfileDropdown = styled.div`
  position: fixed;
  bottom: 8px;
  left: 248px;
  top: auto;
  right: auto;
  width: 220px;
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border: 1px solid ${props => props.theme.colors.border || '#e0e0e0'};
  border-radius: ${props => props.theme.borderRadius?.lg || '12px'};
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 200;
  overflow: hidden;
  animation: ${slideInDown} 0.2s ease-out;

  @media (max-width: 768px) {
    bottom: calc(60px + env(safe-area-inset-bottom, 0px) + 8px);
    left: 8px;
    right: 8px;
    width: auto;
    top: auto;
  }
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


/* ─────────────────────────────────────────────
   모바일 하단 탭바
───────────────────────────────────────────── */

const BottomNav = styled.nav`
  display: none;

  @media (max-width: 768px) {
    display: flex;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: calc(60px + env(safe-area-inset-bottom, 0px));
    padding-bottom: env(safe-area-inset-bottom, 0px);
    background: ${props => props.theme.colors.surface};
    border-top: 1px solid ${props => props.theme.colors.border};
    z-index: 100;
    align-items: stretch;
  }
`;

const BottomNavItem = styled.button`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex: 1;
  border: none;
  background: transparent;
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.textSecondary};
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s ease;

  span:first-child {
    font-size: 20px;
  }

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
  }
`;

/* ─────────────────────────────────────────────
   알림 공통 스타일
───────────────────────────────────────────── */

const NotificationHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${props => props.theme.spacing.md};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  flex-shrink: 0;
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
  flex: 1;
`;

const NotificationItem = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${props => props.theme.spacing.sm};
  padding: ${props => props.theme.spacing.md};
  border-bottom: 1px solid ${props => props.theme.colors.borderLight};
  cursor: pointer;
  transition: background 0.2s ease;
  background: ${props => props.$unread ? props.theme.colors.surfaceElevated || props.theme.colors.surface : props.theme.colors.surface};

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
