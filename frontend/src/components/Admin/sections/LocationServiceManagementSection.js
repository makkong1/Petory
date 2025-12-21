import React, { useState, useRef, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { locationServiceApi } from '../../../api/locationServiceApi';

const LocationServiceManagementSection = () => {
  const [sido, setSido] = useState('');
  const [sigungu, setSigungu] = useState('');
  const [category, setCategory] = useState('');
  const [q, setQ] = useState('');
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [importLoading, setImportLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [importError, setImportError] = useState(null);
  const fileInputRef = useRef(null);

  const fetchServices = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {};
      if (sido) params.sido = sido;
      if (sigungu) params.sigungu = sigungu;
      if (category) params.category = category;
      if (q) params.q = q;

      const res = await locationServiceApi.listLocationServices(params);
      setServices(res.data?.services || []);
    } catch (e) {
      console.error('장소 목록 조회 실패:', e);
      setError(e.response?.data?.message || '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [sido, sigungu, category, q]);

  useEffect(() => {
    fetchServices();
  }, [fetchServices]);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      if (!file.name.endsWith('.csv')) {
        alert('CSV 파일만 업로드 가능합니다.');
        return;
      }
      setSelectedFile(file);
      setImportError(null);
      setResult(null);
    }
  };

  const handleImport = async () => {
    if (!selectedFile) {
      alert('CSV 파일을 선택해주세요.');
      return;
    }

    setImportLoading(true);
    setImportError(null);
    setResult(null);

    try {
      const response = await locationServiceApi.importPublicData(selectedFile);
      setResult(response.data);
      alert(`임포트 완료!\n총 읽음: ${response.data.totalRead}\n저장: ${response.data.saved}\n중복: ${response.data.duplicate}\n스킵: ${response.data.skipped}\n에러: ${response.data.error}`);
      // 파일 선택 초기화
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      // 목록 새로고침
      fetchServices();
    } catch (err) {
      setImportError(err?.response?.data?.message || err.message || '임포트 실패');
      alert('임포트 실패: ' + (err?.response?.data?.message || err.message));
    } finally {
      setImportLoading(false);
    }
  };

  return (
    <Wrapper>
      <Header>
        <Title>지역 서비스 관리</Title>
        <Subtitle>등록된 장소, 리뷰, 외부 API 캐시를 관리합니다.</Subtitle>
      </Header>

      <Card>
        <CardTitle>공공데이터 CSV 임포트</CardTitle>
        <CardDescription>
          공공데이터 포털에서 제공하는 반려동물 관련 시설 정보 CSV 파일을 임포트합니다.
          <br />
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

        {result && (
          <ResultBox>
            <ResultTitle>임포트 결과</ResultTitle>
            <ResultList>
              <ResultItem>총 읽은 라인: <strong>{result.totalRead}</strong></ResultItem>
              <ResultItem>저장된 개수: <strong>{result.saved}</strong></ResultItem>
              <ResultItem>중복 스킵: <strong>{result.duplicate}</strong></ResultItem>
              <ResultItem>검증 실패 스킵: <strong>{result.skipped}</strong></ResultItem>
              <ResultItem>에러 발생: <strong>{result.error}</strong></ResultItem>
            </ResultList>
          </ResultBox>
        )}
      </Card>

      <Filters>
        <Group>
          <Label>시도</Label>
          <Input
            placeholder="시도 (예: 서울특별시)"
            value={sido}
            onChange={e => setSido(e.target.value)}
          />
        </Group>
        <Group>
          <Label>시군구</Label>
          <Input
            placeholder="시군구 (예: 노원구)"
            value={sigungu}
            onChange={e => setSigungu(e.target.value)}
          />
        </Group>
        <Group>
          <Label>카테고리</Label>
          <Input
            placeholder="카테고리"
            value={category}
            onChange={e => setCategory(e.target.value)}
          />
        </Group>
        <Group style={{ flex: 1 }}>
          <Label>검색</Label>
          <Input
            placeholder="시설명/주소/카테고리"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Group>
        <Group>
          <Refresh onClick={fetchServices}>새로고침</Refresh>
        </Group>
      </Filters>

      <Card>
        {loading && services.length === 0 ? (
          <Info>로딩 중...</Info>
        ) : error ? (
          <Info>{error}</Info>
        ) : services.length === 0 ? (
          <Info>데이터가 없습니다.</Info>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>ID</th>
                <th>시설명</th>
                <th>카테고리</th>
                <th>주소</th>
                <th>전화번호</th>
                <th>평점</th>
              </tr>
            </thead>
            <tbody>
              {services.map((service) => (
                <tr key={service.idx}>
                  <td>{service.idx}</td>
                  <td className="ellipsis">{service.name || '-'}</td>
                  <td>{service.category3 || service.category2 || service.category1 || '-'}</td>
                  <td className="ellipsis">{service.address || '-'}</td>
                  <td>{service.phone || '-'}</td>
                  <td>{service.rating || '-'}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
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

const Input = styled.input`
  width: 240px;
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
  
  &:disabled {
    background: ${props => props.theme.colors.surfaceSoft};
    cursor: not-allowed;
  }
  
  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }
`;

const FileInfo = styled.div`
  padding: ${props => props.theme.spacing.xs};
  background: ${props => props.theme.colors.surfaceSoft};
  border-radius: ${props => props.theme.borderRadius.sm};
  font-size: ${props => props.theme.typography.body2.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  
  strong {
    color: ${props => props.theme.colors.text};
    font-weight: 600;
  }
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
  
  &:hover:enabled {
    background: ${props => props.theme.colors.primaryDark || '#176dd1'};
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
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
  
  strong {
    color: ${props => props.theme.colors.text};
    font-weight: 600;
  }
`;


