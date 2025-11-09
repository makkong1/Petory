import React, { useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { commentApi } from '../../api/commentApi';
import { uploadApi } from '../../api/uploadApi';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

const CommunityCommentDrawer = ({ isOpen, board, onClose, currentUser, onCommentAdded }) => {
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [commentFilePath, setCommentFilePath] = useState('');

  const boardTitle = useMemo(() => board?.title || '게시글', [board]);

  useEffect(() => {
    if (!isOpen || !board?.idx) {
      return;
    }

    const fetchComments = async () => {
      try {
        setLoading(true);
        setError('');
        const response = await commentApi.list(board.idx);
        setComments(response.data || []);
      } catch (err) {
        const message = err.response?.data?.error || err.message;
        setError(`댓글을 불러오지 못했습니다: ${message}`);
      } finally {
        setLoading(false);
      }
    };

    fetchComments();
  }, [isOpen, board]);

  useEffect(() => {
    if (!isOpen) {
      setContent('');
      setCommentFilePath('');
      setUploadError('');
      setComments([]);
    }
  }, [isOpen]);

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
        entityId: board?.idx ?? undefined,
      });
      setCommentFilePath(data.url);
    } catch (uploadErr) {
      const message =
        uploadErr.response?.data?.error ||
        uploadErr.message ||
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
    setCommentFilePath('');
    setUploadError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!board?.idx || !currentUser) {
      return;
    }
    if (!content.trim()) {
      setError('댓글 내용을 입력해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);
      setError('');
      const payload = {
        content: content.trim(),
        userId: currentUser.idx,
        commentFilePath: commentFilePath || null,
      };
      const response = await commentApi.create(board.idx, payload);
      setComments((prev) => [...prev, response.data]);
      setContent('');
      setCommentFilePath('');
      setUploadError('');
      if (onCommentAdded) {
        onCommentAdded();
      }
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`댓글 등록에 실패했습니다: ${message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) {
    return null;
  }

  return (
    <>
      <Backdrop onClick={onClose} />
      <Drawer>
        <DrawerHeader>
          <DrawerTitle>댓글 ({comments.length})</DrawerTitle>
          <DrawerSubtitle>{boardTitle}</DrawerSubtitle>
          <DrawerCloseButton type="button" onClick={onClose}>
            ✕
          </DrawerCloseButton>
        </DrawerHeader>

        <DrawerBody>
          {loading ? (
            <LoadingState>댓글을 불러오는 중...</LoadingState>
          ) : error ? (
            <ErrorState>{error}</ErrorState>
          ) : comments.length === 0 ? (
            <EmptyState>
              <EmptyIcon>💬</EmptyIcon>
              <EmptyText>첫 댓글을 남겨보세요!</EmptyText>
            </EmptyState>
          ) : (
            <CommentList>
              {comments.map((comment) => (
                <CommentItem key={comment.idx}>
                  <CommentHeader>
                    <CommentAuthor>
                      <CommentAvatar>
                        {comment.username ? comment.username.charAt(0).toUpperCase() : 'U'}
                      </CommentAvatar>
                      <CommentAuthorInfo>
                        <CommentAuthorName>{comment.username}</CommentAuthorName>
                        <CommentTimestamp>
                          {comment.createdAt
                            ? new Date(comment.createdAt).toLocaleString('ko-KR')
                            : ''}
                        </CommentTimestamp>
                      </CommentAuthorInfo>
                    </CommentAuthor>
                  </CommentHeader>
                  <CommentContent>{comment.content}</CommentContent>
                  {comment.commentFilePath && (
                    <CommentImage>
                      <img src={comment.commentFilePath} alt="댓글 이미지" />
                    </CommentImage>
                  )}
                </CommentItem>
              ))}
            </CommentList>
          )}
        </DrawerBody>

        <DrawerFooter>
          {!currentUser ? (
            <LoginNotice>댓글을 작성하려면 로그인이 필요합니다.</LoginNotice>
          ) : (
            <CommentForm onSubmit={handleSubmit}>
              <Textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="댓글을 입력하세요"
                rows={3}
                disabled={isSubmitting}
              />

              <UploadControls>
                <HiddenFileInput
                  id="community-comment-image"
                  type="file"
                  accept="image/*"
                  onChange={handleFileSelect}
                />
                <UploadButtonRow>
                  <FileSelectButton htmlFor="community-comment-image" $disabled={isUploading}>
                    {isUploading ? '업로드 중...' : '이미지 첨부'}
                  </FileSelectButton>
                  {commentFilePath && (
                    <ClearImageButton type="button" onClick={handleRemoveImage}>
                      첨부 삭제
                    </ClearImageButton>
                  )}
                </UploadButtonRow>
                <HelperText>최대 5MB까지 업로드할 수 있습니다.</HelperText>
                {uploadError && <ErrorText>{uploadError}</ErrorText>}
              </UploadControls>

              {commentFilePath && (
                <ImagePreview>
                  <PreviewImage src={commentFilePath} alt="댓글 이미지 미리보기" />
                </ImagePreview>
              )}

              <SubmitButton type="submit" disabled={isSubmitting || isUploading}>
                {isSubmitting ? '등록 중...' : '댓글 등록'}
              </SubmitButton>
            </CommentForm>
          )}
        </DrawerFooter>
      </Drawer>
    </>
  );
};

export default CommunityCommentDrawer;

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 1090;
`;

const Drawer = styled.div`
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: min(420px, 100%);
  background: ${(props) => props.theme.colors.surface};
  box-shadow: -12px 0 32px rgba(15, 23, 42, 0.2);
  display: flex;
  flex-direction: column;
  z-index: 1100;
`;

const DrawerHeader = styled.div`
  position: relative;
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const DrawerTitle = styled.h2`
  margin: 0;
  font-size: 1.4rem;
`;

const DrawerSubtitle = styled.span`
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const DrawerCloseButton = styled.button`
  position: absolute;
  top: 16px;
  right: 20px;
  border: none;
  background: transparent;
  font-size: 1.4rem;
  cursor: pointer;
  color: ${(props) => props.theme.colors.textSecondary};

  &:hover {
    color: ${(props) => props.theme.colors.text};
  }
`;

const DrawerBody = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const DrawerFooter = styled.div`
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  border-top: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
`;

const LoadingState = styled.div`
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorState = styled.div`
  text-align: center;
  color: ${(props) => props.theme.colors.error || '#e11d48'};
  font-size: 0.95rem;
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
  color: ${(props) => props.theme.colors.textSecondary};
  margin-top: ${(props) => props.theme.spacing.xl};
`;

const EmptyIcon = styled.div`
  font-size: 36px;
`;

const EmptyText = styled.div`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const CommentList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const CommentItem = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.md};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const CommentHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
`;

const CommentAuthor = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
`;

const CommentAvatar = styled.div`
  width: 40px;
  height: 40px;
  border-radius: ${(props) => props.theme.borderRadius.full};
  background: ${(props) => props.theme.colors.gradient};
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-weight: 700;
  font-size: 16px;
`;

const CommentAuthorInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const CommentAuthorName = styled.span`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const CommentTimestamp = styled.span`
  font-size: 0.8rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const CommentContent = styled.p`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
  white-space: pre-wrap;
  line-height: 1.5;
`;

const CommentImage = styled.div`
  margin-top: ${(props) => props.theme.spacing.sm};
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.border};

  img {
    width: 100%;
    height: auto;
    display: block;
  }
`;

const CommentForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const Textarea = styled.textarea`
  padding: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  font-size: 0.95rem;
  resize: vertical;
  min-height: 120px;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const UploadControls = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const UploadButtonRow = styled.div`
  display: flex;
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
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
  cursor: ${(props) => (props.$disabled ? 'not-allowed' : 'pointer')};
  opacity: ${(props) => (props.$disabled ? 0.6 : 1)};
  pointer-events: ${(props) => (props.$disabled ? 'none' : 'auto')};
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.surfaceHover};
    color: ${(props) => props.theme.colors.primary};
  }
`;

const ClearImageButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
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
  font-size: 0.8rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorText = styled.span`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.error || '#e11d48'};
`;

const ImagePreview = styled.div`
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.border};
  max-width: 100%;
`;

const PreviewImage = styled.img`
  width: 100%;
  height: auto;
  display: block;
`;

const SubmitButton = styled.button`
  align-self: flex-end;
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

const LoginNotice = styled.div`
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.95rem;
`;

