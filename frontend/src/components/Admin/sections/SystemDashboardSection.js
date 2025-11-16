import React from 'react';
import styled from 'styled-components';

const SystemDashboardSection = () => {
  return (
    <Wrapper>
      <Header>
        <Title>전체 시스템 대시보드</Title>
        <Subtitle>일/주/월 기준 주요 지표를 한눈에 확인합니다.</Subtitle>
      </Header>
      <Grid>
        <MetricCard>
          <MetricLabel>신규 가입자 수</MetricLabel>
          <MetricValue>-</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>실종 게시글 수</MetricLabel>
          <MetricValue>-</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>신고 수 (미처리 / 처리 완료)</MetricLabel>
          <MetricValue>-</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>인기 게시글 TOP 5</MetricLabel>
          <MetricValue>-</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>새 케어 요청 수</MetricLabel>
          <MetricValue>-</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>산책 모임 생성 수</MetricLabel>
          <MetricValue>-</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>장소 리뷰 신규 수</MetricLabel>
          <MetricValue>-</MetricValue>
        </MetricCard>
      </Grid>
      <HintText>
        1단계에서는 카드 레이아웃만 구성합니다. 이후 실제 통계 API 연동과 기간 선택, 그래프/딥링크를 추가합니다.
      </HintText>
    </Wrapper>
  );
};

export default SystemDashboardSection;

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

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: ${props => props.theme.spacing.lg};
`;

const MetricCard = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  padding: ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surfaceSoft};
  border: 1px solid ${props => props.theme.colors.border};
`;

const MetricLabel = styled.div`
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const MetricValue = styled.div`
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
`;

const HintText = styled.p`
  margin-top: ${props => props.theme.spacing.lg};
  color: ${props => props.theme.colors.textSecondary};
`;


