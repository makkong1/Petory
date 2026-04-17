import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { locationServiceReviewApi } from '../../../api/locationServiceReviewApi';
import { useAuth } from '../../../contexts/AuthContext';
import {
  InfoPanel as BaseInfoPanel,
  PanelHeader, CloseButton, PanelTitle, Divider,
  InfoRow, InfoLabel, InfoValue, InfoGrid,
} from '../shared/BaseInfoPanel';

const LocationLayer = ({ selectedItem, onClose }) => {
  const { user } = useAuth();
  const [reviews, setReviews] = useState([]);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewText, setReviewText] = useState('');
  const [reviewRating, setReviewRating] = useState(5);
  const [editingReview, setEditingReview] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!selectedItem) return;
    setReviews([]);
    setEditingReview(null);
    setReviewText('');
    setReviewRating(5);
    const idx = selectedItem.raw?.idx;
    if (!idx) return;

    setReviewLoading(true);
    locationServiceReviewApi.getReviewsByService(idx)
      .then(res => setReviews(res.data?.reviews || res.data || []))
      .catch(() => setReviews([]))
      .finally(() => setReviewLoading(false));
  }, [selectedItem]);

  if (!selectedItem) return null;
  const r = selectedItem.raw;

  const avgRating = reviews.length
    ? (reviews.reduce((s, rv) => s + (rv.rating || 0), 0) / reviews.length).toFixed(1)
    : null;

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    if (!reviewText.trim()) return;
    setSubmitting(true);
    try {
      if (editingReview) {
        await locationServiceReviewApi.updateReview(editingReview.idx, {
          rating: reviewRating,
          comment: reviewText,
        });
      } else {
        await locationServiceReviewApi.createReview({
          serviceIdx: r.idx,
          rating: reviewRating,
          comment: reviewText,
        });
      }
      const res = await locationServiceReviewApi.getReviewsByService(r.idx);
      setReviews(res.data?.reviews || res.data || []);
      setReviewText('');
      setReviewRating(5);
      setEditingReview(null);
    } catch (err) {
      console.error('리뷰 저장 실패:', err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteReview = async (reviewIdx) => {
    if (!window.confirm('리뷰를 삭제할까요?')) return;
    try {
      await locationServiceReviewApi.deleteReview(reviewIdx);
      setReviews(prev => prev.filter(rv => rv.idx !== reviewIdx));
    } catch (err) {
      console.error('리뷰 삭제 실패:', err);
    }
  };

  const handleEditReview = (review) => {
    setEditingReview(review);
    setReviewText(review.comment || '');
    setReviewRating(review.rating || 5);
  };

  return (
    <InfoPanel $width="320px" $maxHeight="70vh">
      <PanelHeader>
        <TypeBadge>🏥 {r.category || '시설'}</TypeBadge>
        <CloseButton onClick={onClose} aria-label="닫기">✕</CloseButton>
      </PanelHeader>

      <PanelTitle>{selectedItem.title}</PanelTitle>
      {avgRating && (
        <RatingRow>
          <StarRating value={Number(avgRating)} />
          <RatingText>{avgRating} ({reviews.length}개 리뷰)</RatingText>
        </RatingRow>
      )}

      <Divider />

      <ScrollBody>
        <InfoGrid $padding="10px 14px">
          {r.address && <InfoRow><InfoLabel $minWidth="70px">주소</InfoLabel><InfoValue>{r.address}</InfoValue></InfoRow>}
          {r.phone && (
            <InfoRow>
              <InfoLabel $minWidth="70px">전화</InfoLabel>
              <InfoValue><a href={`tel:${r.phone}`}>{r.phone}</a></InfoValue>
            </InfoRow>
          )}
          {r.website && (
            <InfoRow>
              <InfoLabel $minWidth="70px">웹사이트</InfoLabel>
              <InfoValue>
                <a href={r.website} target="_blank" rel="noopener noreferrer">바로가기</a>
              </InfoValue>
            </InfoRow>
          )}
          {r.operatingHours && <InfoRow><InfoLabel $minWidth="70px">운영시간</InfoLabel><InfoValue>{r.operatingHours}</InfoValue></InfoRow>}
          {r.closedDay && <InfoRow><InfoLabel $minWidth="70px">휴무일</InfoLabel><InfoValue>{r.closedDay}</InfoValue></InfoRow>}
          {r.priceInfo && <InfoRow><InfoLabel $minWidth="70px">가격</InfoLabel><InfoValue>{r.priceInfo}</InfoValue></InfoRow>}
          {r.parkingAvailable != null && (
            <InfoRow><InfoLabel $minWidth="70px">주차</InfoLabel><InfoValue>{r.parkingAvailable ? '가능' : '불가능'}</InfoValue></InfoRow>
          )}
          {(r.indoor != null || r.outdoor != null) && (
            <InfoRow>
              <InfoLabel $minWidth="70px">장소</InfoLabel>
              <InfoValue>
                {[r.indoor && '실내', r.outdoor && '실외'].filter(Boolean).join(' / ')}
              </InfoValue>
            </InfoRow>
          )}
          {r.petFriendly != null && (
            <InfoRow>
              <InfoLabel $minWidth="70px">반려동물</InfoLabel>
              <InfoValue>{r.petFriendly ? '✅ 동반 가능' : '❌ 동반 불가'}</InfoValue>
            </InfoRow>
          )}
          {r.petSize && <InfoRow><InfoLabel $minWidth="70px">입장 크기</InfoLabel><InfoValue>{r.petSize}</InfoValue></InfoRow>}
          {r.petRestrictions && <InfoRow><InfoLabel $minWidth="70px">제한사항</InfoLabel><InfoValue>{r.petRestrictions}</InfoValue></InfoRow>}
          {r.petExtraFee && <InfoRow><InfoLabel $minWidth="70px">반려동물 추가요금</InfoLabel><InfoValue>{r.petExtraFee}</InfoValue></InfoRow>}
          {r.description && <InfoRow><InfoLabel $minWidth="70px">설명</InfoLabel><InfoValue>{r.description}</InfoValue></InfoRow>}
        </InfoGrid>

        <Divider />

        {/* 리뷰 섹션 */}
        <ReviewSection>
        <ReviewTitle>리뷰 ({reviews.length})</ReviewTitle>

        {reviewLoading ? (
          <ReviewEmpty>불러오는 중...</ReviewEmpty>
        ) : reviews.length === 0 ? (
          <ReviewEmpty>아직 리뷰가 없습니다.</ReviewEmpty>
        ) : (
          <ReviewList>
            {reviews.map(rv => (
              <ReviewItem key={rv.idx}>
                <ReviewMeta>
                  <ReviewAuthor>{rv.nickname || rv.username || '익명'}</ReviewAuthor>
                  <ReviewRating><StarRating value={rv.rating || 0} size={11} /></ReviewRating>
                  <ReviewDate>{rv.createdAt ? new Date(rv.createdAt).toLocaleDateString('ko-KR') : ''}</ReviewDate>
                </ReviewMeta>
                <ReviewComment>{rv.comment}</ReviewComment>
                {user && (user.idx === rv.userIdx || user.id === rv.userIdx) && (
                  <ReviewActions>
                    <ReviewAction onClick={() => handleEditReview(rv)}>수정</ReviewAction>
                    <ReviewAction onClick={() => handleDeleteReview(rv.idx)}>삭제</ReviewAction>
                  </ReviewActions>
                )}
              </ReviewItem>
            ))}
          </ReviewList>
        )}

        {user && (
          <ReviewForm onSubmit={handleSubmitReview}>
            <RatingSelect value={reviewRating} onChange={e => setReviewRating(Number(e.target.value))}>
              {[5, 4, 3, 2, 1].map(n => (
                <option key={n} value={n}>{'⭐'.repeat(n)} {n}점</option>
              ))}
            </RatingSelect>
            <ReviewInput
              value={reviewText}
              onChange={e => setReviewText(e.target.value)}
              placeholder="리뷰를 작성해주세요..."
              rows={2}
            />
            <ReviewFormRow>
              {editingReview && (
                <CancelButton type="button" onClick={() => { setEditingReview(null); setReviewText(''); setReviewRating(5); }}>
                  취소
                </CancelButton>
              )}
              <SubmitButton type="submit" disabled={submitting || !reviewText.trim()}>
                {submitting ? '저장 중...' : editingReview ? '수정' : '등록'}
              </SubmitButton>
            </ReviewFormRow>
          </ReviewForm>
        )}
        </ReviewSection>
      </ScrollBody>
    </InfoPanel>
  );
};

export default LocationLayer;

const InfoPanel = styled(BaseInfoPanel)``;

const TypeBadge = styled.span`
  font-size: 12px;
  color: ${props => props.theme.colors.domain.location};
  font-weight: 600;
`;

const StarRating = ({ value, max = 5, size = 13 }) => {
  const filled = Math.round(value);
  return (
    <StarGroup aria-label={`${value}점`}>
      {Array.from({ length: max }, (_, i) => (
        <StarChar key={i} $filled={i < filled} $size={size}>★</StarChar>
      ))}
    </StarGroup>
  );
};

const StarGroup = styled.span`
  display: inline-flex;
  gap: 1px;
  vertical-align: middle;
`;

const StarChar = styled.span`
  font-size: ${p => p.$size}px;
  color: ${p => p.$filled ? '#F59E0B' : p.theme.colors.border};
  line-height: 1;
`;

const RatingRow = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px 8px;
`;

const RatingText = styled.span`
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
`;

const ScrollBody = styled.div`
  flex: 1;
  overflow-y: auto;
  min-height: 0;
`;

const ReviewSection = styled.div`
  display: flex;
  flex-direction: column;
  padding: 10px 14px;
`;

const ReviewTitle = styled.div`
  font-size: 13px;
  font-weight: 600;
  color: ${props => props.theme.colors.text};
  margin-bottom: 8px;
  flex-shrink: 0;
`;

const ReviewList = styled.div`
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
`;

const ReviewItem = styled.div`
  background: ${props => props.theme.colors.background};
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12px;
`;

const ReviewMeta = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
`;

const ReviewAuthor = styled.span`
  font-weight: 600;
  color: ${props => props.theme.colors.text};
`;

const ReviewRating = styled.span`
  font-size: 11px;
`;

const ReviewDate = styled.span`
  color: ${props => props.theme.colors.textSecondary};
  margin-left: auto;
  font-size: 11px;
`;

const ReviewComment = styled.p`
  margin: 0;
  color: ${props => props.theme.colors.text};
  line-height: 1.4;
`;

const ReviewActions = styled.div`
  display: flex;
  gap: 8px;
  margin-top: 4px;
`;

const ReviewAction = styled.button`
  background: none;
  border: none;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 11px;
  cursor: pointer;
  padding: 0;
  &:hover { color: ${props => props.theme.colors.primary}; }
`;

const ReviewEmpty = styled.div`
  font-size: 12px;
  color: ${props => props.theme.colors.textSecondary};
  padding: 8px 0;
`;

const ReviewForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
  padding-top: 8px;
  border-top: 1px solid ${props => props.theme.colors.border};
`;

const RatingSelect = styled.select`
  padding: 5px 8px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 6px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: 12px;
`;

const ReviewInput = styled.textarea`
  padding: 7px 10px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 6px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: 12px;
  resize: none;
  outline: none;
  line-height: 1.4;

  &:focus { border-color: ${props => props.theme.colors.primary}; }
`;

const ReviewFormRow = styled.div`
  display: flex;
  gap: 6px;
  justify-content: flex-end;
`;

const SubmitButton = styled.button`
  padding: 5px 14px;
  border-radius: 6px;
  border: none;
  background: ${props => props.theme.colors.primary};
  color: white;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
`;

const CancelButton = styled.button`
  padding: 5px 12px;
  border-radius: 6px;
  border: 1px solid ${props => props.theme.colors.border};
  background: none;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 12px;
  cursor: pointer;
`;
