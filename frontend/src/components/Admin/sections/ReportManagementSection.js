import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { reportApi } from '../../../api/reportApi';

const ReportManagementSection = () => {
  const [activeStatus, setActiveStatus] = useState('PENDING');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reports, setReports] = useState([]);

  const statusTabs = [
    { key: 'ALL', label: '전체' },
    { key: 'PENDING', label: '미처리(PENDING)' },
    { key: 'RESOLVED', label: '처리완료(RESOLVED)' },
    { key: 'REJECTED', label: '반려(REJECTED)' },
  ];

  // 게시글(BOARD) 신고 기준으로만 가져오되, 상태는 탭에 따라 변경
  useEffect(() => {
    const fetchReports = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await reportApi.getReports({
          targetType: 'BOARD',
          status: activeStatus,
        });
        setReports(response.data || []);
      } catch (err) {
        console.error('신고 목록 조회 실패:', err);
        setError('신고 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchReports();
  }, [activeStatus]);

  return (
    <Wrapper>
      <Header>
        <Title>신고 관리</Title>
        <Subtitle>
          신고 테이블의 <Code>status</Code> 컬럼(PENDING / RESOLVED / REJECTED)을 기준으로 신고를 관리합니다.
        </Subtitle>
      </Header>

      <TabsWrapper>
        {statusTabs.map(tab => (
          <StatusTab
            key={tab.key}
            $active={tab.key === activeStatus}
            onClick={() => setActiveStatus(tab.key)}
          >
            {tab.label}
          </StatusTab>
        ))}
      </TabsWrapper>

      <Card>
        <SectionHeader>
          <SectionTitle>
            {activeStatus === 'ALL' && '전체 신고 목록'}
            {activeStatus === 'PENDING' && '미처리 신고 목록'}
            {activeStatus === 'RESOLVED' && '처리 완료된 신고 목록'}
            {activeStatus === 'REJECTED' && '반려된 신고 목록'}
          </SectionTitle>
          <SectionSubtitle>
            1단계에서는 상태별 탭과 테이블 헤더 구조만 구성합니다. 이후 실제 신고 데이터, 상세 모달, 처리 액션을 연동합니다.
          </SectionSubtitle>
        </SectionHeader>

        {loading ? (
          <TableMessage>로딩 중...</TableMessage>
        ) : error ? (
          <TableMessage>{error}</TableMessage>
        ) : reports.length === 0 ? (
          <TableMessage>해당 조건에 맞는 신고가 없습니다.</TableMessage>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>ID</th>
                <th>대상 ID(target_idx)</th>
                <th>신고자</th>
                <th>사유</th>
                <th>상태</th>
                <th>처리자</th>
                <th>처리일시</th>
                <th>생성일</th>
              </tr>
            </thead>
            <tbody>
              {reports.map(report => (
                <tr key={report.idx}>
                  <td>{report.idx}</td>
                  <td>{report.targetIdx}</td>
                  <td>
                    {report.reporterName
                      ? `${report.reporterName} (#${report.reporterId})`
                      : `#${report.reporterId}`}
                  </td>
                  <td>{report.reason}</td>
                  <td>{report.status}</td>
                  <td>{report.handledBy ?? '-'}</td>
                  <td>{report.handledAt ?? '-'}</td>
                  <td>{report.createdAt ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </Wrapper>
  );
};

export default ReportManagementSection;

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

const Code = styled.code`
  font-family: monospace;
  background: ${props => props.theme.colors.surfaceSoft};
  padding: 0 4px;
  border-radius: 4px;
`;

const TabsWrapper = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${props => props.theme.spacing.sm};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const StatusTab = styled.button`
  border-radius: 999px;
  border: 1px solid
    ${props =>
      props.$active ? props.theme.colors.primary : props.theme.colors.border};
  padding: 6px 14px;
  background: ${props =>
    props.$active ? props.theme.colors.primarySoft : props.theme.colors.surface};
  color: ${props =>
    props.$active ? props.theme.colors.primary : props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.caption.fontSize};
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;

  &:hover {
    background: ${props =>
      props.$active
        ? props.theme.colors.primarySoft
        : props.theme.colors.surfaceHover};
  }
`;

const Card = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surfaceSoft};
`;

const SectionHeader = styled.div`
  margin-bottom: ${props => props.theme.spacing.md};
`;

const SectionTitle = styled.h2`
  font-size: ${props => props.theme.typography.h4.fontSize};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const SectionSubtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.caption.fontSize};
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: ${props => props.theme.typography.caption.fontSize};

  thead {
    background: ${props => props.theme.colors.surface};
  }

  th,
  td {
    padding: 8px 10px;
    border-bottom: 1px solid ${props => props.theme.colors.border};
    text-align: left;
    vertical-align: top;
  }

  th {
    color: ${props => props.theme.colors.textSecondary};
    font-weight: 600;
    white-space: nowrap;
  }
`;

const TableMessage = styled.div`
  padding: ${props => props.theme.spacing.lg};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
`;

