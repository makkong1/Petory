import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/admin');

export const adminApi = {
  // 일별 통계 조회
  fetchDailyStatistics: async (startDate, endDate) => {
    const params = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    
    const response = await api.get('/statistics/daily', { params });
    return response.data;
  },

  // 통계 수동 집계 (MASTER 전용)
  initStatistics: async (days = 30) => {
    const endDate = new Date().toISOString().split('T')[0];
    const startDate = new Date(Date.now() - (days - 1) * 86400000).toISOString().split('T')[0];
    const response = await api.post(`/statistics/backfill?startDate=${startDate}&endDate=${endDate}`);
    return response.data;
  },

};
