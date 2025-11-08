import React, { useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import { missingPetApi } from '../../api/missingPetApi';

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
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [boards, setBoards] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);

  const fetchBoards = useMemo(() => async () => {
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

  return (
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
          <RefreshButton type="button" onClick={fetchBoards} disabled={loading}>
            {loading ? '불러오는 중...' : '새로고침'}
          </RefreshButton>
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
        </EmptyState>
      ) : (
        <BoardGrid>
          {boards.map((board) => (
            <BoardCard key={board.idx}>
              <CardHeader>
                <StatusBadge status={board.status}>{statusLabel[board.status] || board.status}</StatusBadge>
                <LostDate>
                  {board.lostDate ? `실종일: ${board.lostDate}` : '실종일 정보 없음'}
                </LostDate>
              </CardHeader>
              <CardTitle>{board.title}</CardTitle>
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
              <CardFooter>
                <Reporter>제보자: {board.username || '알 수 없음'}</Reporter>
                <CommentCount>댓글 {board.commentCount ?? 0}개</CommentCount>
              </CardFooter>
            </BoardCard>
          ))}
        </BoardGrid>
      )}
    </Wrapper>
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
  font-size: 2.4rem;
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

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.surfaceHover};
    color: ${(props) => props.theme.colors.primary};
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
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
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
`;

const BoardCard = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: ${(props) => props.theme.borderRadius.lg};
  padding: ${(props) => props.theme.spacing.lg};
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.sm};
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
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

const CardTitle = styled.h2`
  margin: 0;
  font-size: 1.3rem;
  color: ${(props) => props.theme.colors.text};
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
  color: ${(props) => props.theme.colors.textSecondary};
  margin-top: ${(props) => props.theme.spacing.sm};
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

