# 백엔드 포트폴리오 주제 적용 분석

## 📋 개요

본 문서는 제공된 38개 백엔드 포트폴리오 주제를 Petory 프로젝트에 적용 가능한 항목으로 분석하고, 각 항목별 현재 상태, 적용 방안, 우선순위를 정리한 문서입니다.

### 프로젝트 현재 상태 요약

**기술 스택:**
- Spring Boot 3.5.7, Java 17
- Spring Data JPA (Hibernate)
- MySQL
- Redis (Spring Data Redis)
- Spring Security + JWT
- WebSocket
- OAuth2 Client

**이미 구현된 주요 기능:**
- N+1 문제 해결 (배치 조회, Fetch Join)
- Redis 캐싱 (게시글 상세, 인기 위치 서비스)
- 트랜잭션 관리 및 동시성 제어 (일부)
- WebSocket 채팅
- OAuth2 소셜 로그인
- JWT 인증
- 스케줄러 (@Scheduled)
- 비동기 처리 (@Async)

---

## 📊 주제별 상세 분석

### 1. N+1 문제, fetch join, 페이징 문제, 중복데이터 문제, batchsize, 엔티티 그래프

**현재 상태:** ✅ **이미 구현됨**

**구현 내용:**
- **배치 조회**: `BoardService.mapBoardsWithReactionsBatch()` - IN 절로 반응 정보 배치 조회
- **Fetch Join**: 52개 이상의 쿼리에 `JOIN FETCH` 적용
- **EntityGraph**: 일부 Repository에 `@EntityGraph` 사용
- **Batch Size**: `hibernate.default_batch_fetch_size` 설정 가능 (현재 명시적 설정 없음)

**참고 파일:**
- `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java` (line 518-580)
- `docs/performance/query-optimization.md`
- `docs/troubleshooting/도메인별_트러블슈팅_체크리스트.md`

**개선 방안:**
- 페이징과 Fetch Join 조합 시 메모리 페이징 문제 해결 (별도 쿼리로 분리)
- `hibernate.default_batch_fetch_size` 전역 설정 추가 고려

**우선순위:** Low (이미 잘 구현됨)

---

### 2. 레디스 스프링 인메모리 캐시 - 캐시 유효 시간, 캐시 스탬피드 문제, 캐시랑 DB 불일치, 캐시 교체/무효화 정책, 백업, 이중화 정책

**현재 상태:** ⚠️ **부분 구현됨**

**구현 내용:**
- Spring Cache Abstraction 사용 (`@Cacheable`, `@CacheEvict`)
- Redis 기반 캐싱 (Spring Data Redis)
- TTL 설정: boardDetail (1시간), popularLocationServices (30분)
- 캐시 무효화 전략: 게시글 생성/수정/삭제 시 `@CacheEvict` 적용

**문제점:**
- 게시글 목록 캐시는 현재 비활성화됨 (데이터 동기화 문제)
- 캐시 스탬피드 문제 대응 없음
- Redis 이중화/백업 정책 없음

**참고 파일:**
- `docs/architecture/Redis_캐싱_전략.md`
- `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`

**개선 방안:**
1. **캐시 스탬피드 방지**: 랜덤 TTL 적용, 캐시 워밍업 전략
2. **캐시 무효화 개선**: 이벤트 기반 캐시 무효화
3. **Redis 이중화**: Redis Sentinel 또는 Cluster 구성
4. **캐시 백업**: RDB/AOF 백업 설정

**우선순위:** High

---

### 3. 동시성 문제 - 쿠폰 발급, 같은 자리 중복 예약 등, 트랜잭션 이상현상과 낙관적 락 비관적 락 네임드락 유니크 제약조건

**현재 상태:** ⚠️ **부분 구현됨**

**구현 내용:**
- **Unique 제약조건**: BoardReaction (board_idx, user_idx), BoardViewLog 등
- **비관적 락**: `MeetupRepository.findByIdWithLock()`, `ConversationRepository.findByIdWithLock()`
- **원자적 UPDATE**: `incrementWarningCount()`, `incrementParticipantsIfAvailable()`
- **트랜잭션 격리 수준**: 기본값 (REPEATABLE_READ)

**문제점:**
- 모임 참여 인원 증가는 원자적 쿼리로 해결됨 (✅)
- 펫케어 거래 확정 시 Race Condition 가능성 (문서에 개선안 제시됨)
- 댓글 수 증가는 Lost Update 가능성 있음

**참고 파일:**
- `docs/concurrency/transaction-concurrency-cases.md`
- `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupRepository.java` (line 74)

**개선 방안:**
1. **낙관적 락**: `@Version` 필드 추가하여 충돌 감지
2. **Named Lock**: Redis 분산락으로 전환 고려
3. **댓글 수 동기화**: 원자적 UPDATE 쿼리로 변경

**우선순위:** High

---

### 4. 외래키 데드락 문제

**현재 상태:** ❓ **미확인**

**분석 필요:**
- 외래키 인덱스 확인 필요
- 데드락 발생 가능성 있는 쿼리 패턴 분석 필요

**개선 방안:**
1. **인덱스 최적화**: 외래키 컬럼에 인덱스 확인
2. **쿼리 순서 통일**: 데드락 방지를 위한 쿼리 순서 규칙 수립
3. **데드락 모니터링**: MySQL 데드락 로그 분석

**우선순위:** Medium

---

### 5. 정규화 반정규화 통계테이블 배치

**현재 상태:** ✅ **부분 구현됨**

**구현 내용:**
- **통계 테이블**: `DailyStatistics` 엔티티 존재
- **스냅샷**: `BoardPopularitySnapshot` - 인기글 스냅샷 저장
- **배치 처리**: `@Scheduled`로 주기적 통계 수집

**참고 파일:**
- `backend/main/java/com/linkup/Petory/domain/statistics/entity/DailyStatistics.java`
- `backend/main/java/com/linkup/Petory/domain/board/entity/BoardPopularitySnapshot.java`

**개선 방안:**
- 통계 테이블 활용도 향상
- 배치 처리 최적화 (벌크 INSERT/UPDATE)

**우선순위:** Medium

---

### 6. 조회수 중복 방지 처리

**현재 상태:** ✅ **이미 구현됨**

**구현 내용:**
- `BoardViewLog` 테이블로 사용자별 조회 기록 관리
- `shouldIncrementView()` 메서드로 중복 조회 방지
- Unique 제약조건: `(board_idx, user_idx)`

**참고 파일:**
- `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java` (line 588-612)
- `docs/performance/query-optimization.md` (line 485-530)

**개선 방안:**
- Redis Set 방식으로 전환 고려 (TTL 24시간, DB 부담 감소)

**우선순위:** Low

---

### 7. 잘못된 인덱스로 인한 풀스캔 이슈

**현재 상태:** ✅ **인덱스 최적화 진행 중**

**구현 내용:**
- 복합 인덱스 다수 적용
- FULLTEXT 인덱스 (게시글 검색)
- 쿼리 최적화 문서 존재

**참고 파일:**
- `docs/performance/query-optimization.md`
- `docs/migration/db/indexes.sql`

**개선 방안:**
- EXPLAIN 분석 정기화
- 인덱스 사용률 모니터링
- 불필요한 인덱스 제거

**우선순위:** Medium

---

### 8. 페이징 No offset vs offset 커서기반 페이징 기법

**현재 상태:** ⚠️ **Offset 페이징만 사용 중**

**구현 내용:**
- Spring Data JPA `Pageable` 사용 (Offset 기반)
- 채팅 메시지는 커서 기반 페이징 (`getMessagesBefore()`)

**문제점:**
- 게시글 목록은 Offset 페이징 사용 (뒤 페이지로 갈수록 성능 저하)

**참고 파일:**
- `docs/performance/query-optimization.md` (line 433-482)

**개선 방안:**
1. **커서 기반 페이징 도입**: `lastId` 기반 조회
2. **하이브리드 방식**: 첫 페이지는 Offset, 이후는 커서

**우선순위:** High

---

### 9. 분산환경에서 동시성제어 Redis 분산락

**현재 상태:** ❌ **미구현**

**구현 내용:**
- 단일 서버 환경 가정
- 비관적 락만 사용 (DB 레벨)

**개선 방안:**
1. **Redisson 분산락**: Redis 기반 분산 락 구현
2. **락 타임아웃**: 데드락 방지를 위한 타임아웃 설정
3. **락 재시도**: Exponential Backoff 전략

**구현 예시:**
```java
@Configuration
public class RedisLockConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        return Redisson.create(config);
    }
}

@Service
public class DistributedLockService {
    private final RedissonClient redissonClient;
    
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                return supplier.get();
            }
            throw new RuntimeException("락 획득 실패");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**우선순위:** High

---

### 10. 이벤트 드리븐 @Async 레빗엠큐 카프카 비동기 처리

**현재 상태:** ⚠️ **@Async만 사용 중**

**구현 내용:**
- `@EnableAsync` 활성화
- 알림 발송 등에 비동기 처리 사용
- 이벤트 기반 아키텍처 일부 적용 (MeetupCreatedEvent)

**참고 파일:**
- `backend/main/java/com/linkup/Petory/PetoryApplication.java` (line 19)
- `docs/concurrency/transaction-concurrency-cases.md` (line 117-164)

**개선 방안:**
1. **RabbitMQ/Kafka 도입**: 이벤트 큐 기반 비동기 처리
2. **이벤트 발행 확대**: 도메인 이벤트 패턴 적용
3. **이벤트 저장소**: 이벤트 히스토리 저장

**우선순위:** Medium

---

### 11. 스케줄러 돌릴때 벌크 insert 벌크 update 최적화, 배치 시간대 분산, 부분커밋

**현재 상태:** ⚠️ **부분 구현됨**

**구현 내용:**
- `@Scheduled` 사용 (인기글 스냅샷 생성)
- 벌크 처리 로직 없음

**참고 파일:**
- `backend/main/java/com/linkup/Petory/domain/board/scheduler/BoardPopularityScheduler.java`

**개선 방안:**
1. **Spring Batch 도입**: 대량 데이터 처리
2. **벌크 INSERT/UPDATE**: `@Modifying` + `@Query` 활용
3. **배치 시간 분산**: 여러 스케줄러를 시간대별로 분산
4. **부분 커밋**: 청크 단위 처리

**구현 예시:**
```java
@Scheduled(cron = "0 0 2 * * ?") // 새벽 2시
public void processStatistics() {
    int batchSize = 1000;
    int offset = 0;
    
    while (true) {
        List<Statistics> batch = repository.findBatch(offset, batchSize);
        if (batch.isEmpty()) break;
        
        // 벌크 처리
        repository.bulkUpdate(batch);
        
        offset += batchSize;
        // 부분 커밋
        entityManager.flush();
        entityManager.clear();
    }
}
```

**우선순위:** Medium

---

### 12. DB 커넥션 풀 개수 몇개로 잡을지 문제

**현재 상태:** ❓ **기본 설정 사용 중**

**분석 필요:**
- HikariCP 기본 설정 확인 필요
- 트래픽 패턴 분석 필요

**개선 방안:**
1. **커넥션 풀 튜닝**: 
   - 최소/최대 커넥션 수 설정
   - 타임아웃 설정
   - 커넥션 유효성 검사
2. **모니터링**: 커넥션 풀 사용률 모니터링
3. **공식**: `connections = ((core_count * 2) + effective_spindle_count)`

**설정 예시:**
```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**우선순위:** Medium

---

### 13. OR 대신 UNION 쓰고 order by 칼럼에 인덱스 걸고 서브쿼리 조인으로 변경

**현재 상태:** ❓ **쿼리별 분석 필요**

**분석 필요:**
- OR 조건 사용 쿼리 확인
- ORDER BY 인덱스 활용 확인

**개선 방안:**
1. **OR → UNION**: 복잡한 OR 조건을 UNION으로 분리
2. **인덱스 최적화**: ORDER BY 컬럼에 인덱스 추가
3. **서브쿼리 최적화**: EXISTS, IN 최적화

**우선순위:** Low

---

### 14. auto increment VS uuid 어느것을 PK로 잡을지 고민 클러스터링 인덱스, 혹은 auto increment VS 복합PK

**현재 상태:** ✅ **Auto Increment 사용 중**

**구현 내용:**
- 모든 엔티티가 `@GeneratedValue(strategy = GenerationType.IDENTITY)` 사용
- Long 타입 ID 사용

**장점:**
- 인덱스 성능 우수 (클러스터링 인덱스)
- 저장 공간 효율적
- 순차적 증가로 캐시 효율

**단점:**
- 분산 환경에서 충돌 가능성 (현재는 단일 DB)
- 보안 이슈 (순차적 ID 노출)

**개선 방안:**
- 현재 구조 유지 권장 (단일 DB 환경)
- 향후 MSA 전환 시 UUID 고려

**우선순위:** Low

---

### 15. MSA 아키텍처

**현재 상태:** ❌ **모놀리식 아키텍처**

**현재 구조:**
- 단일 Spring Boot 애플리케이션
- 도메인별 패키지 분리 (DDD 구조)

**개선 방안:**
1. **도메인 분리**: Care, Board, Chat 등 독립 서비스로 분리
2. **API Gateway**: Spring Cloud Gateway 도입
3. **서비스 간 통신**: REST/gRPC
4. **분산 트랜잭션**: Saga 패턴

**우선순위:** Low (중장기)

---

### 16. 헥사고날 아키텍처, DDD

**현재 상태:** ⚠️ **DDD 구조는 있으나 헥사고날은 미적용**

**구현 내용:**
- 도메인별 패키지 구조 (DDD)
- 레이어드 아키텍처 (Controller-Service-Repository)

**개선 방안:**
1. **포트 & 어댑터 패턴**: 도메인 로직과 인프라 분리
2. **의존성 역전**: 도메인이 인프라에 의존하지 않도록
3. **도메인 서비스**: 순수 도메인 로직 분리

**우선순위:** Low

---

### 17. jwt vs 세션, 스티키세션, jwt로 발생하는 각종 이슈들 리프레시토큰 블랙리스트 등, OAuth sns 로그인

**현재 상태:** ⚠️ **JWT 사용 중, 리프레시 토큰/블랙리스트 미구현**

**구현 내용:**
- JWT 기반 인증
- OAuth2 소셜 로그인 (Google, Kakao 등)
- Access Token만 사용

**문제점:**
- 리프레시 토큰 미구현
- 토큰 블랙리스트 미구현
- 토큰 탈취 시 무효화 불가

**참고 파일:**
- `backend/main/java/com/linkup/Petory/global/security/` (JWT 관련)

**개선 방안:**
1. **리프레시 토큰 구현**: Redis에 저장, Access Token 갱신
2. **토큰 블랙리스트**: Redis에 로그아웃된 토큰 저장
3. **토큰 만료 시간**: Access Token (15분), Refresh Token (7일)
4. **스티키 세션**: 로드 밸런서 설정 (현재 불필요)

**구현 예시:**
```java
@Service
public class TokenService {
    private final RedisTemplate<String, String> redisTemplate;
    
    public void saveRefreshToken(String userId, String refreshToken) {
        redisTemplate.opsForValue().set(
            "refresh_token:" + userId, 
            refreshToken, 
            Duration.ofDays(7)
        );
    }
    
    public void addToBlacklist(String token, long expirationTime) {
        long ttl = expirationTime - System.currentTimeMillis();
        redisTemplate.opsForValue().set(
            "blacklist:" + token,
            "true",
            Duration.ofMillis(ttl)
        );
    }
    
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey("blacklist:" + token)
        );
    }
}
```

**우선순위:** Critical

---

### 18. DB 파티셔닝 기법 선택 파티셔닝 키에 대한 고민, 샤딩 샤드키

**현재 상태:** ❌ **미구현**

**현재 구조:**
- 단일 MySQL 인스턴스
- 파티셔닝 없음

**개선 방안:**
1. **파티셔닝 전략**: 
   - Range 파티셔닝: `created_at` 기준 (게시글, 댓글)
   - Hash 파티셔닝: `user_idx` 기준
2. **샤딩**: 
   - 수평 샤딩: 지역별 또는 사용자 ID 범위별
   - 샤드 키: `user_idx` 또는 `region`

**우선순위:** Low (대용량 데이터 시)

---

### 19. icp, 커버링 인덱스로 최적화 기법

**현재 상태:** ⚠️ **일부 적용**

**구현 내용:**
- 복합 인덱스 다수 적용
- 커버링 인덱스 명시적 사용 없음

**개선 방안:**
1. **ICP (Index Condition Pushdown)**: MySQL 5.6+ 자동 활용
2. **커버링 인덱스**: 자주 조회되는 컬럼을 인덱스에 포함
3. **인덱스 분석**: EXPLAIN으로 인덱스 활용 확인

**우선순위:** Medium

---

### 20. 인덱스가 걸려있어도 풀스캔하는 상황, 카디널리티 상, 인덱스보다 풀스캔이 더 빠른 케이스

**현재 상태:** ❓ **분석 필요**

**분석 필요:**
- EXPLAIN 분석으로 풀스캔 쿼리 확인
- 카디널리티 낮은 컬럼 인덱스 확인

**개선 방안:**
1. **카디널리티 분석**: 낮은 카디널리티 인덱스 제거 고려
2. **통계 정보 갱신**: `ANALYZE TABLE` 실행
3. **옵티마이저 힌트**: 필요 시 `USE INDEX` 사용

**우선순위:** Medium

---

### 21. facade 디자인 패턴 적용

**현재 상태:** ❓ **명시적 Facade 없음**

**현재 구조:**
- Service 레이어가 Facade 역할 수행

**개선 방안:**
- 복잡한 도메인 간 협업 시 Facade 패턴 적용 고려
- 예: 게시글 생성 시 알림, 통계 업데이트 등을 Facade로 통합

**우선순위:** Low

---

### 22. 트랜잭션 격리 수준 4가지중 어느것으로 할지 mysql mvvc 로직, 격리수준별 락 차이/트랜잭션 전파 옵션

**현재 상태:** ⚠️ **기본값 사용 중**

**구현 내용:**
- 기본 격리 수준: REPEATABLE_READ (MySQL InnoDB)
- 트랜잭션 전파: 기본값 (REQUIRED)

**참고 파일:**
- `docs/concurrency/transaction-concurrency-cases.md` (line 542-560)

**개선 방안:**
1. **격리 수준 명시**: 필요 시 `@Transactional(isolation = Isolation.READ_COMMITTED)`
2. **전파 옵션**: `REQUIRES_NEW` (독립 트랜잭션), `NESTED` (중첩 트랜잭션)
3. **MVCC 이해**: MySQL InnoDB의 MVCC 동작 원리 문서화

**우선순위:** Medium

---

### 23. JPA가 아니라 jdbc로 변경해서 최적화

**현재 상태:** ❌ **JPA 사용 중**

**현재 구조:**
- Spring Data JPA 전면 사용
- JDBC 직접 사용 없음

**개선 방안:**
- 대부분의 경우 JPA로 충분
- 특정 고성능 쿼리만 JDBC Template 사용 고려
- 예: 대량 통계 조회, 복잡한 집계 쿼리

**우선순위:** Low

---

### 24. 문자열 검색 '%키워드%' 이면 인덱스 안타는 문제, 데이터 CDC, 일라스틱 서치 색인, 풀텍스트 인덱스

**현재 상태:** ⚠️ **FULLTEXT 인덱스 사용 중**

**구현 내용:**
- 게시글 검색에 FULLTEXT 인덱스 사용 (ngram 파서)
- `MATCH ... AGAINST` 쿼리 사용

**참고 파일:**
- `docs/performance/query-optimization.md` (line 248-267)
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaBoardRepository.java`

**개선 방안:**
1. **ElasticSearch 도입**: 복잡한 검색 기능 확장 시
2. **CDC (Change Data Capture)**: Debezium 등으로 실시간 색인
3. **검색 성능 향상**: 검색 결과 캐싱

**우선순위:** Medium

---

### 25. 부하테스트 로드밸런서 역프록시

**현재 상태:** ❌ **미구현**

**개선 방안:**
1. **부하 테스트 도구**: JMeter, Gatling, k6
2. **로드 밸런서**: Nginx, HAProxy
3. **역프록시**: Nginx 설정

**참고 파일:**
- `docs/deployment/04-nginx-configuration.md`

**우선순위:** High

---

### 26. DB 리플리카 마스터 슬레이브 프록시SQL

**현재 상태:** ❌ **단일 DB 사용 중**

**개선 방안:**
1. **읽기 전용 레플리카**: 조회 쿼리는 슬레이브로 라우팅
2. **프록시SQL**: 쿼리 라우팅 자동화
3. **마스터-슬레이브 구성**: MySQL Replication 설정

**우선순위:** Medium

---

### 27. json압축해서 api 전송

**현재 상태:** ❌ **미구현**

**개선 방안:**
1. **Gzip 압축**: Spring Boot 자동 지원 (설정만 필요)
2. **프론트엔드 압축**: axios interceptor에서 압축 해제
3. **대용량 응답**: 스트리밍 응답 고려

**설정 예시:**
```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain
    min-response-size: 1024
```

**우선순위:** Low

---

### 28. S3 presigned-url

**현재 상태:** ❓ **파일 업로드 구현 확인 필요**

**개선 방안:**
1. **S3 연동**: AWS SDK로 파일 업로드
2. **Presigned URL**: 클라이언트 직접 업로드
3. **CDN 연동**: CloudFront 등

**우선순위:** Medium

---

### 29. HTTPS 암호화 과정 이해

**현재 상태:** ❓ **인프라 레벨 이슈**

**개선 방안:**
- SSL/TLS 인증서 설정
- Let's Encrypt 무료 인증서 사용
- Nginx에서 HTTPS 처리

**우선순위:** Medium

---

### 30. JPA 객체 순환 참조 문제

**현재 상태:** ✅ **해결됨**

**구현 내용:**
- DTO 변환으로 순환 참조 방지
- `@JsonIgnore` 사용 (필요 시)
- Converter 패턴으로 Entity → DTO 변환

**우선순위:** Low

---

### 31. 쿼리 not / exist 최적화 기법

**현재 상태:** ❓ **쿼리별 분석 필요**

**개선 방안:**
1. **NOT IN → NOT EXISTS**: 서브쿼리 최적화
2. **LEFT JOIN + IS NULL**: NOT EXISTS 대안
3. **인덱스 활용**: EXISTS 서브쿼리 인덱스 활용

**우선순위:** Low

---

### 32. 배치로 통계 정보 저장 기법

**현재 상태:** ⚠️ **부분 구현됨**

**구현 내용:**
- `DailyStatistics` 엔티티 존재
- 스케줄러로 통계 수집

**개선 방안:**
- Spring Batch 도입
- 벌크 INSERT 최적화
- 통계 집계 쿼리 최적화

**우선순위:** Medium

---

### 33. 보상트랜잭션

**현재 상태:** ❌ **미구현**

**개선 방안:**
1. **Saga 패턴**: 분산 트랜잭션 보상
2. **이벤트 기반**: 실패 시 보상 이벤트 발행
3. **예시**: 결제 실패 시 주문 취소

**우선순위:** Low (결제 기능 추가 시)

---

### 34. 채팅 웹소켓

**현재 상태:** ✅ **이미 구현됨**

**구현 내용:**
- Spring WebSocket 사용
- STOMP 프로토콜
- 실시간 메시지 전송

**참고 파일:**
- `backend/main/java/com/linkup/Petory/global/websocket/config/WebSocketConfig.java`
- `docs/architecture/채팅_시스템_설계.md`

**우선순위:** Low

---

### 35. 이미지 리사이징 어쩌고 사이즈

**현재 상태:** ❓ **파일 업로드 구현 확인 필요**

**개선 방안:**
1. **이미지 리사이징**: Thumbnailator 라이브러리
2. **다중 사이즈**: 원본, 썸네일, 미리보기 등
3. **비동기 처리**: 리사이징은 비동기로 처리

**구현 예시:**
```java
@Service
public class ImageResizeService {
    @Async
    public void resizeImage(String imagePath, int width, int height) {
        Thumbnails.of(imagePath)
            .size(width, height)
            .outputFormat("jpg")
            .toFile(imagePath + "_thumb");
    }
}
```

**우선순위:** Medium

---

### 36. 젠킨스 CICD 무중단 배포

**현재 상태:** ⚠️ **CI/CD 문서 존재**

**참고 파일:**
- `docs/deployment/03-cicd-pipeline.md`
- `docs/deployment/01-deployment-strategy.md`

**개선 방안:**
1. **Jenkins 파이프라인**: 자동 빌드/테스트/배포
2. **무중단 배포**: Blue-Green 또는 Rolling 배포
3. **Docker**: 컨테이너 기반 배포

**우선순위:** High

---

### 37. 결제API연동

**현재 상태:** ❌ **미구현**

**개선 방안:**
1. **PG사 연동**: 토스페이먼츠, 이니시스 등
2. **결제 검증**: 서버 사이드 검증 필수
3. **결제 이력**: 결제 내역 저장 및 관리
4. **환불 처리**: 부분/전체 환불 로직

**우선순위:** Low (비즈니스 요구사항 시)

---

### 38. 자바 GC 문제

**현재 상태:** ❓ **기본 GC 사용 중**

**개선 방안:**
1. **GC 모니터링**: JVM 옵션으로 GC 로그 수집
2. **GC 튜닝**: G1GC 또는 ZGC 고려
3. **메모리 누수**: 힙 덤프 분석

**JVM 옵션 예시:**
```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-Xmx2g
-Xms2g
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:gc.log
```

**우선순위:** Medium

---

### 39. 대용량 파일 처리 문제

**현재 상태:** ❓ **파일 업로드 구현 확인 필요**

**개선 방안:**
1. **스트리밍 업로드**: 청크 단위 업로드
2. **멀티파트 업로드**: S3 멀티파트 업로드
3. **비동기 처리**: 대용량 파일은 백그라운드 처리
4. **진행률 표시**: WebSocket으로 진행률 전송

**우선순위:** Medium

---

## 📊 카테고리별 요약

### 데이터베이스 최적화 (8개)
1. ✅ N+1 문제 해결
2. ⚠️ 페이징 최적화 (커서 기반)
3. ⚠️ 인덱스 최적화
4. ❓ 외래키 데드락
5. ⚠️ 정규화/반정규화
6. ✅ 조회수 중복 방지
7. ⚠️ ICP, 커버링 인덱스
8. ❓ 풀스캔 쿼리 분석

### 캐싱 전략 (1개)
1. ⚠️ Redis 캐시 전략 개선

### 동시성 제어 (3개)
1. ⚠️ 동시성 문제 해결 (락, 트랜잭션)
2. ❌ Redis 분산락
3. ⚠️ 트랜잭션 격리 수준

### 아키텍처 (3개)
1. ❌ MSA 아키텍처
2. ⚠️ 헥사고날 아키텍처
3. ❓ Facade 패턴

### 보안 (1개)
1. ⚠️ JWT 리프레시 토큰/블랙리스트

### 성능 최적화 (7개)
1. ⚠️ 벌크 INSERT/UPDATE
2. ❓ DB 커넥션 풀 튜닝
3. ⚠️ OR → UNION 최적화
4. ⚠️ 문자열 검색 (ElasticSearch)
5. ❌ JSON 압축
6. ⚠️ 이미지 리사이징
7. ⚠️ 대용량 파일 처리

### 인프라/배포 (6개)
1. ❌ 부하 테스트
2. ❌ 로드 밸런서/역프록시
3. ❌ DB 리플리카
4. ⚠️ CI/CD 파이프라인
5. ❓ HTTPS 설정
6. ⚠️ GC 튜닝

### 기타 (9개)
1. ✅ auto increment (현재 구조 유지)
2. ⚠️ 파티셔닝/샤딩
3. ❓ JDBC 직접 사용
4. ✅ JPA 순환 참조 해결
5. ❓ NOT/EXISTS 최적화
6. ⚠️ 배치 통계 저장
7. ❌ 보상 트랜잭션
8. ✅ WebSocket 채팅
9. ❌ 결제 API 연동

---

## 🎯 우선순위별 로드맵

### Critical (즉시 적용)
1. **JWT 리프레시 토큰 및 블랙리스트 구현**
   - 보안 강화 필수
   - 구현 난이도: 중간
   - 예상 시간: 1주

### High (단기 개선 - 1-2개월)
1. **Redis 분산락 구현**
   - 분산 환경 대비
   - 구현 난이도: 중간
   - 예상 시간: 3일

2. **커서 기반 페이징 도입**
   - 성능 개선
   - 구현 난이도: 낮음
   - 예상 시간: 2일

3. **Redis 캐시 전략 개선**
   - 캐시 스탬피드 방지
   - 구현 난이도: 중간
   - 예상 시간: 3일

4. **부하 테스트 및 로드 밸런서 설정**
   - 성능 검증 필수
   - 구현 난이도: 중간
   - 예상 시간: 1주

5. **CI/CD 파이프라인 구축**
   - 배포 자동화
   - 구현 난이도: 높음
   - 예상 시간: 2주

### Medium (중기 개선 - 3-6개월)
1. 이벤트 드리븐 아키텍처 (RabbitMQ/Kafka)
2. ElasticSearch 도입
3. DB 리플리카 구성
4. 벌크 처리 최적화
5. 이미지 리사이징
6. GC 튜닝
7. DB 커넥션 풀 튜닝

### Low (장기 개선 또는 선택적)
1. MSA 전환
2. 파티셔닝/샤딩
3. 결제 API 연동
4. 보상 트랜잭션
5. 헥사고날 아키텍처

---

## 📝 구현 가이드

### 1. JWT 리프레시 토큰 구현

**단계:**
1. RefreshToken 엔티티 생성 또는 Redis 저장
2. TokenService에 리프레시 토큰 발급/검증 로직 추가
3. `/api/auth/refresh` 엔드포인트 생성
4. 프론트엔드에서 Access Token 만료 시 자동 갱신

**코드 위치:**
- `backend/main/java/com/linkup/Petory/global/security/`

### 2. Redis 분산락 구현

**단계:**
1. Redisson 의존성 추가
2. RedissonClient Bean 생성
3. DistributedLockService 생성
4. 동시성 제어 필요한 메서드에 적용

**의존성:**
```gradle
implementation 'org.redisson:redisson-spring-boot-starter:3.24.3'
```

### 3. 커서 기반 페이징

**단계:**
1. Repository에 `findByIdLessThan()` 메서드 추가
2. Service에서 커서 기반 페이징 로직 구현
3. Controller에서 `lastId` 파라미터 받기
4. 프론트엔드에서 무한 스크롤 적용

**코드 예시:**
```java
@Query("SELECT b FROM Board b WHERE b.idx < :lastId AND b.isDeleted = false ORDER BY b.idx DESC")
List<Board> findByIdLessThan(@Param("lastId") Long lastId, Pageable pageable);
```

---

## 📚 참고 자료

- [Redis 캐싱 전략](./architecture/Redis_캐싱_전략.md)
- [트랜잭션 및 동시성 제어](./concurrency/transaction-concurrency-cases.md)
- [성능 최적화 가이드](./performance/query-optimization.md)
- [도메인별 트러블슈팅](./troubleshooting/도메인별_트러블슈팅_체크리스트.md)
- [배포 전략](./deployment/01-deployment-strategy.md)

---

## 🔄 업데이트 이력

- 2026-01-25: 초기 문서 작성
