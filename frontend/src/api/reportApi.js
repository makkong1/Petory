import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/reports';

const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const reportApi = {
  submit: (payload) => api.post('', payload),
  /**
   * 신고 목록 조회 (관리자용)
   * - targetType: 'BOARD' | 'COMMENT' | 'MISSING_PET' | 'PET_CARE_PROVIDER'
   * - status: 'PENDING' | 'RESOLVED' | 'REJECTED'
   */
  getReports: ({ targetType, status } = {}) => {
    const params = {};
    if (targetType) params.targetType = targetType;
    if (status && status !== 'ALL') params.status = status;
    return api.get('', { params });
  },
  getDetail: (id) => api.get(`/${id}`),
  handle: (id, { status, actionTaken, adminNote }) =>
    api.post(`/${id}/handle`, { status, actionTaken, adminNote }),
};

