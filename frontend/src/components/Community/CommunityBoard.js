import React, { useCallback, useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { usePermission } from '../../hooks/usePermission';
import { useAuth } from '../../contexts/AuthContext';
import { boardApi } from '../../api/boardApi';
import { reportApi } from '../../api/reportApi';
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
  const [popularPeriod, setPopularPeriod] = useState('WEEKLY');
  const [popularPosts, setPopularPosts] = useState([]);
  const [popularLoading, setPopularLoading] = useState(false);
  const [popularError, setPopularError] = useState('');

  const categories = [
    { key: 'ALL', label: '전체', icon: '📋', color: '#6366F1' },
    { key: 'TIP', label: '꿀팁', icon: '💡', color: '#F59E0B' },
    { key: 'QUESTION', label: '질문', icon: '❓', color: '#3B82F6' },
    { key: 'INFO', label: '정보', icon: '📢', color: '#10B981' },
    { key: 'PRIDE', label: '자랑', icon: '🐾', color: '#F472B6' },
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

  const fetchPopularPosts = useCallback(async () => {
    if (activeCategory !== 'PRIDE') {
      return;
    }
    try {
      setPopularLoading(true);
      setPopularError('');
      const response = await boardApi.getPopularBoards(popularPeriod);
      setPopularPosts(response.data || []);
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setPopularError(`인기 자랑 게시글을 불러오지 못했습니다: ${message}`);
      setPopularPosts([]);
    } finally {
      setPopularLoading(false);
    }
  }, [activeCategory, popularPeriod]);

  useEffect(() => {
    fetchBoards();
  }, [fetchBoards]);

  useEffect(() => {
    fetchPopularPosts();
  }, [fetchPopularPosts]);

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

  const handlePopularCardClick = (snapshot) => {
    if (!snapshot?.boardId) return;
    setSelectedBoardId(snapshot.boardId);
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

  const handlePostReport = async (postIdx) => {
    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }
    if (!user || !postIdx) {
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
        targetIdx: postIdx,
        reporterId: user.idx,
        reason: reason.trim(),
      });
      alert('신고가 접수되었습니다.');
    } catch (err) {
      const message = err.response?.data?.error || err.message || '신고 처리에 실패했습니다.';
      alert(message);
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

  const handleDeletePost = async (postIdx, event) => {
    event?.stopPropagation?.();
    if (!user || postIdx == null) {
      return;
    }
    const confirmDelete = window.confirm('해당 게시글을 삭제하시겠습니까?');
    if (!confirmDelete) {
      return;
    }
    try {
      await boardApi.deleteBoard(postIdx);
      setPosts((prev) => prev.filter((post) => post.idx !== postIdx));
      if (selectedBoard?.idx === postIdx) {
        handleCommentDrawerClose();
      }
      if (selectedBoardId === postIdx) {
        handleDetailClose();
      }
      fetchBoards();
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      alert(`게시글을 삭제하지 못했습니다: ${message}`);
    }
  };

  const handleBoardDeleted = useCallback(
    (boardId) => {
      setPosts((prev) => prev.filter((post) => post.idx !== boardId));
      if (selectedBoard?.idx === boardId) {
        handleCommentDrawerClose();
      }
      if (selectedBoardId === boardId) {
        handleDetailClose();
      }
      fetchBoards();
    },
    [fetchBoards, selectedBoard, selectedBoardId]
  );

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

  const handleBoardViewUpdate = useCallback((boardId, views) => {
    setPosts((prev) =>
      prev.map((post) =>
        post.idx === boardId
          ? {
              ...post,
              views,
            }
          : post
      )
    );

    setSelectedBoard((prev) =>
      prev && prev.idx === boardId
        ? {
            ...prev,
            views,
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

      {activeCategory === 'PRIDE' && (
        <PopularSection>
          <PopularHeader>
            <PopularTitle>인기 반려동물 자랑 TOP 30</PopularTitle>
            <PopularTabs>
              <PopularTab
                type="button"
                active={popularPeriod === 'WEEKLY'}
                onClick={() => setPopularPeriod('WEEKLY')}
              >
                주간
              </PopularTab>
              <PopularTab
                type="button"
                active={popularPeriod === 'MONTHLY'}
                onClick={() => setPopularPeriod('MONTHLY')}
              >
                월간
              </PopularTab>
            </PopularTabs>
          </PopularHeader>

          {popularError && <ErrorBanner>{popularError}</ErrorBanner>}

          {popularLoading ? (
            <LoadingContainer>
              <LoadingSpinner />
              <LoadingMessage>인기 게시글을 불러오는 중...</LoadingMessage>
            </LoadingContainer>
          ) : (
            <PopularGrid>
              {popularPosts.length === 0 ? (
                <EmptyPopularMessage>아직 인기 자랑글이 없어요.</EmptyPopularMessage>
              ) : (
                popularPosts.slice(0, 6).map((snapshot) => (
                  <PopularCard type="button" key={`${snapshot.periodType}-${snapshot.boardId}-${snapshot.ranking}`} onClick={() => handlePopularCardClick(snapshot)}>
                    <PopularRank>{snapshot.ranking}</PopularRank>
                    <PopularContent>
                      <PopularTitleText>{snapshot.boardTitle || '제목 없음'}</PopularTitleText>
                      <PopularStats>
                        <PopularStat>❤️ {snapshot.likeCount ?? 0}</PopularStat>
                        <PopularStat>💬 {snapshot.commentCount ?? 0}</PopularStat>
                        <PopularStat>👁️ {snapshot.viewCount ?? 0}</PopularStat>
                      </PopularStats>
                    </PopularContent>
                    {snapshot.boardFilePath && (
                      <PopularThumb>
                        <img src={snapshot.boardFilePath} alt={snapshot.boardTitle} />
                      </PopularThumb>
                    )}
                  </PopularCard>
                ))
              )}
            </PopularGrid>
          )}
        </PopularSection>
      )}

      {error && <ErrorBanner>{error}</ErrorBanner>}

      <PostGrid>
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
                      <StatInfo>
                        <StatIcon>👁️</StatIcon>
                        <StatValue>{post.views ?? 0}</StatValue>
                      </StatInfo>
                      <TimeAgo>{formatDate(post.createdAt)}</TimeAgo>
                    </PostStats>
                    <PostActionsRight>
                      {user && user.idx === post.userId && (
                        <DeleteButton
                          type="button"
                          onClick={(event) => handleDeletePost(post.idx, event)}
                        >
                          삭제
                        </DeleteButton>
                      )}
                      <ReportButton
                        onClick={(e) => {
                          e.stopPropagation();
                          handlePostReport(post.idx);
                        }}
                      >
                        <ReportIcon>🚨</ReportIcon>
                      </ReportButton>
                    </PostActionsRight>
                  </PostActions>
                </PostFooter>
              </PostCard>
            );
          })
        )}
      </PostGrid>

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
        onBoardViewUpdate={handleBoardViewUpdate}
        currentUser={user}
        onBoardDeleted={handleBoardDeleted}
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

const PostGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: ${(props) => props.theme.spacing.lg};
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
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  padding: ${(props) => props.theme.spacing.xl};
  transition: transform 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
  min-height: 320px;
  background-image: linear-gradient(135deg, ${(props) =>
    props.theme.colors.surface} 0%, ${(props) => props.theme.colors.surfaceElevated} 100%);

  &:hover {
    transform: translateY(-8px);
    box-shadow: 0 16px 36px ${(props) => props.theme.colors.shadow};
    border-color: ${(props) => props.theme.colors.primary}55;
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
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  flex: 1;
  min-height: 3.6em;
`;

const PostFooter = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: ${(props) => props.theme.spacing.md};
  margin-top: auto;
  padding-top: ${(props) => props.theme.spacing.md};
  border-top: 1px solid ${(props) => props.theme.colors.borderLight};
`;

const AuthorInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  min-width: 0;
`;

const AuthorAvatar = styled.div`
  width: 46px;
  height: 46px;
  border-radius: ${(props) => props.theme.borderRadius.full};
  background: ${(props) => props.theme.colors.gradient};
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
  gap: ${(props) => props.theme.spacing.xs};
  min-width: 0;
`;

const AuthorName = styled.span`
  color: ${(props) => props.theme.colors.text};
  font-size: ${(props) => props.theme.typography.body1.fontSize};
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const AuthorLocation = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: ${(props) => props.theme.typography.caption.fontSize};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.9;
`;

const LocationIcon = styled.span`
  font-size: 12px;
`;

const PostActions = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  flex-wrap: wrap;
  min-width: 0;
`;

const PostStats = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  flex-wrap: wrap;
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: ${(props) => props.theme.typography.body2.fontSize};
`;

const StatItem = styled.button`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  background: none;
  border: 2px solid ${props => props.theme.colors.border};
  color: ${(props) => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  transition: all 0.2s ease;
  min-width: fit-content;

  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.primary};
    transform: scale(1.05);
  }
`;

const StatInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  background: ${props => props.theme.colors.surfaceElevated};
  border: 2px solid ${props => props.theme.colors.border};
  color: ${(props) => props.theme.colors.textSecondary};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  min-width: fit-content;
`;

const StatIcon = styled.span`
  font-size: 16px;
`;

const StatValue = styled.span`
  font-weight: 600;
  font-size: ${props => props.theme.typography.body2.fontSize};
`;

const TimeAgo = styled.span`
  color: ${(props) => props.theme.colors.textLight};
  font-size: ${(props) => props.theme.typography.caption.fontSize};
  white-space: nowrap;
  opacity: 0.85;
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

const DeleteButton = styled.button`
  background: none;
  border: 1px solid ${props => props.theme.colors.error || '#dc2626'};
  color: ${props => props.theme.colors.error || '#dc2626'};
  cursor: pointer;
  padding: ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  transition: all 0.2s ease;

  &:hover {
    background: rgba(220, 38, 38, 0.08);
    transform: translateY(-1px);
  }
`;

const PostActionsRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
  flex-wrap: wrap;
`;

const ReportIcon = styled.span`
  font-size: 18px;
`;

const PopularSection = styled.section`
  margin-bottom: ${(props) => props.theme.spacing.xxl};
  padding: ${(props) => props.theme.spacing.xl};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  background: ${(props) => props.theme.colors.surfaceElevated};
`;

const PopularHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  margin-bottom: ${(props) => props.theme.spacing.lg};

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
  }
`;

const PopularTitle = styled.h2`
  margin: 0;
  font-size: 1.4rem;
  color: ${(props) => props.theme.colors.text};
`;

const PopularTabs = styled.div`
  display: inline-flex;
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.full};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  overflow: hidden;
`;

const PopularTab = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border: none;
  background: ${(props) => (props.active ? props.theme.colors.primary : 'transparent')};
  color: ${(props) => (props.active ? '#fff' : props.theme.colors.textSecondary)};
  cursor: pointer;
  font-weight: 600;
  transition: background 0.2s ease;

  &:hover {
    background: ${(props) => (props.active ? props.theme.colors.primaryDark : props.theme.colors.surfaceHover)};
  }
`;

const PopularGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: ${(props) => props.theme.spacing.md};
`;

const PopularCard = styled.button`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.md};
  padding: ${(props) => props.theme.spacing.md};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  background: ${(props) => props.theme.colors.surface};
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  text-align: left;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 10px 20px rgba(15, 23, 42, 0.1);
  }
`;

const PopularRank = styled.span`
  font-size: 1.4rem;
  font-weight: 700;
  color: ${(props) => props.theme.colors.primary};
  min-width: 24px;
`;

const PopularContent = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.xs};
`;

const PopularTitleText = styled.span`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
  line-height: 1.3;
`;

const PopularStats = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.85rem;
  flex-wrap: wrap;
`;

const PopularStat = styled.span`
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
`;

const PopularThumb = styled.div`
  width: 64px;
  height: 64px;
  border-radius: ${(props) => props.theme.borderRadius.md};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.border};
  flex-shrink: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

const EmptyPopularMessage = styled.div`
  padding: ${(props) => props.theme.spacing.lg};
  text-align: center;
  color: ${(props) => props.theme.colors.textSecondary};
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