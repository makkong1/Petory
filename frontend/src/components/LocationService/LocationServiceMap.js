import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import styled from 'styled-components';
import MapContainer from './MapContainer';
import { locationServiceApi } from '../../api/locationServiceApi';
import { geocodingApi } from '../../api/geocodingApi';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 }; // 서울 시청
const DEFAULT_RADIUS = 3000;
const MAP_DEFAULT_LEVEL = 4;

const CATEGORY_DEFAULT = 'all';
const CATEGORY_CUSTOM = 'custom';

// 키워드 검색 카테고리 목록
const KEYWORD_CATEGORIES = [
  { value: '', label: '전체' },
  { value: '동물약국', label: '동물약국' },
  { value: '미술관', label: '미술관' },
  { value: '카페', label: '카페' },
  { value: '동물병원', label: '동물병원' },
  { value: '반려동물용품', label: '반려동물용품' },
  { value: '미용', label: '미용' },
  { value: '문예회관', label: '문예회관' },
  { value: '펜션', label: '펜션' },
  { value: '식당', label: '식당' },
  { value: '여행지', label: '여행지' },
  { value: '위탁관리', label: '위탁관리' },
  { value: '박물관', label: '박물관' },
  { value: '호텔', label: '호텔' },
];

const CATEGORY_PRESETS = {
  [CATEGORY_DEFAULT]: { label: '전체', keyword: '', categoryType: undefined },
  hospital: { label: '병원', keyword: '동물병원', categoryType: 'hospital' },
  cafe: { label: '애견카페', keyword: '애견카페', categoryType: 'cafe' },
  playground: { label: '놀이터', keyword: '반려견 놀이터', categoryType: 'playground' },
};

const CATEGORY_BUTTONS = ['hospital', 'cafe', 'playground'];

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

const levelToSize = (level) => {
  // 지도 레벨에 따라 가져올 데이터 개수 결정
  // 레벨이 낮을수록(축소, 넓은 화면) 더 많은 데이터
  // 레벨이 높을수록(확대, 좁은 화면) 적은 데이터
  const mapping = {
    1: 30,   // 매우 확대: 30개
    2: 40,   // 확대: 40개
    3: 50,   // 기본 확대: 50개
    4: 75,   // 기본: 75개
    5: 100,  // 약간 축소: 100개
    6: 125,  // 축소: 125개
    7: 150,  // 많이 축소: 150개
  };
  return mapping[level] || 50; // 기본값 50개
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
  const [allServices, setAllServices] = useState([]); // 전체 서비스 데이터 (하이브리드용)
  const [services, setServices] = useState([]); // 현재 표시할 서비스 (필터링된 데이터)
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [statusMessage, setStatusMessage] = useState('지도 준비 중...');
  const [keyword, setKeyword] = useState('');
  const [addressQuery, setAddressQuery] = useState('');
  const [categoryType, setCategoryType] = useState(CATEGORY_DEFAULT);
  const [searchMode, setSearchMode] = useState('keyword');
  const [selectedSido, setSelectedSido] = useState('');
  const [selectedSigungu, setSelectedSigungu] = useState('');
  const [selectedService, setSelectedService] = useState(null);
  const [userLocation, setUserLocation] = useState(null);
  const [mapCenter, setMapCenter] = useState(DEFAULT_CENTER);
  const [mapLevel, setMapLevel] = useState(MAP_DEFAULT_LEVEL);
  const [mapBounds, setMapBounds] = useState(null); // 지도 bounds (하이브리드용)
  const programmaticCenterRef = useRef(null);
  const latestRequestRef = useRef(0);
  const lastFetchedRef = useRef({ lat: null, lng: null, level: null });
  const mapStateRef = useRef({
    center: DEFAULT_CENTER,
    level: MAP_DEFAULT_LEVEL,
  });
  const fetchServicesRef = useRef(null);
  const suppressNextFetchRef = useRef(false);
  const isInitialLoadRef = useRef(true); // 초기 로드 여부

  useEffect(() => {
    mapStateRef.current = {
      center: mapCenter,
      level: mapLevel,
    };
  }, [mapCenter, mapLevel]);

  // 클라이언트에서 지도 bounds 기반 필터링 (하이브리드용)
  const filterServicesByBounds = useCallback((bounds, allServicesData) => {
    if (!bounds || !allServicesData || allServicesData.length === 0) {
      return [];
    }

    const { sw, ne } = bounds; // southwest, northeast
    return allServicesData.filter((service) => {
      if (typeof service.latitude !== 'number' || typeof service.longitude !== 'number') {
        return false;
      }
      return (
        service.latitude >= sw.lat &&
        service.latitude <= ne.lat &&
        service.longitude >= sw.lng &&
        service.longitude <= ne.lng
      );
    });
  }, []);

  const fetchServices = useCallback(
    async ({
      latitude,
      longitude,
      region,
      keywordOverride,
      level,
      categoryOverride,
      append = false, // 기존 서비스에 추가할지 여부
      isInitialLoad = false, // 초기 로드 여부 (하이브리드용)
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
      const effectiveSize = levelToSize(effectiveLevel);
      const effectiveCategoryType = categoryOverride ?? categoryType;
      const apiCategoryType =
        effectiveCategoryType &&
          effectiveCategoryType !== CATEGORY_DEFAULT &&
          effectiveCategoryType !== CATEGORY_CUSTOM
          ? effectiveCategoryType
          : undefined;

      try {
        // 초기 로드이거나 키워드/지역 검색이 아닌 경우 넓은 범위로 로드 (하이브리드)
        const shouldLoadWideRange = isInitialLoad || (!keywordOverride && !region);
        const requestRadius = shouldLoadWideRange ? null : effectiveRadius; // null이면 서버에서 20km로 처리

        const response = await locationServiceApi.searchPlaces({
          keyword: keywordOverride ?? keyword,
          region,
          latitude: effectiveLatitude,
          longitude: effectiveLongitude,
          radius: requestRadius, // 초기 로드 시 null (서버에서 20km 처리)
          size: shouldLoadWideRange ? 500 : effectiveSize, // 넓은 범위일 때 더 많이 가져오기
          categoryType: apiCategoryType,
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

        if (isInitialLoad || shouldLoadWideRange) {
          // 초기 로드: 전체 데이터를 allServices에 저장
          setAllServices(fetchedServices);
          // 현재 지도 bounds에 맞게 필터링
          if (mapBounds) {
            const filtered = filterServicesByBounds(mapBounds, fetchedServices);
            setServices(filtered);
          } else {
            // bounds가 없으면 일단 전체 표시 (나중에 bounds 업데이트 시 필터링)
            setServices(fetchedServices);
          }
          isInitialLoadRef.current = false;
        } else if (append) {
          // 기존 서비스에 추가 (중복 제거)
          setAllServices((prevAll) => {
            const existingIds = new Set(prevAll.map(s => s.externalId || `${s.latitude}-${s.longitude}`));
            const newServices = fetchedServices.filter(
              s => !existingIds.has(s.externalId || `${s.latitude}-${s.longitude}`)
            );
            const updated = [...prevAll, ...newServices];
            // bounds가 있으면 필터링
            if (mapBounds) {
              const filtered = filterServicesByBounds(mapBounds, updated);
              setServices(filtered);
            } else {
              setServices(updated);
            }
            return updated;
          });
        } else {
          // 키워드/지역 검색: allServices 업데이트 및 필터링
          setAllServices(fetchedServices);
          if (mapBounds) {
            const filtered = filterServicesByBounds(mapBounds, fetchedServices);
            setServices(filtered);
          } else {
            setServices(fetchedServices);
          }
        }

        // 상태 메시지 업데이트
        const displayServices = mapBounds && allServices.length > 0
          ? filterServicesByBounds(mapBounds, allServices)
          : fetchedServices;

        if (displayServices.length === 0) {
          setStatusMessage('주변에 표시할 장소가 없습니다.');
        } else {
          setStatusMessage('');
        }

        setSelectedService(null);
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
    [keyword, categoryType, mapBounds, filterServicesByBounds]
  );

  useEffect(() => {
    fetchServicesRef.current = fetchServices;
  }, [fetchServices]);

  useEffect(() => {
    const tryGeolocation = () => {
      if (!navigator.geolocation) {
        // geolocation이 없으면 기본값 사용
        setMapCenter(DEFAULT_CENTER);
        fetchServicesRef.current?.({
          latitude: DEFAULT_CENTER.lat,
          longitude: DEFAULT_CENTER.lng,
          level: MAP_DEFAULT_LEVEL,
          isInitialLoad: true, // 초기 로드
        });
        return;
      }

      // geolocation 옵션: 빠른 응답 우선
      const options = {
        enableHighAccuracy: false, // 빠른 응답을 위해 false
        timeout: 5000, // 5초 타임아웃
        maximumAge: 0, // 캐시 사용 안 함
      };

      navigator.geolocation.getCurrentPosition(
        (position) => {
          const location = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          };
          // userLocation을 먼저 설정 (MapContainer에서 우선 사용)
          setUserLocation(location);
          // 그 다음 mapCenter 설정
          setMapCenter(location);
          programmaticCenterRef.current = location;
          fetchServicesRef.current?.({
            latitude: location.lat,
            longitude: location.lng,
            level: MAP_DEFAULT_LEVEL,
            isInitialLoad: true, // 초기 로드
          });
        },
        (error) => {
          // 위치 정보를 가져오지 못하면 기본값 사용
          console.warn('위치 정보를 가져올 수 없습니다:', error);
          setMapCenter(DEFAULT_CENTER);
          fetchServicesRef.current?.({
            latitude: DEFAULT_CENTER.lat,
            longitude: DEFAULT_CENTER.lng,
            level: MAP_DEFAULT_LEVEL,
            isInitialLoad: true, // 초기 로드
          });
        },
        options
      );
    };

    tryGeolocation();
  }, []);

  const handleMapDragStart = useCallback(() => {
    setStatusMessage('지도 조정 중...');
  }, []);

  const handleMapIdle = useCallback(
    ({ lat, lng, level, bounds }) => {
      const nextCenter = { lat, lng };
      const prevLevel = mapLevel;
      const levelChanged = prevLevel !== level;

      if (
        !mapCenter ||
        Math.abs(mapCenter.lat - lat) > 0.00001 ||
        Math.abs(mapCenter.lng - lng) > 0.00001
      ) {
        setMapCenter(nextCenter);
      }

      if (levelChanged) {
        setMapLevel(level);
      }

      // bounds 업데이트 (하이브리드용)
      if (bounds) {
        setMapBounds(bounds);
      }

      const plannedCenter = programmaticCenterRef.current;
      const centersAreClose = (a, b) =>
        a &&
        b &&
        Math.abs(a.lat - b.lat) < 0.00001 &&
        Math.abs(a.lng - b.lng) < 0.00001;

      if (centersAreClose(plannedCenter, nextCenter)) {
        programmaticCenterRef.current = null;
        // bounds가 있으면 클라이언트 필터링만 수행
        if (bounds && allServices.length > 0) {
          const filtered = filterServicesByBounds(bounds, allServices);
          setServices(filtered);
        }
        return;
      }

      if (suppressNextFetchRef.current) {
        suppressNextFetchRef.current = false;
        programmaticCenterRef.current = null;
        return;
      }

      // 하이브리드: allServices가 있고 키워드/지역 검색이 아닌 경우 클라이언트 필터링만 수행
      // 단, 지도가 너무 멀리 이동했으면 추가 요청 필요
      if (allServices.length > 0 && bounds) {
        const prevFetch = lastFetchedRef.current;
        let shouldFetchFromServer = false;

        // 이전 위치와의 거리 확인
        if (prevFetch.lat != null && prevFetch.lng != null) {
          const movedDistance = calculateDistance(prevFetch.lat, prevFetch.lng, lat, lng);
          // 20km 이상 이동했으면 서버에서 추가 데이터 가져오기
          if (movedDistance != null && movedDistance > 20000) {
            shouldFetchFromServer = true;
          }
        }

        if (!shouldFetchFromServer) {
          // 클라이언트 필터링만 수행
          const filtered = filterServicesByBounds(bounds, allServices);
          setServices(filtered);
          // 상태 메시지 업데이트
          if (filtered.length === 0) {
            setStatusMessage('주변에 표시할 장소가 없습니다.');
          } else {
            setStatusMessage('');
          }
          lastFetchedRef.current = { lat, lng, level };
          programmaticCenterRef.current = null;
          return;
        }
      }

      // allServices가 없거나 범위를 벗어나면 서버 요청
      const prevFetch = lastFetchedRef.current;
      if (prevFetch.lat != null && prevFetch.lng != null) {
        const movedDistance = calculateDistance(prevFetch.lat, prevFetch.lng, lat, lng);
        // 레벨이 변경되지 않고 이동 거리가 작으면 스킵
        if (movedDistance != null && movedDistance < 50 && !levelChanged) {
          programmaticCenterRef.current = null;
          return;
        }
      }

      lastFetchedRef.current = { lat, lng, level };
      programmaticCenterRef.current = null;

      // 서버에서 데이터 가져오기
      fetchServices({
        latitude: lat,
        longitude: lng,
        level,
      });
    },
    [fetchServices, mapCenter, mapLevel, allServices, filterServicesByBounds]
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
      setCategoryType(CATEGORY_CUSTOM);
      fetchServices({
        latitude: mapCenter.lat,
        longitude: mapCenter.lng,
        keywordOverride: keyword,
        level: mapLevel,
        categoryOverride: CATEGORY_CUSTOM,
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
        categoryOverride: categoryType,
      });
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`주소 검색에 실패했습니다: ${message}`);
      setStatusMessage('');
    }
  }, [addressQuery, categoryType, fetchServices, keyword, mapLevel]);

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
        categoryOverride: categoryType,
      });
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`지역 검색에 실패했습니다: ${message}`);
      setStatusMessage('');
    }
  }, [categoryType, fetchServices, keyword, selectedSido, selectedSigungu, mapLevel]);

  const handleCategorySelect = useCallback(
    (nextCategory) => {
      if (!nextCategory || !CATEGORY_PRESETS[nextCategory]) {
        return;
      }

      const preset = CATEGORY_PRESETS[nextCategory];
      setCategoryType(nextCategory);
      if (preset.keyword) {
        setKeyword(preset.keyword);
      }

      const targetCenter = mapCenter || DEFAULT_CENTER;
      programmaticCenterRef.current = { ...targetCenter };
      lastFetchedRef.current = {
        lat: targetCenter.lat,
        lng: targetCenter.lng,
        level: mapLevel,
      };

      fetchServices({
        latitude: targetCenter.lat,
        longitude: targetCenter.lng,
        keywordOverride: preset.keyword ?? keyword,
        level: mapLevel,
        categoryOverride: nextCategory,
      });
    },
    [fetchServices, keyword, mapCenter, mapLevel]
  );

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
              <KeywordCategorySelect
                value={selectedKeywordCategory}
                onChange={(e) => {
                  const categoryValue = e.target.value;
                  setSelectedKeywordCategory(categoryValue);
                  setKeyword(categoryValue);
                  setCategoryType(CATEGORY_CUSTOM);
                  if (categoryValue) {
                    // 카테고리 선택 시 자동 검색
                    if (mapCenter) {
                      programmaticCenterRef.current = { ...mapCenter };
                      lastFetchedRef.current = {
                        lat: mapCenter.lat,
                        lng: mapCenter.lng,
                        level: mapLevel,
                      };
                    }
                    fetchServices({
                      latitude: mapCenter?.lat,
                      longitude: mapCenter?.lng,
                      keywordOverride: categoryValue,
                      level: mapLevel,
                      categoryOverride: CATEGORY_CUSTOM,
                    });
                  }
                }}
              >
                {KEYWORD_CATEGORIES.map((cat) => (
                  <option key={cat.value} value={cat.value}>
                    {cat.label}
                  </option>
                ))}
              </KeywordCategorySelect>
              <SearchInput
                value={keyword}
                onChange={(e) => {
                  setKeyword(e.target.value);
                  setSelectedKeywordCategory('');
                  setCategoryType(CATEGORY_CUSTOM);
                }}
                placeholder="직접 검색어 입력 (예: 반려동물카페, 동물병원 등)"
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
        <CategoryFilterBar>
          {CATEGORY_BUTTONS.map((buttonType) => {
            const preset = CATEGORY_PRESETS[buttonType];
            return (
              <CategoryButton
                key={buttonType}
                type="button"
                active={categoryType === buttonType}
                onClick={() => handleCategorySelect(buttonType)}
              >
                {preset.label}
              </CategoryButton>
            );
          })}
        </CategoryFilterBar>
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
                  isSelected={selectedService?.key === service.key}
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
              {selectedService.rating && (
                <ServiceInfoItem>
                  <strong>평점</strong>
                  <span>⭐ {selectedService.rating.toFixed(1)}</span>
                </ServiceInfoItem>
              )}
              {selectedService.category && (
                <ServiceInfoItem>
                  <strong>분류</strong>
                  <span>{selectedService.category}</span>
                </ServiceInfoItem>
              )}
              {selectedService.description && (
                <ServiceInfoItem>
                  <strong>설명</strong>
                  <span>{selectedService.description}</span>
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
                  <span>
                    <a href={`tel:${selectedService.phone}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                      {selectedService.phone}
                    </a>
                  </span>
                </ServiceInfoItem>
              )}
              {selectedService.website && (
                <ServiceInfoItem>
                  <strong>웹사이트</strong>
                  <span>
                    <a href={selectedService.website} target="_blank" rel="noopener noreferrer" style={{ color: 'inherit', textDecoration: 'underline' }}>
                      {selectedService.website}
                    </a>
                  </span>
                </ServiceInfoItem>
              )}
              {selectedService.operatingHours && (
                <ServiceInfoItem>
                  <strong>운영시간</strong>
                  <span>{selectedService.operatingHours}</span>
                </ServiceInfoItem>
              )}
              {selectedService.closedDay && (
                <ServiceInfoItem>
                  <strong>휴무일</strong>
                  <span>{selectedService.closedDay}</span>
                </ServiceInfoItem>
              )}
              {selectedService.priceInfo && (
                <ServiceInfoItem>
                  <strong>가격 정보</strong>
                  <span>{selectedService.priceInfo}</span>
                </ServiceInfoItem>
              )}
              {selectedService.parkingAvailable !== null && selectedService.parkingAvailable !== undefined && (
                <ServiceInfoItem>
                  <strong>주차</strong>
                  <span>{selectedService.parkingAvailable ? '가능' : '불가능'}</span>
                </ServiceInfoItem>
              )}
              {(selectedService.indoor !== null && selectedService.indoor !== undefined) ||
                (selectedService.outdoor !== null && selectedService.outdoor !== undefined) ? (
                <ServiceInfoItem>
                  <strong>장소 유형</strong>
                  <span>
                    {selectedService.indoor ? '실내' : ''}
                    {selectedService.indoor && selectedService.outdoor ? ' / ' : ''}
                    {selectedService.outdoor ? '실외' : ''}
                  </span>
                </ServiceInfoItem>
              ) : null}
              {selectedService.petFriendly !== null && selectedService.petFriendly !== undefined && (
                <ServiceInfoItem>
                  <strong>반려동물 동반</strong>
                  <span>{selectedService.petFriendly ? '✅ 가능' : '❌ 불가능'}</span>
                </ServiceInfoItem>
              )}
              {selectedService.isPetOnly !== null && selectedService.isPetOnly !== undefined && selectedService.isPetOnly && (
                <ServiceInfoItem>
                  <strong>반려동물 전용</strong>
                  <span>✅ 예</span>
                </ServiceInfoItem>
              )}
              {selectedService.petSize && (
                <ServiceInfoItem>
                  <strong>입장 가능 동물 크기</strong>
                  <span>{selectedService.petSize}</span>
                </ServiceInfoItem>
              )}
              {selectedService.petRestrictions && (
                <ServiceInfoItem>
                  <strong>반려동물 제한사항</strong>
                  <span>{selectedService.petRestrictions}</span>
                </ServiceInfoItem>
              )}
              {selectedService.petExtraFee && (
                <ServiceInfoItem>
                  <strong>애견 동반 추가 요금</strong>
                  <span>{selectedService.petExtraFee}</span>
                </ServiceInfoItem>
              )}
              {selectedService.distanceLabel && (
                <ServiceInfoItem>
                  <strong>거리</strong>
                  <span>{selectedService.distanceLabel}</span>
                </ServiceInfoItem>
              )}
            </ServiceInfo>
            <DetailActions>
              {selectedService.placeUrl && (
                <DetailLink
                  href={selectedService.placeUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  카카오맵에서 자세히 보기 ↗
                </DetailLink>
              )}
              {selectedService.website && (
                <DetailLink
                  href={selectedService.website}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ marginTop: '0.5rem' }}
                >
                  웹사이트 방문 ↗
                </DetailLink>
              )}
            </DetailActions>
          </ServiceDetailPanel>
        )}
      </MapArea>
    </Container>
  );
};

export default LocationServiceMap;

const Container = styled.div`
  width: 100%;
  height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
  background: ${props => props.theme.colors.background};
  overflow: hidden;
`;

const Header = styled.div`
  padding: 1rem 2rem;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
`;

const CategoryFilterBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
`;

const CategoryButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 0.5rem 1rem;
  border-radius: 8px;
  border: 1px solid ${(props) => (props.active ? props.theme.colors.primary : props.theme.colors.border)};
  background: ${(props) => (props.active ? props.theme.colors.primary : props.theme.colors.surface)};
  color: ${(props) => (props.active ? '#ffffff' : props.theme.colors.text)};
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    background: ${(props) => (props.active ? props.theme.colors.primary + 'dd' : props.theme.colors.primary)};
    color: white;
    transform: translateY(-1px);
  }
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
  color: ${props => props.theme.colors.text};
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
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 0.95rem;
  color: ${props => props.theme.colors.text};
  background: ${props => props.theme.colors.surface};

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 0 3px ${props => props.theme.colors.primary}33;
  }
`;

const SearchButton = styled.button`
  padding: 0.55rem 1.2rem;
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary}dd;
    transform: translateY(-1px);
  }

  &:active {
    background: ${props => props.theme.colors.primary};
  }
`;

const SearchModeTabs = styled.div`
  display: inline-flex;
  padding: 0.35rem;
  border-radius: 8px;
  background: ${props => props.theme.colors.background};
  border: 1px solid ${props => props.theme.colors.border};
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
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  background: ${(props) => (props.active ? props.theme.colors.primary : 'transparent')};
  color: ${(props) => (props.active ? '#ffffff' : props.theme.colors.text)};
  transition: all 0.2s;

  &:hover {
    background: ${(props) => (props.active ? props.theme.colors.primary + 'dd' : props.theme.colors.primary + '20')};
  }
`;

const CurrentLocationButton = styled.button`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  background: ${props => props.disabled ? props.theme.colors.border : props.theme.colors.surface};
  color: ${props => props.disabled ? props.theme.colors.textSecondary : props.theme.colors.text};
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;

  &:hover:enabled {
    background: ${props => props.theme.colors.primary};
    color: white;
  }

  &:active:enabled {
    transform: translateY(-1px);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
`;

const RegionControls = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
`;

const RegionSelect = styled.select`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 0.9rem;
  min-width: 200px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  cursor: pointer;

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 0 3px ${props => props.theme.colors.primary}33;
  }

  &:disabled {
    background: ${props => props.theme.colors.background};
    color: ${props => props.theme.colors.textSecondary};
    cursor: not-allowed;
  }
`;

const KeywordCategorySelect = styled.select`
  padding: 0.6rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 0.95rem;
  min-width: 150px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  cursor: pointer;

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 0 3px ${props => props.theme.colors.primary}33;
  }
`;

const RegionSearchButton = styled(SearchButton)`
  min-width: 120px;
  opacity: ${(props) => (props.disabled ? 0.6 : 1)};
  cursor: ${(props) => (props.disabled ? 'not-allowed' : 'pointer')};
`;

const StatusBanner = styled.div`
  padding: 0.75rem 1.5rem;
  background: ${props => props.theme.colors.warning || '#fff3cd'};
  color: ${props => props.theme.colors.text || '#856404'};
  font-size: 0.95rem;
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const ErrorBanner = styled.div`
  padding: 0.75rem 1.5rem;
  background: ${props => props.theme.colors.error || '#fdecea'};
  color: ${props => props.theme.colors.text || '#c0392b'};
  font-size: 0.95rem;
  border-bottom: 1px solid ${props => props.theme.colors.border};
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
  background: ${props => props.theme.colors.background};
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
  color: ${props => props.theme.colors.primary};
  z-index: 200;
`;

const ServiceListPanel = styled.div`
  width: 350px;
  background: ${props => props.theme.colors.surface};
  border-left: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  z-index: 150;
  height: 100%;
  min-height: 0;
  overflow: hidden;
`;

const ServiceListHeader = styled.div`
  padding: 1rem;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
`;

const ServiceListTitle = styled.h3`
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const ServiceListContent = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
`;

const ServiceListItem = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 1rem;
  margin-bottom: 0.5rem;
  background: ${props => props.isSelected ? props.theme.colors.primary + '20' : props.theme.colors.background};
  border: 1px solid ${props => props.isSelected ? props.theme.colors.primary : props.theme.colors.border};
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary + '10'};
    border-color: ${props => props.theme.colors.primary};
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
  margin-bottom: 0.5rem;
  color: ${props => props.theme.colors.text};
`;

const ServiceDistance = styled.span`
  font-size: 0.85rem;
  color: ${props => props.theme.colors.primary};
  font-weight: 600;
`;

const ServiceListItemCategory = styled.div`
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
  margin-bottom: 0.25rem;
`;

const ServiceListItemAddress = styled.div`
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
  margin-bottom: 0.4rem;
  line-height: 1.4;
`;

const ServiceActions = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
`;

const ServiceLink = styled.a`
  color: ${props => props.theme.colors.primary};
  font-weight: 600;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
`;

const EmptyMessage = styled.div`
  padding: 2rem 1rem;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 0.95rem;
`;

const ServiceDetailPanel = styled.div`
  position: absolute;
  top: 1rem;
  left: 1rem;
  width: 320px;
  background: ${props => props.theme.colors.surface};
  border-radius: 12px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.2);
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
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1;

  &:hover {
    color: ${props => props.theme.colors.text};
  }
`;

const ServiceTitle = styled.h3`
  margin: 0 0 1rem 0;
  color: ${props => props.theme.colors.text};
  font-size: 1.25rem;
  font-weight: 600;
`;

const ServiceInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  font-size: 0.95rem;
  color: ${props => props.theme.colors.text};
`;

const ServiceInfoItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem;

  strong {
    color: ${props => props.theme.colors.textSecondary};
    font-size: 0.85rem;
    font-weight: 600;
  }

  span {
    color: ${props => props.theme.colors.text};
  }
`;

const DetailActions = styled.div`
  margin-top: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

const DetailLink = styled.a`
  display: block;
  text-align: center;
  padding: 0.6rem 1rem;
  background: ${props => props.theme.colors.primary};
  color: white;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 600;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary}dd;
    transform: translateY(-1px);
  }
`;
