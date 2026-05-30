import React, { useState, useRef, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { locationServiceApi } from '../../../api/locationServiceApi';
import { adminApi } from '../../../api/adminApi';

const LocationServiceManagementSection = () => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [importLoading, setImportLoading] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState(null);

  const [jsonPreview, setJsonPreview] = useState(null);
  const [jsonLoading, setJsonLoading] = useState(false);
  const [previewFilename, setPreviewFilename] = useState(null);

  const [importFiles, setImportFiles] = useState([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [fileSyncResults, setFileSyncResults] = useState({});
  const [fileSyncLoading, setFileSyncLoading] = useState({});

  const [collectLoading, setCollectLoading] = useState(false);
  const [collectMsg, setCollectMsg] = useState(null);
  const pollRef = useRef(null);

  const fileInputRef = useRef(null);

  const loadJsonPreview = useCallback(async (filename = null) => {
    setJsonLoading(true);
    try {
      const data = await adminApi.getJsonPreview(filename);
      setJsonPreview(data);
      setPreviewFilename(data?.exists ? data.filename : filename);
    } catch (e) {
      setJsonPreview(null);
      setPreviewFilename(filename);
    } finally {
      setJsonLoading(false);
    }
  }, []);

  const loadImportFiles = useCallback(async () => {
    setFilesLoading(true);
    try {
      const data = await adminApi.getImportFiles();
      setImportFiles(data || []);
    } catch (e) {
      setImportFiles([]);
    } finally {
      setFilesLoading(false);
    }
  }, []);

  const stopPoll = useCallback(() => {
    if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
  }, []);

  const startPolling = useCallback(() => {
    if (pollRef.current) return; // 이미 polling 중
    pollRef.current = setInterval(async () => {
      try {
        const status = await adminApi.getCollectStatus();
        if (!status.running) {
          stopPoll();
          setCollectLoading(false);
          if (status.status === 'done') {
            setCollectMsg({ type: 'ok', text: '수집 완료 — 파일 목록을 갱신합니다.' });
            loadImportFiles();
            loadJsonPreview();
          } else if (status.status !== 'idle') {
            setCollectMsg({ type: 'error', text: `수집 실패: ${status.status}` });
          }
        }
      } catch (e) {
        stopPoll();
        setCollectLoading(false);
      }
    }, 5000);
  }, [stopPoll, loadImportFiles, loadJsonPreview]);

  const handleCollect = async () => {
    setCollectLoading(true);
    setCollectMsg({ type: 'info', text: '수집 시작 중...' });
    try {
      await adminApi.startCollect();
      setCollectMsg({ type: 'info', text: '수집 중... 완료되면 파일 목록이 자동 갱신됩니다.' });
      startPolling();
    } catch (err) {
      const status = err?.response?.status;
      if (status === 409) {
        // 이미 서버에서 수집 중 → polling 붙이기
        setCollectMsg({ type: 'info', text: '수집 중... (이미 진행 중인 수집이 있습니다)' });
        startPolling();
      } else {
        setCollectLoading(false);
        const msg = err?.response?.data?.message || err?.response?.data?.error || err.message;
        setCollectMsg({ type: 'error', text: msg || '수집 시작 실패' });
      }
    }
  };

  // 마운트 시 이미 수집 중이면 자동으로 polling 시작
  useEffect(() => {
    adminApi.getCollectStatus().then(status => {
      if (status.running) {
        setCollectLoading(true);
        setCollectMsg({ type: 'info', text: '수집 중... 완료되면 파일 목록이 자동 갱신됩니다.' });
        startPolling();
      }
    }).catch(() => {});
  }, [startPolling]);

  // 언마운트 시 폴링 정리
  useEffect(() => () => stopPoll(), [stopPoll]);

  const handleSyncFile = async (filename) => {
    setFileSyncLoading(prev => ({ ...prev, [filename]: true }));
    setFileSyncResults(prev => ({ ...prev, [filename]: null }));
    try {
      const data = await adminApi.syncFromFile(filename);
      setFileSyncResults(prev => ({ ...prev, [filename]: { ok: true, ...data } }));
    } catch (err) {
      setFileSyncResults(prev => ({
        ...prev,
        [filename]: { ok: false, error: err?.response?.data?.error || '동기화 실패' },
      }));
    } finally {
      setFileSyncLoading(prev => ({ ...prev, [filename]: false }));
    }
  };

  const handlePreviewFile = (filename) => {
    loadJsonPreview(filename);
  };

  useEffect(() => {
    loadJsonPreview();
    loadImportFiles();
  }, [loadJsonPreview, loadImportFiles]);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      if (!file.name.endsWith('.csv')) {
        alert('CSV 파일만 업로드 가능합니다.');
        return;
      }
      setSelectedFile(file);
      setImportError(null);
      setImportResult(null);
    }
  };

  const handleImport = async () => {
    if (!selectedFile) { alert('CSV 파일을 선택해주세요.'); return; }
    setImportLoading(true);
    setImportError(null);
    setImportResult(null);
    try {
      const response = await locationServiceApi.importPublicData(selectedFile);
      setImportResult(response.data);
      setSelectedFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err) {
      setImportError(err?.response?.data?.message || err.message || '임포트 실패');
    } finally {
      setImportLoading(false);
    }
  };

  const formatDate = (iso) => {
    if (!iso) return '-';
    return iso.replace('T', ' ').substring(0, 16);
  };

  return (
    <Wrapper>
      <Header>
        <Title>지역 서비스 관리</Title>
        <Subtitle>등록된 장소, 리뷰, 외부 API 캐시를 관리합니다.</Subtitle>
      </Header>

      {/* ── 수집 파일 목록 ── */}
      <Card>
        <CardTitleRow>
          <CardTitle>수집 파일 목록</CardTitle>
          <ButtonRow>
            <CollectButton onClick={handleCollect} disabled={collectLoading}>
              {collectLoading ? '수집 중...' : '▶ 수집 시작'}
            </CollectButton>
            <RefreshButton onClick={loadImportFiles} disabled={filesLoading}>
              {filesLoading ? '로딩 중...' : '새로고침'}
            </RefreshButton>
          </ButtonRow>
        </CardTitleRow>
        <CardDescription>
          Python 배치가 생성한 JSON 파일 목록입니다. 수집 시작 후 완료되면 자동으로 목록이 갱신됩니다.
        </CardDescription>

        {collectMsg && (
          <CollectStatus $type={collectMsg.type}>{collectMsg.text}</CollectStatus>
        )}

        {filesLoading && <Info>파일 목록 로딩 중...</Info>}
        {!filesLoading && importFiles.length === 0 && (
          <Info>수집 파일이 없습니다. Python 배치를 먼저 실행해주세요.</Info>
        )}
        {!filesLoading && importFiles.length > 0 && (
          <Table>
            <thead>
              <tr>
                <th>파일명</th><th>크기</th><th>수정일</th><th></th>
              </tr>
            </thead>
            <tbody>
              {importFiles.map((f) => {
                const res = fileSyncResults[f.filename];
                const loading = fileSyncLoading[f.filename];
                return (
                  <React.Fragment key={f.filename}>
                    <tr>
                      <td>
                        <FileName
                          type="button"
                          $active={previewFilename === f.filename}
                          onClick={() => handlePreviewFile(f.filename)}
                        >
                          {f.filename}
                        </FileName>
                      </td>
                      <td>{f.sizeKb} KB</td>
                      <td>{formatDate(f.lastModified)}</td>
                      <td>
                        <FileSyncButton onClick={() => handleSyncFile(f.filename)} disabled={loading}>
                          {loading ? '동기화 중...' : '동기화'}
                        </FileSyncButton>
                      </td>
                    </tr>
                    {res && (
                      <tr>
                        <td colSpan={4}>
                          {res.ok
                            ? <SyncOk>✓ 완료 — 총 {res.total}건 / 신규 {res.saved} / 업데이트 {res.updated} / 스킵 {res.skipped}</SyncOk>
                            : <SyncFail>✗ {res.error}</SyncFail>}
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </Table>
        )}
      </Card>


      {/* ── JSON 파일 미리보기 ── */}
      <Card>
        <CardTitle>JSON 원본 파일 미리보기</CardTitle>
        <CardDescription>
          pet-data-api 배치가 생성한 원본 JSON 파일입니다. 동기화 전 데이터를 확인할 수 있습니다.
        </CardDescription>

        {jsonLoading && <Info>로딩 중...</Info>}

        {!jsonLoading && jsonPreview && !jsonPreview.exists && (
          <Info>파일 없음 — pet-data-api 배치를 먼저 실행해주세요. ({jsonPreview.reason})</Info>
        )}

        {!jsonLoading && jsonPreview && jsonPreview.exists && (
          <>
            <MetaRow>
              <MetaBadge>{jsonPreview.filename || previewFilename || '최신 파일'}</MetaBadge>
              <MetaBadge>마지막 생성: {formatDate(jsonPreview.lastModified)}</MetaBadge>
              <MetaBadge>{jsonPreview.count}건</MetaBadge>
            </MetaRow>
            <Table>
              <thead>
                <tr>
                  <th>시설명</th><th>카테고리</th><th>시도</th><th>시군구</th><th>주소</th><th>전화번호</th>
                </tr>
              </thead>
              <tbody>
                {jsonPreview.records.map((r, i) => (
                  <tr key={i}>
                    <td className="ellipsis">{r.name || '-'}</td>
                    <td>{r.category || '-'}</td>
                    <td>{r.sido || '-'}</td>
                    <td>{r.sigungu || '-'}</td>
                    <td className="ellipsis">{r.address || '-'}</td>
                    <td>{r.phone || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </>
        )}
      </Card>

      {/* ── 공공데이터 CSV 임포트 ── */}
      <Card>
        <CardTitle>공공데이터 CSV 임포트</CardTitle>
        <CardDescription>
          공공데이터 포털에서 제공하는 반려동물 관련 시설 정보 CSV 파일을 임포트합니다.<br />
          CSV 파일 형식: 시설명,카테고리1,카테고리2,카테고리3,시도명칭,시군구명칭,법정읍면동명칭,리명칭,번지,도로명이름,건물번호,위도,경도,우편번호,도로명주소,지번주소,전화번호,홈페이지,휴무일,운영시간,주차가능여부,입장가격정보,반려동물동반가능정보,반려동물전용정보,입장가능동물크기,반려동물제한사항,장소실내여부,장소실외여부,기본정보장소설명,애견동반추가요금,최종작성일
        </CardDescription>

        <FormGroup>
          <FormLabel>CSV 파일 선택</FormLabel>
          <FileInputWrapper>
            <FileInput
              type="file"
              accept=".csv"
              ref={fileInputRef}
              onChange={handleFileChange}
              disabled={importLoading}
            />
            {selectedFile && (
              <FileInfo>
                선택된 파일: <strong>{selectedFile.name}</strong> ({(selectedFile.size / 1024 / 1024).toFixed(2)} MB)
              </FileInfo>
            )}
          </FileInputWrapper>
        </FormGroup>

        <ButtonGroup>
          <ImportButton onClick={handleImport} disabled={importLoading || !selectedFile}>
            {importLoading ? '임포트 중...' : 'CSV 파일 임포트'}
          </ImportButton>
        </ButtonGroup>

        {importError && <ErrorMessage>{importError}</ErrorMessage>}

        {importResult && (
          <ResultBox>
            <ResultTitle>임포트 결과</ResultTitle>
            <ResultList>
              <ResultItem>총 읽은 라인: <strong>{importResult.totalRead}</strong></ResultItem>
              <ResultItem>저장된 개수: <strong>{importResult.saved}</strong></ResultItem>
              <ResultItem>중복 스킵: <strong>{importResult.duplicate}</strong></ResultItem>
              <ResultItem>검증 실패 스킵: <strong>{importResult.skipped}</strong></ResultItem>
              <ResultItem>에러 발생: <strong>{importResult.error}</strong></ResultItem>
            </ResultList>
          </ResultBox>
        )}
      </Card>
    </Wrapper>
  );
};

export default LocationServiceManagementSection;

const Wrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.lg};
`;

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

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: ${props => props.theme.typography.caption.fontSize};
  th, td { padding: 8px 10px; border-bottom: 1px solid ${props => props.theme.colors.border}; }
  th { color: ${props => props.theme.colors.text}; text-align: left; white-space: nowrap; }
  td.ellipsis { max-width: 300px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
`;

const Info = styled.div`
  padding: ${props => props.theme.spacing.lg};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const Card = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surface};
`;

const CardTitle = styled.h3`
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  margin-bottom: ${props => props.theme.spacing.sm};
  color: ${props => props.theme.colors.text};
`;

const CardDescription = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  line-height: 1.6;
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const FormGroup = styled.div`
  margin-bottom: ${props => props.theme.spacing.md};
`;

const FormLabel = styled.label`
  display: block;
  margin-bottom: ${props => props.theme.spacing.xs};
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const FileInputWrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.xs};
`;

const FileInput = styled.input`
  padding: ${props => props.theme.spacing.sm};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.body1.fontSize};
  cursor: pointer;
  &:disabled { background: ${props => props.theme.colors.surfaceSoft}; cursor: not-allowed; }
  &:focus { outline: none; border-color: ${props => props.theme.colors.primary}; }
`;

const FileInfo = styled.div`
  padding: ${props => props.theme.spacing.xs};
  background: ${props => props.theme.colors.surfaceSoft};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.body2.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  strong { color: ${props => props.theme.colors.text}; font-weight: 600; }
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.sm};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const ImportButton = styled.button`
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.lg};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
  &:hover:enabled { background: ${props => props.theme.colors.primaryDark || '#176dd1'}; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
`;


const ErrorMessage = styled.div`
  color: #df3737;
  padding: ${props => props.theme.spacing.sm};
  background: #fee;
  border-radius: ${props => props.theme.borderRadius.sm};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const ResultBox = styled.div`
  margin-top: ${props => props.theme.spacing.md};
  padding: ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surfaceSoft};
  border-radius: ${props => props.theme.borderRadius.sm};
`;

const ResultTitle = styled.h4`
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: 600;
  margin-bottom: ${props => props.theme.spacing.sm};
  color: ${props => props.theme.colors.text};
`;

const ResultList = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0;
`;

const ResultItem = styled.li`
  padding: ${props => props.theme.spacing.xs} 0;
  color: ${props => props.theme.colors.textSecondary};
  strong { color: ${props => props.theme.colors.text}; font-weight: 600; }
`;


const ButtonRow = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.xs};
  align-items: center;
`;

const CollectButton = styled.button`
  font-size: 13px;
  padding: 5px 14px;
  border: none;
  border-radius: ${props => props.theme.borderRadius.sm};
  background: #1565c0;
  color: white;
  font-weight: 600;
  cursor: pointer;
  &:hover:enabled { background: #0d47a1; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
`;

const CollectStatus = styled.div`
  font-size: 13px;
  padding: 8px 12px;
  border-radius: ${props => props.theme.borderRadius.sm};
  margin-bottom: ${props => props.theme.spacing.md};
  background: ${props =>
    props.$type === 'ok' ? '#e8f5e9' :
    props.$type === 'error' ? '#ffebee' : '#e3f2fd'};
  color: ${props =>
    props.$type === 'ok' ? '#2e7d32' :
    props.$type === 'error' ? '#c62828' : '#1565c0'};
`;

const CardTitleRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const RefreshButton = styled.button`
  font-size: 12px;
  padding: 4px 12px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.sm};
  background: transparent;
  color: ${props => props.theme.colors.textSecondary};
  cursor: pointer;
  &:hover:enabled { background: ${props => props.theme.colors.surfaceSoft}; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const FileName = styled.button`
  font-family: monospace;
  font-size: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.text};
  cursor: pointer;
  text-align: left;
  &:hover {
    color: ${props => props.theme.colors.primary};
    text-decoration: underline;
  }
`;

const FileSyncButton = styled.button`
  font-size: 12px;
  padding: 3px 10px;
  border: 1px solid #2e7d32;
  border-radius: ${props => props.theme.borderRadius.sm};
  background: transparent;
  color: #2e7d32;
  cursor: pointer;
  white-space: nowrap;
  &:hover:enabled { background: #2e7d3210; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const SyncOk = styled.div`
  font-size: 12px;
  color: #2e7d32;
  padding: 4px 8px;
  background: #e8f5e9;
  border-radius: 4px;
`;

const SyncFail = styled.div`
  font-size: 12px;
  color: #c62828;
  padding: 4px 8px;
  background: #ffebee;
  border-radius: 4px;
`;


const MetaRow = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.sm};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const MetaBadge = styled.span`
  padding: 4px 10px;
  background: ${props => props.theme.colors.surfaceSoft};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.caption.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  font-weight: 600;
`;
