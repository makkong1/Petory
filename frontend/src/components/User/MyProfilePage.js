import React, { useState, useEffect, useRef } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { petApiClient } from '../../api/userApi';
import { uploadApi } from '../../api/uploadApi';

const MyProfilePage = () => {
  const { user } = useAuth();
  const [pets, setPets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showPetForm, setShowPetForm] = useState(false);
  const [editingPet, setEditingPet] = useState(null);
  const [formData, setFormData] = useState({
    petName: '',
    petType: 'DOG',
    customPetType: '', // 기타 선택 시 입력할 종류
    breed: '',
    gender: 'UNKNOWN',
    age: '',
    color: '',
    weight: '',
    birthDate: '',
    isNeutered: false,
    healthInfo: '',
    specialNotes: '',
    profileImageUrl: '',
  });
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [selectedDate, setSelectedDate] = useState(null);
  const [datePickerPosition, setDatePickerPosition] = useState({ top: 0, left: 0 });
  const datePickerButtonRef = useRef(null);

  useEffect(() => {
    if (user) {
      fetchPets();
    }
  }, [user]);

  // 날짜/시간 초기화
  useEffect(() => {
    if (formData.birthDate) {
      const date = new Date(formData.birthDate);
      setSelectedDate(date);
    } else {
      setSelectedDate(null);
    }
  }, [formData.birthDate]);

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

  const fetchPets = async () => {
    try {
      setLoading(true);
      const response = await petApiClient.getMyPets();
      setPets(response.data || []);
    } catch (err) {
      console.error('펫 목록 조회 실패:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
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
        category: 'pets',
        ownerType: 'user',
        ownerId: user?.idx,
        entityId: editingPet?.idx,
      });
      setFormData(prev => ({
        ...prev,
        profileImageUrl: data.url,
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
    setFormData(prev => ({
      ...prev,
      profileImageUrl: '',
    }));
    setUploadError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const submitData = {
        ...formData,
        weight: formData.weight ? parseFloat(formData.weight) : null,
        birthDate: formData.birthDate || null,
        profileImageUrl: formData.profileImageUrl || null,
      };

      if (editingPet) {
        await petApiClient.updatePet(editingPet.idx, submitData);
        alert('펫 정보가 수정되었습니다.');
      } else {
        await petApiClient.createPet(submitData);
        alert('펫이 등록되었습니다.');
      }

      setShowPetForm(false);
      setEditingPet(null);
      setFormData({
        petName: '',
        petType: 'DOG',
        customPetType: '',
        breed: '',
        gender: 'UNKNOWN',
        age: '',
        color: '',
        weight: '',
        birthDate: '',
        isNeutered: false,
        healthInfo: '',
        specialNotes: '',
        profileImageUrl: '',
      });
      fetchPets();
    } catch (err) {
      console.error('펫 저장 실패:', err);
      alert(err.response?.data?.error || '펫 정보 저장에 실패했습니다.');
    }
  };

  const handleEdit = (pet) => {
    setEditingPet(pet);
    const birthDate = pet.birthDate ? pet.birthDate.split('T')[0] : '';
    setFormData({
      petName: pet.petName || '',
      petType: pet.petType || 'DOG',
      customPetType: pet.customPetType || '',
      breed: pet.breed || '',
      gender: pet.gender || 'UNKNOWN',
      age: pet.age || '',
      color: pet.color || '',
      weight: pet.weight || '',
      birthDate: birthDate,
      isNeutered: pet.isNeutered || false,
      healthInfo: pet.healthInfo || '',
      specialNotes: pet.specialNotes || '',
      profileImageUrl: pet.profileImageUrl || '',
    });
    if (birthDate) {
      setSelectedDate(new Date(birthDate));
    } else {
      setSelectedDate(null);
    }
    setShowPetForm(true);
  };

  const handleDelete = async (petIdx) => {
    if (!window.confirm('정말 이 펫 정보를 삭제하시겠습니까?')) {
      return;
    }

    try {
      await petApiClient.deletePet(petIdx);
      alert('펫 정보가 삭제되었습니다.');
      fetchPets();
    } catch (err) {
      console.error('펫 삭제 실패:', err);
      alert(err.response?.data?.error || '펫 정보 삭제에 실패했습니다.');
    }
  };

  const handleCancel = () => {
    setShowPetForm(false);
    setEditingPet(null);
    setFormData({
      petName: '',
      petType: 'DOG',
      customPetType: '',
      breed: '',
      gender: 'UNKNOWN',
      age: '',
      color: '',
      weight: '',
      birthDate: '',
      isNeutered: false,
      healthInfo: '',
      specialNotes: '',
      profileImageUrl: '',
    });
    setSelectedDate(null);
    setShowDatePicker(false);
  };

  // 달력 버튼 위치 계산 (중앙 정렬)
  const handleDatePickerToggle = (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    if (!showDatePicker) {
      const calendarWidth = 320;
      const calendarHeight = 400;
      const gap = 8;

      const left = (window.innerWidth - calendarWidth) / 2 + window.scrollX;
      const top = (window.innerHeight - calendarHeight) / 2 + window.scrollY - 50;

      setDatePickerPosition({
        top: Math.max(gap, top),
        left: Math.max(gap, left),
      });
    }
    setShowDatePicker(!showDatePicker);
  };

  // 날짜 포맷팅
  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return '';

    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    const day = date.getDate();

    return `${year}년 ${month}월 ${day}일`;
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
    const newDate = new Date(day.getFullYear(), day.getMonth(), day.getDate());
    setSelectedDate(newDate);

    // 날짜만 저장 (YYYY-MM-DD 형식)
    const dateString = `${newDate.getFullYear()}-${String(newDate.getMonth() + 1).padStart(2, '0')}-${String(newDate.getDate()).padStart(2, '0')}`;
    setFormData(prev => ({
      ...prev,
      birthDate: dateString,
    }));
  };

  const petTypeLabels = {
    DOG: '강아지',
    CAT: '고양이',
    BIRD: '새',
    RABBIT: '토끼',
    HAMSTER: '햄스터',
    ETC: '기타',
  };

  const genderLabels = {
    M: '수컷',
    F: '암컷',
    UNKNOWN: '미확인',
  };

  if (!user) {
    return (
      <Container>
        <Message>로그인이 필요합니다.</Message>
      </Container>
    );
  }

  return (
    <Container>
      <Header>
        <Title>내 프로필</Title>
        <UserInfo>
          <UserName>{user.nickname || '사용자'}</UserName>
          <UserEmail>{user.email || ''}</UserEmail>
        </UserInfo>
      </Header>

      <PetSection>
        <SectionHeader>
          <SectionHeaderTitle>반려동물 정보</SectionHeaderTitle>
          {!showPetForm && (
            <AddButton onClick={() => setShowPetForm(true)}>
              + 펫 추가
            </AddButton>
          )}
        </SectionHeader>

        {showPetForm && (
          <PetForm onSubmit={handleSubmit}>
            <Section>
              <SectionTitle>기본 정보</SectionTitle>
              <FieldGrid columns={2}>
                <Field>
                  <Label>이름 *</Label>
                  <Input
                    type="text"
                    name="petName"
                    value={formData.petName}
                    onChange={handleInputChange}
                    required
                  />
                </Field>

                <Field>
                  <Label>종류 *</Label>
                  <Select
                    name="petType"
                    value={formData.petType}
                    onChange={handleInputChange}
                    required
                  >
                    {Object.entries(petTypeLabels).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </Select>
                  {formData.petType === 'ETC' && (
                    <Input
                      type="text"
                      name="customPetType"
                      value={formData.customPetType}
                      onChange={handleInputChange}
                      placeholder="어떤 펫인지 입력해주세요 (예: 햄스터, 토끼 등)"
                      style={{ marginTop: '0.5rem' }}
                      required
                    />
                  )}
                </Field>

                <Field>
                  <Label>품종</Label>
                  <Input
                    type="text"
                    name="breed"
                    value={formData.breed}
                    onChange={handleInputChange}
                    placeholder="예: 골든 리트리버"
                  />
                </Field>

                <Field>
                  <Label>성별</Label>
                  <Select
                    name="gender"
                    value={formData.gender}
                    onChange={handleInputChange}
                  >
                    {Object.entries(genderLabels).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </Select>
                </Field>

                <Field>
                  <Label>나이</Label>
                  <Input
                    type="text"
                    name="age"
                    value={formData.age}
                    onChange={handleInputChange}
                    placeholder="예: 3살"
                  />
                </Field>

                <Field>
                  <Label>체중 (kg)</Label>
                  <Input
                    type="number"
                    step="0.1"
                    name="weight"
                    value={formData.weight}
                    onChange={handleInputChange}
                    placeholder="예: 5.5"
                  />
                </Field>

                <Field>
                  <Label>색상</Label>
                  <Input
                    type="text"
                    name="color"
                    value={formData.color}
                    onChange={handleInputChange}
                    placeholder="예: 갈색"
                  />
                </Field>

                <Field>
                  <Label>생년월일</Label>
                  <DatePickerWrapper className="date-picker-wrapper">
                    <DateInputButton
                      ref={datePickerButtonRef}
                      type="button"
                      onClick={handleDatePickerToggle}
                      hasValue={!!formData.birthDate}
                    >
                      {formData.birthDate
                        ? formatDate(formData.birthDate)
                        : '날짜를 선택해주세요'}
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
                        <DatePickerActions>
                          <DatePickerButton onClick={() => setShowDatePicker(false)}>
                            확인
                          </DatePickerButton>
                        </DatePickerActions>
                      </CalendarContainer>
                    </DatePickerDropdown>
                  )}
                </Field>
              </FieldGrid>
            </Section>

            <Section>
              <SectionTitle>추가 정보</SectionTitle>
              <FieldGrid columns={1}>
                <Field>
                  <Label>펫 프로필 이미지</Label>
                  <UploadControls>
                    <HiddenFileInput
                      id="pet-image-upload"
                      type="file"
                      accept="image/*"
                      onChange={handleFileSelect}
                    />
                    <UploadButtonRow>
                      <FileSelectButton htmlFor="pet-image-upload" $disabled={isUploading}>
                        {isUploading ? '업로드 중...' : '이미지 선택'}
                      </FileSelectButton>
                      {formData.profileImageUrl && (
                        <ClearImageButton type="button" onClick={handleRemoveImage}>
                          이미지 삭제
                        </ClearImageButton>
                      )}
                    </UploadButtonRow>
                    {uploadError && <ErrorText>{uploadError}</ErrorText>}
                    {formData.profileImageUrl && (
                      <ImagePreview>
                        <PreviewImage src={formData.profileImageUrl} alt="펫 프로필 이미지 미리보기" />
                      </ImagePreview>
                    )}
                  </UploadControls>
                </Field>

                <Field>
                  <CheckboxGroup>
                    <Checkbox
                      type="checkbox"
                      name="isNeutered"
                      checked={formData.isNeutered}
                      onChange={handleInputChange}
                    />
                    <Label>중성화 여부</Label>
                  </CheckboxGroup>
                </Field>

                <Field>
                  <Label>건강 정보</Label>
                  <Textarea
                    name="healthInfo"
                    value={formData.healthInfo}
                    onChange={handleInputChange}
                    placeholder="알레르기, 특별한 건강 상태 등을 입력하세요"
                    rows={3}
                  />
                </Field>

                <Field>
                  <Label>특이사항</Label>
                  <Textarea
                    name="specialNotes"
                    value={formData.specialNotes}
                    onChange={handleInputChange}
                    placeholder="특별한 주의사항이나 특징을 입력하세요"
                    rows={3}
                  />
                </Field>
              </FieldGrid>
            </Section>

            <ButtonRow>
              <SecondaryButton type="button" onClick={handleCancel}>취소</SecondaryButton>
              <PrimaryButton type="submit">{editingPet ? '수정' : '등록'}</PrimaryButton>
            </ButtonRow>
          </PetForm>
        )}

        {loading ? (
          <LoadingMessage>펫 목록을 불러오는 중...</LoadingMessage>
        ) : pets.length === 0 ? (
          <EmptyMessage>
            등록된 펫이 없습니다. 위의 "펫 추가" 버튼을 눌러 펫을 등록해주세요.
          </EmptyMessage>
        ) : (
          <PetList>
            {pets.map((pet) => (
              <PetCard key={pet.idx}>
                {pet.profileImageUrl && (
                  <PetImageWrapper>
                    <PetImage src={pet.profileImageUrl} alt={pet.petName || '펫 이미지'} />
                  </PetImageWrapper>
                )}
                <PetHeader>
                  <PetName>{pet.petName || '이름 없음'}</PetName>
                  <PetTypeBadge>
                    {pet.petType === 'ETC' && pet.customPetType
                      ? pet.customPetType
                      : petTypeLabels[pet.petType] || pet.petType}
                  </PetTypeBadge>
                </PetHeader>
                <PetInfo>
                  {pet.breed && <InfoItem>품종: {pet.breed}</InfoItem>}
                  {pet.gender && <InfoItem>성별: {genderLabels[pet.gender]}</InfoItem>}
                  {pet.age && <InfoItem>나이: {pet.age}</InfoItem>}
                  {pet.color && <InfoItem>색상: {pet.color}</InfoItem>}
                  {pet.weight && <InfoItem>체중: {pet.weight}kg</InfoItem>}
                  {pet.birthDate && (
                    <InfoItem>
                      생년월일: {new Date(pet.birthDate).toLocaleDateString('ko-KR')}
                    </InfoItem>
                  )}
                  {pet.isNeutered && <InfoItem>중성화 완료</InfoItem>}
                  {pet.healthInfo && <InfoItem>건강 정보: {pet.healthInfo}</InfoItem>}
                  {pet.specialNotes && <InfoItem>특이사항: {pet.specialNotes}</InfoItem>}
                </PetInfo>
                <PetActions>
                  <EditButton onClick={() => handleEdit(pet)}>수정</EditButton>
                  <DeleteButton onClick={() => handleDelete(pet.idx)}>삭제</DeleteButton>
                </PetActions>
              </PetCard>
            ))}
          </PetList>
        )}
      </PetSection>
    </Container>
  );
};

export default MyProfilePage;

const Container = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: ${(props) => props.theme.spacing.xl};
`;

const Header = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.xl};
  padding-bottom: ${(props) => props.theme.spacing.lg};
  border-bottom: 2px solid ${(props) => props.theme.colors.border};
`;

const Title = styled.h1`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  color: ${(props) => props.theme.colors.text};
`;

const UserInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const UserName = styled.div`
  font-size: 1.2rem;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const UserEmail = styled.div`
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const PetSection = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.xl};
`;

const SectionHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const SectionHeaderTitle = styled.h2`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
`;

const Section = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.lg};
`;

const SectionTitle = styled.h3`
  margin: 0;
  font-size: 1.2rem;
  color: ${(props) => props.theme.colors.text};
`;

const AddButton = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.primaryHover || props.theme.colors.primary};
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
  }
`;

const PetForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xl};
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
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const Input = styled.input`
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
`;

const Select = styled.select`
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
`;

const Textarea = styled.textarea`
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
`;

const CheckboxGroup = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
`;

const Checkbox = styled.input`
  width: 18px;
  height: 18px;
  cursor: pointer;
`;

const ButtonRow = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${(props) => props.theme.spacing.sm};
`;

const PrimaryButton = styled.button`
  background: ${(props) => props.theme.colors.primary};
  color: #ffffff;
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

const PetList = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: ${(props) => props.theme.spacing.lg};
`;

const PetCard = styled.div`
  background: ${(props) => props.theme.colors.surfaceElevated};
  padding: ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  display: flex;
  flex-direction: column;
`;

const PetImageWrapper = styled.div`
  width: 100%;
  aspect-ratio: 1;
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  background: ${(props) => props.theme.colors.borderLight || '#e1e5e9'};
  margin-bottom: ${(props) => props.theme.spacing.md};
  display: flex;
  align-items: center;
  justify-content: center;
`;

const PetImage = styled.img`
  width: 100%;
  height: 100%;
  object-fit: cover;
`;

const PetHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${(props) => props.theme.spacing.md};
  padding-bottom: ${(props) => props.theme.spacing.md};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
`;

const PetName = styled.h3`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
`;

const PetTypeBadge = styled.span`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  border-radius: ${(props) => props.theme.borderRadius.full};
  font-size: 0.85rem;
  font-weight: 500;
`;

const PetInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
  margin-bottom: ${(props) => props.theme.spacing.md};
`;

const InfoItem = styled.div`
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const PetActions = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
  padding-top: ${(props) => props.theme.spacing.md};
  border-top: 1px solid ${(props) => props.theme.colors.border};
`;

const EditButton = styled.button`
  flex: 1;
  padding: ${(props) => props.theme.spacing.sm};
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.text};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.sm};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.surfaceHover};
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const DeleteButton = styled(EditButton)`
  color: ${(props) => props.theme.colors.error || '#dc2626'};
  border-color: ${(props) => props.theme.colors.error || '#dc2626'};

  &:hover {
    background: ${(props) => props.theme.colors.error || '#dc2626'};
    color: white;
  }
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const EmptyMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px dashed ${(props) => props.theme.colors.border};
`;

const Message = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const UploadControls = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const UploadButtonRow = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
  flex-wrap: wrap;
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
  color: #ffffff;
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
    border-color: ${(props) => props.theme.colors.error || '#e11d48'};
    color: ${(props) => props.theme.colors.error || '#e11d48'};
  }
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

const ErrorText = styled.span`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.error || '#e11d48'};
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
  font-size: 1.2rem;
`;

const DatePickerDropdown = styled.div`
  position: fixed;
  z-index: 2000;
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
  padding: 1rem;
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
    padding: 0.75rem;
    left: 50% !important;
    transform: translateX(-50%);
    top: 50% !important;
    margin-top: -200px;
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
    return props.theme.colors.text;
  }};
  border-radius: 6px;
  cursor: ${(props) => !props.isCurrentMonth ? 'not-allowed' : 'pointer'};
  font-size: 0.9rem;
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

