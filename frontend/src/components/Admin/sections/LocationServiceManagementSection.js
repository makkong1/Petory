import React, { useState, useRef } from 'react';
import styled from 'styled-components';
import { locationServiceApi } from '../../../api/locationServiceApi';

const LocationServiceManagementSection = () => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const fileInputRef = useRef(null);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      if (!file.name.endsWith('.csv')) {
        alert('CSV 파일만 업로드 가능합니다.');
        return;
      }
      setSelectedFile(file);
      setError(null);
      setResult(null);
    }
  };

  const handleImport = async () => {
    if (!selectedFile) {
      alert('CSV 파일을 선택해주세요.');
      return;
    }

    setLoading(true);
    setError(null);
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
    } catch (err) {
      setError(err?.response?.data?.message || err.message || '임포트 실패');
      alert('임포트 실패: ' + (err?.response?.data?.message || err.message));
    } finally {
      setLoading(false);
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
          <Label>CSV 파일 선택</Label>
          <FileInputWrapper>
            <FileInput
              type="file"
              accept=".csv"
              ref={fileInputRef}
              onChange={handleFileChange}
              disabled={loading}
            />
            {selectedFile && (
              <FileInfo>
                선택된 파일: <strong>{selectedFile.name}</strong> ({(selectedFile.size / 1024 / 1024).toFixed(2)} MB)
              </FileInfo>
            )}
          </FileInputWrapper>
        </FormGroup>

        <ButtonGroup>
          <ImportButton onClick={handleImport} disabled={loading || !selectedFile}>
            {loading ? '임포트 중...' : 'CSV 파일 임포트'}
          </ImportButton>
        </ButtonGroup>

        {error && <ErrorMessage>{error}</ErrorMessage>}
        
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

      <PlaceholderCard>
        <PlaceholderTitle>장소 목록</PlaceholderTitle>
        <PlaceholderText>
          1단계에서는 장소 목록/검색용 기본 테이블만 구성합니다. 이후 사진/웹사이트/설명/좌표 수정, 리뷰 삭제, 캐시 모니터링 및 수동 삭제 기능을 추가합니다.
        </PlaceholderText>
      </PlaceholderCard>
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

const PlaceholderCard = styled.div`
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px dashed ${props => props.theme.colors.border};
  padding: ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.surfaceSoft};
`;

const PlaceholderTitle = styled.h2`
  font-size: ${props => props.theme.typography.h4.fontSize};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const PlaceholderText = styled.p`
  color: ${props => props.theme.colors.textSecondary};
`;

const Card = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  padding: ${props => props.theme.spacing.lg};
  margin-bottom: ${props => props.theme.spacing.lg};
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

const Label = styled.label`
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


