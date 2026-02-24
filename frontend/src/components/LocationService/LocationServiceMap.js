import React, { useCallback, useEffect, useMemo, useRef, useState, useReducer } from 'react';
import styled from 'styled-components';
import { locationServiceApi } from '../../api/locationServiceApi';
import { geocodingApi } from '../../api/geocodingApi';
import { locationServiceReviewApi } from '../../api/locationServiceReviewApi';
import { useAuth } from '../../contexts/AuthContext';
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

// ========== 검색 상태 Reducer ==========
const searchReducer = (state, action) => {
  switch (action.type) {
    case 'SET_KEYWORD':
      return { ...state, keyword: action.payload };
    case 'SET_KEYWORD_CATEGORY':
      return { ...state, selectedKeywordCategory: action.payload };
    case 'SET_ADDRESS_QUERY':
      return { ...state, addressQuery: action.payload };
    case 'SET_CATEGORY_TYPE':
      return { ...state, categoryType: action.payload };
    case 'SET_SEARCH_MODE':
      return { ...state, searchMode: action.payload };
    case 'RESET_SEARCH':
      return {
        keyword: '',
        selectedKeywordCategory: '',
        addressQuery: '',
        categoryType: CATEGORY_DEFAULT,
        searchMode: 'keyword',
      };
    default:
      return state;
  }
};

const initialSearchState = {
  keyword: '',
  selectedKeywordCategory: '',
  addressQuery: '',
  categoryType: CATEGORY_DEFAULT,
  searchMode: 'keyword',
};

// ========== 지역 선택 상태 Reducer ==========
const regionReducer = (state, action) => {
  switch (action.type) {
    case 'SET_SIDO':
      return {
        ...state,
        selectedSido: action.payload,
        selectedSigungu: '', // 시도 선택 시 시군구 초기화
        selectedEupmyeondong: '', // 시도 선택 시 읍면동 초기화
        currentView: 'sigungu', // 시군구 선택 화면으로 전환
      };
    case 'SET_SIGUNGU':
      return {
        ...state,
        selectedSigungu: action.payload,
        selectedEupmyeondong: '', // 시군구 선택 시 읍면동 초기화
        currentView: 'sigungu',
      };
    case 'SET_EUPMYEONDONG':
      return {
        ...state,
        selectedEupmyeondong: action.payload,
      };
    case 'SET_CURRENT_VIEW':
      return { ...state, currentView: action.payload };
    case 'SET_REGION':
      // 한 번에 여러 지역 설정
      return {
        ...state,
        selectedSido: action.payload.sido ?? state.selectedSido,
        selectedSigungu: action.payload.sigungu ?? state.selectedSigungu,
        selectedEupmyeondong: action.payload.eupmyeondong ?? state.selectedEupmyeondong,
        currentView: action.payload.currentView ?? state.currentView,
      };
    case 'RESET_REGION':
      return {
        selectedSido: '',
        selectedSigungu: '',
        selectedEupmyeondong: '',
        currentView: 'sido',
      };
    default:
      return state;
  }
};

const initialRegionState = {
  selectedSido: '',
  selectedSigungu: '',
  selectedEupmyeondong: '',
  currentView: 'sido',
};

// ========== UI 상태 Reducer ==========
const uiReducer = (state, action) => {
  switch (action.type) {
    case 'SET_LOADING':
      return { ...state, loading: action.payload };
    case 'SET_ERROR':
      return { ...state, error: action.payload };
    case 'SET_STATUS_MESSAGE':
      return { ...state, statusMessage: action.payload };
    case 'SET_SELECTED_SERVICE':
      return { ...state, selectedService: action.payload };
    case 'SET_HOVERED_SERVICE':
      return { ...state, hoveredService: action.payload };
    case 'SET_SHOW_DIRECTIONS':
      return { ...state, showDirections: action.payload };
    case 'SET_SHOW_KEYWORD_CONTROLS':
      return { ...state, showKeywordControls: action.payload };
    case 'SET_SHOW_REGION_CONTROLS':
      return { ...state, showRegionControls: action.payload };
    case 'SET_UI':
      // 한 번에 여러 UI 상태 업데이트
      return { ...state, ...action.payload };
    case 'RESET_UI':
      return {
        loading: false,
        error: null,
        statusMessage: '지도 준비 중...',
        selectedService: null,
        hoveredService: null,
        showDirections: false,
        showKeywordControls: false,
        showRegionControls: false,
      };
    default:
      return state;
  }
};

const initialUIState = {
  loading: false,
  error: null,
  statusMessage: '지도 준비 중...',
  selectedService: null,
  hoveredService: null,
  showDirections: false,
  showKeywordControls: false,
  showRegionControls: false,
};

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

// 주소에서 시도/시군구 추출 헬퍼 함수
const extractSidoFromAddress = (address) => {
  if (!address) return null;

  // SIDOS 배열에서 가장 긴 매칭을 찾음 (예: "서울특별시"가 "서울"보다 우선)
  for (const sido of SIDOS.sort((a, b) => b.length - a.length)) {
    if (address.includes(sido)) {
      return sido;
    }
  }
  return null;
};

const extractSigunguFromAddress = (address, sido) => {
  if (!address || !sido) return null;

  // 시도 부분 제거
  const addressWithoutSido = address.replace(sido, '').trim();

  // SIGUNGUS에서 해당 시도의 시군구 목록 가져오기
  const sigunguList = SIGUNGUS[sido] || [];

  // 가장 긴 매칭을 찾음
  for (const sigungu of sigunguList.sort((a, b) => b.length - a.length)) {
    if (addressWithoutSido.includes(sigungu)) {
      return sigungu;
    }
  }
  return null;
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
  const { user } = useAuth();
  const [allServices, setAllServices] = useState([]); // 전체 서비스 데이터 (하이브리드용)
  const [services, setServices] = useState([]); // 현재 표시할 서비스 (필터링된 데이터)
  // ✅ UI 상태를 useReducer로 관리
  const [uiState, dispatchUI] = useReducer(uiReducer, initialUIState);
  const { loading, error, statusMessage, selectedService, hoveredService, showDirections, showKeywordControls, showRegionControls } = uiState;

  // 리뷰 관련 상태
  const [reviews, setReviews] = useState([]);
  const [loadingReviews, setLoadingReviews] = useState(false);
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [editingReview, setEditingReview] = useState(null);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [submittingReview, setSubmittingReview] = useState(false);

  // ✅ 검색 상태를 useReducer로 관리
  const [searchState, dispatchSearch] = useReducer(searchReducer, initialSearchState);
  const { keyword, selectedKeywordCategory, addressQuery, categoryType, searchMode } = searchState;

  // ✅ 지역 선택 상태를 useReducer로 관리
  const [regionState, dispatchRegion] = useReducer(regionReducer, initialRegionState);
  const { selectedSido, selectedSigungu, selectedEupmyeondong, currentView } = regionState;

  // 기타 상태 (아직 그룹화하지 않은 상태들)
  const [directionsData, setDirectionsData] = useState(null);
  const [startLocationAddress, setStartLocationAddress] = useState(null); // 출발지 주소 (좌표 변환 결과)
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
  const initialLoadTypeRef = useRef(null); // 초기 로드 타입: 'region-based' (시도/시군구 기반) 또는 'all' (전체 조회)
  const mapIdleTimeoutRef = useRef(null); // 지도 드래그 디바운싱용

  // "지도는 상태를 바꾸지 않는다" 원칙 적용
  const [pendingSearchLocation, setPendingSearchLocation] = useState(null); // 대기 중인 검색 위치
  const [showSearchButton, setShowSearchButton] = useState(false); // "이 지역 검색" 버튼 표시 여부
  const [loadingRecommend, setLoadingRecommend] = useState(false); // AI 추천 로딩

  // 리뷰 조회
  const fetchReviews = useCallback(async (serviceIdx) => {
    if (!serviceIdx) return;
    setLoadingReviews(true);
    try {
      const response = await locationServiceReviewApi.getReviewsByService(serviceIdx);
      setReviews(response.data?.reviews || []);
    } catch (error) {
      console.error('리뷰 조회 실패:', error);
      setReviews([]);
    } finally {
      setLoadingReviews(false);
    }
  }, []);

  // 서비스 선택 시 리뷰 조회
  useEffect(() => {
    if (selectedService?.idx) {
      fetchReviews(selectedService.idx);
    } else {
      setReviews([]);
    }
  }, [selectedService?.idx, fetchReviews]);

  // 리뷰 작성
  const handleSubmitReview = async () => {
    if (!selectedService?.idx || !user?.idx) {
      alert('리뷰 작성에 필요한 정보가 없습니다.');
      return;
    }

    if (!reviewComment.trim()) {
      alert('리뷰 내용을 입력해주세요.');
      return;
    }

    setSubmittingReview(true);
    try {
      if (editingReview) {
        // 리뷰 수정
        await locationServiceReviewApi.updateReview(editingReview.idx, {
          rating: reviewRating,
          comment: reviewComment.trim(),
        });
        alert('리뷰가 수정되었습니다.');
      } else {
        // 리뷰 작성
        await locationServiceReviewApi.createReview({
          serviceIdx: selectedService.idx,
          userIdx: user.idx,
          rating: reviewRating,
          comment: reviewComment.trim(),
        });
        alert('리뷰가 작성되었습니다.');
      }

      setShowReviewModal(false);
      setEditingReview(null);
      setReviewRating(5);
      setReviewComment('');
      // 리뷰 목록 새로고침
      await fetchReviews(selectedService.idx);
    } catch (error) {
      console.error('리뷰 작성/수정 실패:', error);
      const errorMessage = error.response?.data?.error || error.message || '리뷰 작성에 실패했습니다.';
      alert(errorMessage);
    } finally {
      setSubmittingReview(false);
    }
  };

  // 리뷰 삭제
  const handleDeleteReview = async (reviewIdx) => {
    if (!window.confirm('리뷰를 삭제하시겠습니까?')) {
      return;
    }

    try {
      await locationServiceReviewApi.deleteReview(reviewIdx);
      alert('리뷰가 삭제되었습니다.');
      // 리뷰 목록 새로고침
      if (selectedService?.idx) {
        await fetchReviews(selectedService.idx);
      }
    } catch (error) {
      console.error('리뷰 삭제 실패:', error);
      alert(error.response?.data?.error || '리뷰 삭제에 실패했습니다.');
    }
  };

  // 리뷰 작성 모달 열기
  const handleOpenReviewModal = () => {
    setEditingReview(null);
    setReviewRating(5);
    setReviewComment('');
    setShowReviewModal(true);
  };

  // 리뷰 수정 모달 열기
  const handleEditReview = (review) => {
    setEditingReview(review);
    setReviewRating(review.rating);
    setReviewComment(review.comment || '');
    setShowReviewModal(true);
  };

  // 내가 작성한 리뷰 확인
  const myReview = useMemo(() => {
    if (!user?.idx || !reviews.length) return null;
    return reviews.find(review => review.userIdx === user.idx);
  }, [reviews, user?.idx]);

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
    dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: filtered.length === 0 ? '해당 지역에 표시할 장소가 없습니다.' : `총 ${filtered.length}개의 장소가 있습니다.` });
  }, []);

  // 지도 bounds 기반 필터링 제거됨 (지도 미사용)

  // ========== 검색 전략 함수 분리 ==========

  /**
   * 초기 로드 전략
   */
  const handleInitialLoad = useCallback(async ({
    targetLocation,
    apiCategory,
    effectiveKeyword,
    requestId,
    selectedSido,
    selectedSigungu,
    selectedEupmyeondong,
  }) => {
    const totalStartTime = performance.now();
    console.log('🚀 [성능 측정] 초기 로드 시작');

    const apiStartTime = performance.now();
    let response;

    if (targetLocation) {
      console.log('📍 [초기 로드] 내 위치 기반 반경 검색 (5km)');
      initialLoadTypeRef.current = 'location-based';
      response = await locationServiceApi.searchPlaces({
        latitude: targetLocation.lat,
        longitude: targetLocation.lng,
        radius: 5000,
        category: apiCategory,
        keyword: effectiveKeyword,
      });
    } else {
      console.log('🌐 [초기 로드] 사용자 위치 없음 - 전체 조회');
      initialLoadTypeRef.current = 'all';
      response = await locationServiceApi.searchPlaces({
        category: apiCategory,
        keyword: effectiveKeyword,
        size: 0,
      });
    }

    if (latestRequestRef.current !== requestId) {
      return;
    }

    const apiTime = performance.now() - apiStartTime;
    console.log(`⏱️  [성능 측정] API 호출 시간: ${apiTime.toFixed(2)}ms`);
    console.log(`📊 [성능 측정] 조회된 데이터 수: ${response.data?.services?.length || 0}개`);

    const allFetchedServices = (response.data?.services || []).map((service) => {
      const lat = parseFloat(service.latitude);
      const lng = parseFloat(service.longitude);

      let distance = service.distance || null;
      if (distance === null && !isNaN(lat) && !isNaN(lng) && targetLocation) {
        distance = calculateDistance(
          targetLocation.lat,
          targetLocation.lng,
          lat,
          lng
        );
      }

      return {
        ...service,
        latitude: lat,
        longitude: lng,
        distance,
      };
    });

    setAllServices(allFetchedServices);

    if (targetLocation) {
      dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `내 주변 5km 이내 ${allFetchedServices.length}개의 장소를 찾았습니다.` });
    } else {
      dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `전체 ${allFetchedServices.length}개의 장소를 찾았습니다.` });
    }

    const filterStartTime = performance.now();
    filterServicesByRegion(allFetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
    const filterTime = performance.now() - filterStartTime;
    console.log(`⏱️  [성능 측정] 필터링 시간: ${filterTime.toFixed(2)}ms`);

    if (performance.memory) {
      const memoryUsed = (performance.memory.usedJSHeapSize / 1024 / 1024).toFixed(2);
      const memoryTotal = (performance.memory.totalJSHeapSize / 1024 / 1024).toFixed(2);
      console.log(`💾 [성능 측정] 메모리 사용량: ${memoryUsed} MB / ${memoryTotal} MB`);
    }

    const totalTime = performance.now() - totalStartTime;
    console.log(`✅ [성능 측정] 전체 처리 시간: ${totalTime.toFixed(2)}ms`);
    console.log(`📈 [성능 측정] 시간 분해: API(${apiTime.toFixed(2)}ms) + 필터링(${filterTime.toFixed(2)}ms) = ${totalTime.toFixed(2)}ms`);

    isInitialLoadRef.current = false;
    isSearchModeRef.current = false;
    dispatchUI({ type: 'SET_UI', payload: { selectedService: null, loading: false } });
  }, [filterServicesByRegion]);

  /**
   * 위치 기반 검색 전략
   */
  const handleLocationBasedSearch = useCallback(async ({
    latitude,
    longitude,
    radius,
    apiCategory,
    effectiveKeyword,
    requestId,
  }) => {
    console.log('📍 [위치 기반 검색] API 호출:', { latitude, longitude, radius });

    const response = await locationServiceApi.searchPlaces({
      latitude,
      longitude,
      radius,
      category: apiCategory,
      keyword: effectiveKeyword,
    });

    if (latestRequestRef.current !== requestId) {
      return;
    }

    const fetchedServices = (response.data?.services || []).map((service) => {
      const lat = parseFloat(service.latitude);
      const lng = parseFloat(service.longitude);

      let distance = service.distance || null;
      if (distance === null && !isNaN(lat) && !isNaN(lng)) {
        distance = calculateDistance(latitude, longitude, lat, lng);
      }

      return {
        ...service,
        latitude: lat,
        longitude: lng,
        distance,
      };
    });

    fetchedServices.sort((a, b) => {
      if (a.distance === null && b.distance === null) return 0;
      if (a.distance === null) return 1;
      if (b.distance === null) return -1;
      return a.distance - b.distance;
    });

    console.log(`📍 [위치 기반 검색] 결과: ${fetchedServices.length}개 서비스`);

    setAllServices(fetchedServices);
    setServices(fetchedServices);
    dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `주변 ${radius / 1000}km 이내 ${fetchedServices.length}개의 장소를 찾았습니다.` });
    dispatchUI({ type: 'SET_UI', payload: { selectedService: null, loading: false } });
  }, []);

  /**
   * 지역 검색 전략 실행
   */
  const executeRegionSearchStrategy = useCallback(async ({
    region,
    apiCategory,
    effectiveKeyword,
    requestId,
    selectedSido,
    selectedSigungu,
    selectedEupmyeondong,
  }) => {
    const regionParts = region.trim().split(/\s+/);
    const apiSido = regionParts[0] || undefined;
    const apiSigungu = regionParts[1] || undefined;
    const apiEupmyeondong = regionParts[2] || undefined;

    const response = await locationServiceApi.searchPlaces({
      sido: apiSido,
      sigungu: apiSigungu,
      eupmyeondong: apiEupmyeondong,
      category: apiCategory,
      keyword: effectiveKeyword,
      size: 0,
    });

    if (latestRequestRef.current !== requestId) {
      return;
    }

    const fetchedServices = (response.data?.services || []).map((service) => ({
      ...service,
      latitude: parseFloat(service.latitude),
      longitude: parseFloat(service.longitude),
    }));

    setAllServices(fetchedServices);
    filterServicesByRegion(fetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);

    isSearchModeRef.current = false;
    dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '' });
    dispatchUI({ type: 'SET_UI', payload: { selectedService: null, loading: false } });
  }, [filterServicesByRegion]);

  /**
   * 하이브리드 전략 (현재 데이터 범위 확인 후 필터링 또는 재요청)
   * 
   * 개선: 지역 선택 시 항상 백엔드 재요청하여 일관성 확보
   * - 초기 로드가 위치 기반(5km 반경)이면 allServices에 반경 내 데이터만 포함됨
   * - 이후 지역 선택 시 프론트엔드 필터링만 하면 반경 밖 서비스가 누락됨
   * - 해결: 지역 선택이 있으면 항상 백엔드 재요청
   */
  const handleHybridSearch = useCallback(async ({
    allServices,
    selectedSido,
    selectedSigungu,
    selectedEupmyeondong,
    apiCategory,
    effectiveKeyword,
    requestId,
  }) => {
    // ✅ 개선: 지역 선택이 있으면 항상 백엔드 재요청 (일관성 확보)
    if (selectedSido || selectedSigungu || selectedEupmyeondong) {
      console.log('🌐 [하이브리드] 지역 선택 감지 - 백엔드 재요청 (일관성 확보)');
      const response = await locationServiceApi.searchPlaces({
        sido: selectedSido || undefined,
        sigungu: selectedSigungu || undefined,
        eupmyeondong: selectedEupmyeondong || undefined,
        category: apiCategory,
        keyword: effectiveKeyword,
      });

      if (latestRequestRef.current !== requestId) {
        return;
      }

      const fetchedServices = (response.data?.services || []).map((service) => ({
        ...service,
        latitude: parseFloat(service.latitude),
        longitude: parseFloat(service.longitude),
        distance: null,
      }));

      setAllServices(fetchedServices);
      filterServicesByRegion(fetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
      dispatchUI({ type: 'SET_UI', payload: { statusMessage: `총 ${fetchedServices.length}개의 장소를 찾았습니다.`, loading: false } });
      return;
    }

    // 지역 선택이 없을 때만 기존 하이브리드 전략 사용 (카테고리/키워드 변경 등)
    const loadedSidos = new Set(allServices.map(s => s.sido).filter(Boolean));
    const loadedSigungus = new Set(allServices.map(s => s.sigungu).filter(Boolean));

    const isRegionInLoadedData =
      (!selectedSido || loadedSidos.has(selectedSido)) &&
      (!selectedSigungu || loadedSigungus.has(selectedSigungu));

    if (isRegionInLoadedData) {
      console.log('📍 [하이브리드] 현재 데이터 범위 내 - 프론트엔드 필터링');
      filterServicesByRegion(allServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
      dispatchUI({ type: 'SET_LOADING', payload: false });
      return;
    }

    console.log('🌐 [하이브리드] 현재 데이터 범위 밖 - 백엔드 재요청');
    const response = await locationServiceApi.searchPlaces({
      sido: selectedSido || undefined,
      sigungu: selectedSigungu || undefined,
      eupmyeondong: selectedEupmyeondong || undefined,
      category: apiCategory,
      keyword: effectiveKeyword,
    });

    if (latestRequestRef.current !== requestId) {
      return;
    }

    const fetchedServices = (response.data?.services || []).map((service) => ({
      ...service,
      latitude: parseFloat(service.latitude),
      longitude: parseFloat(service.longitude),
      distance: null,
    }));

    setAllServices(fetchedServices);
    filterServicesByRegion(fetchedServices, selectedSido, selectedSigungu, selectedEupmyeondong, apiCategory);
    dispatchUI({ type: 'SET_UI', payload: { statusMessage: `총 ${fetchedServices.length}개의 장소를 찾았습니다.`, loading: false } });
  }, [filterServicesByRegion]);

  // ========== 메인 검색 함수 (단순화) ==========

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

      dispatchUI({ type: 'SET_UI', payload: { loading: true, statusMessage: '데이터 불러오는 중...', error: null } });

      const effectiveCategoryType = categoryOverride ?? categoryType;
      const apiCategory = effectiveCategoryType &&
        effectiveCategoryType !== CATEGORY_DEFAULT &&
        effectiveCategoryType !== CATEGORY_CUSTOM
        ? effectiveCategoryType
        : undefined;

      // 키워드 처리 (keywordOverride가 있으면 사용, 없으면 현재 keyword 상태 사용)
      const effectiveKeyword = keywordOverride ?? (keyword && keyword.trim() ? keyword.trim() : undefined);

      try {
        // 검색 전략 선택 및 실행
        if (isInitialLoad) {
          const targetLocation = userLocationOverride || userLocation;
          await handleInitialLoad({
            targetLocation,
            apiCategory,
            effectiveKeyword,
            requestId,
            selectedSido,
            selectedSigungu,
            selectedEupmyeondong,
          });
          return;
        }

        if (latitude != null && longitude != null && radius != null) {
          await handleLocationBasedSearch({
            latitude,
            longitude,
            radius,
            apiCategory,
            effectiveKeyword,
            requestId,
          });
          return;
        }

        if (region) {
          await executeRegionSearchStrategy({
            region,
            apiCategory,
            effectiveKeyword,
            requestId,
            selectedSido,
            selectedSigungu,
            selectedEupmyeondong,
          });
          return;
        }

        if (allServices.length > 0) {
          await handleHybridSearch({
            allServices,
            selectedSido,
            selectedSigungu,
            selectedEupmyeondong,
            apiCategory,
            effectiveKeyword,
            requestId,
          });
          return;
        }

        // allServices가 없으면 종료
        dispatchUI({ type: 'SET_LOADING', payload: false });
        return;
      } catch (err) {
        if (latestRequestRef.current !== requestId) {
          return;
        }

        const message = err.response?.data?.error || err.message;
        dispatchUI({ type: 'SET_UI', payload: { error: `장소 정보를 불러오지 못했습니다: ${message}`, statusMessage: '' } });
      } finally {
        if (latestRequestRef.current === requestId) {
          dispatchUI({ type: 'SET_LOADING', payload: false });
        }
      }
    },
    [
      categoryType,
      keyword,
      selectedSido,
      selectedSigungu,
      selectedEupmyeondong,
      allServices,
      userLocation,
      handleInitialLoad,
      handleLocationBasedSearch,
      executeRegionSearchStrategy,
      handleHybridSearch,
    ]
  );

  useEffect(() => {
    fetchServicesRef.current = fetchServices;
  }, [fetchServices]);

  // 컴포넌트 언마운트 시 타이머 정리
  useEffect(() => {
    return () => {
      if (mapIdleTimeoutRef.current) {
        clearTimeout(mapIdleTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    // 초기 로드: 내 위치 기반으로 지도 표시 및 주변 서비스 조회
    const initializeMap = async () => {
      dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '위치 정보를 가져오는 중...' });

      // 초기 로드 중이므로 프로그래매틱 이동으로 설정
      isProgrammaticMoveRef.current = true;

      // 1단계: 내 위치 가져오기 (재시도 로직 포함)
      const getCurrentLocation = (retryCount = 0, useHighAccuracy = false) => {
        return new Promise((resolve, reject) => {
          if (!navigator.geolocation) {
            reject(new Error('Geolocation을 지원하지 않습니다.'));
            return;
          }

          const options = {
            enableHighAccuracy: useHighAccuracy,
            timeout: 10000, // 10초로 증가
            maximumAge: 60000, // 1분 이내 캐시된 위치 사용 허용
          };

          navigator.geolocation.getCurrentPosition(
            (position) => {
              resolve({
                lat: position.coords.latitude,
                lng: position.coords.longitude,
              });
            },
            (error) => {
              // 재시도 로직: 최대 2번 재시도
              if (retryCount < 2) {
                console.log(`📍 위치 가져오기 재시도 ${retryCount + 1}/2...`);
                // 첫 번째 재시도는 고정밀도로 시도
                setTimeout(() => {
                  getCurrentLocation(retryCount + 1, true)
                    .then(resolve)
                    .catch(reject);
                }, 1000);
              } else {
                reject(error);
              }
            },
            options
          );
        });
      };

      try {
        // 내 위치 가져오기
        const location = await getCurrentLocation();

        // 사용자 위치 설정
        setUserLocation(location);
        setUserLocationAddress('현재 위치'); // 임시로 설정, 아래에서 주소로 변환

        // ✅ 현재 위치 좌표를 주소로 변환
        try {
          const addressData = await geocodingApi.coordinatesToAddress(
            location.lat,
            location.lng
          );

          // 응답 형식 확인: address 필드 또는 success 필드 확인
          if (addressData) {
            if (addressData.success === false) {
              console.warn('⚠️ 주소 변환 실패:', addressData.message || addressData.error);
            } else if (addressData.address) {
              setUserLocationAddress(addressData.address);
            } else {
              console.warn('⚠️ 주소 변환 결과에 address 필드가 없음:', addressData);
            }
          } else {
            console.warn('⚠️ 주소 변환 응답이 null:', addressData);
          }
        } catch (addressError) {
          console.error('❌ 현재 위치 주소 변환 실패:', addressError);
          console.error('❌ 에러 상세:', addressError.response?.data || addressError.message);
          // 실패해도 계속 진행 (기본값 "현재 위치" 유지)
        }

        // 지도 중심을 내 위치로 설정 (5km 반경에 맞는 줌 레벨)
        setMapCenter(location);
        setMapLevel(calculateMapLevelFromRadius(5)); // 5km 반경에 맞는 줌 레벨

        dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '주변 서비스를 불러오는 중...' });

        // 2단계: 초기 로드는 내 위치 기반 반경 검색 (빠르고 적은 데이터)
        const currentKeyword = keyword && keyword.trim() ? keyword.trim() : undefined;
        const response = await locationServiceApi.searchPlaces({
          latitude: location.lat,
          longitude: location.lng,
          radius: 5000, // 5km 반경
          keyword: currentKeyword,
        });

        if (response.data?.services) {
          // 거리 계산 및 정렬 (프론트엔드에서 처리)
          const servicesWithDistance = response.data.services.map((service) => {
            let distance = null;
            const lat = parseFloat(service.latitude);
            const lng = parseFloat(service.longitude);

            if (!isNaN(lat) && !isNaN(lng)) {
              distance = calculateDistance(
                location.lat,
                location.lng,
                lat,
                lng
              );
            }
            return {
              ...service,
              latitude: lat,
              longitude: lng,
              distance,
            };
          });

          // 거리순 정렬 (가까운 순)
          servicesWithDistance.sort((a, b) => {
            if (a.distance === null && b.distance === null) return 0;
            if (a.distance === null) return 1;
            if (b.distance === null) return -1;
            return a.distance - b.distance;
          });

          // 데이터 저장
          setAllServices(servicesWithDistance);
          setServices(servicesWithDistance);
          initialLoadTypeRef.current = 'location-based';

          dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `내 주변 5km 이내 ${servicesWithDistance.length}개의 장소를 찾았습니다.` });
        } else {
          setAllServices([]);
          setServices([]);
          dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '주변에 표시할 장소가 없습니다.' });
        }

        // 초기 로드 완료 후 사용자 드래그를 허용하기 위해 플래그 리셋
        setTimeout(() => {
          isProgrammaticMoveRef.current = false;
        }, 2000); // 2초 후 리셋 (지도 로드 완료 대기)
      } catch (error) {
        console.warn('위치 정보를 가져올 수 없습니다:', error);
        console.warn('에러 코드:', error.code, '에러 메시지:', error.message);

        // 위치 권한 거부 시 빈 상태 UX 표시
        if (error.code === 1) {
          // PERMISSION_DENIED - 사용자가 위치 권한을 거부함
          setAllServices([]);
          setServices([]);
          dispatchUI({ type: 'SET_UI', payload: { statusMessage: '위치 권한이 필요합니다. 브라우저 설정에서 위치 권한을 허용해주세요.', error: null } });
          // 빈 상태는 UI에서 처리됨
        } else if (error.code === 2) {
          // POSITION_UNAVAILABLE - 위치 정보를 사용할 수 없음
          console.warn('⚠️ 위치 정보를 사용할 수 없습니다. 기본 위치로 설정합니다.');
          setMapCenter(DEFAULT_CENTER);
          setMapLevel(10);
          isProgrammaticMoveRef.current = true;
          dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '위치를 확인할 수 없어 전체 서비스를 불러옵니다...' });

          try {
            const currentKeyword = keyword && keyword.trim() ? keyword.trim() : undefined;
            const response = await locationServiceApi.searchPlaces({
              keyword: currentKeyword,
              size: 0, // 전체 조회
            });

            if (response.data?.services) {
              setAllServices(response.data.services);
              setServices(response.data.services);
              initialLoadTypeRef.current = 'all';
              dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `전체 ${response.data.services.length}개의 장소를 찾았습니다.` });
            } else {
              setAllServices([]);
              setServices([]);
              dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '서비스를 불러올 수 없습니다.' });
            }
          } catch (fetchError) {
            console.error('전체 서비스 조회 실패:', fetchError);
            setAllServices([]);
            setServices([]);
            dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '서비스를 불러오는 중 오류가 발생했습니다.' });
          }
        } else if (error.code === 3) {
          // TIMEOUT - 타임아웃
          console.warn('⚠️ 위치 가져오기 타임아웃. 기본 위치로 설정합니다.');
          setMapCenter(DEFAULT_CENTER);
          setMapLevel(10);
          isProgrammaticMoveRef.current = true;
          dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '위치 확인 시간이 초과되어 전체 서비스를 불러옵니다...' });

          try {
            const currentKeyword = keyword && keyword.trim() ? keyword.trim() : undefined;
            const response = await locationServiceApi.searchPlaces({
              keyword: currentKeyword,
              size: 0, // 전체 조회
            });

            if (response.data?.services) {
              setAllServices(response.data.services);
              setServices(response.data.services);
              initialLoadTypeRef.current = 'all';
              dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `전체 ${response.data.services.length}개의 장소를 찾았습니다.` });
            } else {
              setAllServices([]);
              setServices([]);
              dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '서비스를 불러올 수 없습니다.' });
            }
          } catch (fetchError) {
            console.error('전체 서비스 조회 실패:', fetchError);
            setAllServices([]);
            setServices([]);
            dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '서비스를 불러오는 중 오류가 발생했습니다.' });
          }
        } else {
          // 기타 에러
          console.warn('⚠️ 위치 정보를 가져올 수 없습니다. 기본 위치로 설정합니다.');
          setMapCenter(DEFAULT_CENTER);
          setMapLevel(10);
          isProgrammaticMoveRef.current = true;
          dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '위치를 확인할 수 없어 전체 서비스를 불러옵니다...' });

          try {
            const currentKeyword = keyword && keyword.trim() ? keyword.trim() : undefined;
            const response = await locationServiceApi.searchPlaces({
              keyword: currentKeyword,
              size: 0, // 전체 조회
            });

            if (response.data?.services) {
              setAllServices(response.data.services);
              setServices(response.data.services);
              initialLoadTypeRef.current = 'all';
              dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `전체 ${response.data.services.length}개의 장소를 찾았습니다.` });
            } else {
              setAllServices([]);
              setServices([]);
              dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '표시할 장소가 없습니다.' });
            }
          } catch (fetchError) {
            console.error('서비스 조회 실패:', fetchError);
            dispatchUI({ type: 'SET_UI', payload: { error: `장소 정보를 불러오지 못했습니다: ${fetchError.message}`, statusMessage: '' } });
          }

          // 초기 로드 완료 후 사용자 드래그를 허용하기 위해 플래그 리셋
          setTimeout(() => {
            isProgrammaticMoveRef.current = false;
            console.log('📍 [초기 로드 완료] isProgrammaticMoveRef.current = false로 리셋');
          }, 2000); // 2초 후 리셋
        }
      } finally {
        isInitialLoadRef.current = false;
        dispatchUI({ type: 'SET_LOADING', payload: false });
      }
    };

    initializeMap();
  }, []);

  const handleKeywordSubmit = useCallback(
    (event) => {
      event.preventDefault();
      if (!keyword || !keyword.trim()) {
        return; // 빈 키워드는 무시
      }

      // 키워드 검색: 백엔드에 keyword 전달하여 FULLTEXT 검색 수행
      fetchServices({
        keywordOverride: keyword.trim(),
        categoryOverride: categoryType !== CATEGORY_DEFAULT ? categoryType : undefined,
      });
    },
    [fetchServices, keyword, categoryType]
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
    // ✅ useReducer로 한 번에 업데이트
    const newCurrentView = viewOverride
      ? viewOverride
      : (!targetSido ? 'sido' : 'sigungu');

    dispatchRegion({
      type: 'SET_REGION',
      payload: {
        sido: targetSido,
        sigungu: targetSigungu,
        eupmyeondong: targetEupmyeondong,
        currentView: newCurrentView,
      },
    });

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

    // ✅ 지역 선택 후 지도 이동 완료 시 플래그 리셋 (사용자가 드래그할 수 있도록)
    // 지도 이동이 완료되면 약간의 지연 후 플래그 리셋
    setTimeout(() => {
      isProgrammaticMoveRef.current = false;
      console.log('📍 [지역 선택 완료] isProgrammaticMoveRef.current = false로 리셋');
    }, 1500); // 1.5초 후 리셋 (지도 이동 완료 대기)

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
      dispatchUI({ type: 'SET_UI', payload: { statusMessage: `'${targetRegion}' 주변 장소를 검색하는 중...`, error: null } });

      await fetchServices({
        region: targetRegion,
        categoryOverride: categoryType,
      });
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      dispatchUI({ type: 'SET_UI', payload: { error: `지역 검색에 실패했습니다: ${message}`, statusMessage: '' } });
    }
  }, [selectedSido, selectedSigungu, selectedEupmyeondong, categoryType, fetchServices, keyword]);

  const handleAddressSearch = useCallback(async () => {
    if (!addressQuery.trim()) {
      return;
    }

    try {
      dispatchUI({ type: 'SET_UI', payload: { statusMessage: '주소를 찾는 중...', error: null } });

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
        dispatchRegion({
          type: 'SET_SIDO',
          payload: foundSido,
        });
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
      dispatchUI({ type: 'SET_ERROR', payload: `주소 검색에 실패했습니다: ${message}` });
      dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '' });
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
      // 읍면동 선택 시: 시도/시군구 기반 검색 (읍면동은 프론트엔드 필터링만 사용)
      if (selectedEupmyeondong) {
        console.log('📍 [지역 선택] 읍면동 선택 - 시도/시군구 기반 검색 (읍면동은 프론트엔드 필터링)');
        // 지도 위치 업데이트 (읍면동 좌표는 사용하지 않음)
        updateMapLocation(selectedSido, selectedSigungu, selectedEupmyeondong).catch(err => {
          console.warn('지도 위치 업데이트 실패:', err);
        });

        // 시도/시군구 기반 검색 (읍면동은 프론트엔드에서 필터링)
        fetchServices({
          region: [selectedSido, selectedSigungu].filter(Boolean).join(' ') || undefined,
          categoryOverride: effectiveCategoryType,
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
          // 현재 데이터 범위 밖: 백엔드 지역 기반 검색 (시도/시군구)
          console.log('🌐 [지역 선택] 위치 기반 데이터 범위 밖 - 백엔드 시도/시군구 기반 검색');
          fetchServices({
            region: [selectedSido, selectedSigungu, selectedEupmyeondong].filter(Boolean).join(' '),
            categoryOverride: effectiveCategoryType,
          });
        }
      } else {
        // 초기 로드가 전체 조회이거나 데이터가 없으면 백엔드 지역 기반 검색
        console.log('🌐 [지역 선택] 백엔드 시도/시군구 기반 검색');
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
    dispatchUI({ type: 'SET_SELECTED_SERVICE', payload: service });

    // 서비스 위치로 지도 이동
    if (service.latitude && service.longitude) {
      // 이전 타이머 취소 (API 재조회 방지)
      if (mapIdleTimeoutRef.current) {
        clearTimeout(mapIdleTimeoutRef.current);
        mapIdleTimeoutRef.current = null;
      }

      // "이 지역 검색" 버튼 숨기기
      setShowSearchButton(false);
      setPendingSearchLocation(null);

      // 프로그래매틱 이동 플래그 설정 (API 재조회 방지)
      isProgrammaticMoveRef.current = true;

      // 지도 중심만 이동 (줌 레벨은 유지)
      setMapCenter({ lat: service.latitude, lng: service.longitude });

      // 마커-리스트 동기화: 리스트에서 해당 항목 스크롤 및 하이라이트
      setTimeout(() => {
        const serviceElement = document.querySelector(`[data-service-idx="${service.idx || service.externalId}"]`);
        if (serviceElement) {
          serviceElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
          // 하이라이트 효과 (CSS로 처리)
        }
      }, 100);

      // 약간의 지연 후 플래그 리셋 (지도 이동 완료 후)
      setTimeout(() => {
        isProgrammaticMoveRef.current = false;
      }, 1000); // 1초 후 리셋
    }
  }, []);

  // 마커 클릭 핸들러
  const handleMarkerClick = useCallback((service) => {
    handleServiceSelect(service);
  }, [handleServiceSelect]);

  // AI 추천 버튼 클릭 핸들러
  const handleRecommendClick = useCallback(async () => {
    if (!user) {
      alert('AI 추천을 이용하려면 로그인이 필요합니다.');
      return;
    }
    if (loadingRecommend) return;

    const effectiveCategory = categoryType !== CATEGORY_DEFAULT && categoryType !== CATEGORY_CUSTOM
      ? categoryType
      : (selectedKeywordCategory || undefined);
    const effectiveKeyword = keyword?.trim() || undefined;

    const buildParams = () => {
      if (effectiveKeyword) {
        return { keyword: effectiveKeyword, category: effectiveCategory };
      }
      const loc = userLocation || mapCenter;
      if (loc && typeof loc.lat === 'number' && typeof loc.lng === 'number') {
        return {
          latitude: loc.lat,
          longitude: loc.lng,
          radius: 5000,
          category: effectiveCategory,
        };
      }
      if (selectedSido || selectedSigungu || selectedEupmyeondong) {
        return {
          sido: selectedSido || undefined,
          sigungu: selectedSigungu || undefined,
          eupmyeondong: selectedEupmyeondong || undefined,
          category: effectiveCategory,
        };
      }
      return null;
    };

    const params = buildParams();
    if (!params) {
      alert('AI 추천을 받으려면 먼저 지역을 선택하거나 키워드로 검색해주세요.');
      return;
    }

    setLoadingRecommend(true);
    dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: 'AI가 추천 장소를 분석하는 중...' });
    try {
      const response = await locationServiceApi.recommendPlaces(params);
      const recommended = response.data?.services || [];
      setAllServices(recommended);
      setServices(recommended);
      dispatchRegion({ type: 'RESET_REGION' });
      dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: `AI가 추천한 ${recommended.length}개의 장소입니다.` });
    } catch (err) {
      const msg = err.response?.data?.error || err.message || 'AI 추천을 불러오지 못했습니다.';
      dispatchUI({ type: 'SET_ERROR', payload: msg });
    } finally {
      setLoadingRecommend(false);
    }
  }, [
    user,
    loadingRecommend,
    categoryType,
    selectedKeywordCategory,
    keyword,
    userLocation,
    mapCenter,
    selectedSido,
    selectedSigungu,
    selectedEupmyeondong,
  ]);

  // 지도 드래그 시작 핸들러 - 사용자가 지도를 드래그하기 시작할 때 플래그 리셋
  const handleMapDragStart = useCallback(() => {
    // 사용자가 지도를 드래그하기 시작하면 프로그래매틱 이동 플래그 리셋
    isProgrammaticMoveRef.current = false;
  }, []);

  // 지도 이동/확대축소 시 처리 - "지도는 상태를 바꾸지 않는다" 원칙 적용
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
      // ✅ MapContainer에서 전달한 isManualOperation 플래그를 우선 사용
      // isManualOperation이 true면 사용자가 직접 드래그한 것
      const isUserDrag = mapInfo.isManualOperation === true;

      // 사용자가 직접 드래그한 경우에만 버튼 표시
      if (isUserDrag) {
        // 사용자가 직접 드래그한 경우 플래그 리셋
        isProgrammaticMoveRef.current = false;

        // ✅ 지도 중심만 업데이트 (레벨은 유지 - 사용자가 설정한 줌 레벨 유지)
        setMapCenter(newCenter);

        // "이 지역 검색" 버튼 표시 (상태 변경 의사만 표시)
        setPendingSearchLocation(newCenter);
        setShowSearchButton(true);
      } else if (!isProgrammaticMoveRef.current) {
        // 프로그래매틱 이동이 아니지만 isManualOperation이 false인 경우
        // (예: 지도 로드 완료 후 자동으로 발생하는 idle 이벤트)
        // 이 경우는 버튼을 표시하지 않음
        setMapCenter(newCenter);
      }
      // 프로그래매틱 이동이면 아무것도 하지 않음
    }
  }, [mapCenter]);

  // "이 지역 검색" 버튼 클릭 핸들러 (UserTriggeredSearch)
  // 지도 중심 좌표 → 역지오코딩 → 시도/시군구 추출 → 시도/시군구 기반 검색
  const handleSearchButtonClick = useCallback(async () => {
    if (!pendingSearchLocation) {
      return;
    }

    const effectiveCategoryType = categoryType !== CATEGORY_DEFAULT && categoryType !== CATEGORY_CUSTOM
      ? categoryType
      : undefined;

    // ✅ 지역 선택 상태 초기화 (지도 이동 후 검색 시 지역 선택 해제)
    dispatchRegion({ type: 'RESET_REGION' });
    setCurrentMapView('sido');

    // ✅ 지도 중심 좌표를 역지오코딩하여 시도/시군구 추출
    dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '지역 정보를 가져오는 중...' });

    try {
      const addressData = await geocodingApi.coordinatesToAddress(
        pendingSearchLocation.lat,
        pendingSearchLocation.lng
      );

      let sido = null;
      let sigungu = null;

      if (addressData && addressData.success !== false && addressData.address) {
        sido = extractSidoFromAddress(addressData.address);
        sigungu = extractSigunguFromAddress(addressData.address, sido);
      } else {
        // 역지오코딩 실패 시 위치 기반 반경 검색으로 fallback
        console.warn('⚠️ [이 지역 검색] 역지오코딩 실패 - 위치 기반 반경 검색으로 fallback');
        await fetchServices({
          latitude: pendingSearchLocation.lat,
          longitude: pendingSearchLocation.lng,
          radius: 5000, // 5km 반경
          categoryOverride: effectiveCategoryType,
        });
        setShowSearchButton(false);
        setPendingSearchLocation(null);
        return;
      }

      // ✅ 지도 레벨을 5km 반경에 맞게 조정
      const appropriateLevel = calculateMapLevelFromRadius(5); // 5km
      setMapLevel(appropriateLevel);

      // 시도/시군구 기반 검색
      await fetchServices({
        region: [sido, sigungu].filter(Boolean).join(' ') || undefined,
        categoryOverride: effectiveCategoryType,
      });
    } catch (error) {
      console.error('❌ [이 지역 검색] 역지오코딩 실패:', error);
      // 역지오코딩 실패 시 위치 기반 반경 검색으로 fallback
      console.warn('⚠️ [이 지역 검색] 역지오코딩 실패 - 위치 기반 반경 검색으로 fallback');
      try {
        await fetchServices({
          latitude: pendingSearchLocation.lat,
          longitude: pendingSearchLocation.lng,
          radius: 5000, // 5km 반경
          categoryOverride: effectiveCategoryType,
        });
        dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '내 주변 5km 이내 장소를 검색했습니다.' });
      } catch (fetchError) {
        console.error('❌ [이 지역 검색] 위치 기반 검색도 실패:', fetchError);
        dispatchUI({ type: 'SET_ERROR', payload: '장소 검색에 실패했습니다.' });
        dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '' });
      }
    }

    // 버튼 숨기기 및 대기 위치 초기화
    setShowSearchButton(false);
    setPendingSearchLocation(null);
  }, [pendingSearchLocation, categoryType, fetchServices]);

  const handleRecenterToUser = useCallback(() => {
    if (!userLocation) {
      return;
    }

    // 내 위치로 지도 중심 이동
    if (userLocation.lat && userLocation.lng) {
      // 프로그래매틱 이동 플래그 설정 (API 자동 재조회 방지)
      isProgrammaticMoveRef.current = true;

      setMapCenter({
        lat: userLocation.lat,
        lng: userLocation.lng
      });

      // 내 위치를 잘 볼 수 있도록 줌 레벨 조정 (2km 반경 정도)
      const zoomLevel = calculateMapLevelFromRadius(2);
      setMapLevel(zoomLevel);

      dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '내 위치로 이동했습니다.' });

      // 지도 이동 후 플래그 리셋
      setTimeout(() => {
        isProgrammaticMoveRef.current = false;
      }, 1000);
    }
  }, [userLocation]);

  return (
    <Container>
      {selectedService && (
        <ServiceDetailPanel onClick={(e) => {
          if (e.target === e.currentTarget) {
            dispatchUI({ type: 'SET_UI', payload: { selectedService: null, showDirections: false } });
          }
        }}>
          <DetailContent onClick={(e) => e.stopPropagation()}>
            <CloseButton onClick={() => {
              // 길찾기 화면이 열려있으면 길찾기만 닫기, 아니면 상세페이지 전체 닫기
              if (showDirections) {
                dispatchUI({ type: 'SET_SHOW_DIRECTIONS', payload: false });
              } else {
                dispatchUI({ type: 'SET_UI', payload: { selectedService: null, showDirections: false } });
              }
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
              {!showDirections && (
                <>
                  <ActionSectionTitle>리뷰</ActionSectionTitle>
                  {loadingReviews ? (
                    <div style={{ padding: '2rem', textAlign: 'center', color: '#666' }}>리뷰를 불러오는 중...</div>
                  ) : (
                    <>
                      {user && !myReview && (
                        <ActionButtons>
                          <ActionButton onClick={handleOpenReviewModal} primary>
                            ✍️ 리뷰 작성하기
                          </ActionButton>
                        </ActionButtons>
                      )}
                      {reviews.length === 0 ? (
                        <div style={{ padding: '2rem', textAlign: 'center', color: '#999' }}>
                          아직 작성된 리뷰가 없습니다.
                        </div>
                      ) : (
                        <ReviewsList>
                          {reviews.map((review) => (
                            <ReviewItem key={review.idx}>
                              <ReviewHeader>
                                <ReviewRating>
                                  {'⭐'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}
                                </ReviewRating>
                                <ReviewDate>
                                  {new Date(review.createdAt).toLocaleDateString('ko-KR')}
                                </ReviewDate>
                                {user?.idx === review.userIdx && (
                                  <ReviewActions>
                                    <ReviewActionButton onClick={() => handleEditReview(review)}>
                                      수정
                                    </ReviewActionButton>
                                    <ReviewActionButton onClick={() => handleDeleteReview(review.idx)} danger>
                                      삭제
                                    </ReviewActionButton>
                                  </ReviewActions>
                                )}
                              </ReviewHeader>
                              {review.comment && (
                                <ReviewComment>{review.comment}</ReviewComment>
                              )}
                            </ReviewItem>
                          ))}
                        </ReviewsList>
                      )}
                    </>
                  )}
                </>
              )}
              {showDirections && selectedService.latitude && selectedService.longitude ? (
                <DirectionsContainer>
                  <DirectionsHeader>
                    <DirectionsTitle>길찾기</DirectionsTitle>
                  </DirectionsHeader>
                  <DirectionsInfo>
                    <div style={{ marginBottom: '1rem' }}>
                      <strong>도착지:</strong> {selectedService.name || selectedService.address}
                    </div>
                    {userLocation && (
                      <div style={{ marginBottom: '1rem', padding: '0.75rem', background: 'rgba(3, 199, 90, 0.1)', borderRadius: '6px' }}>
                        <strong>출발지:</strong> {startLocationAddress || userLocationAddress || '현재 위치'}
                      </div>
                    )}
                    <DirectionsLink
                      href={`https://map.naver.com/p/search/${encodeURIComponent(selectedService.name || selectedService.address || '')}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={async (e) => {
                        if (userLocation && !userLocationAddress && !startLocationAddress) {
                          try {
                            console.log('📍 출발지 좌표를 주소로 변환 중...', { lat: userLocation.lat, lng: userLocation.lng });
                            const addressData = await geocodingApi.coordinatesToAddress(
                              userLocation.lat,
                              userLocation.lng
                            );
                            console.log('📍 출발지 주소 변환 API 응답:', addressData);

                            if (addressData && addressData.success !== false && addressData.address) {
                              setStartLocationAddress(addressData.address);
                              console.log('✅ 출발지 주소 변환 성공:', addressData.address);
                            } else {
                              console.warn('⚠️ 출발지 주소 변환 실패:', addressData?.message || addressData?.error);
                            }
                          } catch (error) {
                            console.error('❌ 출발지 주소 변환 실패:', error);
                            console.error('❌ 에러 상세:', error.response?.data || error.message);
                          }
                        }

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
                    {userLocation && (startLocationAddress || userLocationAddress) && (
                      <>
                        <br />
                        <br />
                        <strong>출발지:</strong> {startLocationAddress || userLocationAddress}
                        <br />
                        네이버맵에서 이 주소를 검색하거나 "현재 위치"를 선택하세요.
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
                            try {
                              const convertDurationToMinutes = (duration) => {
                                if (!duration) return null;
                                if (duration > 1000) {
                                  return Math.round(duration / 1000 / 60);
                                } else {
                                  return Math.round(duration / 60);
                                }
                              };

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

                              if (route && route.traoptimal && Array.isArray(route.traoptimal) && route.traoptimal.length > 0) {
                                const summary = route.traoptimal[0].summary;
                                if (summary && summary.duration) {
                                  console.log('📊 duration 값 (traoptimal):', summary.duration, '타입:', typeof summary.duration);
                                  durationMinutes = convertDurationToMinutes(summary.duration);
                                }
                              }

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
                        onClick={async () => {
                          dispatchUI({ type: 'SET_SHOW_DIRECTIONS', payload: true });
                          if (userLocation && !userLocationAddress && !startLocationAddress) {
                            try {
                              console.log('📍 출발지 좌표를 주소로 변환 중...', { lat: userLocation.lat, lng: userLocation.lng });
                              const addressData = await geocodingApi.coordinatesToAddress(
                                userLocation.lat,
                                userLocation.lng
                              );
                              console.log('📍 출발지 주소 변환 API 응답:', addressData);

                              if (addressData && addressData.success !== false && addressData.address) {
                                setStartLocationAddress(addressData.address);
                                console.log('✅ 출발지 주소 변환 성공:', addressData.address);
                              } else {
                                console.warn('⚠️ 출발지 주소 변환 실패:', addressData?.message || addressData?.error);
                              }
                            } catch (error) {
                              console.error('❌ 출발지 주소 변환 실패:', error);
                              console.error('❌ 에러 상세:', error.response?.data || error.message);
                            }
                          }
                        }}
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
                          dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '주소가 클립보드에 복사되었습니다.' });
                          setTimeout(() => dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '' }), 2000);
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
                          dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '네이버맵 링크가 클립보드에 복사되었습니다.' });
                          setTimeout(() => dispatchUI({ type: 'SET_STATUS_MESSAGE', payload: '' }), 2000);
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
      <Header>
        <HeaderTop>
          <Title>지도에서 반려동물 서비스 찾기</Title>
          <HeaderActions>
            <SearchModeTabs>
              <SearchModeButton
                type="button"
                active={searchMode === 'keyword'}
                onClick={() => {
                  dispatchSearch({ type: 'SET_SEARCH_MODE', payload: 'keyword' });
                  dispatchUI({ type: 'SET_SHOW_KEYWORD_CONTROLS', payload: !showKeywordControls });
                  // 다른 모드의 리스트는 닫기
                  if (showRegionControls) {
                    dispatchUI({ type: 'SET_SHOW_REGION_CONTROLS', payload: false });
                  }
                }}
              >
                키워드 검색
              </SearchModeButton>
              <SearchModeButton
                type="button"
                active={searchMode === 'region'}
                onClick={() => {
                  dispatchSearch({ type: 'SET_SEARCH_MODE', payload: 'region' });
                  dispatchUI({ type: 'SET_SHOW_REGION_CONTROLS', payload: !showRegionControls });
                  // 다른 모드의 리스트는 닫기
                  if (showKeywordControls) {
                    dispatchUI({ type: 'SET_SHOW_KEYWORD_CONTROLS', payload: false });
                  }
                }}
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
                  dispatchRegion({ type: 'RESET_REGION' });
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

        <SearchControls $isOpen={showKeywordControls && searchMode === 'keyword'}>
          <SearchBar onSubmit={handleKeywordSubmit}>
            <SearchInput
              type="text"
              placeholder="키워드 검색 (예: 동물병원, 카페, 호텔 등)"
              value={keyword}
              onChange={(e) => dispatchSearch({ type: 'SET_KEYWORD', payload: e.target.value })}
              onKeyPress={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleKeywordSubmit(e);
                }
              }}
            />
            <SearchButton type="submit">검색</SearchButton>
          </SearchBar>
          <RegionButtonGrid>
            {KEYWORD_CATEGORIES.map((cat) => (
              <RegionButton
                key={cat.value}
                onClick={() => {
                  const categoryValue = cat.value;
                  dispatchSearch({ type: 'SET_KEYWORD_CATEGORY', payload: categoryValue });
                  dispatchSearch({ type: 'SET_KEYWORD', payload: categoryValue });
                  if (categoryValue) {
                    // 카테고리 선택 시: 백엔드 재요청 (백엔드에서 카테고리 필터링)
                    dispatchSearch({ type: 'SET_CATEGORY_TYPE', payload: CATEGORY_CUSTOM });
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
                    dispatchSearch({ type: 'SET_CATEGORY_TYPE', payload: CATEGORY_DEFAULT });
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
        <RegionControls $isOpen={showRegionControls && searchMode === 'region'}>
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
      </Header>

      {statusMessage && (
        <StatusBanner>{statusMessage}</StatusBanner>
      )}

      {error && (
        <ErrorBanner>
          {error}
          <button onClick={() => dispatchUI({ type: 'SET_ERROR', payload: null })}>닫기</button>
        </ErrorBanner>
      )}

      <MapArea>
        {mapCenter && (
          <MapSection>
            {/* "이 지역 검색" 버튼 */}
            {showSearchButton && pendingSearchLocation && (
              <SearchAreaButton
                onClick={handleSearchButtonClick}
                title={`${pendingSearchLocation.lat.toFixed(6)}, ${pendingSearchLocation.lng.toFixed(6)} 위치 검색`}
              >
                🔍 이 지역 검색
              </SearchAreaButton>
            )}
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
              onMapDragStart={handleMapDragStart}
              selectedService={selectedService ? {
                idx: selectedService.idx || selectedService.externalId,
                externalId: selectedService.externalId,
                latitude: selectedService.latitude,
                longitude: selectedService.longitude,
                longitude: selectedService.longitude,
              } : null}
              hoveredService={hoveredService}
            />
          </MapSection>
        )}

        <ServiceListPanel>
          <ServiceListHeader>
            <ServiceListTitle>
              {userLocation ? '내 주변 장소' : '전체 장소'} ({servicesWithDisplay.length})
            </ServiceListTitle>
            <RecommendButton
              type="button"
              onClick={handleRecommendClick}
              disabled={loadingRecommend || !user}
              title={!user ? '로그인 후 이용 가능' : 'AI가 반려동물과 함께 가기 좋은 장소를 추천해드립니다'}
            >
              {loadingRecommend ? '⏳ 분석 중...' : '✨ AI 추천'}
            </RecommendButton>
          </ServiceListHeader>
          <ServiceListContent>
            {servicesWithDisplay.length === 0 ? (
              <EmptyStateContainer>
                <EmptyStateIcon>📍</EmptyStateIcon>
                <EmptyStateTitle>이 지역에 표시할 장소가 없습니다</EmptyStateTitle>
                <EmptyStateMessage>
                  다른 지역을 검색하거나 카테고리를 변경해보세요.
                </EmptyStateMessage>
                <EmptyStateActions>
                  <EmptyStateButton onClick={async () => {
                    dispatchRegion({ type: 'RESET_REGION' });
                    setCurrentMapView('nation');
                    await fetchServices({
                      isInitialLoad: true,
                      categoryOverride: categoryType,
                    });
                  }}>
                    전국 보기
                  </EmptyStateButton>
                </EmptyStateActions>
              </EmptyStateContainer>
            ) : (
              servicesWithDisplay.map((service) => (
                <ServiceListItem
                  key={service.key}
                  data-service-idx={service.idx || service.externalId}
                  isSelected={selectedService?.key === service.key}
                  onClick={() => handleServiceSelect(service)}
                  onMouseEnter={() => dispatchUI({ type: 'SET_HOVERED_SERVICE', payload: service })}
                  onMouseLeave={() => dispatchUI({ type: 'SET_HOVERED_SERVICE', payload: null })}
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
                  {service.recommendationReason && (
                    <ServiceRecommendReason>💡 {service.recommendationReason}</ServiceRecommendReason>
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
      </MapArea>

      {/* 리뷰 작성/수정 모달 */}
      {showReviewModal && (
        <ReviewModal onClick={() => setShowReviewModal(false)}>
          <ReviewModalContent onClick={(e) => e.stopPropagation()}>
            <ReviewModalHeader>
              <ReviewModalTitle>{editingReview ? '리뷰 수정' : '리뷰 작성'}</ReviewModalTitle>
              <ReviewModalClose onClick={() => setShowReviewModal(false)}>✕</ReviewModalClose>
            </ReviewModalHeader>
            <ReviewModalBody>
              <ReviewRatingSection>
                <ReviewLabel>평점</ReviewLabel>
                <StarRating>
                  {[1, 2, 3, 4, 5].map((star) => (
                    <StarButton
                      key={star}
                      type="button"
                      onClick={() => setReviewRating(star)}
                      active={star <= reviewRating}
                    >
                      ⭐
                    </StarButton>
                  ))}
                  <RatingText>{reviewRating}점</RatingText>
                </StarRating>
              </ReviewRatingSection>
              <ReviewCommentSection>
                <ReviewLabel>리뷰 내용</ReviewLabel>
                <ReviewTextarea
                  value={reviewComment}
                  onChange={(e) => setReviewComment(e.target.value)}
                  placeholder="서비스에 대한 리뷰를 작성해주세요..."
                  rows={5}
                />
              </ReviewCommentSection>
            </ReviewModalBody>
            <ReviewModalFooter>
              <ReviewCancelButton onClick={() => setShowReviewModal(false)}>
                취소
              </ReviewCancelButton>
              <ReviewSubmitButton onClick={handleSubmitReview} disabled={submittingReview || !reviewComment.trim()}>
                {submittingReview ? '작성 중...' : editingReview ? '수정하기' : '작성하기'}
              </ReviewSubmitButton>
            </ReviewModalFooter>
          </ReviewModalContent>
        </ReviewModal>
      )}
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
  /* overflow: hidden; -> 제거: Header의 드롭다운이 잘리지 않도록 (하지만 Header가 z-index로 위에 있으면 괜찮음) */
  position: relative;
`;

const Header = styled.div`
  padding: 0.75rem 1.5rem;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  position: relative; /* 드롭다운 기준점 */
  z-index: 2000; /* 지도보다 위에 위치 */
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
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
  font-size: 1.25rem;
  font-weight: 700;
`;

const SearchControls = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== '$isOpen',
})`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  
  /* 오버레이 스타일 적용 */
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
  z-index: 1900;
  padding: ${props => props.$isOpen ? '1rem 1.5rem' : '0 1.5rem'};
  
  /* 애니메이션 */
  max-height: ${props => props.$isOpen ? '400px' : '0'};
  opacity: ${props => props.$isOpen ? '1' : '0'};
  pointer-events: ${props => props.$isOpen ? 'auto' : 'none'}; /* 닫혔을 때 클릭 방지 */
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
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

const RegionControls = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== '$isOpen',
})`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  
  /* 오버레이 스타일 적용 */
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
  z-index: 1900;
  padding: ${props => props.$isOpen ? '1rem 1.5rem' : '0 1.5rem'};
  
  /* 애니메이션 */
  max-height: ${props => props.$isOpen ? '500px' : '0'}; /* 넉넉하게 공간 확보 */
  opacity: ${props => props.$isOpen ? '1' : '0'};
  pointer-events: ${props => props.$isOpen ? 'auto' : 'none'};
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
`;

const RegionButtonGrid = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  width: 100%;
  max-height: 300px; /* 오버레이이므로 높이를 넉넉하게 줌 */
  overflow-y: auto;
  padding: 0.5rem 0; /* 내부 패딩은 줄임 */
  position: relative;
  z-index: 1000;
  pointer-events: auto;
  
  /* 스크롤바 스타일링 */
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: ${props => props.theme.colors.background};
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: ${props => props.theme.colors.border};
    border-radius: 3px;
    &:hover {
      background: ${props => props.theme.colors.primary}80;
    }
  }
`;

const RegionButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 0.4rem 0.8rem;
  border: 1px solid ${props => props.active ? props.theme.colors.primary : props.theme.colors.border};
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: ${props => props.active ? 600 : 500};
  cursor: pointer;
  background: ${props => props.active
    ? `linear-gradient(135deg, ${props.theme.colors.primary} 0%, ${props.theme.colors.primary}dd 100%)`
    : props.theme.colors.surface};
  color: ${props => props.active ? 'white' : props.theme.colors.text};
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  position: relative;
  z-index: 1000;
  pointer-events: auto;
  box-shadow: ${props => props.active
    ? `0 4px 12px ${props.theme.colors.primary}40, 0 2px 4px ${props.theme.colors.primary}20`
    : '0 2px 4px rgba(0, 0, 0, 0.05)'};
  
  /* 호버 효과 */
  &:hover {
    background: ${props => props.active
    ? `linear-gradient(135deg, ${props.theme.colors.primary}dd 0%, ${props.theme.colors.primary} 100%)`
    : `linear-gradient(135deg, ${props.theme.colors.primary}15 0%, ${props.theme.colors.primary}25 100%)`};
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.active ? 'white' : props.theme.colors.primary};
    transform: translateY(-2px);
    box-shadow: ${props => props.active
    ? `0 6px 16px ${props.theme.colors.primary}50, 0 4px 8px ${props.theme.colors.primary}30`
    : `0 4px 12px ${props.theme.colors.primary}25, 0 2px 4px ${props.theme.colors.primary}15`};
  }

  /* 활성 상태 강조 */
  ${props => props.active && `
    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      border-radius: 24px;
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0) 100%);
      pointer-events: none;
    }
  `}

  &:active {
    transform: translateY(0px);
    box-shadow: ${props => props.active
    ? `0 2px 6px ${props.theme.colors.primary}40`
    : '0 1px 2px rgba(0, 0, 0, 0.1)'};
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
  overflow: hidden; /* 지도는 overflow hidden 유지 */
  
  /* 버튼이 지도 위에 표시되도록 */
  & > button {
    position: absolute;
    z-index: 2000;
  }
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
`;

const ServiceListTitle = styled.h3`
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  flex: 1;
  min-width: 0;
`;

const RecommendButton = styled.button`
  flex-shrink: 0;
  padding: 0.4rem 0.75rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: ${props => props.theme.colors.primary};
  background: ${props => props.theme.colors.primary + '15'};
  border: 1px solid ${props => props.theme.colors.primary};
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;

  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primary + '25'};
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
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

const ServiceRecommendReason = styled.div`
  font-size: 0.85rem;
  color: ${props => props.theme.colors.primary};
  margin-bottom: 0.4rem;
  line-height: 1.4;
  font-style: italic;
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

const EmptyStateContainer = styled.div`
  padding: 3rem 1.5rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
`;

const EmptyStateIcon = styled.div`
  font-size: 3rem;
  margin-bottom: 0.5rem;
`;

const EmptyStateTitle = styled.h3`
  margin: 0;
  color: ${props => props.theme.colors.text};
  font-size: 1.2rem;
  font-weight: 600;
`;

const EmptyStateMessage = styled.p`
  margin: 0;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 0.95rem;
  line-height: 1.6;
`;

const EmptyStateActions = styled.div`
  display: flex;
  gap: 0.75rem;
  margin-top: 1rem;
  flex-wrap: wrap;
  justify-content: center;
`;

const EmptyStateButton = styled.button`
  padding: 0.75rem 1.5rem;
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
    transform: translateY(0);
  }
`;

const SearchAreaButton = styled.button`
  position: absolute;
  top: 1rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000; /* 지도 위에 표시되도록 높은 z-index */
  padding: 0.75rem 1.5rem;
  background: ${props => props.theme.colors.primary || '#03C75A'};
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.2s;
  pointer-events: auto; /* 클릭 가능하도록 */

  &:hover {
    background: ${props => props.theme.colors.primary ? props.theme.colors.primary + 'dd' : '#03C75Add'};
    transform: translateX(-50%) translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  }

  &:active {
    transform: translateX(-50%) translateY(0);
  }
`;

const ServiceDetailPanel = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 3000; /* Header(z-index: 2000)보다 위에 표시 */
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

// 리뷰 관련 스타일
const ReviewsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-height: 500px;
  overflow-y: auto;
  padding: 0.5rem 0;
`;

const ReviewItem = styled.div`
  padding: 1rem;
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
`;

const ReviewHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  flex-wrap: wrap;
  gap: 0.5rem;
`;

const ReviewRating = styled.div`
  font-size: 1rem;
  color: ${props => props.theme.colors.warning || '#f59e0b'};
`;

const ReviewDate = styled.div`
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
`;

const ReviewActions = styled.div`
  display: flex;
  gap: 0.5rem;
`;

const ReviewActionButton = styled.button`
  padding: 0.25rem 0.75rem;
  background: ${props => props.danger ? '#ef4444' : props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    opacity: 0.8;
    transform: translateY(-1px);
  }
`;

const ReviewComment = styled.div`
  color: ${props => props.theme.colors.text};
  font-size: 0.95rem;
  line-height: 1.6;
  white-space: pre-wrap;
`;

const ReviewModal = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
`;

const ReviewModalContent = styled.div`
  background: ${props => props.theme.colors.surface || '#ffffff'};
  border-radius: 12px;
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
`;

const ReviewModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const ReviewModalTitle = styled.h2`
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const ReviewModalClose = styled.button`
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const ReviewModalBody = styled.div`
  padding: 20px;
`;

const ReviewRatingSection = styled.div`
  margin-bottom: 20px;
`;

const ReviewCommentSection = styled.div`
  margin-bottom: 20px;
`;

const ReviewLabel = styled.label`
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const StarRating = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`;

const StarButton = styled.button`
  background: transparent;
  border: none;
  font-size: 28px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
  filter: ${({ active }) => active ? 'none' : 'grayscale(100%) opacity(0.3)'};
  transition: all 0.2s ease;

  &:hover {
    transform: scale(1.1);
  }
`;

const RatingText = styled.span`
  margin-left: 8px;
  font-size: 16px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const ReviewTextarea = styled.textarea`
  width: 100%;
  padding: 12px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  color: ${props => props.theme.colors.text};
  background: ${props => props.theme.colors.background};

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }
`;

const ReviewModalFooter = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px;
  border-top: 1px solid ${props => props.theme.colors.border};
`;

const ReviewCancelButton = styled.button`
  padding: 10px 20px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
  }
`;

const ReviewSubmitButton = styled.button`
  padding: 10px 20px;
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primaryDark};
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;
