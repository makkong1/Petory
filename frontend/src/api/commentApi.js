import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';

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

