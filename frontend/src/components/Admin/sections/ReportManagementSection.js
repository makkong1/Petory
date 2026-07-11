import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { reportApi } from '../../../api/reportApi';
import ReportDetailModal from './ReportDetailModal';
import PageNavigation from '../../Common/PageNavigation';
import {
  SectionHeader, SectionTitle, SectionSubtitle,
  Toolbar, Spacer, TabGroup, Tab, Select,
  TableWrap, Table, Th, Td, TableMessage,
  StatusPill, RowBtn, RowBtnPrimary, RowActions,
} from '../ui/AdminUI';

const PAGE_SIZE = 20;

// 신고 상태 → pill tone/라벨
const STATUS_META = {
  PENDING: { tone: 'pending', label: '미처리' },
  RESOLVED: { tone: 'success', label: '처리완료' },
  REJECTED: { tone: 'neutral', label: '반려' },
};

const ReportManagementSection = () => {
  const [activeTargetType, setActiveTargetType] = useState('BOARD');
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reports, setReports] = useState([]);
  const [totalCount, setTotalCount] = useState(0);
  const [selectedReportId, setSelectedReportId] = useState(null);
  const [page, setPage] = useState(0);

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

  // 서버 페이징: 현재 페이지만 조회한다(전건 조회 후 클라이언트 슬라이싱 X).
  const fetchReports = async (pageArg) => {
    try {
      setLoading(true);
      setError(null);
      const response = await reportApi.getReports({
        targetType: activeTargetType,
        status: statusFilter,
        page: pageArg,
        size: PAGE_SIZE,
      });
      setReports(response.data?.reports || []);
      setTotalCount(response.data?.totalCount || 0);
    } catch (err) {
      console.error('신고 목록 조회 실패:', err);
      setError('신고 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handlePageChange = (nextPage) => {
    setPage(nextPage);
    fetchReports(nextPage);
  };

  // 대상 타입 + 상태 변경 시 첫 페이지부터 다시 조회
  useEffect(() => {
    setPage(0);
    fetchReports(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTargetType, statusFilter]);

  const formatDateTime = (value) => {
    if (!value) return '-';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };

  return (
    <Wrapper>
      <SectionHeader>
        <SectionTitle>신고 관리</SectionTitle>
        <SectionSubtitle>
          신고 대상(BOARD / COMMENT / MISSING_PET / PET_CARE_PROVIDER)과 상태(PENDING / RESOLVED / REJECTED)를 기준으로 신고를 관리합니다.
        </SectionSubtitle>
      </SectionHeader>

      <Toolbar>
        <TabGroup role="tablist">
          {targetTypeTabs.map(tab => (
            <Tab
              key={tab.key}
              type="button"
              $active={tab.key === activeTargetType}
              onClick={() => setActiveTargetType(tab.key)}
            >
              {tab.label}
            </Tab>
          ))}
        </TabGroup>
        <Spacer />
        <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
          {statusOptions.map(opt => (
            <option key={opt.key} value={opt.key}>{opt.label}</option>
          ))}
        </Select>
      </Toolbar>

      {loading ? (
        <TableMessage>로딩 중...</TableMessage>
      ) : error ? (
        <TableMessage>{error}</TableMessage>
      ) : reports.length === 0 ? (
        <TableMessage>해당 조건에 맞는 신고가 없습니다.</TableMessage>
      ) : (
        <>
          <TableWrap>
            <Table>
              <thead>
                <tr>
                  <Th $align="right">ID</Th>
                  <Th $align="right">대상 ID</Th>
                  <Th>신고자</Th>
                  <Th>사유</Th>
                  <Th $align="center">상태</Th>
                  <Th>처리자</Th>
                  <Th>처리일시</Th>
                  <Th>생성일</Th>
                  <Th $align="center">처리</Th>
                </tr>
              </thead>
              <tbody>
                {reports.map(report => {
                  const meta = STATUS_META[report.status] || { tone: 'neutral', label: report.status };
                  const handled = report.status === 'RESOLVED' || report.status === 'REJECTED';
                  return (
                    <tr key={report.idx}>
                      <Td $align="right" $mono $muted>#{report.idx}</Td>
                      <Td $align="right" $mono>{report.targetIdx}</Td>
                      <Td>
                        {report.reporterName
                          ? `${report.reporterName} (#${report.reporterId})`
                          : `#${report.reporterId}`}
                      </Td>
                      <Td>{report.reason}</Td>
                      <Td $align="center">
                        <StatusPill $tone={meta.tone}>{meta.label}</StatusPill>
                      </Td>
                      <Td $muted>{report.handledByName || '-'}</Td>
                      <Td $muted>{formatDateTime(report.handledAt)}</Td>
                      <Td $muted>{formatDateTime(report.createdAt)}</Td>
                      <Td $align="center">
                        <RowActions>
                          {!handled && (
                            <RowBtnPrimary onClick={() => setSelectedReportId(report.idx)}>처리</RowBtnPrimary>
                          )}
                          <RowBtn onClick={() => setSelectedReportId(report.idx)}>보기</RowBtn>
                        </RowActions>
                      </Td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          </TableWrap>

          <PaginationWrap>
            <PageNavigation
              currentPage={page}
              totalCount={totalCount}
              pageSize={PAGE_SIZE}
              onPageChange={handlePageChange}
              loading={loading}
              showEdges
            />
          </PaginationWrap>
        </>
      )}

      {selectedReportId && (
        <ReportDetailModal
          reportId={selectedReportId}
          onClose={() => setSelectedReportId(null)}
          onHandled={() => fetchReports(page)}
        />
      )}
    </Wrapper>
  );
};

export default ReportManagementSection;

const Wrapper = styled.div``;

const PaginationWrap = styled.div`
  margin-top: ${p => p.theme.spacing.md};
`;
