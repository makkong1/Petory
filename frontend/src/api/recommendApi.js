import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api');

export const recommendApi = {
  getRecommendation: ({ lat, lng, context }) =>
    api.get('/recommend', { params: { lat, lng, context } }),
};
