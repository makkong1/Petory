import React, { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../../contexts/AuthContext';
import { getOrCreateDirectConversation } from '../../../api/chatApi';
import { careRequestApi } from '../../../api/careRequestApi';
import { userProfileApi } from '../../../api/userApi';
import {
  InfoPanel as BaseInfoPanel,
  PanelHeader, CloseButton, PanelTitle,
  InfoRow, InfoLabel, InfoValue, InfoGrid, ActionRow, Divider,
} from '../shared/BaseInfoPanel';

const formatActivityArea = (location) => {
  if (!location) return '활동 지역 미등록';
  const normalized = String(location).replace(/\s+/g, ' ').trim();
  const tokens = normalized.split(' ');

  if (tokens.length >= 2) {
    return `${tokens[0]}·${tokens[1]} 활동`;
  }

  return `${normalized} 활동`;
};

const formatRating = (rating) => {
  if (rating == null) return '-';
  return Number(rating).toFixed(1);
};

const CareLayer = ({ selectedItem, onClose }) => {
  const { user } = useAuth();
  const [chatLoading, setChatLoading] = useState(false);
  const [chatError, setChatError] = useState(null);
  const [comments, setComments] = useState([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentsError, setCommentsError] = useState(null);
  const [providerProfiles, setProviderProfiles] = useState({});
  const [profileLoadingMap, setProfileLoadingMap] = useState({});
  const [activeReviewUserId, setActiveReviewUserId] = useState(null);
  const r = selectedItem?.raw;

  const isOwner = user && r && (user.idx === r.userIdx || user.idx === r.userId);

  const dateStr = (r?.date || r?.careDate)
    ? new Date(r?.date || r?.careDate).toLocaleString('ko-KR', {
        year: 'numeric', month: 'long', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
      })
    : '';

  const statusLabel = { OPEN: '모집중', IN_PROGRESS: '진행중', COMPLETED: '완료', CANCELLED: '취소' }[r?.status] || r?.status;

  const loadProviderProfiles = useCallback(async (userIds, isCancelled = () => false) => {
    if (userIds.length === 0) return;

    setProfileLoadingMap(prev => {
      const next = { ...prev };
      userIds.forEach((userId) => {
        next[userId] = true;
      });
      return next;
    });

    const profileEntries = await Promise.all(userIds.map(async (userId) => {
      try {
        const response = await userProfileApi.getUserProfile(userId);
        return [userId, response.data];
      } catch (error) {
        console.error('제공자 프로필 조회 실패:', error);
        return [userId, null];
      }
    }));

    if (isCancelled()) return;

    setProviderProfiles(prev => {
      const next = { ...prev };
      profileEntries.forEach(([userId, profile]) => {
        next[userId] = profile;
      });
      return next;
    });

    setProfileLoadingMap(prev => {
      const next = { ...prev };
      userIds.forEach((userId) => {
        delete next[userId];
      });
      return next;
    });
  }, []);

  useEffect(() => {
    if (!r?.idx) {
      setComments([]);
      setCommentsError(null);
      setActiveReviewUserId(null);
      return;
    }

    let cancelled = false;

    const fetchComments = async () => {
      setCommentsLoading(true);
      setCommentsError(null);
      setComments([]);
      setActiveReviewUserId(null);

      try {
        const response = await careRequestApi.getComments(r.idx);
        if (cancelled) return;

        const nextComments = response.data || [];
        setComments(nextComments);

        const providerUserIds = [...new Set(
          nextComments
            .map(comment => comment.userId)
            .filter(Boolean)
        )];

        await loadProviderProfiles(providerUserIds, () => cancelled);
      } catch (error) {
        if (cancelled) return;
        console.error('케어 댓글 조회 실패:', error);
        setCommentsError('제공자 댓글을 불러오지 못했습니다.');
      } finally {
        if (!cancelled) {
          setCommentsLoading(false);
        }
      }
    };

    fetchComments();

    return () => {
      cancelled = true;
    };
  }, [r.idx, loadProviderProfiles]);

  const handleChat = async () => {
    const ownerId = r.userIdx || r.userId;
    if (!user || !ownerId || user.idx === ownerId) return;
    setChatLoading(true);
    try {
      const conversation = await getOrCreateDirectConversation(ownerId);
      if (window.openChatWidget) {
        window.openChatWidget(conversation.idx);
      }
    } catch (err) {
      setChatError('채팅 연결에 실패했습니다.');
    } finally {
      setChatLoading(false);
    }
  };

  const handleProviderChat = async (providerId) => {
    if (!user || !providerId || user.idx === providerId) return;

    setChatLoading(true);
    setChatError(null);

    try {
      const conversation = await getOrCreateDirectConversation(providerId);
      if (window.openChatWidget) {
        window.openChatWidget(conversation.idx);
      }
    } catch (error) {
      console.error('제공자 채팅 연결 실패:', error);
      setChatError('제공자와 채팅 연결에 실패했습니다.');
    } finally {
      setChatLoading(false);
    }
  };

  const handleReviewOpen = async (providerId) => {
    if (!providerId) return;
    setActiveReviewUserId(providerId);

    if (providerProfiles[providerId] === undefined && !profileLoadingMap[providerId]) {
      await loadProviderProfiles([providerId]);
    }
  };

  const activeReviewProfile = activeReviewUserId ? providerProfiles[activeReviewUserId] : null;

  if (!selectedItem || !r) return null;

  return (
    <>
    <InfoPanel $width="380px" $maxHeight="82vh">
      <PanelHeader>
        <TypeBadge>💛 펫케어</TypeBadge>
        <CloseButton onClick={onClose} aria-label="닫기">✕</CloseButton>
      </PanelHeader>

      <PanelTitle>{selectedItem.title}</PanelTitle>

      <InfoGrid>
        {r.status && (
          <InfoRow>
            <InfoLabel>상태</InfoLabel>
            <StatusBadge $status={r.status}>{statusLabel}</StatusBadge>
          </InfoRow>
        )}
        {dateStr && <InfoRow><InfoLabel>일시</InfoLabel><InfoValue>{dateStr}</InfoValue></InfoRow>}
        {r.address && <InfoRow><InfoLabel>위치</InfoLabel><InfoValue>{r.address}</InfoValue></InfoRow>}
        {r.petName && <InfoRow><InfoLabel>반려동물</InfoLabel><InfoValue>{r.petName}</InfoValue></InfoRow>}
        {r.offeredCoins != null && (
          <InfoRow>
            <InfoLabel>보상</InfoLabel>
            <InfoValue><CoinText>💰 {Number(r.offeredCoins).toLocaleString()} 코인</CoinText></InfoValue>
          </InfoRow>
        )}
        {r.description && (
          <InfoRow>
            <InfoLabel>내용</InfoLabel>
            <InfoValue><Description>{r.description}</Description></InfoValue>
          </InfoRow>
        )}
        <Divider />
        <SectionHeader>
          <SectionTitle>제공자 댓글</SectionTitle>
          <SectionMeta>{r.commentCount ?? comments.length}개</SectionMeta>
        </SectionHeader>
        {commentsLoading ? (
          <EmptyState>댓글을 불러오는 중입니다.</EmptyState>
        ) : commentsError ? (
          <EmptyState>{commentsError}</EmptyState>
        ) : comments.length === 0 ? (
          <EmptyState>아직 제공자 댓글이 없습니다.</EmptyState>
        ) : (
          <CommentList>
            {comments.map((comment) => {
              const profile = providerProfiles[comment.userId];
              const displayName =
                profile?.user?.nickname ||
                comment.nickname ||
                profile?.user?.username ||
                comment.username ||
                '제공자';
              const activityArea = formatActivityArea(
                profile?.user?.location || comment.userLocation
              );
              const reviewCount = profile?.reviewCount ?? 0;
              const completedCareCount = profile?.completedCareCount ?? 0;

              return (
                <CommentCard key={comment.idx}>
                  <CommentHeader>
                    <ProviderIdentity>
                      <ProviderAvatar>{displayName.charAt(0)}</ProviderAvatar>
                      <ProviderInfo>
                        <ProviderName>{displayName}</ProviderName>
                        <ProviderStats>
                          ⭐ {formatRating(profile?.averageRating)} (후기 {reviewCount}) · 케어 완료 {completedCareCount}건 · {activityArea}
                        </ProviderStats>
                      </ProviderInfo>
                    </ProviderIdentity>
                    <CommentDate>
                      {comment.createdAt
                        ? new Date(comment.createdAt).toLocaleDateString('ko-KR')
                        : ''}
                    </CommentDate>
                  </CommentHeader>
                  <CommentContent>{comment.content}</CommentContent>
                  <CommentActions>
                    <SecondaryButton
                      type="button"
                      onClick={() => handleReviewOpen(comment.userId)}
                    >
                      후기 보기
                    </SecondaryButton>
                    {isOwner && user?.idx !== comment.userId && (
                      <PrimaryButton
                        type="button"
                        onClick={() => handleProviderChat(comment.userId)}
                        disabled={chatLoading}
                      >
                        {chatLoading ? '연결 중...' : '채팅하기'}
                      </PrimaryButton>
                    )}
                  </CommentActions>
                </CommentCard>
              );
            })}
          </CommentList>
        )}
      </InfoGrid>

      {chatError && <ActionErrorMsg>{chatError}</ActionErrorMsg>}
      {user && !isOwner && r.status === 'OPEN' && (
        <ActionRow>
          <ChatButton onClick={() => { setChatError(null); handleChat(); }} disabled={chatLoading}>
            {chatLoading ? '연결 중...' : '💬 채팅으로 문의하기'}
          </ChatButton>
        </ActionRow>
      )}
      {isOwner && <OwnerBadge>✅ 내가 등록한 케어 요청</OwnerBadge>}
    </InfoPanel>
    {activeReviewUserId && (
      <ReviewModalOverlay onClick={() => setActiveReviewUserId(null)}>
        <ReviewModal onClick={(event) => event.stopPropagation()}>
          <ReviewModalHeader>
            <div>
              <ReviewModalTitle>
                {(activeReviewProfile?.user?.nickname || activeReviewProfile?.user?.username || '제공자')} 후기
              </ReviewModalTitle>
              <ReviewModalStats>
                ⭐ {formatRating(activeReviewProfile?.averageRating)} · 후기 {activeReviewProfile?.reviewCount ?? 0}개 · 케어 완료 {activeReviewProfile?.completedCareCount ?? 0}건
              </ReviewModalStats>
            </div>
            <CloseButton onClick={() => setActiveReviewUserId(null)} aria-label="후기 닫기">✕</CloseButton>
          </ReviewModalHeader>
          <ReviewMeta>{formatActivityArea(activeReviewProfile?.user?.location)}</ReviewMeta>
          {profileLoadingMap[activeReviewUserId] ? (
            <ReviewEmptyState>후기를 불러오는 중입니다.</ReviewEmptyState>
          ) : activeReviewProfile?.reviews?.length > 0 ? (
            <ReviewList>
              {activeReviewProfile.reviews.slice(0, 3).map((review) => (
                <ReviewCard key={review.idx}>
                  <ReviewCardHeader>
                    <ReviewAuthor>{review.reviewerName || '요청자'}</ReviewAuthor>
                    <ReviewRating>{'⭐'.repeat(review.rating)}</ReviewRating>
                  </ReviewCardHeader>
                  <ReviewComment>{review.comment || '등록된 코멘트가 없습니다.'}</ReviewComment>
                  <ReviewDate>
                    {review.createdAt
                      ? new Date(review.createdAt).toLocaleDateString('ko-KR')
                      : ''}
                  </ReviewDate>
                </ReviewCard>
              ))}
            </ReviewList>
          ) : (
            <ReviewEmptyState>아직 등록된 펫케어 후기가 없습니다.</ReviewEmptyState>
          )}
        </ReviewModal>
      </ReviewModalOverlay>
    )}
    </>
  );
};

export default CareLayer;

const InfoPanel = styled(BaseInfoPanel)``;

const TypeBadge = styled.span`
  font-size: 12px;
  color: ${props => props.theme.colors.domain.care};
  font-weight: 600;
`;

const StatusBadge = styled.span`
  font-size: 12px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 8px;
  background: ${props => {
    if (props.$status === 'OPEN') return props.theme.colors.infoSoft;
    if (props.$status === 'IN_PROGRESS') return props.theme.colors.warningSoft;
    if (props.$status === 'COMPLETED') return props.theme.colors.successSoft;
    if (props.$status === 'CANCELLED') return props.theme.colors.errorSoft;
    return props.theme.colors.surfaceSoft;
  }};
  color: ${props => {
    if (props.$status === 'OPEN') return props.theme.colors.status.open;
    if (props.$status === 'IN_PROGRESS') return props.theme.colors.status.inProgress;
    if (props.$status === 'COMPLETED') return props.theme.colors.status.completed;
    if (props.$status === 'CANCELLED') return props.theme.colors.status.cancelled;
    return props.theme.colors.textMuted;
  }};
`;

const CoinText = styled.span`
  color: ${props => props.theme.colors.ai.text};
  font-weight: 600;
`;

const Description = styled.span`
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
`;

const SectionHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
`;

const SectionTitle = styled.h4`
  font-size: 13px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
`;

const SectionMeta = styled.span`
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
`;

const CommentList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 8px;
`;

const CommentCard = styled.div`
  padding: 12px;
  border-radius: 12px;
  background: ${props => props.theme.colors.surfaceSoft};
  border: 1px solid ${props => props.theme.colors.border};
`;

const CommentHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
`;

const ProviderIdentity = styled.div`
  display: flex;
  gap: 10px;
  min-width: 0;
`;

const ProviderAvatar = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: ${props => props.theme.colors.domain.care};
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
`;

const ProviderInfo = styled.div`
  min-width: 0;
`;

const ProviderName = styled.div`
  font-size: 13px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
`;

const ProviderStats = styled.div`
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: ${props => props.theme.colors.textSecondary};
`;

const CommentDate = styled.div`
  font-size: 11px;
  color: ${props => props.theme.colors.textSecondary};
  flex-shrink: 0;
`;

const CommentContent = styled.p`
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: ${props => props.theme.colors.text};
  white-space: pre-wrap;
  word-break: break-word;
`;

const CommentActions = styled.div`
  display: flex;
  gap: 8px;
  margin-top: 10px;
`;

const ChatButton = styled.button`
  width: 100%;
  padding: 9px;
  border-radius: 8px;
  border: none;
  background: ${props => props.theme.colors.domain.care};
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:hover:not(:disabled) { opacity: 0.9; }
`;

const SecondaryButton = styled.button`
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
`;

const PrimaryButton = styled.button`
  padding: 8px 10px;
  border-radius: 8px;
  border: none;
  background: ${props => props.theme.colors.domain.care};
  color: white;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const OwnerBadge = styled.div`
  padding: 6px 14px;
  font-size: 12px;
  color: ${props => props.theme.colors.domain.care};
  font-weight: 600;
`;

const EmptyState = styled.div`
  padding: 12px 0 4px;
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
`;

const ActionErrorMsg = styled.div`
  margin: 0 14px 4px;
  padding: 6px 10px;
  background: ${props => props.theme.colors.errorSoft};
  color: ${props => props.theme.colors.error};
  border-radius: 6px;
  font-size: 12px;
`;

const ReviewModalOverlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.48);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  padding: 20px;
`;

const ReviewModal = styled.div`
  width: min(520px, 100%);
  max-height: min(78vh, 720px);
  overflow-y: auto;
  border-radius: 18px;
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  box-shadow: 0 22px 60px rgba(0, 0, 0, 0.18);
  padding: 20px;
`;

const ReviewModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
`;

const ReviewModalTitle = styled.h3`
  margin: 0;
  font-size: 18px;
  color: ${props => props.theme.colors.text};
`;

const ReviewModalStats = styled.div`
  margin-top: 6px;
  font-size: 13px;
  color: ${props => props.theme.colors.textSecondary};
`;

const ReviewMeta = styled.div`
  margin-top: 10px;
  font-size: 13px;
  color: ${props => props.theme.colors.textSecondary};
`;

const ReviewList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
`;

const ReviewCard = styled.div`
  padding: 14px;
  border-radius: 12px;
  background: ${props => props.theme.colors.surfaceSoft};
  border: 1px solid ${props => props.theme.colors.border};
`;

const ReviewCardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
`;

const ReviewAuthor = styled.div`
  font-size: 13px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
`;

const ReviewRating = styled.div`
  font-size: 12px;
`;

const ReviewComment = styled.p`
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: ${props => props.theme.colors.text};
  white-space: pre-wrap;
`;

const ReviewDate = styled.div`
  margin-top: 10px;
  font-size: 11px;
  color: ${props => props.theme.colors.textSecondary};
`;

const ReviewEmptyState = styled.div`
  margin-top: 16px;
  padding: 16px 0 4px;
  font-size: 13px;
  color: ${props => props.theme.colors.textSecondary};
`;
