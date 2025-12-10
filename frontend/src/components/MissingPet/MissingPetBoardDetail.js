import React, { useState, useEffect, useMemo } from 'react';
import styled from 'styled-components';
import { missingPetApi } from '../../api/missingPetApi';
import { reportApi } from '../../api/reportApi';
import { startMissingPetChat } from '../../api/chatApi';
import AddressMapSelector from './AddressMapSelector';
import MapContainer from '../LocationService/MapContainer';

const statusLabel = {
  MISSING: '실종',
  FOUND: '발견',
  RESOLVED: '완료',
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
      const conversation = await startMissingPetChat(board.idx, currentUser.idx);
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
              <DetailTitle>{board.title}</DetailTitle>
            </DetailTitleRow>
          </DetailHeader>
          <DetailBody>
            <InfoCard>
              <InfoContent>
                <InfoGrid>
                  <InfoItem>
                    <InfoLabel>제보자</InfoLabel>
                    <InfoValue>{board.username || '알 수 없음'}</InfoValue>
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
              <SectionTitle>댓글 및 제보</SectionTitle>
              {board.comments && board.comments.length > 0 ? (
                <CommentList>
                  {board.comments.map((item) => (
                    <CommentItem key={item.idx}>
                      <CommentHeader>
                        <CommentAuthor>{item.username || '익명'}</CommentAuthor>
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
    </>
  );
};

export default MissingPetBoardDetail;

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

const CloseButton = styled.button`
  border: none;
  background: transparent;
  font-size: 1.2rem;
  color: ${(props) => props.theme.colors.textSecondary};
  cursor: pointer;

  &:hover {
    color: ${(props) => props.theme.colors.text};
  }
`;

const DeleteBoardButton = styled.button`
  border: 1px solid ${(props) => props.theme.colors.error || '#dc2626'};
  background: transparent;
  color: ${(props) => props.theme.colors.error || '#dc2626'};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(220, 38, 38, 0.1);
    transform: translateY(-1px);
  }
`;

const ReportBoardButton = styled.button`
  border: 1px solid ${(props) => props.theme.colors.warning || '#f97316'};
  background: transparent;
  color: ${(props) => props.theme.colors.warning || '#f97316'};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(249, 115, 22, 0.1);
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
  font-size: 0.8rem;
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
  font-size: 0.95rem;
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

const StatusBadge = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== 'status',
})`
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.8rem;
  color: #ffffff;
  background: ${(props) => {
    switch (props.status) {
      case 'FOUND':
        return '#10b981';
      case 'RESOLVED':
        return '#6366f1';
      case 'MISSING':
      default:
        return '#ef4444';
    }
  }};
`;

const StatusControl = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const StatusControlLabel = styled.span`
  font-size: 0.85rem;
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
  color: ${(props) => (props.active ? '#ffffff' : props.theme.colors.text)};
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
`;

const CommentDate = styled.span`
  font-size: 0.8rem;
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
  color: ${(props) => props.theme.colors.red || '#ef4444'};
  font-size: 0.82rem;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
`;

const CommentReportButton = styled.button`
  border: none;
  background: transparent;
  color: ${(props) => props.theme.colors.warning || '#f97316'};
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 600;
  padding: 0.1rem 0.4rem;
  border-radius: ${(props) => props.theme.borderRadius.sm};
  transition: all 0.2s ease;

  &:hover {
    background: rgba(249, 115, 22, 0.1);
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
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const CommentLocation = styled.div`
  margin-top: ${(props) => props.theme.spacing.xs};
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  background: ${(props) => props.theme.colors.surfaceHover};
  border-radius: ${(props) => props.theme.borderRadius.sm};
  font-size: 0.9rem;
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
  color: white;
  border: none;
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.md} ${(props) => props.theme.spacing.xl};
  font-weight: 600;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  width: 100%;
  max-width: 300px;

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.primaryHover || props.theme.colors.primary};
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
  font-size: 0.9rem;
  margin-top: ${(props) => props.theme.spacing.xs};

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.surfaceHover};
    border-color: ${(props) => props.theme.colors.primary};
    color: ${(props) => props.theme.colors.primary};
    transform: translateY(-1px);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
  border-radius: 8px;
  overflow: hidden;
  margin-top: 12px;
  border: 1px solid #e2e8f0;
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
  color: #ffffff;
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

