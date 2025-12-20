import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/admin/care-requests';

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

export const careRequestAdminApi = {
    listCareRequests: (params) => api.get('', { params }),
    getCareRequest: (id) => api.get(`/${id}`),
    updateStatus: (id, status) => api.patch(`/${id}/status`, null, { params: { status } }),
    deleteCareRequest: (id) => api.post(`/${id}/delete`),
    restoreCareRequest: (id) => api.post(`/${id}/restore`),
};

