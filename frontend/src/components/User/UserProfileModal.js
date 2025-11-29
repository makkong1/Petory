import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { userApi, userProfileApi, petApiClient } from '../../api/userApi';
import { uploadApi } from '../../api/uploadApi';

const EMPTY_FORM = {
  username: '',
  email: '',
  location: '',
  petInfo: '',
  id: '',
  idx: null,
  role: '',
  password: '',
};

const EMPTY_PET_FORM = {
  petName: '',
  petType: 'DOG',
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
};

const UserProfileModal = ({ isOpen, userId, onClose, onUpdated }) => {
  const [form, setForm] = useState(EMPTY_FORM);
  const [pets, setPets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showPetForm, setShowPetForm] = useState(false);
  const [petForm, setPetForm] = useState(EMPTY_PET_FORM);
  const [editingPetIdx, setEditingPetIdx] = useState(null);
  const [savingPet, setSavingPet] = useState(false);
  const [uploadingPetImage, setUploadingPetImage] = useState(false);
  const [petImageError, setPetImageError] = useState('');

  useEffect(() => {
    if (!isOpen) {
      setForm(EMPTY_FORM);
      setPets([]);
      setError('');
      setSuccess('');
      return;
    }

    const fetchUser = async () => {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        // 자신의 프로필 조회 (펫 정보 포함)
        const response = await userProfileApi.getMyProfile();
        const data = response.data || {};
        setForm({
          username: data.username || '',
          email: data.email || '',
          location: data.location || '',
          petInfo: data.petInfo || '',
          id: data.id || '',
          role: data.role || '',
          password: '',
        });
        setPets(data.pets || []);
      } catch (err) {
        const message = err.response?.data?.error || err.message || '사용자 정보를 불러오지 못했습니다.';
        setError(message);
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  const handleBackdropClick = (event) => {
    if (event.target === event.currentTarget && !saving) {
      onClose?.();
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
    if (!userId || saving) {
      return;
    }
    try {
      setSaving(true);
      setError('');
      setSuccess('');

      const payload = {
        username: form.username?.trim() || null,
        email: form.email?.trim() || null,
        location: form.location?.trim() || null,
        petInfo: form.petInfo?.trim() || null,
      };

      if (form.password && form.password.trim().length > 0) {
        payload.password = form.password.trim();
      }

      const response = await userApi.updateUser(userId, payload);
      const updated = response.data || {};

      setSuccess('사용자 정보가 저장되었습니다.');
      setForm((prev) => ({
        ...prev,
        username: updated.username ?? prev.username,
        email: updated.email ?? prev.email,
        location: updated.location ?? prev.location,
        petInfo: updated.petInfo ?? prev.petInfo,
        role: updated.role ?? prev.role,
        id: updated.id ?? prev.id,
        password: '',
      }));

      onUpdated?.(updated);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '사용자 정보를 저장하지 못했습니다.';
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  const handlePetFormChange = (event) => {
    const { name, value, type, checked } = event.target;
    setPetForm((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleAddPetClick = () => {
    setPetForm(EMPTY_PET_FORM);
    setEditingPetIdx(null);
    setShowPetForm(true);
    setError('');
    setSuccess('');
  };

  const handleEditPetClick = (pet) => {
    setPetForm({
      petName: pet.petName || '',
      petType: pet.petType || 'DOG',
      breed: pet.breed || '',
      gender: pet.gender || 'UNKNOWN',
      age: pet.age || '',
      color: pet.color || '',
      weight: pet.weight?.toString() || '',
      birthDate: pet.birthDate || '',
      isNeutered: pet.isNeutered || false,
      healthInfo: pet.healthInfo || '',
      specialNotes: pet.specialNotes || '',
      profileImageUrl: pet.profileImageUrl || '',
    });
    setEditingPetIdx(pet.idx);
    setShowPetForm(true);
    setError('');
    setSuccess('');
  };

  const handlePetFormSubmit = async (event) => {
    event.preventDefault();
    if (savingPet) return;

    try {
      setSavingPet(true);
      setError('');
      setSuccess('');

      const petData = {
        petName: petForm.petName.trim(),
        petType: petForm.petType,
        breed: petForm.breed.trim() || null,
        gender: petForm.gender,
        age: petForm.age.trim() || null,
        color: petForm.color.trim() || null,
        weight: petForm.weight ? parseFloat(petForm.weight) : null,
        birthDate: petForm.birthDate || null,
        isNeutered: petForm.isNeutered,
        healthInfo: petForm.healthInfo.trim() || null,
        specialNotes: petForm.specialNotes.trim() || null,
        profileImageUrl: petForm.profileImageUrl.trim() || null,
      };

      let response;
      if (editingPetIdx) {
        // 수정
        response = await petApiClient.updatePet(editingPetIdx, petData);
        setSuccess('반려동물 정보가 수정되었습니다.');
      } else {
        // 생성
        response = await petApiClient.createPet(petData);
        setSuccess('반려동물이 등록되었습니다.');
      }

      // 펫 목록 새로고침
      const profileResponse = await userProfileApi.getMyProfile();
      setPets(profileResponse.data?.pets || []);
      
      setShowPetForm(false);
      setPetForm(EMPTY_PET_FORM);
      setEditingPetIdx(null);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '반려동물 정보를 저장하지 못했습니다.';
      setError(message);
    } finally {
      setSavingPet(false);
    }
  };

  const handleDeletePet = async (petIdx) => {
    if (!window.confirm('정말 이 반려동물 정보를 삭제하시겠습니까?')) {
      return;
    }

    try {
      await petApiClient.deletePet(petIdx);
      setSuccess('반려동물 정보가 삭제되었습니다.');
      
      // 펫 목록 새로고침
      const profileResponse = await userProfileApi.getMyProfile();
      setPets(profileResponse.data?.pets || []);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '반려동물 정보를 삭제하지 못했습니다.';
      setError(message);
    }
  };

  const handleCancelPetForm = () => {
    setShowPetForm(false);
    setPetForm(EMPTY_PET_FORM);
    setEditingPetIdx(null);
    setError('');
    setPetImageError('');
  };

  const handlePetImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) {
      return;
    }

    const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    if (file.size > MAX_FILE_SIZE) {
      setPetImageError('이미지 크기는 최대 5MB까지 가능합니다.');
      e.target.value = '';
      return;
    }

    setPetImageError('');
    setUploadingPetImage(true);

    try {
      const data = await uploadApi.uploadImage(file, {
        category: 'pet',
        ownerType: 'user',
        ownerId: getCurrentUserId(),
        entityId: editingPetIdx?.toString() || null,
      });
      setPetForm((prev) => ({
        ...prev,
        profileImageUrl: data.url,
      }));
    } catch (error) {
      const message =
        error.response?.data?.error ||
        error.message ||
        '이미지 업로드 중 문제가 발생했습니다.';
      setPetImageError(message);
    } finally {
      setUploadingPetImage(false);
      e.target.value = '';
    }
  };

  const handleRemovePetImage = () => {
    setPetForm((prev) => ({
      ...prev,
      profileImageUrl: '',
    }));
    setPetImageError('');
  };

  const getCurrentUserId = () => {
    // form에서 사용자 idx 가져오기
    return form.idx || form.id || null;
  };

  return (
    <Backdrop onClick={handleBackdropClick}>
      <Modal>
        <Header>
          <HeaderTitle>내 프로필</HeaderTitle>
          <HeaderActions>
            <HeaderButton type="button" onClick={() => onClose?.()} disabled={saving}>
              닫기
            </HeaderButton>
          </HeaderActions>
        </Header>

        {loading ? (
          <BodyMessage>사용자 정보를 불러오는 중...</BodyMessage>
        ) : error ? (
          <BodyMessage $error>{error}</BodyMessage>
        ) : (
          <ContentWrapper>
            <LeftCard>
              <CardTitle>사용자 정보</CardTitle>
              <Form onSubmit={handleSubmit}>
            <FormRow>
              <Label htmlFor="profile-id">로그인 ID</Label>
              <ReadOnlyInput id="profile-id" value={form.id} readOnly />
            </FormRow>
            <FormRow>
              <Label htmlFor="profile-role">권한</Label>
              <ReadOnlyInput id="profile-role" value={form.role} readOnly />
            </FormRow>
            <FormRow>
              <Label htmlFor="profile-username">이름</Label>
              <TextInput
                id="profile-username"
                name="username"
                value={form.username}
                onChange={handleChange}
                placeholder="이름을 입력하세요"
              />
            </FormRow>
            <FormRow>
              <Label htmlFor="profile-email">이메일</Label>
              <TextInput
                id="profile-email"
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
                placeholder="이메일 주소를 입력하세요"
              />
            </FormRow>
            <FormRow>
              <Label htmlFor="profile-location">지역</Label>
              <TextInput
                id="profile-location"
                name="location"
                value={form.location}
                onChange={handleChange}
                placeholder="거주 지역을 입력하세요"
              />
            </FormRow>
            <FormRow>
              <Label htmlFor="profile-petInfo">반려동물 정보</Label>
              <TextArea
                id="profile-petInfo"
                name="petInfo"
                rows={3}
                value={form.petInfo}
                onChange={handleChange}
                placeholder="반려동물 정보를 입력해주세요"
              />
            </FormRow>
            <FormRow>
              <Label htmlFor="profile-password">비밀번호 재설정</Label>
              <TextInput
                id="profile-password"
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="새 비밀번호 (변경 시에만 입력)"
              />
            </FormRow>

                {success && <Notice $success>{success}</Notice>}
                {error && !loading && <Notice $error>{error}</Notice>}

                <SubmitRow>
                  <SubmitButton type="submit" disabled={saving}>
                    {saving ? '저장 중...' : '정보 저장'}
                  </SubmitButton>
                </SubmitRow>
              </Form>
            </LeftCard>
            
            <RightCard>
              <CardHeader>
                <CardTitle>반려동물 정보</CardTitle>
                {!showPetForm && (
                  <AddPetButton onClick={handleAddPetClick}>
                    + 반려동물 추가
                  </AddPetButton>
                )}
              </CardHeader>
              
              {showPetForm ? (
                <PetForm onSubmit={handlePetFormSubmit}>
                  <FormRow>
                    <Label>이름 *</Label>
                    <TextInput
                      name="petName"
                      value={petForm.petName}
                      onChange={handlePetFormChange}
                      placeholder="반려동물 이름"
                      required
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>종류 *</Label>
                    <Select
                      name="petType"
                      value={petForm.petType}
                      onChange={handlePetFormChange}
                      required
                    >
                      <option value="DOG">강아지</option>
                      <option value="CAT">고양이</option>
                      <option value="BIRD">새</option>
                      <option value="RABBIT">토끼</option>
                      <option value="HAMSTER">햄스터</option>
                      <option value="ETC">기타</option>
                    </Select>
                  </FormRow>
                  
                  <FormRow>
                    <Label>{petForm.petType === 'ETC' ? '기타 종류 *' : '품종'}</Label>
                    <TextInput
                      name="breed"
                      value={petForm.breed}
                      onChange={handlePetFormChange}
                      placeholder={petForm.petType === 'ETC' ? '예: 거북이, 이구아나, 페럿 등' : '예: 골든 리트리버'}
                      required={petForm.petType === 'ETC'}
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>성별</Label>
                    <Select
                      name="gender"
                      value={petForm.gender}
                      onChange={handlePetFormChange}
                    >
                      <option value="M">수컷</option>
                      <option value="F">암컷</option>
                      <option value="UNKNOWN">미확인</option>
                    </Select>
                  </FormRow>
                  
                  <FormRow>
                    <Label>나이</Label>
                    <TextInput
                      name="age"
                      value={petForm.age}
                      onChange={handlePetFormChange}
                      placeholder="예: 3살, 5개월"
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>생년월일</Label>
                    <TextInput
                      type="date"
                      name="birthDate"
                      value={petForm.birthDate}
                      onChange={handlePetFormChange}
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>색상</Label>
                    <TextInput
                      name="color"
                      value={petForm.color}
                      onChange={handlePetFormChange}
                      placeholder="털색"
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>몸무게 (kg)</Label>
                    <TextInput
                      type="number"
                      step="0.1"
                      name="weight"
                      value={petForm.weight}
                      onChange={handlePetFormChange}
                      placeholder="예: 5.5"
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>
                      <input
                        type="checkbox"
                        name="isNeutered"
                        checked={petForm.isNeutered}
                        onChange={handlePetFormChange}
                      />
                      {' '}중성화 여부
                    </Label>
                  </FormRow>
                  
                  <FormRow>
                    <Label>건강 정보</Label>
                    <TextArea
                      name="healthInfo"
                      rows={3}
                      value={petForm.healthInfo}
                      onChange={handlePetFormChange}
                      placeholder="질병, 알레르기 등"
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>특이사항</Label>
                    <TextArea
                      name="specialNotes"
                      rows={3}
                      value={petForm.specialNotes}
                      onChange={handlePetFormChange}
                      placeholder="성격, 주의사항 등"
                    />
                  </FormRow>
                  
                  <FormRow>
                    <Label>프로필 이미지</Label>
                    <ImageUploadWrapper>
                      <HiddenFileInput
                        type="file"
                        id="pet-image-upload"
                        accept="image/*"
                        onChange={handlePetImageUpload}
                        disabled={uploadingPetImage}
                      />
                      <FileSelectButton htmlFor="pet-image-upload" $disabled={uploadingPetImage}>
                        {uploadingPetImage ? '업로드 중...' : '이미지 선택'}
                      </FileSelectButton>
                      {petForm.profileImageUrl && (
                        <ClearImageButton type="button" onClick={handleRemovePetImage}>
                          이미지 삭제
                        </ClearImageButton>
                      )}
                      {petImageError && <ImageErrorText>{petImageError}</ImageErrorText>}
                    </ImageUploadWrapper>
                    {petForm.profileImageUrl && (
                      <ImagePreview>
                        <PreviewImage src={petForm.profileImageUrl} alt="펫 이미지 미리보기" />
                      </ImagePreview>
                    )}
                    <HelperText>JPG, PNG, GIF, WEBP 형식의 이미지를 최대 5MB까지 업로드할 수 있어요.</HelperText>
                  </FormRow>
                  
                  <PetFormActions>
                    <CancelButton type="button" onClick={handleCancelPetForm}>
                      취소
                    </CancelButton>
                    <SubmitButton type="submit" disabled={savingPet}>
                      {savingPet ? '저장 중...' : editingPetIdx ? '수정' : '등록'}
                    </SubmitButton>
                  </PetFormActions>
                </PetForm>
              ) : (
                <>
                  {pets && pets.length > 0 ? (
                    <PetList>
                      {pets.map((pet) => (
                        <PetCard key={pet.idx}>
                          <PetImage 
                            src={pet.profileImageUrl || 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="100" height="100"%3E%3Crect width="100" height="100" fill="%23e2e8f0"/%3E%3Ctext x="50" y="50" font-family="Arial" font-size="14" fill="%2394a3b8" text-anchor="middle" dominant-baseline="middle"%3E이미지 없음%3C/text%3E%3C/svg%3E'} 
                            alt={pet.petName}
                            onError={(e) => {
                              e.target.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="100" height="100"%3E%3Crect width="100" height="100" fill="%23e2e8f0"/%3E%3Ctext x="50" y="50" font-family="Arial" font-size="14" fill="%2394a3b8" text-anchor="middle" dominant-baseline="middle"%3E이미지 없음%3C/text%3E%3C/svg%3E';
                            }}
                          />
                          <PetInfo>
                            <PetName>{pet.petName}</PetName>
                            <PetDetail>
                              {pet.petType === 'DOG' ? '강아지' : 
                               pet.petType === 'CAT' ? '고양이' : 
                               pet.petType === 'BIRD' ? '새' :
                               pet.petType === 'RABBIT' ? '토끼' :
                               pet.petType === 'HAMSTER' ? '햄스터' : '기타'}
                              {' · '}
                              {pet.breed || '품종 미상'}
                            </PetDetail>
                            <PetDetail>
                              {pet.gender === 'M' ? '수컷' : pet.gender === 'F' ? '암컷' : '미확인'}
                              {' · '}
                              {pet.age || '나이 미상'}
                            </PetDetail>
                            {pet.weight && <PetDetail>몸무게: {pet.weight}kg</PetDetail>}
                            <PetActions>
                              <PetActionButton onClick={() => handleEditPetClick(pet)}>
                                수정
                              </PetActionButton>
                              <PetActionButton $danger onClick={() => handleDeletePet(pet.idx)}>
                                삭제
                              </PetActionButton>
                            </PetActions>
                          </PetInfo>
                        </PetCard>
                      ))}
                    </PetList>
                  ) : (
                    <EmptyMessage>
                      등록된 반려동물이 없습니다.
                      <AddPetButtonSmall onClick={handleAddPetClick}>
                        + 반려동물 추가하기
                      </AddPetButtonSmall>
                    </EmptyMessage>
                  )}
                </>
              )}
            </RightCard>
          </ContentWrapper>
        )}
      </Modal>
    </Backdrop>
  );
};

export default UserProfileModal;

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: ${(props) => props.theme.spacing.xxl} ${(props) => props.theme.spacing.lg};
  overflow-y: auto;
  z-index: 1400;
`;

const Modal = styled.div`
  width: min(1200px, 100%);
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  box-shadow: 0 30px 60px rgba(15, 23, 42, 0.3);
  border: 1px solid ${(props) => props.theme.colors.border};
  overflow: hidden;
  display: flex;
  flex-direction: column;
  
  @media (max-width: 768px) {
    width: min(100%, 100vw);
    margin: ${(props) => props.theme.spacing.md};
  }
`;

const Header = styled.header`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};
`;

const HeaderTitle = styled.h2`
  margin: 0;
  font-size: 1.4rem;
  color: ${(props) => props.theme.colors.text};
`;

const HeaderActions = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
`;

const HeaderButton = styled.button`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  color: ${(props) => props.theme.colors.textSecondary};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  border-radius: ${(props) => props.theme.borderRadius.md};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    color: ${(props) => props.theme.colors.primary};
    border-color: ${(props) => props.theme.colors.primary};
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const BodyMessage = styled.div`
  padding: ${(props) => props.theme.spacing.xxl};
  text-align: center;
  color: ${(props) => (props.$error ? props.theme.colors.error : props.theme.colors.textSecondary)};
`;

const Form = styled.form`
  padding: ${(props) => props.theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const FormRow = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const Label = styled.label`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
  font-size: 0.95rem;
`;

const baseInput = `
  width: 100%;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--surface-color);
  color: var(--text-color);
  font-size: 0.95rem;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const TextInput = styled.input`
  --surface-color: ${(props) => props.theme.colors.surface};
  --border-color: ${(props) => props.theme.colors.border};
  --text-color: ${(props) => props.theme.colors.text};
  --primary-color: ${(props) => props.theme.colors.primary};
  ${baseInput}
`;

const ReadOnlyInput = styled.input`
  --surface-color: ${(props) => props.theme.colors.surfaceElevated};
  --border-color: ${(props) => props.theme.colors.borderLight};
  --text-color: ${(props) => props.theme.colors.textSecondary};
  ${baseInput}
  cursor: not-allowed;
`;

const TextArea = styled.textarea`
  --surface-color: ${(props) => props.theme.colors.surface};
  --border-color: ${(props) => props.theme.colors.border};
  --text-color: ${(props) => props.theme.colors.text};
  --primary-color: ${(props) => props.theme.colors.primary};
  ${baseInput}
  resize: vertical;
  min-height: 120px;
`;

const Notice = styled.div`
  padding: ${(props) => props.theme.spacing.sm};
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-size: 0.9rem;
  color: ${(props) =>
    props.$success ? props.theme.colors.success : props.theme.colors.error || '#dc2626'};
  background: ${(props) =>
    props.$success ? 'rgba(34,197,94,0.15)' : 'rgba(220,38,38,0.12)'};
  border: 1px solid
    ${(props) => (props.$success ? 'rgba(34,197,94,0.25)' : 'rgba(220,38,38,0.25)')};
`;

const SubmitRow = styled.div`
  display: flex;
  justify-content: flex-end;
`;

const SubmitButton = styled.button`
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

const ContentWrapper = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.xl};
  padding: ${(props) => props.theme.spacing.xl};
  
  @media (max-width: 768px) {
    flex-direction: column;
    gap: ${(props) => props.theme.spacing.md};
    padding: ${(props) => props.theme.spacing.md};
  }
`;

const LeftCard = styled.div`
  flex: 1;
  min-width: 0;
`;

const RightCard = styled.div`
  flex: 1;
  min-width: 0;
`;

const CardTitle = styled.h3`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  font-size: 1.2rem;
  color: ${(props) => props.theme.colors.text};
  border-bottom: 2px solid ${(props) => props.theme.colors.primary};
  padding-bottom: ${(props) => props.theme.spacing.xs};
`;

const PetList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const PetCard = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.md};
  padding: ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.surfaceElevated || props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
  
  @media (max-width: 480px) {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
`;

const PetImage = styled.img`
  width: 80px;
  height: 80px;
  border-radius: ${(props) => props.theme.borderRadius.md};
  object-fit: cover;
  background: ${(props) => props.theme.colors.borderLight};
  
  @media (max-width: 480px) {
    width: 100px;
    height: 100px;
  }
`;

const PetInfo = styled.div`
  flex: 1;
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

const EmptyMessage = styled.div`
  padding: ${(props) => props.theme.spacing.xl};
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
  font-style: italic;
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${(props) => props.theme.spacing.md};
  
  @media (max-width: 480px) {
    flex-direction: column;
    align-items: flex-start;
    gap: ${(props) => props.theme.spacing.sm};
  }
`;

const AddPetButton = styled.button`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.9rem;
  
  &:hover {
    background: ${(props) => props.theme.colors.primaryDark};
    transform: translateY(-1px);
  }
`;

const AddPetButtonSmall = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
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
  }
`;

const PetForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
  max-height: 70vh;
  overflow-y: auto;
  padding-right: ${(props) => props.theme.spacing.xs};
  
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-track {
    background: ${(props) => props.theme.colors.borderLight};
    border-radius: 3px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: ${(props) => props.theme.colors.border};
    border-radius: 3px;
    
    &:hover {
      background: ${(props) => props.theme.colors.textSecondary};
    }
  }
`;

const Select = styled.select`
  --surface-color: ${(props) => props.theme.colors.surface};
  --border-color: ${(props) => props.theme.colors.border};
  --text-color: ${(props) => props.theme.colors.text};
  --primary-color: ${(props) => props.theme.colors.primary};
  ${baseInput}
  background: var(--surface-color);
`;

const PetFormActions = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.md};
  justify-content: flex-end;
  margin-top: ${(props) => props.theme.spacing.md};
`;

const CancelButton = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.text};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    background: ${(props) => props.theme.colors.borderLight};
  }
`;

const PetActions = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.xs};
  margin-top: ${(props) => props.theme.spacing.sm};
`;

const PetActionButton = styled.button`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.$danger ? props.theme.colors.error : props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.$danger ? props.theme.colors.error : props.theme.colors.text};
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    background: ${(props) => props.$danger ? 'rgba(220,38,38,0.1)' : props.theme.colors.borderLight};
  }
`;

const ImageUploadWrapper = styled.div`
  display: flex;
  flex-direction: column;
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
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  background: ${(props) => props.theme.colors.primary};
  color: #ffffff;
  font-weight: 600;
  cursor: ${(props) => (props.$disabled ? 'not-allowed' : 'pointer')};
  opacity: ${(props) => (props.$disabled ? 0.6 : 1)};
  pointer-events: ${(props) => (props.$disabled ? 'none' : 'auto')};
  transition: all 0.2s ease;
  width: fit-content;
  
  &:hover {
    background: ${(props) => props.theme.colors.primaryDark};
  }
`;

const ClearImageButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  width: fit-content;
  
  &:hover {
    border-color: ${(props) => props.theme.colors.error || '#e11d48'};
    color: ${(props) => props.theme.colors.error || '#e11d48'};
  }
`;

const ImageErrorText = styled.div`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.error || '#e11d48'};
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
  object-fit: cover;
`;

const HelperText = styled.span`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
  margin-top: ${(props) => props.theme.spacing.xs};
`;

