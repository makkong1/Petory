import React, { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { geocodingApi } from '../../api/geocodingApi';

const AddressMapSelector = ({ onAddressSelect, initialAddress, initialLat, initialLng }) => {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const [selectedAddress, setSelectedAddress] = useState(initialAddress || '');
  const [selectedLat, setSelectedLat] = useState(initialLat || null);
  const [selectedLng, setSelectedLng] = useState(initialLng || null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const initMap = () => {
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => {
          initializeMap();
        });
      } else {
        setTimeout(initMap, 100);
      }
    };

    initMap();
  }, []);

  const initializeMap = () => {
    const defaultLat = initialLat || 37.5665;
    const defaultLng = initialLng || 126.9780;
    const defaultPosition = new window.kakao.maps.LatLng(defaultLat, defaultLng);

    const mapOption = {
      center: defaultPosition,
      level: 3,
    };

    const map = new window.kakao.maps.Map(mapRef.current, mapOption);
    mapInstanceRef.current = map;

    // 초기 위치에 마커 표시
    if (initialLat && initialLng) {
      addMarker(defaultPosition);
    }

    // 지도 클릭 이벤트
    window.kakao.maps.event.addListener(map, 'click', (mouseEvent) => {
      const latlng = mouseEvent.latLng;
      addMarker(latlng);
      reverseGeocode(latlng.getLat(), latlng.getLng());
    });
  };

  const addMarker = (position) => {
    // 기존 마커 제거
    if (markerRef.current) {
      markerRef.current.setMap(null);
    }

    // 새 마커 추가
    const marker = new window.kakao.maps.Marker({
      position: position,
      map: mapInstanceRef.current,
    });

    markerRef.current = marker;
    mapInstanceRef.current.panTo(position);
  };

  const reverseGeocode = async (lat, lng) => {
    setIsLoading(true);
    try {
      // 카카오맵 Geocoder 사용
      const geocoder = new window.kakao.maps.services.Geocoder();
      const coord = new window.kakao.maps.LatLng(lat, lng);

      geocoder.coord2Address(coord.getLng(), coord.getLat(), (result, status) => {
        setIsLoading(false);
        if (status === window.kakao.maps.services.Status.OK) {
          const address = result[0].road_address
            ? result[0].road_address.address_name
            : result[0].address.address_name;

          setSelectedAddress(address);
          setSelectedLat(lat);
          setSelectedLng(lng);

          if (onAddressSelect) {
            onAddressSelect({
              address,
              latitude: lat,
              longitude: lng,
            });
          }
        } else {
          // 주소 변환 실패 시 좌표만 저장
          setSelectedAddress(`위도: ${lat.toFixed(6)}, 경도: ${lng.toFixed(6)}`);
          setSelectedLat(lat);
          setSelectedLng(lng);

          if (onAddressSelect) {
            onAddressSelect({
              address: `위도: ${lat.toFixed(6)}, 경도: ${lng.toFixed(6)}`,
              latitude: lat,
              longitude: lng,
            });
          }
        }
      });
    } catch (error) {
      setIsLoading(false);
      console.error('주소 변환 실패:', error);
    }
  };

  const handleAddressSearch = async () => {
    if (!selectedAddress.trim()) return;

    setIsLoading(true);
    try {
      const response = await geocodingApi.addressToCoordinates(selectedAddress);
      if (response && response.length === 2) {
        const [lat, lng] = response;
        const position = new window.kakao.maps.LatLng(lat, lng);
        addMarker(position);
        setSelectedLat(lat);
        setSelectedLng(lng);

        if (onAddressSelect) {
          onAddressSelect({
            address: selectedAddress,
            latitude: lat,
            longitude: lng,
          });
        }
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
          placeholder="주소를 입력하거나 지도를 클릭하세요"
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
      <MapDiv ref={mapRef} />
      <HelperText>지도를 클릭하여 위치를 선택하거나 주소를 검색하세요</HelperText>
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

