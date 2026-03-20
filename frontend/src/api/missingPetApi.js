import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_MISSING_PETS } from '../mock/demoData';

const BASE_URL = 'http://localhost:8080/api/missing-pets';

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

export const missingPetApi = {
  list: (params) => {
    if (isDemoMode()) {
      const { page = 0, size = 20, status } = params || {};
      let filtered = [...DEMO_MISSING_PETS];
      if (status && status !== 'ALL') {
        filtered = filtered.filter((m) => m.status === status);
      }
      const start = page * size;
      const boards = filtered.slice(start, start + size);
      return mockResolve({
        boards,
        totalCount: filtered.length,
        hasNext: start + boards.length < filtered.length,
      });
    }
    return api.get('', { params });
  },
  get: (id) => {
    if (isDemoMode()) {
      const board = DEMO_MISSING_PETS.find((m) => m.idx === Number(id));
      return mockResolve(board || DEMO_MISSING_PETS[0]);
    }
    return api.get(`/${id}`);
  },
  create: (payload) =>
    isDemoMode() ? mockResolve({ idx: 99, ...payload }) : api.post('', payload),
  update: (id, payload) =>
    isDemoMode() ? mockResolve({ idx: id, ...payload }) : api.put(`/${id}`, payload),
  updateStatus: (id, status) =>
    isDemoMode() ? mockResolve({}) : api.patch(`/${id}/status`, { status }),
  delete: (id) => (isDemoMode() ? mockResolve({}) : api.delete(`/${id}`)),
  getComments: (id, page = 0, size = 20) =>
    isDemoMode()
      ? mockResolve({ comments: [], totalCount: 0 })
      : api.get(`/${id}/comments`, { params: { page, size } }),
  addComment: (id, payload) =>
    isDemoMode() ? mockResolve({ idx: 1, ...payload }) : api.post(`/${id}/comments`, payload),
  deleteComment: (boardId, commentId) =>
    isDemoMode()
      ? mockResolve({})
      : api.delete(`/${boardId}/comments/${commentId}`),
  startChat: (boardIdx, witnessId) =>
    isDemoMode()
      ? mockResolve({ idx: 1 })
      : api.post(`/${boardIdx}/start-chat`, null, { params: { witnessId } }),
};

