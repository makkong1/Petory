import { createAuthAxios } from './apiClient';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_ACTIVITIES } from '../mock/demoData';

const api = createAuthAxios('http://localhost:8080/api/activities');

const mockResolve = (data) => Promise.resolve({ data });

export const activityApi = {
  // 내 활동 조회 (기존 API - 하위 호환성 유지)
  getMyActivities: (userId) =>
    isDemoMode() ? mockResolve(DEMO_ACTIVITIES) : api.get('/my', { params: { userId } }),

  // 내 활동 조회 (페이징 지원)
  getMyActivitiesWithPaging: (params = {}) => {
    if (isDemoMode()) {
      const { page = 0, size = 20 } = params;
      const start = page * size;
      const activities = DEMO_ACTIVITIES.slice(start, start + size);
      return mockResolve({
        activities,
        totalCount: DEMO_ACTIVITIES.length,
        hasNext: start + activities.length < DEMO_ACTIVITIES.length,
      });
    }
    const { userId, filter = 'ALL', page = 0, size = 20, ...otherParams } = params;
    const requestParams = {
      userId,
      filter,
      page,
      size,
      ...otherParams,
      _t: Date.now()
    };
    return api.get('/my/paging', {
      params: requestParams,
      headers: { 'Cache-Control': 'no-cache' }
    });
  },
};

