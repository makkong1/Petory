import React, { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { boardApi } from '../../api/boardApi';
import { commentApi } from '../../api/commentApi';
import { reportApi } from '../../api/reportApi';
import { uploadApi } from '../../api/uploadApi';
import { usePermission } from '../../hooks/usePermission';
import { useAuth } from '../../contexts/AuthContext';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

const CommunityDetailPage = ({
  isOpen,
  boardId,
  onClose,
  onCommentAdded,
  onBoardReaction,
  onBoardViewUpdate,
  currentUser,
  onBoardDeleted,
}) => {
  const { requireLogin } = usePermission();
  const { user, redirectToLogin } = useAuth();
  const viewerId = user?.idx;

  const [board, setBoard] = useState(null);
  const [loadingBoard, setLoadingBoard] = useState(false);
  const [boardError, setBoardError] = useState('');

  const [comments, setComments] = useState([]);
  const [loadingComments, setLoadingComments] = useState(false);
  const [commentError, setCommentError] = useState('');
  const [isSubmittingComment, setIsSubmittingComment] = useState(false);
  const [commentContent, setCommentContent] = useState('');
  const [commentFilePath, setCommentFilePath] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  const categoryInfo = useMemo(() => {
    if (!board?.category) {
      return null;
    }

    const mapping = {
      ALL: { label: '전체', icon: '📋', color: '#6366F1' },
      일상: { label: '일상', icon: '📖', color: '#EC4899' },
      자랑: { label: '자랑', icon: '🐾', color: '#F472B6' },
      질문: { label: '질문', icon: '❓', color: '#3B82F6' },
      정보: { label: '정보', icon: '📢', color: '#10B981' },
      후기: { label: '후기', icon: '📝', color: '#8B5CF6' },
      모임: { label: '모임', icon: '🤝', color: '#F59E0B' },
      공지: { label: '공지', icon: '📢', color: '#EF4444' },
      // Legacy mappings for backward compatibility
      TIP: { label: '꿀팁', icon: '💡', color: '#F59E0B' },
      QUESTION: { label: '질문', icon: '❓', color: '#3B82F6' },
      INFO: { label: '정보', icon: '📢', color: '#10B981' },
      PRIDE: { label: '자랑', icon: '🐾', color: '#F472B6' },
      STORY: { label: '일상', icon: '📖', color: '#EC4899' },
    };

    return mapping[board.category] || { label: board.category, icon: '📋', color: '#6366F1' };
  }, [board]);

  const formattedDate = useMemo(() => {
    if (!board?.createdAt) {
      return '';
    }
    const date = new Date(board.createdAt);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }, [board]);

  const resetState = useCallback(() => {
    setBoard(null);
    setLoadingBoard(false);
    setBoardError('');
    setComments([]);
    setLoadingComments(false);
    setCommentError('');
    setIsSubmittingComment(false);
    setCommentContent('');
    setCommentFilePath('');
    setIsUploading(false);
    setUploadError('');
  }, []);

  const fetchBoard = useCallback(async () => {
    if (!boardId) {
      return;
    }
    try {
      setLoadingBoard(true);
      setBoardError('');
      const response = await boardApi.getBoard(boardId, viewerId);
      const boardData = response.data || null;
      setBoard(boardData);
      if (boardData) {
        onBoardViewUpdate?.(boardId, boardData.views ?? 0);
      }
    } catch (err) {
      const message = err.response?.data?.error || err.message || '게시글을 불러오지 못했습니다.';
      setBoardError(message);
    } finally {
      setLoadingBoard(false);
    }
  }, [boardId, viewerId, onBoardViewUpdate]);

  const fetchComments = useCallback(async () => {
    if (!boardId) {
      return;
    }
    try {
      setLoadingComments(true);
      setCommentError('');
      const response = await commentApi.list(boardId);
      setComments(response.data || []);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '댓글을 불러오지 못했습니다.';
      setCommentError(message);
    } finally {
      setLoadingComments(false);
    }
  }, [boardId]);

  useEffect(() => {
    if (!isOpen) {
      resetState();
      return;
    }
    fetchBoard();
    fetchComments();
  }, [isOpen, boardId, fetchBoard, fetchComments, resetState]);

  const handleBoardReaction = useCallback(
    async (reactionType, event) => {
      event?.stopPropagation?.();
      if (!boardId) {
        return;
      }
      const { requiresRedirect } = requireLogin();
      if (requiresRedirect) {
        redirectToLogin();
        return;
      }
      if (!user) {
        redirectToLogin();
        return;
      }

      try {
        setBoardError('');
        const response = await boardApi.reactToBoard(boardId, {
          userId: user.idx,
          reactionType,
        });
        const summary = response.data;
        setBoard((prev) =>
          prev
            ? {
              ...prev,
              likes: summary.likeCount,
              dislikes: summary.dislikeCount,
              userReaction: summary.userReaction,
            }
            : prev
        );
        onBoardReaction?.(boardId, summary);
      } catch (err) {
        const message = err.response?.data?.error || err.message || '반응 처리에 실패했습니다.';
        setBoardError(message);
      }
    },
    [boardId, requireLogin, redirectToLogin, user, onBoardReaction]
  );

  const handleReportClick = useCallback(async () => {
    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }
    if (!user || !boardId) {
      return;
    }
    if (!window.confirm('이 게시글을 신고하시겠습니까?')) {
      return;
    }
    const reason = window.prompt('신고 사유를 입력해주세요.');
    if (!reason || !reason.trim()) {
      return;
    }
    try {
      await reportApi.submit({
        targetType: 'BOARD',
        targetIdx: boardId,
        reporterId: user.idx,
        reason: reason.trim(),
      });
      alert('신고가 접수되었습니다.');
    } catch (err) {
      const message = err.response?.data?.error || err.message || '신고 처리에 실패했습니다.';
      alert(message);
    }
  }, [requireLogin, redirectToLogin, user, boardId]);

  const handleCommentReport = useCallback(
    async (commentId) => {
      const { requiresRedirect } = requireLogin();
      if (requiresRedirect) {
        redirectToLogin();
        return;
      }
      if (!user || !commentId) {
        return;
      }
      if (!window.confirm('해당 댓글을 신고하시겠습니까?')) {
        return;
      }
      const reason = window.prompt('신고 사유를 입력해주세요.');
      if (!reason || !reason.trim()) {
        return;
      }
      try {
        await reportApi.submit({
          targetType: 'COMMENT',
          targetIdx: commentId,
          reporterId: user.idx,
          reason: reason.trim(),
        });
        alert('신고가 접수되었습니다.');
      } catch (err) {
        const message = err.response?.data?.error || err.message || '신고 처리에 실패했습니다.';
        alert(message);
      }
    },
    [requireLogin, redirectToLogin, user]
  );

  const handleFileSelect = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    if (file.size > MAX_FILE_SIZE) {
      setUploadError('이미지 크기는 최대 5MB까지 가능합니다.');
      event.target.value = '';
      return;
    }

    setUploadError('');
    setIsUploading(true);

    try {
      const data = await uploadApi.uploadImage(file, {
        category: 'community-comment',
        ownerType: user ? 'user' : 'guest',
        ownerId: user?.idx ?? undefined,
        entityId: boardId,
      });
      setCommentFilePath(data.url);
    } catch (err) {
      const message =
        err.response?.data?.error ||
        err.message ||
        '이미지 업로드 중 문제가 발생했습니다.';
      setUploadError(message);
    } finally {
      setIsUploading(false);
      event.target.value = '';
    }
  };

  const handleRemoveImage = () => {
    setCommentFilePath('');
    setUploadError('');
  };

  const handleSubmitComment = async (event) => {
    event.preventDefault();
    if (!boardId) {
      return;
    }

    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }

    if (!user) {
      redirectToLogin();
      return;
    }

    if (!commentContent.trim()) {
      setCommentError('댓글 내용을 입력해주세요.');
      return;
    }

    try {
      setIsSubmittingComment(true);
      setCommentError('');
      const payload = {
        content: commentContent.trim(),
        userId: user.idx,
        commentFilePath: commentFilePath || null,
      };
      const response = await commentApi.create(boardId, payload);
      setComments((prev) => [...prev, response.data]);
      setCommentContent('');
      setCommentFilePath('');
      setUploadError('');
      // 댓글 카운트 업데이트를 위해 boardId 전달
      if (onCommentAdded) {
        onCommentAdded(boardId);
      }
      // 알림 개수 즉시 업데이트 (다른 사용자에게 알림이 갔을 수 있음)
      window.dispatchEvent(new Event('notificationUpdate'));
    } catch (err) {
      const message = err.response?.data?.error || err.message || '댓글 등록에 실패했습니다.';
      setCommentError(message);
    } finally {
      setIsSubmittingComment(false);
    }
  };

  const handleCommentReaction = useCallback(
    async (commentId, reactionType) => {
      if (!boardId) {
        return;
      }
      const { requiresRedirect } = requireLogin();
      if (requiresRedirect) {
        redirectToLogin();
        return;
      }
      if (!user) {
        redirectToLogin();
        return;
      }

      try {
        setCommentError('');
        const response = await boardApi.reactToComment(boardId, commentId, {
          userId: user.idx,
          reactionType,
        });
        const summary = response.data;
        setComments((prev) =>
          prev.map((comment) =>
            comment.idx === commentId
              ? {
                ...comment,
                likeCount: summary.likeCount,
                dislikeCount: summary.dislikeCount,
                userReaction: summary.userReaction,
              }
              : comment
          )
        );
      } catch (err) {
        const message = err.response?.data?.error || err.message || '댓글 반응 처리에 실패했습니다.';
        setCommentError(message);
      }
    },
    [boardId, requireLogin, redirectToLogin, user]
  );

  const handleDeleteBoard = useCallback(async () => {
    if (!boardId || !currentUser || board?.userId !== currentUser.idx) {
      return;
    }
    if (!window.confirm('이 게시글을 삭제하시겠습니까?')) {
      return;
    }
    try {
      await boardApi.deleteBoard(boardId);
      onBoardDeleted?.(boardId);
      onClose?.();
    } catch (err) {
      const message = err.response?.data?.error || err.message || '게시글 삭제에 실패했습니다.';
      setBoardError(message);
    }
  }, [boardId, currentUser, board, onBoardDeleted, onClose]);

  const handleDeleteComment = useCallback(
    async (commentId) => {
      if (!boardId || !currentUser) {
        return;
      }
      if (!window.confirm('이 댓글을 삭제하시겠습니까?')) {
        return;
      }
      try {
        await commentApi.delete(boardId, commentId);
        setComments((prev) => prev.filter((comment) => comment.idx !== commentId));
        // 댓글 삭제 시 카운트 감소를 위해 boardId와 isDelete=true 전달
        onCommentAdded?.(boardId, true);
      } catch (err) {
        const message = err.response?.data?.error || err.message || '댓글 삭제에 실패했습니다.';
        setCommentError(message);
      }
    },
    [boardId, currentUser, onCommentAdded]
  );

  if (!isOpen) {
    return null;
  }

  const handleContainerClick = (event) => {
    if (event.target === event.currentTarget) {
      onClose?.();
    }
  };

  return (
    <>
      <Backdrop onClick={onClose} />
      <PageContainer onClick={handleContainerClick}>
        <DetailCard onClick={(event) => event.stopPropagation()}>
          <DetailHeader>
            <HeaderTop>
              <BackButton type="button" onClick={onClose}>
                ← 목록으로
              </BackButton>

              <HeaderActions>
                <HeaderActionButton
                  type="button"
                  onClick={(event) => handleBoardReaction('LIKE', event)}
                  $active={board?.userReaction === 'LIKE'}
                >
                  ❤️ {board?.likes ?? 0}
                </HeaderActionButton>
                <HeaderActionButton
                  type="button"
                  onClick={(event) => handleBoardReaction('DISLIKE', event)}
                  $active={board?.userReaction === 'DISLIKE'}
                >
                  👎 {board?.dislikes ?? 0}
                </HeaderActionButton>
                {currentUser && board?.userId === currentUser.idx && (
                  <HeaderActionButton type="button" onClick={handleDeleteBoard}>
                    🗑 삭제
                  </HeaderActionButton>
                )}
                <HeaderActionButton type="button" onClick={handleReportClick}>
                  🚨 신고
                </HeaderActionButton>
              </HeaderActions>
            </HeaderTop>

            {boardError ? (
              <ErrorBanner>{boardError}</ErrorBanner>
            ) : loadingBoard ? (
              <SkeletonTitle />
            ) : (
              <>
                <CategoryBadge $color={categoryInfo?.color}>
                  <span>{categoryInfo?.icon}</span>
                  {categoryInfo?.label}
                </CategoryBadge>

                <Title>{board?.title}</Title>
                <MetaInfo>
                  <AuthorBadge>{board?.username || '알 수 없음'}</AuthorBadge>
                  <MetaDivider>•</MetaDivider>
                  <span>{formattedDate}</span>
                  {board?.userLocation && (
                    <>
                      <MetaDivider>•</MetaDivider>
                      <span>📍 {board.userLocation}</span>
                    </>
                  )}
                </MetaInfo>
              </>
            )}
          </DetailHeader>

          {loadingBoard ? (
            <SkeletonBody />
          ) : boardError ? null : (
            <>
              {board?.boardFilePath && (
                <HeroImage>
                  <img src={board.boardFilePath} alt={board.title} />
                </HeroImage>
              )}

              <ContentSection>
                <ContentText>{board?.content}</ContentText>
              </ContentSection>
            </>
          )}

          <CommentSection>
            <CommentHeader>
              <CommentTitle>댓글</CommentTitle>
              <CommentCount>{comments.length}개</CommentCount>
            </CommentHeader>

            {loadingComments ? (
              <LoadingState>댓글을 불러오는 중...</LoadingState>
            ) : commentError ? (
              <ErrorBanner>{commentError}</ErrorBanner>
            ) : comments.length === 0 ? (
              <EmptyState>
                <EmptyIcon>💬</EmptyIcon>
                <EmptyText>첫 댓글을 남겨보세요!</EmptyText>
              </EmptyState>
            ) : (
              <CommentList>
                {comments.map((comment) => (
                  <CommentItem key={comment.idx}>
                    <CommentAuthor>
                      <CommentAvatar>
                        {comment.username ? comment.username.charAt(0).toUpperCase() : 'U'}
                      </CommentAvatar>
                      <CommentAuthorInfo>
                        <CommentAuthorName>{comment.username || '알 수 없음'}</CommentAuthorName>
                        <CommentTimestamp>
                          {comment.createdAt
                            ? new Date(comment.createdAt).toLocaleString('ko-KR')
                            : ''}
                        </CommentTimestamp>
                      </CommentAuthorInfo>
                    </CommentAuthor>
                    <CommentBody>{comment.content}</CommentBody>
                    {comment.commentFilePath && (
                      <CommentImage>
                        <img src={comment.commentFilePath} alt="댓글 이미지" />
                      </CommentImage>
                    )}
                    <CommentStats>
                      <ReactionButton
                        type="button"
                        onClick={() => handleCommentReaction(comment.idx, 'LIKE')}
                        $active={comment.userReaction === 'LIKE'}
                      >
                        <ReactionIcon>👍</ReactionIcon>
                        <ReactionCount>{comment.likeCount ?? 0}</ReactionCount>
                      </ReactionButton>
                      <ReactionButton
                        type="button"
                        onClick={() => handleCommentReaction(comment.idx, 'DISLIKE')}
                        $active={comment.userReaction === 'DISLIKE'}
                      >
                        <ReactionIcon>👎</ReactionIcon>
                        <ReactionCount>{comment.dislikeCount ?? 0}</ReactionCount>
                      </ReactionButton>
                      {currentUser &&
                        (comment.userId === currentUser.idx ? (
                          <DeleteCommentButton type="button" onClick={() => handleDeleteComment(comment.idx)}>
                            삭제
                          </DeleteCommentButton>
                        ) : (
                          <ReportCommentButton type="button" onClick={() => handleCommentReport(comment.idx)}>
                            신고
                          </ReportCommentButton>
                        ))}
                    </CommentStats>
                  </CommentItem>
                ))}
              </CommentList>
            )}

            <CommentComposer>
              {!user ? (
                <LoginNotice>댓글을 작성하려면 로그인이 필요합니다.</LoginNotice>
              ) : (
                <CommentForm onSubmit={handleSubmitComment}>
                  <CommentTextarea
                    rows={4}
                    placeholder="댓글을 입력하세요"
                    value={commentContent}
                    onChange={(event) => setCommentContent(event.target.value)}
                    disabled={isSubmittingComment}
                  />

                  <FormFooter>
                    <UploadGroup>
                      <HiddenFileInput
                        id="community-detail-comment-image"
                        type="file"
                        accept="image/*"
                        onChange={handleFileSelect}
                        disabled={isUploading}
                      />
                      <FileSelectLabel htmlFor="community-detail-comment-image" $disabled={isUploading}>
                        {isUploading ? '업로드 중...' : '이미지 첨부'}
                      </FileSelectLabel>
                      {commentFilePath && (
                        <RemoveImageButton type="button" onClick={handleRemoveImage}>
                          첨부 삭제
                        </RemoveImageButton>
                      )}
                      <HelperText>최대 5MB까지 업로드할 수 있습니다.</HelperText>
                      {uploadError && <ErrorText>{uploadError}</ErrorText>}
                    </UploadGroup>

                    <SubmitButton type="submit" disabled={isSubmittingComment || isUploading}>
                      {isSubmittingComment ? '등록 중...' : '댓글 등록'}
                    </SubmitButton>
                  </FormFooter>

                  {commentError && !loadingComments && <ErrorText>{commentError}</ErrorText>}
                  {commentFilePath && (
                    <PreviewImageWrapper>
                      <PreviewImage src={commentFilePath} alt="댓글 이미지 미리보기" />
                    </PreviewImageWrapper>
                  )}
                </CommentForm>
              )}
            </CommentComposer>
          </CommentSection>
        </DetailCard>
      </PageContainer>
    </>
  );
};

export default CommunityDetailPage;

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(4px);
  z-index: 1090;
`;

const PageContainer = styled.div`
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  overflow-y: auto;
  padding: ${(props) => props.theme.spacing.xxl} ${(props) => props.theme.spacing.lg};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
    align-items: stretch;
  }
`;

const DetailCard = styled.article`
  width: min(1000px, 100%);
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  box-shadow: 0 22px 48px rgba(15, 23, 42, 0.25);
  border: 1px solid ${(props) => props.theme.colors.border};
  overflow: hidden;
  display: flex;
  flex-direction: column;

  @media (max-width: 768px) {
    width: 100%;
    border-radius: ${(props) => props.theme.borderRadius.lg};
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.2);
  }
`;

const DetailHeader = styled.header`
  padding: ${(props) => props.theme.spacing.xl} ${(props) => props.theme.spacing.xl};
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
  }
`;

const HeaderTop = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
    gap: ${(props) => props.theme.spacing.sm};
  }
`;

const BackButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  border: none;
  background: transparent;
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
  cursor: pointer;
  font-size: 0.95rem;
  transition: color 0.2s ease;

  &:hover {
    color: ${(props) => props.theme.colors.primary};
  }
`;

const HeaderActions = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
  flex-wrap: wrap;

  @media (max-width: 768px) {
    width: 100%;
    justify-content: flex-start;
  }
`;

const HeaderActionButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== '$active',
})`
  border: 1px solid
    ${(props) => (props.$active ? props.theme.colors.primary : props.theme.colors.border)};
  background: ${(props) =>
    props.$active ? props.theme.colors.primary : props.theme.colors.surface};
  color: ${(props) => (props.$active ? '#ffffff' : props.theme.colors.textSecondary)};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${(props) => props.theme.colors.primary};
    color: ${(props) => (props.$active ? '#ffffff' : props.theme.colors.primary)};
    transform: translateY(-1px);
    background: ${(props) =>
    props.$active ? props.theme.colors.primary : props.theme.colors.surfaceHover};
  }
`;

const CategoryBadge = styled.span`
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  background: ${(props) => `linear-gradient(135deg, ${props.$color} 0%, ${props.$color}dd 100%)`};
  color: #fff;
  font-weight: 600;
  font-size: 0.85rem;
  box-shadow: 0 6px 20px ${(props) => `${props.$color}30`};
`;

const Title = styled.h1`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
  font-size: ${(props) => props.theme.typography.h2.fontSize};
  line-height: 1.4;

  @media (max-width: 768px) {
    font-size: ${(props) => props.theme.typography.h3.fontSize};
  }
`;

const MetaInfo = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${(props) => props.theme.spacing.xs};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.9rem;
  align-items: center;
`;

const MetaDivider = styled.span`
  color: ${(props) => props.theme.colors.textLight};
`;

const AuthorBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  border-radius: ${(props) => props.theme.borderRadius.full};
  background: ${(props) => props.theme.colors.surfaceElevated};
  color: ${(props) => props.theme.colors.text};
  font-weight: 600;
  font-size: 0.9rem;
`;

const HeroImage = styled.div`
  width: 100%;
  max-height: 420px;
  overflow: hidden;
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
`;

const ContentSection = styled.section`
  padding: ${(props) => props.theme.spacing.xl} ${(props) => props.theme.spacing.xl};
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};
  background: ${(props) => props.theme.colors.surface};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
  }
`;

const ContentText = styled.p`
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.8;
  color: ${(props) => props.theme.colors.text};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
`;

const CommentSection = styled.section`
  padding: ${(props) => props.theme.spacing.xl};
  background: ${(props) => props.theme.colors.surfaceElevated};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xl};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
    gap: ${(props) => props.theme.spacing.md};
  }
`;

const CommentHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const CommentTitle = styled.h2`
  margin: 0;
  font-size: 1.2rem;
  color: ${(props) => props.theme.colors.text};
`;

const CommentCount = styled.span`
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
`;

const LoadingState = styled.div`
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const EmptyIcon = styled.div`
  font-size: 40px;
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

const CommentItem = styled.article`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.md};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
  box-shadow: 0 8px 24px ${(props) => props.theme.colors.shadow};
`;

const CommentAuthor = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
`;

const CommentAvatar = styled.div`
  width: 44px;
  height: 44px;
  border-radius: ${(props) => props.theme.borderRadius.full};
  background: ${(props) => props.theme.colors.gradient};
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 1rem;
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

const CommentBody = styled.p`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
  white-space: pre-wrap;
  line-height: 1.6;
`;

const CommentImage = styled.div`
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.borderLight};

  img {
    width: 100%;
    height: auto;
    display: block;
  }
`;

const CommentStats = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  margin-top: ${(props) => props.theme.spacing.sm};
`;

const ReactionButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== '$active',
})`
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid
    ${(props) => (props.$active ? props.theme.colors.primary : props.theme.colors.border)};
  background: ${(props) =>
    props.$active ? props.theme.colors.surfaceHover : props.theme.colors.surface};
  color: ${(props) => (props.$active ? props.theme.colors.primary : props.theme.colors.textSecondary)};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${(props) => props.theme.colors.primary};
    color: ${(props) => props.theme.colors.primary};
    transform: translateY(-1px);
  }
`;

const ReactionIcon = styled.span`
  font-size: 1rem;
`;

const ReactionCount = styled.span`
  font-size: 0.9rem;
`;

const DeleteCommentButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.error || '#dc2626'};
  background: transparent;
  color: ${(props) => props.theme.colors.error || '#dc2626'};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(220, 38, 38, 0.08);
    transform: translateY(-1px);
  }
`;

const ReportCommentButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.warning || '#f97316'};
  background: transparent;
  color: ${(props) => props.theme.colors.warning || '#f97316'};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(249, 115, 22, 0.1);
    transform: translateY(-1px);
  }
`;

const CommentComposer = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.lg};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const LoginNotice = styled.div`
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.95rem;
`;

const CommentForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const CommentTextarea = styled.textarea`
  padding: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  font-size: 0.95rem;
  resize: vertical;
  min-height: 140px;
  color: ${(props) => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const FormFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: ${(props) => props.theme.spacing.md};
  flex-wrap: wrap;
`;

const UploadGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const HiddenFileInput = styled.input`
  display: none;
`;

const FileSelectLabel = styled.label.withConfig({
  shouldForwardProp: (prop) => prop !== '$disabled',
})`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
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

const RemoveImageButton = styled.button`
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
    border-color: ${(props) => props.theme.colors.error || '#dc2626'};
    color: ${(props) => props.theme.colors.error || '#dc2626'};
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

const PreviewImageWrapper = styled.div`
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.border};
  max-width: 240px;
`;

const PreviewImage = styled.img`
  width: 100%;
  height: auto;
  display: block;
`;

const SkeletonBlock = styled.div`
  background: ${(props) => props.theme.colors.surfaceHover};
  border-radius: ${(props) => props.theme.borderRadius.md};
  animation: shimmer 1.2s ease-in-out infinite alternate;

  @keyframes shimmer {
    from {
      opacity: 0.55;
    }
    to {
      opacity: 0.9;
    }
  }
`;

const SkeletonTitle = styled(SkeletonBlock)`
  height: 28px;
  width: 70%;
`;

const SkeletonBody = styled(SkeletonBlock)`
  margin: ${(props) => props.theme.spacing.xl};
  height: 180px;
`;

const ErrorBanner = styled.div`
  background: rgba(220, 38, 38, 0.1);
  color: ${(props) => props.theme.colors.error || '#dc2626'};
  border: 1px solid rgba(220, 38, 38, 0.2);
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.md};
  font-size: 0.95rem;
`;

