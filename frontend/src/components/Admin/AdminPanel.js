import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { adminApi } from '../../api/adminApi';
import { usePermission } from '../../hooks/usePermission';
import PermissionDeniedModal from '../Common/PermissionDeniedModal';

const AdminPanel = () => {
  const { requireAdmin } = usePermission();
  const [region, setRegion] = useState('서울특별시');
  const [maxResults, setMaxResults] = useState(10);
  const [customKeywords, setCustomKeywords] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [showPermissionModal, setShowPermissionModal] = useState(false);

  // 권한 확인
  useEffect(() => {
    const { isAdmin, requiresModal } = requireAdmin();
    if (requiresModal) {
      setShowPermissionModal(true);
    }
  }, [requireAdmin]);

  const regions = [
    '서울특별시',
    '경기도',
    '인천광역시',
    '부산광역시',
    '대구광역시',
    '대전광역시',
    '광주광역시',
    '울산광역시'
  ];

  const handleLoadData = async () => {
    const keywordCount = customKeywords.trim() ? customKeywords.split(',').filter(k => k.trim()).length : 13; // 기본 키워드 13개
    const estimatedTotal = keywordCount * maxResults;
    
    if (!window.confirm(`정말로 ${region} 지역의 초기 데이터를 로드하시겠습니까?\n키워드당 최대 ${maxResults}개씩 검색합니다.\n(전체 최대 50개로 제한됩니다)`)) {
      return;
    }

    setLoading(true);
    setMessage(null);
    setError(null);

    try {
      const response = await adminApi.loadInitialData(region, maxResults, customKeywords.trim() || null);
      setMessage(response.message || '초기 데이터 로딩이 완료되었습니다.');
    } catch (err) {
      setError(err.response?.data?.error || err.message || '데이터 로딩에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const { isAdmin } = requireAdmin();
  
  // 권한이 없으면 모달만 표시하고 내용은 숨김
  if (!isAdmin) {
    return (
      <>
        <PermissionDeniedModal 
          isOpen={showPermissionModal}
          onClose={() => setShowPermissionModal(false)}
        />
        <Container>
          <Header>
            <Title>🔧 관리자 패널</Title>
            <Subtitle>접근 권한이 없습니다.</Subtitle>
          </Header>
        </Container>
      </>
    );
  }

  return (
    <>
      <Container>
        <Header>
          <Title>🔧 관리자 패널</Title>
          <Subtitle>LocationService 초기 데이터 로딩</Subtitle>
        </Header>

      <Card>
        <SectionTitle>초기 데이터 로딩 설정</SectionTitle>
        <Form>
          <FormGroup>
            <Label>지역 선택</Label>
            <Select 
              value={region} 
              onChange={(e) => setRegion(e.target.value)}
              disabled={loading}
            >
              {regions.map(r => (
                <option key={r} value={r}>{r}</option>
              ))}
            </Select>
          </FormGroup>

          <FormGroup>
            <Label>검색 키워드 (선택사항)</Label>
            <Input
              type="text"
              placeholder="반려동물카페, 펫호텔, 강아지동반가능 (쉼표로 구분)"
              value={customKeywords}
              onChange={(e) => setCustomKeywords(e.target.value)}
              disabled={loading}
            />
            <HelperText>입력하지 않으면 기본 키워드를 사용합니다. 쉼표로 여러 키워드를 입력하세요.</HelperText>
          </FormGroup>

          <FormGroup>
            <Label>키워드당 최대 검색 결과 수</Label>
            <Input
              type="number"
              min="1"
              max="50"
              value={maxResults}
              onChange={(e) => setMaxResults(parseInt(e.target.value) || 10)}
              disabled={loading}
            />
            <HelperText>각 키워드당 검색할 최대 장소 수입니다. 여러 키워드를 사용하면 합쳐서 더 많이 저장될 수 있습니다. (전체는 최대 50개로 제한됩니다)</HelperText>
          </FormGroup>

          <ButtonGroup>
            <LoadButton 
              onClick={handleLoadData} 
              disabled={loading}
            >
              {loading ? '로딩 중...' : '초기 데이터 로드하기'}
            </LoadButton>
          </ButtonGroup>

          {message && (
            <SuccessMessage>
              ✅ {message}
            </SuccessMessage>
          )}

          {error && (
            <ErrorMessage>
              ❌ {error}
            </ErrorMessage>
          )}
        </Form>
      </Card>

      <InfoCard>
        <InfoTitle>📋 참고사항</InfoTitle>
        <InfoList>
          <li>카카오맵 API를 사용하여 반려동물 관련 장소를 검색합니다.</li>
          <li>검색 키워드: 반려동물카페, 펫카페, 강아지카페, 고양이카페, 펫호텔, 동물병원, 펫샵, 반려동물용품, 애견미용</li>
          <li><strong>전체 최대 50개로 제한됩니다.</strong></li>
          <li>중복된 장소는 자동으로 제외됩니다.</li>
          <li>API 호출 제한을 고려하여 요청 간 딜레이가 있습니다.</li>
          <li>로딩 시간은 검색 결과 수에 따라 달라질 수 있습니다.</li>
        </InfoList>
      </InfoCard>
    </Container>
    </>
  );
};

export default AdminPanel;

const Container = styled.div`
  max-width: 900px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing.xxl} ${props => props.theme.spacing.lg};
`;

const Header = styled.div`
  margin-bottom: ${props => props.theme.spacing.xl};
  text-align: center;
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h1.fontSize};
  font-weight: ${props => props.theme.typography.h1.fontWeight};
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const Subtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const Card = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xl};
  margin-bottom: ${props => props.theme.spacing.xl};
  box-shadow: 0 4px 6px ${props => props.theme.colors.shadow};
`;

const SectionTitle = styled.h2`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const Form = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.lg};
`;

const FormGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.sm};
`;

const Label = styled.label`
  color: ${props => props.theme.colors.text};
  font-weight: 600;
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const Select = styled.select`
  padding: ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body1.fontSize};
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const Input = styled.input`
  padding: ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body1.fontSize};
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const HelperText = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: 14px;
  margin-top: 4px;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  margin-top: ${props => props.theme.spacing.md};
`;

const LoadButton = styled.button`
  flex: 1;
  background: ${props => props.disabled ? props.theme.colors.border : props.theme.colors.primary};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: ${props => props.disabled ? 'not-allowed' : 'pointer'};
  transition: all 0.2s ease;
  
  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }
  
  &:disabled {
    opacity: 0.6;
  }
`;

const SuccessMessage = styled.div`
  background: #d4edda;
  color: #155724;
  padding: ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.md};
  margin-top: ${props => props.theme.spacing.md};
  border: 1px solid #c3e6cb;
`;

const ErrorMessage = styled.div`
  background: #f8d7da;
  color: #721c24;
  padding: ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.md};
  margin-top: ${props => props.theme.spacing.md};
  border: 1px solid #f5c6cb;
`;

const InfoCard = styled(Card)`
  background: ${props => props.theme.colors.surface};
`;

const InfoTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h4.fontSize};
  font-weight: ${props => props.theme.typography.h4.fontWeight};
  margin-bottom: ${props => props.theme.spacing.md};
`;

const InfoList = styled.ul`
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.8;
  padding-left: ${props => props.theme.spacing.lg};
  
  li {
    margin-bottom: ${props => props.theme.spacing.sm};
  }
`;

