import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { userProfileApi } from '../../api/userApi';

const UserProfileModal = ({ isOpen, userId, onClose }) => {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen && userId) {
      fetchProfile();
    } else {
      setProfile(null);
      setError('');
    }
  }, [isOpen, userId]);

  const fetchProfile = async () => {
    try {
      setLoading(true);
      setError('');
      const response = await userProfileApi.getUserProfile(userId);
      setProfile(response.data);
    } catch (err) {
      const message = err.response?.data?.error || err.message || '프로필을 불러오는데 실패했습니다.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <Backdrop onClick={handleBackdropClick}>
      <ModalContainer onClick={(e) => e.stopPropagation()}>
        <ModalHeader>
          <ModalTitle>프로필</ModalTitle>
          <CloseButton onClick={onClose}>✕</CloseButton>
        </ModalHeader>

        <ModalContent>
          {loading ? (
            <LoadingMessage>프로필을 불러오는 중...</LoadingMessage>
          ) : error ? (
            <ErrorMessage>{error}</ErrorMessage>
          ) : profile ? (
            <>
              <UserInfoSection>
                <UserAvatar>
                  {profile.user?.username ? profile.user.username.charAt(0).toUpperCase() : 'U'}
                </UserAvatar>
                <UserName>{profile.user?.username || '알 수 없음'}</UserName>
                {profile.user?.location && (
                  <UserLocation>
                    <LocationIcon>📍</LocationIcon>
                    {profile.user.location}
                  </UserLocation>
                )}
                {profile.user?.role && (
                  <UserRole>
                    {profile.user.role === 'SERVICE_PROVIDER' ? '서비스 제공자' : '일반 사용자'}
                  </UserRole>
                )}
              </UserInfoSection>

              {profile.user?.role === 'SERVICE_PROVIDER' && (
                <ReviewSummarySection>
                  <ReviewSummaryTitle>펫케어 리뷰</ReviewSummaryTitle>
                  <ReviewStats>
                    <StatItem>
                      <StatLabel>평균 평점</StatLabel>
                      <StatValue>
                        {profile.averageRating ? profile.averageRating.toFixed(1) : '-'}
                        {profile.averageRating && <StarIcon>⭐</StarIcon>}
                      </StatValue>
                    </StatItem>
                    <StatItem>
                      <StatLabel>리뷰 개수</StatLabel>
                      <StatValue>{profile.reviewCount || 0}개</StatValue>
                    </StatItem>
                  </ReviewStats>
                </ReviewSummarySection>
              )}

              {profile.reviews && profile.reviews.length > 0 && (
                <ReviewsSection>
                  <ReviewsTitle>리뷰 목록</ReviewsTitle>
                  <ReviewList>
                    {profile.reviews.map((review) => (
                      <ReviewItem key={review.idx}>
                        <ReviewHeader>
                          <ReviewerName>{review.reviewerName || '알 수 없음'}</ReviewerName>
                          <ReviewRating>
                            {'⭐'.repeat(review.rating)}
                            <RatingNumber>{review.rating}</RatingNumber>
                          </ReviewRating>
                        </ReviewHeader>
                        {review.comment && (
                          <ReviewComment>{review.comment}</ReviewComment>
                        )}
                        <ReviewDate>
                          {review.createdAt
                            ? new Date(review.createdAt).toLocaleDateString('ko-KR')
                            : ''}
                        </ReviewDate>
                      </ReviewItem>
                    ))}
                  </ReviewList>
                </ReviewsSection>
              )}

              {profile.user?.role === 'SERVICE_PROVIDER' && (!profile.reviews || profile.reviews.length === 0) && (
                <EmptyReviewsMessage>아직 리뷰가 없습니다.</EmptyReviewsMessage>
              )}
            </>
          ) : null}
        </ModalContent>
      </ModalContainer>
    </Backdrop>
  );
};

export default UserProfileModal;

const Backdrop = styled.div`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: ${(props) => props.theme.spacing.lg};
`;

const ModalContainer = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  max-width: 600px;
  width: 100%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: ${(props) => props.theme.spacing.lg};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
`;

const ModalTitle = styled.h2`
  margin: 0;
  color: ${(props) => props.theme.colors.text};
  font-size: 1.5rem;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  font-size: 1.5rem;
  color: ${(props) => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: ${(props) => props.theme.borderRadius.md};

  &:hover {
    background: ${(props) => props.theme.colors.surfaceHover};
    color: ${(props) => props.theme.colors.text};
  }
`;

const ModalContent = styled.div`
  padding: ${(props) => props.theme.spacing.lg};
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ErrorMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.error || '#dc2626'};
`;

const UserInfoSection = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  padding-bottom: ${(props) => props.theme.spacing.lg};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const UserAvatar = styled.div`
  width: 80px;
  height: 80px;
  border-radius: ${(props) => props.theme.borderRadius.full};
  background: ${(props) => props.theme.colors.primary};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 2rem;
  font-weight: 600;
`;

const UserName = styled.div`
  font-size: 1.5rem;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const UserLocation = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.95rem;
`;

const LocationIcon = styled.span`
  font-size: 0.9rem;
`;

const UserRole = styled.div`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.primary};
  color: white;
  border-radius: ${(props) => props.theme.borderRadius.full};
  font-size: 0.9rem;
  font-weight: 500;
`;

const ReviewSummarySection = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.lg};
  padding-bottom: ${(props) => props.theme.spacing.lg};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
`;

const ReviewSummaryTitle = styled.h3`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  color: ${(props) => props.theme.colors.text};
  font-size: 1.2rem;
`;

const ReviewStats = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.xl};
`;

const StatItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const StatLabel = styled.div`
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.9rem;
`;

const StatValue = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  color: ${(props) => props.theme.colors.text};
  font-size: 1.5rem;
  font-weight: 600;
`;

const StarIcon = styled.span`
  font-size: 1.2rem;
`;

const ReviewsSection = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const ReviewsTitle = styled.h3`
  margin: 0 0 ${(props) => props.theme.spacing.md} 0;
  color: ${(props) => props.theme.colors.text};
  font-size: 1.2rem;
`;

const ReviewList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const ReviewItem = styled.div`
  padding: ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.md};
`;

const ReviewHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${(props) => props.theme.spacing.xs};
`;

const ReviewerName = styled.div`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const ReviewRating = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
`;

const RatingNumber = styled.span`
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ReviewComment = styled.div`
  color: ${(props) => props.theme.colors.text};
  margin-bottom: ${(props) => props.theme.spacing.xs};
  line-height: 1.5;
`;

const ReviewDate = styled.div`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const EmptyReviewsMessage = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  color: ${(props) => props.theme.colors.textSecondary};
  font-style: italic;
`;
