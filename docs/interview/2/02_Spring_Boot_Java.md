# 2. Spring Boot & Java

## Q2-1. Spring Boot의 주요 특징과 이 프로젝트에서 활용한 부분을 설명해주세요.

### 답변 포인트
- Auto Configuration으로 설정 자동화
- Spring Data JPA로 데이터 액세스 추상화 (Repository + JpaAdapter 패턴)
- Spring Security로 인증/인가 처리 (JWT + OAuth2)
- Spring Scheduler로 주기적 작업 자동화
- Spring Cache로 캐싱 전략 구현 (Redis)

### 상세 답변

#### 1. Auto Configuration
**위치**: `PetoryApplication.java`
- `@SpringBootApplication` 어노테이션으로 자동 설정 활성화
- Spring Boot 3.5.7, Java 17 사용
- DataSource, JPA, Security, Redis 등 자동 설정

**전체 흐름**:
```
@SpringBootApplication
  ↓
자동 설정 스캔
  ↓
DataSource 자동 구성 (application.properties 기반)
JPA 자동 구성 (Hibernate)
Security 자동 구성 (JWT + OAuth2)
Redis 자동 구성 (캐시, 이메일 인증, 알림 버퍼링)
```

#### 2. Spring Data JPA (Repository + Adapter 패턴)
**위치**: `domain/*/repository/`
- **도메인 인터페이스**: `BoardRepository`, `UsersRepository` 등 - 도메인에 필요한 메서드만 노출
- **Jpa*Adapter**: `JpaBoardAdapter`, `JpaUsersAdapter` 등 - Spring Data JPA 구현체 래핑
- **SpringDataJpa*Repository**: `SpringDataJpaBoardRepository` - JpaRepository 상속, 쿼리 메서드, `@Query` JPQL

**예시**:
```java
// domain/board/repository/SpringDataJpaBoardRepository.java
public interface SpringDataJpaBoardRepository extends JpaRepository<Board, Long>, JpaSpecificationExecutor<Board> {
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false ORDER BY b.createdAt DESC")
    List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();
}

// domain/board/repository/JpaBoardAdapter.java
public class JpaBoardAdapter implements BoardRepository {
    private final SpringDataJpaBoardRepository jpaRepository;
    // ...
}
```

#### 3. Spring Security
**위치**: `global/security/SecurityConfig.java`, `filter/JwtAuthenticationFilter.java`
- **JWT 기반 인증**: Access Token (15분) + Refresh Token (1일)
- **OAuth2 소셜 로그인**: Google, Naver (OAuth2SuccessHandler, OAuth2UserProviderRouter)
- **필터 체인**: `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
- **경로별 권한**: `permitAll()` (auth, 회원가입, 이메일 인증, 업로드, 지오코딩, WebSocket), `hasAnyRole('ADMIN','MASTER')` (admin), `hasRole('MASTER')` (master), `authenticated()` (나머지 API)
- **메서드 레벨**: `@PreAuthorize("hasAnyRole('ADMIN','MASTER')")` 등

**전체 흐름**:
```
요청 → JwtAuthenticationFilter.doFilterInternal()
  ↓
Authorization 헤더 또는 token 쿼리 파라미터에서 토큰 추출
  ↓
JwtUtil.validateToken() 검증
  ↓
UserDetailsService.loadUserByUsername() 사용자 조회
  ↓
SecurityContext에 인증 정보 저장
  ↓
SecurityConfig에서 경로별 권한 체크 (permitAll / admin / authenticated)
```

#### 4. Spring Scheduler
**위치**: `domain/*/service/*Scheduler.java`, `domain/user/scheduler/UserSanctionScheduler.java`
- `@EnableScheduling` 활성화 (PetoryApplication)
- `@Scheduled` 어노테이션으로 주기적 작업 정의

**구현 사례**:
| 스케줄러 | 위치 | 스케줄 | 역할 |
|---------|------|--------|------|
| BoardPopularityScheduler | domain/board/service/ | 매일 18:30, 매주 월요일 18:30 | 인기글 스냅샷 생성 |
| StatisticsScheduler | domain/statistics/service/ | `${statistics.scheduler.cron:0 30 18 * * ?}` (기본 18:30) | 일별 통계 집계 |
| UserSanctionScheduler | domain/user/scheduler/ | 매일 자정 `0 0 0 * * *` | 제재 자동 해제 |
| CareRequestScheduler | domain/care/service/ | 매시간 정각, 매일 자정 | 케어 요청 만료 처리, 정리 작업 |

**코드 예시**:
```java
// domain/statistics/service/StatisticsScheduler.java
@Scheduled(cron = "${statistics.scheduler.cron:0 30 18 * * ?}")
@Transactional
public void aggregateDailyStatistics() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    aggregateStatisticsForDate(yesterday);
}
```

#### 5. Spring Cache
**위치**: `PetoryApplication.java` (`@EnableCaching`), `global/security/RedisConfig.java`
- Redis 기반 캐싱
- `@Cacheable`: 인기 위치 서비스 조회 (카테고리별)
- `@CacheEvict`: 게시글 생성/수정/삭제 시 boardList, boardDetail 캐시 무효화

**사용 예시**:
```java
// domain/location/service/LocationServiceService.java
@Cacheable(value = "popularLocationServices", key = "#category")
public List<LocationServiceDTO> getPopularLocationServices(String category) { ... }

// domain/board/service/BoardService.java
@CacheEvict(value = "boardList", allEntries = true)
@Transactional
public BoardDTO createBoard(BoardDTO dto) { ... }
```

---

## Q2-2. @Transactional 어노테이션의 동작 원리와 격리 수준을 설명해주세요.

### 답변 포인트
- AOP 기반으로 트랜잭션 관리
- 기본 격리 수준: READ_COMMITTED (MySQL 기본값 REPEATABLE_READ)
- 전파 속성: REQUIRED (기본값)
- 읽기 전용 트랜잭션: `@Transactional(readOnly = true)`

### 상세 답변

#### 1. 동작 원리 (AOP)
**위치**: 모든 Service 클래스
- Spring AOP 프록시를 통해 트랜잭션 관리
- 메서드 실행 전 트랜잭션 시작
- 메서드 실행 후 커밋 또는 롤백 (RuntimeException 시)

**전체 흐름**:
```
@Transactional 메서드 호출
  ↓
AOP 프록시가 트랜잭션 시작
  ↓
메서드 실행
  ↓
성공 → 커밋
실패(RuntimeException) → 롤백
```

#### 2. 격리 수준
**MySQL 기본값**: REPEATABLE_READ
- Dirty Read 방지
- Non-repeatable Read 방지
- Phantom Read 가능 (Gap Lock으로 일부 방지)

**사용 예시**:
```java
// domain/board/service/BoardService.java
@Service
@Transactional(readOnly = true)  // 클래스 레벨 기본값
public class BoardService {
    
    @Transactional  // 쓰기 작업은 별도 트랜잭션
    public BoardDTO createBoard(BoardDTO boardDTO) { ... }
}
```

#### 3. 전파 속성
**기본값**: REQUIRED
- 기존 트랜잭션이 있으면 참여
- 없으면 새 트랜잭션 생성

**사용 예시**:
```java
// domain/user/service/UserSanctionService.java
@Transactional
public void warnUser(Long userId) {
    usersRepository.incrementWarningCount(userId);
    // REQUIRED: 기존 트랜잭션에 참여
}
```

---

## Q2-3. Spring Security의 필터 체인을 설명해주세요.

### 답변 포인트
- JwtAuthenticationFilter: JWT 토큰 검증 (OncePerRequestFilter 상속)
- SecurityConfig: 경로별 인증/인가 설정
- OAuth2: 소셜 로그인 (Google, Naver)
- 공개 API와 보호된 API 구분

### 상세 답변

#### 1. 필터 체인 구성
**위치**: `global/security/SecurityConfig.java`, `filter/JwtAuthenticationFilter.java`

**필터 순서**:
```
1. JwtAuthenticationFilter (OncePerRequestFilter 상속)
   - addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
2. UsernamePasswordAuthenticationFilter (OAuth2 로그인 시)
3. 기타 Spring Security 필터
```

#### 2. JwtAuthenticationFilter
**위치**: `filter/JwtAuthenticationFilter.java`
**메서드**: `doFilterInternal()`

**전체 흐름**:
```
요청 수신
  ↓
JwtAuthenticationFilter.doFilterInternal()
  ↓
Authorization 헤더(Bearer) 또는 token 쿼리 파라미터에서 토큰 추출
  ↓
JwtUtil.validateToken() 검증
  ↓
UserDetailsService.loadUserByUsername() 사용자 조회
  ↓
UsernamePasswordAuthenticationToken 생성 → SecurityContext에 저장
  ↓
filterChain.doFilter() 다음 필터로 전달
```

**코드 예시**:
```java
// filter/JwtAuthenticationFilter.java
if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
    token = jwtUtil.extractTokenFromHeader(authorizationHeader);
}
if (token == null) {
    token = request.getParameter("token");  // SSE 등 헤더 사용 불가 시
}
if (token != null && jwtUtil.validateToken(token)) {
    UserDetails userDetails = userDetailsService.loadUserByUsername(id);
    SecurityContextHolder.getContext().setAuthentication(authToken);
}
filterChain.doFilter(request, response);
```

#### 3. SecurityConfig 경로별 권한
**위치**: `global/security/SecurityConfig.java`

| 경로 | 권한 |
|------|------|
| `/api/auth/**` | permitAll |
| `/api/users/register`, 이메일 인증, OAuth2 | permitAll |
| `/api/uploads/**`, `/api/geocoding/**` | permitAll |
| `/ws/**`, `/chat/**` | permitAll (WebSocket, 인증은 인터셉터에서) |
| `/api/master/**` | hasRole("MASTER") |
| `/api/admin/**` | hasAnyRole("ADMIN", "MASTER") |
| `/api/**` | authenticated() |

---

## Q2-4. Spring Scheduler를 어떤 용도로 사용했나요?

### 답변 포인트
- 일별 통계 집계 (StatisticsScheduler)
- 인기글 스냅샷 생성 (BoardPopularityScheduler)
- 제재 자동 해제 (UserSanctionScheduler)
- 케어 요청 만료 처리 (CareRequestScheduler)

### 상세 답변

#### 1. 인기글 스냅샷 생성
**위치**: `domain/board/service/BoardPopularityScheduler.java`
- `generateWeeklyPopularitySnapshots()`: 매일 18:30
- `generateMonthlyPopularitySnapshots()`: 매주 월요일 18:30

**전체 흐름**:
```
@Scheduled(cron = "0 30 18 * * ?")
  ↓
BoardPopularityScheduler.generateWeeklyPopularitySnapshots()
  ↓
BoardPopularityService.generateSnapshots(PopularityPeriodType.WEEKLY)
  ↓
조회수·반응 수 기반 인기글 계산 및 스냅샷 저장
```

#### 2. 통계 집계
**위치**: `domain/statistics/service/StatisticsScheduler.java`
- **스케줄**: `application.properties`의 `statistics.scheduler.cron` (기본값: 매일 18:30)
- **집계 대상**: 어제 날짜 (DailyStatistics)
- **집계 항목**: 신규 가입자, 게시글, 케어 요청, 완료된 케어, DAU, 신규 모임, 모임 참여, 신고 접수
- **중복 방지**: `findByStatDate()`로 이미 집계된 날짜 건너뜀

#### 3. 제재 자동 해제
**위치**: `domain/user/scheduler/UserSanctionScheduler.java`
- **스케줄**: 매일 자정 `@Scheduled(cron = "0 0 0 * * *")`
- 만료된 이용제한(SUSPENDED) 자동 해제

#### 4. 케어 요청 만료 처리
**위치**: `domain/care/service/CareRequestScheduler.java`
- **매시간 정각**: 만료된 케어 요청 상태 변경
- **매일 자정**: 정리 작업

---

## Q2-5. Java 17의 주요 특징과 프로젝트에서 활용한 부분은?

### 답변 포인트
- Stream API 활용
- Optional 활용
- Lambda 표현식
- Records, Pattern Matching (향후 활용 가능)

### 상세 답변

#### 1. Stream API
**위치**: 모든 Service 클래스에서 광범위하게 사용

**사용 예시**:
```java
// domain/board/service/BoardService.java
List<BoardDTO> result = boards.stream()
    .map(boardConverter::toDTO)
    .collect(Collectors.toList());

// domain/activity/service/ActivityService.java
List<ActivityDTO> filtered = activities.stream()
    .filter(a -> isPostType(a.getType()))
    .collect(Collectors.toList());
```

#### 2. Optional
**위치**: Repository 메서드 반환 타입, `orElseThrow()` 패턴

**사용 예시**:
```java
// domain/user/repository/UsersRepository.java
Optional<Users> findByEmail(String email);

// domain/board/service/BoardService.java
Board board = boardRepository.findByIdWithUser(idx)
    .orElseThrow(() -> new BoardNotFoundException());
```

#### 3. Lambda 표현식
**위치**: Stream API, `orElseThrow()`, 이벤트 핸들러 등

**사용 예시**:
```java
boards.stream()
    .filter(b -> !b.getIsDeleted())
    .map(boardConverter::toDTO)
    .collect(Collectors.toList());
```

#### 4. Java 17+ 특징 (활용/향후)
- **Records**: DTO 대체 가능 (현재는 Lombok `@Builder` 사용)
- **Pattern Matching for instanceof**: 타입 체크 및 캐스팅 간소화
- **Text Blocks**: SQL/JSON 문자열 작성 시 활용 가능

---

## Q2-6. (보너스) 전역 예외 처리는 어떻게 구성했나요?

### 답변 포인트
- `@RestControllerAdvice` + `@ExceptionHandler`
- `ApiException` 상속 예외: 일관된 형식 (status, errorCode, message)
- 도메인별 구체 예외: `BoardNotFoundException`, `UserNotFoundException`, `FileValidationException` 등

### 상세 답변

**위치**: `global/exception/GlobalExceptionHandler.java`

**처리 대상**:
- `ApiException` 및 하위 클래스: HTTP status, errorCode, message 반환
- `IllegalArgumentException`: 400 Bad Request
- `AuthorizationDeniedException`: 403 Forbidden (SSE 등)
- `AsyncRequestTimeoutException`: SSE 타임아웃 (정상 동작)
- `EmailVerificationRequiredException`: 이메일 인증 필수

**도메인별 예외 예시**:
- `domain/board/exception/`: BoardNotFoundException, BoardConflictException
- `domain/user/exception/`: UserNotFoundException, PetNotFoundException
- `domain/file/exception/`: FileValidationException, FileUploadValidationException, FileNotFoundException, FileStorageException

---

## 📝 핵심 정리

### Spring Boot 활용
- **Auto Configuration**: `@SpringBootApplication`
- **Spring Data JPA**: Repository 인터페이스 + Jpa*Adapter + SpringDataJpa*Repository
- **Spring Security**: JWT 필터 + OAuth2 + SecurityConfig
- **Spring Scheduler**: `@Scheduled` (인기글, 통계, 제재 해제, 케어 만료)
- **Spring Cache**: Redis (`@Cacheable`, `@CacheEvict`)

### 트랜잭션 관리
- **AOP 기반**: 프록시 패턴
- **격리 수준**: MySQL 기본 REPEATABLE_READ
- **전파 속성**: REQUIRED
- **읽기 전용**: `@Transactional(readOnly = true)`

### 필터 체인
- **JwtAuthenticationFilter**: 토큰 추출(헤더/쿼리) → 검증 → SecurityContext 저장
- **SecurityConfig**: 경로별 permitAll / admin / authenticated
- **OAuth2**: Google, Naver 소셜 로그인

### 스케줄러
- **인기글 스냅샷**: 매일 18:30, 매주 월요일 18:30
- **통계 집계**: 매일 18:30 (properties 설정 가능)
- **제재 해제**: 매일 자정
- **케어 만료**: 매시간 정각, 매일 자정
