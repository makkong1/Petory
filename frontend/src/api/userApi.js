import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_USERS } from '../mock/demoData';

const BASE_URL = 'http://localhost:8080/api/admin/users';

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

const mockResolve = (data) => Promise.resolve({ data });

export const userApi = {
  // 전체 유저 조회 (페이징 지원)
  getAllUsersWithPaging: (params = {}) => {
    if (isDemoMode()) {
      const { page = 0, size = 20 } = params;
      const start = page * size;
      const users = DEMO_USERS.slice(start, start + size);
      return mockResolve({
        users,
        totalCount: DEMO_USERS.length,
        hasNext: start + users.length < DEMO_USERS.length,
      });
    }
    const { page = 0, size = 20, ...otherParams } = params;
    const requestParams = {
      page,
      size,
      ...otherParams,
      _t: Date.now()
    };
    return api.get('/paging', {
      params: requestParams,
      headers: { 'Cache-Control': 'no-cache' }
    });
  },

  // 단일 유저 조회
  getUser: (id) => {
    if (isDemoMode()) {
      const user = DEMO_USERS.find((u) => u.idx === Number(id));
      return mockResolve(user || DEMO_USERS[0]);
    }
    return api.get(`/${id}`);
  },

  // 유저 생성
  createUser: (userData) =>
    isDemoMode() ? mockResolve({ idx: 99, ...userData }) : api.post('', userData),

  // 유저 수정
  updateUser: (id, userData) =>
    isDemoMode() ? mockResolve({ idx: id, ...userData }) : api.put(`/${id}`, userData),

  // 유저 삭제 (소프트 삭제)
  deleteUser: (id) => (isDemoMode() ? mockResolve({}) : api.delete(`/${id}`)),

  // 계정 복구
  restoreUser: (id) => (isDemoMode() ? mockResolve({}) : api.post(`/${id}/restore`)),

  // 상태 관리 (상태, 경고 횟수, 정지 기간만 업데이트)
  updateUserStatus: (id, userData) =>
    isDemoMode() ? mockResolve({}) : api.patch(`/${id}/status`, userData),
};

// MASTER 전용: ADMIN 계정 관리 API
const masterApi = axios.create({
  baseURL: 'http://localhost:8080/api/master/admin-users',
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 - 모든 요청에 토큰 자동 추가
masterApi.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const adminUserApi = {
  // 일반 사용자를 ADMIN으로 승격
  promoteToAdmin: (id) => masterApi.patch(`/${id}/promote-to-admin`),
};

// 일반 사용자용 프로필 API
const profileApi = axios.create({
  baseURL: 'http://localhost:8080/api/users',
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 - 모든 요청에 토큰 자동 추가
profileApi.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const userProfileApi = {
  // 자신의 프로필 조회
  getMyProfile: () => profileApi.get('/me'),

  // 자신의 프로필 수정 (닉네임, 이메일, 전화번호, 위치, 펫 정보 등)
  updateMyProfile: (userData) => profileApi.put('/me', userData),

  // 비밀번호 변경
  changePassword: (currentPassword, newPassword) =>
    profileApi.patch('/me/password', { currentPassword, newPassword }),

  // 닉네임 변경
  updateMyUsername: (username) =>
    profileApi.patch('/me/username', { username }),

  // 닉네임 설정 (소셜 로그인 사용자용)
  setNickname: (nickname) =>
    profileApi.post('/me/nickname', { nickname }),

  // 닉네임 중복 검사
  checkNicknameAvailability: (nickname) =>
    profileApi.get('/nickname/check', { params: { nickname } }),

  // 아이디 중복 검사
  checkIdAvailability: (id) =>
    profileApi.get('/id/check', { params: { id } }),

  // 다른 사용자의 프로필 조회 (리뷰 포함)
  getUserProfile: (userId) => profileApi.get(`/${userId}/profile`),

  // 특정 사용자의 리뷰 목록 조회
  getUserReviews: (userId) => profileApi.get(`/${userId}/reviews`),

  // 이메일 인증 메일 발송
  sendVerificationEmail: (purpose) =>
    profileApi.post('/email/verify', { purpose }),

  // 이메일 인증 처리
  verifyEmail: (token) =>
    profileApi.get(`/email/verify/${token}`),

  // 회원가입 전 이메일 인증 메일 발송 (인증 불필요)
  sendPreRegistrationVerificationEmail: (email) =>
    profileApi.post('/email/verify/pre-registration', { email }),

  // 회원가입 전 이메일 인증 완료 여부 확인 (인증 불필요)
  checkPreRegistrationVerification: (email) =>
    profileApi.get('/email/verify/pre-registration/check', { params: { email } }),
};

// 펫 관리 API
const petApi = axios.create({
  baseURL: 'http://localhost:8080/api/pets',
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 - 모든 요청에 토큰 자동 추가
petApi.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const petApiClient = {
  // 자신의 펫 목록 조회
  getMyPets: () => petApi.get(''),

  // 펫 상세 조회
  getPet: (petIdx) => petApi.get(`/${petIdx}`),

  // 펫 생성
  createPet: (petData) => petApi.post('', petData),

  // 펫 수정
  updatePet: (petIdx, petData) => petApi.put(`/${petIdx}`, petData),

  // 펫 삭제
  deletePet: (petIdx) => petApi.delete(`/${petIdx}`),

  // 펫 복구
  restorePet: (petIdx) => petApi.post(`/${petIdx}/restore`),
};