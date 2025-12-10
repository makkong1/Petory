import React, { useState, useEffect, useCallback, useRef } from 'react';
import styled from 'styled-components';
import { meetupApi } from '../../api/meetupApi';
import MapContainer from '../LocationService/MapContainer';
import { useAuth } from '../../contexts/AuthContext';
import { geocodingApi } from '../../api/geocodingApi';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };
const DEFAULT_RADIUS = 5; // km

// zoom level에 따른 반경 계산 (대략적인 값)
const calculateRadiusFromZoom = (zoom) => {
  // 네이버맵 zoom level: 21 (최대 확대) ~ 1 (최대 축소)
  // zoom level이 높을수록 더 확대됨 (작은 범위)
  const zoomRadiusMap = {
    21: 0.1, 20: 0.2, 19: 0.5, 18: 1, 17: 2, 16: 3, 15: 5,
    14: 8, 13: 12, 12: 20, 11: 30, 10: 50, 9: 80, 8: 120,
    7: 200, 6: 300, 5: 500, 4: 800, 3: 1200, 2: 2000, 1: 5000
  };

  // 가장 가까운 zoom level 찾기
  const zoomLevel = Math.round(zoom);
  return zoomRadiusMap[zoomLevel] || 5;
};

// 반경 값을 레벨로 변환 (표시용)
// 레벨 1 (1km) ~ 레벨 5 (20km)
const radiusToLevel = (radiusKm) => {
  const radiusLevelMap = {
    1: 1,    // 레벨 1 = 1km
    3: 2,    // 레벨 2 = 3km
    5: 3,    // 레벨 3 = 5km
    10: 4,   // 레벨 4 = 10km
    20: 5,   // 레벨 5 = 20km
  };
  return radiusLevelMap[radiusKm] || 0;
};

// 반경에 따른 적절한 카카오맵 레벨 계산 (MapContainer에서 네이버맵 줌으로 변환됨)
// 카카오맵 레벨: 낮을수록 확대 (1=최대 확대, 14=최대 축소)
// 반환값은 카카오맵 레벨이며, MapContainer.mapLevelToZoom에서 네이버맵 줌으로 변환
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

const MeetupPage = () => {
  const { user } = useAuth();
  const [meetups, setMeetups] = useState([]);
  const [selectedMeetup, setSelectedMeetup] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [isParticipating, setIsParticipating] = useState(false);
  const [participationLoading, setParticipationLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [userLocation, setUserLocation] = useState(null);
  const [mapCenter, setMapCenter] = useState(null); // 사용자 위치를 가져올 때까지 null
  const [radius, setRadius] = useState(DEFAULT_RADIUS);
  // 반경에 맞게 초기 줌 레벨 설정
  const [mapLevel, setMapLevel] = useState(calculateMapLevelFromRadius(DEFAULT_RADIUS));
  // autoRadius 제거됨 - 항상 수동으로 거리 선택
  const [selectedLocation, setSelectedLocation] = useState(null); // 선택한 위치 정보
  const [selectedSido, setSelectedSido] = useState(''); // 선택한 시도
  const [selectedSigungu, setSelectedSigungu] = useState(''); // 선택한 시군구
  const [selectedEupmyeondong, setSelectedEupmyeondong] = useState(''); // 선택한 동
  const [currentView, setCurrentView] = useState('sido'); // 현재 화면: 'sido', 'sigungu', 'eupmyeondong'
  const [locationError, setLocationError] = useState(null);
  // 리스트는 항상 표시 (사용자가 명시적으로 숨기기 버튼을 누르지 않는 한)
  const [showList, setShowList] = useState(true);
  const showListRef = useRef(true); // ref로도 관리하여 안정성 확보
  const [showCreateForm, setShowCreateForm] = useState(false);
  // 모달 제거 - RegionControls 방식으로 변경
  const [showRegionControls, setShowRegionControls] = useState(false); // 지역 선택 UI 표시 여부
  const [availableSigungus, setAvailableSigungus] = useState([]); // 선택된 시도의 시군구 목록
  const [availableEupmyeondongs, setAvailableEupmyeondongs] = useState([]); // 선택된 시군구의 읍면동 목록
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    location: '',
    latitude: null,
    longitude: null,
    date: '',
    maxParticipants: 10,
  });
  const [formErrors, setFormErrors] = useState({});
  const [formLoading, setFormLoading] = useState(false);
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [selectedDate, setSelectedDate] = useState(null);
  const [selectedTime, setSelectedTime] = useState({ hour: '12', minute: '00' });
  const [datePickerPosition, setDatePickerPosition] = useState({ top: 0, left: 0 });
  const datePickerButtonRef = useRef(null);
  const createFormModalRef = useRef(null);
  const isProgrammaticMoveRef = useRef(false); // 프로그래매틱 이동인지 구분
  const isInitialLoadRef = useRef(true); // 초기 로드 여부

  // 날짜/시간 초기화
  useEffect(() => {
    if (formData.date) {
      const date = new Date(formData.date);
      setSelectedDate(date);
      setSelectedTime({
        hour: String(date.getHours()).padStart(2, '0'),
        minute: String(date.getMinutes()).padStart(2, '0'),
      });
    } else {
      // 기본값: 현재 시간 + 1시간
      const defaultDate = new Date();
      defaultDate.setHours(defaultDate.getHours() + 1, 0, 0, 0);
      setSelectedDate(defaultDate);
      setSelectedTime({
        hour: String(defaultDate.getHours()).padStart(2, '0'),
        minute: '00',
      });
    }
  }, []);

  // 달력 버튼 위치 계산 (모달 오른쪽에 배치)
  const handleDatePickerToggle = () => {
    if (!showDatePicker) {
      // 모달이 있으면 모달의 오른쪽 끝을 기준으로, 없으면 버튼 기준으로
      if (createFormModalRef.current) {
        const modalRect = createFormModalRef.current.getBoundingClientRect();
        const calendarWidth = 320;
        const gap = 16; // 모달과 달력 사이 간격

        setDatePickerPosition({
          top: modalRect.top + window.scrollY,
          left: modalRect.right + window.scrollX + gap,
        });
      } else if (datePickerButtonRef.current) {
        const rect = datePickerButtonRef.current.getBoundingClientRect();
        const calendarWidth = 320;
        const rightPosition = rect.right + window.scrollX - calendarWidth;

        setDatePickerPosition({
          top: rect.top + window.scrollY,
          left: Math.max(10, rightPosition),
        });
      }
    }
    setShowDatePicker(!showDatePicker);
  };

  // 달력 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (showDatePicker &&
        !event.target.closest('.date-picker-wrapper') &&
        !event.target.closest('.date-picker-dropdown')) {
        setShowDatePicker(false);
      }
    };

    if (showDatePicker) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => {
        document.removeEventListener('mousedown', handleClickOutside);
      };
    }
  }, [showDatePicker]);

  // 현재 위치 가져오기 함수
  const fetchUserLocation = useCallback(async () => {
    if (navigator.geolocation) {
      setLocationError(null);
      const options = {
        enableHighAccuracy: true, // 높은 정확도 사용
        timeout: 15000, // 15초 타임아웃 (더 길게)
        maximumAge: 60000, // 1분 이내 캐시된 위치 사용 가능
      };

      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const location = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          };
          setUserLocation(location);

          // 좌표를 주소로 변환하여 동 정보 가져오기 (선택적, 실패해도 계속 진행)
          // TODO: reverse geocoding API 추가 필요
          setSelectedLocation({
            address: '내 위치',
            lat: location.lat,
            lng: location.lng,
            bname: '내 위치',
          });

          // 내 위치 설정 및 초기화
          const initialRadius = 3; // 동 기준 기본 반경 3km
          const initialMapLevel = calculateMapLevelFromRadius(initialRadius);

          setUserLocation(location);
          setMapCenter(location); // 사용자 위치를 기본 중심점으로 설정
          setRadius(initialRadius);
          setMapLevel(initialMapLevel);
          setLocationError(null);

          // 프로그래매틱 이동 플래그 설정 (리스트는 자동으로 조회됨)
          isProgrammaticMoveRef.current = true;
        },
        (error) => {
          console.error('위치 정보 가져오기 실패:', error);
          let errorMessage = '위치 정보를 가져올 수 없습니다.';

          switch (error.code) {
            case error.PERMISSION_DENIED:
              errorMessage = '위치 권한이 거부되었습니다. 브라우저 설정에서 위치 권한을 허용해주세요.';
              alert(errorMessage);
              break;
            case error.POSITION_UNAVAILABLE:
              errorMessage = '위치 정보를 사용할 수 없습니다. GPS가 켜져 있는지 확인해주세요.';
              alert(errorMessage);
              break;
            case error.TIMEOUT:
              errorMessage = '위치 정보 요청 시간이 초과되었습니다. 다시 시도해주세요.';
              alert(errorMessage);
              break;
          }

          console.warn(errorMessage);
          setLocationError(errorMessage);
          // 위치 가져오기 실패 시 기본 위치 사용 (한 번만)
          setMapCenter(prev => prev || DEFAULT_CENTER);
        },
        options
      );
    } else {
      // Geolocation API를 지원하지 않는 경우 기본 위치 사용
      const errorMessage = 'Geolocation API를 지원하지 않는 브라우저입니다.';
      console.warn(errorMessage);
      setLocationError(errorMessage);
      setMapCenter(prev => prev || DEFAULT_CENTER);
    }
  }, []); // 의존성 배열 비우기 - 무한 루프 방지

  // 현재 위치 가져오기 (초기 마운트 시에만 실행)
  useEffect(() => {
    fetchUserLocation();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 빈 배열로 초기 마운트 시에만 실행

  // 모임 목록 조회
  const fetchMeetups = useCallback(async (filterSido = null, filterSigungu = null, filterEupmyeondong = null) => {
    if (!mapCenter || !mapCenter.lat || !mapCenter.lng) {
      return;
    }

    // 필터링할 지역 결정 (파라미터가 있으면 파라미터 사용, 없으면 현재 상태 사용)
    const targetFilterSido = filterSido !== null ? filterSido : selectedSido;
    const targetFilterSigungu = filterSigungu !== null ? filterSigungu : selectedSigungu;
    const targetFilterEupmyeondong = filterEupmyeondong !== null ? filterEupmyeondong : selectedEupmyeondong;

    setLoading(true);
    try {
      const response = await meetupApi.getNearbyMeetups(
        mapCenter.lat,
        mapCenter.lng,
        radius
      );
      const allMeetups = response.data.meetups || [];

      // 선택된 지역으로 필터링
      let filteredMeetups = allMeetups;
      if (targetFilterSido) {
        filteredMeetups = filteredMeetups.filter(meetup => {
          if (!meetup.location) return false;
          const locationParts = meetup.location.split(' ');
          if (locationParts.length < 1) return false;
          if (locationParts[0] !== targetFilterSido) return false;

          if (targetFilterSigungu) {
            if (locationParts.length < 2) return false;
            if (locationParts[1] !== targetFilterSigungu) return false;

            if (targetFilterEupmyeondong) {
              if (locationParts.length < 3) return false;
              if (locationParts[2] !== targetFilterEupmyeondong) return false;
            }
          }
          return true;
        });
      }

      setMeetups(filteredMeetups);

      // 시군구만 선택된 경우 읍면동 목록 추출
      if (targetFilterSigungu && !targetFilterEupmyeondong) {
        const eupmyeondongSet = new Set();
        for (const meetup of filteredMeetups) {
          if (meetup.location) {
            // location에서 읍면동 추출 시도 (예: "서울특별시 노원구 중계동" -> "중계동")
            const locationParts = meetup.location.split(' ');
            if (locationParts.length >= 3) {
              const eupmyeondong = locationParts[2];
              if (eupmyeondong && eupmyeondong.endsWith('동') || eupmyeondong.endsWith('면') || eupmyeondong.endsWith('읍')) {
                eupmyeondongSet.add(eupmyeondong);
              }
            }
          }
        }
        const extractedEupmyeondongs = Array.from(eupmyeondongSet).sort();
        // 추출된 목록이 있으면 사용, 없으면 EUPMYEONDONGS 상수 사용
        if (extractedEupmyeondongs.length > 0) {
          setAvailableEupmyeondongs(extractedEupmyeondongs);
        } else if (EUPMYEONDONGS[targetFilterSido] && EUPMYEONDONGS[targetFilterSido][targetFilterSigungu]) {
          setAvailableEupmyeondongs(EUPMYEONDONGS[targetFilterSido][targetFilterSigungu]);
        }
      }
    } catch (error) {
      console.error('모임 조회 실패:', error);
      const errorMessage = error.response?.data?.error || error.message || '모임을 불러오는데 실패했습니다.';
      console.error('에러 상세:', errorMessage);
      setMeetups([]); // 에러 시 빈 배열
    } finally {
      setLoading(false);
    }
  }, [mapCenter, radius, selectedSido, selectedSigungu, selectedEupmyeondong]);

  // 거리 계산 함수 (Haversine 공식)
  const calculateDistance = (lat1, lng1, lat2, lng2) => {
    const R = 6371; // 지구 반지름 (km)
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLng = (lng2 - lng1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
      Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  };


  // 지도 이동/확대축소 시 모임 재조회
  const handleMapIdle = useCallback((mapInfo) => {
    if (!mapInfo || !mapInfo.lat || !mapInfo.lng) {
      return;
    }

    const newCenter = {
      lat: mapInfo.lat,
      lng: mapInfo.lng,
    };

    // 자동 반경 기능 제거됨 - 사용자가 직접 거리를 선택해야 함

    // 위치가 실제로 변경되었을 때만 업데이트
    const isLocationChanged = !mapCenter ||
      Math.abs(mapCenter.lat - newCenter.lat) > 0.0001 ||
      Math.abs(mapCenter.lng - newCenter.lng) > 0.0001;

    if (isLocationChanged) {
      // 프로그래매틱 이동이 아니면 mapCenter 업데이트 (리스트 자동 조회됨)
      if (!isProgrammaticMoveRef.current) {
        setMapCenter(newCenter);
      } else {
        isProgrammaticMoveRef.current = false;
      }
    }
  }, [mapCenter, radius]);

  // mapCenter 또는 radius가 변경될 때 모임 자동 조회
  useEffect(() => {
    if (mapCenter && mapCenter.lat && mapCenter.lng) {
      // 초기 로드이거나 프로그래매틱 이동이 아닐 때만 조회
      if (isInitialLoadRef.current) {
        // 초기 로드 시에는 항상 조회
        isInitialLoadRef.current = false;
        fetchMeetups();
      } else if (!isProgrammaticMoveRef.current) {
        // 프로그래매틱 이동이 아닐 때만 조회 (사용자가 지도를 직접 조작한 경우)
        fetchMeetups();
      } else {
        // 프로그래매틱 이동이면 플래그만 리셋 (리스트 조회 안 함)
        isProgrammaticMoveRef.current = false;
      }
    }
  }, [mapCenter, radius, fetchMeetups]);

  // 참가자 목록 조회
  const fetchParticipants = async (meetupIdx) => {
    try {
      const response = await meetupApi.getParticipants(meetupIdx);
      setParticipants(response.data.participants || []);
    } catch (error) {
      console.error('참가자 목록 조회 실패:', error);
    }
  };

  // 참가 여부 확인
  const checkParticipation = async (meetupIdx) => {
    try {
      const response = await meetupApi.checkParticipation(meetupIdx);
      setIsParticipating(response.data.isParticipating || false);
    } catch (error) {
      console.error('참가 여부 확인 실패:', error);
      setIsParticipating(false);
    }
  };

  // 모임 참가
  const handleJoinMeetup = async () => {
    if (!selectedMeetup) return;

    setParticipationLoading(true);
    try {
      await meetupApi.joinMeetup(selectedMeetup.idx);
      setIsParticipating(true);
      // 참가자 목록과 모임 정보 새로고침
      await fetchParticipants(selectedMeetup.idx);
      // 모임 정보도 새로고침
      try {
        const response = await meetupApi.getMeetupById(selectedMeetup.idx);
        setSelectedMeetup(response.data.meetup);
      } catch (error) {
        console.error('모임 정보 갱신 실패:', error);
      }
      // 모임 목록도 새로고침
      await fetchMeetups();
      alert('모임에 참가했습니다!');
    } catch (error) {
      const errorMessage = error.response?.data?.error || '모임 참가에 실패했습니다.';
      alert(errorMessage);
    } finally {
      setParticipationLoading(false);
    }
  };

  // 모임 참가 취소
  const handleCancelParticipation = async () => {
    if (!selectedMeetup) return;

    if (!window.confirm('정말 모임 참가를 취소하시겠습니까?')) {
      return;
    }

    setParticipationLoading(true);
    try {
      await meetupApi.cancelParticipation(selectedMeetup.idx);
      setIsParticipating(false);
      // 참가자 목록과 모임 정보 새로고침
      await fetchParticipants(selectedMeetup.idx);
      // 모임 정보도 새로고침
      try {
        const response = await meetupApi.getMeetupById(selectedMeetup.idx);
        setSelectedMeetup(response.data.meetup);
      } catch (error) {
        console.error('모임 정보 갱신 실패:', error);
      }
      // 모임 목록도 새로고침
      await fetchMeetups();
      alert('모임 참가를 취소했습니다.');
    } catch (error) {
      const errorMessage = error.response?.data?.error || '모임 참가 취소에 실패했습니다.';
      alert(errorMessage);
    } finally {
      setParticipationLoading(false);
    }
  };

  // 모임 클릭 핸들러
  const handleMeetupClick = async (meetup) => {
    // 모임 위치로 지도 이동 (프로그래매틱 이동으로 표시하여 리스트 재조회 방지)
    if (meetup.latitude && meetup.longitude) {
      isProgrammaticMoveRef.current = true;
      setMapCenter({
        lat: meetup.latitude,
        lng: meetup.longitude,
      });
    }

    setSelectedMeetup(meetup);
    await fetchParticipants(meetup.idx);
    await checkParticipation(meetup.idx);
  };

  // 마커 클릭 핸들러
  const handleMarkerClick = async (service) => {
    // 실종신고와 모임 구분
    if (service.type === 'missingPet') {
      // 실종신고 클릭 시 상세 정보 표시 (추후 구현 가능)
      // 실종신고 상세 페이지로 이동하거나 모달 표시
      window.open(`/missing-pets/${service.idx}`, '_blank');
    } else {
      // 모임 클릭
      await handleMeetupClick(service);
    }
  };

  // 날짜 포맷팅
  const formatDate = (dateString) => {
    if (!dateString) return '';
    // ISO 문자열을 로컬 시간으로 파싱 (타임존 문제 방지)
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return '';

    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    const day = date.getDate();
    const hour = date.getHours();
    const minute = date.getMinutes();

    const ampm = hour >= 12 ? '오후' : '오전';
    const displayHour = hour > 12 ? hour - 12 : (hour === 0 ? 12 : hour);

    return `${year}년 ${month}월 ${day}일 ${ampm} ${displayHour}:${String(minute).padStart(2, '0')}`;
  };


  // 시도/시군구/동 데이터 (LocationServiceMap에서 가져옴)
  const SIDOS = [
    '서울특별시', '부산광역시', '대구광역시', '인천광역시', '광주광역시', '대전광역시', '울산광역시',
    '세종특별자치시', '경기도', '강원특별자치도', '충청북도', '충청남도', '전북특별자치도', '전라남도',
    '경상북도', '경상남도', '제주특별자치도',
  ];

  const SIDO_CENTERS = {
    '서울특별시': { lat: 37.5665, lng: 126.9780 },
    '부산광역시': { lat: 35.1796, lng: 129.0756 },
    '대구광역시': { lat: 35.8714, lng: 128.6014 },
    '인천광역시': { lat: 37.4563, lng: 126.7052 },
    '광주광역시': { lat: 35.1595, lng: 126.8526 },
    '대전광역시': { lat: 36.3504, lng: 127.3845 },
    '울산광역시': { lat: 35.5384, lng: 129.3114 },
    '세종특별자치시': { lat: 36.4800, lng: 127.2890 },
    '경기도': { lat: 37.4138, lng: 127.5183 },
    '강원특별자치도': { lat: 37.8228, lng: 128.1555 },
    '충청북도': { lat: 36.8000, lng: 127.7000 },
    '충청남도': { lat: 36.5184, lng: 126.8000 },
    '전북특별자치도': { lat: 35.7175, lng: 127.1530 },
    '전라남도': { lat: 34.8679, lng: 126.9910 },
    '경상북도': { lat: 36.4919, lng: 128.8889 },
    '경상남도': { lat: 35.4606, lng: 128.2132 },
    '제주특별자치도': { lat: 33.4996, lng: 126.5312 },
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

  // 시군구별 동 목록
  const EUPMYEONDONGS = {
    '서울특별시': {
      '노원구': ['중계동', '상계동', '하계동', '공릉동', '월계동'],
      '강남구': ['역삼동', '개포동', '삼성동', '청담동', '논현동', '압구정동', '신사동', '도곡동', '대치동'],
      '강동구': ['천호동', '성내동', '암사동', '상일동', '길동', '둔촌동', '명일동', '고덕동'],
      '강북구': ['미아동', '수유동', '번동', '우이동', '인수동', '삼양동', '삼각산동'],
      '강서구': ['화곡동', '가양동', '등촌동', '염창동', '공항동', '방화동', '마곡동'],
      '관악구': ['신림동', '봉천동', '남현동', '서원동', '신원동', '서림동', '삼성동', '미성동'],
      '광진구': ['자양동', '구의동', '화양동', '군자동', '능동', '광장동'],
      '구로구': ['구로동', '가리봉동', '신도림동', '고척동', '개봉동', '오류동', '궁동', '온수동'],
      '금천구': ['가산동', '독산동', '시흥동', '범물동'],
      '도봉구': ['도봉동', '방학동', '쌍문동', '창동', '월계동'],
      '동대문구': ['용신동', '제기동', '전농동', '답십리동', '장안동', '청량리동', '회기동', '휘경동', '이문동'],
      '동작구': ['노량진동', '상도동', '흑석동', '사당동', '대방동', '신대방동'],
      '마포구': ['공덕동', '아현동', '도화동', '용강동', '대흥동', '염리동', '신수동', '서강동', '서교동', '합정동', '망원동', '상암동'],
      '서대문구': ['충현동', '천연동', '북아현동', '신촌동', '연희동', '홍제동', '홍은동', '불광동', '수색동'],
      '서초구': ['방배동', '양재동', '우면동', '원지동', '잠원동', '반포동', '서초동', '내곡동', '염곡동'],
      '성동구': ['왕십리동', '마장동', '사근동', '행당동', '응봉동', '금호동', '옥수동', '성수동', '송정동', '용답동'],
      '성북구': ['성북동', '삼선동', '동선동', '돈암동', '안암동', '보문동', '정릉동', '길음동', '종암동', '하월곡동', '상월곡동', '장위동', '석관동'],
      '송파구': ['잠실동', '신천동', '마천동', '거여동', '문정동', '장지동', '위례동', '가락동', '방이동', '오금동', '송파동', '석촌동', '삼전동', '올림픽동'],
      '양천구': ['목동', '신월동', '신정동', '오목교동', '염창동'],
      '영등포구': ['영등포동', '여의도동', '당산동', '도림동', '문래동', '양평동', '신길동', '대림동', '신당동', '구로동'],
      '용산구': ['남영동', '원효로동', '효창동', '용산동', '한강로동', '이촌동', '이태원동', '한남동', '서빙고동', '보광동'],
      '은평구': ['녹번동', '불광동', '갈현동', '구산동', '대조동', '응암동', '역촌동', '신사동', '증산동', '수색동'],
      '종로구': ['청와대', '효자동', '신교동', '궁정동', '와룡동', '무악동', '교남동', '평창동', '부암동', '삼청동', '가회동', '종로동', '이화동', '혜화동', '창신동', '숭인동'],
      '중구': ['소공동', '회현동', '명동', '필동', '장충동', '광희동', '을지로동', '신당동', '다산동', '약수동', '청구동', '신당동', '중림동', '만리동', '순화동', '의주로동', '중림동'],
      '중랑구': ['면목동', '상봉동', '중화동', '묵동', '망우동', '신내동'],
    },
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

  // 지도 위치 업데이트 함수
  const updateMapLocation = async (targetSido, targetSigungu, targetEupmyeondong) => {
    // 전국 선택 시 기본 위치로
    if (!targetSido || targetSido === '' || targetSido === '전국') {
      setSelectedLocation(null);
      setMapCenter(DEFAULT_CENTER);
      setRadius(DEFAULT_RADIUS);
      setMapLevel(calculateMapLevelFromRadius(DEFAULT_RADIUS));
      isProgrammaticMoveRef.current = true;
      return { center: DEFAULT_CENTER, radius: DEFAULT_RADIUS, mapLevel: calculateMapLevelFromRadius(DEFAULT_RADIUS) };
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
      const selectedRadius = 50;
      const selectedMapLevel = sidoZoomLevels[targetSido] || 4;
      setMapCenter({ lat: center.lat, lng: center.lng });
      setRadius(selectedRadius);
      setMapLevel(selectedMapLevel);
      setSelectedLocation({
        sido: targetSido,
        sigungu: '',
        eupmyeondong: '',
      });
      isProgrammaticMoveRef.current = true;
      return { center: { lat: center.lat, lng: center.lng }, radius: selectedRadius, mapLevel: selectedMapLevel };
    }

    // 시군구 또는 동 선택한 경우: geocoding API 사용
    let address = targetSido;
    if (targetSigungu) {
      address = `${targetSido} ${targetSigungu}`;
    }
    if (targetEupmyeondong && targetEupmyeondong !== '전체' && targetEupmyeondong.trim() !== '') {
      address = `${targetSido} ${targetSigungu} ${targetEupmyeondong}`;
    }

    try {
      const coordData = await geocodingApi.addressToCoordinates(address);
      if (coordData && coordData.success !== false && coordData.latitude && coordData.longitude) {
        let selectedRadius = 20;
        let selectedMapLevel;
        if (targetEupmyeondong && targetEupmyeondong !== '전체' && targetEupmyeondong.trim() !== '') {
          selectedRadius = 3;
          selectedMapLevel = calculateMapLevelFromRadius(selectedRadius);
        } else if (targetSigungu) {
          selectedRadius = 20;
          selectedMapLevel = calculateMapLevelFromRadius(selectedRadius);
        }
        setMapCenter({ lat: coordData.latitude, lng: coordData.longitude });
        setRadius(selectedRadius);
        setMapLevel(selectedMapLevel);
        setSelectedLocation({
          sido: targetSido,
          sigungu: targetSigungu || '',
          eupmyeondong: (targetEupmyeondong && targetEupmyeondong !== '전체' && targetEupmyeondong.trim() !== '') ? targetEupmyeondong : '',
        });
        isProgrammaticMoveRef.current = true;
        return { center: { lat: coordData.latitude, lng: coordData.longitude }, radius: selectedRadius, mapLevel: selectedMapLevel };
      } else {
        alert('위치를 찾을 수 없습니다. 다시 시도해주세요.');
        return null;
      }
    } catch (error) {
      console.error('위치 좌표 변환 실패:', error);
      alert('위치를 찾을 수 없습니다. 다시 시도해주세요.');
      return null;
    }
  };

  // 지역 선택 핸들러 (LocationServiceMap 방식)
  const handleRegionSelect = async (sidoOverride = null, sigunguOverride = null, eupmyeondongOverride = null, viewOverride = null) => {
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

    // 전국 선택 시 기본 위치로
    if (!targetSido || targetSido === '' || targetSido === '전국') {
      await updateMapLocation('', '', '');
      setAvailableSigungus([]);
      setAvailableEupmyeondongs([]);
      setShowRegionControls(false); // 전국 선택 시 RegionControls 닫기
      return;
    }

    // 지도 위치 업데이트
    const mapResult = await updateMapLocation(targetSido, targetSigungu, targetEupmyeondong);
    if (!mapResult) {
      return; // 위치 업데이트 실패
    }

    // 시도만 선택한 경우
    if (!targetSigungu) {
      // 시군구 목록 설정
      setAvailableSigungus(SIGUNGUS[targetSido] || []);
      setAvailableEupmyeondongs([]);
      // 지역 선택 시 모임 목록 새로 조회
      fetchMeetups(targetSido, null, null);
      return;
    }

    // 시군구 선택한 경우
    // 시군구 선택 시 RegionControls 닫기
    setShowRegionControls(false);

    // 지역 선택 시 모임 목록 새로 조회 (mapCenter 업데이트 후, 필터링할 지역을 명시적으로 전달)
    fetchMeetups(targetSido, targetSigungu || null, null);
  };



  // 주소 입력 시 자동으로 위도/경도 변환 (debounce)
  useEffect(() => {
    if (!formData.location || formData.location.trim().length < 3) {
      return;
    }

    const timeoutId = setTimeout(async () => {
      try {
        const coordData = await geocodingApi.addressToCoordinates(formData.location);
        if (coordData && coordData.success !== false && coordData.latitude && coordData.longitude) {
          setFormData(prev => ({
            ...prev,
            latitude: coordData.latitude,
            longitude: coordData.longitude,
          }));
          // 주소 변환 성공 시 에러 제거
          setFormErrors(prev => {
            const newErrors = { ...prev };
            delete newErrors.location;
            return newErrors;
          });
        }
      } catch (error) {
        console.error('주소 변환 실패:', error);
        // 에러는 조용히 처리 (사용자가 입력 중일 수 있음)
      }
    }, 1000); // 1초 debounce

    return () => clearTimeout(timeoutId);
  }, [formData.location]);

  // 폼 입력 핸들러
  const handleFormChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'maxParticipants' ? Number(value) : value,
    }));
    // 에러 제거
    if (formErrors[name]) {
      setFormErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  // 폼 검증
  const validateForm = () => {
    const errors = {};

    if (!formData.title.trim()) {
      errors.title = '모임 제목을 입력해주세요.';
    }

    if (!formData.location.trim()) {
      errors.location = '모임 장소를 입력해주세요.';
    }

    if (!formData.latitude || !formData.longitude) {
      errors.location = '모임 장소의 위도/경도를 설정해주세요. (주소 입력 후 자동 설정)';
    }

    if (!formData.date) {
      errors.date = '모임 일시를 선택해주세요.';
    } else {
      const selectedDate = new Date(formData.date);
      if (selectedDate < new Date()) {
        errors.date = '모임 일시는 현재 시간 이후여야 합니다.';
      }
    }

    if (!formData.maxParticipants || formData.maxParticipants < 1) {
      errors.maxParticipants = '최대 인원은 1명 이상이어야 합니다.';
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // 달력 날짜 생성
  // 달력 날짜 생성
  const getCalendarDays = (date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const startDate = new Date(firstDay);
    startDate.setDate(startDate.getDate() - startDate.getDay());

    const days = [];
    const currentDate = new Date(startDate);

    for (let i = 0; i < 42; i++) {
      days.push(new Date(currentDate));
      currentDate.setDate(currentDate.getDate() + 1);
    }

    return days;
  };

  // 날짜 선택 핸들러
  const handleDateSelect = (day) => {
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const selectedDay = new Date(day.getFullYear(), day.getMonth(), day.getDate());

    // 과거 날짜는 선택 불가
    if (selectedDay < today) {
      return;
    }

    // 선택한 날짜에 현재 선택된 시간 적용
    const hour = parseInt(selectedTime.hour) || 0;
    const minute = parseInt(selectedTime.minute) || 0;

    // 날짜만 사용 (시간은 0으로 초기화 후 다시 설정)
    const newDate = new Date(day.getFullYear(), day.getMonth(), day.getDate());
    newDate.setHours(hour, minute, 0, 0);

    // 오늘 날짜이고 과거 시간이면 현재 시간 + 1시간으로 설정
    if (selectedDay.getTime() === today.getTime() && newDate < now) {
      const futureDate = new Date(now);
      futureDate.setHours(futureDate.getHours() + 1, 0, 0, 0);
      setSelectedDate(futureDate);
      setSelectedTime({
        hour: String(futureDate.getHours()).padStart(2, '0'),
        minute: String(futureDate.getMinutes()).padStart(2, '0'),
      });
      // 로컬 시간 문자열 생성 (UTC 변환 방지)
      const localDateString = `${futureDate.getFullYear()}-${String(futureDate.getMonth() + 1).padStart(2, '0')}-${String(futureDate.getDate()).padStart(2, '0')}T${String(futureDate.getHours()).padStart(2, '0')}:${String(futureDate.getMinutes()).padStart(2, '0')}`;
      setFormData(prev => ({
        ...prev,
        date: localDateString,
      }));
    } else {
      // 날짜와 시간을 모두 설정
      setSelectedDate(newDate);
      setSelectedTime({
        hour: String(hour).padStart(2, '0'),
        minute: String(minute).padStart(2, '0'),
      });
      // 로컬 시간 문자열 생성 (UTC 변환 방지)
      const localDateString = `${newDate.getFullYear()}-${String(newDate.getMonth() + 1).padStart(2, '0')}-${String(newDate.getDate()).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
      setFormData(prev => ({
        ...prev,
        date: localDateString,
      }));
    }
  };

  // 시간 변경 핸들러
  const handleTimeChange = (type, value) => {
    // 현재 선택된 날짜 가져오기 (formData.date 또는 selectedDate)
    let baseDate = selectedDate;
    if (!baseDate && formData.date) {
      baseDate = new Date(formData.date);
    }
    if (!baseDate) {
      // 날짜가 없으면 오늘 + 1시간으로 설정
      const defaultDate = new Date();
      defaultDate.setHours(defaultDate.getHours() + 1, 0, 0, 0);
      setSelectedDate(defaultDate);
      setSelectedTime({
        hour: String(defaultDate.getHours()).padStart(2, '0'),
        minute: '00',
      });
      // 로컬 시간 문자열 생성 (UTC 변환 방지)
      const localDateString = `${defaultDate.getFullYear()}-${String(defaultDate.getMonth() + 1).padStart(2, '0')}-${String(defaultDate.getDate()).padStart(2, '0')}T${String(defaultDate.getHours()).padStart(2, '0')}:${String(defaultDate.getMinutes()).padStart(2, '0')}`;
      setFormData(prev => ({
        ...prev,
        date: localDateString,
      }));
      return;
    }

    // 날짜 부분만 사용 (시간은 새로 설정)
    const dateOnly = new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate());

    let hour = parseInt(selectedTime.hour) || 0;
    let minute = parseInt(selectedTime.minute) || 0;

    if (type === 'hour') {
      hour = Math.max(0, Math.min(23, parseInt(value) || 0));
    } else if (type === 'minute') {
      minute = Math.max(0, Math.min(59, parseInt(value) || 0));
    }

    // 날짜는 유지하고 시간만 변경
    const newDate = new Date(dateOnly);
    newDate.setHours(hour, minute, 0, 0);

    // 과거 시간 체크 (오늘 날짜인 경우에만)
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const selectedDay = new Date(dateOnly.getFullYear(), dateOnly.getMonth(), dateOnly.getDate());

    if (selectedDay.getTime() === today.getTime() && newDate < now) {
      // 오늘 날짜이고 과거 시간이면 현재 시간 + 1시간으로 설정
      const futureDate = new Date(now);
      futureDate.setHours(futureDate.getHours() + 1, 0, 0, 0);
      setSelectedDate(futureDate);
      setSelectedTime({
        hour: String(futureDate.getHours()).padStart(2, '0'),
        minute: String(futureDate.getMinutes()).padStart(2, '0'),
      });
      // 로컬 시간 문자열 생성 (UTC 변환 방지)
      const localDateString = `${futureDate.getFullYear()}-${String(futureDate.getMonth() + 1).padStart(2, '0')}-${String(futureDate.getDate()).padStart(2, '0')}T${String(futureDate.getHours()).padStart(2, '0')}:${String(futureDate.getMinutes()).padStart(2, '0')}`;
      setFormData(prev => ({
        ...prev,
        date: localDateString,
      }));
    } else {
      // 정상적인 날짜/시간 (날짜는 유지)
      setSelectedDate(newDate);
      setSelectedTime({
        hour: String(hour).padStart(2, '0'),
        minute: String(minute).padStart(2, '0'),
      });
      // 로컬 시간 문자열 생성 (UTC 변환 방지)
      const localDateString = `${newDate.getFullYear()}-${String(newDate.getMonth() + 1).padStart(2, '0')}-${String(newDate.getDate()).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
      setFormData(prev => ({
        ...prev,
        date: localDateString,
      }));
    }
  };

  // 모임 등록
  const handleCreateMeetup = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setFormLoading(true);
    try {
      const meetupData = {
        title: formData.title,
        description: formData.description || '',
        location: formData.location,
        latitude: formData.latitude,
        longitude: formData.longitude,
        date: formData.date,
        maxParticipants: formData.maxParticipants,
      };

      await meetupApi.createMeetup(meetupData);
      alert('모임이 성공적으로 등록되었습니다!');

      // 폼 초기화 및 닫기
      setFormData({
        title: '',
        description: '',
        location: '',
        latitude: null,
        longitude: null,
        date: '',
        maxParticipants: 10,
      });
      setFormErrors({});
      setShowCreateForm(false);

      // 모임 목록 새로고침
      fetchMeetups();
    } catch (error) {
      console.error('모임 등록 실패:', error);
      alert(error.response?.data?.error || '모임 등록에 실패했습니다.');
    } finally {
      setFormLoading(false);
    }
  };

  return (
    <Container>
      <Header>
        <Title>🐾 산책 모임</Title>
        <Controls>
          <LocationButton onClick={fetchUserLocation} title="내 위치로 이동">
            📍 내 위치
          </LocationButton>
          <LocationSelectButton onClick={() => setShowRegionControls(!showRegionControls)} title="위치 선택">
            📌 위치 선택
          </LocationSelectButton>
          {selectedLocation && (
            <SelectedLocationInfo>
              {selectedLocation.eupmyeondong && selectedLocation.eupmyeondong !== '전체'
                ? `${selectedLocation.sido} ${selectedLocation.sigungu} ${selectedLocation.eupmyeondong}`
                : selectedLocation.sigungu
                  ? `${selectedLocation.sido} ${selectedLocation.sigungu}`
                  : selectedLocation.sido || '내위치'}
            </SelectedLocationInfo>
          )}
          <RadiusControls>
            <RadiusSelect
              value={radius}
              onChange={(e) => {
                const newRadius = Number(e.target.value);
                const newMapLevel = calculateMapLevelFromRadius(newRadius);

                setRadius(newRadius);
                setMapLevel(newMapLevel);

                // 프로그래매틱 이동 플래그 설정 (리스트는 자동으로 조회됨)
                isProgrammaticMoveRef.current = true;
              }}
            >
              <option value={1}>레벨 1</option>
              <option value={3}>레벨 2</option>
              <option value={5}>레벨 3</option>
              <option value={10}>레벨 4</option>
              <option value={20}>레벨 5</option>
            </RadiusSelect>
          </RadiusControls>
          <CreateButton onClick={() => setShowCreateForm(true)}>
            ➕ 모임 등록
          </CreateButton>
          <ToggleButton onClick={() => {
            const newValue = !showList;
            setShowList(newValue);
            showListRef.current = newValue;
          }}>
            {showList ? '📋 리스트 숨기기' : '📋 리스트 보기'}
          </ToggleButton>
        </Controls>
        {showRegionControls && (
          <RegionControls>
            {currentView === 'sido' ? (
              // 시/도 선택 화면
              <RegionButtonGrid>
                {SIDOS.map((sido) => (
                  <RegionButton
                    key={sido}
                    onClick={async () => {
                      await handleRegionSelect(sido, null, null);
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
                    await handleRegionSelect(selectedSido, null, null, 'sido');
                  }}
                >
                  ← 뒤로
                </RegionButton>
                {(availableSigungus.length > 0 ? availableSigungus : (SIGUNGUS[selectedSido] || [])).map((sigungu) => (
                  <RegionButton
                    key={sigungu}
                    onClick={async () => {
                      await handleRegionSelect(selectedSido, sigungu, null);
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

      <ContentWrapper>
        <MapSection>
          {mapCenter && (
            <MapContainer
              services={[
                // 모임 마커만 표시
                ...meetups.map(m => ({
                  idx: m.idx,
                  name: m.title,
                  latitude: m.latitude,
                  longitude: m.longitude,
                  address: m.location,
                  type: 'meetup',
                })),
              ]}
              onServiceClick={handleMarkerClick}
              userLocation={userLocation}
              mapCenter={mapCenter}
              mapLevel={mapLevel}
              onMapIdle={handleMapIdle}
            />
          )}
        </MapSection>

        <ListSection style={{ display: showList ? 'flex' : 'none' }}>
          <ListHeader>
            {selectedLocation
              ? `${selectedLocation.bname || selectedLocation.sigungu || '선택한 위치'} 주변 모임 (${meetups.length}개)`
              : `주변 모임 목록 (${meetups.length}개)`}
            {radius > 0 && <RadiusInfo>레벨 {radiusToLevel(radius)}</RadiusInfo>}
          </ListHeader>
          {loading ? (
            <LoadingText>로딩 중...</LoadingText>
          ) : meetups.length === 0 ? (
            <EmptyText>주변에 모임이 없습니다.</EmptyText>
          ) : (
            <MeetupList>
              {meetups.map((meetup) => (
                <MeetupItem
                  key={meetup.idx}
                  onClick={() => handleMeetupClick(meetup)}
                  $isSelected={selectedMeetup?.idx === meetup.idx}
                >
                  <MeetupTitle>{meetup.title}</MeetupTitle>
                  <MeetupInfo>
                    <InfoItem>📍 {meetup.location}</InfoItem>
                    <InfoItem>🕐 {formatDate(meetup.date)}</InfoItem>
                    <InfoItem>
                      👥 {meetup.currentParticipants || 0}/{meetup.maxParticipants}명
                    </InfoItem>
                  </MeetupInfo>
                </MeetupItem>
              ))}
            </MeetupList>
          )}
        </ListSection>
      </ContentWrapper>

      {/* 모달 제거됨 - RegionControls로 대체 */}

      {selectedMeetup && (
        <ModalOverlay onClick={() => setSelectedMeetup(null)}>
          <ModalContent onClick={(e) => e.stopPropagation()}>
            <ModalHeader>
              <ModalTitle>{selectedMeetup.title}</ModalTitle>
              <CloseButton onClick={() => setSelectedMeetup(null)}>×</CloseButton>
            </ModalHeader>

            <ModalBody>
              <Section>
                <SectionTitle>📅 모임 일시</SectionTitle>
                <SectionContent>{formatDate(selectedMeetup.date)}</SectionContent>
              </Section>

              <Section>
                <SectionTitle>📍 모임 장소</SectionTitle>
                <SectionContent>{selectedMeetup.location}</SectionContent>
              </Section>

              {selectedMeetup.description && (
                <Section>
                  <SectionTitle>📝 모임 설명</SectionTitle>
                  <SectionContent>{selectedMeetup.description}</SectionContent>
                </Section>
              )}

              <Section>
                <SectionTitle>👥 참가자 ({participants.length}명)</SectionTitle>
                {participants.length === 0 ? (
                  <EmptyText>아직 참가자가 없습니다.</EmptyText>
                ) : (
                  <ParticipantsList>
                    {participants.map((p, index) => (
                      <ParticipantItem key={index}>
                        <ParticipantName>{p.username}</ParticipantName>
                        <ParticipantDate>
                          {new Date(p.joinedAt).toLocaleDateString('ko-KR')}
                        </ParticipantDate>
                      </ParticipantItem>
                    ))}
                  </ParticipantsList>
                )}
              </Section>

              <Section>
                <SectionTitle>📊 모임 정보</SectionTitle>
                <InfoGrid>
                  <InfoItem>
                    <Label>주최자:</Label>
                    <Value>{selectedMeetup.organizerName || '알 수 없음'}</Value>
                  </InfoItem>
                  <InfoItem>
                    <Label>참가 인원:</Label>
                    <Value>
                      {selectedMeetup.currentParticipants || 0}/{selectedMeetup.maxParticipants}명
                    </Value>
                  </InfoItem>
                  <InfoItem>
                    <Label>상태:</Label>
                    <Value>
                      {selectedMeetup.status === 'RECRUITING' ? '모집중' :
                        selectedMeetup.status === 'CLOSED' ? '마감' : '종료'}
                    </Value>
                  </InfoItem>
                </InfoGrid>
              </Section>

              {/* 참가하기 버튼 */}
              {selectedMeetup.organizerIdx?.toString() !== user?.idx?.toString() && (
                <ActionSection>
                  {isParticipating ? (
                    <CancelButton
                      onClick={handleCancelParticipation}
                      disabled={participationLoading}
                    >
                      {participationLoading ? '처리 중...' : '참가 취소'}
                    </CancelButton>
                  ) : (
                    <JoinButton
                      onClick={handleJoinMeetup}
                      disabled={
                        participationLoading ||
                        (selectedMeetup.currentParticipants || 0) >= (selectedMeetup.maxParticipants || 0) ||
                        selectedMeetup.status === 'CLOSED' ||
                        selectedMeetup.status === 'COMPLETED'
                      }
                    >
                      {participationLoading
                        ? '처리 중...'
                        : (selectedMeetup.currentParticipants || 0) >= (selectedMeetup.maxParticipants || 0)
                          ? '인원 마감'
                          : selectedMeetup.status === 'CLOSED' || selectedMeetup.status === 'COMPLETED'
                            ? '참가 불가'
                            : '참가하기'}
                    </JoinButton>
                  )}
                </ActionSection>
              )}
            </ModalBody>
          </ModalContent>
        </ModalOverlay>
      )}

      {showCreateForm && (
        <ModalOverlay onClick={() => setShowCreateForm(false)}>
          <ModalContent ref={createFormModalRef} onClick={(e) => e.stopPropagation()}>
            <ModalHeader>
              <ModalTitle>새 모임 등록</ModalTitle>
              <CloseButton onClick={() => setShowCreateForm(false)}>×</CloseButton>
            </ModalHeader>

            <Form onSubmit={handleCreateMeetup}>
              <FormGroup>
                <FormLabel>모임 제목 *</FormLabel>
                <Input
                  type="text"
                  name="title"
                  value={formData.title}
                  onChange={handleFormChange}
                  placeholder="예: 강아지 산책 모임"
                  required
                />
                {formErrors.title && <ErrorText>{formErrors.title}</ErrorText>}
              </FormGroup>

              <FormGroup>
                <FormLabel>모임 설명</FormLabel>
                <TextArea
                  name="description"
                  value={formData.description}
                  onChange={handleFormChange}
                  placeholder="모임에 대한 설명을 입력해주세요"
                  rows={4}
                />
              </FormGroup>

              <FormGroup>
                <FormLabel>모임 장소 *</FormLabel>
                <Input
                  type="text"
                  name="location"
                  value={formData.location}
                  onChange={handleFormChange}
                  placeholder="모임 장소를 입력해주세요 (예: 서울시 강남구 테헤란로)"
                  required
                />
                {formErrors.location && <ErrorText>{formErrors.location}</ErrorText>}
              </FormGroup>

              <FormGroup>
                <FormLabel>모임 일시 *</FormLabel>
                <DatePickerWrapper className="date-picker-wrapper">
                  <DateInputButton
                    ref={datePickerButtonRef}
                    type="button"
                    onClick={handleDatePickerToggle}
                    hasValue={!!formData.date}
                  >
                    {formData.date
                      ? formatDate(formData.date)
                      : '날짜와 시간을 선택해주세요'}
                    <CalendarIcon>📅</CalendarIcon>
                  </DateInputButton>
                </DatePickerWrapper>
                {showDatePicker && (
                  <DatePickerDropdown
                    className="date-picker-dropdown"
                    style={{
                      top: `${datePickerPosition.top}px`,
                      left: `${datePickerPosition.left}px`,
                    }}
                  >
                    <CalendarContainer>
                      <CalendarHeader>
                        <NavButton
                          type="button"
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            const current = selectedDate || new Date();
                            const newDate = new Date(current.getFullYear(), current.getMonth() - 1, 1);
                            setSelectedDate(newDate);
                          }}
                        >
                          ‹
                        </NavButton>
                        <MonthYear>
                          {selectedDate
                            ? `${selectedDate.getFullYear()}년 ${selectedDate.getMonth() + 1}월`
                            : `${new Date().getFullYear()}년 ${new Date().getMonth() + 1}월`}
                        </MonthYear>
                        <NavButton
                          type="button"
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            const current = selectedDate || new Date();
                            const newDate = new Date(current.getFullYear(), current.getMonth() + 1, 1);
                            setSelectedDate(newDate);
                          }}
                        >
                          ›
                        </NavButton>
                      </CalendarHeader>
                      <CalendarGrid>
                        {['일', '월', '화', '수', '목', '금', '토'].map((day) => (
                          <CalendarDayHeader key={day}>{day}</CalendarDayHeader>
                        ))}
                        {getCalendarDays(selectedDate || new Date()).map((day, index) => {
                          const now = new Date();
                          const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
                          const dayDate = new Date(day.getFullYear(), day.getMonth(), day.getDate());

                          const isToday = dayDate.getTime() === today.getTime();
                          const isSelected = selectedDate &&
                            dayDate.getTime() === new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate()).getTime();
                          const isPast = dayDate < today;
                          const isCurrentMonth = day.getMonth() === (selectedDate || new Date()).getMonth();

                          return (
                            <CalendarDay
                              key={index}
                              type="button"
                              isToday={isToday}
                              isSelected={isSelected}
                              isPast={isPast}
                              isCurrentMonth={isCurrentMonth}
                              onClick={(e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                if (!isPast && isCurrentMonth) {
                                  handleDateSelect(day);
                                }
                              }}
                            >
                              {day.getDate()}
                            </CalendarDay>
                          );
                        })}
                      </CalendarGrid>
                      <TimeSelector>
                        <TimeLabel>시간 선택:</TimeLabel>
                        <TimeInputs>
                          <TimeInput
                            type="number"
                            min="0"
                            max="23"
                            value={selectedTime.hour}
                            onChange={(e) => {
                              e.preventDefault();
                              e.stopPropagation();
                              handleTimeChange('hour', e.target.value);
                            }}
                            onBlur={(e) => {
                              if (e.target.value === '' || parseInt(e.target.value) < 0) {
                                handleTimeChange('hour', '0');
                              }
                            }}
                          />
                          <TimeSeparator>:</TimeSeparator>
                          <TimeInput
                            type="number"
                            min="0"
                            max="59"
                            value={selectedTime.minute}
                            onChange={(e) => {
                              e.preventDefault();
                              e.stopPropagation();
                              handleTimeChange('minute', e.target.value);
                            }}
                            onBlur={(e) => {
                              if (e.target.value === '' || parseInt(e.target.value) < 0) {
                                handleTimeChange('minute', '0');
                              }
                            }}
                          />
                        </TimeInputs>
                      </TimeSelector>
                      <DatePickerActions>
                        <DatePickerButton onClick={() => setShowDatePicker(false)}>
                          확인
                        </DatePickerButton>
                      </DatePickerActions>
                    </CalendarContainer>
                  </DatePickerDropdown>
                )}
                {formErrors.date && <ErrorText>{formErrors.date}</ErrorText>}
              </FormGroup>

              <FormGroup>
                <FormLabel>최대 인원 *</FormLabel>
                <Input
                  type="number"
                  name="maxParticipants"
                  value={formData.maxParticipants}
                  onChange={handleFormChange}
                  min="1"
                  max="100"
                  required
                />
                {formErrors.maxParticipants && <ErrorText>{formErrors.maxParticipants}</ErrorText>}
              </FormGroup>

              <ButtonGroup>
                <Button type="button" variant="secondary" onClick={() => setShowCreateForm(false)}>
                  취소
                </Button>
                <Button type="submit" variant="primary" disabled={formLoading}>
                  {formLoading ? '등록 중...' : '등록하기'}
                </Button>
              </ButtonGroup>
            </Form>
          </ModalContent>
        </ModalOverlay>
      )}
    </Container>
  );
};

export default MeetupPage;

const Container = styled.div`
  width: 100%;
  height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
  background: ${props => props.theme.colors.background};

  @media (max-width: 768px) {
    height: auto;
    min-height: calc(100vh - 80px);
  }
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};

  @media (max-width: 768px) {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
    padding: 1rem;
  }
`;

const Title = styled.h1`
  font-size: 1.5rem;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;

  @media (max-width: 768px) {
    font-size: 1.25rem;
  }
`;

const Controls = styled.div`
  display: flex;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;

  @media (max-width: 768px) {
    width: 100%;
    justify-content: space-between;
    gap: 0.5rem;
  }
`;

const RadiusSelect = styled.select`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
`;

const CreateButton = styled.button`
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 8px;
  background: ${props => props.theme.colors.primary};
  color: white;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary}dd;
    transform: translateY(-1px);
  }
`;

const LocationButton = styled.button`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    background: ${props => props.theme.colors.primary};
    color: white;
  }
`;

const LocationSelectButton = styled.button`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    background: ${props => props.theme.colors.primary};
    color: white;
  }
`;

const SelectedLocationInfo = styled.div`
  padding: 0.5rem 1rem;
  background: ${props => props.theme.colors.primary}22;
  color: ${props => props.theme.colors.primary};
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  white-space: nowrap;
`;

const RadiusControls = styled.div`
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
`;

const RegionControls = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.5rem 0;
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
  font-weight: 500;
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

// AutoRadiusCheckbox 제거됨

const ToggleButton = styled.button`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary};
    color: white;
  }
`;

const ContentWrapper = styled.div`
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
  min-height: 0; /* flexbox 자식이 올바르게 축소되도록 */

  @media (max-width: 1024px) {
    flex-direction: column;
  }
`;

const MapSection = styled.div`
  flex: 1;
  position: relative;
  min-width: 0; /* flexbox 자식이 올바르게 축소되도록 */
  overflow: hidden;
`;

const ListSection = styled.div`
  width: 350px;
  min-width: 300px;
  background: ${props => props.theme.colors.surface};
  border-left: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  flex-shrink: 0; /* 리스트 섹션이 줄어들지 않도록 */
  z-index: 10; /* z-index 증가 */

  @media (max-width: 1024px) {
    width: 100%;
    min-width: unset;
    border-left: none;
    border-top: 1px solid ${props => props.theme.colors.border};
    max-height: 400px;
    flex-shrink: 1;
  }
`;

const ListHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 1rem;
  font-weight: 600;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
`;

const RadiusInfo = styled.span`
  font-size: 0.85rem;
  font-weight: 400;
  color: ${props => props.theme.colors.textSecondary || props.theme.colors.text}99;
`;

const MeetupList = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
`;

const MeetupItem = styled.div`
  padding: 1rem;
  margin-bottom: 0.5rem;
  background: ${props => props.$isSelected ? props.theme.colors.primary + '20' : props.theme.colors.background};
  border: 1px solid ${props => props.$isSelected ? props.theme.colors.primary : props.theme.colors.border};
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary + '10'};
    border-color: ${props => props.theme.colors.primary};
  }
`;

const MeetupTitle = styled.div`
  font-weight: 600;
  font-size: 1rem;
  margin-bottom: 0.5rem;
  color: ${props => props.theme.colors.text};
`;

const MeetupInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
`;

const InfoItem = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem;
`;

const LoadingText = styled.div`
  padding: 2rem;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const EmptyText = styled.div`
  padding: 2rem;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;

const ModalOverlay = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const ModalContent = styled.div`
  background: ${props => props.theme.colors.surface};
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  position: relative;

  @media (max-width: 768px) {
    width: 95%;
    max-width: 100%;
    max-height: 90vh;
    border-radius: 8px;
  }
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const ModalTitle = styled.h2`
  font-size: 1.5rem;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 2rem;
  cursor: pointer;
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1;

  &:hover {
    color: ${props => props.theme.colors.text};
  }
`;

const ModalBody = styled.div`
  padding: 1.5rem;
`;

const Section = styled.div`
  margin-bottom: 1.5rem;
`;

const ActionSection = styled.div`
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid ${props => props.theme.colors.border};
  display: flex;
  justify-content: center;
`;

const JoinButton = styled.button`
  padding: 0.75rem 2rem;
  border: none;
  border-radius: 8px;
  background: ${props => props.disabled ? props.theme.colors.border : props.theme.colors.primary};
  color: white;
  font-size: 1rem;
  font-weight: 600;
  cursor: ${props => props.disabled ? 'not-allowed' : 'pointer'};
  transition: all 0.2s;
  width: 100%;
  max-width: 300px;

  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primary}dd;
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.6;
  }
`;

const CancelButton = styled.button`
  padding: 0.75rem 2rem;
  border: 1px solid ${props => props.theme.colors.error};
  border-radius: 8px;
  background: white;
  color: ${props => props.theme.colors.error};
  font-size: 1rem;
  font-weight: 600;
  cursor: ${props => props.disabled ? 'not-allowed' : 'pointer'};
  transition: all 0.2s;
  width: 100%;
  max-width: 300px;

  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.error};
    color: white;
  }

  &:disabled {
    opacity: 0.6;
  }
`;

const SectionTitle = styled.h3`
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: ${props => props.theme.colors.text};
`;

const SectionContent = styled.div`
  color: ${props => props.theme.colors.textSecondary};
  line-height: 1.6;
`;

const ParticipantsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

const ParticipantItem = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  background: ${props => props.theme.colors.background};
  border-radius: 8px;
`;

const ParticipantName = styled.div`
  font-weight: 500;
  color: ${props => props.theme.colors.text};
`;

const ParticipantDate = styled.div`
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
`;

const InfoGrid = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
`;

const Label = styled.span`
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  margin-right: 0.5rem;
`;

const Value = styled.span`
  color: ${props => props.theme.colors.textSecondary};
`;

const Form = styled.form`
  padding: 1.5rem;
`;

const FormGroup = styled.div`
  margin-bottom: 1.5rem;
`;

const FormLabel = styled.label`
  display: block;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: ${props => props.theme.colors.text};
`;

const Input = styled.input`
  width: 100%;
  padding: 0.75rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 1rem;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }
`;

const TextArea = styled.textarea`
  width: 100%;
  padding: 0.75rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  font-size: 1rem;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-family: inherit;
  resize: vertical;

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }
`;

const RegionSelectGroup = styled.div`
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
`;

const RegionSelect = styled.select`
  padding: 0.5rem 1rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 120px;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
  }

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }
`;

const AddressInputGroup = styled.div`
  display: flex;
  gap: 0.5rem;
`;

const SearchButton = styled.button`
  padding: 0.75rem 1.5rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 0.9rem;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary};
    color: white;
    border-color: ${props => props.theme.colors.primary};
  }
`;

const InfoText = styled.div`
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
`;

const ErrorText = styled.div`
  margin-top: 0.25rem;
  font-size: 0.85rem;
  color: #e74c3c;
`;

const ButtonGroup = styled.div`
  display: flex;
  gap: 1rem;
  margin-top: 2rem;
  justify-content: flex-end;
`;

const ConfirmButton = styled.button`
  padding: 0.75rem 2rem;
  border: none;
  border-radius: 8px;
  background: ${props => props.theme.colors.primary};
  color: white;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primary}dd;
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const Button = styled.button`
  flex: 1;
  padding: 0.75rem;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  ${props => props.variant === 'primary' && `
    background: ${props.theme.colors.primary};
    color: white;

    &:hover:not(:disabled) {
      background: ${props.theme.colors.primary}dd;
    }

    &:disabled {
      background: #ccc;
      cursor: not-allowed;
    }
  `}

  ${props => props.variant === 'secondary' && `
    background: ${props.theme.colors.surface};
    color: ${props.theme.colors.text};
    border: 1px solid ${props.theme.colors.border};

    &:hover {
      background: ${props.theme.colors.background};
    }
  `}
`;

const DatePickerWrapper = styled.div`
  position: relative;
`;

const DateInputButton = styled.button`
  width: 100%;
  padding: 0.75rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.hasValue ? props.theme.colors.text : props.theme.colors.textSecondary};
  font-size: 1rem;
  text-align: left;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all 0.2s;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
  }

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 0 2px ${props => props.theme.colors.primary}33;
  }
`;

const CalendarIcon = styled.span`
  font-size: 1.2rem;
`;

const DatePickerDropdown = styled.div`
  position: fixed;
  z-index: 2000;
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  padding: 1rem;
  min-width: 320px;
  animation: slideDown 0.2s ease-out;
  
  @keyframes slideDown {
    from {
      opacity: 0;
      transform: translateY(-10px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @media (max-width: 768px) {
    min-width: 280px;
    max-width: 90vw;
    padding: 0.75rem;
  }
`;

const CalendarContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem;
`;

const CalendarHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
`;

const NavButton = styled.button`
  width: 32px;
  height: 32px;
  border: none;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  border-radius: 6px;
  cursor: pointer;
  font-size: 1.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary};
    color: white;
  }
`;

const MonthYear = styled.div`
  font-weight: 600;
  font-size: 1.1rem;
  color: ${props => props.theme.colors.text};
`;

const CalendarGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 0.25rem;
`;

const CalendarDayHeader = styled.div`
  text-align: center;
  font-weight: 600;
  font-size: 0.85rem;
  color: ${props => props.theme.colors.textSecondary};
  padding: 0.5rem 0;
`;

const CalendarDay = styled.button`
  aspect-ratio: 1;
  border: none;
  background: ${props => {
    if (props.isSelected) return props.theme.colors.primary;
    if (props.isToday) return props.theme.colors.primary + '20';
    return 'transparent';
  }};
  color: ${props => {
    if (props.isSelected) return 'white';
    if (!props.isCurrentMonth) return props.theme.colors.textSecondary + '60';
    if (props.isPast) return props.theme.colors.textSecondary + '80';
    return props.theme.colors.text;
  }};
  border-radius: 6px;
  cursor: ${props => (props.isPast || !props.isCurrentMonth) ? 'not-allowed' : 'pointer'};
  font-size: 0.9rem;
  font-weight: ${props => (props.isToday || props.isSelected) ? '600' : '400'};
  transition: all 0.2s;
  opacity: ${props => (props.isPast || !props.isCurrentMonth) ? 0.5 : 1};

  &:hover:not(:disabled) {
    background: ${props => {
    if (props.isSelected) return props.theme.colors.primary;
    if (props.isPast || !props.isCurrentMonth) return 'transparent';
    return props.theme.colors.primary + '20';
  }};
    transform: ${props => (props.isPast || !props.isCurrentMonth) ? 'none' : 'scale(1.1)'};
  }

  &:disabled {
    cursor: not-allowed;
  }
`;

const TimeSelector = styled.div`
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: ${props => props.theme.colors.background};
  border-radius: 8px;
  border: 1px solid ${props => props.theme.colors.border};
`;

const TimeLabel = styled.div`
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const TimeInputs = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem;
`;

const TimeInput = styled.input`
  width: 60px;
  padding: 0.5rem;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 6px;
  text-align: center;
  font-size: 1rem;
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }
`;

const TimeSeparator = styled.span`
  font-size: 1.2rem;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const DatePickerActions = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
`;

const DatePickerButton = styled.button`
  padding: 0.5rem 1.5rem;
  border: none;
  border-radius: 6px;
  background: ${props => props.theme.colors.primary};
  color: white;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${props => props.theme.colors.primary}dd;
  }
`;

