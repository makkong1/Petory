import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/admin/files';

const getToken = () => localStorage.getItem('accessToken') || localStorage.getItem('token');

const api = axios.create({
    baseURL: BASE_URL,
    headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export const fileAdminApi = {
    listFiles: (params) => api.get('', { params }),
    getFilesByTarget: (targetType, targetIdx) => api.get('/target', { params: { targetType, targetIdx } }),
    deleteFile: (id) => api.delete(`/${id}`),
    deleteFilesByTarget: (targetType, targetIdx) => api.delete('/target', { params: { targetType, targetIdx } }),
    getStatistics: () => api.get('/statistics'),
};

