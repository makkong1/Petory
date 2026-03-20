import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_CARE_REQUESTS } from '../mock/demoData';

const BASE_URL = 'http://localhost:8080/api/care-requests';

const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

const mockResolve = (data) => Promise.resolve({ data });

export const careRequestApi = {
  // 전체 케어 요청 조회 (페이징 지원)
  getAllCareRequests: (params = {}) => {
    if (isDemoMode()) {
      const { page = 0, size = 20, status } = params;
      let filtered = [...DEMO_CARE_REQUESTS];
      if (status && status !== 'ALL') {
        filtered = filtered.filter((c) => c.status === status);
      }
      const start = page * size;
      const careRequests = filtered.slice(start, start + size);
      return mockResolve({
        careRequests,
        totalCount: filtered.length,
      });
    }
    const { page = 0, size = 20, ...rest } = params;
    return api.get('', { params: { page, size, ...rest } });
  },

  // 단일 케어 요청 조회
  getCareRequest: (id) => {
    if (isDemoMode()) {
      const cr = DEMO_CARE_REQUESTS.find((c) => c.idx === Number(id));
      return mockResolve(cr || DEMO_CARE_REQUESTS[0]);
    }
    return api.get(`/${id}`);
  },
  
  // 케어 요청 생성
  createCareRequest: (data) =>
    isDemoMode() ? mockResolve({ idx: 99, ...data }) : api.post('', data),

  // 케어 요청 수정
  updateCareRequest: (id, data) =>
    isDemoMode() ? mockResolve({ idx: id, ...data }) : api.put(`/${id}`, data),

  // 케어 요청 삭제
  deleteCareRequest: (id) => (isDemoMode() ? mockResolve({}) : api.delete(`/${id}`)),

  // 내 케어 요청 조회
  getMyCareRequests: (userId) =>
    isDemoMode()
      ? mockResolve({ careRequests: DEMO_CARE_REQUESTS.filter((c) => c.userId === 1), totalCount: 1 })
      : api.get('/my-requests', { params: { userId } }),

  // 상태 변경
  updateStatus: (id, status) =>
    isDemoMode()
      ? mockResolve({})
      : api.patch(`/${id}/status`, null, { params: { status } }),

  // 댓글 관련
  getComments: (careRequestId) =>
    isDemoMode()
      ? mockResolve([])
      : api.get(`/${careRequestId}/comments`),
  createComment: (careRequestId, payload) =>
    isDemoMode()
      ? mockResolve({ idx: 1, ...payload })
      : api.post(`/${careRequestId}/comments`, payload),
  deleteComment: (careRequestId, commentId) =>
    isDemoMode()
      ? mockResolve({})
      : api.delete(`/${careRequestId}/comments/${commentId}`),

  // 검색 (페이징 지원)
  searchCareRequests: (keyword, page = 0, size = 20) =>
    isDemoMode()
      ? mockResolve({
          careRequests: keyword
            ? DEMO_CARE_REQUESTS.filter(
                (c) =>
                  (c.title && c.title.includes(keyword)) ||
                  (c.description && c.description.includes(keyword))
              )
            : DEMO_CARE_REQUESTS,
          totalCount: DEMO_CARE_REQUESTS.length,
        })
      : api.get('/search', { params: { keyword, page, size } }),
};
