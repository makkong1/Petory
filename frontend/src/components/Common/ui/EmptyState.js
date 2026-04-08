import React from 'react';
import styled from 'styled-components';

const Wrapper = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${({ padding }) => padding || '48px 24px'};
  text-align: center;
  gap: 12px;
`;

const Icon = styled.div`
  font-size: ${({ iconSize }) => iconSize || '48px'};
  line-height: 1;
`;

const Title = styled.p`
  font-size: 15px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin: 0;
`;

const Description = styled.p`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.textLight};
  margin: 0;
  line-height: 1.5;
`;

function EmptyState({ icon, title, description, padding, iconSize, children }) {
  return (
    <Wrapper padding={padding}>
      {icon && <Icon iconSize={iconSize}>{icon}</Icon>}
      {title && <Title>{title}</Title>}
      {description && <Description>{description}</Description>}
      {children}
    </Wrapper>
  );
}

export default EmptyState;
