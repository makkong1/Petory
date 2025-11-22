import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/meetups';

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

export const meetupApi = {
  // 반경 기반 모임 조회 (마커 표시용)
  getNearbyMeetups: (lat, lng, radius = 5) =>
    api.get('/nearby', {
      params: { lat, lng, radius },
    }),

  // 특정 모임 조회
  getMeetupById: (meetupIdx) => api.get(`/${meetupIdx}`),

  // 참가자 목록 조회
  getParticipants: (meetupIdx) => api.get(`/${meetupIdx}/participants`),

  // 모임 생성
  createMeetup: (meetupData) => api.post('', meetupData),

  // 모임 수정
  updateMeetup: (meetupIdx, meetupData) => api.put(`/${meetupIdx}`, meetupData),

  // 모임 삭제
  deleteMeetup: (meetupIdx) => api.delete(`/${meetupIdx}`),

  // 모임 참가
  joinMeetup: (meetupIdx) => api.post(`/${meetupIdx}/participants`),

  // 모임 참가 취소
  cancelParticipation: (meetupIdx) => api.delete(`/${meetupIdx}/participants`),

  // 참가 여부 확인
  checkParticipation: (meetupIdx) => api.get(`/${meetupIdx}/participants/check`),
};

