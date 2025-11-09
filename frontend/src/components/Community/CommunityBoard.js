import React, { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { usePermission } from '../../hooks/usePermission';
import { useAuth } from '../../contexts/AuthContext';
import { boardApi } from '../../api/boardApi';
import CommunityPostModal from './CommunityPostModal';
import CommunityCommentDrawer from './CommunityCommentDrawer';
import CommunityDetailPage from './CommunityDetailPage';

const CommunityBoard = () => {
  const { requireLogin } = usePermission();
  const { user, redirectToLogin } = useAuth();
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [error, setError] = useState('');
  const [isPostModalOpen, setIsPostModalOpen] = useState(false);
  const [isSubmittingPost, setIsSubmittingPost] = useState(false);
  const [isCommentDrawerOpen, setIsCommentDrawerOpen] = useState(false);
  const [selectedBoard, setSelectedBoard] = useState(null);
  const [selectedBoardId, setSelectedBoardId] = useState(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  const categories = [
    { key: 'ALL', label: '전체', icon: '📋', color: '#6366F1' },
    { key: 'TIP', label: '꿀팁', icon: '💡', color: '#F59E0B' },
    { key: 'QUESTION', label: '질문', icon: '❓', color: '#3B82F6' },
    { key: 'INFO', label: '정보', icon: '📢', color: '#10B981' },
    { key: 'STORY', label: '일상', icon: '📖', color: '#EC4899' }
  ];

  const fetchBoards = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const params = activeCategory === 'ALL' ? {} : { category: activeCategory };
      const response = await boardApi.getAllBoards(params);
      setPosts(response.data || []);
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`커뮤니티 게시글을 불러오지 못했습니다: ${message}`);
    } finally {
      setLoading(false);
    }
  }, [activeCategory]);

  useEffect(() => {
    fetchBoards();
  }, [fetchBoards]);

  const filteredPosts = useMemo(() => {
    if (activeCategory === 'ALL') {
      return posts;
    }
    return posts.filter((post) => post.category === activeCategory);
  }, [posts, activeCategory]);

  const getCategoryInfo = (category) => {
    const cat = categories.find((c) => c.key === category);
    return cat || { label: category, icon: '📋', color: '#6366F1' };
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    if (Number.isNaN(date.getTime())) return '';
    const now = new Date();
    const diff = now - date;
    const hours = Math.floor(diff / (1000 * 60 * 60));

    if (hours < 1) return '방금 전';
    if (hours < 24) return `${hours}시간 전`;
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
  };

  const handleWriteClick = () => {
    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }
    setIsPostModalOpen(true);
  };

  const handlePostSubmit = async (form) => {
    if (!user) {
      redirectToLogin();
      return;
    }
    try {
      setIsSubmittingPost(true);
      const payload = {
        title: form.title,
        content: form.content,
        category: form.category,
        boardFilePath: form.boardFilePath || null,
        userId: user.idx,
      };
      await boardApi.createBoard(payload);
      setIsPostModalOpen(false);
      await fetchBoards();
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      alert(`게시글 등록에 실패했습니다: ${message}`);
    } finally {
      setIsSubmittingPost(false);
    }
  };

  const handlePostSelect = (post) => {
    if (!post?.idx) return;
    setSelectedBoardId(post.idx);
    setIsDetailOpen(true);
  };

  const handleDetailClose = () => {
    setIsDetailOpen(false);
    setSelectedBoardId(null);
  };

  const handleCommentClick = (post, event) => {
    event?.stopPropagation?.();
    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }
    setSelectedBoard(post);
    setIsCommentDrawerOpen(true);
  };

  const handlePostReport = (postIdx) => {
    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }
    if (window.confirm('이 게시글을 신고하시겠습니까?')) {
      alert('신고 기능은 준비 중입니다.');
    }
  };

  const handleLikeClick = (postIdx, e) => {
    e.stopPropagation();
    reactToBoard(postIdx, 'LIKE');
  };

  const handleCommentDrawerClose = () => {
    setIsCommentDrawerOpen(false);
    setSelectedBoard(null);
  };

  const handleCommentAdded = () => {
    fetchBoards();
  };

  const reactToBoard = async (boardId, reactionType) => {
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
      const response = await boardApi.reactToBoard(boardId, {
        userId: user.idx,
        reactionType,
      });
      const summary = response.data;
      setPosts((prev) =>
        prev.map((post) =>
          post.idx === boardId
            ? {
                ...post,
                likes: summary.likeCount,
                dislikes: summary.dislikeCount,
                userReaction: summary.userReaction,
              }
            : post
        )
      );
      if (selectedBoard?.idx === boardId) {
        setSelectedBoard((prev) =>
          prev
            ? {
                ...prev,
                likes: summary.likeCount,
                dislikes: summary.dislikeCount,
                userReaction: summary.userReaction,
              }
            : prev
        );
      }
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      alert(`반응 처리에 실패했습니다: ${message}`);
    }
  };

  const handleBoardReactionUpdate = useCallback((boardId, summary) => {
    setPosts((prev) =>
      prev.map((post) =>
        post.idx === boardId
          ? {
              ...post,
              likes: summary.likeCount,
              dislikes: summary.dislikeCount,
              userReaction: summary.userReaction,
            }
          : post
      )
    );
    setSelectedBoard((prev) =>
      prev && prev.idx === boardId
        ? {
            ...prev,
            likes: summary.likeCount,
            dislikes: summary.dislikeCount,
            userReaction: summary.userReaction,
          }
        : prev
    );
  }, []);

  if (loading && posts.length === 0) {
    return (
      <LoadingContainer>
        <LoadingSpinner />
        <LoadingMessage>커뮤니티 게시글을 불러오는 중...</LoadingMessage>
      </LoadingContainer>
    );
  }

  return (
    <Container>
      <Header>
        <TitleSection>
          <TitleIcon>💬</TitleIcon>
          <Title>커뮤니티</Title>
          <Subtitle>반려동물과 함께하는 따뜻한 이야기</Subtitle>
        </TitleSection>
        <WriteButton onClick={handleWriteClick}>
          <WriteIcon>✏️</WriteIcon>
          글쓰기
        </WriteButton>
      </Header>

      <CategoryTabs>
        {categories.map((category) => (
          <CategoryTab
            key={category.key}
            active={activeCategory === category.key}
            onClick={() => setActiveCategory(category.key)}
            categoryColor={category.color}
          >
            <CategoryIcon>{category.icon}</CategoryIcon>
            {category.label}
          </CategoryTab>
        ))}
      </CategoryTabs>

      {error && <ErrorBanner>{error}</ErrorBanner>}

      <PostList>
        {filteredPosts.length === 0 ? (
          <EmptyState>
            <EmptyIcon>📭</EmptyIcon>
            <EmptyText>아직 게시글이 없어요</EmptyText>
            <EmptySubtext>첫 번째 게시글을 작성해보세요!</EmptySubtext>
          </EmptyState>
        ) : (
          filteredPosts.map((post) => {
            const categoryInfo = getCategoryInfo(post.category);
            return (
              <PostCard key={post.idx} onClick={() => handlePostSelect(post)}>
                <PostHeader>
                  <PostTitleSection>
                    <PostTitle>{post.title}</PostTitle>
                    <CategoryBadge categoryColor={categoryInfo.color}>
                      <CategoryBadgeIcon>{categoryInfo.icon}</CategoryBadgeIcon>
                      {categoryInfo.label}
                    </CategoryBadge>
                  </PostTitleSection>
                </PostHeader>

                {post.boardFilePath && (
                  <PostImage>
                    <img src={post.boardFilePath} alt={post.title} />
                  </PostImage>
                )}

                <PostContent>{post.content}</PostContent>

                <PostFooter>
                  <AuthorInfo>
                    <AuthorAvatar>
                      {post.username ? post.username.charAt(0).toUpperCase() : 'U'}
                    </AuthorAvatar>
                    <AuthorDetails>
                      <AuthorName>{post.username || '알 수 없음'}</AuthorName>
                      <AuthorLocation>
                        <LocationIcon>📍</LocationIcon>
                        {post.userLocation || '위치 정보 없음'}
                      </AuthorLocation>
                    </AuthorDetails>
                  </AuthorInfo>
                  <PostActions>
                    <PostStats>
                      <StatItem onClick={(e) => handleCommentClick(post, e)}>
                        <StatIcon>💬</StatIcon>
                        <StatValue>{post.commentCount ?? 0}</StatValue>
                      </StatItem>
                      <StatItem onClick={(e) => handleLikeClick(post.idx, e)}>
                        <StatIcon>❤️</StatIcon>
                        <StatValue>{post.likes ?? 0}</StatValue>
                      </StatItem>
                      <TimeAgo>{formatDate(post.createdAt)}</TimeAgo>
                    </PostStats>
                    <ReportButton
                      onClick={(e) => {
                        e.stopPropagation();
                        handlePostReport(post.idx);
                      }}
                    >
                      <ReportIcon>🚨</ReportIcon>
                    </ReportButton>
                  </PostActions>
                </PostFooter>
              </PostCard>
            );
          })
        )}
      </PostList>

      <CommunityPostModal
        isOpen={isPostModalOpen}
        onClose={() => setIsPostModalOpen(false)}
        onSubmit={handlePostSubmit}
        loading={isSubmittingPost}
        currentUser={user}
      />

      <CommunityCommentDrawer
        isOpen={isCommentDrawerOpen}
        board={selectedBoard}
        onClose={handleCommentDrawerClose}
        currentUser={user}
        onCommentAdded={handleCommentAdded}
      />

      <CommunityDetailPage
        isOpen={isDetailOpen}
        boardId={selectedBoardId}
        onClose={handleDetailClose}
        onCommentAdded={handleCommentAdded}
        onBoardReaction={handleBoardReactionUpdate}
      />
    </Container>
  );
};

export default CommunityBoard;

const Container = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: ${props => props.theme.spacing.xl} ${props => props.theme.spacing.lg};
  min-height: 100vh;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: ${props => props.theme.spacing.xxl};
  padding-bottom: ${props => props.theme.spacing.xl};
  border-bottom: 2px solid ${props => props.theme.colors.borderLight};
  
  @media (max-width: 768px) {
    flex-direction: column;
    gap: ${props => props.theme.spacing.md};
    align-items: stretch;
  }
`;

const TitleSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.xs};
`;

const TitleIcon = styled.span`
  font-size: 32px;
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const Title = styled.h1`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h1.fontSize};
  font-weight: ${props => props.theme.typography.h1.fontWeight};
  margin: 0;
  background: ${props => props.theme.colors.gradient};
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  
  @media (max-width: 768px) {
    font-size: ${props => props.theme.typography.h2.fontSize};
  }
`;

const Subtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
  margin: 0;
  margin-top: ${props => props.theme.spacing.xs};
`;

const WriteButton = styled.button`
  background: ${props => props.theme.colors.gradient};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.xl};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  box-shadow: 0 4px 12px rgba(255, 126, 54, 0.25);
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(255, 126, 54, 0.35);
  }
  
  &:active {
    transform: translateY(0);
  }
`;

const WriteIcon = styled.span`
  font-size: 18px;
`;

const CategoryTabs = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  margin-bottom: ${props => props.theme.spacing.xl};
  flex-wrap: wrap;
  padding-bottom: ${props => props.theme.spacing.md};
`;

const CategoryTab = styled.button`
  background: ${props => props.active 
    ? `linear-gradient(135deg, ${props.categoryColor} 0%, ${props.categoryColor}dd 100%)`
    : props.theme.colors.surface};
  color: ${props => props.active ? 'white' : props.theme.colors.text};
  border: 2px solid ${props => props.active ? props.categoryColor : props.theme.colors.border};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.lg};
  border-radius: ${props => props.theme.borderRadius.full};
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  box-shadow: ${props => props.active 
    ? `0 4px 12px ${props.categoryColor}40`
    : 'none'};
  
  &:hover {
    background: ${props => props.active 
      ? `linear-gradient(135deg, ${props.categoryColor}dd 0%, ${props.categoryColor}cc 100%)`
      : props.theme.colors.surfaceHover};
    transform: translateY(-2px);
    box-shadow: ${props => props.active 
      ? `0 6px 16px ${props.categoryColor}50`
      : `0 4px 8px ${props.theme.colors.shadow}`};
  }
`;

const CategoryIcon = styled.span`
  font-size: 16px;
`;

const PostList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.lg};
`;

const ErrorBanner = styled.div`
  background: rgba(220, 38, 38, 0.1);
  color: ${props => props.theme.colors.error || '#dc2626'};
  border: 1px solid rgba(220, 38, 38, 0.2);
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
  margin-bottom: ${props => props.theme.spacing.lg};
  font-size: 0.95rem;
`;

const PostCard = styled.div`
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.xl};
  padding: ${props => props.theme.spacing.xl};
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: ${props => props.theme.colors.gradient};
    transform: scaleX(0);
    transition: transform 0.3s ease;
  }
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 32px ${props => props.theme.colors.shadow};
    border-color: ${props => props.theme.colors.primary};
    
    &::before {
      transform: scaleX(1);
    }
  }
`;

const PostHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: ${props => props.theme.spacing.md};
`;

const PostTitleSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.sm};
  flex: 1;
`;

const PostImage = styled.div`
  margin: ${props => props.theme.spacing.md} 0;
  border-radius: ${props => props.theme.borderRadius.lg};
  overflow: hidden;
  border: 1px solid ${props => props.theme.colors.border};

  img {
    width: 100%;
    height: auto;
    display: block;
    object-fit: cover;
  }
`;

const PostTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  margin: 0;
  line-height: 1.4;
`;

const CategoryBadge = styled.span`
  background: ${props => `linear-gradient(135deg, ${props.categoryColor} 0%, ${props.categoryColor}dd 100%)`};
  color: white;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.caption.fontSize};
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  width: fit-content;
  box-shadow: 0 2px 8px ${props => `${props.categoryColor}40`};
`;

const CategoryBadgeIcon = styled.span`
  font-size: 12px;
`;

const PostContent = styled.p`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
  line-height: 1.7;
  margin: ${props => props.theme.spacing.md} 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
`;

const PostFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: ${props => props.theme.spacing.lg};
  padding-top: ${props => props.theme.spacing.md};
  border-top: 1px solid ${props => props.theme.colors.borderLight};
  
  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
    gap: ${props => props.theme.spacing.md};
  }
`;

const AuthorInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
`;

const AuthorAvatar = styled.div`
  width: 48px;
  height: 48px;
  border-radius: ${props => props.theme.borderRadius.full};
  background: ${props => props.theme.colors.gradient};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 700;
  font-size: 18px;
  box-shadow: 0 4px 12px rgba(255, 126, 54, 0.25);
`;

const AuthorDetails = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.xs};
`;

const AuthorName = styled.span`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
`;

const AuthorLocation = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.caption.fontSize};
`;

const LocationIcon = styled.span`
  font-size: 12px;
`;

const PostActions = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.lg};
  
  @media (max-width: 768px) {
    width: 100%;
    justify-content: space-between;
  }
`;

const PostStats = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.lg};
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body2.fontSize};
`;

const StatItem = styled.button`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  background: none;
  border: none;
  color: ${props => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  transition: all 0.2s ease;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
    transform: scale(1.05);
  }
`;

const StatIcon = styled.span`
  font-size: 16px;
`;

const StatValue = styled.span`
  font-weight: 600;
  font-size: ${props => props.theme.typography.body2.fontSize};
`;

const TimeAgo = styled.span`
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.caption.fontSize};
  white-space: nowrap;
`;

const ReportButton = styled.button`
  background: none;
  border: none;
  color: ${props => props.theme.colors.textLight};
  cursor: pointer;
  padding: ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  
  &:hover {
    color: ${props => props.theme.colors.error || '#dc3545'};
    background: ${props => props.theme.colors.surfaceHover || 'rgba(220, 53, 69, 0.1)'};
    transform: scale(1.1);
  }
`;

const ReportIcon = styled.span`
  font-size: 18px;
`;

const LoadingContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${props => props.theme.spacing.xxl};
  min-height: 400px;
  gap: ${props => props.theme.spacing.lg};
`;

const LoadingSpinner = styled.div`
  width: 48px;
  height: 48px;
  border: 4px solid ${props => props.theme.colors.border};
  border-top-color: ${props => props.theme.colors.primary};
  border-radius: 50%;
  animation: spin 1s linear infinite;
  
  @keyframes spin {
    to { transform: rotate(360deg); }
  }
`;

const LoadingMessage = styled.div`
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${props => props.theme.spacing.xxl};
  text-align: center;
  min-height: 400px;
  gap: ${props => props.theme.spacing.md};
`;

const EmptyIcon = styled.div`
  font-size: 64px;
  margin-bottom: ${props => props.theme.spacing.md};
`;

const EmptyText = styled.div`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: 600;
`;

const EmptySubtext = styled.div`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;