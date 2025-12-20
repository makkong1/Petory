import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/admin/missing-pets';

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

export const missingPetAdminApi = {
    listMissingPets: (params) => api.get('', { params }),
    getMissingPet: (id) => api.get(`/${id}`),
    updateStatus: (id, status) => api.patch(`/${id}/status`, { status }),
    deleteMissingPet: (id) => api.post(`/${id}/delete`),
    restoreMissingPet: (id) => api.post(`/${id}/restore`),
    listComments: (boardId, params) => api.get(`/${boardId}/comments`, { params }),
    deleteComment: (boardId, commentId) => api.post(`/${boardId}/comments/${commentId}/delete`),
};

