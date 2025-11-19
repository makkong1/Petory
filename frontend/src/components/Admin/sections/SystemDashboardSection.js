import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { adminApi } from '../../../api/adminApi';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  BarChart,
  Bar
} from 'recharts';

const SystemDashboardSection = () => {
  const [stats, setStats] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [summary, setSummary] = useState({
    newUsers: 0,
    newPosts: 0,
    newCareRequests: 0,
    activeUsers: 0,
    totalRevenue: 0
  });

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      setLoading(true);
      // 기본적으로 최근 30일 데이터 조회
      const data = await adminApi.fetchDailyStatistics();
      setStats(data);
      
      // 오늘(또는 가장 최근) 데이터로 요약 정보 업데이트
      if (data.length > 0) {
        const latest = data[data.length - 1];
        setSummary({
          newUsers: latest.newUsers,
          newPosts: latest.newPosts,
          newCareRequests: latest.newCareRequests,
          activeUsers: latest.activeUsers,
          totalRevenue: latest.totalRevenue
        });
      }
    } catch (err) {
      console.error('Failed to fetch dashboard stats:', err);
      setError('통계 데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <LoadingMessage>데이터를 불러오는 중...</LoadingMessage>;
  if (error) return <ErrorMessage>{error}</ErrorMessage>;

  return (
    <Wrapper>
      <Header>
        <Title>전체 시스템 대시보드</Title>
        <Subtitle>일/주/월 기준 주요 지표를 한눈에 확인합니다.</Subtitle>
      </Header>

      {/* 1. 상단 요약 카드 */}
      <Grid>
        <MetricCard>
          <MetricLabel>신규 가입자 (오늘)</MetricLabel>
          <MetricValue>{summary.newUsers}명</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>활성 사용자 (DAU)</MetricLabel>
          <MetricValue>{summary.activeUsers}명</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>새 게시글</MetricLabel>
          <MetricValue>{summary.newPosts}개</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>새 케어 요청</MetricLabel>
          <MetricValue>{summary.newCareRequests}건</MetricValue>
        </MetricCard>
        <MetricCard>
          <MetricLabel>오늘 매출 (예상)</MetricLabel>
          <MetricValue>₩ {summary.totalRevenue.toLocaleString()}</MetricValue>
        </MetricCard>
      </Grid>

      {/* 2. 중단 차트 영역 */}
      <ChartSection>
        <ChartContainer>
          <ChartTitle>서비스 성장 추이 (최근 30일)</ChartTitle>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={stats}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="statDate" />
              <YAxis yAxisId="left" />
              <YAxis yAxisId="right" orientation="right" />
              <Tooltip />
              <Legend />
              <Line yAxisId="left" type="monotone" dataKey="newUsers" name="신규 가입" stroke="#8884d8" activeDot={{ r: 8 }} />
              <Line yAxisId="right" type="monotone" dataKey="activeUsers" name="활성 유저" stroke="#82ca9d" />
            </LineChart>
          </ResponsiveContainer>
        </ChartContainer>

        <ChartContainer>
          <ChartTitle>서비스 활성화 (최근 30일)</ChartTitle>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={stats}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="statDate" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Bar dataKey="newPosts" name="게시글" stackId="a" fill="#8884d8" />
              <Bar dataKey="newCareRequests" name="케어 요청" stackId="a" fill="#ffc658" />
            </BarChart>
          </ResponsiveContainer>
        </ChartContainer>
      </ChartSection>
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
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: ${props => props.theme.spacing.lg};
  margin-bottom: ${props => props.theme.spacing.xl};
`;

const MetricCard = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  padding: ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surfaceSoft};
  border: 1px solid ${props => props.theme.colors.border};
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
`;

const MetricLabel = styled.div`
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const MetricValue = styled.div`
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  color: ${props => props.theme.colors.primary};
`;

const ChartSection = styled.div`
  display: grid;
  grid-template-columns: 1fr;
  gap: ${props => props.theme.spacing.xl};
  
  @media (min-width: 1024px) {
    grid-template-columns: 1fr 1fr;
  }
`;

const ChartContainer = styled.div`
  background: ${props => props.theme.colors.surface};
  padding: ${props => props.theme.spacing.lg};
  border-radius: ${props => props.theme.borderRadius.lg};
  border: 1px solid ${props => props.theme.colors.border};
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
`;

const ChartTitle = styled.h3`
  font-size: ${props => props.theme.typography.h4.fontSize};
  margin-bottom: ${props => props.theme.spacing.lg};
  color: ${props => props.theme.colors.text};
`;

const LoadingMessage = styled.div`
  padding: 2rem;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const ErrorMessage = styled.div`
  padding: 2rem;
  text-align: center;
  color: ${props => props.theme.colors.error};
`;
