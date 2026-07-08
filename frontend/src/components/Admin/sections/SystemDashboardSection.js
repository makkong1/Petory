import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { adminApi } from '../../../api/adminApi';
import { usePermission } from '../../../hooks/usePermission';
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
import {
  SectionHeader, SectionTitle, SectionSubtitle,
  StatGrid, StatCard, StatLabel, StatValue,
} from '../ui/AdminUI';

// 차트 시리즈 색 (새 브랜드 팔레트)
const C_PRIMARY = '#E8714A';
const C_BLUE = '#3B82F6';
const C_GREEN = '#10B981';
const C_RED = '#EF4444';

const SystemDashboardSection = () => {
  const { checkRole } = usePermission();
  const isMaster = checkRole('MASTER');
  const [stats, setStats] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [initLoading, setInitLoading] = useState(false);
  const [initMessage, setInitMessage] = useState(null);
  const [summary, setSummary] = useState({
    newUsers: 0,
    newPosts: 0,
    newCareRequests: 0,
    newMeetups: 0,
    meetupParticipants: 0,
    newReports: 0,
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
          newMeetups: latest.newMeetups ?? 0,
          meetupParticipants: latest.meetupParticipants ?? 0,
          newReports: latest.newReports ?? 0,
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

  const handleInitStatistics = async () => {
    if (!isMaster) return;
    try {
      setInitLoading(true);
      setInitMessage(null);
      const message = await adminApi.initStatistics(30);
      setInitMessage(message);
      await fetchStats();
    } catch (err) {
      console.error('Failed to init statistics:', err);
      setInitMessage(err?.response?.data?.message || '통계 집계에 실패했습니다.');
    } finally {
      setInitLoading(false);
    }
  };

  if (loading) return <LoadingMessage>데이터를 불러오는 중...</LoadingMessage>;
  if (error) return <ErrorMessage>{error}</ErrorMessage>;

  return (
    <Wrapper>
      <SectionHeader>
        <HeaderContent>
          <div>
            <SectionTitle>전체 시스템 대시보드</SectionTitle>
            <SectionSubtitle>일/주/월 기준 주요 지표를 한눈에 확인합니다.</SectionSubtitle>
          </div>
          {isMaster && (
            <InitButton
              onClick={handleInitStatistics}
              disabled={initLoading}
            >
              {initLoading ? '집계 중...' : '통계 수동 집계'}
            </InitButton>
          )}
        </HeaderContent>
        {initMessage && (
          <InitMessage $success={!initMessage.includes('실패')}>
            {initMessage}
          </InitMessage>
        )}
      </SectionHeader>

      {/* 1. 상단 요약 카드 */}
      <StatGrid>
        <StatCard><StatLabel>신규 가입자 (오늘)</StatLabel><StatValue $accent>{summary.newUsers}명</StatValue></StatCard>
        <StatCard><StatLabel>활성 사용자 (DAU)</StatLabel><StatValue $accent>{summary.activeUsers}명</StatValue></StatCard>
        <StatCard><StatLabel>새 게시글</StatLabel><StatValue>{summary.newPosts}개</StatValue></StatCard>
        <StatCard><StatLabel>새 케어 요청</StatLabel><StatValue>{summary.newCareRequests}건</StatValue></StatCard>
        <StatCard><StatLabel>새 모임</StatLabel><StatValue>{summary.newMeetups}개</StatValue></StatCard>
        <StatCard><StatLabel>모임 참여</StatLabel><StatValue>{summary.meetupParticipants}명</StatValue></StatCard>
        <StatCard><StatLabel>신규 신고</StatLabel><StatValue>{summary.newReports}건</StatValue></StatCard>
        <StatCard><StatLabel>오늘 매출 (예상)</StatLabel><StatValue>₩ {(summary.totalRevenue ?? 0).toLocaleString()}</StatValue></StatCard>
      </StatGrid>

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
              <Line yAxisId="left" type="monotone" dataKey="newUsers" name="신규 가입" stroke={C_PRIMARY} activeDot={{ r: 8 }} />
              <Line yAxisId="left" type="monotone" dataKey="newMeetups" name="새 모임" stroke={C_BLUE} />

              <Line yAxisId="right" type="monotone" dataKey="activeUsers" name="활성 유저" stroke={C_GREEN} />
              <Line yAxisId="right" type="monotone" dataKey="newReports" name="신고" stroke={C_RED} />
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
              <Bar dataKey="newPosts" name="게시글" stackId="a" fill={C_PRIMARY} />
              <Bar dataKey="newCareRequests" name="케어 요청" stackId="a" fill={C_BLUE} />
              <Bar dataKey="newMeetups" name="모임" stackId="a" fill={C_GREEN} />
              <Bar dataKey="newReports" name="신고" stackId="a" fill={C_RED} />
            </BarChart>
          </ResponsiveContainer>
        </ChartContainer>
      </ChartSection>
    </Wrapper>
  );
};

export default SystemDashboardSection;

const Wrapper = styled.div``;

const HeaderContent = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: ${props => props.theme.spacing.md};
  flex-wrap: wrap;
`;

const InitButton = styled.button`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;

  &:hover:not(:disabled) {
    opacity: 0.9;
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const InitMessage = styled.div`
  margin-top: ${props => props.theme.spacing.sm};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.caption.fontSize};
  background: ${props => props.$success
    ? 'rgba(34, 197, 94, 0.1)'
    : 'rgba(239, 68, 68, 0.1)'};
  color: ${props => props.$success
    ? props.theme.colors.success || '#16a34a'
    : props.theme.colors.error};
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
