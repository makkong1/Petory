import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import styled from 'styled-components';
import { locationServiceApi } from '../../api/locationServiceApi';
import { geocodingApi } from '../../api/geocodingApi';
import MapContainer from './MapContainer';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };

// 반경에 따른 적절한 카카오맵 레벨 계산 (MapContainer에서 네이버맵 줌으로 변환됨)
// 카카오맵 레벨: 낮을수록 확대 (1=최대 확대, 14=최대 축소)
const calculateMapLevelFromRadius = (radiusKm) => {
  if (radiusKm <= 1) {
    return 5; // 카카오맵 레벨 5 → 네이버맵 줌 17 (가장 확대, 1km)
  } else if (radiusKm <= 3) {
    return 6; // 카카오맵 레벨 6 → 네이버맵 줌 16 (3km)
  } else if (radiusKm <= 5) {
    return 7; // 카카오맵 레벨 7 → 네이버맵 줌 15 (5km)
  } else if (radiusKm <= 10) {
    return 8; // 카카오맵 레벨 8 → 네이버맵 줌 14 (10km)
  } else if (radiusKm <= 20) {
    return 9; // 카카오맵 레벨 9 → 네이버맵 줌 13 (20km, 가장 축소)
  } else {
    return 10; // 카카오맵 레벨 10 → 네이버맵 줌 12 (20km 초과)
  }
};

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

// 시군구 중심 좌표 (주요 시군구만)
const SIGUNGU_CENTERS = {
  '서울특별시': {
    '강남구': { lat: 37.5172, lng: 127.0473 },
    '강동구': { lat: 37.5301, lng: 127.1238 },
    '강북구': { lat: 37.6398, lng: 127.0256 },
    '강서구': { lat: 37.5509, lng: 126.8495 },
    '관악구': { lat: 37.4785, lng: 126.9516 },
    '광진구': { lat: 37.5384, lng: 127.0822 },
    '구로구': { lat: 37.4954, lng: 126.8874 },
    '금천구': { lat: 37.4519, lng: 126.9020 },
    '노원구': { lat: 37.6542, lng: 127.0568 },
    '도봉구': { lat: 37.6688, lng: 127.0471 },
    '동대문구': { lat: 37.5744, lng: 127.0396 },
    '동작구': { lat: 37.5124, lng: 126.9393 },
    '마포구': { lat: 37.5663, lng: 126.9019 },
    '서대문구': { lat: 37.5791, lng: 126.9368 },
    '서초구': { lat: 37.4837, lng: 127.0324 },
    '성동구': { lat: 37.5633, lng: 127.0368 },
    '성북구': { lat: 37.5894, lng: 127.0167 },
    '송파구': { lat: 37.5145, lng: 127.1058 },
    '양천구': { lat: 37.5170, lng: 126.8663 },
    '영등포구': { lat: 37.5264, lng: 126.8962 },
    '용산구': { lat: 37.5326, lng: 126.9905 },
    '은평구': { lat: 37.6027, lng: 126.9291 },
    '종로구': { lat: 37.5735, lng: 126.9788 },
    '중구': { lat: 37.5640, lng: 126.9970 },
    '중랑구': { lat: 37.6063, lng: 127.0926 },
  },
  // 주요 시군구만 추가 (필요시 확장)
};

const SIGUNGUS = {
  '서울특별시': [
    '강남구', '강동구', '강북구', '강서구', '관악구', '광진구', '구로구', '금천구',
    '노원구', '도봉구', '동대문구', '동작구', '마포구', '서대문구', '서초구', '성동구',
    '성북구', '송파구', '양천구', '영등포구', '용산구', '은평구', '종로구', '중구', '중랑구',
  ],
  '부산광역시': ['중구', '서구', '동구', '영도구', '부산진구', '동래구', '남구', '북구', '해운대구', '사하구', '금정구', '강서구', '연제구', '수영구', '사상구', '기장군'],
  '대구광역시': ['중구', '동구', '서구', '남구', '북구', '수성구', '달서구', '달성군'],
  '인천광역시': ['중구', '동구', '미추홀구', '연수구', '남동구', '부평구', '계양구', '서구', '강화군', '옹진군'],
  '광주광역시': ['동구', '서구', '남구', '북구', '광산구'],
  '대전광역시': ['동구', '중구', '서구', '유성구', '대덕구'],
  '울산광역시': ['중구', '남구', '동구', '북구', '울주군'],
  '세종특별자치시': ['세종시'],
  '경기도': [
    '수원시', '성남시', '고양시', '용인시', '부천시', '안산시', '안양시', '남양주시',
    '화성시', '평택시', '의정부시', '시흥시', '김포시', '광명시', '하남시', '이천시',
    '오산시', '구리시', '안성시', '포천시', '의왕시', '양주시', '동두천시', '과천시',
    '가평군', '양평군', '여주시', '연천군',
  ],
  '강원특별자치도': ['춘천시', '원주시', '강릉시', '동해시', '속초시', '삼척시', '태백시', '정선군', '철원군', '화천군', '양구군', '인제군', '고성군', '양양군', '홍천군', '횡성군', '평창군', '영월군'],
  '충청북도': ['청주시', '충주시', '제천시', '보은군', '옥천군', '영동군', '증평군', '진천군', '괴산군', '음성군', '단양군'],
  '충청남도': ['천안시', '공주시', '아산시', '서산시', '논산시', '계룡시', '당진시', '금산군', '부여군', '서천군', '청양군', '홍성군', '예산군', '태안군'],
  '전북특별자치도': ['전주시', '군산시', '익산시', '정읍시', '남원시', '김제시', '완주군', '진안군', '무주군', '장수군', '임실군', '순창군', '고창군', '부안군'],
  '전라남도': ['목포시', '여수시', '순천시', '나주시', '광양시', '담양군', '곡성군', '구례군', '고흥군', '보성군', '화순군', '장흥군', '강진군', '해남군', '영암군', '무안군', '함평군', '영광군', '장성군', '완도군', '진도군', '신안군'],
  '경상북도': ['포항시', '경주시', '김천시', '안동시', '구미시', '영주시', '영천시', '상주시', '문경시', '경산시', '군위군', '의성군', '청송군', '영양군', '영덕군', '청도군', '고령군', '성주군', '칠곡군', '예천군', '봉화군', '울진군', '울릉군'],
  '경상남도': ['창원시', '진주시', '통영시', '사천시', '김해시', '밀양시', '거제시', '양산시', '의령군', '함안군', '창녕군', '고성군', '남해군', '하동군', '산청군', '함양군', '거창군', '합천군'],
  '제주특별자치도': ['제주시', '서귀포시'],
};

// 지도 레벨 관련 함수들 제거됨 (지도 미사용)

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
  const [currentView, setCurrentView] = useState('sido'); // 현재 화면: 'sido', 'sigungu', 'eupmyeondong'
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
  const [mapCenter, setMapCenter] = useState(null); // 지도 중심 좌표
  const [mapLevel, setMapLevel] = useState(10); // 기본 지도 레벨 (전국 뷰)
  const isProgrammaticMoveRef = useRef(false); // 프로그래매틱 이동인지 구분
  const isSearchModeRef = useRef(false); // 검색 모드 여부 (카테고리/키워드 검색)
  const latestRequestRef = useRef(0);
  const fetchServicesRef = useRef(null);
  const isInitialLoadRef = useRef(true); // 초기 로드 여부
  const initialLoadTypeRef = useRef(null); // 초기 로드 타입: 'location-based' (위치 기반) 또는 'all' (전체 조회)

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

    // 읍면동 목록 설정 (시군구가 선택된 경우)
    if (sigungu) {
      if (eupmyeondongSet.size > 0) {
        // 동 목록이 있으면 설정 (동이 선택된 경우에도 목록 유지)
        setAvailableEupmyeondongs(Array.from(eupmyeondongSet).sort());
      } else if (availableEupmyeondongs.length === 0) {
        // 목록이 없고 기존 목록도 없으면 빈 배열로 설정
        setAvailableEupmyeondongs([]);
      }
      // 동이 선택된 경우에도 목록은 유지 (다른 동을 선택할 수 있도록)
    } else {
      // 시군구가 선택되지 않은 경우 목록 초기화
      setAvailableEupmyeondongs([]);
    }

    setServices(filtered);
    setStatusMessage(filtered.length === 0 ? '해당 지역에 표시할 장소가 없습니다.' : `총 ${filtered.length}개의 장소가 있습니다.`);
  }, []);

  // 지도 bounds 기반 필터링 제거됨 (지도 미사용)

  const fetchServices = useCallback(
    async ({
      region,
      keywordOverride,
      categoryOverride,
      isInitialLoad = false, // 초기 로드 여부
      userLocation: userLocationOverride = null, // 사용자 위치 (초기 로드 시 내 주변 서비스 필터링용)
      latitude, // 위치 기반 검색: 위도
      longitude, // 위치 기반 검색: 경도
      radius, // 위치 기반 검색: 반경 (미터)
    }) => {
      const requestId = Date.now();
      latestRequestRef.current = requestId;

      setLoading(true);
      setStatusMessage('데이터 불러오는 중...');
      setError(null);

      const effectiveCategoryType = categoryOverride ?? categoryType;
      const apiCategory = effectiveCategoryType &&
        effectiveCategoryType !== CATEGORY_DEFAULT &&
        effectiveCategoryType !== CATEGORY_CUSTOM
        ? effectiveCategoryType
        : undefined;

      try {

        // 지역 계층별 검색만 수행 (내 위치는 거리 계산용으로만 사용)
        const regionParams = {};

        // 초기 로드 시 전략 선택
        if (isInitialLoad) {
          const targetLocation = userLocationOverride || userLocation;

          // ========== 성능 측정 시작 ==========
          const totalStartTime = performance.now();
          console.log('🚀 [성능 측정] 초기 로드 시작');

          // 전략: 위치 기반 검색 (10km 반경) + 백엔드 카테고리 필터링
          const apiStartTime = performance.now();
          let response;

          if (targetLocation) {
            // 사용자 위치가 있으면 위치 기반 검색 (10km 반경)
            console.log('📍 [위치 기반 검색] 사용자 위치 기반으로 10km 반경 검색');
            initialLoadTypeRef.current = 'location-based';
            response = await locationServiceApi.searchPlaces({
              latitude: targetLocation.lat,
              longitude: targetLocation.lng,
              radius: 10000, // 10km
              category: apiCategory, // 백엔드에서 카테고리 필터링
            });
          } else {
            // 사용자 위치가 없으면 전체 조회
            console.log('🌐 [전체 검색] 사용자 위치 없음 - 전체 조회');
            initialLoadTypeRef.current = 'all';
            response = await locationServiceApi.searchPlaces({
              category: apiCategory,
              size: null, // 전체 조회
            });
          }

          const apiTime = performance.now() - apiStartTime;
          console.log(`⏱️  [성능 측정] API 호출 시간: ${apiTime.toFixed(2)}ms`);
          console.log(`📊 [성능 측정] 조회된 데이터 수: ${response.data?.services?.length || 0}개`);

          if (latestRequestRef.current !== requestId) {
            return;
          }

          // 백엔드에서 이미 위치 기반 필터링이 완료되었으므로 거리 계산은 선택적
          // (표시용 거리 정보는 필요 시 계산)
          let allFetchedServices = (response.data?.services || []).map((service) => {
            let distance = null;
            if (targetLocation && service.latitude && service.longitude) {
              distance = calculateDistance(
                targetLocation.lat,
                targetLocation.lng,
                service.latitude,
                service.longitude
              );
            }
            return {
              ...service,
              distance,
            };
          });

          // 전체 데이터를 allServices에 저장 (지역 필터링에 사용)
          setAllServices(allFetchedServices);

          // 사용자 위치가 있으면 메시지 표시
          if (targetLocation) {
            setStatusMessage(`내 주변 10km 이내 ${allFetchedServices.length}개의 장소를 찾았습니다.`);
          } else {
            setStatusMessage(`전체 ${allFetchedServices.length}개의 장소를 찾았습니다.`);
          }

          // 선택된 지역에 따라 필터링 (현재 로드된 데이터 기준)
          const filterStartTime = performance.now();
          filterServicesByRegion(allFetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
          const filterTime = performance.now() - filterStartTime;
          console.log(`⏱️  [성능 측정] 필터링 시간: ${filterTime.toFixed(2)}ms`);

          // 메모리 사용량 측정
          if (performance.memory) {
            const memoryUsed = (performance.memory.usedJSHeapSize / 1024 / 1024).toFixed(2);
            const memoryTotal = (performance.memory.totalJSHeapSize / 1024 / 1024).toFixed(2);
            console.log(`💾 [성능 측정] 메모리 사용량: ${memoryUsed} MB / ${memoryTotal} MB`);
          }

          const totalTime = performance.now() - totalStartTime;
          console.log(`✅ [성능 측정] 전체 처리 시간: ${totalTime.toFixed(2)}ms`);
          console.log(`📈 [성능 측정] 시간 분해: API(${apiTime.toFixed(2)}ms) + 필터링(${filterTime.toFixed(2)}ms) = ${totalTime.toFixed(2)}ms`);
          // ========== 성능 측정 종료 ==========

          isInitialLoadRef.current = false;
          isSearchModeRef.current = false;
          setSelectedService(null);
          setLoading(false);
          return;
        }

        // 위치 기반 검색이 명시적으로 요청된 경우
        if (latitude != null && longitude != null && radius != null) {
          console.log('📍 [위치 기반 검색] API 호출:', { latitude, longitude, radius, category: apiCategory });

          const response = await locationServiceApi.searchPlaces({
            latitude,
            longitude,
            radius,
            category: apiCategory,
          });

          if (latestRequestRef.current !== requestId) {
            return;
          }

          const fetchedServices = (response.data?.services || []).map((service) => ({
            ...service,
            distance: null, // 위치 기반 검색 시 거리는 백엔드에서 계산됨
          }));

          console.log(`위치 기반 검색 결과: ${fetchedServices.length}개 서비스`, { latitude, longitude, radius });

          // 위치 기반 데이터를 allServices에 업데이트하고 필터링
          setAllServices(fetchedServices);
          filterServicesByRegion(fetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);

          isSearchModeRef.current = false;
          setStatusMessage(`반경 ${(radius / 1000).toFixed(1)}km 이내 ${fetchedServices.length}개의 장소를 찾았습니다.`);
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

          console.log('🌐 [지역 검색] API 호출:', { apiSido, apiSigungu, apiEupmyeondong, region });

          const response = await locationServiceApi.searchPlaces({
            sido: apiSido,
            sigungu: apiSigungu,
            eupmyeondong: apiEupmyeondong,
            category: apiCategory,
            size: null, // 제한 없음
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

        // 초기 로드가 아니고 지역 검색도 아닌 경우
        // 하이브리드 전략: 현재 데이터 범위 내면 필터링, 범위 밖이면 백엔드 재요청
        if (allServices.length > 0) {
          // 현재 로드된 데이터의 지역 범위 확인
          const loadedSidos = new Set(allServices.map(s => s.sido).filter(Boolean));
          const loadedSigungus = new Set(allServices.map(s => s.sigungu).filter(Boolean));

          // 선택한 지역이 현재 데이터 범위 내에 있는지 확인
          const isRegionInLoadedData =
            (!selectedSido || loadedSidos.has(selectedSido)) &&
            (!selectedSigungu || loadedSigungus.has(selectedSigungu));

          if (isRegionInLoadedData) {
            // 현재 데이터 범위 내: 프론트엔드 필터링
            console.log('📍 [하이브리드] 현재 데이터 범위 내 - 프론트엔드 필터링');
            filterServicesByRegion(allServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
            setLoading(false);
            return;
          } else {
            // 현재 데이터 범위 밖: 백엔드 재요청
            console.log('🌐 [하이브리드] 현재 데이터 범위 밖 - 백엔드 재요청');
            const response = await locationServiceApi.searchPlaces({
              sido: selectedSido || undefined,
              sigungu: selectedSigungu || undefined,
              eupmyeondong: selectedEupmyeondong || undefined,
              category: apiCategory,
            });

            if (latestRequestRef.current !== requestId) {
              return;
            }

            const fetchedServices = (response.data?.services || []).map((service) => ({
              ...service,
              distance: null, // 지역 검색 시 거리는 계산하지 않음
            }));

            setAllServices(fetchedServices);
            filterServicesByRegion(fetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
            setStatusMessage(`총 ${fetchedServices.length}개의 장소를 찾았습니다.`);
            setLoading(false);
            return;
          }
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
      }
    },
    [categoryType, selectedSido, selectedSigungu, selectedEupmyeondong, filterServicesByRegion, allServices, userLocation]
  );

  useEffect(() => {
    fetchServicesRef.current = fetchServices;
  }, [fetchServices]);

  useEffect(() => {
    // 초기 지도 중심 설정
    setMapCenter(DEFAULT_CENTER);
    setMapLevel(10); // 전국 뷰

    // 내 위치를 먼저 가져온 후, 내 주변 서비스를 보여주기
    const tryGeolocation = () => {
      if (!navigator.geolocation) {
        // 위치 정보를 가져올 수 없으면 전체 데이터 로드
        fetchServicesRef.current?.({
          isInitialLoad: true,
        });
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
          setUserLocation(location);
          setUserLocationAddress('현재 위치');

          // 지도 중심을 사용자 위치로 설정
          setMapCenter(location);
          setMapLevel(calculateMapLevelFromRadius(5)); // 5km 반경에 맞는 줌 레벨

          // 전체 데이터를 가져온 후 거리순으로 정렬하여 내 주변 서비스 표시
          fetchServicesRef.current?.({
            isInitialLoad: true,
            userLocation: location, // 사용자 위치 전달
          });
        },
        (error) => {
          console.warn('위치 정보를 가져올 수 없습니다:', error);
          // 위치 정보를 가져올 수 없으면 전체 데이터 로드
          fetchServicesRef.current?.({
            isInitialLoad: true,
          });
        },
        options
      );
    };

    tryGeolocation();
  }, []);

  const handleKeywordSubmit = useCallback(
    (event) => {
      event.preventDefault();
      setCategoryType(CATEGORY_CUSTOM);
      // 키워드 검색은 전체 데이터에서 필터링 (지도 없이)
      if (allServices.length > 0) {
        filterServicesByRegion(allServices, selectedSido, selectedSigungu, selectedEupmyeondong, keyword);
      } else {
        // allServices가 없으면 초기 로드
        fetchServices({
          isInitialLoad: true,
          categoryOverride: CATEGORY_CUSTOM,
        });
      }
    },
    [fetchServices, keyword, selectedSido, selectedSigungu, selectedEupmyeondong, allServices, filterServicesByRegion]
  );

  // 지도 위치 업데이트 함수
  // 시도 중심 좌표 fallback 헬퍼 함수
  const fallbackToSidoCenter = useCallback((targetSido, targetSigungu, resolve) => {
    console.log('🔄 [Fallback] 네이버 지오코딩 API 실패 → 시군구 중심 좌표 사용');
    // 시군구 중심 좌표가 있으면 우선 사용
    if (targetSigungu && SIGUNGU_CENTERS[targetSido] && SIGUNGU_CENTERS[targetSido][targetSigungu]) {
      const sigunguCenter = SIGUNGU_CENTERS[targetSido][targetSigungu];
      const selectedMapLevel = calculateMapLevelFromRadius(5);
      console.log('✅ [Fallback] 시군구 중심 좌표 사용:', {
        sido: targetSido,
        sigungu: targetSigungu,
        center: { lat: sigunguCenter.lat, lng: sigunguCenter.lng },
        mapLevel: selectedMapLevel,
        source: 'SIGUNGU_CENTERS (하드코딩)'
      });
      setMapCenter({ lat: sigunguCenter.lat, lng: sigunguCenter.lng });
      setMapLevel(selectedMapLevel);
      isProgrammaticMoveRef.current = true;
      resolve({ center: { lat: sigunguCenter.lat, lng: sigunguCenter.lng }, mapLevel: selectedMapLevel });
    } else if (SIDO_CENTERS[targetSido]) {
      // 시군구 좌표가 없으면 시도 중심 좌표 사용
      const sidoCenter = SIDO_CENTERS[targetSido];
      const selectedMapLevel = targetSigungu ? calculateMapLevelFromRadius(5) : 10;
      console.log('⚠️ [Fallback] 시군구 중심 좌표 없음 → 시도 중심 좌표 사용:', {
        sido: targetSido,
        sigungu: targetSigungu,
        center: { lat: sidoCenter.lat, lng: sidoCenter.lng },
        mapLevel: selectedMapLevel,
        source: 'SIDO_CENTERS (하드코딩)'
      });
      setMapCenter({ lat: sidoCenter.lat, lng: sidoCenter.lng });
      setMapLevel(selectedMapLevel);
      isProgrammaticMoveRef.current = true;
      resolve({ center: { lat: sidoCenter.lat, lng: sidoCenter.lng }, mapLevel: selectedMapLevel });
    } else {
      console.error('❌ [Fallback] 시도 중심 좌표도 없음 - 지도 이동 실패');
      resolve(null);
    }
  }, []);

  const updateMapLocation = useCallback(async (targetSido, targetSigungu, targetEupmyeondong) => {
    // 전국 선택 시 기본 위치로
    if (!targetSido) {
      setMapCenter(DEFAULT_CENTER);
      setMapLevel(10);
      isProgrammaticMoveRef.current = true;
      return { center: DEFAULT_CENTER, mapLevel: 10 };
    }

    // 시도만 선택한 경우: 하드코딩된 중심 좌표 사용
    if (!targetSigungu && SIDO_CENTERS[targetSido]) {
      const center = SIDO_CENTERS[targetSido];
      const sidoZoomLevels = {
        '서울특별시': 11,
        '부산광역시': 10,
        '대구광역시': 12,
        '인천광역시': 12,
        '광주광역시': 11,
        '대전광역시': 11,
        '울산광역시': 11,
        '세종특별자치시': 11,
        '경기도': 13,
        '강원특별자치도': 13,
        '충청북도': 13,
        '충청남도': 13,
        '전북특별자치도': 13,
        '전라남도': 13,
        '경상북도': 13,
        '경상남도': 13,
        '제주특별자치도': 13,
      };
      const selectedMapLevel = sidoZoomLevels[targetSido] || 10;
      setMapCenter({ lat: center.lat, lng: center.lng });
      setMapLevel(selectedMapLevel);
      isProgrammaticMoveRef.current = true;
      return { center: { lat: center.lat, lng: center.lng }, mapLevel: selectedMapLevel };
    }

    // 시군구 선택한 경우: 지오코딩 API 호출 없이 바로 시군구 중심 좌표 사용
    // (네이버 지오코딩 API는 광역 지역명에 대해 결과를 반환하지 않으므로 불필요한 API 호출 제거)
    if (targetSigungu && !targetEupmyeondong) {
      if (SIGUNGU_CENTERS[targetSido] && SIGUNGU_CENTERS[targetSido][targetSigungu]) {
        const sigunguCenter = SIGUNGU_CENTERS[targetSido][targetSigungu];
        const selectedMapLevel = calculateMapLevelFromRadius(5);
        console.log('✅ [지도 이동] 시군구 중심 좌표 사용:', {
          sido: targetSido,
          sigungu: targetSigungu,
          center: { lat: sigunguCenter.lat, lng: sigunguCenter.lng },
          mapLevel: selectedMapLevel,
          source: 'SIGUNGU_CENTERS (하드코딩)'
        });
        setMapCenter({ lat: sigunguCenter.lat, lng: sigunguCenter.lng });
        setMapLevel(selectedMapLevel);
        isProgrammaticMoveRef.current = true;
        return { center: { lat: sigunguCenter.lat, lng: sigunguCenter.lng }, mapLevel: selectedMapLevel };
      } else {
        // 시군구 중심 좌표가 없으면 시도 중심 좌표로 fallback
        console.warn('⚠️ [지도 이동] 시군구 중심 좌표 없음 - 시도 중심 좌표 사용:', {
          sido: targetSido,
          sigungu: targetSigungu
        });
        if (SIDO_CENTERS[targetSido]) {
          const sidoCenter = SIDO_CENTERS[targetSido];
          const selectedMapLevel = calculateMapLevelFromRadius(5);
          setMapCenter({ lat: sidoCenter.lat, lng: sidoCenter.lng });
          setMapLevel(selectedMapLevel);
          isProgrammaticMoveRef.current = true;
          return { center: { lat: sidoCenter.lat, lng: sidoCenter.lng }, mapLevel: selectedMapLevel };
        }
      }
    }

    // 동(읍면동) 선택한 경우: 지오코딩 API 호출 (구체적인 주소이므로 성공 가능성 높음)
    if (targetEupmyeondong) {
      const address = `${targetSido} ${targetSigungu} ${targetEupmyeondong}`;
      console.log('📍 [지도 이동] 동 선택 - 지오코딩 API 호출:', { address });

      return new Promise((resolve) => {
        geocodingApi.addressToCoordinates(address)
          .then(coordData => {
            if (coordData && coordData.success !== false && coordData.latitude && coordData.longitude) {
              const selectedMapLevel = calculateMapLevelFromRadius(3);
              console.log('✅ [지도 이동] 지오코딩 API 성공:', {
                address,
                center: { lat: coordData.latitude, lng: coordData.longitude },
                mapLevel: selectedMapLevel
              });
              setMapCenter({ lat: coordData.latitude, lng: coordData.longitude });
              setMapLevel(selectedMapLevel);
              isProgrammaticMoveRef.current = true;
              resolve({ center: { lat: coordData.latitude, lng: coordData.longitude }, mapLevel: selectedMapLevel });
            } else {
              // 지오코딩 실패 시 시군구 중심 좌표로 fallback
              console.warn('⚠️ [지도 이동] 지오코딩 API 실패 - 시군구 중심 좌표로 fallback:', {
                address,
                response: coordData
              });
              fallbackToSidoCenter(targetSido, targetSigungu, resolve);
            }
          })
          .catch(err => {
            console.error('❌ [지도 이동] 지오코딩 API 호출 실패 - 시군구 중심 좌표로 fallback:', {
              address,
              error: err.message,
              response: err.response?.data
            });
            fallbackToSidoCenter(targetSido, targetSigungu, resolve);
          });
      });
    }

    return null;
  }, []);

  const handleRegionSearch = useCallback(async (sidoOverride = null, sigunguOverride = null, eupmyeondongOverride = null, viewOverride = null) => {
    // target 값 계산: null이면 빈 문자열, 아니면 해당 값 사용
    const targetSido = sidoOverride !== null ? sidoOverride : '';
    const targetSigungu = sigunguOverride !== null ? sigunguOverride : '';
    const targetEupmyeondong = eupmyeondongOverride !== null ? eupmyeondongOverride : '';

    // 상태는 무조건 세팅해야 UI가 정상적으로 넘어감
    setSelectedSido(targetSido);
    setSelectedSigungu(targetSigungu);
    setSelectedEupmyeondong(targetEupmyeondong);

    // 화면 상태 업데이트 (viewOverride가 있으면 그것을 사용, 없으면 자동 계산)
    // 동 선택 화면 제거: 시도 또는 시군구 선택 화면만 사용
    if (viewOverride) {
      setCurrentView(viewOverride);
    } else {
      if (!targetSido) {
        setCurrentView('sido');
      } else {
        setCurrentView('sigungu');
      }
    }

    // 전국 선택 시
    if (!targetSido) {
      await updateMapLocation('', '', '');
      await fetchServices({
        isInitialLoad: true,
        categoryOverride: categoryType,
      });
      return;
    }

    // 지도 위치 업데이트
    const mapResult = await updateMapLocation(targetSido, targetSigungu, targetEupmyeondong);
    if (!mapResult) {
      return; // 위치 업데이트 실패
    }

    // 시군구 선택 시 RegionControls 닫기
    if (targetSigungu) {
      // 시군구 선택 완료
    }

    // 지역 정보만으로 API 호출
    let targetRegion = targetSido;
    if (targetSigungu) {
      targetRegion = `${targetSido} ${targetSigungu}`;
    }

    try {
      setStatusMessage(`'${targetRegion}' 주변 장소를 검색하는 중...`);
      setError(null);

      await fetchServices({
        region: targetRegion,
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

  // 시도/시군구/읍면동 선택 시 자동으로 서비스 필터링 및 지도 이동
  // 카테고리 변경은 버튼 클릭에서 처리하므로 여기서는 지역 선택만 처리
  useEffect(() => {
    if (isInitialLoadRef.current) {
      return; // 초기 로드 중이면 무시
    }

    const effectiveCategoryType = categoryType !== CATEGORY_DEFAULT && categoryType !== CATEGORY_CUSTOM
      ? categoryType
      : undefined;

    // 지역 선택 시: 지도 위치 업데이트 + 데이터 조회 전략
    if (selectedSido || selectedSigungu || selectedEupmyeondong) {
      // 동 선택 시: 지오코딩 후 위치 기반 검색 (반경)
      if (selectedEupmyeondong) {
        console.log('📍 [지역 선택] 동 선택 - 지오코딩 후 위치 기반 검색');
        updateMapLocation(selectedSido, selectedSigungu, selectedEupmyeondong)
          .then(locationResult => {
            if (locationResult && locationResult.center) {
              // 지오코딩 성공 시 위치 기반 검색
              fetchServices({
                latitude: locationResult.center.lat,
                longitude: locationResult.center.lng,
                radius: 5000, // 5km 반경
                categoryOverride: effectiveCategoryType,
              });
            } else {
              // 지오코딩 실패 시 지역 기반 검색으로 fallback
              console.warn('⚠️ [지역 선택] 지오코딩 실패 - 지역 기반 검색으로 fallback');
              fetchServices({
                region: [selectedSido, selectedSigungu, selectedEupmyeondong].filter(Boolean).join(' '),
                categoryOverride: effectiveCategoryType,
              });
            }
          })
          .catch(err => {
            console.error('❌ [지역 선택] 지도 위치 업데이트 실패:', err);
            // 에러 발생 시 지역 기반 검색으로 fallback
            fetchServices({
              region: [selectedSido, selectedSigungu, selectedEupmyeondong].filter(Boolean).join(' '),
              categoryOverride: effectiveCategoryType,
            });
          });
        return;
      }

      // 시도/시군구 선택 시: 지도 위치 업데이트 (지오코딩 API 호출 없음)
      updateMapLocation(selectedSido, selectedSigungu, selectedEupmyeondong).catch(err => {
        console.warn('지도 위치 업데이트 실패:', err);
      });

      // 시도/시군구 선택 시: 하이브리드 전략
      if (allServices.length > 0 && initialLoadTypeRef.current === 'location-based') {
        // 초기 로드가 위치 기반이면 범위 내 필터링, 범위 밖이면 백엔드 재요청
        const loadedSidos = new Set(allServices.map(s => s.sido).filter(Boolean));
        const loadedSigungus = new Set(allServices.map(s => s.sigungu).filter(Boolean));

        const isRegionInLoadedData =
          (!selectedSido || loadedSidos.has(selectedSido)) &&
          (!selectedSigungu || loadedSigungus.has(selectedSigungu));

        if (isRegionInLoadedData) {
          // 현재 데이터 범위 내: 프론트엔드 필터링
          console.log('📍 [지역 선택] 위치 기반 데이터 범위 내 - 프론트엔드 필터링');
          filterServicesByRegion(allServices, selectedSido, selectedSigungu, selectedEupmyeondong, effectiveCategoryType);
        } else {
          // 현재 데이터 범위 밖: 백엔드 지역 기반 검색
          console.log('🌐 [지역 선택] 위치 기반 데이터 범위 밖 - 백엔드 지역 기반 검색');
          fetchServices({
            region: [selectedSido, selectedSigungu, selectedEupmyeondong].filter(Boolean).join(' '),
            categoryOverride: effectiveCategoryType,
          });
        }
      } else {
        // 초기 로드가 전체 조회이거나 데이터가 없으면 백엔드 지역 기반 검색
        console.log('🌐 [지역 선택] 백엔드 지역 기반 검색');
        fetchServices({
          region: [selectedSido, selectedSigungu, selectedEupmyeondong].filter(Boolean).join(' '),
          categoryOverride: effectiveCategoryType,
        });
      }
    } else if (allServices.length > 0) {
      // 지역 선택이 없으면 현재 카테고리로 필터링만
      filterServicesByRegion(allServices, selectedSido, selectedSigungu, selectedEupmyeondong, effectiveCategoryType);
    }
  }, [selectedSido, selectedSigungu, selectedEupmyeondong, allServices, filterServicesByRegion, fetchServices, categoryType, updateMapLocation]);


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
    // 서비스 위치로 지도 이동
    if (service.latitude && service.longitude) {
      isProgrammaticMoveRef.current = true;
      setMapCenter({ lat: service.latitude, lng: service.longitude });
      setMapLevel(8); // 상세 뷰로 확대
    }
  }, []);

  // 마커 클릭 핸들러
  const handleMarkerClick = useCallback((service) => {
    handleServiceSelect(service);
  }, [handleServiceSelect]);

  // 지도 이동/확대축소 시 처리
  const handleMapIdle = useCallback((mapInfo) => {
    if (!mapInfo || !mapInfo.lat || !mapInfo.lng) {
      return;
    }

    const newCenter = {
      lat: mapInfo.lat,
      lng: mapInfo.lng,
    };

    // 위치가 실제로 변경되었을 때만 업데이트
    const isLocationChanged = !mapCenter ||
      Math.abs(mapCenter.lat - newCenter.lat) > 0.0001 ||
      Math.abs(mapCenter.lng - newCenter.lng) > 0.0001;

    if (isLocationChanged) {
      // 프로그래매틱 이동이 아니면 mapCenter 업데이트
      if (!isProgrammaticMoveRef.current) {
        setMapCenter(newCenter);
      } else {
        isProgrammaticMoveRef.current = false;
      }
    }
  }, [mapCenter]);

  const handleRecenterToUser = useCallback(() => {
    if (!userLocation) {
      return;
    }
    // 지도 관련 코드 제거됨 (내 위치는 거리 계산용으로만 사용)
    setStatusMessage('내 위치는 거리 계산에만 사용됩니다.');
  }, [userLocation]);

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
                  await fetchServices({
                    isInitialLoad: true,
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
            <RegionButtonGrid>
              {KEYWORD_CATEGORIES.map((cat) => (
                <RegionButton
                  key={cat.value}
                  onClick={() => {
                    const categoryValue = cat.value;
                    setSelectedKeywordCategory(categoryValue);
                    setKeyword(categoryValue);
                    if (categoryValue) {
                      // 카테고리 선택 시: 백엔드 재요청 (백엔드에서 카테고리 필터링)
                      setCategoryType(CATEGORY_CUSTOM);
                      const targetLocation = userLocation;
                      if (targetLocation) {
                        // 위치 기반 재요청
                        fetchServices({
                          categoryOverride: categoryValue,
                          userLocation: targetLocation,
                        });
                      } else if (selectedSido || selectedSigungu || selectedEupmyeondong) {
                        // 지역 기반 재요청
                        fetchServices({
                          region: [selectedSido, selectedSigungu, selectedEupmyeondong].filter(Boolean).join(' '),
                          categoryOverride: categoryValue,
                        });
                      } else {
                        // 전체 조회 재요청
                        fetchServices({
                          categoryOverride: categoryValue,
                        });
                      }
                    } else {
                      // 전체 선택 시: 백엔드 재요청
                      setCategoryType(CATEGORY_DEFAULT);
                      const targetLocation = userLocation;
                      if (targetLocation) {
                        // 위치 기반 재요청
                        fetchServices({
                          categoryOverride: undefined,
                          userLocation: targetLocation,
                        });
                      } else if (selectedSido || selectedSigungu || selectedEupmyeondong) {
                        // 지역 기반 재요청
                        fetchServices({
                          region: [selectedSido, selectedSigungu, selectedEupmyeondong].filter(Boolean).join(' '),
                          categoryOverride: undefined,
                        });
                      } else {
                        // 전체 조회 재요청
                        fetchServices({
                          categoryOverride: undefined,
                        });
                      }
                    }
                  }}
                  active={selectedKeywordCategory === cat.value}
                >
                  {cat.label}
                </RegionButton>
              ))}
            </RegionButtonGrid>
          </SearchControls>
        ) : (
          <RegionControls>
            {currentView === 'sido' ? (
              // 시/도 선택 화면
              <RegionButtonGrid>
                {SIDOS.map((sido) => (
                  <RegionButton
                    key={sido}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      // 시/도 검색
                      handleRegionSearch(sido, null, null);
                    }}
                    onMouseEnter={() => {
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
            ) : (
              // 시/군/구 선택 화면
              <RegionButtonGrid>
                <RegionButton
                  onClick={async () => {
                    // 시도 선택 화면으로 돌아가기
                    await handleRegionSearch(selectedSido, null, null, 'sido');
                  }}
                >
                  ← 뒤로
                </RegionButton>
                {(availableSigungus.length > 0 ? availableSigungus : (SIGUNGUS[selectedSido] || [])).map((sigungu) => (
                  <RegionButton
                    key={sigungu}
                    onClick={async () => {
                      // 시/군/구 검색
                      await handleRegionSearch(selectedSido, sigungu, null);
                    }}
                    active={selectedSigungu === sigungu}
                  >
                    {sigungu}
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
        {mapCenter && (
          <MapSection>
            <MapContainer
              services={servicesWithDisplay.map(service => ({
                idx: service.idx || service.externalId,
                name: service.name,
                latitude: service.latitude,
                longitude: service.longitude,
                address: service.address,
                type: 'service',
              }))}
              onServiceClick={handleMarkerClick}
              userLocation={userLocation}
              mapCenter={mapCenter}
              mapLevel={mapLevel}
              onMapIdle={handleMapIdle}
            />
          </MapSection>
        )}

        <ServiceListPanel>
          <ServiceListHeader>
            <ServiceListTitle>
              {userLocation ? '내 주변 장소' : '전체 장소'} ({servicesWithDisplay.length})
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
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.5rem 0;
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
  min-width: 200px;
  max-width: 300px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
  }

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
  display: flex;
  overflow: hidden;
  position: relative;
  min-height: 0;
  background: ${props => props.theme.colors.background};

  @media (max-width: 1024px) {
    flex-direction: column;
  }
`;

const MapSection = styled.div`
  flex: 1;
  position: relative;
  min-width: 0;
  overflow: hidden;
`;

// MapWrapper, LoadingOverlay 제거됨 (지도 미사용)

const ServiceListPanel = styled.div`
  width: 350px;
  min-width: 300px;
  background: ${props => props.theme.colors.surface};
  display: flex;
  flex-direction: column;
  z-index: 150;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border-left: 1px solid ${props => props.theme.colors.border};
  flex-shrink: 0;

  @media (max-width: 1024px) {
    width: 100%;
    min-width: unset;
    border-left: none;
    border-top: 1px solid ${props => props.theme.colors.border};
    max-height: 400px;
    flex-shrink: 1;
  }
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
