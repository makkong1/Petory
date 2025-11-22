import React, { useState, useEffect, useCallback, useRef } from 'react';
import styled from 'styled-components';
import { meetupApi } from '../../api/meetupApi';
import MapContainer from '../LocationService/MapContainer';
import { useAuth } from '../../contexts/AuthContext';
import { geocodingApi } from '../../api/geocodingApi';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };
const DEFAULT_RADIUS = 5; // km

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
  const [locationError, setLocationError] = useState(null);
  const [showList, setShowList] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
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
  const fetchUserLocation = useCallback(() => {
    if (navigator.geolocation) {
      setLocationError(null);
      const options = {
        enableHighAccuracy: true, // 높은 정확도 사용
        timeout: 15000, // 15초 타임아웃 (더 길게)
        maximumAge: 60000, // 1분 이내 캐시된 위치 사용 가능
      };

      navigator.geolocation.getCurrentPosition(
        (position) => {
          const location = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          };
          console.log('사용자 위치 가져오기 성공:', location);
          console.log('위치 정확도:', position.coords.accuracy, 'm');
          setUserLocation(location);
          setMapCenter(location); // 사용자 위치를 기본 중심점으로 설정
          setLocationError(null);
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
  const fetchMeetups = useCallback(async () => {
    if (!mapCenter || !mapCenter.lat || !mapCenter.lng) {
      return;
    }

    setLoading(true);
    try {
      const response = await meetupApi.getNearbyMeetups(
        mapCenter.lat,
        mapCenter.lng,
        radius
      );
      setMeetups(response.data.meetups || []);
    } catch (error) {
      console.error('모임 조회 실패:', error);
      const errorMessage = error.response?.data?.error || error.message || '모임을 불러오는데 실패했습니다.';
      console.error('에러 상세:', errorMessage);
      // 에러는 콘솔에만 출력 (alert 제거)
      // alert(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [mapCenter, radius]);

  // 지도 이동 시 모임 재조회
  const handleMapIdle = useCallback((mapInfo) => {
    if (mapInfo && mapInfo.lat && mapInfo.lng) {
      const newCenter = {
        lat: mapInfo.lat,
        lng: mapInfo.lng,
      };
      // 위치가 실제로 변경되었을 때만 업데이트
      if (!mapCenter ||
        Math.abs(mapCenter.lat - newCenter.lat) > 0.0001 ||
        Math.abs(mapCenter.lng - newCenter.lng) > 0.0001) {
        setMapCenter(newCenter);
      }
    }
  }, [mapCenter]);

  // mapCenter가 변경될 때만 모임 조회 (프로그래매틱 이동 제외)
  useEffect(() => {
    if (mapCenter && mapCenter.lat && mapCenter.lng) {
      // 프로그래매틱 이동이면 모임 목록 재조회하지 않음
      if (isProgrammaticMoveRef.current) {
        isProgrammaticMoveRef.current = false;
        return;
      }
      fetchMeetups();
    }
  }, [mapCenter, radius]); // fetchMeetups를 의존성에서 제거하여 무한 루프 방지

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
  const handleMarkerClick = async (meetup) => {
    await handleMeetupClick(meetup);
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

  // 주소 검색 (다음 주소 API)
  useEffect(() => {
    if (showCreateForm && !window.daum?.Postcode) {
      const script = document.createElement('script');
      script.src = '//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
      script.async = true;
      document.body.appendChild(script);
      return () => {
        if (document.body.contains(script)) {
          document.body.removeChild(script);
        }
      };
    }
  }, [showCreateForm]);

  const handleAddressSearch = async () => {
    if (!window.daum?.Postcode) {
      alert('주소 검색 스크립트 로드중입니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    new window.daum.Postcode({
      oncomplete: async function (data) {
        const address = data.roadAddress || data.jibunAddress;
        setFormData(prev => ({ ...prev, location: address }));

        // 주소로 위도/경도 변환
        try {
          const response = await geocodingApi.addressToCoordinates(address);
          const data = response.data; // axios response의 data 속성

          if (data && data.success !== false && data.latitude && data.longitude) {
            setFormData(prev => ({
              ...prev,
              latitude: data.latitude,
              longitude: data.longitude,
            }));
            // 주소 검색 성공 시 에러 제거
            setFormErrors(prev => {
              const newErrors = { ...prev };
              delete newErrors.location;
              return newErrors;
            });
          } else {
            throw new Error(data?.message || data?.error || '위도/경도 정보를 받지 못했습니다.');
          }
        } catch (error) {
          console.error('주소 변환 실패:', error);
          const errorMessage = error.response?.data?.error || error.response?.data?.message || error.message || '위도/경도 변환에 실패했습니다.';
          alert(errorMessage);
        }
      }
    }).open();
  };

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
      errors.location = '주소 검색을 통해 위치를 설정해주세요.';
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
          <RadiusSelect value={radius} onChange={(e) => setRadius(Number(e.target.value))}>
            <option value={1}>1km</option>
            <option value={3}>3km</option>
            <option value={5}>5km</option>
            <option value={10}>10km</option>
          </RadiusSelect>
          <CreateButton onClick={() => setShowCreateForm(true)}>
            ➕ 모임 등록
          </CreateButton>
          <ToggleButton onClick={() => setShowList(!showList)}>
            {showList ? '📋 리스트 숨기기' : '📋 리스트 보기'}
          </ToggleButton>
        </Controls>
      </Header>

      <ContentWrapper>
        <MapSection>
          {mapCenter && (
            <MapContainer
              services={meetups.map(m => ({
                idx: m.idx,
                name: m.title,
                latitude: m.latitude,
                longitude: m.longitude,
                address: m.location,
              }))}
              onServiceClick={handleMarkerClick}
              userLocation={userLocation}
              mapCenter={mapCenter}
              onMapIdle={handleMapIdle}
            />
          )}
        </MapSection>

        {showList && (
          <ListSection>
            <ListHeader>주변 모임 목록 ({meetups.length}개)</ListHeader>
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
                    isSelected={selectedMeetup?.idx === meetup.idx}
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
        )}
      </ContentWrapper>

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
                <AddressInputGroup>
                  <Input
                    type="text"
                    name="location"
                    value={formData.location}
                    onChange={handleFormChange}
                    placeholder="주소를 검색해주세요"
                    required
                    readOnly
                  />
                  <SearchButton type="button" onClick={handleAddressSearch}>
                    주소 검색
                  </SearchButton>
                </AddressInputGroup>
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
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const Title = styled.h1`
  font-size: 1.5rem;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
`;

const Controls = styled.div`
  display: flex;
  gap: 1rem;
  align-items: center;
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
`;

const MapSection = styled.div`
  flex: 1;
  position: relative;
`;

const ListSection = styled.div`
  width: 350px;
  background: ${props => props.theme.colors.surface};
  border-left: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  overflow: hidden;
`;

const ListHeader = styled.div`
  padding: 1rem;
  font-weight: 600;
  border-bottom: 1px solid ${props => props.theme.colors.border};
  color: ${props => props.theme.colors.text};
`;

const MeetupList = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
`;

const MeetupItem = styled.div`
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

