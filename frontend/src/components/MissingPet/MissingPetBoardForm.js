import React, { useEffect, useState, useRef } from 'react';
import styled from 'styled-components';
import { uploadApi } from '../../api/uploadApi';
import { petApiClient } from '../../api/userApi';
import AddressMapSelector from './AddressMapSelector';

const defaultForm = {
  title: '',
  content: '',
  petName: '',
  species: '',
  breed: '',
  gender: '',
  age: '',
  color: '',
  lostDate: '',
  lostLocation: '',
  latitude: '',
  longitude: '',
  imageUrl: '',
};

const MissingPetBoardForm = ({ isOpen, onClose, onSubmit, initialData, loading, currentUser }) => {
  const [form, setForm] = useState(defaultForm);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [pets, setPets] = useState([]);
  const [selectedPetIdx, setSelectedPetIdx] = useState(null);
  const [loadingPets, setLoadingPets] = useState(false);
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [selectedDate, setSelectedDate] = useState(null);
  const [selectedTime, setSelectedTime] = useState({ hour: '12', minute: '00' });
  const [datePickerPosition, setDatePickerPosition] = useState({ top: 0, left: 0 });
  const datePickerButtonRef = useRef(null);

  // 펫 목록 불러오기
  useEffect(() => {
    if (currentUser && isOpen) {
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
  }, [currentUser, isOpen]);

  useEffect(() => {
    if (initialData) {
      setForm({
        ...defaultForm,
        ...initialData,
        lostDate: initialData.lostDate || '',
      });
    } else {
      setForm(defaultForm);
      setSelectedPetIdx(null);
    }
    setUploadError('');
    setIsUploading(false);
  }, [initialData, isOpen]);

  // 날짜/시간 초기화
  useEffect(() => {
    if (form.lostDate) {
      const date = new Date(form.lostDate);
      setSelectedDate(date);
      setSelectedTime({
        hour: String(date.getHours()).padStart(2, '0'),
        minute: String(date.getMinutes()).padStart(2, '0'),
      });
    } else {
      // 기본값: 현재 시간
      const defaultDate = new Date();
      setSelectedDate(defaultDate);
      setSelectedTime({
        hour: String(defaultDate.getHours()).padStart(2, '0'),
        minute: String(defaultDate.getMinutes()).padStart(2, '0'),
      });
    }
  }, [form.lostDate]);

  // 달력 버튼 위치 계산 (중앙 정렬)
  const handleDatePickerToggle = (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    if (!showDatePicker) {
      const calendarWidth = 320;
      const calendarHeight = 400; // Approximate height of the calendar dropdown
      const gap = 8;

      // Center the calendar on the screen
      const left = (window.innerWidth - calendarWidth) / 2 + window.scrollX;
      const top = (window.innerHeight - calendarHeight) / 2 + window.scrollY - 50; // Adjusted slightly up

      setDatePickerPosition({
        top: Math.max(gap, top),
        left: Math.max(gap, left),
      });
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
    const hour = parseInt(selectedTime.hour) || 0;
    const minute = parseInt(selectedTime.minute) || 0;

    const newDate = new Date(day.getFullYear(), day.getMonth(), day.getDate());
    newDate.setHours(hour, minute, 0, 0);

    setSelectedDate(newDate);
    setSelectedTime({
      hour: String(hour).padStart(2, '0'),
      minute: String(minute).padStart(2, '0'),
    });
    
    // 날짜만 저장 (YYYY-MM-DD 형식)
    const dateString = `${newDate.getFullYear()}-${String(newDate.getMonth() + 1).padStart(2, '0')}-${String(newDate.getDate()).padStart(2, '0')}`;
    setForm(prev => ({
      ...prev,
      lostDate: dateString,
    }));
  };

  // 시간 변경 핸들러
  const handleTimeChange = (type, value) => {
    let baseDate = selectedDate;
    if (!baseDate && form.lostDate) {
      baseDate = new Date(form.lostDate);
    }
    if (!baseDate) {
      const defaultDate = new Date();
      setSelectedDate(defaultDate);
      setSelectedTime({
        hour: String(defaultDate.getHours()).padStart(2, '0'),
        minute: String(defaultDate.getMinutes()).padStart(2, '0'),
      });
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

    setSelectedDate(newDate);
    setSelectedTime({
      hour: String(hour).padStart(2, '0'),
      minute: String(minute).padStart(2, '0'),
    });
    
    // 날짜만 저장 (YYYY-MM-DD 형식)
    const dateString = `${newDate.getFullYear()}-${String(newDate.getMonth() + 1).padStart(2, '0')}-${String(newDate.getDate()).padStart(2, '0')}`;
    setForm(prev => ({
      ...prev,
      lostDate: dateString,
    }));
  };

  // 펫 선택 시 폼에 정보 자동 입력
  useEffect(() => {
    if (selectedPetIdx && pets.length > 0) {
      const selectedPet = pets.find(p => p.idx === selectedPetIdx);
      if (selectedPet) {
        setForm(prev => ({
          ...prev,
          petName: selectedPet.petName || prev.petName,
          species: selectedPet.petType === 'DOG' ? '개' : 
                   selectedPet.petType === 'CAT' ? '고양이' : 
                   selectedPet.petType === 'BIRD' ? '새' :
                   selectedPet.petType === 'RABBIT' ? '토끼' :
                   selectedPet.petType === 'HAMSTER' ? '햄스터' : selectedPet.petType || prev.species,
          breed: selectedPet.breed || prev.breed,
          gender: selectedPet.gender || prev.gender,
          age: selectedPet.age || prev.age,
          color: selectedPet.color || prev.color,
          imageUrl: selectedPet.profileImageUrl || prev.imageUrl,
        }));
      }
    }
  }, [selectedPetIdx, pets]);

  if (!isOpen) {
    return null;
  }

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));

    if (name === 'imageUrl') {
      setUploadError('');
    }
  };

  const handleFileSelect = async (e) => {
    const file = e.target.files?.[0];
    if (!file) {
      return;
    }

    setUploadError('');
    setIsUploading(true);

    try {
      const data = await uploadApi.uploadImage(file, {
        category: 'missing-pets',
        ownerType: currentUser ? 'user' : 'guest',
        ownerId: currentUser?.idx ?? undefined,
        entityId: initialData?.idx ?? undefined,
      });
      setForm((prev) => ({
        ...prev,
        imageUrl: data.url,
      }));
    } catch (error) {
      const message =
        error.response?.data?.error ||
        error.message ||
        '이미지 업로드 중 문제가 발생했습니다.';
      setUploadError(message);
    } finally {
      setIsUploading(false);
      if (e.target) {
        e.target.value = '';
      }
    }
  };

  const handleRemoveImage = () => {
    setForm((prev) => ({
      ...prev,
      imageUrl: '',
    }));
    setUploadError('');
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(form);
  };

  return (
    <Overlay>
      <Modal>
        <ModalHeader>
          <ModalTitle>실종 제보 등록</ModalTitle>
          <CloseButton type="button" onClick={onClose}>
            ✕
          </CloseButton>
        </ModalHeader>
        <ModalBody>
          <FormWrapper>
            <LeftSection>
              <Form onSubmit={handleSubmit}>
                <Section>
                  <SectionTitle>기본 정보</SectionTitle>
                  <FieldGrid columns={2}>
                <Field>
                  <Label>제목 *</Label>
                  <Input
                    name="title"
                    value={form.title}
                    onChange={handleChange}
                    required
                    placeholder="제보 제목을 입력하세요"
                  />
                </Field>
                <Field>
                  <Label>실종일</Label>
                  <DatePickerWrapper className="date-picker-wrapper">
                    <DateInputButton
                      ref={datePickerButtonRef}
                      type="button"
                      onClick={handleDatePickerToggle}
                      hasValue={!!form.lostDate}
                    >
                      {form.lostDate
                        ? formatDate(form.lostDate + 'T' + selectedTime.hour + ':' + selectedTime.minute)
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
                            const dayDate = new Date(day.getFullYear(), day.getMonth(), day.getDate());

                            const isToday = dayDate.getTime() === new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate()).getTime();
                            const isSelected = selectedDate &&
                              dayDate.getTime() === new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate()).getTime();
                            const isCurrentMonth = day.getMonth() === (selectedDate || new Date()).getMonth();

                            return (
                              <CalendarDay
                                key={index}
                                type="button"
                                isToday={isToday}
                                isSelected={isSelected}
                                isCurrentMonth={isCurrentMonth}
                                onClick={(e) => {
                                  e.preventDefault();
                                  e.stopPropagation();
                                  if (isCurrentMonth) {
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
                </Field>
                <Field>
                  <Label>반려동물 이름</Label>
                  <Input
                    name="petName"
                    value={form.petName}
                    onChange={handleChange}
                    placeholder="예: 초코"
                  />
                </Field>
                <Field>
                  <Label>동물 종</Label>
                  <Input
                    name="species"
                    value={form.species}
                    onChange={handleChange}
                    placeholder="예: 개, 고양이"
                  />
                </Field>
                <Field>
                  <Label>품종</Label>
                  <Input
                    name="breed"
                    value={form.breed}
                    onChange={handleChange}
                    placeholder="예: 말티즈"
                  />
                </Field>
                <Field>
                  <Label>색상</Label>
                  <Input
                    name="color"
                    value={form.color}
                    onChange={handleChange}
                    placeholder="예: 크림색"
                  />
                </Field>
                <Field>
                  <Label>성별</Label>
                  <Select name="gender" value={form.gender} onChange={handleChange}>
                    <option value="">선택</option>
                    <option value="M">수컷</option>
                    <option value="F">암컷</option>
                  </Select>
                </Field>
                <Field>
                  <Label>나이</Label>
                  <Input
                    name="age"
                    value={form.age}
                    onChange={handleChange}
                    placeholder="예: 3살 추정"
                  />
                </Field>
              </FieldGrid>
            </Section>

            <Section>
              <SectionTitle>실종 위치</SectionTitle>
              <FieldGrid columns={1}>
                <Field>
                  <Label>실종 위치 (지도에서 선택)</Label>
                  <AddressMapSelector
                    onAddressSelect={(location) => {
                      setForm((prev) => ({
                        ...prev,
                        lostLocation: location.address,
                        latitude: location.latitude,
                        longitude: location.longitude,
                      }));
                    }}
                    initialAddress={form.lostLocation}
                    initialLat={form.latitude}
                    initialLng={form.longitude}
                  />
                </Field>
                <Field>
                  <Label>대표 이미지</Label>
                  <UploadControls>
                    <HiddenFileInput
                      id="missing-pet-image-upload"
                      type="file"
                      accept="image/*"
                      onChange={handleFileSelect}
                    />
                    <UploadButtonRow>
                      <FileSelectButton htmlFor="missing-pet-image-upload" $disabled={isUploading}>
                        {isUploading ? '업로드 중...' : '이미지 선택'}
                      </FileSelectButton>
                      {form.imageUrl && (
                        <ClearImageButton type="button" onClick={handleRemoveImage}>
                          이미지 삭제
                        </ClearImageButton>
                      )}
                    </UploadButtonRow>
                    <HelperText>
                      이미지 파일을 업로드하거나 직접 링크를 입력할 수 있어요. (JPG, PNG 등)
                    </HelperText>
                    {uploadError && <ErrorText>{uploadError}</ErrorText>}
                    <ManualUrlInput
                      type="url"
                      name="imageUrl"
                      value={form.imageUrl}
                      onChange={handleChange}
                      placeholder="직접 이미지 링크 입력 (선택 사항)"
                    />
                  </UploadControls>
                  {form.imageUrl && (
                    <ImagePreview>
                      <PreviewImage src={form.imageUrl} alt="대표 이미지 미리보기" />
                    </ImagePreview>
                  )}
                </Field>
              </FieldGrid>
            </Section>

            <Section>
              <SectionTitle>상세 설명 *</SectionTitle>
              <Textarea
                name="content"
                value={form.content}
                onChange={handleChange}
                placeholder="실종 당시 상황, 특징, 목격 정보 등 상세 내용을 작성해주세요."
                required
                rows={6}
              />
            </Section>

                <ButtonRow>
                  <SecondaryButton type="button" onClick={onClose}>
                    취소
                  </SecondaryButton>
                  <PrimaryButton type="submit" disabled={loading}>
                    {loading ? '등록 중...' : '등록'}
                  </PrimaryButton>
                </ButtonRow>
              </Form>
            </LeftSection>

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

                  {selectedPetIdx && pets.find(p => p.idx === selectedPetIdx) && (
                    <PetInfoCard>
                      {(() => {
                        const selectedPet = pets.find(p => p.idx === selectedPetIdx);
                        return (
                          <>
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
                            </PetDetails>
                          </>
                        );
                      })()}
                    </PetInfoCard>
                  )}
                </>
              )}
            </RightCard>
          </FormWrapper>
        </ModalBody>
      </Modal>
    </Overlay>
  );
};

export default MissingPetBoardForm;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: ${(props) => props.theme.colors.overlay};
  display: flex;
  align-items: flex-start;
  justify-content: center;
  overflow-y: auto;
  z-index: 1000;
  padding: ${(props) => props.theme.spacing['5xl']} ${(props) => props.theme.spacing.lg};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.lg};
  }
`;

const Modal = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  max-width: 880px;
  width: 100%;
  box-shadow: ${(props) => props.theme.shadows.xl};

  @media (max-width: 768px) {
    max-width: 100%;
    border-radius: ${(props) => props.theme.borderRadius.lg};
  }
`;

const ModalHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
  }
`;

const ModalTitle = styled.h2`
  margin: 0;
  font-size: ${(props) => props.theme.typography.h2.fontSize};
`;

const CloseButton = styled.button`
  border: none;
  background: transparent;
  font-size: ${(props) => props.theme.typography.h2.fontSize};
  cursor: pointer;
  color: ${(props) => props.theme.colors.textSecondary};

  &:hover {
    color: ${(props) => props.theme.colors.text};
  }
`;

const ModalBody = styled.div`
  padding: ${(props) => props.theme.spacing.xl};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
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

const LeftSection = styled.div`
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
  font-size: ${(props) => props.theme.typography.h4.fontSize};
  color: ${(props) => props.theme.colors.text};
`;

const PetSelect = styled.select`
  width: 100%;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  margin-bottom: ${(props) => props.theme.spacing.md};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: ${(props) => props.theme.shadows.focus};
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
  font-size: ${(props) => props.theme.typography.body2.fontSize};
`;

const PetDetails = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const PetName = styled.div`
  font-size: ${(props) => props.theme.typography.h4.fontSize};
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const PetDetail = styled.div`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
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
  gap: ${(props) => props.theme.spacing.xl};
`;

const Section = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.lg};
`;

const SectionTitle = styled.h3`
  margin: 0;
  font-size: ${(props) => props.theme.typography.h3.fontSize};
  color: ${(props) => props.theme.colors.text};
`;

const FieldGrid = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'columns',
})`
  display: grid;
  grid-template-columns: repeat(${(props) => props.columns || 1}, minmax(0, 1fr));
  gap: ${(props) => props.theme.spacing.lg};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const Label = styled.label`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const Input = styled.input`
  width: 100%;
  padding: ${(props) => props.theme.spacing.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const Select = styled.select`
  width: 100%;
  padding: ${(props) => props.theme.spacing.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const Textarea = styled.textarea`
  width: 100%;
  padding: ${(props) => props.theme.spacing.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};
  font-family: inherit;
  resize: vertical;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const UploadControls = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const UploadButtonRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${(props) => props.theme.spacing.sm};
`;

const HiddenFileInput = styled.input`
  display: none;
`;

const FileSelectButton = styled.label.withConfig({
  shouldForwardProp: (prop) => prop !== '$disabled',
})`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
  font-weight: 600;
  cursor: ${(props) => (props.$disabled ? 'not-allowed' : 'pointer')};
  opacity: ${(props) => (props.$disabled ? 0.6 : 1)};
  pointer-events: ${(props) => (props.$disabled ? 'none' : 'auto')};
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.primaryDark};
  }
`;

const ClearImageButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${(props) => props.theme.colors.error};
    color: ${(props) => props.theme.colors.error};
  }
`;

const HelperText = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorText = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.error};
`;

const ManualUrlInput = styled(Input)`
  width: 100%;
`;

const ImagePreview = styled.div`
  margin-top: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  max-width: 320px;
`;

const PreviewImage = styled.img`
  width: 100%;
  height: auto;
  display: block;
  object-fit: contain;
  max-height: 400px;
`;

const ButtonRow = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${(props) => props.theme.spacing.sm};
`;

const PrimaryButton = styled.button`
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
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

const SecondaryButton = styled.button`
  background: ${(props) => props.theme.colors.surfaceElevated};
  color: ${(props) => props.theme.colors.textSecondary};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: ${(props) => props.theme.colors.primary};
    border-color: ${(props) => props.theme.colors.primary};
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
`;

const CalendarIcon = styled.span`
  font-size: ${(props) => props.theme.typography.h3.fontSize};
`;

const DatePickerDropdown = styled.div`
  position: fixed;
  z-index: 2000;
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  box-shadow: ${(props) => props.theme.shadows.xl};
  padding: ${(props) => props.theme.spacing.lg};
  min-width: 320px;
  max-width: 90vw;
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
    max-width: 95vw;
    padding: ${(props) => props.theme.spacing.md};
    left: 50% !important;
    transform: translateX(-50%);
    top: 50% !important;
    margin-top: -200px;
    max-width: 90vw;
  }
`;

const CalendarContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.lg};
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
  border-radius: ${(props) => props.theme.borderRadius.md};
  cursor: pointer;
  font-size: ${(props) => props.theme.typography.h3.fontSize};
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;

  &:hover {
    background: ${(props) => props.theme.colors.primary};
    color: ${(props) => props.theme.colors.textInverse};
  }
`;

const MonthYear = styled.div`
  font-weight: 600;
  font-size: ${(props) => props.theme.typography.h4.fontSize};
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
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
  padding: ${(props) => props.theme.spacing.sm} 0;
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
    if (props.isSelected) return props.theme.colors.textInverse;
    if (!props.isCurrentMonth) return props.theme.colors.textSecondary + '60';
    return props.theme.colors.text;
  }};
  border-radius: ${(props) => props.theme.borderRadius.md};
  cursor: ${(props) => !props.isCurrentMonth ? 'not-allowed' : 'pointer'};
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  font-weight: ${(props) => (props.isToday || props.isSelected) ? '600' : '400'};
  transition: all 0.2s;
  opacity: ${(props) => !props.isCurrentMonth ? 0.5 : 1};

  &:hover:not(:disabled) {
    background: ${(props) => {
    if (props.isSelected) return props.theme.colors.primary;
    if (!props.isCurrentMonth) return 'transparent';
    return props.theme.colors.primary + '20';
  }};
    transform: ${(props) => !props.isCurrentMonth ? 'none' : 'scale(1.1)'};
  }

  &:disabled {
    cursor: not-allowed;
  }
`;

const TimeSelector = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.lg};
  padding: ${(props) => props.theme.spacing.lg};
  background: ${(props) => props.theme.colors.background};
  border-radius: ${(props) => props.theme.borderRadius.md};
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
  padding: ${(props) => props.theme.spacing.sm};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
  text-align: center;
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const TimeSeparator = styled.span`
  font-size: ${(props) => props.theme.typography.h3.fontSize};
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const DatePickerActions = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
`;

const DatePickerButton = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.xl};
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: ${(props) => props.theme.colors.primary}dd;
  }
`;

