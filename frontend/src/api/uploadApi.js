import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/uploads';

const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
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

export const uploadApi = {
  uploadImage: async (file, options = {}) => {
    const formData = new FormData();
    formData.append('file', file);

    const params = {};
    const { category, ownerType, ownerId, entityId } = options;

    if (category) {
      params.category = category;
    }
    if (ownerType) {
      params.ownerType = ownerType;
    }
    if (ownerId !== undefined && ownerId !== null) {
      params.ownerId = ownerId;
    }
    if (entityId !== undefined && entityId !== null) {
      params.entityId = entityId;
    }

    const response = await api.post('/images', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      params,
    });

    return response.data;
  },
};

