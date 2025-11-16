import React from 'react';
import styled from 'styled-components';

const CommunityManagementSection = () => {
  return (
    <Wrapper>
      <Header>
        <Title>커뮤니티 관리</Title>
        <Subtitle>게시글, 댓글, 리액션 및 인기 게시글 스냅샷을 모니터링합니다.</Subtitle>
      </Header>
      <PlaceholderCard>
        <PlaceholderTitle>게시글 / 댓글 리스트</PlaceholderTitle>
        <PlaceholderText>
          1단계에서는 게시글/댓글용 기본 테이블 구조만 구성합니다. 이후 필터, 상태 변경(ACTIVE → BLINDED), 인기 게시글 통계 화면을 추가합니다.
        </PlaceholderText>
      </PlaceholderCard>
    </Wrapper>
  );
};

export default CommunityManagementSection;

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


