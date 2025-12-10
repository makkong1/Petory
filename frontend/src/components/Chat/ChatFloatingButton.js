import React from 'react';
import styled from 'styled-components';

const ChatFloatingButton = ({ onClick, unreadCount = 0 }) => {
  return (
    <FloatingButton onClick={onClick} unreadCount={unreadCount}>
      <ChatIcon>💬</ChatIcon>
      {unreadCount > 0 && <Badge>{unreadCount > 99 ? '99+' : unreadCount}</Badge>}
    </FloatingButton>
  );
};

export default ChatFloatingButton;

const FloatingButton = styled.button`
  position: fixed;
  bottom: 24px;
  left: 24px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: ${({ theme }) => theme.colors.primary};
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  transition: all 0.3s ease;
  
  &:hover {
    transform: scale(1.1);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  }
  
  &:active {
    transform: scale(0.95);
  }
  
  @media (max-width: 768px) {
    bottom: 20px;
    left: 20px;
    width: 52px;
    height: 52px;
  }
`;

const ChatIcon = styled.span`
  font-size: 24px;
  line-height: 1;
`;

const Badge = styled.span`
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: ${({ theme }) => theme.colors.error};
  color: white;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
`;

