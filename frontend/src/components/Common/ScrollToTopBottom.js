import React, { useState, useEffect } from 'react';
import styled from 'styled-components';

const ScrollToTopBottom = () => {
  const [showTopButton, setShowTopButton] = useState(false);
  const [showBottomButton, setShowBottomButton] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
      const windowHeight = window.innerHeight;
      const documentHeight = document.documentElement.scrollHeight;
      const scrollBottom = documentHeight - (scrollTop + windowHeight);

      // 맨 위로 가기 버튼: 300px 이상 스크롤했을 때 표시
      setShowTopButton(scrollTop > 300);

      // 맨 아래로 가기 버튼: 하단 300px 이내에 있을 때 표시
      setShowBottomButton(scrollBottom > 300);
    };

    window.addEventListener('scroll', handleScroll);
    handleScroll(); // 초기 상태 확인

    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, []);

  const scrollToTop = () => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  };

  const scrollToBottom = () => {
    window.scrollTo({
      top: document.documentElement.scrollHeight,
      behavior: 'smooth'
    });
  };

  // 두 버튼 중 하나라도 보여야 할 때만 컨테이너 표시
  if (!showTopButton && !showBottomButton) {
    return null;
  }

  return (
    <ButtonContainer>
      {showTopButton && (
        <ScrollButton top onClick={scrollToTop} aria-label="맨 위로">
          <ScrollIcon>↑</ScrollIcon>
        </ScrollButton>
      )}
      {showBottomButton && (
        <ScrollButton bottom onClick={scrollToBottom} aria-label="맨 아래로">
          <ScrollIcon>↓</ScrollIcon>
        </ScrollButton>
      )}
    </ButtonContainer>
  );
};

export default ScrollToTopBottom;

const ButtonContainer = styled.div`
  position: fixed;
  bottom: 20px;
  right: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 999;

  @media (max-width: 768px) {
    bottom: 16px;
    right: 16px;
    gap: 6px;
  }
`;

const ScrollButton = styled.button`
  width: 44px;
  height: 44px;
  background: ${props => props.theme.colors.gradient};
  color: white;
  border: none;
  border-radius: ${props => props.theme.borderRadius.full};
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
  font-size: 20px;
  font-weight: bold;

  &:hover {
    transform: translateY(${props => props.top ? '-2px' : '2px'});
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  }

  &:active {
    transform: translateY(0);
  }

  @media (max-width: 768px) {
    width: 40px;
    height: 40px;
    font-size: 18px;
  }
`;

const ScrollIcon = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
`;

