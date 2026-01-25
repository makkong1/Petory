# Real MySQL 적용 로그

이 문서는 Real MySQL 주제를 Petory 작업에 연결하고 튜닝 결과를 기록합니다.

## 1) 챕터-작업 매핑

| Real MySQL 주제 | Petory 대상 | 중요성 | 참고 문서 |
| --- | --- | --- | --- |
| 실행 계획 (EXPLAIN) | 게시글 목록/상세 쿼리 | 인덱스 사용 검증 및 filesort/temp 방지 | `docs/performance/query-optimization.md` |
| 인덱스 설계 (단일/복합/커버링) | 게시글 목록, 케어 요청 목록, 반응 | 필터링 + 정렬 경로 개선 | `docs/performance/query-optimization.md` |
| 조인 전략 + N+1 | 로그인/채팅방 목록 | 쿼리 수 및 지연 시간 감소 | `docs/troubleshooting/users/login-n-plus-one-issue.md` |
| 트랜잭션/락 | 모임 참여, 제재 | Lost Update 및 경쟁 조건 방지 | `docs/concurrency/transaction-concurrency-cases.md` |
| 캐싱 | 게시글 상세, 인기 게시글 | 반복 읽기 감소 | `docs/performance/query-optimization.md` |
| 페이징 | 게시글 목록 | 큰 OFFSET 스캔 방지 | `docs/performance/query-optimization.md` |

## 2) 초기 후보 (2-3개)

1) 게시글 목록/상세 (N+1, 인덱스 + 프로젝션)
2) 로그인/채팅방 목록 (배치 조회, 최신 메시지 쿼리)
3) 위치 검색 (공간 인덱스, 거리 필터)

## 3) 측정 템플릿

**시나리오**:
- 데이터셋 크기: <작성>
- 쿼리 경로: <API 또는 repository 메서드>
- 파라미터: <필터, 페이징, 사용자 역할>

**기준선 (Baseline)**:
- 쿼리 수: <n>
- 평균 지연 시간: <ms>
- EXPLAIN: type=<>, key=<>, rows=<>, Extra=<>

**변경 후**:
- 쿼리 수: <n>
- 평균 지연 시간: <ms>
- EXPLAIN: type=<>, key=<>, rows=<>, Extra=<>

**참고사항**:
- 위험/부작용:
- 롤백 계획:

## 4) 주간 로그

### Week 1
- 주제 집중: 실행 계획
- 대상 케이스: 게시글 목록/상세 (EXPLAIN 1-2개 쿼리)
- 상태: 진행 중
- 기준선 측정: 대기 중
- 변경 요약: 대기 중
- 결과: 대기 중

### Week 2
- 주제 집중: 인덱스 설계
- 대상 케이스: Week 1 쿼리에서 선정 (인덱스 후보)
- 상태: 대기 중
- 기준선 측정: 대기 중
- 변경 요약: 대기 중
- 결과: 대기 중

### Week 2 인덱스 후보 노트
- 후보 1: board(category, is_deleted, created_at DESC)
- 후보 2: board(user_idx, is_deleted, created_at DESC)
- 후보 3: board_reaction(board_idx, reaction_type)
- 기대 효과: 일반적인 목록 쿼리에서 행 수 감소 및 filesort 방지

### Week 3
- 주제 집중: 트랜잭션과 잠금
- 대상 케이스: 동시성 사례 재검토
- 상태: 대기 중
- 기준선 측정: 대기 중
- 변경 요약: 대기 중
- 결과: 대기 중

### Week 3 동시성 집중 포인트
- 재검토할 사례: 모임 참여자 수, 제재 증가
- 체크포인트: Lost Update 방지, 격리 수준 노트, 잠금 전략

### Week 4
- 주제 집중: SQL (11.3-11.7) + 데이터 타입
- 대상 케이스: SQL 패턴 정리 (1-2개 쿼리)
- 상태: 대기 중
- 기준선 측정: 대기 중
- 변경 요약: 대기 중
- 결과: 대기 중

### Week 4 SQL 정리 노트
- 가능한 경우 비인덱스 사용 조건 제거
- 안전한 경우 SELECT *를 프로젝션으로 축소
- 적절한 인덱스 필터로 UPDATE/DELETE 검증
- DDL 변경: 다운타임 윈도우 계획 및 인덱스 빌드 비용 확인

## 5) 케이스 로그

### 케이스 A: 게시글 목록/상세
- 범위: 게시글 목록 페이지, 상세 보기
- 기준선: 대기 중
- 변경사항: 대기 중
- 변경 후: 대기 중

### 케이스 B: 로그인/채팅방 목록
- 범위: 로그인 시 채팅방 목록
- 기준선: 대기 중
- 변경사항: 대기 중
- 변경 후: 대기 중

### 케이스 C: 위치 검색
- 범위: 반경 검색 및 정렬
- 기준선: 대기 중
- 변경사항: 대기 중
- 변경 후: 대기 중

