import React, { useState } from 'react';
import styled from 'styled-components';
import { careRequestApi } from '../../api/careRequestApi';
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

  return (
    <FormContainer>
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
  max-width: 720px;
  width: 100%;
  margin: 0 auto;

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
    border-radius: ${(props) => props.theme.borderRadius.lg};
  }
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

