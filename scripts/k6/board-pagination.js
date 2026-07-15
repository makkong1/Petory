import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8081';
const TOKEN = __ENV.TOKEN;

export const options = { vus: 20, duration: '30s' };

export default function () {
  const params = { headers: { Authorization: `Bearer ${TOKEN}` } };
  // 얕은 / 깊은 / 맨뒤 를 섞어 친다
  const pages = [0, 1000, 2000, 2499];
  const p = pages[Math.floor(Math.random() * pages.length)];
  const res = http.get(`${BASE}/api/boards?page=${p}&size=20`, params);
  check(res, { '200': (r) => r.status === 200 });
}
