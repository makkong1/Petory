import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/missing-pets';

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const missingPetApi = {
  list: (params) => api.get('/', { params }),
  get: (id) => api.get(`/${id}`),
  create: (payload) => api.post('/', payload),
  update: (id, payload) => api.put(`/${id}`, payload),
  updateStatus: (id, status) => api.patch(`/${id}/status`, { status }),
  delete: (id) => api.delete(`/${id}`),
  getComments: (id) => api.get(`/${id}/comments`),
  addComment: (id, payload) => api.post(`/${id}/comments`, payload),
  deleteComment: (boardId, commentId) => api.delete(`/${boardId}/comments/${commentId}`),
};

