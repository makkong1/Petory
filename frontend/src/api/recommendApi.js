import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api');

export const recommendApi = {
  getRecommendation: ({ lat, lng, context }) =>
    api.get('/recommend', { params: { lat, lng, context } }),

  // v3: 본 추천(/recommend)의 request_id 를 그대로 넘겨서 LLM 카피를 별도로 받음.
  // facilities 는 첫 응답에서 name + distance_m 만 추려서 전달.
  getCopy: ({ requestId, context, facilities, trends }) =>
    api.post('/recommend/copy', {
      request_id: requestId,
      context,
      facilities,
      trends,
    }),

  // v3: 카테고리별 트렌드 키워드의 일별 시계열 (Postgres 직접 조회).
  // 비-시설 카테고리(snack/food/clothes) 에서 특히 유용.
  getTrendTimeseries: ({ category, days = 14, topKeywords = 10 }) =>
    api.get(`/recommend/trends/${encodeURIComponent(category)}/timeseries`, {
      params: { days, top_keywords: topKeywords },
    }),

  // v3: 추천 노출/클릭 콜백. fire-and-forget — 실패해도 사용자 액션을 막지 않는다.
  // user_ref 는 백엔드가 userId 의 해시로 자동 채움.
  sendEvents: ({ requestId, events }) =>
    api.post('/recommend/events', { request_id: requestId, events }),
};
