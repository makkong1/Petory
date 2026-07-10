# 오버페칭 리팩토링 실측 근거 (2026-07-10)

목록/지도 조회 오버페칭 제거(projection) 리팩토링의 **전/후를 실제로 실행해 비교한 원자료**. 추정치가 아니라 실행 결과다.

## 0. 무엇을·어떻게 쟀나 — "두 구간, 두 낭비" 모델

데이터는 두 구간을 흐르고, 오버페칭은 그 두 구간 중 어디서든 생길 수 있다. 그래서 **측정도 두 레벨**로 했다.

```
[MySQL] ──(구간1: SELECT로 끌어옴)──▶ [Spring 앱/JPA] ──DTO 변환(안 쓰는 필드 버림)──▶ [JSON 응답] ──(구간2)──▶ [클라이언트]
```

| 구간 | 낭비 형태 | 클라이언트까지 가나 | 측정 |
| --- | --- | --- | --- |
| **구간1** (DB→앱) | 쿼리가 안 쓰는 컬럼·연관까지 로딩 | ✗ (DTO에서 버려짐) | **측정 A (DB레벨)** — 끌어와 버리는 바이트. 그 낭비는 HTTP **응답시간**으로도 나타남 |
| **구간2** (앱→클라이언트) | DTO에 안 쓰는 필드가 있어 응답 JSON이 큼 | ✓ | **측정 B (HTTP레벨)** — 응답 바이트 |

---

## 측정 A — DB레벨 (구간1: 서버가 DB에서 끌어오는 폭)

### 방법·조건

- **지표**: `SUM(LENGTH(COALESCE(col,'')))` — 해당 페이지 행들에 대해 각 컬럼 데이터의 바이트 길이 합. "서버가 DB에서 실제로 끌어오는 데이터 폭"을 격리 측정한다(HTTP 직렬화 이전).
- **BEFORE / AFTER**: BEFORE = 기존 쿼리가 로딩하던 컬럼 집합(작성자 27컬럼 전체 등), AFTER = projection이 SELECT 하는 컬럼 집합. 둘 다 **실제로 실행한 결과**.
- **범위**: 목록 첫 페이지 `page=0, size=20`, 각 목록 API의 실제 `WHERE`/`ORDER BY created_at DESC` 반영.
- **DB**: 로컬 MySQL(`petory`) 실데이터(측정 시점 board 10,264 · users 6,775 · carerequest 1,014행).

### 결과 (page 0, size 20 기준)

| 케이스 | 측정 대상 | BEFORE | AFTER | 감소 |
| --- | --- | --- | --- | --- |
| **Board** | 목록 행 페이로드(본문 13컬럼 + 작성자 컬럼) | 7,698 B | 3,386 B | **−56%** |
| **Board** | └ 그중 **작성자 컬럼만** (27→3컬럼) | 5,065 B | 753 B | **−85%** (버려지던 4,312 B) |
| **Users(관리자)** | 사용자 컬럼 (27→12컬럼) | 3,622 B | 2,403 B | **−33.7%** (버려지던 1,219 B) |

- Board의 절감은 전적으로 **작성자 컬럼 축소(27→3)** 에서 온다(본문 `content`는 양쪽 유지). 작성자 조인 부분만 보면 85%가 버려지고 있었고, 그게 행 전체 페이로드의 56% 감소로 이어진다.
- Users는 목록·모달이 쓰는 12컬럼만 남겨 33.7% 감소. (추가로 BEFORE는 `socialUsers` 배치 쿼리 1회를 더 발생 — 측정 B 응답시간에 반영)

### ⚠️ 한계 — 이 DB레벨 수치는 "보수적 하한선"

측정 대상 로컬 데이터는 대용량/토큰 필드가 거의 비어 있어 **프로덕션 오버페칭을 과소평가**한다. 즉 운영에선 절감 폭이 더 크다.

| 필드 | 로컬 실태 | 프로덕션 예상 |
| --- | --- | --- |
| `password` | 평균 약 16자 | bcrypt 60자 |
| `pet_info`(@Lob) | 대부분 빈값 | 수백 바이트 가능 |
| `refresh_token` | 6,775명 중 144명만 | 로그인 세션마다 존재 |
| `profile_image` | 6,775명 중 136명만 | 소셜 로그인 사용자 다수(최대 500자) |

---

## 측정 B — HTTP레벨 (구간1+2: 실제 엔드포인트 응답)

### 실험 설계 (가정 → 통제 → 절차)

- **가정(가설)**: 목록/지도 응답이 화면 미사용 컬럼·연관을 실어 나른다면, projection 후 **응답 바이트** 또는 **응답시간** 중 하나 이상이 줄어야 한다. (응답 DTO 형태를 바꾸는 Users/CareRequest는 바이트+시간, DTO가 불변인 Board는 시간으로 나타날 것으로 예상)
- **통제 변수**: 동일 DB(로컬 populated MySQL) · 동일 JWT · 동일 요청 URL · 동일 포트(8081). **유일한 독립변수 = `git stash`로 토글한 리팩토링 코드.**
- **절차**: ① 리팩토링 전(BEFORE) 상태로 앱 기동 → 3개 엔드포인트 각 15회 측정 → ② `git stash pop`으로 리팩토링 적용(AFTER) → ③ **동일 조건 재측정** → ④ 시드 데이터 정리·환경 복원.

### 실행 환경 (실제로 이렇게 돌림)

- 도커 컨테이너(app 8080 / mysql 3307 / redis 6380)는 **DB가 비어 있어** 측정 부적합 → 사용 안 함.
- 데이터가 있는 **로컬 MySQL(3306)·로컬 Redis(6379)** 에 붙는 앱을 **포트 8081**로 별도 기동(`./gradlew bootRun --args='--server.port=8081'`, 도커 8080과 충돌 회피). `ddl-auto=none`이라 populated DB 무변경 확인 후 실행.
- 인증: `POST /api/auth/register`로 임시 계정 생성 → DB에서 `role='ADMIN'` 승격 → `POST /api/auth/login`으로 JWT 획득(3개 엔드포인트 모두 접근 가능). **측정 종료 후 이 계정 삭제.**
- 측정: `curl -w '%{size_download}'`(응답 바이트, 결정적) + `%{time_total}` **15회 평균**.

### 결과

| 엔드포인트 | 응답 바이트 (전→후) | 평균 응답시간 (전→후) |
| --- | --- | --- |
| `GET /api/boards?page=0&size=20` | 9,351 → 9,351 B (**0%**) | 61.3 → **46.0 ms (−25%)** |
| `GET /api/admin/users/paging?page=0&size=20` | 8,647 → **5,829 B (−33%)** | 30.2 → **25.8 ms (−15%)** |
| `GET /api/care-requests/nearby`(20건) | 17,621 → **7,421 B (−58%)** | 38.3 → **9.9 ms (−74%)** |

### 해석

- **Board: 응답 바이트 동일(9,351B), 시간만 −25%.** 낭비가 **구간1(DB→앱)** 에만 있어 응답 DTO(`BoardDTO`)가 불변 → 클라이언트로 가는 바이트는 그대로. 개선은 응답 크기가 아니라 **지연시간**으로 나타난다(측정 A의 DB레벨 56%↓와 **같은 개선을 다른 각도로 잰 것**). HTTP 응답 바이트만 봤다면 "Board는 개선 0%"라고 잘못 결론 냈을 것.
- **Users: 바이트 −33% + 시간 −15%.** DTO를 `AdminUserListDTO`(12필드)로 바꿔 `UsersDTO`가 싣던 필드(petInfo/phone/socialUsers/petCoinBalance/emailVerified 등)가 빠짐 + `socialUsers` 배치 쿼리 제거.
- **CareRequest(지도): 바이트 −58% + 시간 −74% (최대 효과).** BEFORE는 20건마다 중첩 `PetDTO`·`applications`·전체 필드를 직렬화하고 user/pet/applications `@BatchSize` + pet 파일 조회까지 유발. AFTER는 14필드 flat 단일 쿼리 → 바이트·쿼리수 동시 급감.

### 측정 조건·한계

- `time_total`은 localhost 왕복(수십 ms)이라 **상대 개선폭** 지표이지 프로덕션 절대 지연이 아니다. 실서버·네트워크에선 payload 감소가 전송 시간에 추가로 기여한다.
- **care nearby는 원본 DB에 좌표+OPEN(`OPEN`/`IN_PROGRESS`) 데이터가 1건뿐**이라, 서울 좌표 OPEN 20건(pet 1마리 포함)을 측정용으로 시드한 뒤 전/후 측정하고 **측정 종료 즉시 삭제**했다. 합성 데이터이므로 절대치보다 전/후 비율로 해석. (DB레벨 측정 A에서 care를 뺀 이유이기도 하다.)
- 두 실행의 유일한 차이는 `git stash`로 토글한 리팩토링 코드뿐(동일 JWT·DB·포트).

---

## 재현 방법

```bash
# 측정 A (DB레벨) — 예: Board 작성자 컬럼 BEFORE(27) vs AFTER(3), page0 size20
#   SUM(LENGTH(COALESCE(u.<27개 컬럼>,''))) vs SUM(LENGTH(COALESCE(u.idx/username/location,'')))
#   FROM (SELECT b.user_idx FROM board b JOIN users uu ON uu.idx=b.user_idx
#         WHERE b.is_deleted=false AND uu.is_deleted=false AND uu.status='ACTIVE'
#         ORDER BY b.created_at DESC LIMIT 20) page JOIN users u ON u.idx=page.user_idx;

# 측정 B (HTTP레벨) — BEFORE/AFTER 각각 앱 기동 후 실행
TOKEN=...  # register→ADMIN 승격→login 으로 획득
# 응답 바이트
curl -s -o /dev/null -w '%{size_download}\n' -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/boards?page=0&size=20"
# 응답시간 15회 평균
for i in $(seq 1 15); do curl -s -o /dev/null -w '%{time_total}\n' -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/boards?page=0&size=20"; done | awk '{s+=$1;n++} END{print s/n}'
# BEFORE 재현: git stash 로 리팩토링 되돌린 뒤 위 앱 기동/측정 반복
```

## 정리

- **DB레벨(측정 A)** 은 구간1 낭비를 바이트로 직접 잡고, **HTTP레벨(측정 B)** 은 (구간1을 시간으로 + 구간2를 바이트로) 잡는다. 두 측정을 함께 봐야 오버페칭의 전체 그림이 완성된다.
- 예상(가설)과 실측이 일치했다 — 특히 Board가 "바이트가 아니라 지연으로" 개선된다는 예측이 맞았다는 점이, 우연이 아니라 메커니즘을 이해하고 검증했음을 보여준다.
