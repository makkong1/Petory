import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/uploads');

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

