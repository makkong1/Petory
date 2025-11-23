import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { reportApi } from '../../../api/reportApi';
import ReportDetailModal from './ReportDetailModal';

const ReportManagementSection = () => {
  const [activeTargetType, setActiveTargetType] = useState('BOARD');
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reports, setReports] = useState([]);
  const [selectedReportId, setSelectedReportId] = useState(null);

  const targetTypeTabs = [
    { key: 'BOARD', label: '게시글 신고' },
    { key: 'COMMENT', label: '댓글 신고' },
    { key: 'MISSING_PET', label: '실종 제보 신고' },
    { key: 'PET_CARE_PROVIDER', label: '유저 신고' },
  ];

  const statusOptions = [
    { key: 'ALL', label: '전체' },
    { key: 'PENDING', label: '미처리(PENDING)' },
    { key: 'RESOLVED', label: '처리완료(RESOLVED)' },
    { key: 'REJECTED', label: '반려(REJECTED)' },
  ];

  // 대상 타입 + 상태 조합으로 신고 목록 조회
  useEffect(() => {
    const fetchReports = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await reportApi.getReports({
          targetType: activeTargetType,
          status: statusFilter,
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
  }, [activeTargetType, statusFilter]);

  const formatDateTime = (value) => {
    if (!value) return '-';
    try {
      const d = new Date(value);
      if (Number.isNaN(d.getTime())) return String(value);
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      const hh = String(d.getHours()).padStart(2, '0');
      const mi = String(d.getMinutes()).padStart(2, '0');
      return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
    } catch {
      return String(value);
    }
  };

  return (
    <Wrapper>
      <Header>
        <Title>신고 관리</Title>
        <Subtitle>
          신고 테이블의 <Code>target_type</Code> (BOARD / COMMENT / MISSING_PET / PET_CARE_PROVIDER)와{' '}
          <Code>status</Code> (PENDING / RESOLVED / REJECTED)를 기준으로 신고를 관리합니다.
        </Subtitle>
      </Header>

      <TabsWrapper>
        {targetTypeTabs.map(tab => (
          <StatusTab
            key={tab.key}
            $active={tab.key === activeTargetType}
            onClick={() => setActiveTargetType(tab.key)}
          >
            {tab.label}
          </StatusTab>
        ))}
      </TabsWrapper>

      <FilterBar>
        <FilterLabel>상태 필터</FilterLabel>
        <FilterSelect
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value)}
        >
          {statusOptions.map(opt => (
            <option key={opt.key} value={opt.key}>
              {opt.label}
            </option>
          ))}
        </FilterSelect>
      </FilterBar>

      <Card>
        <SectionHeader>
          <SectionTitle>
            {targetTypeTabs.find(t => t.key === activeTargetType)?.label || '신고 목록'}
          </SectionTitle>
          <SectionSubtitle>
            상단 탭에서 신고 대상을 선택하고, 우측 상태 필터로 PENDING / RESOLVED / REJECTED 등을 조합해 확인할 수 있습니다.
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
                <th></th>
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
                  <td>{report.handledByName || '-'}</td>
                  <td>{formatDateTime(report.handledAt)}</td>
                  <td>{formatDateTime(report.createdAt)}</td>
                  <td>
                    <ViewButton onClick={() => setSelectedReportId(report.idx)}>보기</ViewButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      {selectedReportId && (
        <ReportDetailModal
          reportId={selectedReportId}
          onClose={() => setSelectedReportId(null)}
          onHandled={() => {
            // 처리 후 목록 새로고침
            (async () => {
              try {
                setLoading(true);
                const response = await reportApi.getReports({
                  targetType: activeTargetType,
                  status: statusFilter,
                });
                setReports(response.data || []);
              } finally {
                setLoading(false);
              }
            })();
          }}
        />
      )}
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

const FilterBar = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const FilterLabel = styled.span`
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.textSecondary};
`;

const FilterSelect = styled.select`
  padding: 6px 10px;
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
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

const ViewButton = styled.button`
  padding: 6px 10px;
  border-radius: ${props => props.theme.borderRadius.sm};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
  cursor: pointer;
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.text};

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;
const TableMessage = styled.div`
  padding: ${props => props.theme.spacing.lg};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
`;

