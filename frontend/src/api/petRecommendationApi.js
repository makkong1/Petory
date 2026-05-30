import { createAuthAxios } from './apiClient';
import { getToken } from './tokenStorage';

const api = createAuthAxios('http://localhost:8080/api/pet-recommend');

export const petRecommendationApi = {
  getSignals: async () => {
    if (!getToken()) return [];
    try {
      const res = await api.get('/signals');
      return Array.isArray(res.data) ? res.data : [];
    } catch (error) {
      return [];
    }
  },
};
