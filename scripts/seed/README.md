# 개발/성능측정용 시드 데이터

## 왜 있나

2026-07-13 이전의 더미 데이터는 **앱을 통하지 않고 테이블마다 따로 INSERT** 되어 있었고,
그 결과 도메인 규칙을 전면적으로 위반한 상태였다:

| 실태 | 앱 규칙 |
|---|---|
| 유저 7,419명 중 **7,411명이 펫 없음** (펫 총 8마리) | 반려동물 케어 앱 |
| 케어요청 1,014건 **전부 에스크로 없음** | 거래 확정 시 에스크로가 생겨야 함 |
| 모임 3,766개 중 **3,002개가 참여자 0명** | 주최자는 항상 참여자 |
| `board.view_count` 총합 **5,154,619** vs `board_view_log` **17행** | 로그 삽입 성공 시에만 조회수 증가 |

카운터를 손으로 채워 넣은 것이 근본 원인이었다. 그래서 이 시드는 **카운터를 직접 넣지 않는다.**

## 설계 원칙

**반정규화 카운터는 전부 실제 자식 행을 집계해서 유도한다** (`seed-dev-data.sql` §6).

`like_count`, `comment_count`, `view_count`, `current_participants`, `pet_coin_balance`,
`rating`, `review_count` — 어느 것도 임의값을 넣지 않는다. 자식 행을 먼저 만들고
마지막에 `UPDATE ... JOIN (SELECT COUNT(*) ...)` 로 계산한다.

이러면 카운터 불일치가 **구조적으로 발생할 수 없다.** 생성 중 `INSERT IGNORE` 로
중복이 걸러져도 카운터는 실제 행 수를 세므로 저절로 맞는다.

## 사용법

```bash
# 시드 (기존 데이터 삭제 후 재생성 — locationservice·MASTER 계정은 보존)
mysql -h127.0.0.1 -P3306 -uroot -p petory < scripts/seed/seed-dev-data.sql

# 정합성 검증 — 25개 항목이 전부 0 이어야 한다
mysql -h127.0.0.1 -P3306 -uroot -p -t petory < scripts/seed/verify-data-integrity.sql
```

`verify-data-integrity.sql` 은 시드 직후뿐 아니라 **주기적으로** 돌려서 카운터 드리프트를
잡는 용도로도 쓴다. 운영 중에는 배치 삽입·수동 SQL 등 앱을 우회하는 경로가 생기므로,
원자적 UPDATE 만으로는 정합성이 영구히 보장되지 않는다.

## 규모

`seed-dev-data.sql` 상단의 `@USERS`, `@BOARDS` 등만 바꾸면 조절된다. 기본값:

| 테이블 | 행 수 | |
|---|---|---|
| users / pets | 10,000 / 12,000 | 전원 펫 보유 |
| board / comment / board_reaction | 50,000 / 150,000 / 175,000 | |
| board_view_log | 125,000 | `view_count` 의 원본 |
| meetup / meetupparticipants | 5,000 / 24,292 | 주최자 항상 포함 |
| carerequest / careapplication | 3,000 / 6,000 | OPEN 1,200 · 확정 1,800 |
| pet_coin_escrow / pet_coin_transaction | 1,800 / 13,000 | 상태 기계대로 |
| conversation / chatmessage | 6,800 / 30,600 | |
| locationservicereview | 20,000 | `rating` 의 원본 |
| **locationservice** | **22,905** | **공공데이터 — 보존, 재생성 안 함** |

**board 5만이 하한선인 이유**: MySQL 옵티마이저는 테이블이 작으면 인덱스를 무시하고
풀스캔한다. 1만 행 아래에서는 인덱스를 걸든 말든 측정값이 같게 나와서
**리팩토링 전후 성능 비교가 무의미해진다.** 딥 페이징(OFFSET) 문제도 이 규모부터 드러난다.

## 시드 계정

- 로그인 ID: `seed_user_1` ~ `seed_user_10000`
- 비밀번호: `Seed1234!` (전원 동일)
- 역할: 대부분 USER, 20명 중 1명 SERVICE_PROVIDER, 1000명 중 1명 ADMIN

## 주의

- ⚠️ **이 스크립트를 Flyway 경로(`backend/main/resources/db/migration/`)에 두지 말 것.**
  거기 두면 운영 DB에서도 더미가 생성된다.
- 도커 DB에는 시드를 넣지 않는다. 도커는 배포 리허설 환경이므로 실제 서버와 같이
  빈 상태에서 시작해야 한다 (`locationservice` 공공데이터만 보존).
- 테스트는 `petory_test` DB를 쓰므로(`build.gradle`) 이 시드 데이터를 오염시키지 않는다.
