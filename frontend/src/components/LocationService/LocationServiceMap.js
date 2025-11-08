import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import styled from 'styled-components';
import MapContainer from './MapContainer';
import { locationServiceApi } from '../../api/locationServiceApi';
import { geocodingApi } from '../../api/geocodingApi';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 }; // 서울 시청
const DEFAULT_RADIUS = 3000;
const MAP_DEFAULT_LEVEL = 4;

const SIDOS = [
  '서울특별시',
  '부산광역시',
  '대구광역시',
  '인천광역시',
  '광주광역시',
  '대전광역시',
  '울산광역시',
  '세종특별자치시',
  '경기도',
  '강원특별자치도',
  '충청북도',
  '충청남도',
  '전북특별자치도',
  '전라남도',
  '경상북도',
  '경상남도',
  '제주특별자치도',
];

const SIGUNGUS = {
  '서울특별시': [
    '강남구', '강동구', '강북구', '강서구', '관악구', '광진구', '구로구', '금천구',
    '노원구', '도봉구', '동대문구', '동작구', '마포구', '서대문구', '서초구', '성동구',
    '성북구', '송파구', '양천구', '영등포구', '용산구', '은평구', '종로구', '중구', '중랑구',
  ],
  '부산광역시': ['중구', '서구', '동구', '영도구', '부산진구', '동래구', '남구', '북구', '해운대구', '사하구'],
  '대구광역시': ['중구', '동구', '서구', '남구', '북구', '수성구', '달서구'],
  '인천광역시': ['중구', '동구', '미추홀구', '연수구', '남동구', '부평구', '계양구', '서구'],
  '광주광역시': ['동구', '서구', '남구', '북구', '광산구'],
  '대전광역시': ['동구', '중구', '서구', '유성구', '대덕구'],
  '울산광역시': ['중구', '남구', '동구', '북구', '울주군'],
  '세종특별자치시': ['세종시'],
  '경기도': [
    '수원시', '성남시', '고양시', '용인시', '부천시', '안산시', '안양시', '남양주시',
    '화성시', '평택시', '의정부시', '시흥시', '김포시', '광명시', '하남시', '이천시',
  ],
  '강원특별자치도': ['춘천시', '원주시', '강릉시', '동해시', '속초시'],
  '충청북도': ['청주시', '충주시', '제천시', '보은군', '옥천군'],
  '충청남도': ['천안시', '공주시', '아산시', '서산시', '논산시'],
  '전북특별자치도': ['전주시', '군산시', '익산시', '정읍시', '남원시'],
  '전라남도': ['목포시', '여수시', '순천시', '나주시', '광양시'],
  '경상북도': ['포항시', '경주시', '김천시', '안동시', '구미시'],
  '경상남도': ['창원시', '진주시', '통영시', '사천시', '김해시'],
  '제주특별자치도': ['제주시', '서귀포시'],
};

const levelToRadius = (level) => {
  const mapping = {
    1: 200,
    2: 400,
    3: 800,
    4: 1500,
    5: 3000,
    6: 6000,
    7: 10000,
  };
  return mapping[level] || DEFAULT_RADIUS;
};

const calculateDistance = (lat1, lng1, lat2, lng2) => {
  if (
    typeof lat1 !== 'number' ||
    typeof lng1 !== 'number' ||
    typeof lat2 !== 'number' ||
    typeof lng2 !== 'number'
  ) {
    return null;
  }

  const toRad = (value) => (value * Math.PI) / 180;
  const R = 6371e3;
  const φ1 = toRad(lat1);
  const φ2 = toRad(lat2);
  const Δφ = toRad(lat2 - lat1);
  const Δλ = toRad(lng2 - lng1);

  const a =
    Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
    Math.cos(φ1) * Math.cos(φ2) *
      Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c); // meters
};

const formatDistance = (meters) => {
  if (meters == null) return null;
  if (meters >= 1000) {
    return `${(meters / 1000).toFixed(1)} km`;
  }
  return `${meters} m`;
};

const LocationServiceMap = () => {
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [statusMessage, setStatusMessage] = useState('지도 준비 중...');
  const [keyword, setKeyword] = useState('반려동물');
  const [addressQuery, setAddressQuery] = useState('');
  const [searchMode, setSearchMode] = useState('keyword');
  const [selectedSido, setSelectedSido] = useState('');
  const [selectedSigungu, setSelectedSigungu] = useState('');
  const [selectedService, setSelectedService] = useState(null);
  const [userLocation, setUserLocation] = useState(null);
  const [mapCenter, setMapCenter] = useState(DEFAULT_CENTER);
  const [mapLevel, setMapLevel] = useState(MAP_DEFAULT_LEVEL);
  const programmaticCenterRef = useRef(null);
  const latestRequestRef = useRef(0);
  const lastFetchedRef = useRef({ lat: null, lng: null, level: null });
  const mapStateRef = useRef({
    center: DEFAULT_CENTER,
    level: MAP_DEFAULT_LEVEL,
  });
  const fetchServicesRef = useRef(null);
  const suppressNextFetchRef = useRef(false);

  useEffect(() => {
    mapStateRef.current = {
      center: mapCenter,
      level: mapLevel,
    };
  }, [mapCenter, mapLevel]);

  const fetchServices = useCallback(
    async ({
      latitude,
      longitude,
      region,
      keywordOverride,
      level,
    }) => {
      const requestId = Date.now();
      latestRequestRef.current = requestId;

      setLoading(true);
      setStatusMessage('지도 데이터 불러오는 중...');
      setError(null);

      const { center, level: currentLevel } = mapStateRef.current;
      const effectiveLatitude = typeof latitude === 'number' ? latitude : center.lat;
      const effectiveLongitude = typeof longitude === 'number' ? longitude : center.lng;
      const effectiveLevel = typeof level === 'number' ? level : currentLevel;
      const effectiveRadius = levelToRadius(effectiveLevel);

      try {
        const response = await locationServiceApi.searchPlaces({
          keyword: keywordOverride ?? keyword,
          region,
          latitude: effectiveLatitude,
          longitude: effectiveLongitude,
          radius: effectiveRadius,
          size: 100,
        });

        if (latestRequestRef.current !== requestId) {
          return;
        }

        const fetchedServices = (response.data?.services || []).map((service) => {
          const distance = calculateDistance(
            effectiveLatitude,
            effectiveLongitude,
            service.latitude,
            service.longitude
          );

          return {
            ...service,
            distance,
          };
        });

        setServices(fetchedServices);
        setSelectedService(null);
        if (fetchedServices.length === 0) {
          setStatusMessage('주변에 표시할 장소가 없습니다.');
        } else {
          setStatusMessage('');
        }
      } catch (err) {
        if (latestRequestRef.current !== requestId) {
          return;
        }

        const message = err.response?.data?.error || err.message;
        setError(`장소 정보를 불러오지 못했습니다: ${message}`);
        setStatusMessage('');
      } finally {
        if (latestRequestRef.current === requestId) {
          setLoading(false);
        }
        lastFetchedRef.current = {
          lat: effectiveLatitude,
          lng: effectiveLongitude,
          level: effectiveLevel,
        };
      }
    },
    [keyword]
  );

  useEffect(() => {
    fetchServicesRef.current = fetchServices;
  }, [fetchServices]);

  useEffect(() => {
    const tryGeolocation = () => {
      if (!navigator.geolocation) {
        fetchServicesRef.current?.({
          latitude: DEFAULT_CENTER.lat,
          longitude: DEFAULT_CENTER.lng,
          level: MAP_DEFAULT_LEVEL,
        });
        return;
      }

      navigator.geolocation.getCurrentPosition(
        (position) => {
          const location = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          };
          setUserLocation(location);
          setMapCenter(location);
          programmaticCenterRef.current = location;
          fetchServicesRef.current?.({
            latitude: location.lat,
            longitude: location.lng,
            level: MAP_DEFAULT_LEVEL,
          });
        },
        () => {
          fetchServicesRef.current?.({
            latitude: DEFAULT_CENTER.lat,
            longitude: DEFAULT_CENTER.lng,
            level: MAP_DEFAULT_LEVEL,
          });
        }
      );
    };

    tryGeolocation();
  }, []);

  const handleMapDragStart = useCallback(() => {
    setStatusMessage('지도 조정 중...');
  }, []);

  const handleMapIdle = useCallback(
    ({ lat, lng, level }) => {
      const nextCenter = { lat, lng };

      if (
        !mapCenter ||
        Math.abs(mapCenter.lat - lat) > 0.00001 ||
        Math.abs(mapCenter.lng - lng) > 0.00001
      ) {
        setMapCenter(nextCenter);
      }

      if (mapLevel !== level) {
        setMapLevel(level);
      }

      const plannedCenter = programmaticCenterRef.current;
      const centersAreClose = (a, b) =>
        a &&
        b &&
        Math.abs(a.lat - b.lat) < 0.00001 &&
        Math.abs(a.lng - b.lng) < 0.00001;

      if (centersAreClose(plannedCenter, nextCenter)) {
        programmaticCenterRef.current = null;
        return;
      }

      if (suppressNextFetchRef.current) {
        suppressNextFetchRef.current = false;
        programmaticCenterRef.current = null;
        return;
      }

      const prevFetch = lastFetchedRef.current;
      if (prevFetch.lat != null && prevFetch.lng != null) {
        const movedDistance = calculateDistance(prevFetch.lat, prevFetch.lng, lat, lng);
        if (movedDistance != null && movedDistance < 50 && prevFetch.level === level) {
          programmaticCenterRef.current = null;
          return;
        }
      }

      lastFetchedRef.current = { lat, lng, level };
      programmaticCenterRef.current = null;
      fetchServices({
        latitude: lat,
        longitude: lng,
        level,
      });
    },
    [fetchServices, mapCenter, mapLevel]
  );

  const handleKeywordSubmit = useCallback(
    (event) => {
      event.preventDefault();
      if (mapCenter) {
        programmaticCenterRef.current = { ...mapCenter };
        lastFetchedRef.current = {
          lat: mapCenter.lat,
          lng: mapCenter.lng,
          level: mapLevel,
        };
      }
      fetchServices({
        latitude: mapCenter.lat,
        longitude: mapCenter.lng,
        keywordOverride: keyword,
        level: mapLevel,
      });
    },
    [fetchServices, keyword, mapCenter, mapLevel]
  );

  const handleAddressSearch = useCallback(async () => {
    if (!addressQuery.trim()) {
      return;
    }

    try {
      setStatusMessage('주소를 찾는 중...');
      setError(null);

      const response = await geocodingApi.addressToCoordinates(addressQuery.trim());
      if (!response.success || !response.latitude || !response.longitude) {
        setStatusMessage('해당 주소를 찾을 수 없습니다.');
        return;
      }

      const location = {
        lat: response.latitude,
        lng: response.longitude,
      };

      setMapCenter(location);
      programmaticCenterRef.current = location;
      lastFetchedRef.current = {
        lat: location.lat,
        lng: location.lng,
        level: mapLevel,
      };

      fetchServices({
        latitude: location.lat,
        longitude: location.lng,
        keywordOverride: keyword,
        level: mapLevel,
        region: addressQuery.trim(),
      });
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`주소 검색에 실패했습니다: ${message}`);
      setStatusMessage('');
    }
  }, [addressQuery, fetchServices, keyword, mapLevel]);

  const handleRegionSearch = useCallback(async () => {
    if (!selectedSido) {
      setStatusMessage('검색할 시/도를 선택해주세요.');
      return;
    }

    const targetRegion = selectedSigungu ? `${selectedSido} ${selectedSigungu}` : selectedSido;

    try {
      setStatusMessage(`'${targetRegion}' 주변 장소를 검색하는 중...`);
      setError(null);

      const response = await geocodingApi.addressToCoordinates(targetRegion);
      if (!response.success || !response.latitude || !response.longitude) {
        setStatusMessage('해당 지역의 좌표를 찾지 못했습니다. 다른 지역을 선택해 주세요.');
        return;
      }

      const location = {
        lat: response.latitude,
        lng: response.longitude,
      };

      setMapCenter(location);
      programmaticCenterRef.current = location;
      lastFetchedRef.current = {
        lat: location.lat,
        lng: location.lng,
        level: mapLevel,
      };

      fetchServices({
        latitude: location.lat,
        longitude: location.lng,
        keywordOverride: keyword,
        level: mapLevel,
        region: targetRegion,
      });
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`지역 검색에 실패했습니다: ${message}`);
      setStatusMessage('');
    }
  }, [fetchServices, keyword, selectedSido, selectedSigungu, mapLevel]);

  const servicesWithDisplay = useMemo(() =>
    services.map((service, index) => ({
      ...service,
      key: service.externalId || service.placeUrl || `${service.latitude}-${service.longitude}-${index}`,
      distanceLabel: formatDistance(service.distance),
    })),
    [services]
  );

  const handleServiceSelect = useCallback((service) => {
    setSelectedService(service);

    if (service?.latitude && service?.longitude) {
      suppressNextFetchRef.current = true;
      const center = { lat: service.latitude, lng: service.longitude };
      programmaticCenterRef.current = center;
      setMapCenter(center);
    }
  }, []);

  const handleRecenterToUser = useCallback(() => {
    if (!userLocation) {
      return;
    }

    const center = { ...userLocation };
    programmaticCenterRef.current = center;
    setMapCenter(center);
    lastFetchedRef.current = {
      lat: center.lat,
      lng: center.lng,
      level: mapLevel,
    };
    fetchServices({
      latitude: center.lat,
      longitude: center.lng,
      level: mapLevel,
    });
  }, [fetchServices, mapLevel, userLocation]);

  return (
    <Container>
      <Header>
        <HeaderTop>
          <Title>지도에서 반려동물 서비스 찾기</Title>
          <HeaderActions>
            <SearchModeTabs>
              <SearchModeButton
                type="button"
                active={searchMode === 'keyword'}
                onClick={() => setSearchMode('keyword')}
              >
                키워드 검색
              </SearchModeButton>
              <SearchModeButton
                type="button"
                active={searchMode === 'region'}
                onClick={() => setSearchMode('region')}
              >
                지역 선택
              </SearchModeButton>
            </SearchModeTabs>
            <CurrentLocationButton
              type="button"
              onClick={handleRecenterToUser}
              disabled={!userLocation}
            >
              내 위치로 이동
            </CurrentLocationButton>
          </HeaderActions>
        </HeaderTop>

        {searchMode === 'keyword' ? (
          <SearchControls>
            <SearchBar onSubmit={handleKeywordSubmit}>
              <SearchInput
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="검색어 (예: 반려동물카페, 동물병원 등)"
              />
              <SearchButton type="submit">검색</SearchButton>
            </SearchBar>
            <AddressBox>
              <SearchInput
                value={addressQuery}
                onChange={(e) => setAddressQuery(e.target.value)}
                placeholder="원하는 위치를 입력하세요 (예: 서울 강남구)"
              />
              <SearchButton type="button" onClick={handleAddressSearch}>
                위치 이동
              </SearchButton>
            </AddressBox>
          </SearchControls>
        ) : (
          <RegionControls>
            <RegionSelect
              value={selectedSido}
              onChange={(e) => {
                setSelectedSido(e.target.value);
                setSelectedSigungu('');
              }}
            >
              <option value="">시/도 선택</option>
              {SIDOS.map((sido) => (
                <option key={sido} value={sido}>
                  {sido}
                </option>
              ))}
            </RegionSelect>
            <RegionSelect
              value={selectedSigungu}
              onChange={(e) => setSelectedSigungu(e.target.value)}
              disabled={!selectedSido || !SIGUNGUS[selectedSido]}
            >
              <option value="">시/군/구 선택 (선택)</option>
              {selectedSido && SIGUNGUS[selectedSido]?.map((sigungu) => (
                <option key={sigungu} value={sigungu}>
                  {sigungu}
                </option>
              ))}
            </RegionSelect>
            <RegionSearchButton
              type="button"
              disabled={!selectedSido}
              onClick={handleRegionSearch}
            >
              지역 검색
            </RegionSearchButton>
          </RegionControls>
        )}
      </Header>

      {statusMessage && (
        <StatusBanner>{statusMessage}</StatusBanner>
      )}

      {error && (
        <ErrorBanner>
          {error}
          <button onClick={() => setError(null)}>닫기</button>
        </ErrorBanner>
      )}

      <MapArea>
        <MapWrapper>
          <MapContainer
            services={servicesWithDisplay}
            onServiceClick={setSelectedService}
            userLocation={userLocation}
            mapCenter={mapCenter}
            onMapDragStart={handleMapDragStart}
            onMapIdle={handleMapIdle}
          />

          {loading && (
            <LoadingOverlay>
              <div>데이터 불러오는 중...</div>
            </LoadingOverlay>
          )}
        </MapWrapper>

        <ServiceListPanel>
          <ServiceListHeader>
            <ServiceListTitle>
              내 주변 장소 ({servicesWithDisplay.length})
            </ServiceListTitle>
          </ServiceListHeader>
          <ServiceListContent>
            {servicesWithDisplay.length === 0 ? (
              <EmptyMessage>주변에 표시할 장소가 없습니다.</EmptyMessage>
            ) : (
              servicesWithDisplay.map((service) => (
                <ServiceListItem
                  key={service.key}
                  active={selectedService?.key === service.key}
                  onClick={() => handleServiceSelect(service)}
                >
                  <ServiceListItemHeader>
                    <ServiceListItemName>{service.name}</ServiceListItemName>
                    {service.distanceLabel && (
                      <ServiceDistance>{service.distanceLabel}</ServiceDistance>
                    )}
                  </ServiceListItemHeader>
                  {service.category && (
                    <ServiceListItemCategory>{service.category}</ServiceListItemCategory>
                  )}
                  {service.address && (
                    <ServiceListItemAddress>{service.address}</ServiceListItemAddress>
                  )}
                  <ServiceActions>
                    {service.phone && <span>📞 {service.phone}</span>}
                    {service.placeUrl && (
                      <ServiceLink
                        href={service.placeUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        onClick={(e) => e.stopPropagation()}
                      >
                        카카오맵 열기 ↗
                      </ServiceLink>
                    )}
                  </ServiceActions>
                </ServiceListItem>
              ))
            )}
          </ServiceListContent>
        </ServiceListPanel>

        {selectedService && (
          <ServiceDetailPanel>
            <CloseButton onClick={() => setSelectedService(null)}>✕</CloseButton>
            <ServiceTitle>{selectedService.name}</ServiceTitle>
            <ServiceInfo>
              {selectedService.category && (
                <ServiceInfoItem>
                  <strong>분류</strong>
                  <span>{selectedService.category}</span>
                </ServiceInfoItem>
              )}
              {selectedService.address && (
                <ServiceInfoItem>
                  <strong>주소</strong>
                  <span>{selectedService.address}</span>
                </ServiceInfoItem>
              )}
              {selectedService.phone && (
                <ServiceInfoItem>
                  <strong>전화</strong>
                  <span>{selectedService.phone}</span>
                </ServiceInfoItem>
              )}
              {selectedService.distanceLabel && (
                <ServiceInfoItem>
                  <strong>거리</strong>
                  <span>{selectedService.distanceLabel}</span>
                </ServiceInfoItem>
              )}
            </ServiceInfo>
            {selectedService.placeUrl && (
              <DetailLink
                href={selectedService.placeUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                카카오맵에서 자세히 보기 ↗
              </DetailLink>
            )}
          </ServiceDetailPanel>
        )}
      </MapArea>
    </Container>
  );
};

export default LocationServiceMap;

const Container = styled.div`
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f4f6f9;
  overflow: hidden;
`;

const Header = styled.div`
  padding: 1rem 1.5rem;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
`;

const HeaderTop = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
`;

const Title = styled.h1`
  margin: 0;
  color: #1f2933;
  font-size: 1.5rem;
  font-weight: 700;
`;

const SearchControls = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
`;

const SearchBar = styled.form`
  display: flex;
  gap: 0.5rem;
  flex: 1;
  min-width: 260px;
`;

const AddressBox = styled.div`
  display: flex;
  gap: 0.5rem;
  flex: 1;
  min-width: 260px;
`;

const SearchInput = styled.input`
  flex: 1;
  min-width: 220px;
  padding: 0.6rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  font-size: 0.95rem;
  color: #1f2933;

  &:focus {
    outline: none;
    border-color: #2563eb;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
  }
`;

const SearchButton = styled.button`
  padding: 0.55rem 1.2rem;
  background: #2563eb;
  color: #ffffff;
  border: none;
  border-radius: 999px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: #1d4ed8;
  }

  &:active {
    background: #1e40af;
  }
`;

const SearchModeTabs = styled.div`
  display: inline-flex;
  padding: 0.35rem;
  border-radius: 999px;
  background: #e5e7eb;
  gap: 0.25rem;
`;

const HeaderActions = styled.div`
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: flex-end;
`;

const SearchModeButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 0.4rem 0.9rem;
  border: none;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  background: ${(props) => (props.active ? '#1d4ed8' : 'transparent')};
  color: ${(props) => (props.active ? '#ffffff' : '#374151')};
  transition: background 0.2s ease, color 0.2s ease;

  &:hover {
    background: ${(props) => (props.active ? '#1e40af' : '#d1d5db')};
  }
`;

const CurrentLocationButton = styled.button`
  padding: 0.45rem 1rem;
  border: none;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  background: #10b981;
  color: #ffffff;
  transition: background 0.2s ease, transform 0.1s ease;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;

  &:hover:enabled {
    background: #059669;
  }

  &:active:enabled {
    transform: scale(0.97);
  }

  &:disabled {
    background: #d1d5db;
    color: #6b7280;
    cursor: not-allowed;
  }
`;

const RegionControls = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
`;

const RegionSelect = styled.select`
  padding: 0.55rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  font-size: 0.95rem;
  min-width: 200px;
  background: #ffffff;
  color: #1f2933;

  &:focus {
    outline: none;
    border-color: #2563eb;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
  }

  &:disabled {
    background: #f3f4f6;
    color: #9ca3af;
    cursor: not-allowed;
  }
`;

const RegionSearchButton = styled(SearchButton)`
  min-width: 120px;
  opacity: ${(props) => (props.disabled ? 0.6 : 1)};
  cursor: ${(props) => (props.disabled ? 'not-allowed' : 'pointer')};
`;

const StatusBanner = styled.div`
  padding: 0.75rem 1.5rem;
  background: #fff3cd;
  color: #856404;
  font-size: 0.95rem;
  border-bottom: 1px solid #ffeeba;
`;

const ErrorBanner = styled.div`
  padding: 0.75rem 1.5rem;
  background: #fdecea;
  color: #c0392b;
  font-size: 0.95rem;
  border-bottom: 1px solid #f5c6cb;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;

  button {
    background: none;
    border: none;
    color: inherit;
    font-weight: 600;
    cursor: pointer;
  }
`;

const MapArea = styled.div`
  flex: 1;
  position: relative;
  display: flex;
  background: #e5e7eb;
  min-height: 0;
`;

const MapWrapper = styled.div`
  flex: 1;
  position: relative;
  min-width: 0;
  min-height: 0;
`;

const LoadingOverlay = styled.div`
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #2563eb;
  z-index: 200;
`;

const ServiceListPanel = styled.div`
  width: 360px;
  background: #ffffff;
  border-left: 1px solid #d1d5db;
  display: flex;
  flex-direction: column;
  z-index: 150;
  height: 100%;
  min-height: 0;
  overflow: hidden;
`;

const ServiceListHeader = styled.div`
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
`;

const ServiceListTitle = styled.h3`
  margin: 0;
  font-size: 1rem;
  color: #111827;
`;

const ServiceListContent = styled.div`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0.75rem;
  padding-right: 0.35rem;
  scrollbar-width: thin;
  scrollbar-color: rgba(37, 99, 235, 0.45) rgba(226, 232, 240, 0.7);

  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: rgba(226, 232, 240, 0.6);
    border-radius: 999px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(37, 99, 235, 0.45);
    border-radius: 999px;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: rgba(37, 99, 235, 0.65);
  }
`;

const ServiceListItem = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 0.9rem 1rem;
  margin-bottom: 0.6rem;
  border: 1px solid ${props => (props.active ? '#2563eb' : '#e5e7eb')};
  border-radius: 10px;
  background: ${props => (props.active ? '#eef2ff' : '#ffffff')};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: #2563eb;
    box-shadow: 0 8px 16px rgba(37, 99, 235, 0.12);
    transform: translateY(-2px);
  }
`;

const ServiceListItemHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.35rem;
`;

const ServiceListItemName = styled.div`
  font-weight: 600;
  font-size: 1rem;
  color: #1f2937;
  flex: 1;
`;

const ServiceDistance = styled.span`
  font-size: 0.85rem;
  color: #2563eb;
  font-weight: 600;
`;

const ServiceListItemCategory = styled.div`
  font-size: 0.85rem;
  color: #4b5563;
  margin-bottom: 0.25rem;
`;

const ServiceListItemAddress = styled.div`
  font-size: 0.85rem;
  color: #6b7280;
  margin-bottom: 0.4rem;
  line-height: 1.4;
`;

const ServiceActions = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #4b5563;
`;

const ServiceLink = styled.a`
  color: #2563eb;
  font-weight: 600;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
`;

const EmptyMessage = styled.div`
  padding: 2rem 1rem;
  text-align: center;
  color: #6b7280;
  font-size: 0.95rem;
`;

const ServiceDetailPanel = styled.div`
  position: absolute;
  top: 1rem;
  left: 1rem;
  width: 320px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 15px 35px rgba(30, 41, 59, 0.2);
  padding: 1.25rem;
  z-index: 300;
  max-height: calc(100vh - 2rem);
  overflow-y: auto;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 0.7rem;
  right: 0.8rem;
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  color: #6b7280;

  &:hover {
    color: #111827;
  }
`;

const ServiceTitle = styled.h3`
  margin: 0 0 1rem 0;
  color: #111827;
  font-size: 1.25rem;
`;

const ServiceInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  font-size: 0.95rem;
  color: #374151;
`;

const ServiceInfoItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem;

  strong {
    color: #6b7280;
    font-size: 0.85rem;
    font-weight: 600;
  }

  span {
    color: #1f2937;
  }
`;

const DetailLink = styled.a`
  display: block;
  margin-top: 1.25rem;
  text-align: center;
  padding: 0.6rem 1rem;
  background: #2563eb;
  color: #ffffff;
  border-radius: 999px;
  text-decoration: none;
  font-weight: 600;

  &:hover {
    background: #1d4ed8;
  }
`;
