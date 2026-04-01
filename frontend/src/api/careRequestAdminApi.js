import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/admin/care-requests');

export const careRequestAdminApi = {
    listCareRequests: (params) => api.get('', { params }),
    getCareRequest: (id) => api.get(`/${id}`),
    updateStatus: (id, status) => api.patch(`/${id}/status`, null, { params: { status } }),
    deleteCareRequest: (id) => api.post(`/${id}/delete`),
    restoreCareRequest: (id) => api.post(`/${id}/restore`),
};

