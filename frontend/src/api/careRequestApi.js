import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/care-requests';

// 토큰을 가져오는 함수
const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 - 모든 요청에 토큰 자동 추가 (전역 인터셉터와 중복되지만 안전을 위해 유지)
api.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터 제거 - 전역 인터셉터가 처리 (setupApiInterceptors)
// 401 에러는 전역 인터셉터에서 refresh token으로 자동 처리됨

export const careRequestApi = {
  // 전체 케어 요청 조회
  getAllCareRequests: async (params = {}) => {
    const startTime = performance.now();
    const startMemory = performance.memory ? performance.memory.usedJSHeapSize : null;
    
    console.log('=== [프론트엔드] 펫케어 전체조회 시작 ===');
    console.log('  - 파라미터:', params);
    
    try {
      const response = await api.get('', { params });
      
      const endTime = performance.now();
      const endMemory = performance.memory ? performance.memory.usedJSHeapSize : null;
      const executionTime = endTime - startTime;
      const memoryUsed = endMemory && startMemory ? endMemory - startMemory : null;
      
      console.log('=== [프론트엔드] 펫케어 전체조회 완료 ===');
      console.log(`  - 실행 시간: ${executionTime.toFixed(2)}ms (${(executionTime / 1000).toFixed(2)}초)`);
      if (memoryUsed !== null) {
        console.log(`  - 메모리 사용량: ${(memoryUsed / 1024 / 1024).toFixed(2)}MB (${(memoryUsed / 1024).toFixed(2)}KB)`);
      }
      console.log(`  - 조회된 데이터 수: ${response.data?.length || 0}개`);
      if (performance.memory) {
        console.log(`  - 현재 메모리 상태 - Used: ${(performance.memory.usedJSHeapSize / 1024 / 1024).toFixed(2)}MB, Total: ${(performance.memory.totalJSHeapSize / 1024 / 1024).toFixed(2)}MB, Limit: ${(performance.memory.jsHeapSizeLimit / 1024 / 1024).toFixed(2)}MB`);
      }
      
      return response;
    } catch (error) {
      const endTime = performance.now();
      const executionTime = endTime - startTime;
      console.error(`=== [프론트엔드] 펫케어 전체조회 실패 (${executionTime.toFixed(2)}ms) ===`, error);
      throw error;
    }
  },
  
  // 단일 케어 요청 조회
  getCareRequest: (id) => api.get(`/${id}`),
  
  // 케어 요청 생성
  createCareRequest: (data) => api.post('', data),
  
  // 케어 요청 수정
  updateCareRequest: (id, data) => api.put(`/${id}`, data),
  
  // 케어 요청 삭제
  deleteCareRequest: (id) => api.delete(`/${id}`),
  
  // 내 케어 요청 조회
  getMyCareRequests: (userId) => api.get('/my-requests', { params: { userId } }),
  
  // 상태 변경
  updateStatus: (id, status) => api.patch(`/${id}/status`, null, { params: { status } }),

  // 댓글 관련
  getComments: (careRequestId) => api.get(`/${careRequestId}/comments`),
  createComment: (careRequestId, payload) => api.post(`/${careRequestId}/comments`, payload),
  deleteComment: (careRequestId, commentId) => api.delete(`/${careRequestId}/comments/${commentId}`),

  // 검색
  searchCareRequests: async (keyword) => {
    const startTime = performance.now();
    const startMemory = performance.memory ? performance.memory.usedJSHeapSize : null;
    
    console.log('=== [프론트엔드] 펫케어 검색조회 시작 ===');
    console.log('  - 검색어:', keyword);
    
    try {
      const response = await api.get('/search', { params: { keyword } });
      
      const endTime = performance.now();
      const endMemory = performance.memory ? performance.memory.usedJSHeapSize : null;
      const executionTime = endTime - startTime;
      const memoryUsed = endMemory && startMemory ? endMemory - startMemory : null;
      
      console.log('=== [프론트엔드] 펫케어 검색조회 완료 ===');
      console.log(`  - 실행 시간: ${executionTime.toFixed(2)}ms (${(executionTime / 1000).toFixed(2)}초)`);
      if (memoryUsed !== null) {
        console.log(`  - 메모리 사용량: ${(memoryUsed / 1024 / 1024).toFixed(2)}MB (${(memoryUsed / 1024).toFixed(2)}KB)`);
      }
      console.log(`  - 조회된 데이터 수: ${response.data?.length || 0}개`);
      if (performance.memory) {
        console.log(`  - 현재 메모리 상태 - Used: ${(performance.memory.usedJSHeapSize / 1024 / 1024).toFixed(2)}MB, Total: ${(performance.memory.totalJSHeapSize / 1024 / 1024).toFixed(2)}MB, Limit: ${(performance.memory.jsHeapSizeLimit / 1024 / 1024).toFixed(2)}MB`);
      }
      
      return response;
    } catch (error) {
      const endTime = performance.now();
      const executionTime = endTime - startTime;
      console.error(`=== [프론트엔드] 펫케어 검색조회 실패 (${executionTime.toFixed(2)}ms) ===`, error);
      throw error;
    }
  },
};
