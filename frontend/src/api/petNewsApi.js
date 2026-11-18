import { API_ROOT, createAuthAxios } from './apiClient';

const api = createAuthAxios(`${API_ROOT}/pet-news`);

export const petNewsApi = {
  // 소식 1건. 알림에는 relatedId 만 있어서 원문 주소를 여기서 읽는다.
  // 목록 API 는 없다 — 전용 화면 없이 알림·배너로만 보여준다.
  get: (idx) => api.get(`/${idx}`),
};
