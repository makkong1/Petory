import React, { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { careRequestApi } from '../../api/careRequestApi';
import { uploadApi } from '../../api/uploadApi';
import { reportApi } from '../../api/reportApi';
import { getOrCreateDirectConversation, createConversation } from '../../api/chatApi';
import { usePermission } from '../../hooks/usePermission';
import { useAuth } from '../../contexts/AuthContext';
import UserProfileModal from '../User/UserProfileModal';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

const CareRequestDetailPage = ({
  isOpen,
  careRequestId,
  onClose,
  onCommentAdded,
  currentUser,
  onCareRequestDeleted,
}) => {
  const { requireLogin } = usePermission();
  const { user, redirectToLogin } = useAuth();

  const [careRequest, setCareRequest] = useState(null);
  const [loadingCareRequest, setLoadingCareRequest] = useState(false);
  const [careRequestError, setCareRequestError] = useState('');

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
  const [isStartingChat, setIsStartingChat] = useState(false);

  const formattedDate = useMemo(() => {
    if (!careRequest?.createdAt) {
      return '';
    }
    const date = new Date(careRequest.createdAt);
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
  }, [careRequest]);

  const getStatusLabel = (status) => {
    switch (status) {
      case 'OPEN': return '모집중';
      case 'IN_PROGRESS': return '진행중';
      case 'COMPLETED': return '완료';
      case 'CANCELLED': return '취소';
      default: return status;
    }
  };

  const resetState = useCallback(() => {
    setCareRequest(null);
    setLoadingCareRequest(false);
    setCareRequestError('');
    setComments([]);
    setLoadingComments(false);
    setCommentError('');
    setIsSubmittingComment(false);
    setCommentContent('');
    setCommentFilePath('');
    setIsUploading(false);
    setUploadError('');
  }, []);

  const fetchCareRequest = useCallback(async () => {
    if (!careRequestId) {
      return;
    }
    try {
      setLoadingCareRequest(true);
      setCareRequestError('');
      const response = await careRequestApi.getCareRequest(careRequestId);
      setCareRequest(response.data || null);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '펫케어 요청을 불러오지 못했습니다.';
      setCareRequestError(message);
    } finally {
      setLoadingCareRequest(false);
    }
  }, [careRequestId]);

  const fetchComments = useCallback(async () => {
    if (!careRequestId) {
      return;
    }
    try {
      setLoadingComments(true);
      setCommentError('');
      const response = await careRequestApi.getComments(careRequestId);
      setComments(response.data || []);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '댓글을 불러오지 못했습니다.';
      setCommentError(message);
    } finally {
      setLoadingComments(false);
    }
  }, [careRequestId]);

  useEffect(() => {
    if (!isOpen) {
      resetState();
      return;
    }
    fetchCareRequest();
    fetchComments();
  }, [isOpen, careRequestId, fetchCareRequest, fetchComments, resetState]);

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
        category: 'care-request-comment',
        ownerType: user ? 'user' : 'guest',
        ownerId: user?.idx ?? undefined,
        entityId: careRequestId,
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
    if (!careRequestId) {
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

    // SERVICE_PROVIDER만 댓글 작성 가능
    if (user.role !== 'SERVICE_PROVIDER') {
      setCommentError('당신은 댓글 작성 불가입니다.');
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
      const response = await careRequestApi.createComment(careRequestId, payload);
      setComments((prev) => [...prev, response.data]);
      setCommentContent('');
      setCommentFilePath('');
      setUploadError('');
      if (onCommentAdded) {
        onCommentAdded();
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

  const handleDeleteComment = useCallback(
    async (commentId) => {
      if (!careRequestId || !currentUser) {
        return;
      }
      if (!window.confirm('이 댓글을 삭제하시겠습니까?')) {
        return;
      }
      try {
        await careRequestApi.deleteComment(careRequestId, commentId);
        setComments((prev) => prev.filter((comment) => comment.idx !== commentId));
        onCommentAdded?.();
      } catch (err) {
        const message = err.response?.data?.error || err.message || '댓글 삭제에 실패했습니다.';
        setCommentError(message);
      }
    },
    [careRequestId, currentUser, onCommentAdded]
  );

  const handleReportProvider = useCallback(
    async (providerId) => {
      const { requiresRedirect } = requireLogin();
      if (requiresRedirect) {
        redirectToLogin();
        return;
      }
      if (!user || !providerId) {
        return;
      }
      if (!window.confirm('해당 서비스 제공자를 신고하시겠습니까?')) {
        return;
      }
      const reason = window.prompt('신고 사유를 입력해주세요.');
      if (!reason || !reason.trim()) {
        return;
      }
      try {
        await reportApi.submit({
          targetType: 'PET_CARE_PROVIDER',
          targetIdx: providerId,
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

  const handleDeleteCareRequest = useCallback(async () => {
    if (!careRequestId || !currentUser || careRequest?.userId !== currentUser.idx) {
      return;
    }
    if (!window.confirm('이 펫케어 요청을 삭제하시겠습니까?')) {
      return;
    }
    try {
      await careRequestApi.deleteCareRequest(careRequestId);
      onCareRequestDeleted?.(careRequestId);
      onClose?.();
    } catch (err) {
      const message = err.response?.data?.error || err.message || '펫케어 요청 삭제에 실패했습니다.';
      setCareRequestError(message);
    }
  }, [careRequestId, currentUser, careRequest, onCareRequestDeleted, onClose]);

  const handleViewProfile = useCallback((userId) => {
    setSelectedUserId(userId);
    setIsProfileModalOpen(true);
  }, []);

  const handleStartChat = useCallback(async (otherUserId) => {
    if (!currentUser || !otherUserId || !careRequestId) {
      return;
    }

    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }

    try {
      setIsStartingChat(true);
      // 펫케어 요청과 연결된 채팅방 생성
      const conversation = await createConversation({
        conversationType: 'DIRECT',
        relatedType: 'CARE_REQUEST',
        relatedIdx: Number(careRequestId), // 숫자로 변환
        title: null,
        participantUserIds: [currentUser.idx, otherUserId]
      });
      
      // 채팅 위젯 열기 (ChatWidget가 전역적으로 관리된다고 가정)
      if (window.openChatWidget) {
        window.openChatWidget(conversation.idx);
      } else {
        // 채팅 탭으로 이동
        if (window.setActiveTab) {
          window.setActiveTab('chat');
        }
      }
      
      alert('채팅방이 열렸습니다.');
    } catch (err) {
      const message = err.response?.data?.error || err.message || '채팅 시작에 실패했습니다.';
      alert(message);
    } finally {
      setIsStartingChat(false);
    }
  }, [currentUser, careRequestId, requireLogin, redirectToLogin]);

  if (!isOpen) {
    return null;
  }

  const handleContainerClick = (event) => {
    if (event.target === event.currentTarget) {
      onClose?.();
    }
  };

  const canWriteComment = user && user.role === 'SERVICE_PROVIDER';

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
                {currentUser && careRequest?.userId === currentUser.idx && (
                  <HeaderActionButton type="button" onClick={handleDeleteCareRequest}>
                    🗑 삭제
                  </HeaderActionButton>
                )}
              </HeaderActions>
            </HeaderTop>

            {careRequestError ? (
              <ErrorBanner>{careRequestError}</ErrorBanner>
            ) : loadingCareRequest ? (
              <SkeletonTitle />
            ) : (
              <>
                <StatusBadge status={careRequest?.status}>
                  {getStatusLabel(careRequest?.status)}
                </StatusBadge>

                <Title>{careRequest?.title}</Title>
                <MetaInfo>
                  <AuthorBadge>{careRequest?.username || '알 수 없음'}</AuthorBadge>
                  <MetaDivider>•</MetaDivider>
                  <span>{formattedDate}</span>
                  {careRequest?.userLocation && (
                    <>
                      <MetaDivider>•</MetaDivider>
                      <span>📍 {careRequest.userLocation}</span>
                    </>
                  )}
                </MetaInfo>
              </>
            )}
          </DetailHeader>

          {loadingCareRequest ? (
            <SkeletonBody />
          ) : careRequestError ? null : (
            <ContentWrapper>
              <ContentSection>
                <ContentText>{careRequest?.description}</ContentText>
                {careRequest?.date ? (
                  <DateInfo>
                    <DateLabel>요청 일시</DateLabel>
                    <DateValue>
                      <TimeIcon>🕐</TimeIcon>
                      <span>
                        {(() => {
                          try {
                            const dateObj = new Date(careRequest.date);
                            if (!isNaN(dateObj.getTime())) {
                              const year = dateObj.getFullYear();
                              const month = dateObj.getMonth() + 1;
                              const day = dateObj.getDate();
                              const hour = dateObj.getHours();
                              const minute = dateObj.getMinutes();
                              const ampm = hour >= 12 ? '오후' : '오전';
                              const displayHour = hour > 12 ? hour - 12 : (hour === 0 ? 12 : hour);
                              return `${year}년 ${month}월 ${day}일 ${ampm} ${displayHour}:${String(minute).padStart(2, '0')}`;
                            }
                          } catch (e) {
                            console.error('Date parsing error:', e);
                          }
                          return careRequest.date;
                        })()}
                      </span>
                    </DateValue>
                  </DateInfo>
                ) : null}
              </ContentSection>

              {careRequest?.pet && (
                <PetInfoSection>
                  <PetInfoTitle>관련 반려동물 정보</PetInfoTitle>
                  <PetInfoCard>
                    <PetImageWrapper>
                      {careRequest.pet.profileImageUrl ? (
                        <PetImage 
                          src={careRequest.pet.profileImageUrl} 
                          alt={careRequest.pet.petName}
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
                      <PetName>{careRequest.pet.petName}</PetName>
                      <PetDetail>
                        {careRequest.pet.petType === 'DOG' ? '강아지' : 
                         careRequest.pet.petType === 'CAT' ? '고양이' : 
                         careRequest.pet.petType === 'BIRD' ? '새' :
                         careRequest.pet.petType === 'RABBIT' ? '토끼' :
                         careRequest.pet.petType === 'HAMSTER' ? '햄스터' : '기타'}
                        {' · '}
                        {careRequest.pet.breed || '품종 미상'}
                      </PetDetail>
                      {careRequest.pet.age && <PetDetail>나이: {careRequest.pet.age}</PetDetail>}
                      {careRequest.pet.gender && (
                        <PetDetail>
                          성별: {careRequest.pet.gender === 'M' ? '수컷' : careRequest.pet.gender === 'F' ? '암컷' : '미확인'}
                        </PetDetail>
                      )}
                      {careRequest.pet.color && <PetDetail>색상: {careRequest.pet.color}</PetDetail>}
                      {careRequest.pet.weight && <PetDetail>몸무게: {careRequest.pet.weight}kg</PetDetail>}
                      {careRequest.pet.healthInfo && <PetDetail>건강 정보: {careRequest.pet.healthInfo}</PetDetail>}
                      {careRequest.pet.specialNotes && <PetDetail>특이사항: {careRequest.pet.specialNotes}</PetDetail>}
                    </PetDetails>
                  </PetInfoCard>
                </PetInfoSection>
              )}
            </ContentWrapper>
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
                        <CommentAuthorName
                          onClick={() => handleViewProfile(comment.userId)}
                          style={{ cursor: 'pointer' }}
                        >
                          {comment.username || '알 수 없음'}
                          {comment.userRole === 'SERVICE_PROVIDER' && (
                            <ProviderBadge>서비스 제공자</ProviderBadge>
                          )}
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
                    <CommentActions>
                      {currentUser && comment.userId === currentUser.idx && (
                        <DeleteCommentButton type="button" onClick={() => handleDeleteComment(comment.idx)}>
                          삭제
                        </DeleteCommentButton>
                      )}
                      {currentUser &&
                        careRequest?.userId === currentUser.idx &&
                        comment.userId !== currentUser.idx && (
                          <ChatButton
                            type="button"
                            onClick={() => handleStartChat(comment.userId)}
                            disabled={isStartingChat}
                          >
                            {isStartingChat ? '연결 중...' : '💬 채팅하기'}
                          </ChatButton>
                        )}
                      {currentUser &&
                        comment.userRole === 'SERVICE_PROVIDER' &&
                        comment.userId !== currentUser.idx && (
                          <ReportProviderButton type="button" onClick={() => handleReportProvider(comment.userId)}>
                            신고
                          </ReportProviderButton>
                        )}
                    </CommentActions>
                  </CommentItem>
                ))}
              </CommentList>
            )}

            <CommentComposer>
              {!user ? (
                <LoginNotice>댓글을 작성하려면 로그인이 필요합니다.</LoginNotice>
              ) : !canWriteComment ? (
                <LoginNotice style={{ color: '#dc2626' }}>
                  당신은 댓글 작성 불가입니다.
                </LoginNotice>
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
                        id="care-request-detail-comment-image"
                        type="file"
                        accept="image/*"
                        onChange={handleFileSelect}
                        disabled={isUploading}
                      />
                      <FileSelectLabel htmlFor="care-request-detail-comment-image" $disabled={isUploading}>
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

export default CareRequestDetailPage;

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
  overflow-x: hidden;
  padding: ${(props) => props.theme.spacing.xxl} ${(props) => props.theme.spacing.lg};
  -webkit-overflow-scrolling: touch;

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
  overflow: visible;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - ${(props) => props.theme.spacing.xxl} * 2);
  margin: ${(props) => props.theme.spacing.xl} 0;

  @media (max-width: 768px) {
    width: 100%;
    border-radius: ${(props) => props.theme.borderRadius.lg};
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.2);
    max-height: calc(100vh - ${(props) => props.theme.spacing.md} * 2);
    margin: ${(props) => props.theme.spacing.md} 0;
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

const HeaderActionButton = styled.button`
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.textSecondary};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: ${(props) => props.theme.colors.error || '#dc2626'};
    color: ${(props) => props.theme.colors.error || '#dc2626'};
    transform: translateY(-1px);
  }
`;

const StatusBadge = styled.span`
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  background: ${(props) => {
    switch (props.status) {
      case 'OPEN': return props.theme.colors.success || '#22c55e';
      case 'IN_PROGRESS': return props.theme.colors.warning || '#f59e0b';
      case 'COMPLETED': return props.theme.colors.textLight || '#94a3b8';
      default: return props.theme.colors.primary;
    }
  }};
  color: white;
  font-weight: 600;
  font-size: 0.85rem;
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

const ContentWrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xl};
  overflow-y: auto;
  flex: 1;
  min-height: 0;

  @media (max-width: 1024px) {
    gap: ${(props) => props.theme.spacing.lg};
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

const PetInfoSection = styled.section`
  padding: ${(props) => props.theme.spacing.xl} ${(props) => props.theme.spacing.xl};
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};
  background: ${(props) => props.theme.colors.surfaceElevated};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
  }
`;

const PetInfoTitle = styled.h3`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  font-size: 1.1rem;
  color: ${(props) => props.theme.colors.text};
  font-weight: 600;
`;

const PetInfoCard = styled.div`
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: ${(props) => props.theme.spacing.lg};
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.lg};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
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

  @media (max-width: 768px) {
    max-width: 200px;
    margin: 0 auto;
  }
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
  font-size: 1.2rem;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
  margin-bottom: ${(props) => props.theme.spacing.xs};
`;

const PetDetail = styled.div`
  font-size: 0.95rem;
  color: ${(props) => props.theme.colors.textSecondary};
  line-height: 1.6;
`;

const ContentText = styled.p`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  white-space: pre-wrap;
  line-height: 1.8;
  color: ${(props) => props.theme.colors.text};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
`;

const DateInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  margin-top: ${(props) => props.theme.spacing.md};
`;

const DateLabel = styled.span`
  font-weight: 600;
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const DateValue = styled.span`
  color: ${(props) => props.theme.colors.text};
  font-weight: 500;
  font-size: 1rem;
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
`;

const TimeIcon = styled.span`
  font-size: 1.1rem;
  line-height: 1;
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
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
`;

const ProviderBadge = styled.span`
  font-size: 0.75rem;
  padding: 2px 6px;
  border-radius: ${(props) => props.theme.borderRadius.sm};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  font-weight: 500;
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

const CommentActions = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  margin-top: ${(props) => props.theme.spacing.sm};
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

const ReportProviderButton = styled.button`
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
    background: rgba(249, 115, 22, 0.12);
    transform: translateY(-1px);
  }
`;

const ChatButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.primary};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.primaryDark};
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(255, 126, 54, 0.3);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
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

