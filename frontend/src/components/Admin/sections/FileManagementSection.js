import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { fileAdminApi } from '../../../api/fileAdminApi.js';
import {
  SectionHeader, SectionTitle, SectionSubtitle,
  Toolbar, Spacer, FieldLabel, Select, Search,
  TableWrap, Table, Th, Td, TableMessage,
  RowBtn, RowBtnDanger, RowActions,
  StatGrid, StatCard, StatLabel, StatValue,
} from '../ui/AdminUI';

const FileManagementSection = () => {
  const [targetType, setTargetType] = useState('');
  const [targetIdx, setTargetIdx] = useState('');
  const [q, setQ] = useState('');
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [statistics, setStatistics] = useState(null);

  const fetchFiles = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {};
      if (targetType) params.targetType = targetType;
      if (targetIdx) params.targetIdx = parseInt(targetIdx);
      if (q) params.q = q;
      
      const res = await fileAdminApi.listFiles(params);
      setFiles(res.data || []);
    } catch (e) {
      console.error('파일 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [targetType, targetIdx, q]);

  const fetchStatistics = useCallback(async () => {
    try {
      const res = await fileAdminApi.getStatistics();
      setStatistics(res.data);
    } catch (e) {
      console.error('통계 조회 실패:', e);
    }
  }, []);

  useEffect(() => {
    fetchFiles();
    fetchStatistics();
  }, [fetchFiles, fetchStatistics]);

  const handleDelete = async (id) => {
    if (!window.confirm('이 파일을 삭제하시겠습니까?')) return;
    try {
      await fileAdminApi.deleteFile(id);
      fetchFiles();
      fetchStatistics();
    } catch (e) {
      alert(e.response?.data?.message || '삭제 실패');
    }
  };

  const handleDeleteByTarget = async () => {
    if (!targetType || !targetIdx) {
      alert('타겟 타입과 ID를 입력해주세요.');
      return;
    }
    if (!window.confirm(`타겟(${targetType}:${targetIdx})의 모든 파일을 삭제하시겠습니까?`)) return;
    try {
      await fileAdminApi.deleteFilesByTarget(targetType, parseInt(targetIdx));
      fetchFiles();
      fetchStatistics();
    } catch (e) {
      alert(e.response?.data?.message || '삭제 실패');
    }
  };

  const targetTypeOptions = [
    { value: '', label: '전체' },
    { value: 'BOARD', label: '게시글' },
    { value: 'COMMENT', label: '댓글' },
    { value: 'MISSING_PET', label: '실종 제보' },
    { value: 'MISSING_PET_COMMENT', label: '실종 제보 댓글' },
    { value: 'CARE_REQUEST', label: '케어 요청' },
    { value: 'USER', label: '사용자' },
  ];

  return (
    <Wrapper>
      <SectionHeader>
        <SectionTitle>파일 관리</SectionTitle>
        <SectionSubtitle>업로드된 파일들을 조회하고 관리합니다.</SectionSubtitle>
      </SectionHeader>

      {statistics && (
        <StatGrid>
          <StatCard>
            <StatLabel>전체 파일</StatLabel>
            <StatValue $accent>{(statistics.totalFiles || 0).toLocaleString()}</StatValue>
          </StatCard>
          {statistics.filesByType && Object.entries(statistics.filesByType).map(([type, count]) => (
            <StatCard key={type}>
              <StatLabel>{type}</StatLabel>
              <StatValue>{Number(count).toLocaleString()}</StatValue>
            </StatCard>
          ))}
        </StatGrid>
      )}

      <Toolbar>
        <div><FieldLabel>타겟 타입</FieldLabel>
          <Select value={targetType} onChange={e => setTargetType(e.target.value)}>
            {targetTypeOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </div>
        <Search
          type="number"
          placeholder="타겟 ID"
          value={targetIdx}
          onChange={e => setTargetIdx(e.target.value)}
          style={{ minWidth: 110 }}
        />
        <Search
          placeholder="파일 경로/타입 검색…"
          value={q}
          onChange={e => setQ(e.target.value)}
        />
        <Spacer />
        {targetType && targetIdx && (
          <RowBtnDanger onClick={handleDeleteByTarget}>타겟의 모든 파일 삭제</RowBtnDanger>
        )}
        <RowBtn onClick={fetchFiles}>새로고침</RowBtn>
      </Toolbar>

      {loading && files.length === 0 ? (
        <TableMessage>로딩 중...</TableMessage>
      ) : error ? (
        <TableMessage>{error}</TableMessage>
      ) : files.length === 0 ? (
        <TableMessage>데이터가 없습니다.</TableMessage>
      ) : (
        <TableWrap>
          <Table>
            <thead>
              <tr>
                <Th $align="right">ID</Th>
                <Th>타겟 타입</Th>
                <Th $align="right">타겟 ID</Th>
                <Th>파일 경로</Th>
                <Th>파일 타입</Th>
                <Th>생성일</Th>
                <Th $align="center">액션</Th>
              </tr>
            </thead>
            <tbody>
              {files.map((file) => (
                <tr key={file.idx}>
                  <Td $align="right" $mono $muted>#{file.idx}</Td>
                  <Td>{file.targetType || '-'}</Td>
                  <Td $align="right" $mono>{file.targetIdx || '-'}</Td>
                  <Td $ellipsis $mono>{file.filePath || '-'}</Td>
                  <Td $muted>{file.fileType || '-'}</Td>
                  <Td $muted>{file.createdAt ? new Date(file.createdAt).toLocaleString('ko-KR') : '-'}</Td>
                  <Td $align="center">
                    <RowActions>
                      {file.downloadUrl && (
                        <RowBtn as="a" href={file.downloadUrl} target="_blank" rel="noopener noreferrer" style={{ textDecoration: 'none' }}>
                          보기
                        </RowBtn>
                      )}
                      <RowBtnDanger onClick={() => handleDelete(file.idx)}>삭제</RowBtnDanger>
                    </RowActions>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </TableWrap>
      )}
    </Wrapper>
  );
};

export default FileManagementSection;

const Wrapper = styled.div``;






















