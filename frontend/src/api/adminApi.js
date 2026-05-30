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
    const endDate = new Date().toISOString().split('T')[0];
    const startDate = new Date(Date.now() - (days - 1) * 86400000).toISOString().split('T')[0];
    const response = await api.post(`/statistics/backfill?startDate=${startDate}&endDate=${endDate}`);
    return response.data;
  },

  // pet-data-api 시설 데이터 수동 동기화 (서버 설정 경로에서 읽기)
  syncFacilitiesFromPetDataApi: async () => {
    const response = await api.post('/location/sync');
    return response.data;
  },

  // JSON 파일 미리보기 (마지막 수정일 + 레코드 목록)
  getJsonPreview: async (filename = null) => {
    const response = await api.get('/location/json-preview', {
      params: filename ? { filename } : undefined,
    });
    return response.data;
  },

  // 수집 디렉토리의 파일 목록 조회
  getImportFiles: async () => {
    const response = await api.get('/location/import-files');
    return response.data;
  },

  // 특정 파일명으로 동기화
  syncFromFile: async (filename) => {
    const response = await api.post(`/location/sync-file?filename=${encodeURIComponent(filename)}`);
    return response.data;
  },

  // Python 수집 프로세스 시작
  startCollect: async () => {
    const response = await api.post('/location/collect');
    return response.data;
  },

  // 수집 상태 조회
  getCollectStatus: async () => {
    const response = await api.get('/location/collect-status');
    return response.data;
  },
};
