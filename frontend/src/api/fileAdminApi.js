import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/admin/files');

export const fileAdminApi = {
    listFiles: (params) => api.get('', { params }),
    getFilesByTarget: (targetType, targetIdx) => api.get('/target', { params: { targetType, targetIdx } }),
    deleteFile: (id) => api.delete(`/${id}`),
    deleteFilesByTarget: (targetType, targetIdx) => api.delete('/target', { params: { targetType, targetIdx } }),
    getStatistics: () => api.get('/statistics'),
};

