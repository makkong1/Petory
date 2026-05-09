import React, { useCallback, useEffect, useMemo, useState } from 'react';
import styled, { useTheme } from 'styled-components';
import { boardApi } from '../../api/boardApi';
import { commentApi } from '../../api/commentApi';
import { reportApi } from '../../api/reportApi';
import { uploadApi } from '../../api/uploadApi';
import { usePermission } from '../../hooks/usePermission';
import { useAuth } from '../../contexts/AuthContext';
import PageNavigation from '../Common/PageNavigation';
import UserProfileModal from '../User/UserProfileModal';

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
  const themeObj = useTheme();
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
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);

  // 댓글 페이징 상태
  const [commentPage, setCommentPage] = useState(0);
  const [commentPageSize] = useState(20);
  const [commentTotalCount, setCommentTotalCount] = useState(0);

  const categoryInfo = useMemo(() => {
    if (!board?.category) {
      return null;
    }

    const DETAIL_CATEGORY_THEME_KEY = {
      ALL: 'all', 일상: 'daily', 자랑: 'pride', 질문: 'question',
      정보: 'info', 후기: 'review', 모임: 'meetup', 공지: 'notice',
      TIP: 'meetup', QUESTION: 'question', INFO: 'info',
      PRIDE: 'pride', STORY: 'daily',
    };

    const DETAIL_CATEGORY_META = {
      ALL: { label: '전체', icon: '📋' },
      일상: { label: '일상', icon: '📖' },
      자랑: { label: '자랑', icon: '🐾' },
      질문: { label: '질문', icon: '❓' },
      정보: { label: '정보', icon: '📢' },
      후기: { label: '후기', icon: '📝' },
      모임: { label: '모임', icon: '🤝' },
      공지: { label: '공지', icon: '📢' },
      TIP: { label: '꿀팁', icon: '💡' },
      QUESTION: { label: '질문', icon: '❓' },
      INFO: { label: '정보', icon: '📢' },
      PRIDE: { label: '자랑', icon: '🐾' },
      STORY: { label: '일상', icon: '📖' },
    };

    const cat = board.category;
    const meta = DETAIL_CATEGORY_META[cat] || { label: cat, icon: '📋' };
    const themeKey = DETAIL_CATEGORY_THEME_KEY[cat] || 'all';
    const colors = themeObj.colors.category;
    return { ...meta, color: colors[themeKey] || colors.all };
  }, [board, themeObj]);

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
    setCommentPage(0);
    setCommentTotalCount(0);
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

  // 댓글 페이징으로 가져오기
  const fetchComments = useCallback(async (pageNum = 0) => {
    if (!boardId) {
      return;
    }
    try {
      setLoadingComments(true);
      setCommentError('');
      const response = await commentApi.list(boardId, pageNum, commentPageSize);
      const pageData = response.data || {};
      const commentsData = pageData.comments || [];
      setComments(commentsData);

      setCommentTotalCount(pageData.totalCount || 0);
      setCommentPage(pageNum);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '댓글을 불러오지 못했습니다.';
      setCommentError(message);
    } finally {
      setLoadingComments(false);
    }
  }, [boardId, commentPageSize]);

  const handleCommentPageChange = useCallback((newPage) => {
    const totalPages = Math.max(1, Math.ceil(commentTotalCount / commentPageSize));
    if (newPage >= 0 && newPage < totalPages) {
      fetchComments(newPage);
    }
  }, [commentTotalCount, commentPageSize, fetchComments]);

  useEffect(() => {
    if (!isOpen) {
      resetState();
      return;
    }
    fetchBoard();
    fetchComments(0);
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
      await commentApi.create(boardId, payload);
      // 댓글 목록 새로고침
      await fetchComments(0);
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
        // 댓글 목록 새로고침
        await fetchComments(0);
        // 댓글 삭제 시 카운트 감소를 위해 boardId와 isDelete=true 전달
        onCommentAdded?.(boardId, true);
      } catch (err) {
        const message = err.response?.data?.error || err.message || '댓글 삭제에 실패했습니다.';
        setCommentError(message);
      }
    },
    [boardId, currentUser, fetchComments, onCommentAdded]
  );

  const handleViewProfile = useCallback((userId) => {
    setSelectedUserId(userId);
    setIsProfileModalOpen(true);
  }, []);

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
              <CommentCount>{commentTotalCount > 0 ? `${comments.length} / ${commentTotalCount}개` : `${comments.length}개`}</CommentCount>
            </CommentHeader>

            {loadingComments && comments.length === 0 ? (
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
                        <CommentAuthorName
                          onClick={() => handleViewProfile(comment.userId)}
                          style={{ cursor: 'pointer' }}
                        >
                          {comment.username || '알 수 없음'}
                        </CommentAuthorName>
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

            {commentTotalCount > 0 && (
              <CommentPaginationWrapper>
                <PageNavigation
                  currentPage={commentPage}
                  totalCount={commentTotalCount}
                  pageSize={commentPageSize}
                  onPageChange={handleCommentPageChange}
                  loading={loadingComments}
                />
              </CommentPaginationWrapper>
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

      <UserProfileModal
        isOpen={isProfileModalOpen}
        userId={selectedUserId}
        onClose={() => {
          setIsProfileModalOpen(false);
          setSelectedUserId(null);
        }}
      />
    </>
  );
};

export default CommunityDetailPage;

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  background: ${(props) => props.theme.colors.overlay};
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
  box-shadow: ${(props) => props.theme.shadows.xl};
  border: 1px solid ${(props) => props.theme.colors.border};
  overflow: hidden;
  display: flex;
  flex-direction: column;

  @media (max-width: 768px) {
    width: 100%;
    border-radius: ${(props) => props.theme.borderRadius.lg};
    box-shadow: ${(props) => props.theme.shadows.lg};
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
  font-size: ${(props) => props.theme.typography.body1.fontSize};
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
  color: ${(props) => (props.$active ? props.theme.colors.textInverse : props.theme.colors.textSecondary)};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${(props) => props.theme.colors.primary};
    color: ${(props) => (props.$active ? props.theme.colors.textInverse : props.theme.colors.primary)};
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
  color: ${(props) => props.theme.colors.textInverse};
  font-weight: 600;
  font-size: ${(props) => props.theme.typography.body2.fontSize};
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
  font-size: ${(props) => props.theme.typography.body2.fontSize};
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
  font-size: ${(props) => props.theme.typography.body2.fontSize};
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
  font-size: ${(props) => props.theme.typography.h3.fontSize};
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
  font-size: ${(props) => props.theme.typography.hero.fontSize};
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
  color: ${(props) => props.theme.colors.textInverse};
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: ${(props) => props.theme.typography.body1.fontSize};
`;

const CommentAuthorInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const CommentAuthorName = styled.span`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
  transition: color 0.2s ease;

  &:hover {
    color: ${(props) => props.theme.colors.primary};
  }
`;

const CommentTimestamp = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
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
  font-size: ${(props) => props.theme.typography.body2.fontSize};
`;

const DeleteCommentButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.error};
  background: transparent;
  color: ${(props) => props.theme.colors.error};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.errorSoft};
    transform: translateY(-1px);
  }
`;

const ReportCommentButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.warning};
  background: transparent;
  color: ${(props) => props.theme.colors.warning};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.warningSoft};
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
  font-size: ${(props) => props.theme.typography.body1.fontSize};
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
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  resize: vertical;
  min-height: 140px;
  color: ${(props) => props.theme.colors.text};

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: ${(props) => props.theme.shadows.focus};
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
    border-color: ${(props) => props.theme.colors.error};
    color: ${(props) => props.theme.colors.error};
  }
`;

const HelperText = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorText = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.error};
`;

const SubmitButton = styled.button`
  align-self: flex-end;
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
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
  background: ${(props) => props.theme.colors.errorSoft};
  color: ${(props) => props.theme.colors.error};
  border: 1px solid ${(props) => props.theme.colors.error}33;
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.md};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
`;

const CommentPaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.md} 0;
  margin-top: ${props => props.theme.spacing.sm};
`;

