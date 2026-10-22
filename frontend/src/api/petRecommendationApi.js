import { createAuthAxios } from './apiClient';
import { getToken } from './tokenStorage';

const api = createAuthAxios('http://localhost:8080/api/pet-recommend');

export const petRecommendationApi = {
  getSignals: async () => {
    if (!getToken()) return [];
    try {
      const res = await api.get('/signals');
      return Array.isArray(res.data) ? res.data : [];
    } catch (error) {
      return [];
    }
  },

  // 장소 행동 신호를 적재한다. 인기 점수(최근 30일)의 유일한 입력이다.
  // 화면 동작에 영향을 주면 안 되므로 비로그인·실패는 조용히 넘긴다.
  interact: async (locationIdx, type) => {
    if (!getToken() || !locationIdx) return;
    try {
      await api.post('/interact', null, { params: { locationIdx, type } });
    } catch (error) {
      // 수집 실패가 상세 조회·길찾기를 막지 않는다
    }
  },
};
