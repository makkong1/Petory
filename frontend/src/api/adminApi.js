import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/admin');

export const adminApi = {
  // 초기 데이터 로딩
  loadInitialData: async (region = '서울특별시', maxResultsPerKeyword = 10, customKeywords = null) => {
    let url = `/location-services/load-data?region=${encodeURIComponent(region)}&maxResultsPerKeyword=${maxResultsPerKeyword}`;
    if (customKeywords) {
      url += `&customKeywords=${encodeURIComponent(customKeywords)}`;
    }
    const response = await api.post(url);
    return response.data;
  },

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
    const response = await api.post(`/statistics/init?days=${days}`);
    return response.data;
  },
};

