import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { uploadApi } from '../../api/uploadApi';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

const defaultForm = {
  title: '',
  content: '',
  category: '일상',
  boardFilePath: '',
};

const CommunityPostModal = ({ isOpen, onClose, onSubmit, loading, currentUser }) => {
  const [form, setForm] = useState(defaultForm);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  useEffect(() => {
    if (!isOpen) {
      setForm(defaultForm);
      setUploadError('');
      setIsUploading(false);
    }
  }, [isOpen]);

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

  const handleFileSelect = async (e) => {
    const file = e.target.files?.[0];
    if (!file) {
      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      setUploadError('이미지 크기는 최대 5MB까지 가능합니다.');
      e.target.value = '';
      return;
    }

    setUploadError('');
    setIsUploading(true);

    try {
      const data = await uploadApi.uploadImage(file, {
        category: 'community',
        ownerType: currentUser ? 'user' : 'guest',
        ownerId: currentUser?.idx ?? undefined,
      });
      setForm((prev) => ({
        ...prev,
        boardFilePath: data.url,
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
      boardFilePath: '',
    }));
    setUploadError('');
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(form);
  };

  const handleOverlayClick = (event) => {
    if (event.target === event.currentTarget) {
      onClose();
    }
  };

  return (
    <Overlay onClick={handleOverlayClick}>
      <Modal onClick={(event) => event.stopPropagation()}>
        <ModalHeader>
          <ModalTitle>커뮤니티 글 작성</ModalTitle>
          <CloseButton type="button" onClick={onClose}>
            ✕
          </CloseButton>
        </ModalHeader>
        <ModalBody>
          <Form onSubmit={handleSubmit}>
            <Field>
              <Label>제목 *</Label>
              <Input
                name="title"
                value={form.title}
                onChange={handleChange}
                required
                placeholder="제목을 입력하세요"
              />
            </Field>

            <Field>
              <Label>카테고리 *</Label>
              <Select name="category" value={form.category} onChange={handleChange}>
                <option value="일상">일상</option>
                <option value="자랑">자랑</option>
                <option value="질문">질문</option>
                <option value="정보">정보</option>
                <option value="후기">후기</option>
                <option value="모임">모임</option>
                <option value="공지">공지</option>
              </Select>
            </Field>

            <Field>
              <Label>내용 *</Label>
              <Textarea
                name="content"
                value={form.content}
                onChange={handleChange}
                required
                rows={6}
                placeholder="내용을 입력하세요"
              />
            </Field>

            <Field>
              <Label>대표 이미지</Label>
              <UploadControls>
                <HiddenFileInput
                  id="community-post-image"
                  type="file"
                  accept="image/*"
                  onChange={handleFileSelect}
                />
                <UploadButtonRow>
                  <FileSelectButton htmlFor="community-post-image" $disabled={isUploading}>
                    {isUploading ? '업로드 중...' : '이미지 선택'}
                  </FileSelectButton>
                  {form.boardFilePath && (
                    <ClearImageButton type="button" onClick={handleRemoveImage}>
                      이미지 삭제
                    </ClearImageButton>
                  )}
                </UploadButtonRow>
                <HelperText>JPG, PNG, GIF, WEBP 형식의 이미지를 최대 5MB까지 업로드할 수 있어요.</HelperText>
                {uploadError && <ErrorText>{uploadError}</ErrorText>}
              </UploadControls>
              {form.boardFilePath && (
                <ImagePreview>
                  <PreviewImage src={form.boardFilePath} alt="게시글 이미지 미리보기" />
                </ImagePreview>
              )}
            </Field>

            <ButtonRow>
              <SecondaryButton type="button" onClick={onClose}>
                취소
              </SecondaryButton>
              <PrimaryButton type="submit" disabled={loading || isUploading}>
                {loading ? '등록 중...' : '등록'}
              </PrimaryButton>
            </ButtonRow>
          </Form>
        </ModalBody>
      </Modal>
    </Overlay>
  );
};

export default CommunityPostModal;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow-y: auto;
  z-index: 1100;
  padding: 3rem 1rem;

  @media (max-width: 768px) {
    padding: 1rem;
    align-items: flex-start;
  }
`;

const Modal = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  max-width: 832px;
  width: 100%;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);

  @media (max-width: 768px) {
    max-width: 100%;
    border-radius: ${(props) => props.theme.borderRadius.lg};
    margin-top: 1rem;
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
  font-size: 1.4rem;
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

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
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
  min-height: 160px;

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

const HelperText = styled.span`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorText = styled.span`
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

