import React from 'react';
import styled from 'styled-components';

const LocationServiceManagementSection = () => {
  return (
    <Wrapper>
      <Header>
        <Title>지역 서비스 관리</Title>
        <Subtitle>등록된 장소, 리뷰, 외부 API 캐시를 관리합니다.</Subtitle>
      </Header>
      <PlaceholderCard>
        <PlaceholderTitle>장소 목록</PlaceholderTitle>
        <PlaceholderText>
          1단계에서는 장소 목록/검색용 기본 테이블만 구성합니다. 이후 사진/웹사이트/설명/좌표 수정, 리뷰 삭제, 캐시 모니터링 및 수동 삭제 기능을 추가합니다.
        </PlaceholderText>
      </PlaceholderCard>
    </Wrapper>
  );
};

export default LocationServiceManagementSection;

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


