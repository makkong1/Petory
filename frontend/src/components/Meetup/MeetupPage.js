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
  const [loading, setLoading] = useState(false);
  const [userLocation, setUserLocation] = useState(null);
  const [mapCenter, setMapCenter] = useState(DEFAULT_CENTER);
  const [radius, setRadius] = useState(DEFAULT_RADIUS);
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

  // formData.date가 변경될 때 selectedDate와 selectedTime 업데이트
  useEffect(() => {
    if (formData.date) {
      const date = new Date(formData.date);
      setSelectedDate(date);
      setSelectedTime({
        hour: String(date.getHours()).padStart(2, '0'),
        minute: String(date.getMinutes()).padStart(2, '0'),
      });
    }
  }, [formData.date]);

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

  // 현재 위치 가져오기
  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const location = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          };
          setUserLocation(location);
          setMapCenter(location);
        },
        (error) => {
          console.error('위치 정보 가져오기 실패:', error);
        }
      );
    }
  }, []);

  // 모임 목록 조회
  const fetchMeetups = useCallback(async () => {
    if (!mapCenter) return;

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
      alert('모임을 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [mapCenter, radius]);

  // 지도 이동 시 모임 재조회
  const handleMapIdle = useCallback((mapInfo) => {
    if (mapInfo && mapInfo.lat && mapInfo.lng) {
      setMapCenter({
        lat: mapInfo.lat,
        lng: mapInfo.lng,
      });
    }
  }, []);

  useEffect(() => {
    fetchMeetups();
  }, [fetchMeetups]);

  // 참가자 목록 조회
  const fetchParticipants = async (meetupIdx) => {
    try {
      const response = await meetupApi.getParticipants(meetupIdx);
      setParticipants(response.data.participants || []);
    } catch (error) {
      console.error('참가자 목록 조회 실패:', error);
    }
  };

  // 모임 클릭 핸들러
  const handleMeetupClick = async (meetup) => {
    setSelectedMeetup(meetup);
    await fetchParticipants(meetup.idx);
  };

  // 마커 클릭 핸들러
  const handleMarkerClick = async (meetup) => {
    await handleMeetupClick(meetup);
  };

  // 날짜 포맷팅
  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
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
      oncomplete: async function(data) {
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
  const getCalendarDays = (date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const startDate = new Date(firstDay);
    startDate.setDate(startDate.getDate() - startDate.getDay());
    
    const days = [];
    for (let i = 0; i < 42; i++) {
      const day = new Date(startDate);
      day.setDate(startDate.getDate() + i);
      days.push(day);
    }
    return days;
  };

  // 같은 날인지 확인
  const isSameDay = (date1, date2) => {
    return (
      date1.getFullYear() === date2.getFullYear() &&
      date1.getMonth() === date2.getMonth() &&
      date1.getDate() === date2.getDate()
    );
  };

  // 날짜/시간 업데이트
  const updateDateTime = (date, hour, minute) => {
    if (!date) {
      // 날짜가 없으면 오늘 날짜 사용
      date = new Date();
    }
    
    const newDate = new Date(date);
    const h = parseInt(hour) || 12;
    const m = parseInt(minute) || 0;
    newDate.setHours(h, m, 0, 0);
    
    // 과거 날짜인지 확인 (시간 포함)
    const now = new Date();
    if (newDate < now) {
      // 과거면 현재 시간 이후로 설정
      const futureDate = new Date(now);
      futureDate.setHours(h, m, 0, 0);
      // 선택한 시간이 현재 시간보다 과거면 1시간 후로 설정
      if (futureDate < now) {
        futureDate.setHours(now.getHours() + 1, 0, 0, 0);
      }
      setFormData(prev => ({
        ...prev,
        date: futureDate.toISOString().slice(0, 16),
      }));
      setSelectedDate(futureDate);
      setSelectedTime({
        hour: String(futureDate.getHours()).padStart(2, '0'),
        minute: String(futureDate.getMinutes()).padStart(2, '0'),
      });
    } else {
      setFormData(prev => ({
        ...prev,
        date: newDate.toISOString().slice(0, 16),
      }));
      setSelectedDate(newDate);
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
                            onClick={() => {
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
                            onClick={() => {
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
                            const isToday = isSameDay(day, new Date());
                            const isSelected = formData.date && isSameDay(day, new Date(formData.date));
                            const isPast = day < new Date(new Date().setHours(0, 0, 0, 0));
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
                                    const newDate = new Date(day);
                                    const hour = parseInt(selectedTime.hour) || 12;
                                    const minute = parseInt(selectedTime.minute) || 0;
                                    newDate.setHours(hour, minute, 0, 0);
                                    
                                    setSelectedDate(newDate);
                                    setFormData(prev => ({
                                      ...prev,
                                      date: newDate.toISOString().slice(0, 16),
                                    }));
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
                                let hour = e.target.value;
                                if (hour === '') hour = '0';
                                hour = Math.max(0, Math.min(23, parseInt(hour) || 0)).toString().padStart(2, '0');
                                setSelectedTime(prev => {
                                  const updated = { ...prev, hour };
                                  // 날짜가 있으면 업데이트, 없으면 오늘 날짜로 설정
                                  const baseDate = selectedDate || (formData.date ? new Date(formData.date) : new Date());
                                  updateDateTime(baseDate, hour, updated.minute);
                                  return updated;
                                });
                              }}
                            />
                            <TimeSeparator>:</TimeSeparator>
                            <TimeInput
                              type="number"
                              min="0"
                              max="59"
                              value={selectedTime.minute}
                              onChange={(e) => {
                                let minute = e.target.value;
                                if (minute === '') minute = '0';
                                minute = Math.max(0, Math.min(59, parseInt(minute) || 0)).toString().padStart(2, '0');
                                setSelectedTime(prev => {
                                  const updated = { ...prev, minute };
                                  // 날짜가 있으면 업데이트, 없으면 오늘 날짜로 설정
                                  const baseDate = selectedDate || (formData.date ? new Date(formData.date) : new Date());
                                  updateDateTime(baseDate, updated.hour, minute);
                                  return updated;
                                });
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

