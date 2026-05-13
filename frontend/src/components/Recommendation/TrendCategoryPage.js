import React, { useEffect, useMemo, useState } from 'react';
import styled from 'styled-components';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { recommendApi } from '../../api/recommendApi';
import Spinner from '../Common/ui/Spinner';

// 가이드 §5: snack/food/clothes 는 공인 시설 없이 트렌드 중심.
// grooming/hospital 도 시계열로 보면 변동을 볼 수 있어 포함.
const CATEGORIES = [
  { id: 'snack', label: '간식' },
  { id: 'food', label: '사료' },
  { id: 'clothes', label: '의류' },
  { id: 'grooming', label: '미용' },
  { id: 'hospital', label: '병원' },
];

// 라인 차트 시리즈 컬러 팔레트. 동일 키워드는 동일 색을 유지하기 위해 정렬된 인덱스로 매핑.
const COLORS = [
  '#FF7E36', '#3B82F6', '#10B981', '#A855F7', '#EF4444',
  '#F59E0B', '#06B6D4', '#84CC16', '#EC4899', '#6366F1',
];

function pivotPoints(points) {
  // points: [{date, keyword, score}, ...] (date ASC, score DESC by 서버 정렬)
  // → recharts 가 쓰기 좋은 형태로 피벗:
  //   chartRows: [{ date, keywordA: 32, keywordB: 28, ... }, ...]
  //   keywords:  ["keywordA", "keywordB", ...] (시리즈 정의용, 등장 빈도순)
  if (!points || points.length === 0) {
    return { chartRows: [], keywords: [] };
  }

  const rowsByDate = new Map();
  const keywordFrequency = new Map();

  for (const p of points) {
    if (!rowsByDate.has(p.date)) {
      rowsByDate.set(p.date, { date: p.date });
    }
    rowsByDate.get(p.date)[p.keyword] = p.score;
    keywordFrequency.set(p.keyword, (keywordFrequency.get(p.keyword) || 0) + 1);
  }

  const chartRows = Array.from(rowsByDate.values()).sort((a, b) =>
    a.date.localeCompare(b.date)
  );
  // 등장 빈도 많은 키워드부터 (legend 가 보기 좋아짐)
  const keywords = Array.from(keywordFrequency.entries())
    .sort((a, b) => b[1] - a[1])
    .map((e) => e[0]);

  return { chartRows, keywords };
}

function latestSnapshot(points) {
  if (!points || points.length === 0) return [];
  // 마지막 날짜의 점수만 추려서 키워드 카드 리스트로
  const lastDate = points.reduce(
    (acc, p) => (p.date > acc ? p.date : acc),
    points[0].date
  );
  return points
    .filter((p) => p.date === lastDate)
    .sort((a, b) => b.score - a.score);
}

function TrendCategoryPage() {
  const [category, setCategory] = useState('snack');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setData(null);
    recommendApi
      .getTrendTimeseries({ category, days: 14, topKeywords: 10 })
      .then((res) => {
        if (cancelled) return;
        setData(res.data);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(e?.response?.data?.message || '트렌드 데이터를 불러오지 못했어요.');
      })
      .finally(() => {
        if (cancelled) return;
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [category]);

  const { chartRows, keywords } = useMemo(
    () => pivotPoints(data?.points),
    [data]
  );
  const snapshot = useMemo(() => latestSnapshot(data?.points), [data]);

  return (
    <Wrap>
      <Header>
        <h1>요즘 인기 키워드</h1>
        <Sub>
          최근 {data?.days ?? 14}일간 카테고리별 트렌드. 펫 데이터 서버가 네이버 블로그 검색·형태소 분석으로 매일 갱신.
        </Sub>
      </Header>

      <TabBar>
        {CATEGORIES.map((c) => (
          <Tab
            key={c.id}
            $active={category === c.id}
            onClick={() => setCategory(c.id)}
          >
            {c.label}
          </Tab>
        ))}
      </TabBar>

      {loading && <Spinner text="트렌드 불러오는 중..." />}
      {error && <ErrorBox>{error}</ErrorBox>}

      {!loading && !error && data && snapshot.length === 0 && (
        <ErrorBox>아직 이 카테고리의 트렌드 데이터가 없어요.</ErrorBox>
      )}

      {!loading && !error && snapshot.length > 0 && (
        <>
          <SectionTitle>오늘 톱 키워드</SectionTitle>
          <KeywordGrid>
            {snapshot.slice(0, 10).map((p, i) => (
              <KeywordCard key={p.keyword}>
                <Rank>#{i + 1}</Rank>
                <Keyword>{p.keyword}</Keyword>
                <ScoreBar>
                  <ScoreFill style={{ width: `${Math.min(100, p.score)}%` }} />
                </ScoreBar>
                <ScoreText>{Math.round(p.score)}</ScoreText>
              </KeywordCard>
            ))}
          </KeywordGrid>

          <SectionTitle>14일 추이</SectionTitle>
          <ChartWrap>
            <ResponsiveContainer width="100%" height={320}>
              <LineChart data={chartRows} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#eee" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                {keywords.slice(0, 5).map((k, i) => (
                  <Line
                    key={k}
                    type="monotone"
                    dataKey={k}
                    stroke={COLORS[i % COLORS.length]}
                    strokeWidth={2}
                    dot={false}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
            <ChartHint>
              상위 5개 키워드만 차트에 표시합니다. 전체 키워드는 위쪽 카드 리스트 참고.
            </ChartHint>
          </ChartWrap>
        </>
      )}
    </Wrap>
  );
}

export default TrendCategoryPage;

const Wrap = styled.div`
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px 64px;
`;

const Header = styled.div`
  margin-bottom: 16px;
  h1 {
    font-size: 22px;
    font-weight: 700;
    margin: 0 0 6px;
    color: ${({ theme }) => theme.colors.text};
  }
`;

const Sub = styled.p`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin: 0;
  line-height: 1.5;
`;

const TabBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 20px;
`;

const Tab = styled.button`
  background: ${({ theme, $active }) =>
    $active ? theme.colors.primary : theme.colors.surface};
  color: ${({ theme, $active }) =>
    $active ? '#fff' : theme.colors.text};
  border: 1px solid
    ${({ theme, $active }) =>
      $active ? theme.colors.primary : theme.colors.border};
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  &:hover {
    opacity: 0.9;
  }
`;

const SectionTitle = styled.h2`
  font-size: 15px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
  margin: 20px 0 12px;
`;

const KeywordGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
`;

const KeywordCard = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const Rank = styled.span`
  font-size: 11px;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-weight: 600;
`;

const Keyword = styled.span`
  font-size: 15px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const ScoreBar = styled.div`
  background: ${({ theme }) => theme.colors.background || '#f5f5f7'};
  border-radius: 4px;
  height: 6px;
  overflow: hidden;
`;

const ScoreFill = styled.div`
  background: ${({ theme }) => theme.colors.primary};
  height: 100%;
`;

const ScoreText = styled.span`
  font-size: 11px;
  color: ${({ theme }) => theme.colors.textSecondary};
  align-self: flex-end;
`;

const ChartWrap = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: 10px;
  padding: 16px 8px 12px;
`;

const ChartHint = styled.p`
  font-size: 11px;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin: 8px 12px 0;
`;

const ErrorBox = styled.div`
  background: ${({ theme }) => theme.colors.background || '#fafafa'};
  border: 1px dashed ${({ theme }) => theme.colors.border};
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 14px;
`;
