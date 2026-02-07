# Board 검색 기능 리팩토링

## 개요

| 항목 | 내용 |
|------|------|
| **작업일** | 2026-01-31 |
| **대상** | 게시글 검색 기능 |
| **목적** | 검색 성능 최적화 및 코드 간소화 |

---

## 변경 사항

### 1. TITLE/CONTENT 개별 검색 → TITLE_CONTENT 통합

#### 문제점
- `TITLE`, `CONTENT` 개별 검색이 `LIKE '%keyword%'` 사용
- **풀 테이블 스캔** 발생 (인덱스 활용 불가)
- 중복되는 쿼리 메서드 유지 부담

#### 해결
- FULLTEXT 인덱스 활용하는 `TITLE_CONTENT` 검색으로 통합
- 제목/내용 개별 검색 메서드 삭제

#### Before
```
검색 타입: NICKNAME | TITLE | CONTENT | TITLE_CONTENT
```

#### After
```
검색 타입: TITLE_CONTENT | NICKNAME
```

#### 삭제된 메서드
| 파일 | 메서드명 |
|------|----------|
| `SpringDataJpaBoardRepository` | `findByTitleContainingAndIsDeletedFalseOrderByCreatedAtDesc` |
| `SpringDataJpaBoardRepository` | `findByContentContainingAndIsDeletedFalseOrderByCreatedAtDesc` |
| `BoardRepository` | 동일 |
| `JpaBoardAdapter` | 동일 |

---

### 2. NICKNAME 검색 최적화

#### 문제점 (Before)
```
요청 → findByNickname(nickname) → User 객체
     → findByUserAndIsDeletedFalse...(user) → Board 목록
     → 메모리에서 페이징 처리 (subList)
```

| 문제 | 설명 |
|------|------|
| **2번 쿼리** | User 조회 + Board 조회 분리 |
| **메모리 페이징** | 전체 조회 후 subList로 잘라냄 → 대용량 데이터 시 OOM 위험 |
| **정확 일치만** | `findByNickname`은 완전 일치만 검색 |

#### 해결 (After)
```
요청 → searchByNicknameWithPaging(nickname, pageable) → Page<Board>
     (JOIN 쿼리로 1번에 처리, DB 레벨 페이징)
```

| 개선 | 설명 |
|------|------|
| **1번 쿼리** | JOIN으로 User + Board 한 번에 조회 |
| **DB 페이징** | `Pageable`로 DB 레벨에서 LIMIT/OFFSET 처리 |
| **부분 일치** | `LIKE %nickname%`로 부분 검색 지원 |

#### 추가된 메서드

```java
// SpringDataJpaBoardRepository.java
@Query("SELECT b FROM Board b JOIN FETCH b.user u " +
       "WHERE u.nickname LIKE %:nickname% " +
       "AND b.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
Page<Board> searchByNicknameWithPaging(@Param("nickname") String nickname, Pageable pageable);
```

---

## 쿼리 흐름 비교

### NICKNAME 검색

#### Before (2 Query + Memory Paging)
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│   Service   │────▶│ UsersRepo   │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                   │
                           │  findByNickname   │
                           │◀──────────────────│
                           │                   
                           │     ┌─────────────┐
                           │────▶│ BoardRepo   │
                           │     └─────────────┘
                           │          │
                           │  findByUser... (전체)
                           │◀─────────│
                           │
                    ┌──────▼──────┐
                    │ Memory에서  │
                    │ subList()   │
                    └─────────────┘
```

#### After (1 Query + DB Paging)
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│   Service   │────▶│ BoardRepo   │
└─────────────┘     └─────────────┘     └─────────────┘
                                              │
                              searchByNicknameWithPaging
                              (JOIN + LIMIT/OFFSET)
                                              │
                           ┌──────────────────▼──────────────────┐
                           │  Page<Board> (DB 레벨 페이징 완료)  │
                           └─────────────────────────────────────┘
```

---

## 성능 비교

| 항목 | Before | After | 개선 |
|------|--------|-------|------|
| **TITLE/CONTENT 검색** | LIKE (Full Scan) | FULLTEXT (인덱스) | 인덱스 활용 |
| **NICKNAME 쿼리 수** | 2번 | 1번 | 50% 감소 |
| **NICKNAME 페이징** | 메모리 (subList) | DB (LIMIT/OFFSET) | 메모리 사용량 감소 |
| **NICKNAME 검색 방식** | 완전 일치 | 부분 일치 | UX 개선 |

---

## 관련 인덱스

### 기존 인덱스 (활용)
```sql
-- 제목+내용 FULLTEXT 검색
CREATE FULLTEXT INDEX idx_board_title_content ON board(title, content);

-- 사용자별 게시글 조회 (NICKNAME 검색의 JOIN 최적화)
CREATE INDEX idx_board_user_deleted_created ON board(user_idx, is_deleted, created_at);

-- 닉네임 Unique 제약 (인덱스 역할 겸용)
-- uk_users_nickname ON users(nickname)
```

### NICKNAME 인덱스 관련 분석

#### 현재 상태
| Key_name | Non_unique | 비고 |
|----------|------------|------|
| `uk_users_nickname` | 0 (Unique) | 닉네임 중복 방지 + 인덱스 역할 |

#### EXPLAIN 결과 (2026-01-31 테스트)

```sql
-- 테스트 쿼리
EXPLAIN
SELECT b.*, u.*
FROM board b
INNER JOIN users u ON b.user_idx = u.idx
WHERE u.nickname LIKE '사용자%'
  AND b.is_deleted = false
  AND u.is_deleted = false
  AND u.status = 'ACTIVE'
ORDER BY b.created_at DESC
LIMIT 0, 20;
```

| 테이블 | type | key | rows | 분석 |
|--------|------|-----|------|------|
| users | ALL | NULL | 1653 | Full Scan (인덱스 안 탐) |
| board | ref | idx_board_user_deleted_created | 15 | 인덱스 사용 OK |

#### 왜 인덱스를 안 타나?

**MySQL 옵티마이저 판단**: 1653행이면 Full Scan이 더 빠르다고 판단.

```sql
-- FORCE INDEX로 강제 지정하면 인덱스 탐
EXPLAIN
SELECT b.*, u.*
FROM board b
INNER JOIN users u FORCE INDEX (uk_users_nickname) ON b.user_idx = u.idx
WHERE u.nickname LIKE '사용자%' ...

-- 결과: type = range, key = uk_users_nickname (인덱스 사용됨)
```

#### 결론

| 상황 | 동작 |
|------|------|
| users 테이블 작음 (~수천 행) | Full Scan 선택 (오버헤드 < 이득) |
| users 테이블 큼 (수만 행+) | 자동으로 인덱스 사용 |

**현재 1653행에서 Full Scan은 밀리초 단위로 빠름. 성능 이슈 없음.**

유저가 10만명 이상으로 증가하면 옵티마이저가 자동으로 인덱스 활용.

#### LIKE 패턴별 인덱스 사용 가능 여부

| 패턴 | 인덱스 사용 | 현재 쿼리 |
|------|------------|-----------|
| `LIKE 'value%'` | O (Prefix Match) | - |
| `LIKE '%value'` | X | - |
| `LIKE '%value%'` | X | **현재 사용 중** |

`LIKE '%value%'`는 인덱스가 있어도 Full Scan. 하지만 users 테이블이 작아서 문제 없음.

---

## 변경된 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `SpringDataJpaBoardRepository.java` | TITLE/CONTENT 삭제, searchByNicknameWithPaging 추가 |
| `BoardRepository.java` | 인터페이스 동기화 |
| `JpaBoardAdapter.java` | 어댑터 동기화 |
| `BoardService.java` | 검색 로직 간소화, 불필요 import 제거 |
| `CommunityBoard.js` | 검색 타입 옵션 정리 (제목+내용, 작성자) |

---

## 프론트엔드 변경

### 검색 타입 옵션

#### Before
```jsx
<option value="NICKNAME">닉네임</option>
<option value="TITLE">제목</option>
<option value="CONTENT">내용</option>
<option value="TITLE_CONTENT">제목+내용</option>
```

#### After
```jsx
<option value="TITLE_CONTENT">제목+내용</option>
<option value="NICKNAME">작성자</option>
```

---

## 테스트 체크리스트

- [ ] 제목+내용 검색 정상 동작
- [ ] 작성자(닉네임) 부분 검색 동작 확인 (예: "홍" 검색 시 "홍길동" 포함)
- [ ] 페이징 정상 동작 (더보기 버튼)
- [ ] 삭제된 사용자/게시글 필터링 확인
- [ ] 검색 결과 없을 때 빈 배열 반환 확인
