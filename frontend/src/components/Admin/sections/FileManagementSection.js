import React from 'react';
import styled from 'styled-components';

const FileManagementSection = () => {
  return (
    <Wrapper>
      <Header>
        <Title>파일 관리</Title>
        <Subtitle>업로드된 파일들을 조회하고 관리합니다.</Subtitle>
      </Header>
      <PlaceholderCard>
        <PlaceholderTitle>파일 목록</PlaceholderTitle>
        <PlaceholderText>
          1단계에서는 파일 목록용 기본 테이블만 구성합니다. 이후 게시글/댓글/케어 요청과의 연결 정보, 잘못 업로드된 파일 삭제, 고아 파일 정리 기능을 추가합니다.
        </PlaceholderText>
      </PlaceholderCard>
    </Wrapper>
  );
};

export default FileManagementSection;

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


