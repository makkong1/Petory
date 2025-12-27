import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { fileAdminApi } from '../../../api/fileAdminApi.js';

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
      <Header>
        <Title>파일 관리</Title>
        <Subtitle>업로드된 파일들을 조회하고 관리합니다.</Subtitle>
      </Header>

      {statistics && (
        <StatsCard>
          <StatsTitle>파일 통계</StatsTitle>
          <StatsGrid>
            <StatItem>
              <StatLabel>전체 파일</StatLabel>
              <StatValue>{statistics.totalFiles || 0}</StatValue>
            </StatItem>
            {statistics.filesByType && Object.entries(statistics.filesByType).map(([type, count]) => (
              <StatItem key={type}>
                <StatLabel>{type}</StatLabel>
                <StatValue>{count}</StatValue>
              </StatItem>
            ))}
          </StatsGrid>
        </StatsCard>
      )}

      <Filters>
        <Group>
          <Label>타겟 타입</Label>
          <Select value={targetType} onChange={e => setTargetType(e.target.value)}>
            {targetTypeOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </Group>
        <Group>
          <Label>타겟 ID</Label>
          <Input
            type="number"
            placeholder="타겟 ID"
            value={targetIdx}
            onChange={e => setTargetIdx(e.target.value)}
          />
        </Group>
        <Group style={{ flex: 1 }}>
          <Label>검색</Label>
          <Input
            placeholder="파일 경로/타입"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Group>
        <Group>
          <Refresh onClick={fetchFiles}>새로고침</Refresh>
        </Group>
      </Filters>

      {targetType && targetIdx && (
        <ActionBar>
          <Danger onClick={handleDeleteByTarget}>
            타겟의 모든 파일 삭제
          </Danger>
        </ActionBar>
      )}

      <Card>
        {loading && files.length === 0 ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>{error}</Info>
        ) : files.length === 0 ? (
          <Info>데이터가 없습니다.</Info>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>ID</th>
                <th>타겟 타입</th>
                <th>타겟 ID</th>
                <th>파일 경로</th>
                <th>파일 타입</th>
                <th>생성일</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {files.map((file) => (
                <tr key={file.idx}>
                  <td>{file.idx}</td>
                  <td>{file.targetType || '-'}</td>
                  <td>{file.targetIdx || '-'}</td>
                  <td className="ellipsis">{file.filePath || '-'}</td>
                  <td>{file.fileType || '-'}</td>
                  <td>{file.createdAt ? new Date(file.createdAt).toLocaleString() : '-'}</td>
                  <td>
                    <Actions>
                      {file.downloadUrl && (
                        <Btn as="a" href={file.downloadUrl} target="_blank" rel="noopener noreferrer">
                          보기
                        </Btn>
                      )}
                      <Danger onClick={() => handleDelete(file.idx)}>삭제</Danger>
                    </Actions>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
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

const StatsCard = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surface};
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const StatsTitle = styled.h3`
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const StatsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: ${props => props.theme.spacing.md};
`;

const StatItem = styled.div`
  padding: ${props => props.theme.spacing.sm};
  background: ${props => props.theme.colors.surfaceSoft};
  border-radius: ${props => props.theme.borderRadius.sm};
`;

const StatLabel = styled.div`
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const StatValue = styled.div`
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const Filters = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.md};
  flex-wrap: wrap;
`;

const Group = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
`;

const Label = styled.span`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.caption.fontSize};
`;

const Select = styled.select`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.sm};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
`;

const Input = styled.input`
  width: 120px;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.sm};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
`;

const Refresh = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const ActionBar = styled.div`
  margin-bottom: ${props => props.theme.spacing.md};
`;

const Card = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surface};
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: ${props => props.theme.typography.caption.fontSize};
  th, td { padding: 8px 10px; border-bottom: 1px solid ${props => props.theme.colors.border}; }
  th { color: ${props => props.theme.colors.text}; text-align: left; white-space: nowrap; }
  td.ellipsis { max-width: 420px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
`;

const Info = styled.div`
  padding: ${props => props.theme.spacing.lg};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const Actions = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.xs};
`;

const Btn = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
  text-decoration: none;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const Danger = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.error};
  color: ${props => props.theme.colors.error};
  background: transparent;
  border-radius: ${props => props.theme.borderRadius.sm};
  cursor: pointer;
`;
