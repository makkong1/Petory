import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/admin';

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

export const communityAdminApi = {
    // Boards (기존 API - 하위 호환성 유지)
    listBoards: (params) => api.get('/boards', { params }),
    
    // Boards (페이징 지원)
    listBoardsWithPaging: (params = {}) => {
        const { page = 0, size = 20, ...otherParams } = params;
        const requestParams = {
            page,
            size,
            ...otherParams,
            _t: Date.now()
        };
        return api.get('/boards/paging', {
            params: requestParams,
            headers: { 'Cache-Control': 'no-cache' }
        });
    },
    
    blindBoard: (id, body) => api.patch(`/boards/${id}/blind`, body || {}),
    unblindBoard: (id, body) => api.patch(`/boards/${id}/unblind`, body || {}),
    deleteBoard: (id, body) => api.post(`/boards/${id}/delete`, body || {}),
    restoreBoard: (id, body) => api.post(`/boards/${id}/restore`, body || {}),

    // Comments
    listComments: (boardId, params) => api.get(`/boards/${boardId}/comments`, { params }),
    blindComment: (boardId, commentId, body) => api.patch(`/boards/${boardId}/comments/${commentId}/blind`, body || {}),
    unblindComment: (boardId, commentId, body) => api.patch(`/boards/${boardId}/comments/${commentId}/unblind`, body || {}),
    deleteComment: (boardId, commentId, body) => api.post(`/boards/${boardId}/comments/${commentId}/delete`, body || {}),
    restoreComment: (boardId, commentId, body) => api.post(`/boards/${boardId}/comments/${commentId}/restore`, body || {}),
};


