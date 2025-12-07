import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { geocodingApi } from '../../api/geocodingApi';

const AddressMapSelector = ({ onAddressSelect, initialAddress, initialLat, initialLng }) => {
  const [selectedAddress, setSelectedAddress] = useState(initialAddress || '');
  const [selectedLat, setSelectedLat] = useState(initialLat || null);
  const [selectedLng, setSelectedLng] = useState(initialLng || null);
  const [isLoading, setIsLoading] = useState(false);

  // initialLat, initialLng가 변경되면 주소만 업데이트
  useEffect(() => {
    if (initialLat && initialLng) {
      setSelectedLat(initialLat);
      setSelectedLng(initialLng);
    }
  }, [initialLat, initialLng]);

  const handleAddressSearch = async () => {
    if (!selectedAddress.trim()) return;

    setIsLoading(true);
    try {
      const searchQuery = selectedAddress.trim();

      // 백엔드 geocodingApi 사용
      const response = await geocodingApi.addressToCoordinates(searchQuery);

      // 응답 구조 확인
      if (response.success && response.latitude && response.longitude) {
        const lat = response.latitude;
        const lng = response.longitude;
        const address = response.address || searchQuery;

        setSelectedAddress(address);
        setSelectedLat(lat);
        setSelectedLng(lng);

        if (onAddressSelect) {
          onAddressSelect({
            address: address,
            latitude: lat,
            longitude: lng,
          });
        }
      } else {
        alert('주소를 찾을 수 없습니다. 더 구체적인 주소로 검색해주세요.');
      }
    } catch (error) {
      console.error('주소 검색 실패:', error);
      alert('주소를 찾을 수 없습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Container>
      <AddressInputRow>
        <AddressInput
          type="text"
          value={selectedAddress}
          onChange={(e) => setSelectedAddress(e.target.value)}
          placeholder="주소를 입력하세요"
          onKeyPress={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              handleAddressSearch();
            }
          }}
        />
        <SearchButton type="button" onClick={handleAddressSearch} disabled={isLoading}>
          {isLoading ? '검색 중...' : '검색'}
        </SearchButton>
      </AddressInputRow>
      {isLoading && <LoadingText>주소를 검색하는 중...</LoadingText>}
      {selectedAddress && !isLoading && (
        <SelectedAddress>
          <AddressLabel>선택된 위치:</AddressLabel>
          <AddressValue>{selectedAddress}</AddressValue>
        </SelectedAddress>
      )}
      {/* 지도 기능 비활성화 - 네이버맵 사용 안 함 */}
      <HelperText>주소를 입력하고 검색 버튼을 클릭하세요</HelperText>
    </Container>
  );
};

export default AddressMapSelector;

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const AddressInputRow = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
`;

const AddressInput = styled.input`
  flex: 1;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  font-size: 0.95rem;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const SearchButton = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.primary};
  background: ${(props) => props.theme.colors.primary};
  color: #ffffff;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.primaryDark};
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const LoadingText = styled.div`
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.9rem;
`;

const SelectedAddress = styled.div`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const AddressLabel = styled.span`
  font-size: 0.8rem;
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
`;

const AddressValue = styled.span`
  font-size: 0.95rem;
  color: ${(props) => props.theme.colors.text};
  font-weight: 500;
`;

const MapDiv = styled.div`
  width: 100%;
  height: 400px;
  border-radius: ${(props) => props.theme.borderRadius.lg};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.border};
`;

const HelperText = styled.span`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

