import { createAuthAxios } from './apiClient';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_BOARDS, DEMO_POPULAR_BOARDS } from '../mock/demoData';

const api = createAuthAxios('http://localhost:8080/api/boards');

const mockResolve = (data) => Promise.resolve({ data });

export const boardApi = {
  // 전체 게시글 조회 (페이징 지원)
  getAllBoards: (params = {}) => {
    if (isDemoMode()) {
      const { page = 0, size = 20, category } = params;
      let filtered = [...DEMO_BOARDS];
      if (category) {
        filtered = filtered.filter((b) => b.category === category);
      }
      const start = page * size;
      const boards = filtered.slice(start, start + size);
      return mockResolve({
        boards,
        totalCount: filtered.length,
        hasNext: start + boards.length < filtered.length,
      });
    }
    const { page = 0, size = 20, ...otherParams } = params;
    const requestParams = {
      page,
      size,
      ...otherParams,
      _t: Date.now()
    };
    return api.get('', {
      params: requestParams,
      headers: { 'Cache-Control': 'no-cache' }
    });
  },

  // 단일 게시글 조회 (옵션 viewerId)
  getBoard: (id, viewerId) => {
    if (isDemoMode()) {
      const board = DEMO_BOARDS.find((b) => b.idx === Number(id));
      return mockResolve(board || DEMO_BOARDS[0]);
    }
    const params = viewerId ? { viewerId } : {};
    return api.get(`/${id}`, { params });
  },

  // 인기 자랑 게시글 조회
  getPopularBoards: (period = 'WEEKLY') => {
    if (isDemoMode()) {
      return mockResolve(DEMO_POPULAR_BOARDS);
    }
    const normalized = (period || 'WEEKLY').toUpperCase();
    return api.get('/popular', { params: { period: normalized } });
  },

  // 게시글 생성
  createBoard: (data) => isDemoMode() ? mockResolve({ idx: 99, ...data }) : api.post('', data),

  // 게시글 수정
  updateBoard: (id, data) => isDemoMode() ? mockResolve({ idx: id, ...data }) : api.put(`/${id}`, data),

  // 게시글 삭제
  deleteBoard: (id) => isDemoMode() ? mockResolve({}) : api.delete(`/${id}`),

  // 내 게시글 조회
  getMyBoards: (userId) =>
    isDemoMode()
      ? mockResolve({ boards: DEMO_BOARDS.filter((b) => b.userId === 1), totalCount: 2 })
      : api.get('/my-posts', { params: { userId } }),

  // 게시글 검색 (페이징 지원)
  searchBoards: (keyword, searchType = 'TITLE_CONTENT', page = 0, size = 20) => {
    if (isDemoMode()) {
      const filtered = keyword
        ? DEMO_BOARDS.filter(
            (b) =>
              (b.title && b.title.includes(keyword)) || (b.content && b.content.includes(keyword))
          )
        : DEMO_BOARDS;
      const start = page * size;
      const boards = filtered.slice(start, start + size);
      return mockResolve({
        boards,
        totalCount: filtered.length,
        hasNext: start + boards.length < filtered.length,
      });
    }
    return api.get('/search', { params: { keyword, searchType, page, size } });
  },

  // 게시글 좋아요/싫어요 반응
  reactToBoard: (boardId, data) =>
    isDemoMode() ? mockResolve({}) : api.post(`/${boardId}/reactions`, data),

  // 댓글 좋아요/싫어요 반응
  reactToComment: (boardId, commentId, data) =>
    isDemoMode()
      ? mockResolve({})
      : api.post(`/${boardId}/comments/${commentId}/reactions`, data),
};
