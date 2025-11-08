import React, { useEffect, useState } from 'react';
import styled from 'styled-components';

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

const MissingPetBoardForm = ({ isOpen, onClose, onSubmit, initialData, loading }) => {
  const [form, setForm] = useState(defaultForm);

  useEffect(() => {
    if (initialData) {
      setForm({
        ...defaultForm,
        ...initialData,
        lostDate: initialData.lostDate || '',
      });
    } else {
      setForm(defaultForm);
    }
  }, [initialData, isOpen]);

  if (!isOpen) {
    return null;
  }

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleNumberChange = (e) => {
    const { name, value } = e.target;
    const numericValue = value.replace(/[^0-9.\-]/g, '');
    setForm((prev) => ({
      ...prev,
      [name]: numericValue,
    }));
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
                  <Input type="date" name="lostDate" value={form.lostDate} onChange={handleChange} />
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
              <FieldGrid columns={2}>
                <Field>
                  <Label>실종 위치</Label>
                  <Input
                    name="lostLocation"
                    value={form.lostLocation}
                    onChange={handleChange}
                    placeholder="예: 서울시 강남구 테헤란로 123"
                  />
                </Field>
                <Field>
                  <Label>대표 이미지 URL</Label>
                  <Input
                    name="imageUrl"
                    value={form.imageUrl}
                    onChange={handleChange}
                    placeholder="이미지 링크 입력"
                  />
                </Field>
                <Field>
                  <Label>위도</Label>
                  <Input
                    name="latitude"
                    value={form.latitude}
                    onChange={handleNumberChange}
                    placeholder="예: 37.5665"
                  />
                </Field>
                <Field>
                  <Label>경도</Label>
                  <Input
                    name="longitude"
                    value={form.longitude}
                    onChange={handleNumberChange}
                    placeholder="예: 126.9780"
                  />
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
        </ModalBody>
      </Modal>
    </Overlay>
  );
};

export default MissingPetBoardForm;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  overflow-y: auto;
  z-index: 1000;
  padding: 3rem 1rem;
`;

const Modal = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  max-width: 880px;
  width: 100%;
  box-shadow: 0 25px 80px rgba(15, 23, 42, 0.25);
`;

const ModalHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
`;

const ModalTitle = styled.h2`
  margin: 0;
  font-size: 1.6rem;
`;

const CloseButton = styled.button`
  border: none;
  background: transparent;
  font-size: 1.5rem;
  cursor: pointer;
  color: ${(props) => props.theme.colors.textSecondary};

  &:hover {
    color: ${(props) => props.theme.colors.text};
  }
`;

const ModalBody = styled.div`
  padding: ${(props) => props.theme.spacing.xl};
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
  font-size: 1.2rem;
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
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const Input = styled.input`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  font-size: 0.95rem;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const Select = styled.select`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  font-size: 0.95rem;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const Textarea = styled.textarea`
  padding: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  font-size: 0.95rem;
  resize: vertical;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
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

