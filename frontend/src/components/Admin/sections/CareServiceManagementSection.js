import React from 'react';
import styled from 'styled-components';

const CareServiceManagementSection = () => {
  return (
    <Wrapper>
      <Header>
        <Title>케어 서비스 관리</Title>
        <Subtitle>케어 요청, 지원자, 후기, 댓글을 모니터링하고 관리합니다.</Subtitle>
      </Header>
      <PlaceholderCard>
        <PlaceholderTitle>케어 요청 리스트</PlaceholderTitle>
        <PlaceholderText>
          1단계에서는 케어 요청 및 지원자 목록용 기본 테이블만 구성합니다. 이후 강제 승인/거절, 후기 삭제, 댓글 관리 기능을 추가합니다.
        </PlaceholderText>
      </PlaceholderCard>
    </Wrapper>
  );
};

export default CareServiceManagementSection;

const Wrapper = styled.div``;

const Header = styled.div`
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const Title = styled.h1`
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const Subtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
`;

const PlaceholderCard = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px dashed ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surfaceSoft};
`;

const PlaceholderTitle = styled.h2`
  font-size: ${props => props.theme.typography.h4.fontSize};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const PlaceholderText = styled.p`
  color: ${props => props.theme.colors.textSecondary};
`;


