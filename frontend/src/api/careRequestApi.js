import { createAuthAxios } from './apiClient';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_CARE_REQUESTS } from '../mock/demoData';

const api = createAuthAxios('http://localhost:8080/api/care-requests');

const mockResolve = (data) => Promise.resolve({ data });

/**
 * 펫케어 요청 API.
 *
 * 케어는 지도 전용이다(2026-09-19 확정). 목록·검색·상세 페이지는 `d60bc05b`(2026-04-09)
 * 에서 탭 통합과 함께 삭제됐고, 그 뒤로 여기 있던 래퍼 8개는 호출처가 0이었다 —
 * 목록/검색/수정/삭제/상태변경/단건조회/댓글작성/댓글삭제. 남은 백엔드 엔드포인트는
 * 관리자 화면(`/api/admin/care-requests`)과 포트폴리오 근거로 살아 있어 그대로 둔다.
 *
 * 계약(제안/수락)은 `careOfferApi` 가 담당한다.
 */
export const careRequestApi = {
  // 케어 요청 생성 (지도의 CareCreateModal)
  createCareRequest: (data) =>
    isDemoMode() ? mockResolve({ idx: 99, ...data }) : api.post('', data),

  // 내 케어 요청 — 채팅방에서 "이 분께 맡기기" 후보를 고를 때 쓴다
  getMyCareRequests: () =>
    isDemoMode()
      ? mockResolve({ careRequests: DEMO_CARE_REQUESTS.filter((c) => c.userId === 1), totalCount: 1 })
      : api.get('/my-requests'),

  // 이행 완료 확인. 제공자가 먼저 알리고 요청자가 승인한다(요청자 승인 = 즉시 정산)
  confirmCompletion: (id) =>
    isDemoMode() ? mockResolve({}) : api.post(`/${id}/complete`),

  // 제공자가 자기 확인을 되돌린다 (실수 클릭 복구)
  cancelCompletion: (id) =>
    isDemoMode() ? mockResolve({}) : api.delete(`/${id}/complete`),

  // 제공자 댓글 — 지도 상세 패널에서 읽기 전용으로 보여준다(작성 UI는 없다)
  getComments: (careRequestId) =>
    isDemoMode()
      ? mockResolve([])
      : api.get(`/${careRequestId}/comments`),

  // 반경 기반 근처 케어 요청 조회 (지도 마커)
  getNearby: ({ lat, lng, radius = 5, limit }) => {
    if (isDemoMode()) return mockResolve([]);
    return api.get('/nearby', {
      params: { lat, lng, radius, ...(typeof limit === 'number' && { limit }) },
    });
  },
};
