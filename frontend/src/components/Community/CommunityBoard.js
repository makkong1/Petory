import React, { useEffect, useState, useCallback, useMemo } from 'react';
import styled, { useTheme } from 'styled-components';
import { boardApi } from '../../api/boardApi';
import { reportApi } from '../../api/reportApi';
import { usePermission } from '../../hooks/usePermission';
import { useAuth } from '../../contexts/AuthContext';
import PageNavigation from '../Common/PageNavigation';
import CommunityPostModal from './CommunityPostModal';
import CommunityDetailPage from './CommunityDetailPage';

const CATEGORY_THEME_KEY = {
  ALL: 'all', 일상: 'daily', 자랑: 'pride', PRIDE: 'pride',
  질문: 'question', 정보공유: 'info', 정보: 'info',
  후기: 'review', 모임: 'meetup', 공지: 'notice',
};

const CATEGORY_META = {
  ALL: { label: '전체', icon: '📋' },
  일상: { label: '일상', icon: '📖' },
  자랑: { label: '자랑', icon: '🐾' },
  질문: { label: '질문', icon: '❓' },
  정보공유: { label: '정보공유', icon: '📢' },
  정보: { label: '정보공유', icon: '📢' },
  후기: { label: '후기', icon: '📝' },
  모임: { label: '모임', icon: '🤝' },
  공지: { label: '공지', icon: '📢' },
  PRIDE: { label: '자랑', icon: '🐾' },
};

const CommunityBoard = () => {
  const { requireLogin } = usePermission();
  const { user, redirectToLogin } = useAuth();
  const theme = useTheme();

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [isPostModalOpen, setIsPostModalOpen] = useState(false);
  const [isSubmittingPost, setIsSubmittingPost] = useState(false);
  const [selectedBoard, setSelectedBoard] = useState(null);
  const [selectedBoardId, setSelectedBoardId] = useState(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [popularPosts, setPopularPosts] = useState([]);
  const [popularLoading, setPopularLoading] = useState(false);
  const [popularError, setPopularError] = useState('');
  const [popularPeriod, setPopularPeriod] = useState('WEEKLY');

  // 서버 사이드 페이징 상태
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  // Map + Array 조합: Map으로 빠른 조회/업데이트, Array로 순서 유지
  // React 상태에서 Map을 직접 사용하기 어려우므로 객체로 관리
  const [postsData, setPostsData] = useState({ map: {}, order: [] }); // { map: {[id]: BoardDTO}, order: [id, ...] }

  // 검색 상태
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchType, setSearchType] = useState('TITLE_CONTENT'); // NICKNAME, TITLE, CONTENT, TITLE_CONTENT
  const [isSearchMode, setIsSearchMode] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchPage, setSearchPage] = useState(0);
  const [searchTotalCount, setSearchTotalCount] = useState(0);
  // 검색 결과도 동일한 구조 사용
  const [searchPostsData, setSearchPostsData] = useState({ map: {}, order: [] });

  // 카테고리 변경 시 페이징 리셋은 fetchBoards에서 처리됨

  // Map + Array를 배열로 변환하는 헬퍼 함수
  const getPostsArray = useCallback((postsData) => {
    return postsData.order.map(id => postsData.map[id]).filter(Boolean);
  }, []);

  // 게시글 배열을 Map + Array 구조로 변환하는 헬퍼 함수
  const convertToMapAndOrder = useCallback((boards) => {
    const map = {};
    const order = [];
    boards.forEach(board => {
      if (board?.idx && !map[board.idx]) {
        map[board.idx] = board;
        order.push(board.idx);
      }
    });
    return { map, order };
  }, []);

  const categoryColors = theme.colors.category;

  const categories = useMemo(() => [
    { key: 'ALL', label: '전체', icon: '📋', color: categoryColors.all },
    { key: '일상', label: '일상', icon: '📖', color: categoryColors.daily },
    { key: '자랑', label: '자랑', icon: '🐾', color: categoryColors.pride },
    { key: '질문', label: '질문', icon: '❓', color: categoryColors.question },
    { key: '정보공유', label: '정보공유', icon: '📢', color: categoryColors.info },
    { key: '후기', label: '후기', icon: '📝', color: categoryColors.review },
    { key: '모임', label: '모임', icon: '🤝', color: categoryColors.meetup },
    { key: '공지', label: '공지', icon: '📢', color: categoryColors.notice },
  ], [categoryColors]);

  const getCategoryInfo = useCallback((category) => {
    const meta = CATEGORY_META[category] || { label: category || '전체', icon: '📋' };
    const themeKey = CATEGORY_THEME_KEY[category] || 'all';
    return { ...meta, color: categoryColors[themeKey] || categoryColors.all };
  }, [categoryColors]);

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

  // 서버 사이드 페이징으로 게시글 가져오기
  const fetchBoards = useCallback(async (pageNum = 0) => {
    try {
      setLoading(true);
      setError('');

      const requestParams = {
        category: activeCategory === 'ALL' ? null : activeCategory,
        page: pageNum,
        size: pageSize
      };

      const response = await boardApi.getAllBoards(requestParams);
      const pageData = response.data || {};

      const boards = pageData.boards || [];
      const newData = convertToMapAndOrder(boards);
      setPostsData(newData);

      setTotalCount(pageData.totalCount || 0);
      setPage(pageNum);
    } catch (err) {
      console.error('❌ [CommunityBoard] 게시글 조회 실패:', err);
      console.error('❌ [CommunityBoard] 에러 상세:', err.response?.data);
      alert(`[CommunityBoard] 게시글 조회 실패:\n${JSON.stringify(err.response?.data || err.message, null, 2)}`);
      const message = err.response?.data?.error || err.message || '게시글을 불러오지 못했습니다.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [activeCategory, pageSize, convertToMapAndOrder]);

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

  // 카테고리나 페이지 크기 변경 시 게시글 다시 로드
  useEffect(() => {
    fetchBoards(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeCategory, pageSize]);

  useEffect(() => {
    fetchPopularBoards();
  }, [fetchPopularBoards]);

  // 서버에서 이미 필터링되어 오므로 최소한만 필터링
  // 검색어 변경 시에는 재계산하지 않도록 최적화
  const filteredPosts = useMemo(() => {
    // 검색 모드일 때는 검색 결과 사용
    if (isSearchMode) {
      const searchArray = getPostsArray(searchPostsData);
      return searchArray.filter((post) => {
        if (post.deleted === true || post.status === 'DELETED' || post.status === 'BLINDED') {
          return false;
        }
        return true;
      });
    }

    // 백엔드에서 이미 삭제된 게시글은 필터링되어 오므로, 프론트엔드에서는 최소한만 필터링
    // deleted가 명시적으로 true인 경우만 제외 (null이나 undefined는 통과)
    const postsArray = getPostsArray(postsData);
    let result = postsArray.filter((post) => {
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

    // 카테고리는 서버에서 이미 필터링되어 옴
    return result;
  }, [postsData, isSearchMode, searchPostsData, getPostsArray]);
  // searchKeyword는 의존성에 포함하지 않음 (검색어 변경 시 재계산 불필요)

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

  // 표시할 게시글 (이미 categorizedPosts에 있음)
  const displayedPosts = categorizedPosts;

  const handlePageChange = useCallback((newPage) => {
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
    if (newPage >= 0 && newPage < totalPages) {
      fetchBoards(newPage);
    }
  }, [totalCount, pageSize, fetchBoards]);

  // 페이지 크기 변경 핸들러
  const handlePageSizeChange = useCallback((newSize) => {
    setPageSize(newSize);
    setPage(0);
    setPostsData({ map: {}, order: [] });
  }, []);

  // 검색어 변경 핸들러 (최적화)
  const handleSearchKeywordChange = useCallback((e) => {
    setSearchKeyword(e.target.value);
  }, []);

  // 검색 타입 변경 핸들러 (최적화)
  const handleSearchTypeChange = useCallback((e) => {
    setSearchType(e.target.value);
  }, []);

  const handleSearch = useCallback(async (pageNum = 0) => {
    if (!searchKeyword.trim()) {
      alert('검색어를 입력하세요');
      return;
    }

    try {
      setSearchLoading(true);
      setIsSearchMode(true);

      const response = await boardApi.searchBoards(searchKeyword.trim(), searchType, pageNum, pageSize);
      const pageData = response.data || {};
      const results = pageData.boards || [];
      const newData = convertToMapAndOrder(results);
      setSearchPostsData(newData);

      setSearchTotalCount(pageData.totalCount || 0);
      setSearchPage(pageNum);
    } catch (err) {
      console.error('❌ 검색 실패:', err);
      alert(`검색 실패: ${err.response?.data?.error || err.message}`);
      setSearchPostsData({ map: {}, order: [] });
    } finally {
      setSearchLoading(false);
    }
  }, [searchKeyword, searchType, pageSize, convertToMapAndOrder]);

  // 검색 취소 핸들러
  const handleCancelSearch = useCallback(() => {
    setIsSearchMode(false);
    setSearchKeyword('');
    setSearchPostsData({ map: {}, order: [] });
    setSearchType('TITLE_CONTENT');
    setSearchPage(0);
    setSearchTotalCount(0);
  }, []);

  const handleSearchPageChange = useCallback((newPage) => {
    const totalPages = Math.max(1, Math.ceil(searchTotalCount / pageSize));
    if (newPage >= 0 && newPage < totalPages) {
      handleSearch(newPage);
    }
  }, [searchTotalCount, pageSize, handleSearch]);

  const handleSearchKeyPress = useCallback((e) => {
    if (e.key === 'Enter') {
      handleSearch(0);
    }
  }, [handleSearch]);


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
      await boardApi.createBoard(payload);
      setIsPostModalOpen(false);
      // 게시글 작성 후 첫 페이지부터 다시 로드
      await fetchBoards(0);
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
    setSelectedBoard(null);
  };

  const handleCommentAdded = useCallback((boardId, isDelete = false) => {
    // 댓글 추가/삭제 시 해당 게시글의 댓글 카운트만 업데이트 (게시글 목록 전체 재조회 방지)
    if (boardId) {
      setPostsData((prev) => {
        const post = prev.map[boardId];
        if (post) {
          return {
            ...prev,
            map: {
              ...prev.map,
              [boardId]: {
                ...post,
                commentCount: Math.max(0, (post.commentCount ?? 0) + (isDelete ? -1 : 1)),
              },
            },
          };
        }
        return prev;
      });
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
      setPostsData((prev) => {
        const { [postIdx]: removed, ...restMap } = prev.map;
        return {
          map: restMap,
          order: prev.order.filter(id => id !== postIdx),
        };
      });
      if (selectedBoard?.idx === postIdx) {
        handleCommentDrawerClose();
      }
      if (selectedBoardId === postIdx) {
        handleDetailClose();
      }
      fetchBoards(0);
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      alert(`게시글을 삭제하지 못했습니다: ${message}`);
    }
  };

  const handleBoardDeleted = useCallback(
    (boardId) => {
      setPostsData((prev) => {
        const { [boardId]: removed, ...restMap } = prev.map;
        return {
          map: restMap,
          order: prev.order.filter(id => id !== boardId),
        };
      });
      if (selectedBoard?.idx === boardId) {
        handleCommentDrawerClose();
      }
      if (selectedBoardId === boardId) {
        handleDetailClose();
      }
      fetchBoards(0);
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
      setPostsData((prev) => {
        const post = prev.map[boardId];
        if (post) {
          return {
            ...prev,
            map: {
              ...prev.map,
              [boardId]: {
                ...post,
                likes: summary.likeCount,
                dislikes: summary.dislikeCount,
                userReaction: summary.userReaction,
              },
            },
          };
        }
        return prev;
      });
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
    setPostsData((prev) => {
      const post = prev.map[boardId];
      if (post) {
        return {
          ...prev,
          map: {
            ...prev.map,
            [boardId]: {
              ...post,
              likes: summary.likeCount,
              dislikes: summary.dislikeCount,
              userReaction: summary.userReaction,
            },
          },
        };
      }
      return prev;
    });
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
    setPostsData((prev) => {
      const post = prev.map[boardId];
      if (post) {
        return {
          ...prev,
          map: {
            ...prev.map,
            [boardId]: {
              ...post,
              views,
            },
          },
        };
      }
      return prev;
    });

    setSelectedBoard((prev) =>
      prev && prev.idx === boardId
        ? {
          ...prev,
          views,
        }
        : prev
    );
  }, []);


  if (loading && postsData.order.length === 0) {
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
        <HeaderWriteButton type="button" onClick={handleWriteClick}>
          글쓰기
        </HeaderWriteButton>
      </Header>

      <CategoryTabs>
        {categories.map((category) => (
          <CategoryTab
            key={category.key}
            active={activeCategory === category.key}
            onClick={() => setActiveCategory(category.key)}
            $categoryColor={category.color}
          >
            <CategoryIcon>{category.icon}</CategoryIcon>
            {category.label}
          </CategoryTab>
        ))}
      </CategoryTabs>

      <SearchContainer>
        <SearchBox>
          <SearchInput
            type="text"
            placeholder="게시글 검색..."
            value={searchKeyword}
            onChange={handleSearchKeywordChange}
            onKeyPress={handleSearchKeyPress}
          />
          <SearchTypeSelect
            value={searchType}
            onChange={handleSearchTypeChange}
          >
            <option value="TITLE_CONTENT">제목+내용</option>
            <option value="NICKNAME">작성자</option>
          </SearchTypeSelect>
          <SearchButton onClick={() => handleSearch(0)} disabled={searchLoading}>
            {searchLoading ? '검색 중...' : '🔍 검색'}
          </SearchButton>
          {isSearchMode && (
            <CancelSearchButton onClick={handleCancelSearch}>
              ✕ 취소
            </CancelSearchButton>
          )}
        </SearchBox>
        {isSearchMode && (
          <SearchInfo>
            검색 결과: {searchPostsData.order.length} / {searchTotalCount}개
            {searchKeyword && ` (검색어: "${searchKeyword}")`}
          </SearchInfo>
        )}
      </SearchContainer>

      <PageSizeSelector>
        <PageSizeLabel>페이지당 게시글 수:</PageSizeLabel>
        <PageSizeButtons>
          <PageSizeButton active={pageSize === 20} onClick={() => handlePageSizeChange(20)}>
            20
          </PageSizeButton>
          <PageSizeButton active={pageSize === 50} onClick={() => handlePageSizeChange(50)}>
            50
          </PageSizeButton>
          <PageSizeButton active={pageSize === 100} onClick={() => handlePageSizeChange(100)}>
            100
          </PageSizeButton>
        </PageSizeButtons>
      </PageSizeSelector>

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
                      <CategoryBadge $categoryColor={categoryInfo.color}>
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
                      <CategoryBadge $categoryColor={categoryInfo.color}>
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
                      <CategoryBadge $categoryColor={categoryInfo.color}>
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

          {((isSearchMode ? searchTotalCount : totalCount) > 0) && (
            <PaginationWrapper>
              <PageNavigation
                currentPage={isSearchMode ? searchPage : page}
                totalCount={isSearchMode ? searchTotalCount : totalCount}
                pageSize={pageSize}
                onPageChange={isSearchMode ? handleSearchPageChange : handlePageChange}
                loading={isSearchMode ? searchLoading : loading}
              />
            </PaginationWrapper>
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
  gap: ${props => props.theme.spacing.md};
  margin-bottom: ${props => props.theme.spacing.xxl};
  padding-bottom: ${props => props.theme.spacing.xl};
  border-bottom: 2px solid ${props => props.theme.colors.borderLight};
  
  @media (max-width: 768px) {
    flex-direction: column;
    align-items: stretch;
  }
`;

const TitleSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${props => props.theme.spacing.xs};
`;

const TitleIcon = styled.span`
  font-size: ${props => props.theme.typography.h1.fontSize};
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

const HeaderWriteButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: ${props => props.theme.spacing.xs};
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.xl};
  border: none;
  border-radius: ${props => props.theme.borderRadius.full};
  background: linear-gradient(135deg, #e8714a 0%, #c9573a 100%);
  color: ${props => props.theme.colors.textInverse};
  font-size: ${props => props.theme.typography.body1.fontSize};
  font-weight: 600;
  cursor: pointer;
  box-shadow: ${props => props.theme.shadows.md};
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: ${props => props.theme.shadows.lg};
  }

  &:active {
    transform: translateY(0);
  }

  @media (max-width: 768px) {
    align-self: stretch;
    margin-top: 0;
    padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
    text-align: center;
    justify-content: center;
  }
`;

const CategoryTabs = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.md};
  margin-bottom: ${props => props.theme.spacing.xl};
  flex-wrap: wrap;
  padding-bottom: ${props => props.theme.spacing.md};
`;

const CategoryTab = styled.button`
  padding: 8px 16px;
  border-radius: 50px;
  border: 1.5px solid ${props => props.active ? props.theme.colors.primary : props.theme.colors.border};
  background: ${props => props.active ? props.theme.colors.primary : 'transparent'};
  color: ${props => props.active ? 'white' : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${props => props.active ? '600' : '400'};
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.theme.colors.primary};
  }
`;

const CategoryIcon = styled.span`
  font-size: ${props => props.theme.typography.body2.fontSize};
`;

const PostGrid = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;

  @media (min-width: 769px) {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    padding: 24px;
    max-width: 1200px;
    margin: 0 auto;
  }
`;


const ErrorBanner = styled.div`
  background: ${props => props.theme.colors.errorSoft};
  color: ${props => props.theme.colors.error};
  border: 1px solid ${props => props.theme.colors.error}33;
  border-radius: ${props => props.theme.borderRadius.lg};
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
  margin-bottom: ${props => props.theme.spacing.lg};
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const PostCard = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'size',
})`
  background: ${(props) => props.theme.colors.surfaceElevated};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px ${(props) => props.theme.colors.shadow};
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px ${(props) => props.theme.colors.shadowHover};
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
    if (props.size === 'large') return '420px';
    if (props.size === 'medium') return '260px';
    return '180px';
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
  background: ${props => `linear-gradient(135deg, ${props.$categoryColor} 0%, ${props.$categoryColor}dd 100%)`};
  color: ${props => props.theme.colors.textInverse};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.md};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.caption.fontSize};
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: ${props => props.theme.spacing.xs};
  width: fit-content;
  box-shadow: 0 2px 8px ${props => `${props.$categoryColor}40`};
`;

const CategoryBadgeIcon = styled.span`
  font-size: ${props => props.theme.typography.caption.fontSize};
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
  width: 40px;
  height: 40px;
  border-radius: ${(props) => props.theme.borderRadius.full};
  background: ${(props) => props.theme.colors.gradient};
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${(props) => props.theme.colors.textInverse};
  font-weight: 700;
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  box-shadow: ${(props) => props.theme.shadows.sm};
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
  font-size: ${props => props.theme.typography.caption.fontSize};
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
  border: 1px solid ${props => props.theme.colors.border};
  color: ${(props) => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.sm};
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
  border: 1px solid ${props => props.theme.colors.border};
  color: ${(props) => props.theme.colors.textSecondary};
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.sm};
  min-width: fit-content;
`;

const StatIcon = styled.span`
  font-size: ${props => props.theme.typography.body2.fontSize};
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
    color: ${props => props.theme.colors.error};
    background: ${props => props.theme.colors.errorSoft};
    transform: scale(1.1);
  }
`;

const DeleteButton = styled.button`
  background: none;
  border: 1px solid ${props => props.theme.colors.error};
  color: ${props => props.theme.colors.error};
  cursor: pointer;
  padding: ${props => props.theme.spacing.sm};
  border-radius: ${props => props.theme.borderRadius.md};
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.theme.colors.errorSoft};
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
  font-size: ${props => props.theme.typography.body1.fontSize};
`;

const PopularSection = styled.section`
  margin-bottom: ${(props) => props.theme.spacing.xl};
  padding: ${(props) => props.theme.spacing.lg};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  border-radius: ${(props) => props.theme.borderRadius.lg};
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
  font-size: ${props => props.theme.typography.h2.fontSize};
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
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.md};
  border: none;
  background: ${(props) => (props.active ? props.theme.colors.primary : 'transparent')};
  color: ${(props) => (props.active ? props.theme.colors.textInverse : props.theme.colors.textSecondary)};
  cursor: pointer;
  font-weight: 600;
  font-size: ${props => props.theme.typography.body2.fontSize};
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
  gap: ${(props) => props.theme.spacing.sm};
  padding: ${(props) => props.theme.spacing.sm};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  border-radius: ${(props) => props.theme.borderRadius.md};
  background: ${(props) => props.theme.colors.surface};
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  text-align: left;
  flex-shrink: 0;
  width: 180px;
  min-width: 180px;

  &:hover {
    transform: translateY(-4px);
    box-shadow: ${(props) => props.theme.shadows.lg};
  }
`;

const PopularRank = styled.span`
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: 700;
  color: ${(props) => props.theme.colors.primary};
  min-width: 20px;
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
  font-size: ${(props) => props.theme.typography.body2.fontSize};
  flex-wrap: wrap;
`;

const PopularStat = styled.span`
  display: inline-flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.xs};
`;

const PopularThumb = styled.div`
  width: 48px;
  height: 48px;
  border-radius: ${(props) => props.theme.borderRadius.sm};
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
  font-size: ${props => props.theme.typography.hero.fontSize};
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

const SearchContainer = styled.div`
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const SearchBox = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.sm};
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.sm};
  
  @media (max-width: 768px) {
    flex-wrap: wrap;
  }
`;

const SearchInput = styled.input`
  flex: 1;
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body1.fontSize};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  
  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 0 2px ${props => props.theme.colors.primary}20;
  }
  
  &::placeholder {
    color: ${props => props.theme.colors.textLight};
  }
`;

const SearchTypeSelect = styled.select`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body2.fontSize};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  cursor: pointer;
  
  &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.primary};
  }
  
  @media (max-width: 768px) {
    flex: 1;
    min-width: 120px;
  }
`;

const SearchButton = styled.button`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.lg};
  background: ${props => props.theme.colors.gradient};
  color: ${props => props.theme.colors.textInverse};
  border: none;
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  
  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: ${props => props.theme.shadows.md};
  }
  
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const CancelSearchButton = styled.button`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body2.fontSize};
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  
  &:hover {
    background: ${props => props.theme.colors.surfaceHover};
    color: ${props => props.theme.colors.error};
  }
`;

const SearchInfo = styled.div`
  padding: ${props => props.theme.spacing.sm} ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.md};
  font-size: ${props => props.theme.typography.body2.fontSize};
  color: ${props => props.theme.colors.textSecondary};
`;

const PageSizeSelector = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.md};
  padding: ${props => props.theme.spacing.md} ${props => props.theme.spacing.lg};
  margin-bottom: ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surface};
  border-radius: ${props => props.theme.borderRadius.md};
`;

const PageSizeLabel = styled.span`
  font-size: ${props => props.theme.typography.body2.fontSize};
  color: ${props => props.theme.colors.textSecondary};
  font-weight: 500;
`;

const PageSizeButtons = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.xs};
`;

const PageSizeButton = styled.button`
  padding: ${props => props.theme.spacing.xs} ${props => props.theme.spacing.md};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  background: ${props => props.active ? props.theme.colors.primary : 'transparent'};
  color: ${props => props.active ? props.theme.colors.textInverse : props.theme.colors.text};
  font-size: ${props => props.theme.typography.body2.fontSize};
  font-weight: ${props => props.active ? 600 : 400};
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: ${props => props.active ? props.theme.colors.primary : props.theme.colors.background};
    border-color: ${props => props.theme.colors.primary};
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const PaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: ${props => props.theme.spacing.xl} 0;
  margin-top: ${props => props.theme.spacing.lg};
`;