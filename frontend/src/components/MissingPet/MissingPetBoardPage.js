import React, { useCallback, useEffect, useState } from 'react';
import styled from 'styled-components';
import { missingPetApi } from '../../api/missingPetApi';
import { useAuth } from '../../contexts/AuthContext';
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

const MissingPetBoardPage = () => {
  const { user } = useAuth();
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

  const fetchBoards = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = statusFilter === 'ALL' ? {} : { status: statusFilter };
      const response = await missingPetApi.list(params);
      setBoards(response.data || []);
      setLastUpdated(new Date());
    } catch (err) {
      const message = err.response?.data?.error || err.message;
      setError(`실종 신고 정보를 불러오지 못했습니다: ${message}`);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchBoards();
  }, [fetchBoards]);

  const loadBoardDetail = useCallback(
    async (id) => {
      if (!id) return;
      try {
        setDetailLoading(true);
        const response = await missingPetApi.get(id);
        setActiveBoard(response.data);
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
    await fetchBoards();
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
      await fetchBoards();

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

  const closeDrawer = useCallback(() => {
    setIsDrawerOpen(false);
    setActiveBoardId(null);
    setActiveBoard(null);
    setDetailLoading(false);
  }, []);

  const handleBoardDeleted = useCallback(async () => {
    await fetchBoards();
    closeDrawer();
  }, [fetchBoards, closeDrawer]);

  return (
    <>
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

        {boards.length === 0 && !loading && !error ? (
          <EmptyState>
            <p>등록된 실종 신고가 없습니다.</p>
            <span>새로운 제보가 등록되면 이곳에서 확인하실 수 있습니다.</span>
            <EmptyAction type="button" onClick={openCreateForm}>
              첫 제보 등록하기
            </EmptyAction>
          </EmptyState>
        ) : (
          <BoardGrid>
            {boards.map((board) => (
              <BoardCard key={board.idx} onClick={() => handleCardClick(board)}>
                {board.imageUrl && (
                  <CardImage>
                    <img src={board.imageUrl} alt={board.title} />
                  </CardImage>
                )}
                <CardBody>
                  <CardHeader>
                    <StatusBadge status={board.status}>
                      {statusLabel[board.status] || board.status}
                    </StatusBadge>
                    <LostDate>
                      {board.lostDate ? `실종일: ${board.lostDate}` : '실종일 정보 없음'}
                    </LostDate>
                  </CardHeader>
                  <CardTitleRow>
                    <CardTitle>{board.title}</CardTitle>
                    <CardNumber>#{board.idx}</CardNumber>
                  </CardTitleRow>
                  <MetaRow>
                    {board.petName && <MetaItem>이름: {board.petName}</MetaItem>}
                    {board.species && <MetaItem>종: {board.species}</MetaItem>}
                    {board.breed && <MetaItem>품종: {board.breed}</MetaItem>}
                    {board.color && <MetaItem>색상: {board.color}</MetaItem>}
                    {board.gender && <MetaItem>성별: {board.gender === 'M' ? '수컷' : '암컷'}</MetaItem>}
                  </MetaRow>
                  {board.lostLocation && (
                    <LostLocation>실종 위치: {board.lostLocation}</LostLocation>
                  )}
                  {board.content && <Description>{board.content}</Description>}
                </CardBody>
                <CardFooter>
                  <Reporter>제보자: {board.username || '알 수 없음'}</Reporter>
                  <CommentCount>댓글 {board.commentCount ?? 0}개</CommentCount>
                </CardFooter>
              </BoardCard>
            ))}
          </BoardGrid>
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
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: none;
  font-weight: 600;
  cursor: pointer;
  background: ${(props) => (props.active ? props.theme.colors.primary : 'transparent')};
  color: ${(props) => (props.active ? '#ffffff' : props.theme.colors.text)};
  transition: background 0.2s ease, color 0.2s ease, transform 0.1s ease;

  &:hover {
    transform: translateY(-1px);
    background: ${(props) =>
    props.active ? props.theme.colors.primaryDark : 'rgba(255, 126, 54, 0.1)'};
  }
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
  color: #ffffff;
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
  background: #fdecea;
  color: #c0392b;
  border: 1px solid #f5c6cb;
  border-radius: ${(props) => props.theme.borderRadius.md};
  padding: ${(props) => props.theme.spacing.md} ${(props) => props.theme.spacing.lg};
  margin-bottom: ${(props) => props.theme.spacing.lg};
`;

const UpdatedAt = styled.div`
  margin-bottom: ${(props) => props.theme.spacing.md};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.9rem;
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

const BoardGrid = styled.div`
  display: grid;
  gap: ${(props) => props.theme.spacing.lg};
  grid-template-columns: repeat(4, 1fr);

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: ${(props) => props.theme.spacing.md};
  }
`;

const BoardCard = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  display: grid;
  grid-template-rows: auto 1fr auto;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  overflow: hidden;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  }
`;

const CardImage = styled.div`
  width: 100%;
  height: 180px;
  background: ${(props) => props.theme.colors.surfaceHover};
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
`;

const CardBody = styled.div`
  padding: ${(props) => props.theme.spacing.lg};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
`;

const CardHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${(props) => props.theme.spacing.sm};
`;

const StatusBadge = styled.span.withConfig({
  shouldForwardProp: (prop) => prop !== 'status',
})`
  padding: ${(props) => props.theme.spacing.xs} ${(props) => props.theme.spacing.sm};
  border-radius: ${(props) => props.theme.borderRadius.md};
  font-size: 0.8rem;
  font-weight: 700;
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

const LostDate = styled.span`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const CardTitleRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${props => props.theme.spacing.sm};
  flex-wrap: wrap;
  margin-bottom: ${props => props.theme.spacing.md};
`;

const CardTitle = styled.h2`
  margin: 0;
  font-size: 1.1rem;
  color: ${(props) => props.theme.colors.text};
  flex: 1;
  min-width: 0;
`;

const CardNumber = styled.span`
  color: ${props => props.theme.colors.textLight};
  font-size: ${props => props.theme.typography.body2.fontSize || '0.9rem'};
  font-weight: 500;
  white-space: nowrap;
`;

const MetaRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${(props) => props.theme.spacing.xs};
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const MetaItem = styled.span`
  background: ${(props) => props.theme.colors.surfaceHover};
  border-radius: ${(props) => props.theme.borderRadius.sm};
  padding: 2px 8px;
`;

const LostLocation = styled.div`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const Description = styled.p`
  margin: 0;
  color: ${(props) => props.theme.colors.textSecondary};
  line-height: 1.5;
`;

const CardFooter = styled.div`
  padding: 0 ${(props) => props.theme.spacing.lg} ${(props) => props.theme.spacing.lg};
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const Reporter = styled.span`
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

const CommentCount = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
`;

const EmptyAction = styled.button`
  margin-top: ${(props) => props.theme.spacing.lg};
  border: none;
  background: ${(props) => props.theme.colors.primary};
  color: #ffffff;
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
  background: rgba(15, 23, 42, 0.35);
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
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.2);
  z-index: 1001;
  font-weight: 600;
  color: ${(props) => props.theme.colors.text};
`;

