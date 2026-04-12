import React, { useState, useEffect, useRef } from 'react';
import styled from 'styled-components';
import { careRequestApi } from '../../api/careRequestApi';
import { geocodingApi } from '../../api/geocodingApi';
import { petApiClient } from '../../api/userApi';
import { paymentApi } from '../../api/paymentApi';
import { useEmailVerification } from '../../hooks/useEmailVerification';
import MiniMapPicker from './MiniMapPicker';

const defaultForm = () => ({
  title: '',
  description: '',
  address: '',
  latitude: null,
  longitude: null,
  date: '',
  offeredCoins: '',
  petIdx: '',
});

const CareCreateModal = ({ onClose, onSuccess }) => {
  const { checkAndRedirect, EmailVerificationPromptComponent } = useEmailVerification('PET_CARE');
  const [formData, setFormData] = useState(defaultForm());
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [locationQuery, setLocationQuery] = useState('');
  const [locationResults, setLocationResults] = useState([]);
  const [showResults, setShowResults] = useState(false);
  const [locationSearching, setLocationSearching] = useState(false);
  const [showMap, setShowMap] = useState(true);
  const [pets, setPets] = useState([]);
  const [coinBalance, setCoinBalance] = useState(null);
  const locationInputRef = useRef(null);
  const resultsRef = useRef(null);

  useEffect(() => {
    petApiClient.getMyPets().then(res => setPets(res.data || [])).catch(() => {});
    paymentApi.getBalance().then(res => setCoinBalance(res?.balance ?? 0)).catch(() => {});
  }, []);

  // 주소 검색 debounce
  useEffect(() => {
    if (!locationQuery || locationQuery.trim().length < 2) {
      setLocationResults([]);
      setShowResults(false);
      return;
    }
    const timer = setTimeout(async () => {
      setLocationSearching(true);
      try {
        const data = await geocodingApi.searchPlaces(locationQuery);
        if (data && data.success && data.results && data.results.length > 0) {
          setLocationResults(data.results);
          setShowResults(true);
        } else {
          setLocationResults([]);
          setShowResults(false);
        }
      } catch {
        setLocationResults([]);
      } finally {
        setLocationSearching(false);
      }
    }, 500);
    return () => clearTimeout(timer);
  }, [locationQuery]);

  useEffect(() => {
    const handler = (e) => {
      if (locationInputRef.current && !locationInputRef.current.contains(e.target) &&
          resultsRef.current && !resultsRef.current.contains(e.target)) {
        setShowResults(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleLocationSelect = (result) => {
    setFormData(prev => ({ ...prev, address: result.address, latitude: result.latitude, longitude: result.longitude }));
    setLocationQuery(result.address);
    setShowResults(false);
    setErrors(prev => { const e = { ...prev }; delete e.address; return e; });
  };

  const handleMapSelect = (lat, lng, address) => {
    setFormData(prev => ({ ...prev, address: address || prev.address, latitude: lat, longitude: lng }));
    if (address) setLocationQuery(address);
    setErrors(prev => { const e = { ...prev }; delete e.address; return e; });
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    if (errors[name]) setErrors(prev => { const e = { ...prev }; delete e[name]; return e; });
  };

  const validate = () => {
    const e = {};
    if (!formData.title.trim()) e.title = '제목을 입력해주세요.';
    if (!formData.date) e.date = '날짜/시간을 입력해주세요.';
    else if (new Date(formData.date) < new Date()) e.date = '현재 이후 시간을 입력해주세요.';
    if (formData.offeredCoins !== '' && Number(formData.offeredCoins) < 0) e.offeredCoins = '0 이상의 값을 입력해주세요.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!checkAndRedirect()) return;
    if (!validate()) return;
    setLoading(true);
    try {
      await careRequestApi.createCareRequest({
        title: formData.title,
        description: formData.description,
        address: formData.address || undefined,
        latitude: formData.latitude || undefined,
        longitude: formData.longitude || undefined,
        date: formData.date,
        offeredCoins: formData.offeredCoins !== '' ? Number(formData.offeredCoins) : undefined,
        petIdx: formData.petIdx || undefined,
      });
      onSuccess?.();
      onClose();
    } catch (err) {
      alert(err.response?.data?.error || '케어 요청 등록에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Overlay onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <EmailVerificationPromptComponent />
      <Modal>
        <ModalHeader>
          <ModalTitle>🐾 케어 요청 등록</ModalTitle>
          <CloseBtn onClick={onClose}>✕</CloseBtn>
        </ModalHeader>

        <Form onSubmit={handleSubmit}>
          <Field>
            <Label>제목 *</Label>
            <Input name="title" value={formData.title} onChange={handleChange} placeholder="케어 요청 제목을 입력하세요" />
            {errors.title && <ErrorMsg>{errors.title}</ErrorMsg>}
          </Field>

          <Field>
            <Label>날짜 / 시간 *</Label>
            <Input
              type="datetime-local"
              name="date"
              value={formData.date}
              onChange={handleChange}
              min={new Date().toISOString().slice(0, 16)}
            />
            {errors.date && <ErrorMsg>{errors.date}</ErrorMsg>}
          </Field>

          <Field>
            <LocationLabelRow>
              <Label>위치 (선택)</Label>
              <MapToggleBtn type="button" onClick={() => setShowMap(v => !v)}>
                {showMap ? '지도 숨기기' : '지도 보기'}
              </MapToggleBtn>
            </LocationLabelRow>
            <LocationInputWrapper>
              <Input
                ref={locationInputRef}
                value={locationQuery}
                onChange={e => setLocationQuery(e.target.value)}
                placeholder="주소를 검색하거나 지도를 클릭하세요"
                autoComplete="off"
              />
              {locationSearching && <SearchingHint>검색 중...</SearchingHint>}
              {showResults && locationResults.length > 0 && (
                <ResultsList ref={resultsRef}>
                  {locationResults.map((r, i) => (
                    <ResultItem key={i} onClick={() => handleLocationSelect(r)}>
                      <ResultAddress>{r.address}</ResultAddress>
                      {r.jibunAddress && r.jibunAddress !== r.address && (
                        <ResultSub>{r.jibunAddress}</ResultSub>
                      )}
                    </ResultItem>
                  ))}
                </ResultsList>
              )}
            </LocationInputWrapper>
            {errors.address && <ErrorMsg>{errors.address}</ErrorMsg>}
            {showMap && (
              <MiniMapPicker
                lat={formData.latitude}
                lng={formData.longitude}
                onSelect={handleMapSelect}
              />
            )}
          </Field>

          <Row>
            <Field style={{ flex: 1 }}>
              <Label>제공 코인 (선택){coinBalance != null && <BalanceHint> · 보유 {coinBalance.toLocaleString()}C</BalanceHint>}</Label>
              <Input
                type="number"
                name="offeredCoins"
                value={formData.offeredCoins}
                onChange={handleChange}
                placeholder="0"
                min="0"
              />
              {errors.offeredCoins && <ErrorMsg>{errors.offeredCoins}</ErrorMsg>}
            </Field>

            {pets.length > 0 && (
              <Field style={{ flex: 1 }}>
                <Label>반려동물 (선택)</Label>
                <Select name="petIdx" value={formData.petIdx} onChange={handleChange}>
                  <option value="">선택 안함</option>
                  {pets.map(p => (
                    <option key={p.idx} value={p.idx}>{p.name}</option>
                  ))}
                </Select>
              </Field>
            )}
          </Row>

          <Field>
            <Label>설명</Label>
            <Textarea
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder="케어 요청 내용, 반려동물 특이사항 등을 입력하세요"
              rows={4}
            />
          </Field>

          <ButtonRow>
            <CancelButton type="button" onClick={onClose}>취소</CancelButton>
            <SubmitButton type="submit" disabled={loading}>
              {loading ? '등록 중...' : '요청 등록'}
            </SubmitButton>
          </ButtonRow>
        </Form>
      </Modal>
    </Overlay>
  );
};

export default CareCreateModal;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 16px;
`;

const Modal = styled.div`
  background: ${props => props.theme.colors.surface};
  border-radius: 16px;
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 8px 40px rgba(0,0,0,0.2);
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px 12px;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  position: sticky;
  top: 0;
  background: ${props => props.theme.colors.surface};
  z-index: 1;
`;

const ModalTitle = styled.h2`
  font-size: 17px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
`;

const CloseBtn = styled.button`
  background: none;
  border: none;
  font-size: 16px;
  color: ${props => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  &:hover { background: ${props => props.theme.colors.surfaceHover}; }
`;

const Form = styled.form`
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const Row = styled.div`
  display: flex;
  gap: 12px;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: ${props => props.theme.colors.textSecondary};
`;

const BalanceHint = styled.span`
  font-weight: 400;
  color: ${props => props.theme.colors.textSecondary};
`;

const Input = styled.input`
  padding: 9px 12px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: 14px;
  outline: none;
  width: 100%;
  box-sizing: border-box;
  &:focus { border-color: ${props => props.theme.colors.primary}; }
`;

const Select = styled.select`
  padding: 9px 12px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: 14px;
  outline: none;
  width: 100%;
  box-sizing: border-box;
  &:focus { border-color: ${props => props.theme.colors.primary}; }
`;

const Textarea = styled.textarea`
  padding: 9px 12px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: 14px;
  outline: none;
  resize: none;
  line-height: 1.5;
  &:focus { border-color: ${props => props.theme.colors.primary}; }
`;

const LocationInputWrapper = styled.div`
  position: relative;
`;

const SearchingHint = styled.div`
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
`;

const ResultsList = styled.div`
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  z-index: 100;
  overflow: hidden;
`;

const ResultItem = styled.div`
  padding: 10px 12px;
  font-size: 13px;
  cursor: pointer;
  color: ${props => props.theme.colors.text};
  &:hover { background: ${props => props.theme.colors.surfaceHover}; }
  & + & { border-top: 1px solid ${props => props.theme.colors.borderLight || props.theme.colors.border}; }
`;

const LocationLabelRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const MapToggleBtn = styled.button`
  background: none;
  border: none;
  font-size: 12px;
  color: ${props => props.theme.colors.domain.care};
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
`;

const ResultAddress = styled.div`
  font-size: 13px;
`;

const ResultSub = styled.div`
  font-size: 11px;
  color: ${props => props.theme.colors.textSecondary};
  margin-top: 2px;
`;

const ErrorMsg = styled.span`
  font-size: 12px;
  color: ${props => props.theme.colors.error || '#ef4444'};
`;

const ButtonRow = styled.div`
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 4px;
`;

const CancelButton = styled.button`
  padding: 9px 18px;
  border-radius: 8px;
  border: 1px solid ${props => props.theme.colors.border};
  background: none;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 14px;
  cursor: pointer;
`;

const SubmitButton = styled.button`
  padding: 9px 20px;
  border-radius: 8px;
  border: none;
  background: ${props => props.theme.colors.domain.care};
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:hover:not(:disabled) { opacity: 0.9; }
`;
