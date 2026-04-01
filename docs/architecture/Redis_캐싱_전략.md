# Redis 캐싱 전략 정리

백엔드 기준(`RedisConfig`, Board/Location/Notification/User 도메인)으로 정리했습니다. 코드가 바뀌면 이 문서도 함께 갱신하는 것이 좋습니다.

## 1. 한눈에 보기

| 구분 | 방식 | 실제 읽기 캐시 적재 |
|------|------|---------------------|
| 인기 위치 서비스 | Spring Cache `@Cacheable` (`popularLocationServices`) | 있음 (유일한 `@Cacheable`) |
| 게시글 목록 `boardList` | 설정·주석만 존재 | 없음 (`@Cacheable` 주석 처리) |
| 게시글 상세 `boardDetail` | `RedisCacheManager`에 영역만 정의 | **없음** (`getBoard`에서 `@Cacheable` 제거됨) |
| 알림 | `notificationRedisTemplate` 직접 사용 | Redis에 최신 N건 버퍼 |
| 이메일 사전 인증 | `StringRedisTemplate` 직접 사용 | 키·값 문자열 |

**중요**: `boardDetail` / `boardList` 이름으로 `@CacheEvict`가 여전히 붙어 있으나, **상세·목록 조회에 `@Cacheable`이 없으면 Redis에 해당 키가 쌓이지 않습니다.** 무효화 호출은 **레거시 정리·향후 재도입 대비**에 가깝고, 현재는 **실질적 캐시 히트가 없을 수 있습니다.**

---

## 2. Spring Cache (`RedisCacheManager`)

**설정**: `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`

- **기본 TTL**: 30분 (`defaultCacheConfig().entryTtl(Duration.ofMinutes(30))`)
- **명시적 이름**
  - `boardList`: 10분 (현재 **읽기 캐시 미사용** — `getAllBoards`의 `@Cacheable` 주석 처리)
  - `boardDetail`: 1시간 (현재 **읽기 캐시 미사용** — `getBoard`에 `@Cacheable` 없음)
  - `user`: 1시간 (**`@Cacheable` 사용처 없음**)

**실제 `@Cacheable`이 붙은 메서드** (전역 검색 기준):

- `LocationServiceService.getPopularLocationServices(String category)`  
  - 캐시 이름: `popularLocationServices`  
  - 키: `#category`  
  - **TTL**: `popularLocationServices` 전용 설정이 없어 **기본 30분** 적용  
  - 코드: 약 59행 부근 (`backend/.../location/service/LocationServiceService.java`)

---

## 3. 게시판 도메인 — 목록·상세·무효화

### 3.1 게시글 목록 (`boardList`)

- `BoardService.getAllBoards(String category)`  
  - `@Cacheable` **주석 처리** (데이터 동기화 이슈 대응 이력).
- **코드**: `BoardService.java` 앞부분 (주석 참고).

### 3.2 게시글 상세 (`boardDetail`)

- `BoardService.getBoard(long idx, Long viewerId)`  
  - **`@Cacheable` 없음.** 주석: 조회수 실시간 반영을 위해 제거.
- 따라서 **Spring Cache로 상세 DTO를 Redis에 넣는 경로는 현재 없음.**

### 3.3 `@CacheEvict` (레거시 무효화)

아래 메서드들은 여전히 `boardDetail` / `boardList`에 대해 `@CacheEvict`를 호출합니다. **캐시에 엔트리가 없으면 동작은 no-op에 가깝습니다.**

| 위치 | 내용 |
|------|------|
| `BoardService` | `createBoard` → `boardList` 전체 무효화; `updateBoard`/`deleteBoard`/상태·복구 등 → `boardDetail` + `boardList` |
| `CommentService` | 댓글 추가·수정·삭제·상태·복구 시 `boardDetail` 무효화 |
| `ReactionService` | 게시글 반응 시 `boardDetail` 무효화 |

**댓글 반응** `reactToComment`: 게시글 상세 캐시 무효화 **없음** (문서 이전 버전과 동일).

---

## 4. 인기 위치 서비스 (`popularLocationServices`)

- **메서드**: `LocationServiceService.getPopularLocationServices(String category)`
- **TTL**: Redis 캐시 **기본 30분** (`RedisCacheManager`의 `cacheDefaults`)
- **무효화**: 코드상 `@CacheEvict` 없음 → **TTL 의존**
- 평점·데이터 변경 시 캐시가 오래 남을 수 있어, 필요 시 무효화 또는 짧은 TTL 검토

---

## 5. 알림 — `notificationRedisTemplate`

**파일**: `backend/.../notification/service/NotificationService.java`

- **빈**: `RedisTemplate<String, Object> notificationRedisTemplate` (`RedisConfig`에서 정의)
- **키**: `notification:{userId}` (`REDIS_KEY_PREFIX`)
- **값**: `List<NotificationDTO>` (최대 50개, 최신 우선)
- **TTL**: 24시간 (`REDIS_TTL_HOURS`)
- **흐름**: 생성 시 DB 저장 후 Redis에도 적재; 목록 조회 시 Redis가 비어 있지 않으면 DB 목록과 병합(`mergeNotifications`). 읽음 처리 시 Redis에서 해당 항목 제거, 전체 읽음 시 키 삭제.

`getUnreadNotifications` 등은 Redis 병합 없이 DB 위주일 수 있음 — 상세는 `NotificationService` 참고.

---

## 6. 이메일 사전 인증 — `StringRedisTemplate`

**파일**: `backend/.../user/service/EmailVerificationService.java`

- **주입 타입**: `org.springframework.data.redis.core.StringRedisTemplate`  
  → Spring Boot Redis 자동 구성으로 제공되는 빈(프로젝트의 `customStringRedisTemplate` **Bean과는 별개**).
- **키 접두사**: `email_verification:pre_registration:{email}`
- **TTL**: 24시간
- **용도**: 회원가입 전 이메일 인증 완료 플래그 저장·조회·회원가입 후 삭제

**참고**: `RedisConfig.customStringRedisTemplate()` Bean은 정의만 되어 있고, **현재 도메인 코드에서 주입·사용되는 곳은 없음** (필요 시 토큰 블랙리스트 등으로 쓸 여지).

---

## 7. `RedisConfig`의 기타 `RedisTemplate` Bean

| Bean | 코드 사용 여부 |
|------|----------------|
| `objectRedisTemplate` | 미사용 |
| `reactionCountRedisTemplate` | 미사용 |
| `customStringRedisTemplate` | 미사용 |

---

## 8. 캐시 무효화·트랜잭션 (요약)

- `@CacheEvict`는 기본적으로 **메서드 성공 후** 무효화(`beforeInvocation = false`). 롤백 시 동작은 Spring Cache 문서 참고.
- `RedisTemplate` 직접 사용은 트랜잭션과 독립적으로 동작할 수 있어, DB 커밋 전후 순서 설계가 필요할 수 있음.

---

## 9. 운영 시 체크 포인트

1. **게시글 상세·목록**: 읽기 캐시를 다시 켤 경우 조회수·목록 동기화 정책을 먼저 정할 것.
2. **`boardDetail` 무효화**: 현재는 캐시 적재가 없어도 호출 비용은 작지만, 혼동을 줄이려면 주석·이슈로 “미사용”을 명시하는 편이 좋음.
3. **인기 위치**: 데이터 변경 시 stale 노출 → TTL 또는 무효화 전략.
4. **미사용 Redis Bean**: 유지보수 부담이면 제거·용도 주석 정리 검토.

---

## 10. 참고

- Spring Cache: https://docs.spring.io/spring-framework/reference/integration/cache.html  
- `RedisConfig.java`: `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`  
- Spring Data Redis: https://docs.spring.io/spring-data/redis/docs/current/reference/html/
