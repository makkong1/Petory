import React, { useState, useEffect } from 'react';
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
    <FormContainer>
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
          <TextInput
            id="care-request-date"
            name="date"
            type="datetime-local"
            value={form.date}
            onChange={handleChange}
            disabled={submitting}
          />
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
  padding: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  font-size: 1rem;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
`;

const TextArea = styled.textarea`
  padding: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  font-size: 1rem;
  line-height: 1.6;
  resize: vertical;
  min-height: 160px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
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

