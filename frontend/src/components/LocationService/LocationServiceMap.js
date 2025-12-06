import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import styled from 'styled-components';
import MapContainer from './MapContainer';
import { locationServiceApi } from '../../api/locationServiceApi';
import { geocodingApi } from '../../api/geocodingApi';
import {
  loadSidoGeoJSON,
  loadSigunguGeoJSON,
  loadDongGeoJSON,
  getSidoCode,
  getSigunguCodeByName,
  getBoundingBox,
  calculateZoomFromBoundingBox
} from '../../utils/geojsonUtils';

const DEFAULT_CENTER = { lat: 36.5, lng: 127.5 }; // 대한민국 중심 좌표
const DEFAULT_RADIUS = 3000;
const MAP_DEFAULT_LEVEL = 14; // 전국 뷰: 레벨 14 (줌 8) - 전국이 완전히 보이는 레벨

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

// 각 시/도의 중심 좌표와 적절한 지도 레벨 (확대 레벨) - 레벨이 낮을수록 더 확대됨
const SIDO_CENTERS = {
  '서울특별시': { lat: 37.5665, lng: 126.9780, level: 6 },
  '부산광역시': { lat: 35.1796, lng: 129.0756, level: 6 },
  '대구광역시': { lat: 35.8714, lng: 128.6014, level: 6 },
  '인천광역시': { lat: 37.4563, lng: 126.7052, level: 6 },
  '광주광역시': { lat: 35.1595, lng: 126.8526, level: 6 },
  '대전광역시': { lat: 36.3504, lng: 127.3845, level: 6 },
  '울산광역시': { lat: 35.5384, lng: 129.3114, level: 6 },
  '세종특별자치시': { lat: 36.4800, lng: 127.2890, level: 7 },
  '경기도': { lat: 37.4138, lng: 127.5183, level: 5 },
  '강원특별자치도': { lat: 37.8228, lng: 128.1555, level: 5 },
  '충청북도': { lat: 36.8000, lng: 127.7000, level: 5 },
  '충청남도': { lat: 36.5184, lng: 126.8000, level: 5 },
  '전북특별자치도': { lat: 35.7175, lng: 127.1530, level: 5 },
  '전라남도': { lat: 34.8679, lng: 126.9910, level: 5 },
  '경상북도': { lat: 36.4919, lng: 128.8889, level: 5 },
  '경상남도': { lat: 35.4606, lng: 128.2132, level: 5 },
  '제주특별자치도': { lat: 33.4996, lng: 126.5312, level: 6 },
};

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

// 지도 레벨에 따라 표시할 지역 단위 결정
const getRegionLevel = (mapLevel) => {
  if (mapLevel >= 7) return 'sido';        // 전국 범위: 시도
  if (mapLevel >= 5) return 'sigungu';    // 시도 범위: 시군구
  if (mapLevel >= 3) return 'eupmyeondong'; // 시군구 범위: 읍면동
  return 'roadName';                      // 읍면동 범위: 도로명
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
  const [selectedKeywordCategory, setSelectedKeywordCategory] = useState('');
  const [addressQuery, setAddressQuery] = useState('');
  const [categoryType, setCategoryType] = useState(CATEGORY_DEFAULT);
  const [searchMode, setSearchMode] = useState('keyword');
  const [selectedSido, setSelectedSido] = useState('');
  const [selectedSigungu, setSelectedSigungu] = useState('');
  const [selectedEupmyeondong, setSelectedEupmyeondong] = useState('');
  const [selectedService, setSelectedService] = useState(null);
  const [showDirections, setShowDirections] = useState(false);
  const [directionsData, setDirectionsData] = useState(null);
  const [hoveredSido, setHoveredSido] = useState(null); // 마우스 호버된 시/도
  const [currentMapView, setCurrentMapView] = useState('nation'); // 'nation', 'sido', 'sigungu'

  // 선택된 지역의 하위 지역 목록 (서비스 데이터에서 추출)
  const [availableSigungus, setAvailableSigungus] = useState([]); // 선택된 시도의 시군구 목록
  const [availableEupmyeondongs, setAvailableEupmyeondongs] = useState([]); // 선택된 시군구의 읍면동 목록
  const [userLocation, setUserLocation] = useState(null);
  const [userLocationAddress, setUserLocationAddress] = useState(null);
  const [mapCenter, setMapCenter] = useState(DEFAULT_CENTER);
  const [mapLevel, setMapLevel] = useState(MAP_DEFAULT_LEVEL);
  const [mapBounds, setMapBounds] = useState(null); // 지도 bounds (하이브리드용)
  const isSearchModeRef = useRef(false); // 검색 모드 여부 (카테고리/키워드 검색)
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

  // 클라이언트에서 지역별 필터링 (시도, 시군구, 읍면동) - 최적화: 한 번의 순회로 처리
  const filterServicesByRegion = useCallback((allServicesData, sido, sigungu, eupmyeondong, category) => {
    if (!allServicesData || allServicesData.length === 0) {
      setServices([]);
      setAvailableSigungus([]);
      setAvailableEupmyeondongs([]);
      return;
    }

    // 한 번의 순회로 필터링과 목록 추출을 동시에 처리 (성능 최적화)
    const filtered = [];
    const sigunguSet = new Set();
    const eupmyeondongSet = new Set();

    for (const service of allServicesData) {
      // 시도 필터링
      if (sido && service.sido !== sido) continue;

      // 시군구 필터링
      if (sigungu && service.sigungu !== sigungu) continue;

      // 읍면동 필터링
      if (eupmyeondong && service.eupmyeondong !== eupmyeondong) continue;

      // 카테고리 필터링
      if (category && service.category3 !== category && service.category2 !== category && service.category1 !== category) continue;

      // 시군구 목록 추출 (시도만 선택된 경우)
      if (sido && !sigungu && service.sigungu) {
        sigunguSet.add(service.sigungu);
      }

      // 읍면동 목록 추출 (시군구만 선택된 경우)
      if (sigungu && !eupmyeondong && service.eupmyeondong) {
        eupmyeondongSet.add(service.eupmyeondong);
      }

      filtered.push(service);
    }

    // 시군구 목록 설정
    if (sido && !sigungu) {
      const sigunguList = Array.from(sigunguSet).sort();
      setAvailableSigungus(sigunguList.length > 0 ? sigunguList : (SIGUNGUS[sido] || []));
    } else {
      setAvailableSigungus([]);
    }

    // 읍면동 목록 설정
    if (sigungu && !eupmyeondong) {
      setAvailableEupmyeondongs(Array.from(eupmyeondongSet).sort());
    } else {
      setAvailableEupmyeondongs([]);
    }

    setServices(filtered);
    setStatusMessage(filtered.length === 0 ? '해당 지역에 표시할 장소가 없습니다.' : `총 ${filtered.length}개의 장소가 있습니다.`);
  }, []);

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
      // 지역 검색 시에는 위도/경도를 전달하지 않음 (명시적으로 null 체크)
      const isRegionOnlySearch = region && latitude === undefined && longitude === undefined;
      const effectiveLatitude = isRegionOnlySearch ? undefined : (typeof latitude === 'number' ? latitude : center.lat);
      const effectiveLongitude = isRegionOnlySearch ? undefined : (typeof longitude === 'number' ? longitude : center.lng);
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
        // 지도 레벨에 따라 지역 단위 결정
        const regionLevel = getRegionLevel(effectiveLevel);
        const effectiveCategoryType = categoryOverride ?? categoryType;
        const apiCategory = effectiveCategoryType &&
          effectiveCategoryType !== CATEGORY_DEFAULT &&
          effectiveCategoryType !== CATEGORY_CUSTOM
          ? effectiveCategoryType
          : undefined;

        // 지역 계층별 검색만 수행 (내 위치는 거리 계산용으로만 사용)
        const regionParams = {};

        // 초기 로드 시에만 전체 데이터 가져오기
        if (isInitialLoad) {
          const response = await locationServiceApi.searchPlaces({
            category: apiCategory,
            size: 5000, // 초기 로드 시 적절한 크기로 제한 (성능 최적화)
          });

          if (latestRequestRef.current !== requestId) {
            return;
          }

          // 거리 계산은 나중에 필요할 때만 수행 (초기 로드 시 성능 최적화)
          const fetchedServices = (response.data?.services || []).map((service) => ({
            ...service,
            // distance는 나중에 필요할 때 계산
          }));

          // 전체 데이터를 allServices에 저장하고, 선택된 지역에 따라 필터링
          setAllServices(fetchedServices);
          filterServicesByRegion(fetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);

          isInitialLoadRef.current = false;
          isSearchModeRef.current = false;
          setStatusMessage('');
          setSelectedService(null);
          setLoading(false);
          return;
        }

        // 지역 검색이 명시적으로 요청된 경우 서버에서 데이터 가져오기
        if (region) {
          // region 파라미터를 파싱하여 sido, sigungu, eupmyeondong 추출
          // region 형식: "서울특별시" 또는 "서울특별시 강남구" 또는 "서울특별시 강남구 역삼동"
          const regionParts = region.trim().split(/\s+/);
          let apiSido = regionParts[0] || undefined;
          let apiSigungu = regionParts[1] || undefined;
          let apiEupmyeondong = regionParts[2] || undefined;

          console.log('지역 검색 API 호출:', { apiSido, apiSigungu, apiEupmyeondong, region });

          const response = await locationServiceApi.searchPlaces({
            sido: apiSido,
            sigungu: apiSigungu,
            eupmyeondong: apiEupmyeondong,
            category: apiCategory,
            size: effectiveSize,
          });

          if (latestRequestRef.current !== requestId) {
            return;
          }

          const fetchedServices = (response.data?.services || []).map((service) => ({
            ...service,
          }));

          console.log(`지역 검색 결과: ${fetchedServices.length}개 서비스`, { region, apiSido, apiSigungu, apiEupmyeondong });

          // 지역별 데이터를 allServices에 업데이트하고 필터링
          setAllServices(fetchedServices);
          filterServicesByRegion(fetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);

          isSearchModeRef.current = false;
          setStatusMessage('');
          setSelectedService(null);
          setLoading(false);
          return;
        }

        // 초기 로드가 아니고 지역 검색도 아닌 경우 allServices에서 클라이언트 사이드 필터링만 수행
        if (allServices.length > 0) {
          filterServicesByRegion(allServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
          setLoading(false);
          return;
        }

        // allServices가 없으면 다시 로드
        setLoading(false);
        return;
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
    [keyword, categoryType, mapBounds, filterServicesByBounds, userLocation, selectedSido, selectedSigungu, selectedEupmyeondong, filterServicesByRegion, allServices]
  );

  useEffect(() => {
    fetchServicesRef.current = fetchServices;
  }, [fetchServices]);

  useEffect(() => {
    // 초기 로드: 전국 데이터 가져오기 (내 위치와 관계없이)
    setMapCenter(DEFAULT_CENTER);
    setMapLevel(MAP_DEFAULT_LEVEL); // 전국이 보이도록 레벨 1로 설정
    fetchServicesRef.current?.({
      isInitialLoad: true, // 초기 로드 - 전국 데이터
    });

    // 내 위치는 나중에 가져오기 (길찾기/거리 계산용으로만 사용)
    const tryGeolocation = () => {
      if (!navigator.geolocation) {
        return;
      }

      const options = {
        enableHighAccuracy: false,
        timeout: 5000,
        maximumAge: 0,
      };

      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const location = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          };
          // 내 위치는 길찾기/거리 계산용으로만 저장 (지도 중심은 변경하지 않음)
          setUserLocation(location);

          // 주소 변환은 백엔드 API를 통해 처리하거나, 간단하게 "현재 위치"로 표시
          // 네이버맵 API는 CORS 문제로 직접 호출 불가
          setUserLocationAddress('현재 위치');
        },
        (error) => {
          console.warn('위치 정보를 가져올 수 없습니다:', error);
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
    ({ lat, lng, level, bounds, isManualOperation = false }) => {
      const nextCenter = { lat, lng };
      const prevLevel = mapLevel;
      const levelChanged = prevLevel !== level;

      // 초기 로드가 완료되기 전에는 mapCenter를 변경하지 않음 (DEFAULT_CENTER 유지)
      // 프로그래밍 방식으로 이동 중일 때는 mapCenter를 업데이트하지 않음 (무한 루프 방지)
      if (!isInitialLoadRef.current && !isManualOperation) {
        // 프로그래밍 이동 중이 아니고, 실제로 중심이 변경되었을 때만 업데이트
        const plannedCenter = programmaticCenterRef.current;
        const COORD_EPSILON = 0.0001; // 좌표 비교 오차 범위

        // 프로그래밍 이동이 진행 중이 아니거나, 목표 위치와 다를 때만 업데이트
        if (!plannedCenter ||
          Math.abs(plannedCenter.lat - lat) > COORD_EPSILON ||
          Math.abs(plannedCenter.lng - lng) > COORD_EPSILON) {
          // 실제로 중심이 변경되었을 때만 업데이트
          if (
            !mapCenter ||
            Math.abs(mapCenter.lat - lat) > 0.00001 ||
            Math.abs(mapCenter.lng - lng) > 0.00001
          ) {
            setMapCenter(nextCenter);
          }
        }
      }

      // 수동 조작이 아닐 때만 mapLevel 업데이트 (프로그래밍 방식으로 이동한 경우)
      // 수동 조작 시에는 mapLevel을 업데이트하지 않아서 useEffect가 다시 실행되지 않음
      if (levelChanged && !isManualOperation) {
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

      // 프로그래밍 방식으로 이동한 경우, 지도가 목표 위치에 도달했으면 서버 요청 수행
      if (centersAreClose(plannedCenter, nextCenter)) {
        programmaticCenterRef.current = null;
        // 지도 이동이 완료되었으므로 서버에서 데이터 가져오기
        lastFetchedRef.current = { lat, lng, level };
        fetchServices({
          latitude: lat,
          longitude: lng,
          level,
        });
        return;
      }

      if (suppressNextFetchRef.current) {
        suppressNextFetchRef.current = false;
        programmaticCenterRef.current = null;
        return;
      }

      // 하이브리드: allServices가 있고 키워드/지역 검색이 아닌 경우 클라이언트 필터링만 수행
      // 단, 지도가 너무 멀리 이동했으면 추가 요청 필요
      // 카테고리/키워드 검색 결과는 bounds 필터링을 하지 않음 (전체 표시)
      if (allServices.length > 0 && bounds && !isSearchModeRef.current) {
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
          // 클라이언트 필터링만 수행 (bounds에 맞는 서비스만 표시)
          // 초기 로드 직후에는 allServices가 많을 수 있으므로 bounds로 필터링
          const filtered = filterServicesByBounds(bounds, allServices);
          setServices(filtered);
          // 상태 메시지 업데이트
          if (filtered.length === 0) {
            setStatusMessage('현재 지도 영역에 표시할 장소가 없습니다.');
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

  const handleRegionSearch = useCallback(async (sidoOverride = null, sigunguOverride = null, eupmyeondongOverride = null) => {
    const targetSido = sidoOverride !== null ? sidoOverride : selectedSido;
    const targetSigungu = sigunguOverride !== null ? sigunguOverride : selectedSigungu;
    const targetEupmyeondong = eupmyeondongOverride !== null ? eupmyeondongOverride : selectedEupmyeondong;

    if (!targetSido) {
      setStatusMessage('검색할 시/도를 선택해주세요.');
      return;
    }

    let targetRegion = targetSido;
    if (targetSigungu) {
      targetRegion = `${targetSido} ${targetSigungu}`;
    }
    if (targetEupmyeondong) {
      targetRegion = `${targetSido} ${targetSigungu} ${targetEupmyeondong}`;
    }

    try {
      setStatusMessage(`'${targetRegion}' 주변 장소를 검색하는 중...`);
      setError(null);

      // 지역 정보만으로 API 호출 (지도 관련 로직 제거)
      const regionParam = targetEupmyeondong
        ? `${targetSido} ${targetSigungu} ${targetEupmyeondong}`
        : targetSigungu
          ? `${targetSido} ${targetSigungu}`
          : targetSido;

      // API 호출만 수행 (지도 관련 로직 제거)
      await fetchServices({
        latitude: undefined,
        longitude: undefined,
        keywordOverride: keyword,
        level: undefined,
        region: regionParam,
        categoryOverride: categoryType,
      });
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`지역 검색에 실패했습니다: ${message}`);
      setStatusMessage('');
    }
  }, [selectedSido, selectedSigungu, selectedEupmyeondong, categoryType, fetchServices, keyword]);

  const handleAddressSearch = useCallback(async () => {
    if (!addressQuery.trim()) {
      return;
    }

    try {
      setStatusMessage('주소를 찾는 중...');
      setError(null);

      // 주소를 지역명으로 인식하여 지역 검색 수행
      const address = addressQuery.trim();

      // 주소에서 시도 추출 시도
      let foundSido = null;
      for (const sido of SIDOS) {
        if (address.includes(sido)) {
          foundSido = sido;
          break;
        }
      }

      if (foundSido) {
        // 시도가 포함된 경우 지역 검색으로 처리
        setSelectedSido(foundSido);
        setSelectedSigungu('');
        setSelectedEupmyeondong('');
        await handleRegionSearch(foundSido);
      } else {
        // 시도가 없으면 일반 지역 검색으로 처리
        await fetchServices({
          latitude: undefined,
          longitude: undefined,
          keywordOverride: keyword,
          level: undefined,
          region: address,
          categoryOverride: categoryType,
        });
      }
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`주소 검색에 실패했습니다: ${message}`);
      setStatusMessage('');
    }
  }, [addressQuery, categoryType, fetchServices, keyword, handleRegionSearch]);

  // 시도/시군구/읍면동 선택 시 자동으로 서비스 필터링 (클라이언트 사이드)
  useEffect(() => {
    if (!isInitialLoadRef.current && allServices.length > 0) {
      const effectiveCategoryType = categoryType !== CATEGORY_DEFAULT && categoryType !== CATEGORY_CUSTOM
        ? categoryType
        : undefined;
      filterServicesByRegion(allServices, selectedSido, selectedSigungu, selectedEupmyeondong, effectiveCategoryType);
    }
  }, [selectedSido, selectedSigungu, selectedEupmyeondong, categoryType, allServices, filterServicesByRegion]);


  // 거리 계산을 지연 로딩 (필요할 때만 계산)
  const servicesWithDisplay = useMemo(() => {
    return services.map((service, index) => {
      // 거리는 필요할 때만 계산 (userLocation이 있고 아직 계산되지 않은 경우)
      let distance = service.distance;
      if (distance == null && userLocation && service.latitude && service.longitude) {
        distance = calculateDistance(
          userLocation.lat,
          userLocation.lng,
          service.latitude,
          service.longitude
        );
      }

      return {
        ...service,
        key: service.externalId || service.placeUrl || `${service.latitude}-${service.longitude}-${index}`,
        distance,
        distanceLabel: formatDistance(distance),
      };
    });
  }, [services, userLocation]);

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
            {(selectedSido || selectedSigungu || selectedEupmyeondong) && (
              <CurrentLocationButton
                type="button"
                onClick={async () => {
                  setSelectedSido('');
                  setSelectedSigungu('');
                  setSelectedEupmyeondong('');
                  setCurrentMapView('nation');
                  setMapCenter(DEFAULT_CENTER);
                  setMapLevel(MAP_DEFAULT_LEVEL);
                  await fetchServices({
                    latitude: undefined,
                    longitude: undefined,
                    keywordOverride: keyword,
                    level: MAP_DEFAULT_LEVEL,
                    region: undefined,
                    categoryOverride: categoryType,
                  });
                }}
              >
                전국 보기
              </CurrentLocationButton>
            )}
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
            {!selectedSido ? (
              // 시/도 선택 화면
              <RegionButtonGrid>
                <RegionButton
                  onClick={async () => {
                    setSelectedSido('');
                    setSelectedSigungu('');
                    setSelectedEupmyeondong('');
                    setMapCenter(DEFAULT_CENTER);
                    setMapLevel(MAP_DEFAULT_LEVEL);
                    // 전국 검색
                    await fetchServices({
                      latitude: undefined,
                      longitude: undefined,
                      keywordOverride: keyword,
                      level: MAP_DEFAULT_LEVEL,
                      region: undefined,
                      categoryOverride: categoryType,
                    });
                  }}
                  active={!selectedSido && !selectedSigungu && !selectedEupmyeondong}
                >
                  전국
                </RegionButton>
                {SIDOS.map((sido) => (
                  <RegionButton
                    key={sido}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      console.log('시/도 클릭:', sido);
                      setSelectedSido(sido);
                      setSelectedSigungu('');
                      setSelectedEupmyeondong('');
                      // 시/도 검색
                      handleRegionSearch(sido);
                    }}
                    onMouseEnter={() => {
                      console.log('시/도 호버:', sido);
                      setHoveredSido(sido);
                    }}
                    onMouseLeave={() => {
                      setHoveredSido(null);
                    }}
                    active={selectedSido === sido}
                  >
                    {sido}
                  </RegionButton>
                ))}
              </RegionButtonGrid>
            ) : !selectedSigungu ? (
              // 시/군/구 선택 화면
              <RegionButtonGrid>
                <RegionButton
                  onClick={() => {
                    setSelectedSido('');
                    setSelectedSigungu('');
                    setSelectedEupmyeondong('');
                    setMapCenter(DEFAULT_CENTER);
                    setMapLevel(MAP_DEFAULT_LEVEL);
                  }}
                >
                  ← 뒤로
                </RegionButton>
                {(availableSigungus.length > 0 ? availableSigungus : (SIGUNGUS[selectedSido] || [])).map((sigungu) => (
                  <RegionButton
                    key={sigungu}
                    onClick={async () => {
                      setSelectedSigungu(sigungu);
                      setSelectedEupmyeondong('');
                      // 시/군/구 검색
                      await handleRegionSearch(selectedSido, sigungu);
                    }}
                    active={selectedSigungu === sigungu}
                  >
                    {sigungu}
                  </RegionButton>
                ))}
              </RegionButtonGrid>
            ) : (
              // 읍/면/동 선택 화면
              <RegionButtonGrid>
                <RegionButton
                  onClick={() => {
                    setSelectedSigungu('');
                    setSelectedEupmyeondong('');
                  }}
                >
                  ← 뒤로
                </RegionButton>
                {availableEupmyeondongs.map((eupmyeondong) => (
                  <RegionButton
                    key={eupmyeondong}
                    onClick={async () => {
                      setSelectedEupmyeondong(eupmyeondong);
                      // 읍/면/동 검색
                      await handleRegionSearch(selectedSido, selectedSigungu, eupmyeondong);
                    }}
                    active={selectedEupmyeondong === eupmyeondong}
                  >
                    {eupmyeondong}
                  </RegionButton>
                ))}
              </RegionButtonGrid>
            )}
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
        {/* 지도 제거 - 지역 선택 UI만 사용 */}

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
          <ServiceDetailPanel onClick={(e) => {
            if (e.target === e.currentTarget) {
              setSelectedService(null);
              setShowDirections(false);
            }
          }}>
            <DetailContent onClick={(e) => e.stopPropagation()}>
              <CloseButton onClick={() => {
                setSelectedService(null);
                setShowDirections(false);
              }}>✕</CloseButton>
              <DetailLeft>
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
              </DetailLeft>
              <DetailRight>
                {showDirections && selectedService.latitude && selectedService.longitude ? (
                  <DirectionsContainer>
                    <DirectionsHeader>
                      <DirectionsTitle>길찾기</DirectionsTitle>
                      <CloseDirectionsButton onClick={() => setShowDirections(false)}>✕</CloseDirectionsButton>
                    </DirectionsHeader>
                    <DirectionsInfo>
                      <div style={{ marginBottom: '1rem' }}>
                        <strong>도착지:</strong> {selectedService.name || selectedService.address}
                        {selectedService.latitude && selectedService.longitude && (
                          <div style={{ fontSize: '0.85rem', color: '#666', marginTop: '0.5rem' }}>
                            좌표: ({selectedService.latitude.toFixed(6)}, {selectedService.longitude.toFixed(6)})
                          </div>
                        )}
                      </div>
                      {userLocation && (
                        <div style={{ marginBottom: '1rem', padding: '0.75rem', background: 'rgba(3, 199, 90, 0.1)', borderRadius: '6px' }}>
                          <strong>출발지:</strong> {userLocationAddress || '현재 위치'}
                          <div style={{ fontSize: '0.85rem', color: '#666', marginTop: '0.5rem' }}>
                            좌표: ({userLocation.lat.toFixed(6)}, {userLocation.lng.toFixed(6)})
                          </div>
                        </div>
                      )}
                      <DirectionsLink
                        href={`https://map.naver.com/p/search/${encodeURIComponent(selectedService.name || selectedService.address || '')}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        onClick={async (e) => {
                          // 네이버맵 Directions API 호출하여 경로 정보 표시
                          if (userLocation && selectedService.latitude && selectedService.longitude) {
                            try {
                              console.log('🔍 길찾기 API 호출 시작...');
                              const directionsData = await geocodingApi.getDirections(
                                userLocation.lat,
                                userLocation.lng,
                                selectedService.latitude,
                                selectedService.longitude,
                                'traoptimal'
                              );
                              console.log('📊 길찾기 API 응답:', directionsData);
                              if (directionsData.success && directionsData.data) {
                                console.log('✅ 경로 데이터 수신 성공:', directionsData.data);
                                setDirectionsData(directionsData.data);
                              } else {
                                console.warn('⚠️ 경로 데이터 수신 실패:', directionsData);
                                setDirectionsData(null);
                              }
                            } catch (error) {
                              console.error('❌ 길찾기 API 호출 실패:', error);
                              setDirectionsData(null);
                            }
                          }
                        }}
                      >
                        네이버맵에서 장소 검색 ↗
                      </DirectionsLink>
                    </DirectionsInfo>
                    <DirectionsMessage>
                      <strong>안내:</strong> 네이버맵은 보안상의 이유로 외부에서 출발지/도착지를 자동으로 입력할 수 없습니다.
                      <br />
                      위 링크를 클릭하여 네이버맵에서 도착지를 검색한 후, 출발지를 직접 입력해주세요.
                      {userLocation && (
                        <>
                          <br />
                          <br />
                          <strong>출발지 좌표:</strong> {userLocation.lat.toFixed(6)}, {userLocation.lng.toFixed(6)}
                          <br />
                          네이버맵에서 이 좌표를 검색하거나 "현재 위치"를 선택하세요.
                        </>
                      )}
                    </DirectionsMessage>
                    {directionsData && (
                      <DirectionsSummary>
                        <div style={{ marginBottom: '0.5rem', fontWeight: 600, color: '#03C75A' }}>
                          📍 경로 정보 (백엔드 API 응답)
                        </div>
                        <SummaryItem>
                          <strong>예상 소요 시간:</strong>
                          <span>
                            {(() => {
                              // 실시간 교통 상황을 반영한 예상 시간
                              try {
                                // duration을 분으로 변환하는 함수 (네이버 Directions API는 밀리초 단위)
                                const convertDurationToMinutes = (duration) => {
                                  if (!duration) return null;
                                  // duration이 밀리초 단위인지 확인 (일반적으로 1000 이상)
                                  // 네이버 Directions API는 보통 밀리초 단위
                                  if (duration > 1000) {
                                    return Math.round(duration / 1000 / 60); // 밀리초 -> 초 -> 분
                                  } else {
                                    return Math.round(duration / 60); // 초 -> 분
                                  }
                                };

                                // 시간과 분으로 포맷팅하는 함수
                                const formatDuration = (minutes) => {
                                  if (!minutes || minutes < 0) return '정보 없음';
                                  const hours = Math.floor(minutes / 60);
                                  const mins = minutes % 60;
                                  
                                  if (hours > 0 && mins > 0) {
                                    return `${hours}시간 ${mins}분`;
                                  } else if (hours > 0) {
                                    return `${hours}시간`;
                                  } else {
                                    return `${mins}분`;
                                  }
                                };

                                const route = directionsData.route;
                                let durationMinutes = null;

                                // 최적 경로(traoptimal) 확인
                                if (route && route.traoptimal && Array.isArray(route.traoptimal) && route.traoptimal.length > 0) {
                                  const summary = route.traoptimal[0].summary;
                                  if (summary && summary.duration) {
                                    console.log('📊 duration 값 (traoptimal):', summary.duration, '타입:', typeof summary.duration);
                                    durationMinutes = convertDurationToMinutes(summary.duration);
                                  }
                                }
                                
                                // 최단 경로(trafast) 확인 (traoptimal이 없을 경우)
                                if (!durationMinutes && route && route.trafast && Array.isArray(route.trafast) && route.trafast.length > 0) {
                                  const summary = route.trafast[0].summary;
                                  if (summary && summary.duration) {
                                    console.log('📊 duration 값 (trafast):', summary.duration, '타입:', typeof summary.duration);
                                    durationMinutes = convertDurationToMinutes(summary.duration);
                                  }
                                }

                                if (durationMinutes !== null) {
                                  return formatDuration(durationMinutes);
                                }
                                return '정보 없음';
                              } catch (e) {
                                console.error('경로 데이터 파싱 오류:', e, directionsData);
                                return '파싱 오류';
                              }
                            })()}
                          </span>
                        </SummaryItem>
                        <SummaryItem>
                          <strong>예상 거리:</strong>
                          <span>
                            {(() => {
                              try {
                                const route = directionsData.route;
                                if (route && route.traoptimal && Array.isArray(route.traoptimal) && route.traoptimal.length > 0) {
                                  const summary = route.traoptimal[0].summary;
                                  if (summary && summary.distance) {
                                    return `${(summary.distance / 1000).toFixed(1)}km`;
                                  }
                                }
                                // 다른 경로 옵션 확인
                                if (route && route.trafast && Array.isArray(route.trafast) && route.trafast.length > 0) {
                                  const summary = route.trafast[0].summary;
                                  if (summary && summary.distance) {
                                    return `${(summary.distance / 1000).toFixed(1)}km`;
                                  }
                                }
                                return '정보 없음';
                              } catch (e) {
                                console.error('경로 데이터 파싱 오류:', e, directionsData);
                                return '파싱 오류';
                              }
                            })()}
                          </span>
                        </SummaryItem>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.85rem', color: '#666', lineHeight: '1.5' }}>
                          * 실시간 교통 상황(정체, 공사 등)을 반영한 예상 시간입니다.
                          <br />
                          * 실제 소요 시간은 교통 상황에 따라 달라질 수 있습니다.
                          <br />
                          (네이버맵 웹사이트는 별도로 열어야 합니다)
                        </div>
                      </DirectionsSummary>
                    )}
                  </DirectionsContainer>
                ) : (
                  <>
                    <ActionSectionTitle>편의 기능</ActionSectionTitle>
                    <ActionButtons>
                      {selectedService.latitude && selectedService.longitude && (
                        <ActionButton
                          onClick={() => setShowDirections(true)}
                          primary
                        >
                          🗺️ 네이버맵 길찾기
                        </ActionButton>
                      )}
                      {selectedService.phone && (
                        <ActionButton
                          as="a"
                          href={`tel:${selectedService.phone}`}
                        >
                          📞 전화하기
                        </ActionButton>
                      )}
                      {selectedService.address && (
                        <ActionButton
                          onClick={() => {
                            navigator.clipboard.writeText(selectedService.address);
                            setStatusMessage('주소가 클립보드에 복사되었습니다.');
                            setTimeout(() => setStatusMessage(''), 2000);
                          }}
                        >
                          📋 주소 복사
                        </ActionButton>
                      )}
                      {selectedService.latitude && selectedService.longitude && (
                        <ActionButton
                          onClick={() => {
                            const url = `https://map.naver.com/v5/search/${encodeURIComponent(selectedService.name || '')}`;
                            navigator.clipboard.writeText(url);
                            setStatusMessage('네이버맵 링크가 클립보드에 복사되었습니다.');
                            setTimeout(() => setStatusMessage(''), 2000);
                          }}
                        >
                          🔗 링크 공유
                        </ActionButton>
                      )}
                      {selectedService.placeUrl && (
                        <ActionButton
                          as="a"
                          href={selectedService.placeUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          📍 카카오맵 보기
                        </ActionButton>
                      )}
                      {selectedService.website && (
                        <ActionButton
                          as="a"
                          href={selectedService.website}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          🌐 웹사이트 방문
                        </ActionButton>
                      )}
                    </ActionButtons>
                  </>
                )}
              </DetailRight>
            </DetailContent>
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
  width: 100%;
`;

const RegionButtonGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 0.5rem;
  width: 100%;
  max-height: 200px;
  overflow-y: auto;
  padding: 0.5rem;
  position: relative;
  z-index: 1000;
  pointer-events: auto;
`;

const RegionButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 0.6rem 1rem;
  border: 1px solid ${props => props.active ? props.theme.colors.primary : props.theme.colors.border};
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: ${props => props.active ? 600 : 500};
  cursor: pointer;
  background: ${props => props.active ? props.theme.colors.primary : props.theme.colors.surface};
  color: ${props => props.active ? 'white' : props.theme.colors.text};
  transition: all 0.2s;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  position: relative;
  z-index: 1000;
  pointer-events: auto;

  &:hover {
    background: ${props => props.active ? props.theme.colors.primary + 'dd' : props.theme.colors.primary + '20'};
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.active ? 'white' : props.theme.colors.primary};
  }

  &:active {
    transform: translateY(1px);
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
  display: flex;
  flex-direction: column;
  height: calc(100vh - 200px);
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
  width: 100%;
  background: ${props => props.theme.colors.surface};
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
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
`;

const DetailContent = styled.div`
  position: relative;
  background: ${props => props.theme.colors.surface};
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 95%;
  max-width: 1800px;
  max-height: 90vh;
  display: flex;
  overflow: hidden;
`;

const DetailLeft = styled.div`
  flex: 0 0 450px;
  padding: 2rem;
  overflow-y: auto;
  border-right: 2px solid ${props => props.theme.colors.border};
`;

const DetailRight = styled.div`
  flex: 1;
  padding: 2rem;
  background: ${props => props.theme.colors.background};
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
`;

const ActionSectionTitle = styled.h4`
  margin: 0;
  color: ${props => props.theme.colors.text};
  font-size: 1.1rem;
  font-weight: 600;
`;

const ActionButtons = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
`;

const ActionButton = styled.button`
  width: 100%;
  padding: 0.9rem 1.2rem;
  border: 1px solid ${props => props.primary ? props.theme.colors.primary : props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.primary ? props.theme.colors.primary : props.theme.colors.surface};
  color: ${props => props.primary ? '#fff' : props.theme.colors.text};
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;

  &:hover {
    background: ${props => props.primary ? props.theme.colors.primary : props.theme.colors.border};
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  &:active {
    transform: translateY(0);
  }
`;

const DirectionsContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
`;

const DirectionsHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const DirectionsInfo = styled.div`
  padding: 1rem;
  background: ${props => props.theme.colors.primary}15;
  border-radius: 8px;
  font-size: 0.95rem;
  color: ${props => props.theme.colors.primary};
  font-weight: 500;
  margin-bottom: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
`;

const DirectionsLink = styled.a`
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  background: ${props => props.theme.colors.primary};
  color: white;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 600;
  font-size: 1rem;
  transition: all 0.2s;
  width: fit-content;

  &:hover {
    background: ${props => props.theme.colors.primary}dd;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }
`;

const DirectionsMessage = styled.div`
  padding: 2rem;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 0.95rem;
  line-height: 1.6;
  background: ${props => props.theme.colors.background};
  border-radius: 8px;
  border: 1px dashed ${props => props.theme.colors.border};
`;

const DirectionsSummary = styled.div`
  margin-top: 1rem;
  padding: 1rem;
  background: ${props => props.theme.colors.surface};
  border-radius: 8px;
  border: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

const SummaryItem = styled.div`
  display: flex;
  justify-content: space-between;
  font-size: 0.9rem;
  color: ${props => props.theme.colors.text};
  
  strong {
    color: ${props => props.theme.colors.primary};
    font-weight: 600;
  }
`;

const DirectionsTitle = styled.h4`
  margin: 0;
  color: ${props => props.theme.colors.text};
  font-size: 1.1rem;
  font-weight: 600;
`;

const CloseDirectionsButton = styled.button`
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1;
  padding: 0.25rem;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.border};
    color: ${props => props.theme.colors.text};
  }
`;

const DirectionsIframe = styled.iframe`
  width: 100%;
  flex: 1;
  border: none;
  border-radius: 8px;
  min-height: 700px;
  height: calc(90vh - 100px);
`;

const CloseButton = styled.button`
  position: absolute;
  top: 1rem;
  right: 1rem;
  background: rgba(0, 0, 0, 0.1);
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: ${props => props.theme.colors.text};
  line-height: 1;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  transition: all 0.2s;

  &:hover {
    background: rgba(0, 0, 0, 0.2);
    transform: rotate(90deg);
  }
`;

const ServiceTitle = styled.h3`
  margin: 0 0 2rem 0;
  color: ${props => props.theme.colors.text};
  font-size: 1.75rem;
  font-weight: 700;
  padding-bottom: 1.5rem;
  border-bottom: 2px solid ${props => props.theme.colors.border};
`;

const ServiceInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem;
  font-size: 0.95rem;
  color: ${props => props.theme.colors.text};
`;

const ServiceInfoItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  background: ${props => props.theme.colors.background};
  border-radius: 8px;
  border: 1px solid ${props => props.theme.colors.border};
  transition: all 0.2s;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  }

  strong {
    color: ${props => props.theme.colors.primary};
    font-size: 0.9rem;
    font-weight: 700;
    margin-bottom: 0.25rem;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  span {
    color: ${props => props.theme.colors.text};
    font-size: 1rem;
    line-height: 1.6;
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
