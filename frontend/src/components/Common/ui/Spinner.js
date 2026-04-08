import React from 'react';
import styled, { keyframes } from 'styled-components';

const spin = keyframes`
  to { transform: rotate(360deg); }
`;

const SpinnerRing = styled.div`
  display: inline-block;
  width: ${({ size }) => size || '24px'};
  height: ${({ size }) => size || '24px'};
  border: 2.5px solid ${({ theme }) => theme.colors.border};
  border-top-color: ${({ theme, color }) => color || theme.colors.primary};
  border-radius: 50%;
  animation: ${spin} 0.7s linear infinite;
`;

const SpinnerWrapper = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: ${({ padding }) => padding || '20px'};
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 14px;
`;

function Spinner({ size, text, padding, color }) {
  return (
    <SpinnerWrapper padding={padding}>
      <SpinnerRing size={size} color={color} />
      {text && <span>{text}</span>}
    </SpinnerWrapper>
  );
}

export default Spinner;
