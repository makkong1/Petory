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

export const commentApi = {
  list: (boardId) => api.get(`/${boardId}/comments`),
  create: (boardId, payload) => api.post(`/${boardId}/comments`, payload),
  delete: (boardId, commentId) => api.delete(`/${boardId}/comments/${commentId}`),
};

