import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { recommendApi } from '../../api/recommendApi';
import Spinner from '../Common/ui/Spinner';

const Card = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: 12px;
  padding: 16px;
  margin: 12px 0;
`;

const Title = styled.p`
  font-size: 13px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin: 0 0 8px;
`;

const RecommendText = styled.p`
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text};
  line-height: 1.6;
  margin: 0 0 12px;
`;

const TagRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
`;

const Tag = styled.span`
  background: ${({ theme }) => theme.colors.primaryLight || '#eef2ff'};
  color: ${({ theme }) => theme.colors.primary};
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 20px;
`;

const FacilityList = styled.ul`
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
`;

const FacilityItem = styled.li`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text};
  padding: 4px 0;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  &:last-child { border-bottom: none; }
`;

function RecommendCard({ lat, lng, context }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!lat || !lng || !context) return;

    setLoading(true);
    recommendApi
      .getRecommendation({ lat, lng, context })
      .then((res) => setData(res.data))
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [lat, lng, context]);

  if (loading) return <Spinner text="추천 정보 불러오는 중..." />;
  if (!data || (!data.recommendation && !data.trends?.length)) return null;

  return (
    <Card>
      <Title>AI 추천</Title>

      {data.recommendation && (
        <RecommendText>{data.recommendation}</RecommendText>
      )}

      {data.trends?.length > 0 && (
        <>
          <Title>요즘 인기 키워드</Title>
          <TagRow>
            {data.trends.map((t) => (
              <Tag key={t.keyword}># {t.keyword}</Tag>
            ))}
          </TagRow>
        </>
      )}

      {data.facilities?.length > 0 && (
        <>
          <Title style={{ marginTop: 12 }}>주변 시설</Title>
          <FacilityList>
            {data.facilities.map((f) => (
              <FacilityItem key={f.name}>
                {f.name} · {f.distance_m}m
              </FacilityItem>
            ))}
          </FacilityList>
        </>
      )}
    </Card>
  );
}

export default RecommendCard;
