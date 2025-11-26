import React, { useEffect, useState, useCallback, useMemo } from 'react';
import styled from 'styled-components';
import { boardApi } from '../../api/boardApi';
import { reportApi } from '../../api/reportApi';
import { usePermission } from '../../hooks/usePermission';
import { useAuth } from '../../contexts/AuthContext';
import CommunityPostModal from './CommunityPostModal';
import CommunityDetailPage from './CommunityDetailPage';

const CommunityBoard = () => {
  const { requireLogin } = usePermission();
  const { user, redirectToLogin } = useAuth();

  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [isPostModalOpen, setIsPostModalOpen] = useState(false);
  const [isSubmittingPost, setIsSubmittingPost] = useState(false);
  const [selectedBoard, setSelectedBoard] = useState(null);
  const [isCommentDrawerOpen, setIsCommentDrawerOpen] = useState(false);
  const [selectedBoardId, setSelectedBoardId] = useState(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [popularPosts, setPopularPosts] = useState([]);
  const [popularLoading, setPopularLoading] = useState(false);
  const [popularError, setPopularError] = useState('');
  const [popularPeriod, setPopularPeriod] = useState('WEEKLY');
  const [displayCount, setDisplayCount] = useState(20); // 페이징: 초기 20개만 표시

  // 카테고리 변경 시 displayCount 리셋
  useEffect(() => {
    setDisplayCount(20);
  }, [activeCategory]);

  const categories = [
    { key: 'ALL', label: '전체', icon: '📋', color: '#6366F1' },
    { key: '일상', label: '일상', icon: '📖', color: '#EC4899' },
    { key: '자랑', label: '자랑', icon: '🐾', color: '#F472B6' },
    { key: '질문', label: '질문', icon: '❓', color: '#3B82F6' },
    { key: '정보공유', label: '정보공유', icon: '📢', color: '#10B981' },
    { key: '후기', label: '후기', icon: '📝', color: '#8B5CF6' },
    { key: '모임', label: '모임', icon: '🤝', color: '#F59E0B' },
    { key: '공지', label: '공지', icon: '📢', color: '#EF4444' },
  ];

  const getCategoryInfo = useCallback((category) => {
    const mapping = {
      ALL: { label: '전체', icon: '📋', color: '#6366F1' },
      일상: { label: '일상', icon: '📖', color: '#EC4899' },
      자랑: { label: '자랑', icon: '🐾', color: '#F472B6' },
      질문: { label: '질문', icon: '❓', color: '#3B82F6' },
      정보: { label: '정보공유', icon: '📢', color: '#10B981' },
      후기: { label: '후기', icon: '📝', color: '#8B5CF6' },
      모임: { label: '모임', icon: '🤝', color: '#F59E0B' },
      공지: { label: '공지', icon: '📢', color: '#EF4444' },
      PRIDE: { label: '자랑', icon: '🐾', color: '#F472B6' }, // 레거시 호환
    };
    return mapping[category] || { label: category || '전체', icon: '📋', color: '#6366F1' };
  }, []);

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    if (Number.isNaN(date.getTime())) return '-';
    const now = new Date();
    const diff = now - date;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '방금 전';
    if (minutes < 60) return `${minutes}분 전`;
    if (hours < 24) return `${hours}시간 전`;
    if (days < 7) return `${days}일 전`;
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
  };

  // 전역 이벤트 리스너: 알림에서 게시글로 이동할 때 사용
  useEffect(() => {
    const handleOpenBoardDetail = (event) => {
      const { boardId } = event.detail;
      if (boardId) {
        console.log('알림에서 게시글 열기:', boardId);
        setSelectedBoardId(boardId);
        setIsDetailOpen(true);
      }
    };

    window.addEventListener('openBoardDetail', handleOpenBoardDetail);
    return () => {
      window.removeEventListener('openBoardDetail', handleOpenBoardDetail);
    };
  }, []);

  // 전체 게시글을 한 번만 가져오기 (카테고리 변경 시 재호출하지 않음)
  const fetchBoards = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      // 항상 전체 게시글을 가져옴 (카테고리는 프론트엔드에서 필터링)
      const requestParams = {};
      const response = await boardApi.getAllBoards(requestParams);

      const boards = response.data || [];
      setPosts(boards);
    } catch (err) {
      console.error('❌ [CommunityBoard] 게시글 조회 실패:', err);
      console.error('❌ [CommunityBoard] 에러 상세:', err.response?.data);
      alert(`[CommunityBoard] 게시글 조회 실패:\n${JSON.stringify(err.response?.data || err.message, null, 2)}`);
      const message = err.response?.data?.error || err.message || '게시글을 불러오지 못했습니다.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []); // activeCategory 의존성 제거

  const fetchPopularBoards = useCallback(async () => {
    // 자랑 카테고리일 때만 인기 게시글 조회
    if (activeCategory !== '자랑' && activeCategory !== 'PRIDE') return;
    try {
      setPopularLoading(true);
      setPopularError('');
      const response = await boardApi.getPopularBoards(popularPeriod);
      const popularData = response.data || [];
      setPopularPosts(popularData);
    } catch (err) {
      console.error(`❌ ${popularPeriod} 인기 게시글 조회 실패:`, err);
      console.error('❌ 에러 상세:', err.response?.data);
      const message = err.response?.data?.error || err.response?.data?.message || err.message || '인기 게시글을 불러오지 못했습니다.';
      setPopularError(message);
    } finally {
      setPopularLoading(false);
    }
  }, [activeCategory, popularPeriod]);

  // 컴포넌트 마운트 시 한 번만 전체 게시글 로드
  useEffect(() => {
    fetchBoards();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 빈 배열로 마운트 시 한 번만 실행

  useEffect(() => {
    fetchPopularBoards();
  }, [fetchPopularBoards]);

  const filteredPosts = useMemo(() => {
    // 백엔드에서 이미 삭제된 게시글은 필터링되어 오므로, 프론트엔드에서는 최소한만 필터링
    // deleted가 명시적으로 true인 경우만 제외 (null이나 undefined는 통과)
    let result = posts.filter((post) => {
      // 명시적으로 삭제된 게시글만 제외
      if (post.deleted === true) {
        return false;
      }
      // status가 명시적으로 DELETED인 경우만 제외
      if (post.status === 'DELETED') {
        return false;
      }
      // 블라인드된 게시글도 제외 (일반 사용자는 볼 수 없음)
      if (post.status === 'BLINDED') {
        return false;
      }
      return true;
    });


    // 카테고리 필터링
    if (activeCategory === 'ALL') {
      return result;
    }
    // 자랑 카테고리는 일반 게시글과 인기 게시글 모두 표시
    const categoryFiltered = result.filter((post) => {
      const matches = post.category === activeCategory || (activeCategory === '자랑' && (post.category === '자랑' || post.category === 'PRIDE'));
      return matches;
    });
    return categoryFiltered;
  }, [posts, activeCategory]);

  // Magazine 스타일을 위한 게시글 분류
  const categorizedPosts = useMemo(() => {
    if (filteredPosts.length === 0) return { large: [], medium: [], small: [] };

    const large = [];
    const medium = [];
    const small = [];

    // 첫 번째 게시글 중 공지사항이 있으면 대형 카드로, 없으면 첫 번째 썸네일 게시글을 대형으로
    const noticePost = filteredPosts.find(post => post.category === '공지');
    const firstWithImage = filteredPosts.find(post => post.boardFilePath);

    if (noticePost) {
      large.push(noticePost);
    } else if (firstWithImage) {
      large.push(firstWithImage);
    }

    // 나머지 게시글 분류
    filteredPosts.forEach((post) => {
      // 이미 대형 카드로 선택된 게시글은 제외
      if (large.includes(post)) return;

      if (post.boardFilePath) {
        medium.push(post);
      } else {
        small.push(post);
      }
    });

    return { large, medium, small };
  }, [filteredPosts]);

  // 페이징을 위한 게시글 제한
  const displayedPosts = useMemo(() => {
    const allPosts = [
      ...categorizedPosts.large,
      ...categorizedPosts.medium,
      ...categorizedPosts.small
    ];

    // 처음 displayCount개만 반환
    const limited = allPosts.slice(0, displayCount);

    // 다시 large, medium, small로 분류
    const result = { large: [], medium: [], small: [] };

    limited.forEach((post) => {
      if (categorizedPosts.large.includes(post)) {
        result.large.push(post);
      } else if (categorizedPosts.medium.includes(post)) {
        result.medium.push(post);
      } else if (categorizedPosts.small.includes(post)) {
        result.small.push(post);
      }
    });

    return result;
  }, [categorizedPosts, displayCount]);

  // 더 보기 버튼 표시 여부
  const hasMore = useMemo(() => {
    const totalCount = categorizedPosts.large.length + categorizedPosts.medium.length + categorizedPosts.small.length;
    return displayCount < totalCount;
  }, [categorizedPosts, displayCount]);

  const handleLoadMore = useCallback(() => {
    setDisplayCount(prev => prev + 20); // 20개씩 추가
  }, []);


  const handleWriteClick = () => {
    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }
    setIsPostModalOpen(true);
  };

  const handlePostSubmit = async (form) => {
    const { requiresRedirect } = requireLogin();
    if (requiresRedirect) {
      redirectToLogin();
      return;
    }
    if (!user) {
      alert('로그인이 필요합니다.');
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
      const response = await boardApi.createBoard(payload);
      setIsPostModalOpen(false);
      // 게시글 작성 후 강제로 새로고침 (캐시 무시)
      await fetchBoards();
    } catch (err) {
      console.error('❌ 게시글 생성 실패:', err);
      const message = err.response?.data?.error || err.message;
      alert(`게시글 등록에 실패했습니다: ${message}`);
    } finally {
      setIsSubmittingPost(false);
    }
  };

  const handleDetailClose = () => {
    setIsDetailOpen(false);
    setSelectedBoardId(null);
    setIsCommentDrawerOpen(false);
    setSelectedBoard(null);
  };

  const handlePopularCardClick = (snapshot) => {
    if (!snapshot?.boardId) return;
    setSelectedBoardId(snapshot.boardId);
    setIsDetailOpen(true);
  };

  const handleCommentClick = (post, e) => {
    e.stopPropagation();
    if (!post?.idx) return;
    setSelectedBoardId(post.idx);
    setIsDetailOpen(true);
  };

  const handlePostSelect = (post, event) => {
    if (!post?.idx) return;
    event?.stopPropagation?.();
    setSelectedBoardId(post.idx);
    setIsDetailOpen(true);
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

  const handleCommentAdded = useCallback((boardId, isDelete = false) => {
    // 댓글 추가/삭제 시 해당 게시글의 댓글 카운트만 업데이트 (게시글 목록 전체 재조회 방지)
    if (boardId) {
      setPosts((prev) =>
        prev.map((post) =>
          post.idx === boardId
            ? {
              ...post,
              commentCount: Math.max(0, (post.commentCount ?? 0) + (isDelete ? -1 : 1)),
            }
            : post
        )
      );
    }
  }, []);

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

      {activeCategory === '자랑' && (
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
              <LoadingMessage>{popularPeriod === 'WEEKLY' ? '주간' : '월간'} 인기 게시글을 불러오는 중...</LoadingMessage>
            </LoadingContainer>
          ) : (
            <PopularScrollContainer>
              {popularPosts.length === 0 ? (
                <EmptyPopularMessage>
                  {popularPeriod === 'WEEKLY'
                    ? '아직 주간 인기 자랑글이 없어요.'
                    : '아직 월간 인기 자랑글이 없어요.'}
                </EmptyPopularMessage>
              ) : (
                <PopularScrollContent>
                  {popularPosts.map((snapshot) => (
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
                  ))}
                </PopularScrollContent>
              )}
            </PopularScrollContainer>
          )}
        </PopularSection>
      )}

      {error && <ErrorBanner>{error}</ErrorBanner>}

      {filteredPosts.length === 0 ? (
        <EmptyState>
          <EmptyIcon>📭</EmptyIcon>
          <EmptyText>아직 게시글이 없어요</EmptyText>
          <EmptySubtext>첫 번째 게시글을 작성해보세요!</EmptySubtext>
        </EmptyState>
      ) : (
        <>
          <PostGrid>
            {/* 대형 카드 (전체 너비) */}
            {displayedPosts.large.map((post) => {
              const categoryInfo = getCategoryInfo(post.category);
              return (
                <PostCard key={post.idx} size="large" onClick={() => handlePostSelect(post)}>
                  <PostHeader>
                    <PostTitleSection>
                      <PostTitleRow>
                        <PostTitle>{post.title}</PostTitle>
                        <PostNumber>#{post.idx}</PostNumber>
                      </PostTitleRow>
                      <CategoryBadge categoryColor={categoryInfo.color}>
                        <CategoryBadgeIcon>{categoryInfo.icon}</CategoryBadgeIcon>
                        {categoryInfo.label}
                      </CategoryBadge>
                    </PostTitleSection>
                  </PostHeader>

                  {post.boardFilePath && (
                    <PostImage size="large">
                      <img src={post.boardFilePath} alt={post.title} loading="lazy" />
                    </PostImage>
                  )}

                  <PostContent size="large">{post.content}</PostContent>

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
            })}

            {/* 중간 카드 (썸네일 있는 글) */}
            {displayedPosts.medium.map((post) => {
              const categoryInfo = getCategoryInfo(post.category);
              return (
                <PostCard key={post.idx} size="medium" onClick={() => handlePostSelect(post)}>
                  <PostHeader>
                    <PostTitleSection>
                      <PostTitleRow>
                        <PostTitle>{post.title}</PostTitle>
                        <PostNumber>#{post.idx}</PostNumber>
                      </PostTitleRow>
                      <CategoryBadge categoryColor={categoryInfo.color}>
                        <CategoryBadgeIcon>{categoryInfo.icon}</CategoryBadgeIcon>
                        {categoryInfo.label}
                      </CategoryBadge>
                    </PostTitleSection>
                  </PostHeader>

                  {post.boardFilePath && (
                    <PostImage size="medium">
                      <img src={post.boardFilePath} alt={post.title} loading="lazy" />
                    </PostImage>
                  )}

                  <PostContent size="medium">{post.content}</PostContent>

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
            })}

            {/* 작은 카드 (텍스트만 있는 글) */}
            {displayedPosts.small.map((post) => {
              const categoryInfo = getCategoryInfo(post.category);
              return (
                <PostCard key={post.idx} size="small" onClick={() => handlePostSelect(post)}>
                  <PostHeader>
                    <PostTitleSection>
                      <PostTitleRow>
                        <PostTitle>{post.title}</PostTitle>
                        <PostNumber>#{post.idx}</PostNumber>
                      </PostTitleRow>
                      <CategoryBadge categoryColor={categoryInfo.color}>
                        <CategoryBadgeIcon>{categoryInfo.icon}</CategoryBadgeIcon>
                        {categoryInfo.label}
                      </CategoryBadge>
                    </PostTitleSection>
                  </PostHeader>

                  <PostContent size="small">{post.content}</PostContent>

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
            })}
          </PostGrid>

          {hasMore && (
            <LoadMoreContainer>
              <LoadMoreButton onClick={handleLoadMore}>
                더 보기 ({displayedPosts.large.length + displayedPosts.medium.length + displayedPosts.small.length} / {categorizedPosts.large.length + categorizedPosts.medium.length + categorizedPosts.small.length})
              </LoadMoreButton>
            </LoadMoreContainer>
          )}
        </>
      )}

      <CommunityPostModal
        isOpen={isPostModalOpen}
        onClose={() => setIsPostModalOpen(false)}
        onSubmit={handlePostSubmit}
        loading={isSubmittingPost}
        currentUser={user}
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
  font-size: 15px;
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
  font-size: 14px;
`;

const PostGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: ${(props) => props.theme.spacing.lg};
  grid-auto-flow: row dense;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(4, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: ${(props) => props.theme.spacing.md};
    grid-auto-flow: row;
  }
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

const PostCard = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'size',
})`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  border-radius: ${(props) => props.theme.borderRadius.xl};
  padding: ${(props) => props.theme.spacing.xl};
  transition: transform 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
  background-image: linear-gradient(135deg, ${(props) =>
    props.theme.colors.surface} 0%, ${(props) => props.theme.colors.surfaceElevated} 100%);

  /* 대형 카드: 전체 너비 (12칸) */
  ${(props) => props.size === 'large' && `
    grid-column: span 12;
    min-height: 400px;
  `}

  /* 중간 카드: PC에서 6칸 (2개씩), Tablet에서 2칸 (2개씩) */
  ${(props) => props.size === 'medium' && `
    grid-column: span 6;
    min-height: 350px;

    @media (max-width: 1024px) {
      grid-column: span 2;
      min-height: 320px;
    }
  `}

  /* 작은 카드: PC에서 3칸 (4개씩), Tablet에서 2칸 (2개씩) */
  ${(props) => props.size === 'small' && `
    grid-column: span 3;
    min-height: 280px;

    @media (max-width: 1024px) {
      grid-column: span 2;
      min-height: 260px;
    }
  `}

  /* Mobile: 모든 카드 1열 */
  @media (max-width: 768px) {
    grid-column: span 1 !important;
    min-height: auto;
  }

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

const PostImage = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'size',
})`
  margin: ${props => props.theme.spacing.md} 0;
  border-radius: ${props => props.theme.borderRadius.lg};
  overflow: hidden;
  border: 1px solid ${props => props.theme.colors.border};

  img {
    width: 100%;
    height: auto;
    display: block;
    object-fit: cover;
    max-height: ${props => {
    if (props.size === 'large') return '500px';
    if (props.size === 'medium') return '300px';
    return '200px';
  }};
  }
`;

const PostTitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  flex-wrap: wrap;
`;

const PostTitle = styled.h3`
  color: ${props => props.theme.colors.text};
  font-size: ${props => props.theme.typography.h3.fontSize};
  font-weight: ${props => props.theme.typography.h3.fontWeight};
  margin: 0;
  line-height: 1.4;
  flex: 1;
`;

const PostNumber = styled.span`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.caption.fontSize};
  font-weight: 500;
  opacity: 0.7;
  white-space: nowrap;
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

const PostContent = styled.p.withConfig({
  shouldForwardProp: (prop) => prop !== 'size',
})`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.body1.fontSize};
  line-height: 1.7;
  margin: ${props => props.theme.spacing.md} 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  flex: 1;
  
  /* 크기에 따라 다른 줄 수 제한 */
  -webkit-line-clamp: ${props => {
    if (props.size === 'large') return 6;
    if (props.size === 'medium') return 4;
    return 3;
  }};
  
  min-height: ${props => {
    if (props.size === 'large') return '4.8em';
    if (props.size === 'medium') return '3.6em';
    return '2.7em';
  }};
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
  font-size: 15px;
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
  font-size: 14px;
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
  font-size: 15px;
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

const PopularScrollContainer = styled.div`
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: ${(props) => props.theme.spacing.sm};
  
  /* 스크롤바 스타일링 */
  &::-webkit-scrollbar {
    height: 8px;
  }
  
  &::-webkit-scrollbar-track {
    background: ${(props) => props.theme.colors.surface};
    border-radius: ${(props) => props.theme.borderRadius.md};
  }
  
  &::-webkit-scrollbar-thumb {
    background: ${(props) => props.theme.colors.border};
    border-radius: ${(props) => props.theme.borderRadius.md};
    
    &:hover {
      background: ${(props) => props.theme.colors.textSecondary};
    }
  }
`;

const PopularScrollContent = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.md};
  min-width: fit-content;
  padding: ${(props) => props.theme.spacing.xs} 0;
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
  flex-shrink: 0;
  width: 220px;
  min-width: 220px;

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

const LoadMoreContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.xl} 0;
  margin-top: ${props => props.theme.spacing.lg};
`;

const LoadMoreButton = styled.button`
  background: ${props => props.theme.colors.gradient};
  color: white;
  border: none;
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.xl};
  border-radius: ${props => props.theme.borderRadius.xl};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 12px rgba(255, 126, 54, 0.25);
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(255, 126, 54, 0.35);
  }
  
  &:active {
    transform: translateY(0);
  }
`;