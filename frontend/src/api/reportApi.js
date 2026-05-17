import { createAuthAxios } from './apiClient';

const api = createAuthAxios('http://localhost:8080/api/reports');
const adminApi = createAuthAxios('http://localhost:8080/api/admin/reports');

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

