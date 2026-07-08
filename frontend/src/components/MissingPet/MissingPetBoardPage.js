import React, { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { missingPetApi } from '../../api/missingPetApi';
import { useAuth } from '../../contexts/AuthContext';
import { useEmailVerification } from '../../hooks/useEmailVerification';
import PageNavigation from '../Common/PageNavigation';
import MissingPetBoardForm from './MissingPetBoardForm';
import MissingPetBoardDetail from './MissingPetBoardDetail';

const STATUS_OPTIONS = [
  { value: 'ALL', label: '전체' },
  { value: 'MISSING', label: '실종' },
  { value: 'FOUND', label: '발견' },
  { value: 'RESOLVED', label: '완료' },
];

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

const MissingPetBoardPage = () => {
  const { user } = useAuth();
  const { checkAndRedirect, EmailVerificationPromptComponent } = useEmailVerification('MISSING_PET');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [boards, setBoards] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [formLoading, setFormLoading] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [activeBoardId, setActiveBoardId] = useState(null);
  const [activeBoard, setActiveBoard] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 서버 사이드 페이징 상태
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);

  // 서버 사이드 페이징으로 게시글 가져오기
  const fetchBoards = useCallback(async (pageNum = 0) => {
    try {
      setLoading(true);
      setError(null);

      const requestParams = {
        ...(statusFilter !== 'ALL' ? { status: statusFilter } : {}),
        page: pageNum,
        size: pageSize
      };

      const response = await missingPetApi.list(requestParams);
      const pageData = response.data || {};
      const boardsData = pageData.boards || [];

      setBoards(boardsData);

      setTotalCount(pageData.totalCount || 0);
      setPage(pageNum);
      setLastUpdated(new Date());
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`실종 신고 정보를 불러오지 못했습니다: ${message}`);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, pageSize]);

  useEffect(() => {
    fetchBoards(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, pageSize]);

  const loadBoardDetail = useCallback(
    async (id) => {
      if (!id) return;
      
      try {
        setDetailLoading(true);
        
        // 성능 측정 시작
        const startTime = performance.now();
        const startMemory = performance.memory ? performance.memory.usedJSHeapSize : null;
        
        const response = await missingPetApi.get(id);
        
        // 성능 측정 종료
        const endTime = performance.now();
        const endMemory = performance.memory ? performance.memory.usedJSHeapSize : null;
        const executionTime = Math.round(endTime - startTime);
        const memoryUsed = startMemory && endMemory ? Math.round((endMemory - startMemory) / 1024 / 1024 * 100) / 100 : null;
        
        const board = response.data;
        const commentCount = board.comments ? board.comments.length : 0;
        
        // 성능 측정 로그 출력
        console.log('=== [성능 측정] 게시글 상세 조회 완료 ===');
        console.log(`  - 게시글 ID: ${id}`);
        console.log(`  - 실행 시간: ${executionTime}ms`);
        if (commentCount > 0) {
          console.log(`  - 평균 댓글당 시간: ${Math.round((executionTime / commentCount) * 100) / 100}ms`);
        }
        console.log(`  - 조회된 댓글 수: ${commentCount}개`);
        if (memoryUsed !== null) {
          console.log(`  - 메모리 사용량: ${memoryUsed > 0 ? '+' : ''}${memoryUsed}MB`);
        }
        if (performance.memory) {
          console.log(`  - 현재 메모리: ${Math.round(performance.memory.usedJSHeapSize / 1024 / 1024)}MB`);
          console.log(`  - 최대 메모리: ${Math.round(performance.memory.jsHeapSizeLimit / 1024 / 1024)}MB`);
        }
        
        setActiveBoard(board);
      } catch (err) {
        alert(err.response?.data?.error || err.message);
      } finally {
        setDetailLoading(false);
      }
    },
    []
  );

  // 전역 이벤트 리스너: 알림에서 실종제보 게시글로 이동할 때 사용
  useEffect(() => {
    const handleOpenMissingPetDetail = async (event) => {
      const { boardId } = event.detail;
      if (boardId) {
        console.log('알림에서 실종제보 게시글 열기:', boardId);
        setActiveBoardId(boardId);
        setIsDrawerOpen(true);
        setActiveBoard(null);
        await loadBoardDetail(boardId);
      }
    };

    window.addEventListener('openMissingPetDetail', handleOpenMissingPetDetail);
    return () => {
      window.removeEventListener('openMissingPetDetail', handleOpenMissingPetDetail);
    };
  }, [loadBoardDetail]);

  const refreshBoardDetail = useCallback(async () => {
    await fetchBoards(0);
    if (activeBoardId) {
      await loadBoardDetail(activeBoardId);
    }
  }, [fetchBoards, loadBoardDetail, activeBoardId]);

  const openCreateForm = () => {
    if (!user) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }
    setIsFormOpen(true);
  };

  const handleCreateBoard = async (form) => {
    if (!user) {
      window.dispatchEvent(new Event('showPermissionModal'));
      return;
    }

    // 이메일 인증 체크
    if (!checkAndRedirect()) {
      return; // 이메일 인증이 필요하면 확인 다이얼로그 표시되고 함수 종료
    }

    try {
      setFormLoading(true);
      const payload = {
        ...form,
        userId: user.idx,
        lostDate: form.lostDate || null,
        latitude:
          form.latitude && !Number.isNaN(Number.parseFloat(form.latitude))
            ? Number.parseFloat(form.latitude)
            : null,
        longitude:
          form.longitude && !Number.isNaN(Number.parseFloat(form.longitude))
            ? Number.parseFloat(form.longitude)
            : null,
      };

      Object.keys(payload).forEach((key) => {
        if (payload[key] === '' || payload[key] === undefined) {
          payload[key] = null;
        }
      });

      const response = await missingPetApi.create(payload);
      setIsFormOpen(false);
      await fetchBoards(0);

      if (response?.data?.idx) {
        const newId = response.data.idx;
        setActiveBoardId(newId);
        setIsDrawerOpen(true);
        await loadBoardDetail(newId);
      }
    } catch (err) {
      alert(err.response?.data?.error || err.message);
    } finally {
      setFormLoading(false);
    }
  };

  const handleCardClick = (board) => {
    setActiveBoardId(board.idx);
    setIsDrawerOpen(true);
    setActiveBoard(null);
    loadBoardDetail(board.idx);
  };

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
    setBoards([]);
  }, []);

  const closeDrawer = useCallback(() => {
    setIsDrawerOpen(false);
    setActiveBoardId(null);
    setActiveBoard(null);
    setDetailLoading(false);
  }, []);

  const handleBoardDeleted = useCallback(async () => {
    await fetchBoards(0);
    closeDrawer();
  }, [fetchBoards, closeDrawer]);

  return (
    <>
      <EmailVerificationPromptComponent />
      <Wrapper>
        <Header>
          <div>
            <Title>실종 동물 제보</Title>
            <Subtitle>실시간으로 공유되는 우리 동네 실종 동물 정보를 확인하세요.</Subtitle>
          </div>
          <Controls>
            <StatusFilter>
              {STATUS_OPTIONS.map((option) => (
                <StatusButton
                  key={option.value}
                  type="button"
                  onClick={() => setStatusFilter(option.value)}
                  active={statusFilter === option.value}
                >
                  {option.label}
                </StatusButton>
              ))}
            </StatusFilter>
            <ControlRow>
              <RefreshButton type="button" onClick={fetchBoards} disabled={loading}>
                {loading ? '불러오는 중...' : '새로고침'}
              </RefreshButton>
              <CreateButton type="button" onClick={openCreateForm}>
                + 실종 제보 등록
              </CreateButton>
            </ControlRow>
          </Controls>
        </Header>

        {error && <ErrorBanner>{error}</ErrorBanner>}

        {lastUpdated && (
          <UpdatedAt>
            마지막 업데이트:{' '}
            {lastUpdated.toLocaleString('ko-KR', {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
            })}
          </UpdatedAt>
        )}

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

        {loading && boards.length === 0 ? (
          <LoadingContainer>
            <LoadingSpinner />
            <LoadingMessage>실종 신고 정보를 불러오는 중...</LoadingMessage>
          </LoadingContainer>
        ) : boards.length === 0 && !error ? (
          <EmptyState>
            <p>등록된 실종 신고가 없습니다.</p>
            <span>새로운 제보가 등록되면 이곳에서 확인하실 수 있습니다.</span>
            <EmptyAction type="button" onClick={openCreateForm}>
              첫 제보 등록하기
            </EmptyAction>
          </EmptyState>
        ) : (
          <MissingList>
            {boards.map((board) => {
              const info = getElapsedInfo(board.lostDate);
              const isMissing = board.status === 'MISSING';
              const golden = isMissing && info && info.level === 'critical';
              const metaLine = [
                board.species,
                board.breed,
                board.color,
                board.gender && (board.gender === 'M' ? '수컷' : '암컷'),
              ].filter(Boolean).join(' · ');
              return (
                <MissingRow key={board.idx} onClick={() => handleCardClick(board)}>
                  <RowThumb>
                    {board.imageUrl ? (
                      <img
                        src={board.imageUrl}
                        alt={board.petName || board.title || '반려동물'}
                        onError={(e) => { e.target.style.display = 'none'; }}
                      />
                    ) : (
                      <span aria-hidden="true">🐾</span>
                    )}
                  </RowThumb>
                  <RowMain>
                    <RowTop>
                      <StatusBadge $status={board.status}>
                        {statusLabel[board.status] || '실종'}
                      </StatusBadge>
                      {isMissing && info && (
                        golden ? (
                          <GoldenBadge>⏱ 골든타임 · {info.text}</GoldenBadge>
                        ) : (
                          <ElapsedText>실종 {info.text.replace(' 경과', '째')}</ElapsedText>
                        )
                      )}
                    </RowTop>
                    <RowTitle>
                      {board.petName && <PetName>{board.petName}</PetName>}
                      {board.title}
                    </RowTitle>
                    <RowSub>
                      {metaLine}
                      {board.lostLocation ? `${metaLine ? ' · ' : ''}📍${board.lostLocation}` : ''}
                    </RowSub>
                  </RowMain>
                  <RowRight>
                    <CommentMeta>💬 {board.commentCount ?? 0}</CommentMeta>
                  </RowRight>
                </MissingRow>
              );
            })}
          </MissingList>
        )}

        {totalCount > 0 && (
          <PaginationWrapper>
            <PageNavigation
              currentPage={page}
              totalCount={totalCount}
              pageSize={pageSize}
              onPageChange={handlePageChange}
              loading={loading}
              showEdges
            />
          </PaginationWrapper>
        )}
      </Wrapper>

      <MissingPetBoardForm
        isOpen={isFormOpen}
        onClose={() => setIsFormOpen(false)}
        onSubmit={handleCreateBoard}
        loading={formLoading}
        currentUser={user}
      />

      {isDrawerOpen && (
        <>
          {detailLoading && !activeBoard ? (
            <>
              <DrawerBackdrop onClick={closeDrawer} />
              <DrawerLoader>상세 정보를 불러오는 중...</DrawerLoader>
            </>
          ) : (
            <MissingPetBoardDetail
              board={activeBoard}
              onClose={closeDrawer}
              onRefresh={refreshBoardDetail}
              currentUser={user}
              onDeleteBoard={handleBoardDeleted}
            />
          )}
        </>
      )}
    </>
  );
};

export default MissingPetBoardPage;

const Wrapper = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  padding: ${(props) => props.theme.spacing.xl} ${(props) => props.theme.spacing.lg};
`;

const Header = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.lg};
  margin-bottom: ${(props) => props.theme.spacing.xl};

  @media (min-width: 768px) {
    flex-direction: row;
    align-items: flex-end;
    justify-content: space-between;
  }
`;

const Title = styled.h1`
  margin: 0;
  font-size: 2rem;
  color: ${(props) => props.theme.colors.text};
`;

const Subtitle = styled.p`
  margin-top: ${(props) => props.theme.spacing.sm};
  color: ${(props) => props.theme.colors.textSecondary};
`;

const Controls = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};

  @media (min-width: 768px) {
    align-items: flex-end;
  }
`;

const StatusFilter = styled.div`
  display: inline-flex;
  flex-wrap: wrap;
  gap: ${(props) => props.theme.spacing.xs};
  background: ${(props) => props.theme.colors.surface};
  padding: ${(props) => props.theme.spacing.xs};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  border: 1px solid ${(props) => props.theme.colors.border};
`;

const StatusButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 8px 16px;
  border-radius: 50px;
  border: 1.5px solid ${(props) => props.active ? '#EF4444' : props.theme.colors.border};
  background: ${(props) => props.active ? '#EF4444' : 'transparent'};
  color: ${(props) => props.active ? 'white' : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${(props) => props.active ? '600' : '400'};
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  flex-shrink: 0;
`;

const RefreshButton = styled.button`
  border: none;
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s ease;
  color: ${(props) => props.theme.colors.text};

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.surfaceHover};
    color: ${(props) => props.theme.colors.primary};
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
`;

const CreateButton = styled.button`
  border: none;
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.1s ease;

  &:hover {
    background: ${(props) => props.theme.colors.primaryDark};
    transform: translateY(-1px);
  }
`;

const ControlRow = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
`;

const ErrorBanner = styled.div`
  background: ${(props) => props.theme.colors.error}12;
  color: ${(props) => props.theme.colors.errorDark};
  border: 1px solid ${(props) => props.theme.colors.error}40;
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.md} ${(props) => props.theme.spacing.lg};
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const UpdatedAt = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.md};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: ${(props) => props.theme.typography.body2.fontSize};
`;

/* ── 컴팩트 리스트(실종 제보) ── */
const MissingList = styled.div`
  max-width: 820px;
  margin: 0 auto;
  padding: 4px 20px 24px;

  @media (max-width: 768px) {
    padding: 4px 14px 24px;
  }
`;

const MissingRow = styled.div`
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 6px;
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: ${(props) => props.theme.colors.surfaceHover};
  }

  @media (max-width: 768px) {
    gap: 11px;
  }
`;

const RowThumb = styled.div`
  width: 66px;
  height: 66px;
  border-radius: 12px;
  flex-shrink: 0;
  overflow: hidden;
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.borderLight};
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  @media (max-width: 768px) {
    width: 58px;
    height: 58px;
    font-size: 24px;
  }
`;

const RowMain = styled.div`
  flex: 1;
  min-width: 0;
`;

const RowTop = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;
`;

const StatusBadge = styled.span`
  font-size: 11px;
  font-weight: 800;
  padding: 3px 9px;
  border-radius: 999px;
  color: #fff;
  background: ${(props) =>
    props.$status === 'FOUND'
      ? props.theme.colors.status.found
      : props.$status === 'RESOLVED'
      ? props.theme.colors.textMuted
      : props.theme.colors.status.missing};
`;

const GoldenBadge = styled.span`
  font-size: 11px;
  font-weight: 800;
  padding: 3px 9px;
  border-radius: 8px;
  color: ${(props) => props.theme.colors.status.missing};
  background: ${(props) => props.theme.colors.status.missing + '1F'};
`;

const ElapsedText = styled.span`
  font-size: 11.5px;
  font-weight: 700;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const RowTitle = styled.div`
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: ${(props) => props.theme.colors.text};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
`;

const PetName = styled.span`
  color: ${(props) => props.theme.colors.status.missing};
  margin-right: 6px;
`;

const RowSub = styled.div`
  font-size: 12.5px;
  color: ${(props) => props.theme.colors.textSecondary};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const RowRight = styled.div`
  flex-shrink: 0;
  text-align: right;
`;

const CommentMeta = styled.span`
  font-size: 12px;
  font-weight: 600;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const EmptyState = styled.div`
  text-align: center;
  padding: ${(props) => props.theme.spacing.xl};
  border: 1px dashed ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  color: ${(props) => props.theme.colors.textSecondary};

  p {
    margin-bottom: ${(props) => props.theme.spacing.sm};
    font-weight: 600;
    color: ${(props) => props.theme.colors.text};
  }
`;
























const EmptyAction = styled.button`
  margin-top: ${(props) => props.theme.spacing.lg};
  border: none;
  background: ${(props) => props.theme.colors.primary};
  color: ${(props) => props.theme.colors.textInverse};
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background: ${(props) => props.theme.colors.primaryDark};
  }
`;

const DrawerBackdrop = styled.div`
  position: fixed;
  inset: 0;
  background: ${(props) => props.theme.colors.overlay};
  backdrop-filter: blur(2px);
  z-index: 900;
`;

const DrawerLoader = styled.div`
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: ${(props) => props.theme.colors.surface};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.xl};
  box-shadow: ${(props) => props.theme.shadows.xl};
  z-index: 1001;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
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



