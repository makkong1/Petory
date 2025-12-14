import React, { useState, useEffect, useRef } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { userProfileApi, petApiClient } from '../../api/userApi';
import { uploadApi } from '../../api/uploadApi';

const UserProfileModal = ({ isOpen, userId, onClose, onUpdated }) => {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [isEditMode, setIsEditMode] = useState(false);
  const [editFormData, setEditFormData] = useState({
    nickname: '',
    email: '',
    phone: '',
    location: '',
  });
  const [saving, setSaving] = useState(false);

  // 반려동물 관련 상태
  const [pets, setPets] = useState([]);
  const [petsLoading, setPetsLoading] = useState(false);
  const [showPetForm, setShowPetForm] = useState(false);
  const [editingPet, setEditingPet] = useState(null);
  const [petFormData, setPetFormData] = useState({
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
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [selectedDate, setSelectedDate] = useState(null);
  const [datePickerPosition, setDatePickerPosition] = useState({ top: 0, left: 0 });
  const datePickerButtonRef = useRef(null);

  const isMyProfile = user && (user.idx === userId || user.id === userId);

  useEffect(() => {
    if (isOpen && userId) {
      fetchProfile();
      if (isMyProfile) {
        fetchPets();
      }
    } else {
      setProfile(null);
      setError('');
      setIsEditMode(false);
      setPets([]);
    }
  }, [isOpen, userId, isMyProfile]);

  const fetchProfile = async () => {
    try {
      setLoading(true);
      setError('');
      if (isMyProfile) {
        // 내 프로필인 경우 getMyProfile 사용
        const response = await userProfileApi.getMyProfile();
        setProfile({ user: response.data });
        setEditFormData({
          nickname: response.data.nickname || '',
          email: response.data.email || '',
          phone: response.data.phone || '',
          location: response.data.location || '',
        });
      } else {
        // 다른 사용자 프로필인 경우 getUserProfile 사용
      const response = await userProfileApi.getUserProfile(userId);
      setProfile(response.data);
      }
    } catch (err) {
      const message = err.response?.data?.error || err.message || '프로필을 불러오는데 실패했습니다.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const fetchPets = async () => {
    try {
      setPetsLoading(true);
      const response = await petApiClient.getMyPets();
      setPets(response.data || []);
    } catch (err) {
      console.error('펫 목록 조회 실패:', err);
    } finally {
      setPetsLoading(false);
    }
  };

  const handleEditProfile = async () => {
    try {
      setSaving(true);
      const updated = await userProfileApi.updateMyProfile(editFormData);
      setProfile({ user: updated });
      setIsEditMode(false);
      if (onUpdated) {
        onUpdated(updated);
      }
    } catch (err) {
      alert(err.response?.data?.error || '프로필 수정에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  // 반려동물 관련 핸들러들
  const handlePetInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setPetFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handlePetFileSelect = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploadError('');
    setIsUploading(true);

    try {
      const data = await uploadApi.uploadImage(file, {
        category: 'pets',
        ownerType: 'user',
        ownerId: user?.idx,
        entityId: editingPet?.idx,
      });
      setPetFormData(prev => ({
        ...prev,
        profileImageUrl: data.url,
      }));
    } catch (error) {
      setUploadError(error.response?.data?.error || error.message || '이미지 업로드 중 문제가 발생했습니다.');
    } finally {
      setIsUploading(false);
      if (e.target) {
        e.target.value = '';
      }
    }
  };

  const handlePetSubmit = async (e) => {
    e.preventDefault();
    try {
      const submitData = {
        ...petFormData,
        weight: petFormData.weight ? parseFloat(petFormData.weight) : null,
        birthDate: petFormData.birthDate || null,
        profileImageUrl: petFormData.profileImageUrl || null,
      };

      if (editingPet) {
        await petApiClient.updatePet(editingPet.idx, submitData);
      } else {
        await petApiClient.createPet(submitData);
      }

      setShowPetForm(false);
      setEditingPet(null);
      setPetFormData({
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
      alert(err.response?.data?.error || '펫 정보 저장에 실패했습니다.');
    }
  };

  const handlePetEdit = (pet) => {
    setEditingPet(pet);
    const birthDate = pet.birthDate ? pet.birthDate.split('T')[0] : '';
    setPetFormData({
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
    setShowPetForm(true);
  };

  const handlePetDelete = async (petIdx) => {
    if (!window.confirm('정말 이 펫 정보를 삭제하시겠습니까?')) {
      return;
    }

    try {
      await petApiClient.deletePet(petIdx);
      fetchPets();
    } catch (err) {
      alert(err.response?.data?.error || '펫 정보 삭제에 실패했습니다.');
    }
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

  if (!isOpen) return null;

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <Backdrop onClick={handleBackdropClick}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <ModalTitle>{isMyProfile ? '내 프로필' : '프로필'}</ModalTitle>
          <CloseButton onClick={onClose}>✕</CloseButton>
        </ModalHeader>

        <ModalContent>
          {loading ? (
            <LoadingMessage>프로필을 불러오는 중...</LoadingMessage>
          ) : error ? (
            <ErrorMessage>{error}</ErrorMessage>
          ) : profile ? (
            <>
              <UserInfoSection>
                <UserAvatar>
                  {profile.user?.username ? profile.user.username.charAt(0).toUpperCase() : 'U'}
                </UserAvatar>
                {isEditMode ? (
                  <EditForm>
                    <FormField>
                      <Label>닉네임</Label>
                      <Input
                        type="text"
                        value={editFormData.nickname}
                        onChange={(e) => setEditFormData(prev => ({ ...prev, nickname: e.target.value }))}
                      />
                    </FormField>
                    <FormField>
                      <Label>이메일</Label>
                      <Input
                        type="email"
                        value={editFormData.email}
                        onChange={(e) => setEditFormData(prev => ({ ...prev, email: e.target.value }))}
                      />
                    </FormField>
                    <FormField>
                      <Label>전화번호</Label>
                      <Input
                        type="tel"
                        value={editFormData.phone}
                        onChange={(e) => setEditFormData(prev => ({ ...prev, phone: e.target.value }))}
                        placeholder="010-1234-5678"
                      />
                    </FormField>
                    <FormField>
                      <Label>위치</Label>
                      <Input
                        type="text"
                        value={editFormData.location}
                        onChange={(e) => setEditFormData(prev => ({ ...prev, location: e.target.value }))}
                        placeholder="예: 서울시 강남구"
                      />
                    </FormField>
                    <ButtonRow>
                      <SecondaryButton onClick={() => setIsEditMode(false)}>취소</SecondaryButton>
                      <PrimaryButton onClick={handleEditProfile} disabled={saving}>
                        {saving ? '저장 중...' : '저장'}
                      </PrimaryButton>
                    </ButtonRow>
                  </EditForm>
                ) : (
                  <>
                    <UserName>{profile.user?.nickname || profile.user?.username || '알 수 없음'}</UserName>
                    {profile.user?.email && (
                      <UserEmail>{profile.user.email}</UserEmail>
                    )}
                    {profile.user?.phone && (
                      <UserPhone>📞 {profile.user.phone}</UserPhone>
                    )}
                {profile.user?.location && (
                  <UserLocation>
                    <LocationIcon>📍</LocationIcon>
                    {profile.user.location}
                  </UserLocation>
                )}
                {profile.user?.role && (
                  <UserRole>
                    {profile.user.role === 'SERVICE_PROVIDER' ? '서비스 제공자' : '일반 사용자'}
                  </UserRole>
                    )}
                    {isMyProfile && (
                      <EditButton onClick={() => setIsEditMode(true)}>프로필 수정</EditButton>
                    )}
                  </>
                )}
              </UserInfoSection>

              {isMyProfile && (
                <PetsSection>
                  <SectionHeader>
                    <SectionTitle>반려동물 정보</SectionTitle>
                    {!showPetForm && (
                      <AddButton onClick={() => setShowPetForm(true)}>+ 펫 추가</AddButton>
                    )}
                  </SectionHeader>

                  {showPetForm && (
                    <PetForm onSubmit={handlePetSubmit}>
                      <FormField>
                        <Label>이름 *</Label>
                        <Input
                          type="text"
                          name="petName"
                          value={petFormData.petName}
                          onChange={handlePetInputChange}
                          required
                        />
                      </FormField>
                      <FormField>
                        <Label>종류 *</Label>
                        <Select
                          name="petType"
                          value={petFormData.petType}
                          onChange={handlePetInputChange}
                          required
                        >
                          {Object.entries(petTypeLabels).map(([value, label]) => (
                            <option key={value} value={value}>{label}</option>
                          ))}
                        </Select>
                        {petFormData.petType === 'ETC' && (
                          <Input
                            type="text"
                            name="customPetType"
                            value={petFormData.customPetType}
                            onChange={handlePetInputChange}
                            placeholder="어떤 펫인지 입력해주세요"
                            style={{ marginTop: '0.5rem' }}
                            required
                          />
                        )}
                      </FormField>
                      <FormField>
                        <Label>품종</Label>
                        <Input
                          type="text"
                          name="breed"
                          value={petFormData.breed}
                          onChange={handlePetInputChange}
                          placeholder="예: 골든 리트리버"
                        />
                      </FormField>
                      <FormField>
                        <Label>성별</Label>
                        <Select
                          name="gender"
                          value={petFormData.gender}
                          onChange={handlePetInputChange}
                        >
                          {Object.entries(genderLabels).map(([value, label]) => (
                            <option key={value} value={value}>{label}</option>
                          ))}
                        </Select>
                      </FormField>
                      <FormField>
                        <Label>나이</Label>
                        <Input
                          type="text"
                          name="age"
                          value={petFormData.age}
                          onChange={handlePetInputChange}
                          placeholder="예: 3살"
                        />
                      </FormField>
                      <FormField>
                        <Label>체중 (kg)</Label>
                        <Input
                          type="number"
                          step="0.1"
                          name="weight"
                          value={petFormData.weight}
                          onChange={handlePetInputChange}
                          placeholder="예: 5.5"
                        />
                      </FormField>
                      <FormField>
                        <Label>색상</Label>
                        <Input
                          type="text"
                          name="color"
                          value={petFormData.color}
                          onChange={handlePetInputChange}
                          placeholder="예: 갈색"
                        />
                      </FormField>
                      <FormField>
                        <Label>생년월일</Label>
                        <Input
                          type="date"
                          name="birthDate"
                          value={petFormData.birthDate}
                          onChange={handlePetInputChange}
                        />
                      </FormField>
                      <FormField>
                        <Label>펫 프로필 이미지</Label>
                        <HiddenFileInput
                          id="pet-image-upload"
                          type="file"
                          accept="image/*"
                          onChange={handlePetFileSelect}
                        />
                        <FileSelectButton htmlFor="pet-image-upload" $disabled={isUploading}>
                          {isUploading ? '업로드 중...' : '이미지 선택'}
                        </FileSelectButton>
                        {uploadError && <ErrorText>{uploadError}</ErrorText>}
                        {petFormData.profileImageUrl && (
                          <ImagePreview>
                            <PreviewImage src={petFormData.profileImageUrl} alt="펫 프로필 이미지" />
                            <RemoveImageButton type="button" onClick={() => setPetFormData(prev => ({ ...prev, profileImageUrl: '' }))}>
                              이미지 삭제
                            </RemoveImageButton>
                          </ImagePreview>
                        )}
                      </FormField>
                      <FormField>
                        <CheckboxGroup>
                          <Checkbox
                            type="checkbox"
                            name="isNeutered"
                            checked={petFormData.isNeutered}
                            onChange={handlePetInputChange}
                          />
                          <Label>중성화 여부</Label>
                        </CheckboxGroup>
                      </FormField>
                      <FormField>
                        <Label>건강 정보</Label>
                        <Textarea
                          name="healthInfo"
                          value={petFormData.healthInfo}
                          onChange={handlePetInputChange}
                          placeholder="알레르기, 특별한 건강 상태 등을 입력하세요"
                          rows={3}
                        />
                      </FormField>
                      <FormField>
                        <Label>특이사항</Label>
                        <Textarea
                          name="specialNotes"
                          value={petFormData.specialNotes}
                          onChange={handlePetInputChange}
                          placeholder="특별한 주의사항이나 특징을 입력하세요"
                          rows={3}
                        />
                      </FormField>
                      <ButtonRow>
                        <SecondaryButton type="button" onClick={() => {
                          setShowPetForm(false);
                          setEditingPet(null);
                          setPetFormData({
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
                        }}>취소</SecondaryButton>
                        <PrimaryButton type="submit">{editingPet ? '수정' : '등록'}</PrimaryButton>
                      </ButtonRow>
                    </PetForm>
                  )}

                  {petsLoading ? (
                    <LoadingMessage>펫 목록을 불러오는 중...</LoadingMessage>
                  ) : pets.length === 0 ? (
                    <EmptyMessage>
                      등록된 펫이 없습니다. "펫 추가" 버튼을 눌러 펫을 등록해주세요.
                    </EmptyMessage>
                  ) : (
                    <PetList>
                      {pets.map((pet) => (
                        <PetCard key={pet.idx}>
                          {pet.profileImageUrl && (
                            <PetImage src={pet.profileImageUrl} alt={pet.petName || '펫 이미지'} />
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
                            <EditButton onClick={() => handlePetEdit(pet)}>수정</EditButton>
                            <DeleteButton onClick={() => handlePetDelete(pet.idx)}>삭제</DeleteButton>
                          </PetActions>
                        </PetCard>
                      ))}
                    </PetList>
                  )}
                </PetsSection>
              )}

              {profile.user?.role === 'SERVICE_PROVIDER' && (
                <ReviewSummarySection>
                  <ReviewSummaryTitle>펫케어 리뷰</ReviewSummaryTitle>
                  <ReviewStats>
                    <StatItem>
                      <StatLabel>평균 평점</StatLabel>
                      <StatValue>
                        {profile.averageRating ? profile.averageRating.toFixed(1) : '-'}
                        {profile.averageRating && <StarIcon>⭐</StarIcon>}
                      </StatValue>
                    </StatItem>
                    <StatItem>
                      <StatLabel>리뷰 개수</StatLabel>
                      <StatValue>{profile.reviewCount || 0}개</StatValue>
                    </StatItem>
                  </ReviewStats>
                </ReviewSummarySection>
              )}

              {profile.reviews && profile.reviews.length > 0 && (
                <ReviewsSection>
                  <ReviewsTitle>리뷰 목록</ReviewsTitle>
                  <ReviewList>
                    {profile.reviews.map((review) => (
                      <ReviewItem key={review.idx}>
                        <ReviewHeader>
                          <ReviewerName>{review.reviewerName || '알 수 없음'}</ReviewerName>
                          <ReviewRating>
                            {'⭐'.repeat(review.rating)}
                            <RatingNumber>{review.rating}</RatingNumber>
                          </ReviewRating>
                        </ReviewHeader>
                        {review.comment && (
                          <ReviewComment>{review.comment}</ReviewComment>
                        )}
                        <ReviewDate>
                          {review.createdAt
                            ? new Date(review.createdAt).toLocaleDateString('ko-KR')
                            : ''}
                        </ReviewDate>
                      </ReviewItem>
                    ))}
                  </ReviewList>
                </ReviewsSection>
              )}

              {profile.user?.role === 'SERVICE_PROVIDER' && (!profile.reviews || profile.reviews.length === 0) && (
                <EmptyReviewsMessage>아직 리뷰가 없습니다.</EmptyReviewsMessage>
              )}
            </>
          ) : null}
        </ModalContent>
      </ModalContainer>
    </Backdrop>
  );
};

export default UserProfileModal;

const Backdrop = styled.div`
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
  padding: ${(props) => props.theme.spacing.lg};
`;

const ModalContainer = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  max-width: 800px;
  width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${(props) => props.theme.spacing.lg};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
  position: sticky;
  top: 0;
  background: ${(props) => props.theme.colors.surface};
  z-index: 10;
`;

const ModalTitle = styled.h2`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
  font-size: 1.5rem;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 1.5rem;
  color: ${(props) => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: ${(props) => props.theme.borderRadius.md};

  &:hover {
    background: ${(props) => props.theme.colors.surfaceHover};
    color: ${(props) => props.theme.colors.text};
  }
`;

const ModalContent = styled.div`
  padding: ${(props) => props.theme.spacing.lg};
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.error || '#dc2626'};
`;

const UserInfoSection = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  padding-bottom: ${(props) => props.theme.spacing.lg};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const UserAvatar = styled.div`
  width: 80px;
  height: 80px;
  border-radius: ${(props) => props.theme.borderRadius.full};
  background: ${(props) => props.theme.colors.primary};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 2rem;
  font-weight: 600;
`;

const UserName = styled.div`
  font-size: 1.5rem;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const UserEmail = styled.div`
  font-size: 0.95rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const UserPhone = styled.div`
  font-size: 0.95rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const UserLocation = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.95rem;
`;

const LocationIcon = styled.span`
  font-size: 0.9rem;
`;

const UserRole = styled.div`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  border-radius: ${(props) => props.theme.borderRadius.full};
  font-size: 0.9rem;
  font-weight: 500;
`;

const EditButton = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: ${(props) => props.theme.spacing.sm};

  &:hover {
    background: ${(props) => props.theme.colors.primaryDark};
    transform: translateY(-2px);
  }
`;

const EditForm = styled.div`
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const FormField = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const Label = styled.label`
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 500;
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

const ButtonRow = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${(props) => props.theme.spacing.sm};
  margin-top: ${(props) => props.theme.spacing.md};
`;

const PrimaryButton = styled.button`
  background: ${(props) => props.theme.colors.primary};
  color: white;
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

const PetsSection = styled.div`
  margin-top: ${(props) => props.theme.spacing.xl};
  padding-top: ${(props) => props.theme.spacing.xl};
  border-top: 1px solid ${(props) => props.theme.colors.border};
`;

const SectionHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const SectionTitle = styled.h3`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
  font-size: 1.2rem;
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
    background: ${(props) => props.theme.colors.primaryDark};
    transform: translateY(-2px);
  }
`;

const PetForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
  padding: ${(props) => props.theme.spacing.lg};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.md};
  margin-bottom: ${(props) => props.theme.spacing.lg};
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

const HiddenFileInput = styled.input`
  display: none;
`;

const FileSelectButton = styled.label`
  display: inline-block;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  background: ${(props) => props.$disabled ? props.theme.colors.border : props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-weight: 600;
  cursor: ${(props) => props.$disabled ? 'not-allowed' : 'pointer'};
  opacity: ${(props) => props.$disabled ? 0.6 : 1};
  transition: all 0.2s ease;

  &:hover:not([disabled]) {
    background: ${(props) => props.theme.colors.primaryDark};
  }
`;

const ErrorText = styled.div`
  color: ${(props) => props.theme.colors.error || '#dc2626'};
  font-size: 0.875rem;
  margin-top: ${(props) => props.theme.spacing.xs};
`;

const ImagePreview = styled.div`
  margin-top: ${(props) => props.theme.spacing.md};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const PreviewImage = styled.img`
  max-width: 200px;
  max-height: 200px;
  border-radius: ${(props) => props.theme.borderRadius.md};
  object-fit: cover;
`;

const RemoveImageButton = styled.button`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  background: ${(props) => props.theme.colors.error || '#dc2626'};
  color: white;
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    opacity: 0.8;
  }
`;

const EmptyMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
  font-style: italic;
`;

const PetList = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: ${(props) => props.theme.spacing.md};
`;

const PetCard = styled.div`
  background: ${(props) => props.theme.colors.surfaceElevated};
  padding: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const PetImage = styled.img`
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: ${(props) => props.theme.borderRadius.md};
  margin-bottom: ${(props) => props.theme.spacing.sm};
`;

const PetHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const PetName = styled.div`
  font-size: 1.1rem;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const PetTypeBadge = styled.span`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  border-radius: ${(props) => props.theme.borderRadius.full};
  font-size: 0.875rem;
`;

const PetInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const InfoItem = styled.div`
  color: ${(props) => props.theme.colors.textSecondary};
`;

const PetActions = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
  margin-top: ${(props) => props.theme.spacing.sm};
`;

const DeleteButton = styled.button`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.error || '#dc2626'};
  color: white;
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    opacity: 0.8;
  }
`;

const ReviewSummarySection = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.lg};
  padding-bottom: ${(props) => props.theme.spacing.lg};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
`;

const ReviewSummaryTitle = styled.h3`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  color: ${(props) => props.theme.colors.text};
  font-size: 1.2rem;
`;

const ReviewStats = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.xl};
`;

const StatItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const StatLabel = styled.div`
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.9rem;
`;

const StatValue = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  color: ${(props) => props.theme.colors.text};
  font-size: 1.5rem;
  font-weight: 600;
`;

const StarIcon = styled.span`
  font-size: 1.2rem;
`;

const ReviewsSection = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const ReviewsTitle = styled.h3`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  color: ${(props) => props.theme.colors.text};
  font-size: 1.2rem;
`;

const ReviewList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const ReviewItem = styled.div`
  padding: ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
`;

const ReviewHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${(props) => props.theme.spacing.xs};
`;

const ReviewerName = styled.div`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const ReviewRating = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
`;

const RatingNumber = styled.span`
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ReviewComment = styled.div`
  color: ${(props) => props.theme.colors.text};
  margin-bottom: ${(props) => props.theme.spacing.xs};
  line-height: 1.5;
`;

const ReviewDate = styled.div`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const EmptyReviewsMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
  font-style: italic;
`;
