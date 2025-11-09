import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { userApi } from '../../api/userApi';

const EMPTY_FORM = {
  username: '',
  email: '',
  location: '',
  petInfo: '',
  id: '',
  role: '',
  password: '',
};

const UserProfileModal = ({ isOpen, userId, onClose, onUpdated }) => {
  const [form, setForm] = useState(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (!isOpen || !userId) {
      setForm(EMPTY_FORM);
      setError('');
      setSuccess('');
      return;
    }

    const fetchUser = async () => {
      try {
        setLoading(true);
        setError('');
        setSuccess('');
        const response = await userApi.getUser(userId);
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
      } catch (err) {
        const message = err.response?.data?.error || err.message || '사용자 정보를 불러오지 못했습니다.';
        setError(message);
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, [isOpen, userId]);

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
  width: min(520px, 100%);
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  box-shadow: 0 30px 60px rgba(15, 23, 42, 0.3);
  border: 1px solid ${(props) => props.theme.colors.border};
  overflow: hidden;
  display: flex;
  flex-direction: column;
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

