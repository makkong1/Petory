import React, { useEffect, useState } from "react";
import styled from "styled-components";
import { recommendApi } from "../../api/recommendApi";
import Spinner from "../Common/ui/Spinner";

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

// LLM 카피는 추천 카드의 핵심 메시지. 좌측 컬러 바 + 폰트/라인하이트 ↑ 로 prominent.
const RecommendQuote = styled.blockquote`
  margin: 0 0 14px;
  padding: 10px 12px;
  border-left: 3px solid ${({ theme }) => theme.colors.primary};
  background: ${({ theme }) => theme.colors.primaryLight || "#eef2ff"};
  border-radius: 6px;
  font-size: 15px;
  line-height: 1.6;
  color: ${({ theme }) => theme.colors.text};
`;

const CopyLoading = styled.p`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-style: italic;
  margin: 0 0 12px;
`;

const TrendHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
`;

const MoreLink = styled.button`
  background: none;
  border: none;
  font-size: 12px;
  color: ${({ theme }) => theme.colors.primary};
  cursor: pointer;
  padding: 0;
  &:hover {
    text-decoration: underline;
  }
`;

const TagRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
`;

const Tag = styled.span`
  background: ${({ theme }) => theme.colors.primaryLight || "#eef2ff"};
  color: ${({ theme }) => theme.colors.primary};
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 20px;
`;

// 콜백 이벤트 전송 헬퍼. fire-and-forget — 실패해도 무시.
function sendRecommendEvents(requestId, eventList) {
  if (!requestId || !eventList || eventList.length === 0) return;
  // facility_id 또는 source_id 가 있는 이벤트만 보냄 (가이드 §6)
  const usable = eventList.filter(
    (e) => e.facility_id != null || e.source_id != null
  );
  if (usable.length === 0) return;
  recommendApi.sendEvents({ requestId, events: usable }).catch(() => {});
}

function RecommendCard({ lat, lng, context, onFacilitiesLoaded }) {
  const [data, setData] = useState(null);
  const [loadingMain, setLoadingMain] = useState(true);
  const [copy, setCopy] = useState(null);
  const [loadingCopy, setLoadingCopy] = useState(false);

  // 추천 카드가 마운트되고 데이터가 도착했을 때 view 이벤트 배치 1콜 전송.
  // request_id 가 바뀔 때만 (= 새 추천 결과가 도착했을 때만) 한 번 발송.
  useEffect(() => {
    const requestId = data?.request_id;
    const facilities = data?.facilities ?? [];
    if (!requestId || facilities.length === 0) return;

    const occurredAt = new Date().toISOString();
    const events = facilities.map((f) => ({
      facility_id: f.id ?? null,
      source_id: f.source_id ?? null,
      event: "view",
      occurred_at: occurredAt,
    }));
    sendRecommendEvents(requestId, events);
  }, [data?.request_id]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!lat || !lng || !context) return;

    let cancelled = false;
    setLoadingMain(true);
    setCopy(null);
    setLoadingCopy(false);

    recommendApi
      .getRecommendation({ lat, lng, context })
      .then((res) => {
        if (cancelled) return;
        const main = res.data;
        setData(main);
        if (onFacilitiesLoaded) {
          onFacilitiesLoaded({
            facilities: main?.facilities ?? [],
            requestId: main?.request_id ?? null,
          });
        }

        // v3 카피 분리: 본 추천이 비어 있지 않으면 LLM 카피를 두 번째 콜로 요청.
        const requestId = main?.request_id;
        const facilities = main?.facilities ?? [];
        if (requestId && facilities.length > 0) {
          setLoadingCopy(true);
          recommendApi
            .getCopy({
              requestId,
              context,
              facilities: facilities.map((f) => ({
                name: f.name,
                distance_m: f.distance_m,
              })),
              trends: main?.trends ?? [],
            })
            .then((copyRes) => {
              if (cancelled) return;
              setCopy(copyRes.data);
            })
            .catch(() => {
              if (cancelled) return;
              setCopy(null);
            })
            .finally(() => {
              if (cancelled) return;
              setLoadingCopy(false);
            });
        }
      })
      .catch(() => {
        if (cancelled) return;
        setData(null);
        onFacilitiesLoaded?.({ facilities: [], requestId: null });
      })
      .finally(() => {
        if (cancelled) return;
        setLoadingMain(false);
      });

    return () => {
      cancelled = true;
    };
  }, [lat, lng, context]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loadingMain) return <Spinner text="추천 정보 불러오는 중..." />;
  if (
    !data ||
    (!data.recommendation && !data.trends?.length && !data.facilities?.length)
  )
    return null;

  // 카피 우선순위: LLM/rule 카피(/recommend/copy) → 본 추천 응답의 recommendation 폴백.
  const displayedCopy = copy?.recommendation || data.recommendation;

  return (
    <Card>
      <Title>AI 추천</Title>

      {displayedCopy && <RecommendQuote>{displayedCopy}</RecommendQuote>}
      {!copy && loadingCopy && (
        <CopyLoading>추천 코멘트 생성 중...</CopyLoading>
      )}

      {data.trends?.length > 0 && (
        <>
          <TrendHeader>
            <Title style={{ margin: 0 }}>요즘 인기 키워드</Title>
            <MoreLink
              type="button"
              onClick={() =>
                window.setActiveTab && window.setActiveTab("trends")
              }
            >
              트렌드 자세히 보기 →
            </MoreLink>
          </TrendHeader>
          <TagRow>
            {data.trends.map((t) => (
              <Tag key={t.keyword}># {t.keyword}</Tag>
            ))}
          </TagRow>
        </>
      )}
    </Card>
  );
}

export default RecommendCard;
