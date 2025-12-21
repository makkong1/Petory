import axios from 'axios';

// 일반 사용자용 신고 생성 API
const BASE_URL = 'http://localhost:8080/api/reports';

// 관리자용 신고 관리 API
const ADMIN_BASE_URL = 'http://localhost:8080/api/admin/reports';

const getToken = () => {
  return localStorage.getItem('accessToken') || localStorage.getItem('token');
};

// 일반 사용자용 API (신고 생성만)
const api = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 관리자용 API (신고 조회, 상세, 처리)
const adminApi = axios.create({
  baseURL: ADMIN_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const addAuthToken = (config) => {
  const token = getToken();
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
};

api.interceptors.request.use(addAuthToken, (error) => Promise.reject(error));
adminApi.interceptors.request.use(addAuthToken, (error) => Promise.reject(error));

export const reportApi = {
  // 일반 사용자용: 신고 생성
  submit: (payload) => api.post('', payload),
  
  // 관리자용: 신고 목록 조회
  /**
   * 신고 목록 조회 (관리자용)
   * - targetType: 'BOARD' | 'COMMENT' | 'MISSING_PET' | 'PET_CARE_PROVIDER'
   * - status: 'PENDING' | 'RESOLVED' | 'REJECTED'
   */
  getReports: ({ targetType, status } = {}) => {
    const params = {};
    if (targetType) params.targetType = targetType;
    if (status && status !== 'ALL') params.status = status;
    return adminApi.get('', { params });
  },
  
  // 관리자용: 신고 상세 조회
  getDetail: (id) => adminApi.get(`/${id}`),
  
  // 관리자용: 신고 처리
  handle: (id, { status, actionTaken, adminNote }) =>
    adminApi.post(`/${id}/handle`, { status, actionTaken, adminNote }),
};

