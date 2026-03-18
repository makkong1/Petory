import axios from 'axios';
import { isDemoMode } from '../mock/isDemoMode';
import { DEMO_MEETUPS } from '../mock/demoData';

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

const mockResolve = (data) => Promise.resolve({ data });

export const meetupApi = {
  // 반경 기반 모임 조회 (마커 표시용)
  getNearbyMeetups: (lat, lng, radius = 5) => {
    if (isDemoMode()) {
      return mockResolve(DEMO_MEETUPS);
    }
    return api.get('/nearby', {
      params: { lat, lng, radius },
    });
  },

  // 특정 모임 조회
  getMeetupById: (meetupIdx) => {
    if (isDemoMode()) {
      const meetup = DEMO_MEETUPS.find((m) => m.idx === Number(meetupIdx));
      return mockResolve(meetup || DEMO_MEETUPS[0]);
    }
    return api.get(`/${meetupIdx}`);
  },

  // 참가자 목록 조회
  getParticipants: (meetupIdx) =>
    isDemoMode()
      ? mockResolve([{ idx: 1, nickname: '데모유저' }])
      : api.get(`/${meetupIdx}/participants`),

  // 모임 생성
  createMeetup: (meetupData) =>
    isDemoMode() ? mockResolve({ idx: 99, ...meetupData }) : api.post('', meetupData),

  // 모임 수정
  updateMeetup: (meetupIdx, meetupData) =>
    isDemoMode() ? mockResolve({ idx: meetupIdx, ...meetupData }) : api.put(`/${meetupIdx}`, meetupData),

  // 모임 삭제
  deleteMeetup: (meetupIdx) =>
    isDemoMode() ? mockResolve({}) : api.delete(`/${meetupIdx}`),

  // 모임 참가
  joinMeetup: (meetupIdx) =>
    isDemoMode() ? mockResolve({}) : api.post(`/${meetupIdx}/participants`),

  // 모임 참가 취소
  cancelParticipation: (meetupIdx) =>
    isDemoMode() ? mockResolve({}) : api.delete(`/${meetupIdx}/participants`),

  // 참가 여부 확인
  checkParticipation: (meetupIdx) =>
    isDemoMode() ? mockResolve({ participating: false }) : api.get(`/${meetupIdx}/participants/check`),
};

