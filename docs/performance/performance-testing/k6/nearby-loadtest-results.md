# 모임 반경조회(nearby) k6 부하테스트 — before/after 실측 (소규모 + 대용량)

> 측정일: 2026-07-11
> 대상: `GET /api/meetups/nearby`
> 스크립트: [`nearby-loadtest.js`](./nearby-loadtest.js)
> 환경: 로컬 MySQL 8 + Spring Boot(bootRun), macOS. 단일 머신(부하기·서버 동일 호스트).

---

## 1. 무엇을 before/after로 비교했나

meetup 리팩토링(`docs/refactoring/meetup/nearby-meetups/`)의 실체는 **알고리즘 교체**다:

- **Before**: `findAllNotDeleted()`로 미삭제 모임 **전체**를 메모리에 로드 → Java Haversine 거리 계산(전건) → Stream 필터·정렬. O(n) 메모리.
- **After**: DB 바운딩박스 네이티브 쿼리(`findNearbyMeetupIds`, `ST_Within`+`ST_Distance_Sphere`)로 후보 ID만 뽑고 → ID로 필요한 것만 fetch → distance 세팅.

두 재료(`findAllNotDeleted`, `calculateDistanceMeters`)가 현재 코드에 남아 있어, **임시 legacy 엔드포인트**(`/api/meetups/nearby-legacy`)로 before를 재현하고 **같은 데이터**에 apples-to-apples로 측정. 두 엔드포인트 결과셋 동일 확인(같은 좌표에서 both `count` 일치).

---

## 2. 소규모 데이터 (미삭제 708건, 20 VU)

| 지표 | BEFORE (in-memory) | AFTER (DB 쿼리) | 개선 |
|---|---|---|---|
| 처리량 | 109.4 req/s | **127.8 req/s** | +16.8% |
| 평균 지연 | 44.1ms | 24.0ms | -45.6% |
| **p95 지연** | **78.0ms** | **37.4ms** | **-52.1%** |
| 최대 지연 | 340.3ms | 59.9ms | -82.4% |
| 실패율 | 0.00% | 0.00% | — |

이 규모에선 개선이 "있지만 modest"(-52%). 그리고 아래 EXPLAIN이 보여주듯 이 개선은 **spatial 인덱스가 아니라 전건 로드 제거**에서 온다.

## 3. 대용량 데이터 (미삭제 50,708건, 5 VU)

미래 날짜 RECRUITING 모임 50,000건을 벌크 삽입(date 필터를 비선택적으로 만들어 옵티마이저가 spatial 인덱스를 선택하도록). legacy가 매 요청 전건 로드라 OOM 방지 위해 5 VU로 측정.

| 지표 | BEFORE (in-memory) | AFTER (DB+spatial) | 배율 |
|---|---|---|---|
| 처리량 | 2.11 req/s | **26.7 req/s** | **~12.6x** |
| 평균 지연 | 1.61s | 35.1ms | ~46x |
| **p95 지연** | **1.75s** | **57.5ms** | **~30x** |
| 최대 지연 | 2.59s | 131.8ms | ~20x |
| 단건 응답 | 2.65s | 0.024s | ~110x |
| 실패율 | 0.00% | 0.00% | — |

→ **소규모 -52%였던 격차가 대용량에선 p95 기준 ~30배로 폭증.** legacy는 결과가 28건이든 매 요청 50,708건을 로드+Haversine하므로 데이터에 정비례해 악화된다. 튜닝본은 DB가 인덱스로 좁혀 거의 일정하게 유지.

## 4. EXPLAIN — 데이터 규모에 따라 인덱스 선택이 바뀐다

동일한 nearby 네이티브 쿼리, 데이터 규모만 다름:

| 규모 | 선택된 인덱스 | 비용 | 의미 |
|---|---|---|---|
| 708건 | `idx_meetup_date` (날짜) | cost=7.01, rows=15 | `date > NOW()`가 선택적 → 날짜로 좁힘. spatial 인덱스 **미사용** |
| 50,708건 | **`idx_meetup_geo_point_spatial`** (공간) | cost=210, rows=466 | 미래 모임이 5만+라 날짜 필터 무의미 → 옵티마이저가 **spatial R-tree 인덱스로 전환** |

```
-- 대용량 EXPLAIN (핵심 줄)
-> Index range scan on m using idx_meetup_geo_point_spatial
     over (geo_point ...)  (cost=210 rows=466)
```

### 정직한 해석 (면접 포지셔닝)

- **소규모**: nearby 개선의 본질은 spatial 인덱스가 아니라 **전건 로드 제거**(in-memory → DB). 이 볼륨에선 옵티마이저가 date 인덱스를 쓴다.
- **대용량**: 데이터가 커지고 날짜 선택도가 낮아지면 옵티마이저가 **spatial 인덱스로 전환**하고, in-memory 방식과의 격차가 ~30배로 벌어진다.
- 즉 "spatial 인덱스로 N배 빨라졌다"고 뭉뚱그리지 않고, **"인덱스 선택은 데이터 분포에 의존한다. 소규모에선 date 인덱스가 선택되고 개선의 본질은 전건 로드 제거이며, 대용량에선 spatial 인덱스가 선택되어 격차가 폭증한다"**까지 EXPLAIN 근거로 말할 수 있는 것이 핵심.

## 5. 측정 데이터/코드 정리

- 대용량 더미(50,000건)는 `description='__LOADTEST_DUMMY__'` 태그. 측정 후 `DELETE FROM meetup WHERE description='__LOADTEST_DUMMY__'`로 정리.
- `/api/meetups/nearby-legacy`와 `MeetupService.getNearbyMeetupsLegacy`는 측정 전용 임시 코드. 측정 후 제거.
- 참고: `docs/refactoring/meetup/nearby-meetups/performance-comparison.md`에 이전(다른 볼륨) 측정치(486ms→273ms, 메모리 1.48MB→0.21MB, 스캔행 2958→117)도 있음.

## 6. 재현 방법

```bash
./gradlew bootRun
# 테스트 유저 1회 생성 (dev 프로파일=이메일인증 스킵)
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"id":"loadtest","password":"Loadtest1234!","username":"loadtest","nickname":"부하테스트유저","email":"loadtest@petory.local","role":"USER"}'

# 소규모: 20 VU
k6 run -e NEARBY_PATH=/api/meetups/nearby        docs/performance/performance-testing/k6/nearby-loadtest.js
k6 run -e NEARBY_PATH=/api/meetups/nearby-legacy docs/performance/performance-testing/k6/nearby-loadtest.js

# 대용량: 50k 삽입 후 5 VU (legacy OOM 방지)
k6 run -e NEARBY_PATH=/api/meetups/nearby        -e VUS=5 -e DURATION=15s docs/performance/performance-testing/k6/nearby-loadtest.js
k6 run -e NEARBY_PATH=/api/meetups/nearby-legacy -e VUS=5 -e DURATION=15s docs/performance/performance-testing/k6/nearby-loadtest.js
```
