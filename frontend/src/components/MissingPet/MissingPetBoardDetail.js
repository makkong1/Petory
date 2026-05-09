import React, { useState, useEffect, useCallback } from 'react';
import styled from 'styled-components';
import { missingPetApi } from '../../api/missingPetApi';
import { reportApi } from '../../api/reportApi';
import { startMissingPetChat } from '../../api/chatApi';
import PageNavigation from '../Common/PageNavigation';
import AddressMapSelector from './AddressMapSelector';
import MapContainer from '../LocationService/MapContainer';
import UserProfileModal from '../User/UserProfileModal';

const statusLabel = {
  MISSING: '실종',
  FOUND: '발견',
  RESOLVED: '완료',
};

const getElapsedInfo = (lostDate) => {
  if (!lostDate) return null;
  const lost = new Date(lostDate);
  if (Number.isNaN(lost.getTime())) return null;
  const now = new Date();
  const diffMs = now - lost;
  if (diffMs < 0) return null;

  const hours = Math.floor(diffMs / 3600000);
  const days = Math.floor(diffMs / 86400000);

  if (hours < 24) return { text: `${hours}시간 경과`, level: 'critical' };
  if (days <= 3) return { text: `${days}일 경과`, level: 'critical' };
  if (days <= 7) return { text: `${days}일 경과`, level: 'urgent' };
  if (days <= 30) return { text: `${days}일 경과`, level: 'warning' };
  return { text: `${days}일 경과`, level: 'cold' };
};

const MissingPetBoardDetail = ({
  board,
  onClose,
  onRefresh,
  currentUser,
  onDeleteComment,
  onDeleteBoard,
}) => {
  const [comment, setComment] = useState('');
  const [commentAddress, setCommentAddress] = useState('');
  const [commentLat, setCommentLat] = useState(null);
  const [commentLng, setCommentLng] = useState(null);
  const [showAddressMap, setShowAddressMap] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [statusUpdating, setStatusUpdating] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);

  // 댓글 페이징 상태
  const [comments, setComments] = useState([]);
  const [commentPage, setCommentPage] = useState(0);
  const [commentPageSize] = useState(20);
  const [commentTotalCount, setCommentTotalCount] = useState(0);
  const [loadingComments, setLoadingComments] = useState(false);

  const handleViewProfile = (userId) => {
    setSelectedUserId(userId);
    setIsProfileModalOpen(true);
  };

  // 댓글 페이징으로 가져오기
  const fetchComments = useCallback(async (pageNum = 0) => {
    if (!board?.idx) return;
    
    try {
      setLoadingComments(true);
      const response = await missingPetApi.getComments(board.idx, pageNum, commentPageSize);
      const pageData = response.data || {};
      const commentsData = pageData.comments || [];
      setComments(commentsData);

      setCommentTotalCount(pageData.totalCount || 0);
      setCommentPage(pageNum);
    } catch (err) {
      console.error('댓글 조회 실패:', err);
    } finally {
      setLoadingComments(false);
    }
  }, [board?.idx, commentPageSize]);

  const handleCommentPageChange = useCallback((newPage) => {
    const totalPages = Math.max(1, Math.ceil(commentTotalCount / commentPageSize));
    if (newPage >= 0 && newPage < totalPages) {
      fetchComments(newPage);
    }
  }, [commentTotalCount, commentPageSize, fetchComments]);

  // 게시글 변경 시 댓글 초기화 및 로드
  useEffect(() => {
    if (board?.idx) {
      // 초기 댓글은 board.comments에서 가져오거나 별도로 로드
      if (board.comments && Array.isArray(board.comments)) {
        setComments(board.comments);
        setCommentTotalCount(board.commentCount || board.comments.length);
      } else {
        // board.comments가 없으면 별도 API로 로드
        fetchComments(0);
      }
    }
  }, [board?.idx, board?.comments, board?.commentCount, fetchComments]);

  if (!board) {
    return null;
  }

  const handleAddComment = async (e) => {
    e.preventDefault();
    if (!currentUser) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }

    // 댓글 내용이나 위치 중 하나는 있어야 함
    if (!comment.trim() && !commentAddress) {
      alert('댓글 내용 또는 목격 위치를 입력해주세요.');
      return;
    }

    try {
      setSubmitting(true);
      await missingPetApi.addComment(board.idx, {
        boardId: board.idx,
        userId: currentUser.idx,
        content: comment.trim(),
        address: commentAddress || null,
        latitude: commentLat || null,
        longitude: commentLng || null,
      });
      setComment('');
      setCommentAddress('');
      setCommentLat(null);
      setCommentLng(null);
      setShowAddressMap(false);
      // 댓글 목록 새로고침
      await fetchComments(0);
      onRefresh();
      // 알림 개수 즉시 업데이트 (다른 사용자에게 알림이 갔을 수 있음)
      window.dispatchEvent(new Event('notificationUpdate'));
    } catch (err) {
      alert(err.response?.data?.error || err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusUpdate = async (nextStatus) => {
    if (board.status === nextStatus) return;
    try {
      setStatusUpdating(true);
      await missingPetApi.updateStatus(board.idx, nextStatus);
      onRefresh();
    } catch (err) {
      alert(err.response?.data?.error || err.message);
    } finally {
      setStatusUpdating(false);
    }
  };

  const handleDeleteComment = async (commentId) => {
    if (!currentUser) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }

    if (!window.confirm('댓글을 삭제하시겠습니까?')) {
      return;
    }

    try {
      await missingPetApi.deleteComment(board.idx, commentId);
      onDeleteComment?.(commentId);
      // 댓글 목록 새로고침
      await fetchComments(0);
      onRefresh();
    } catch (err) {
      alert(err.response?.data?.error || err.message);
    }
  };

  const canManageStatus = currentUser && currentUser.idx === board.userId;
  const canDeleteBoard = currentUser && currentUser.idx === board.userId;
  const isReporter = currentUser && currentUser.idx === board.userId;

  const handleDeleteBoard = async () => {
    if (!canDeleteBoard) {
      return;
    }
    if (!window.confirm('해당 실종 제보를 삭제하시겠습니까?')) {
      return;
    }
    try {
      await missingPetApi.delete(board.idx);
      onDeleteBoard?.(board.idx);
      onClose?.();
    } catch (err) {
      alert(err.response?.data?.error || err.message);
    }
  };

  const handleReportBoard = async () => {
    if (!currentUser) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }
    if (!window.confirm('이 실종 제보를 신고하시겠습니까?')) {
      return;
    }
    const reason = window.prompt('신고 사유를 입력해주세요.');
    if (!reason || !reason.trim()) {
      return;
    }
    try {
      await reportApi.submit({
        targetType: 'MISSING_PET',
        targetIdx: board.idx,
        reporterId: currentUser.idx,
        reason: reason.trim(),
      });
      alert('신고가 접수되었습니다.');
    } catch (err) {
      alert(err.response?.data?.error || err.message || '신고 처리에 실패했습니다.');
    }
  };

  const handleReportComment = async (commentId) => {
    if (!currentUser) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }
    if (!window.confirm('해당 제보 댓글을 신고하시겠습니까?')) {
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
        reporterId: currentUser.idx,
        reason: reason.trim(),
      });
      alert('신고가 접수되었습니다.');
    } catch (err) {
      alert(err.response?.data?.error || err.message || '신고 처리에 실패했습니다.');
    }
  };

  const handleStartChat = async () => {
    if (!currentUser) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }

    if (isReporter) {
      alert('본인의 제보에는 채팅을 시작할 수 없습니다.');
      return;
    }

    try {
      const conversation = await startMissingPetChat(board.idx);
      // 채팅 위젯 열기
      if (window.openChatWidget) {
        window.openChatWidget(conversation.idx);
      } else {
        alert('채팅방이 생성되었습니다. 채팅 목록에서 확인해주세요.');
      }
    } catch (err) {
      alert(err.response?.data?.error || err.message || '채팅 시작에 실패했습니다.');
    }
  };

  return (
    <>
      <Backdrop onClick={onClose} />
      <PageContainer onClick={onClose}>
        <DetailCard onClick={(event) => event.stopPropagation()}>
          <DetailHeader>
            <HeaderTop>
              <BackButton type="button" onClick={onClose}>
                ← 목록으로
              </BackButton>
              <HeaderRight>
                {canDeleteBoard && (
                  <DeleteBoardButton type="button" onClick={handleDeleteBoard}>
                    삭제
                  </DeleteBoardButton>
                )}
                {currentUser && currentUser.idx !== board.userId && (
                  <ReportBoardButton type="button" onClick={handleReportBoard}>
                    신고
                  </ReportBoardButton>
                )}
              </HeaderRight>
            </HeaderTop>
            <DetailTitleRow>
              <StatusBadge status={board.status}>
                {statusLabel[board.status] || board.status}
              </StatusBadge>
              {board.status === 'MISSING' && (() => {
                const elapsed = getElapsedInfo(board.lostDate);
                return elapsed ? (
                  <UrgencyBadge level={elapsed.level}>{elapsed.text}</UrgencyBadge>
                ) : null;
              })()}
              <DetailTitle>{board.title}</DetailTitle>
            </DetailTitleRow>
          </DetailHeader>
          <DetailBody>
            <InfoCard>
              <InfoContent>
                <InfoGrid>
                  <InfoItem>
                    <InfoLabel>제보자</InfoLabel>
                    <InfoValue>{board.nickname || '알 수 없음'}</InfoValue>
                  </InfoItem>
                  <InfoItem>
                    <InfoLabel>실종일</InfoLabel>
                    <InfoValue>{board.lostDate || '미등록'}</InfoValue>
                  </InfoItem>
                  <InfoItem fullWidth>
                    <InfoLabel>실종 위치</InfoLabel>
                    <InfoValue>{board.lostLocation || '미등록'}</InfoValue>
                    {board.latitude && board.longitude && (
                      <MapWrapper>
                        <MapContainer
                          mapCenter={{
                            lat: typeof board.latitude === 'object' ? board.latitude.doubleValue?.() || board.latitude : Number(board.latitude),
                            lng: typeof board.longitude === 'object' ? board.longitude.doubleValue?.() || board.longitude : Number(board.longitude),
                          }}
                          mapLevel={7}
                          services={[{
                            idx: board.idx,
                            name: board.petName || board.title || '실종신고',
                            latitude: typeof board.latitude === 'object' ? board.latitude.doubleValue?.() || board.latitude : Number(board.latitude),
                            longitude: typeof board.longitude === 'object' ? board.longitude.doubleValue?.() || board.longitude : Number(board.longitude),
                            address: board.lostLocation || '',
                            type: 'missingPet',
                          }]}
                          onServiceClick={(service) => {
                            // 마커 클릭 시 상세 정보 표시 (이미 상세 페이지이므로 별도 처리 불필요)
                          }}
                        />
                      </MapWrapper>
                    )}
                  </InfoItem>
                  <InfoItem>
                    <InfoLabel>연락처</InfoLabel>
                    <InfoValue>
                      {board.phoneNumber ? (
                        <a href={`tel:${board.phoneNumber}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                          {board.phoneNumber}
                        </a>
                      ) : (
                        '댓글로 제보해주세요'
                      )}
                    </InfoValue>
                  </InfoItem>
                </InfoGrid>

                <Divider />

                <InfoGrid columns={2}>
                  {board.petName && (
                    <InfoItem>
                      <InfoLabel>반려동물 이름</InfoLabel>
                      <InfoValue>{board.petName}</InfoValue>
                    </InfoItem>
                  )}
                  {board.species && (
                    <InfoItem>
                      <InfoLabel>동물 종</InfoLabel>
                      <InfoValue>{board.species}</InfoValue>
                    </InfoItem>
                  )}
                  {board.breed && (
                    <InfoItem>
                      <InfoLabel>품종</InfoLabel>
                      <InfoValue>{board.breed}</InfoValue>
                    </InfoItem>
                  )}
                  {board.color && (
                    <InfoItem>
                      <InfoLabel>색상</InfoLabel>
                      <InfoValue>{board.color}</InfoValue>
                    </InfoItem>
                  )}
                  {board.gender && (
                    <InfoItem>
                      <InfoLabel>성별</InfoLabel>
                      <InfoValue>{board.gender === 'M' ? '수컷' : '암컷'}</InfoValue>
                    </InfoItem>
                  )}
                  {board.age && (
                    <InfoItem>
                      <InfoLabel>나이</InfoLabel>
                      <InfoValue>{board.age}</InfoValue>
                    </InfoItem>
                  )}
                </InfoGrid>

                {!isReporter && currentUser && (
                  <>
                    <Divider />
                    <WitnessButtonContainer>
                      <WitnessButton type="button" onClick={handleStartChat}>
                        실종동물 목격했어요
                      </WitnessButton>
                    </WitnessButtonContainer>
                  </>
                )}

                {canManageStatus && (
                  <>
                    <Divider />
                    <StatusControl>
                      <StatusControlLabel>상태 변경</StatusControlLabel>
                      <StatusButtonRow>
                        <StatusButton
                          type="button"
                          active={board.status === 'MISSING'}
                          onClick={() => handleStatusUpdate('MISSING')}
                          disabled={statusUpdating}
                        >
                          실종
                        </StatusButton>
                        <StatusButton
                          type="button"
                          active={board.status === 'FOUND'}
                          onClick={() => handleStatusUpdate('FOUND')}
                          disabled={statusUpdating}
                        >
                          발견
                        </StatusButton>
                        <StatusButton
                          type="button"
                          active={board.status === 'RESOLVED'}
                          onClick={() => handleStatusUpdate('RESOLVED')}
                          disabled={statusUpdating}
                        >
                          완료
                        </StatusButton>
                      </StatusButtonRow>
                    </StatusControl>
                  </>
                )}
              </InfoContent>
              {board.imageUrl && (
                <Preview>
                  <img src={board.imageUrl} alt={board.title} />
                </Preview>
              )}
            </InfoCard>

            <Section>
              <SectionTitle>상세 설명</SectionTitle>
              <ContentBox>{board.content || '상세 설명이 없습니다.'}</ContentBox>
            </Section>

            <Section>
              <SectionTitle>댓글 및 제보 {commentTotalCount > 0 && `(${commentTotalCount}개)`}</SectionTitle>
              {comments.length > 0 ? (
                <CommentList>
                  {comments.map((item) => (
                    <CommentItem key={item.idx}>
                      <CommentHeader>
                        <CommentAuthor
                          onClick={() => item.userId && handleViewProfile(item.userId)}
                          style={{ cursor: item.userId ? 'pointer' : 'default' }}
                        >
                          {item.nickname || '익명'}
                        </CommentAuthor>
                        <CommentDate>
                          {item.createdAt?.replace('T', ' ').substring(0, 16)}
                        </CommentDate>
                      </CommentHeader>
                      <CommentContent>{item.content}</CommentContent>
                      {item.address && (
                        <CommentLocation>
                          📍 목격 위치: {item.address}
                        </CommentLocation>
                      )}
                      {currentUser && (
                        <CommentActions>
                          {currentUser.idx === item.userId || currentUser.idx === board.userId ? (
                            <CommentDeleteButton onClick={() => handleDeleteComment(item.idx)}>
                              삭제
                            </CommentDeleteButton>
                          ) : (
                            <CommentReportButton type="button" onClick={() => handleReportComment(item.idx)}>
                              신고
                            </CommentReportButton>
                          )}
                        </CommentActions>
                      )}
                    </CommentItem>
                  ))}
                </CommentList>
              ) : (
                <EmptyComments>아직 제보가 없습니다. 가장 먼저 댓글을 남겨보세요!</EmptyComments>
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

              <CommentForm onSubmit={handleAddComment}>
                <CommentTextArea
                  placeholder={
                    currentUser
                      ? '목격 정보나 도움이 될 만한 내용을 남겨주세요.'
                      : '로그인 후 댓글을 작성할 수 있습니다.'
                  }
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  rows={3}
                  disabled={!currentUser || submitting}
                />
                {currentUser && (
                  <>
                    <AddressToggleButton
                      type="button"
                      onClick={() => setShowAddressMap(!showAddressMap)}
                      disabled={submitting}
                    >
                      {showAddressMap ? '📍 주소 입력 숨기기' : '📍 목격 위치 추가하기'}
                    </AddressToggleButton>
                    {showAddressMap && (
                      <AddressMapContainer>
                        <AddressMapSelector
                          onAddressSelect={(location) => {
                            setCommentAddress(location.address);
                            setCommentLat(location.latitude);
                            setCommentLng(location.longitude);
                          }}
                          initialAddress={commentAddress}
                          initialLat={commentLat}
                          initialLng={commentLng}
                        />
                      </AddressMapContainer>
                    )}
                  </>
                )}
                <CommentSubmit type="submit" disabled={!currentUser || submitting || (!comment.trim() && !commentAddress)}>
                  {submitting ? '등록 중...' : '댓글 등록'}
                </CommentSubmit>
              </CommentForm>
            </Section>
          </DetailBody>
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

export default MissingPetBoardDetail;

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
  background: none;
  border: none;
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  cursor: pointer;
  transition: color 0.2s ease;
  padding: ${(props) => props.theme.spacing.xs};

  &:hover {
    color: ${(props) => props.theme.colors.text};
  }
`;

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
  flex-wrap: wrap;

  @media (max-width: 768px) {
    width: 100%;
    justify-content: flex-start;
  }
`;

const DetailTitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  flex-wrap: wrap;
`;

const DetailTitle = styled.h2`
  margin: 0;
  font-size: ${(props) => props.theme.typography.h2.fontSize};

  @media (max-width: 768px) {
    font-size: ${(props) => props.theme.typography.h3.fontSize};
  }
  font-weight: ${(props) => props.theme.typography.h2.fontWeight};
  color: ${(props) => props.theme.colors.text};
  line-height: 1.4;
  flex: 1;
  min-width: 0;
`;

const DeleteBoardButton = styled.button`
  border: 1px solid ${(props) => props.theme.colors.error};
  background: transparent;
  color: ${(props) => props.theme.colors.error};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.errorSoft};
    transform: translateY(-1px);
  }
`;

const ReportBoardButton = styled.button`
  border: 1px solid ${(props) => props.theme.colors.warning};
  background: transparent;
  color: ${(props) => props.theme.colors.warning};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.warningSoft};
    transform: translateY(-1px);
  }
`;

const DetailBody = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: ${(props) => props.theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xl};

  @media (max-width: 768px) {
    padding: ${(props) => props.theme.spacing.md};
    gap: ${(props) => props.theme.spacing.md};
  }
`;

const InfoCard = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  border: 1px solid ${(props) => props.theme.colors.border};

  @media (min-width: 720px) {
    grid-template-columns: minmax(0, 1fr) 220px;
  }
`;

const InfoContent = styled.div`
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.lg};
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
`;

const InfoGrid = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'columns',
})`
  display: grid;
  gap: ${(props) => props.theme.spacing.md};
  grid-template-columns: repeat(${(props) => props.columns || 1}, minmax(0, 1fr));
  width: 100%;
  box-sizing: border-box;
`;

const InfoItem = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'fullWidth',
})`
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  width: 100%;
  ${(props) => props.fullWidth && `
    grid-column: 1 / -1;
  `}
`;

const InfoLabel = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const InfoValue = styled.span`
  font-size: 1rem;
  color: ${(props) => props.theme.colors.text};
  font-weight: 600;
  word-break: break-word;
  overflow-wrap: break-word;
  line-height: 1.5;
`;

const Divider = styled.hr`
  border: none;
  border-top: 1px dashed ${(props) => props.theme.colors.border};
  margin: ${(props) => props.theme.spacing.sm} 0;
`;

const Preview = styled.div`
  border-left: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surface};
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    max-height: 500px;
  }

  @media (max-width: 720px) {
    border-left: none;
    border-top: 1px solid ${(props) => props.theme.colors.border};
    min-height: 250px;
  }
`;

const Section = styled.section`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const SectionTitle = styled.h3`
  margin: 0;
  font-size: ${(props) => props.theme.typography.body1.fontSize};
`;

const ContentBox = styled.div`
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  padding: ${(props) => props.theme.spacing.lg};
  line-height: 1.6;
  color: ${(props) => props.theme.colors.textSecondary};
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  width: 100%;
  box-sizing: border-box;
`;

const UrgencyBadge = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== 'level',
})`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  border-radius: ${(props) => props.theme.borderRadius.pill};
  font-size: ${(props) => props.theme.typography.caption.fontSize};
  font-weight: 700;
  white-space: nowrap;
  animation: ${(props) => props.level === 'critical' ? 'urgencyPulse 2s infinite' : 'none'};
  color: ${(props) => props.theme.colors.textInverse};
  background: ${(props) => {
    switch (props.level) {
      case 'critical': return props.theme.colors.error;
      case 'urgent': return props.theme.colors.warning;
      case 'warning': return props.theme.colors.info;
      default: return props.theme.colors.textMuted;
    }
  }};

  @keyframes urgencyPulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.7; }
  }
`;

const StatusBadge = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== 'status',
})`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.pill};
  font-weight: 700;
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textInverse};
  background: ${(props) => {
    switch (props.status) {
      case 'FOUND':
        return props.theme.colors.status.found;
      case 'RESOLVED':
        return props.theme.colors.status.resolved;
      case 'MISSING':
      default:
        return props.theme.colors.status.missing;
    }
  }};
`;

const StatusControl = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const StatusControlLabel = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
`;

const StatusButtonRow = styled.div`
  display: inline-flex;
  gap: ${(props) => props.theme.spacing.sm};
`;

const StatusButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  font-weight: 600;
  cursor: pointer;
  background: ${(props) => (props.active ? props.theme.colors.primary : props.theme.colors.surface)};
  color: ${(props) => (props.active ? props.theme.colors.textInverse : props.theme.colors.text)};
  border: 1px solid ${(props) =>
    props.active ? props.theme.colors.primary : props.theme.colors.border};
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${(props) =>
    props.active ? props.theme.colors.primaryDark : props.theme.colors.surfaceHover};
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const CommentList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const CommentItem = styled.div`
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  padding: ${(props) => props.theme.spacing.md};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const CommentHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const CommentAuthor = styled.span`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
  transition: color 0.2s ease;

  &:hover {
    color: ${(props) => props.theme.colors.primary};
  }
`;

const CommentDate = styled.span`
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const CommentContent = styled.p`
  margin: 0;
  color: ${(props) => props.theme.colors.textSecondary};
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  line-height: 1.6;
`;

const CommentActions = styled.div`
  display: flex;
  justify-content: flex-end;
`;

const CommentDeleteButton = styled.button`
  border: none;
  background: transparent;
  color: ${(props) => props.theme.colors.error};
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
`;

const CommentReportButton = styled.button`
  border: none;
  background: transparent;
  color: ${(props) => props.theme.colors.warning};
  cursor: pointer;
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  font-weight: 600;
  padding: ${(props) => props.theme.spacing.xs};
  border-radius: ${(props) => props.theme.borderRadius.sm};
  transition: all 0.2s ease;

  &:hover {
    background: ${(props) => props.theme.colors.warningSoft};
  }
`;

const EmptyComments = styled.div`
  border: 1px dashed ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.lg};
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const CommentForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
  margin-top: ${(props) => props.theme.spacing.md};
`;

const CommentTextArea = styled.textarea`
  padding: ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  resize: vertical;
  min-height: 100px;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: ${(props) => props.theme.shadows.focus};
  }
`;

const CommentLocation = styled.div`
  margin-top: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  background: ${(props) => props.theme.colors.surfaceHover};
  border-radius: ${(props) => props.theme.borderRadius.sm};
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  color: ${(props) => props.theme.colors.text};
  font-weight: 500;
`;

const WitnessButtonContainer = styled.div`
  display: flex;
  justify-content: center;
  margin: ${(props) => props.theme.spacing.md} 0;
`;

const WitnessButton = styled.button`
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.md} ${(props) => props.theme.spacing.xl};
  font-weight: 600;
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  cursor: pointer;
  transition: all 0.2s ease;
  width: 100%;
  max-width: 300px;

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.primaryDark};
    transform: translateY(-2px);
    box-shadow: ${(props) => props.theme.shadows.md};
  }

  &:active:not(:disabled) {
    transform: translateY(0);
    box-shadow: ${(props) => props.theme.shadows.sm};
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const AddressToggleButton = styled.button`
  align-self: flex-start;
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.text};
  border: 1.5px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  margin-top: ${(props) => props.theme.spacing.xs};

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.surfaceHover};
    border-color: ${(props) => props.theme.colors.primary};
    color: ${(props) => props.theme.colors.primary};
    transform: translateY(-1px);
    box-shadow: ${(props) => props.theme.shadows.sm};
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const MapWrapper = styled.div`
  width: 100%;
  height: 300px;
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  margin-top: ${(props) => props.theme.spacing.md};
  border: 1px solid ${(props) => props.theme.colors.border};
`;

const AddressMapContainer = styled.div`
  margin-top: ${(props) => props.theme.spacing.md};
  padding: ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
`;

const CommentSubmit = styled.button`
  align-self: flex-end;
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.primaryDark};
  }
`;

const CommentPaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.md} 0;
  margin-top: ${props => props.theme.spacing.sm};
`;

