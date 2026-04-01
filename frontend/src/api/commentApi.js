import { createAuthAxios } from './apiClient';
import { isDemoMode } from '../mock/isDemoMode';

const api = createAuthAxios('http://localhost:8080/api/boards');

const mockResolve = (data) => Promise.resolve({ data });

export const commentApi = {
  list: (boardId, page = 0, size = 20) => {
    if (isDemoMode()) {
      return mockResolve({
        comments: [],
        totalCount: 0,
        hasNext: false,
      });
    }
    return api.get(`/${boardId}/comments`, { params: { page, size } });
  },
  create: (boardId, payload) =>
    isDemoMode() ? mockResolve({ idx: 1, ...payload }) : api.post(`/${boardId}/comments`, payload),
  delete: (boardId, commentId) =>
    isDemoMode() ? mockResolve({}) : api.delete(`/${boardId}/comments/${commentId}`),
};

