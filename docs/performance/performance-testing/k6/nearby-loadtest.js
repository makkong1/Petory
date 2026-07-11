// 모임 반경조회(GET /api/meetups/nearby) 부하테스트
//
// 실행:
//   k6 run docs/performance/performance-testing/k6/nearby-loadtest.js
// 환경변수(선택):
//   BASE_URL   기본 http://localhost:8080
//   LOGIN_ID   기본 loadtest
//   LOGIN_PW   기본 Loadtest1234!
//
// setup()에서 1회 로그인해 accessToken을 받고, 각 VU가 서울 주변 좌표를 랜덤으로
// 뿌리며 nearby 엔드포인트를 호출한다. 결과(TPS·p95/p99)는 종단 성능 근거로 기록.

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'loadtest';
const LOGIN_PW = __ENV.LOGIN_PW || 'Loadtest1234!';
// before/after 비교: /api/meetups/nearby(튜닝후) vs /api/meetups/nearby-legacy(in-memory)
const NEARBY_PATH = __ENV.NEARBY_PATH || '/api/meetups/nearby';

// VUS/DURATION은 대용량 legacy(전건 로드) 실험 시 OOM 방지를 위해 낮출 수 있게 ENV로 노출.
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    nearby: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: VUS },     // 램프업
        { duration: DURATION, target: VUS },   // 정상 부하 유지
        { duration: '5s', target: 0 },         // 램프다운
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    // 임계 초과해도 요약은 나오도록 abortOnFail 미설정
    'http_req_duration{endpoint:nearby}': ['p(95)<500', 'p(99)<1000'],
  },
};

export function setup() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ id: LOGIN_ID, password: LOGIN_PW }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  const token = res.json('accessToken');
  if (!token) {
    throw new Error(`로그인 실패, 토큰 없음: status=${res.status} body=${res.body}`);
  }
  return { token };
}

export default function (data) {
  // 더미데이터 미삭제 모임의 ~98%가 (37.6, 127.0) 밀집. 결과가 일관되게 나오도록
  // 이 지점 중심으로 소폭(±0.03) 산포해 거리필터 스캔을 실제로 태운다.
  const lat = 37.6 + (Math.random() - 0.5) * 0.06; // 37.57 ~ 37.63
  const lng = 127.0 + (Math.random() - 0.5) * 0.06; // 126.97 ~ 127.03
  const url = `${BASE}${NEARBY_PATH}?lat=${lat.toFixed(5)}&lng=${lng.toFixed(5)}&radius=5.0&maxResults=500`;

  const res = http.get(url, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { endpoint: 'nearby' },
  });
  check(res, { 'nearby 200': (r) => r.status === 200 });
  sleep(0.1);
}
