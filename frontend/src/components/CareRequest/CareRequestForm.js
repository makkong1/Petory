import React, { useState, useEffect, useRef } from 'react';
import styled from 'styled-components';
import { careRequestApi } from '../../api/careRequestApi';
import { petApiClient } from '../../api/userApi';
import { useAuth } from '../../contexts/AuthContext';

const CareRequestForm = ({ onCancel, onCreated }) => {
  const { user } = useAuth();
  const [form, setForm] = useState({
    title: '',
    date: '',
    description: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [pets, setPets] = useState([]);
  const [selectedPetIdx, setSelectedPetIdx] = useState(null);
  const [loadingPets, setLoadingPets] = useState(false);
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [selectedDate, setSelectedDate] = useState(null);
  const [selectedTime, setSelectedTime] = useState({ hour: '12', minute: '00' });
  const [datePickerPosition, setDatePickerPosition] = useState({ top: 0, left: 0 });
  const datePickerButtonRef = useRef(null);
  const formContainerRef = useRef(null);

  // 펫 목록 불러오기
  useEffect(() => {
    if (user) {
      const fetchPets = async () => {
        try {
          setLoadingPets(true);
          const response = await petApiClient.getMyPets();
          setPets(response.data || []);
        } catch (err) {
          console.error('펫 목록 조회 실패:', err);
        } finally {
          setLoadingPets(false);
        }
      };
      fetchPets();
    }
  }, [user]);

  // 날짜/시간 초기화
  useEffect(() => {
    if (form.date) {
      const date = new Date(form.date);
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

  // 달력 버튼 위치 계산 (화면 중앙에 배치)
  const handleDatePickerToggle = () => {
    if (!showDatePicker) {
      if (datePickerButtonRef.current) {
        const rect = datePickerButtonRef.current.getBoundingClientRect();
        const calendarWidth = 320;

        // 화면 중앙에 배치
        const centerX = window.innerWidth / 2;
        const centerY = window.innerHeight / 2;

        setDatePickerPosition({
          top: centerY - 200 + window.scrollY, // 달력 높이의 약 절반만큼 위로
          left: centerX - calendarWidth / 2 + window.scrollX,
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

  // 날짜 포맷팅
  const formatDate = (dateString) => {
    if (!dateString) return '';
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

    const hour = parseInt(selectedTime.hour) || 0;
    const minute = parseInt(selectedTime.minute) || 0;

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
      const localDateString = `${futureDate.getFullYear()}-${String(futureDate.getMonth() + 1).padStart(2, '0')}-${String(futureDate.getDate()).padStart(2, '0')}T${String(futureDate.getHours()).padStart(2, '0')}:${String(futureDate.getMinutes()).padStart(2, '0')}`;
      setForm(prev => ({
        ...prev,
        date: localDateString,
      }));
    } else {
      setSelectedDate(newDate);
      setSelectedTime({
        hour: String(hour).padStart(2, '0'),
        minute: String(minute).padStart(2, '0'),
      });
      const localDateString = `${newDate.getFullYear()}-${String(newDate.getMonth() + 1).padStart(2, '0')}-${String(newDate.getDate()).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
      setForm(prev => ({
        ...prev,
        date: localDateString,
      }));
    }
  };

  // 시간 변경 핸들러
  const handleTimeChange = (type, value) => {
    let baseDate = selectedDate;
    if (!baseDate && form.date) {
      baseDate = new Date(form.date);
    }
    if (!baseDate) {
      const defaultDate = new Date();
      defaultDate.setHours(defaultDate.getHours() + 1, 0, 0, 0);
      setSelectedDate(defaultDate);
      setSelectedTime({
        hour: String(defaultDate.getHours()).padStart(2, '0'),
        minute: '00',
      });
      const localDateString = `${defaultDate.getFullYear()}-${String(defaultDate.getMonth() + 1).padStart(2, '0')}-${String(defaultDate.getDate()).padStart(2, '0')}T${String(defaultDate.getHours()).padStart(2, '0')}:${String(defaultDate.getMinutes()).padStart(2, '0')}`;
      setForm(prev => ({
        ...prev,
        date: localDateString,
      }));
      return;
    }

    const dateOnly = new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate());

    let hour = parseInt(selectedTime.hour) || 0;
    let minute = parseInt(selectedTime.minute) || 0;

    if (type === 'hour') {
      hour = Math.max(0, Math.min(23, parseInt(value) || 0));
    } else if (type === 'minute') {
      minute = Math.max(0, Math.min(59, parseInt(value) || 0));
    }

    const newDate = new Date(dateOnly);
    newDate.setHours(hour, minute, 0, 0);

    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const selectedDay = new Date(dateOnly.getFullYear(), dateOnly.getMonth(), dateOnly.getDate());

    if (selectedDay.getTime() === today.getTime() && newDate < now) {
      const futureDate = new Date(now);
      futureDate.setHours(futureDate.getHours() + 1, 0, 0, 0);
      setSelectedDate(futureDate);
      setSelectedTime({
        hour: String(futureDate.getHours()).padStart(2, '0'),
        minute: String(futureDate.getMinutes()).padStart(2, '0'),
      });
      const localDateString = `${futureDate.getFullYear()}-${String(futureDate.getMonth() + 1).padStart(2, '0')}-${String(futureDate.getDate()).padStart(2, '0')}T${String(futureDate.getHours()).padStart(2, '0')}:${String(futureDate.getMinutes()).padStart(2, '0')}`;
      setForm(prev => ({
        ...prev,
        date: localDateString,
      }));
    } else {
      setSelectedDate(newDate);
      setSelectedTime({
        hour: String(hour).padStart(2, '0'),
        minute: String(minute).padStart(2, '0'),
      });
      const localDateString = `${newDate.getFullYear()}-${String(newDate.getMonth() + 1).padStart(2, '0')}-${String(newDate.getDate()).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
      setForm(prev => ({
        ...prev,
        date: localDateString,
      }));
    }
  };


  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!user) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }

    if (!form.title.trim()) {
      setError('제목을 입력해주세요.');
      return;
    }

    if (!form.description.trim()) {
      setError('요청 내용을 입력해주세요.');
      return;
    }

    const payload = {
      title: form.title.trim(),
      description: form.description.trim(),
      userId: user.idx,
      petIdx: selectedPetIdx || null,
    };

    if (form.date) {
      const parsedDate = new Date(form.date);
      if (Number.isNaN(parsedDate.getTime())) {
        setError('유효한 날짜와 시간을 선택해주세요.');
        return;
      }
      payload.date = parsedDate.toISOString();
    }

    try {
      setSubmitting(true);
      setError('');
      const response = await careRequestApi.createCareRequest(payload);
      const created = response.data || null;
      if (created) {
        onCreated?.(created);
      } else {
        onCreated?.({
          ...payload,
          status: 'OPEN',
        });
      }
    } catch (err) {
      const message =
        err.response?.data?.error ||
        err.response?.data?.message ||
        err.message ||
        '요청을 등록하지 못했습니다.';
      setError(message);
    } finally {
      setSubmitting(false);
    }
  };

  const selectedPet = pets.find(p => p.idx === selectedPetIdx);

  return (
    <FormContainer ref={formContainerRef}>
      <FormWrapper>
        <LeftCard>
          <Form onSubmit={handleSubmit}>
            <Field>
              <Label htmlFor="care-request-title">제목</Label>
              <TextInput
                id="care-request-title"
                name="title"
                value={form.title}
                onChange={handleChange}
                placeholder="예: 주말 여행 동안 강아지 산책 도와주세요"
                disabled={submitting}
                required
              />
            </Field>

            <Field>
              <Label htmlFor="care-request-date">요청 일시</Label>
              <DatePickerWrapper className="date-picker-wrapper">
                <DateInputButton
                  ref={datePickerButtonRef}
                  type="button"
                  onClick={handleDatePickerToggle}
                  hasValue={!!form.date}
                  disabled={submitting}
                >
                  {form.date
                    ? formatDate(form.date)
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
              <HelperText>선택 사항입니다. 필요한 경우 정확한 일시를 입력하세요.</HelperText>
            </Field>

            <Field>
              <Label htmlFor="care-request-description">요청 내용</Label>
              <TextArea
                id="care-request-description"
                name="description"
                value={form.description}
                onChange={handleChange}
                placeholder="돌봄이 필요한 반려동물 정보, 원하는 도움 내용을 자세히 적어주세요."
                rows={6}
                disabled={submitting}
                required
              />
            </Field>

            {error && <ErrorBanner>{error}</ErrorBanner>}

            <ButtonRow>
              <SecondaryButton type="button" onClick={onCancel} disabled={submitting}>
                취소
              </SecondaryButton>
              <PrimaryButton type="submit" disabled={submitting}>
                {submitting ? '등록 중...' : '등록하기'}
              </PrimaryButton>
            </ButtonRow>
          </Form>
        </LeftCard>

        <RightCard>
          <CardTitle>반려동물 정보</CardTitle>
          {loadingPets ? (
            <LoadingMessage>펫 정보를 불러오는 중...</LoadingMessage>
          ) : pets.length === 0 ? (
            <EmptyMessage>등록된 반려동물이 없습니다.</EmptyMessage>
          ) : (
            <>
              <PetSelect
                value={selectedPetIdx || ''}
                onChange={(e) => setSelectedPetIdx(e.target.value ? Number(e.target.value) : null)}
              >
                <option value="">펫 선택 (선택사항)</option>
                {pets.map(pet => (
                  <option key={pet.idx} value={pet.idx}>
                    {pet.petName} ({pet.petType === 'DOG' ? '강아지' : pet.petType === 'CAT' ? '고양이' : pet.petType})
                  </option>
                ))}
              </PetSelect>

              {selectedPet && (
                <PetInfoCard>
                  <PetImageWrapper>
                    {selectedPet.profileImageUrl ? (
                      <PetImage
                        src={selectedPet.profileImageUrl}
                        alt={selectedPet.petName}
                        onError={(e) => {
                          e.target.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="200" height="200"%3E%3Crect width="200" height="200" fill="%23e2e8f0"/%3E%3Ctext x="100" y="100" font-family="Arial" font-size="16" fill="%2394a3b8" text-anchor="middle" dominant-baseline="middle"%3E사진 없음%3C/text%3E%3C/svg%3E';
                        }}
                      />
                    ) : (
                      <NoImagePlaceholder>
                        <NoImageText>사진 없음</NoImageText>
                      </NoImagePlaceholder>
                    )}
                  </PetImageWrapper>
                  <PetDetails>
                    <PetName>{selectedPet.petName}</PetName>
                    <PetDetail>
                      {selectedPet.petType === 'DOG' ? '강아지' :
                        selectedPet.petType === 'CAT' ? '고양이' :
                          selectedPet.petType === 'BIRD' ? '새' :
                            selectedPet.petType === 'RABBIT' ? '토끼' :
                              selectedPet.petType === 'HAMSTER' ? '햄스터' : '기타'}
                      {' · '}
                      {selectedPet.breed || '품종 미상'}
                    </PetDetail>
                    {selectedPet.age && <PetDetail>나이: {selectedPet.age}</PetDetail>}
                    {selectedPet.gender && (
                      <PetDetail>
                        성별: {selectedPet.gender === 'M' ? '수컷' : selectedPet.gender === 'F' ? '암컷' : '미확인'}
                      </PetDetail>
                    )}
                    {selectedPet.color && <PetDetail>색상: {selectedPet.color}</PetDetail>}
                    {selectedPet.weight && <PetDetail>몸무게: {selectedPet.weight}kg</PetDetail>}
                    {selectedPet.healthInfo && <PetDetail>건강 정보: {selectedPet.healthInfo}</PetDetail>}
                    {selectedPet.specialNotes && <PetDetail>특이사항: {selectedPet.specialNotes}</PetDetail>}
                  </PetDetails>
                </PetInfoCard>
              )}
            </>
          )}
        </RightCard>
      </FormWrapper>
    </FormContainer>
  );
};

export default CareRequestForm;

const FormContainer = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  padding: ${(props) => props.theme.spacing.xxl};
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.18);
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
    border-radius: ${(props) => props.theme.borderRadius.lg};
  }
`;

const FormWrapper = styled.div`
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: ${(props) => props.theme.spacing.xl};

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const LeftCard = styled.div`
  display: flex;
  flex-direction: column;
`;

const RightCard = styled.div`
  background: ${(props) => props.theme.colors.surfaceElevated};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.lg};
  height: fit-content;
  position: sticky;
  top: ${(props) => props.theme.spacing.xl};

  @media (max-width: 1024px) {
    position: static;
  }
`;

const CardTitle = styled.h3`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  font-size: 1.1rem;
  color: ${(props) => props.theme.colors.text};
`;

const PetSelect = styled.select`
  width: 100%;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  font-size: 0.95rem;
  margin-bottom: ${(props) => props.theme.spacing.md};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const PetInfoCard = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const PetImageWrapper = styled.div`
  width: 100%;
  aspect-ratio: 1;
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  background: ${(props) => props.theme.colors.borderLight};
  display: flex;
  align-items: center;
  justify-content: center;
`;

const PetImage = styled.img`
  width: 100%;
  height: 100%;
  object-fit: cover;
`;

const NoImagePlaceholder = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${(props) => props.theme.colors.borderLight};
`;

const NoImageText = styled.div`
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.9rem;
`;

const PetDetails = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const PetName = styled.div`
  font-size: 1.1rem;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const PetDetail = styled.div`
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const LoadingMessage = styled.div`
  padding: ${(props) => props.theme.spacing.lg};
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const EmptyMessage = styled.div`
  padding: ${(props) => props.theme.spacing.lg};
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
  font-style: italic;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.lg};
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const Label = styled.label`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
  font-size: 0.95rem;
`;

const TextInput = styled.input`
  width: 100%;
  padding: 0.75rem;
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 8px;
  font-size: 1rem;
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
`;

const TextArea = styled.textarea`
  width: 100%;
  padding: 0.75rem;
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 8px;
  font-size: 1rem;
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};
  font-family: inherit;
  resize: vertical;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
`;

const HelperText = styled.p`
  margin: 0;
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorBanner = styled.div`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  background: rgba(220, 38, 38, 0.12);
  color: ${(props) => props.theme.colors.error || '#dc2626'};
  border: 1px solid rgba(220, 38, 38, 0.2);
  font-size: 0.9rem;
`;

const ButtonRow = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${(props) => props.theme.spacing.sm};
`;

const PrimaryButton = styled.button`
  min-width: 140px;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: none;
  background: ${(props) => props.theme.colors.primary};
  color: #ffffff;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.primaryDark};
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const SecondaryButton = styled.button`
  min-width: 120px;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    color: ${(props) => props.theme.colors.primary};
    border-color: ${(props) => props.theme.colors.primary};
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const DatePickerWrapper = styled.div`
  position: relative;
`;

const DateInputButton = styled.button`
  width: 100%;
  padding: 0.75rem;
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 8px;
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.hasValue ? props.theme.colors.text : props.theme.colors.textSecondary};
  font-size: 1rem;
  text-align: left;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all 0.2s;

  &:hover {
    border-color: ${(props) => props.theme.colors.primary};
  }

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 2px ${(props) => props.theme.colors.primary}33;
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
`;

const CalendarIcon = styled.span`
  font-size: 1.2rem;
`;

const DatePickerDropdown = styled.div`
  position: fixed;
  z-index: 2000;
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
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
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};
  border-radius: 6px;
  cursor: pointer;
  font-size: 1.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  &:hover {
    background: ${(props) => props.theme.colors.primary};
    color: white;
  }
`;

const MonthYear = styled.div`
  font-weight: 600;
  font-size: 1.1rem;
  color: ${(props) => props.theme.colors.text};
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
  color: ${(props) => props.theme.colors.textSecondary};
  padding: 0.5rem 0;
`;

const CalendarDay = styled.button`
  aspect-ratio: 1;
  border: none;
  background: ${(props) => {
    if (props.isSelected) return props.theme.colors.primary;
    if (props.isToday) return props.theme.colors.primary + '20';
    return 'transparent';
  }};
  color: ${(props) => {
    if (props.isSelected) return 'white';
    if (!props.isCurrentMonth) return props.theme.colors.textSecondary + '60';
    if (props.isPast) return props.theme.colors.textSecondary + '80';
    return props.theme.colors.text;
  }};
  border-radius: 6px;
  cursor: ${(props) => (props.isPast || !props.isCurrentMonth) ? 'not-allowed' : 'pointer'};
  font-size: 0.9rem;
  font-weight: ${(props) => (props.isToday || props.isSelected) ? '600' : '400'};
  transition: all 0.2s;
  opacity: ${(props) => (props.isPast || !props.isCurrentMonth) ? 0.5 : 1};

  &:hover:not(:disabled) {
    background: ${(props) => {
    if (props.isSelected) return props.theme.colors.primary;
    if (props.isPast || !props.isCurrentMonth) return 'transparent';
    return props.theme.colors.primary + '20';
  }};
    transform: ${(props) => (props.isPast || !props.isCurrentMonth) ? 'none' : 'scale(1.1)'};
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
  background: ${(props) => props.theme.colors.background};
  border-radius: 8px;
  border: 1px solid ${(props) => props.theme.colors.border};
`;

const TimeLabel = styled.div`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const TimeInputs = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem;
`;

const TimeInput = styled.input`
  width: 60px;
  padding: 0.5rem;
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 6px;
  text-align: center;
  font-size: 1rem;
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const TimeSeparator = styled.span`
  font-size: 1.2rem;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
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
  background: ${(props) => props.theme.colors.primary};
  color: white;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${(props) => props.theme.colors.primary}dd;
  }
`;

