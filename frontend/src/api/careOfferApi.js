import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/care-offers');

/**
 * 케어 계약 체결 API. 요청자가 제안하고 제공자가 수락/거절한다.
 *
 * 예전엔 채팅방 API(`/api/conversations/{idx}/confirm-deal`)가 이 일을 했다. 계약이 방에 묶여
 * 있어서 방 타입이 어긋나면 경로 전체가 조용히 멈췄다 — 계약을 care 로 옮기면서 같이 옮겼다.
 */
export const careOfferApi = {
  // 요청자 → 제공자 제안. 같은 제공자에게 두 번 보내면 기존 제안을 돌려준다.
  offer: (careRequestIdx, providerIdx) =>
    api.post('', null, { params: { careRequestIdx, providerIdx } }),

  accept: (offerIdx) => api.post(`/${offerIdx}/accept`),

  reject: (offerIdx) => api.post(`/${offerIdx}/reject`),

  // 상대와 나 사이에 살아 있는 제안. 채팅방은 계약을 모르므로 상대 사용자로 찾는다.
  between: (otherUserIdx) => api.get('/between', { params: { otherUserIdx } }),
};
