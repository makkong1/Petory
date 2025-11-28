import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/boards';

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

export const boardApi = {
  // 전체 게시글 조회 (페이징 지원)
  getAllBoards: (params = {}) => {
    const { page = 0, size = 20, ...otherParams } = params;
    const requestParams = {
      page,
      size,
      ...otherParams,
      _t: Date.now()
    }; // 캐시 무시를 위한 타임스탬프 추가
    return api.get('', {
      params: requestParams,
      headers: { 'Cache-Control': 'no-cache' }
    });
  },

  // 단일 게시글 조회 (옵션 viewerId)
  getBoard: (id, viewerId) => {
    const params = {};
    if (viewerId) {
      params.viewerId = viewerId;
    }
    return api.get(`/${id}`, { params });
  },

  // 인기 자랑 게시글 조회
  getPopularBoards: (period = 'WEEKLY') => {
    const normalized = (period || 'WEEKLY').toUpperCase();
    return api.get('/popular', { params: { period: normalized } });
  },

  // 게시글 생성
  createBoard: (data) => api.post('', data),

  // 게시글 수정
  updateBoard: (id, data) => api.put(`/${id}`, data),

  // 게시글 삭제
  deleteBoard: (id) => api.delete(`/${id}`),

  // 내 게시글 조회
  getMyBoards: (userId) => api.get('/my-posts', { params: { userId } }),

  // 게시글 검색 (페이징 지원)
  searchBoards: (keyword, searchType = 'TITLE_CONTENT', page = 0, size = 20) => api.get('/search', {
    params: { keyword, searchType, page, size }
  }),

  // 게시글 좋아요/싫어요 반응
  reactToBoard: (boardId, data) => api.post(`/${boardId}/reactions`, data),

  // 댓글 좋아요/싫어요 반응
  reactToComment: (boardId, commentId, data) => api.post(`/${boardId}/comments/${commentId}/reactions`, data),
};
