import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/admin/missing-pets');

export const missingPetAdminApi = {
    // [리팩토링] DB 레벨 필터링 + 페이징 (기존 listMissingPets 전체 메모리 로드 제거)
    listMissingPetsWithPaging: (params = {}) => {
        const { page = 0, size = 20, ...otherParams } = params;
        return api.get('/paging', { params: { page, size, ...otherParams } });
    },
    getMissingPet: (id) => api.get(`/${id}`),
    updateStatus: (id, status) => api.patch(`/${id}/status`, { status }),
    deleteMissingPet: (id) => api.post(`/${id}/delete`),
    restoreMissingPet: (id) => api.post(`/${id}/restore`),
    listComments: (boardId, params) => api.get(`/${boardId}/comments`, { params }),
    deleteComment: (boardId, commentId) => api.post(`/${boardId}/comments/${commentId}/delete`),
};

