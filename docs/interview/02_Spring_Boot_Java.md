# 2. Spring Boot & Java

## Q2-1. Spring Boot의 주요 특징과 이 프로젝트에서 활용한 부분을 설명해주세요.

### 답변 포인트
- Auto Configuration으로 설정 자동화
- Spring Data JPA로 데이터 액세스 추상화
- Spring Security로 인증/인가 처리
- Spring Scheduler로 주기적 작업 자동화
- Spring Cache로 캐싱 전략 구현

### 상세 답변

#### 1. Auto Configuration
**위치**: `PetoryApplication.java`
- `@SpringBootApplication` 어노테이션으로 자동 설정 활성화
- Spring Boot 3.5.7 버전 사용
- DataSource, JPA, Security 등 자동 설정

**전체 흐름**:
```
@SpringBootApplication
  ↓
자동 설정 스캔
  ↓
DataSource 자동 구성 (application.properties 기반)
JPA 자동 구성 (Hibernate)
Security 자동 구성
```

#### 2. Spring Data JPA
**위치**: `domain/*/repository/SpringDataJpa*Repository.java`
- JpaRepository 인터페이스 상속으로 기본 CRUD 제공
- 쿼리 메서드 네이밍 컨벤션 활용
- `@Query` 어노테이션으로 커스텀 쿼리 작성

**예시**:
```java
// domain/board/repository/SpringDataJpaBoardRepository.java
public interface SpringDataJpaBoardRepository extends JpaRepository<Board, Long> {
    @Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false")
    List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();
}
```

#### 3. Spring Security
**위치**: `global/security/SecurityConfig.java`, `filter/JwtAuthenticationFilter.java`
- JWT 기반 인증 구현
- 필터 체인 구성
- `@EnableMethodSecurity`로 메서드 레벨 권한 체크

**전체 흐름**:
```
요청 → JwtAuthenticationFilter.doFilterInternal()
  ↓
토큰 추출 및 검증
  ↓
SecurityContext에 인증 정보 저장
  ↓
SecurityConfig에서 경로별 권한 체크
```

#### 4. Spring Scheduler
**위치**: `domain/*/service/*Scheduler.java`
- `@EnableScheduling` 활성화 (PetoryApplication)
- `@Scheduled` 어노테이션으로 주기적 작업 정의

**구현 사례**:
- **BoardPopularityScheduler**: 매일 18:30, 매주 월요일 18:30 인기글 스냅샷 생성
- **StatisticsScheduler**: 매일 18:30 통계 집계
- **UserSanctionScheduler**: 매일 자정 제재 자동 해제
- **CareRequestScheduler**: 매시간 만료 처리, 매일 자정 정리

**코드 예시**:
```java
// domain/board/service/BoardPopularityScheduler.java
@Scheduled(cron = "0 30 18 * * ?")
@Transactional
public void generateWeeklyPopularitySnapshots() {
    boardPopularityService.generateSnapshots(PopularityPeriodType.WEEKLY);
}
```

#### 5. Spring Cache
**위치**: `PetoryApplication.java` (`@EnableCaching`)
- Redis 기반 캐싱
- `@Cacheable`, `@CacheEvict` 어노테이션 활용

**사용 예시**:
```java
// domain/location/service/LocationServiceService.java
@Cacheable(value = "popularLocationServices", key = "#category")
public List<LocationServiceDTO> getPopularLocationServices(String category) {
    // ...
}
```

---

## Q2-2. @Transactional 어노테이션의 동작 원리와 격리 수준을 설명해주세요.

### 답변 포인트
- AOP 기반으로 트랜잭션 관리
- 기본 격리 수준: READ_COMMITTED
- 전파 속성: REQUIRED (기본값)
- 읽기 전용 트랜잭션: @Transactional(readOnly = true)

### 상세 답변

#### 1. 동작 원리 (AOP)
**위치**: 모든 Service 클래스
- Spring AOP 프록시를 통해 트랜잭션 관리
- 메서드 실행 전 트랜잭션 시작
- 메서드 실행 후 커밋 또는 롤백

**전체 흐름**:
```
@Transactional 메서드 호출
  ↓
AOP 프록시가 트랜잭션 시작
  ↓
메서드 실행
  ↓
성공 → 커밋
실패 → 롤백
```

#### 2. 격리 수준
**기본값**: READ_COMMITTED
- 다른 트랜잭션의 커밋된 데이터만 읽기
- Dirty Read 방지
- Phantom Read, Non-repeatable Read 가능

**사용 예시**:
```java
// domain/board/service/BoardService.java
@Service
@Transactional(readOnly = true)  // 클래스 레벨 기본값
public class BoardService {
    
    @Transactional  // 쓰기 작업은 별도 트랜잭션
    public BoardDTO createBoard(BoardDTO boardDTO) {
        // ...
    }
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
    // REQUIRED: 기존 트랜잭션에 참여
    usersRepository.incrementWarningCount(userId);
}
```

---

## Q2-3. Spring Security의 필터 체인을 설명해주세요.

### 답변 포인트
- JwtAuthenticationFilter: JWT 토큰 검증
- SecurityConfig: 인증/인가 설정
- 필터 순서와 역할
- 공개 API와 보호된 API 구분

### 상세 답변

#### 1. 필터 체인 구성
**위치**: `global/security/SecurityConfig.java`, `filter/JwtAuthenticationFilter.java`

**필터 순서**:
```
1. JwtAuthenticationFilter (OncePerRequestFilter 상속)
2. SecurityConfig의 필터 체인
   - 공개 API: permitAll()
   - 보호된 API: authenticated()
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
Authorization 헤더에서 토큰 추출
  ↓
JwtUtil.validateToken() 검증
  ↓
UserDetailsService.loadUserByUsername() 사용자 조회
  ↓
SecurityContext에 인증 정보 저장
  ↓
다음 필터로 전달
```

**코드 예시**:
```java
// filter/JwtAuthenticationFilter.java
@Override
protected void doFilterInternal(HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) {
    String token = jwtUtil.extractTokenFromHeader(authorizationHeader);
    
    if (token != null && jwtUtil.validateToken(token)) {
        String id = jwtUtil.getIdFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(id);
        
        UsernamePasswordAuthenticationToken authToken = 
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
    
    filterChain.doFilter(request, response);
}
```

#### 3. SecurityConfig
**위치**: `global/security/SecurityConfig.java`
- 경로별 권한 설정
- 공개 API: `/api/auth/**`, `/api/public/**`
- 보호된 API: 나머지 모든 경로

---

## Q2-4. Spring Scheduler를 어떤 용도로 사용했나요?

### 답변 포인트
- 일별 통계 집계 (Statistics Scheduler)
- 인기글 스냅샷 생성 (Popularity Scheduler)
- 제재 자동 해제 (Sanction Scheduler)
- 케어 요청 만료 처리 (Care Scheduler)

### 상세 답변

#### 1. 인기글 스냅샷 생성
**위치**: `domain/board/service/BoardPopularityScheduler.java`
**메서드**: 
- `generateWeeklyPopularitySnapshots()` - 매일 18:30
- `generateMonthlyPopularitySnapshots()` - 매주 월요일 18:30

**전체 흐름**:
```
@Scheduled(cron = "0 30 18 * * ?")
  ↓
BoardPopularityScheduler.generateWeeklyPopularitySnapshots()
  ↓
BoardPopularityService.generateSnapshots(PopularityPeriodType.WEEKLY)
  ↓
인기글 계산 및 스냅샷 저장
```

**코드 예시**:
```java
@Scheduled(cron = "0 30 18 * * ?")
@Transactional
public void generateWeeklyPopularitySnapshots() {
    boardPopularityService.generateSnapshots(PopularityPeriodType.WEEKLY);
}
```

#### 2. 통계 집계
**위치**: `domain/statistics/service/StatisticsScheduler.java`
**스케줄**: 매일 18:30 (application.properties에서 설정 가능)

#### 3. 제재 자동 해제
**위치**: `domain/user/scheduler/UserSanctionScheduler.java`
**스케줄**: 매일 자정 (`@Scheduled(cron = "0 0 0 * * *")`)

#### 4. 케어 요청 만료 처리
**위치**: `domain/care/service/CareRequestScheduler.java`
**스케줄**: 
- 매시간 정각: 만료 처리
- 매일 자정: 정리 작업

---

## Q2-5. Java 17의 주요 특징과 프로젝트에서 활용한 부분은?

### 답변 포인트
- Records, Pattern Matching, Sealed Classes
- Stream API 활용
- Optional 활용
- Lambda 표현식

### 상세 답변

#### 1. Stream API
**위치**: 모든 Service 클래스에서 광범위하게 사용

**사용 예시**:
```java
// domain/board/service/BoardService.java
List<BoardDTO> result = boards.stream()
    .map(boardConverter::toDTO)
    .collect(Collectors.toList());
```

#### 2. Optional
**위치**: Repository 메서드 반환 타입

**사용 예시**:
```java
// domain/user/repository/UsersRepository.java
Optional<Users> findByEmail(String email);
```

#### 3. Lambda 표현식
**위치**: Stream API와 함께 사용

**사용 예시**:
```java
boards.stream()
    .filter(board -> board.getIsDeleted() == false)
    .map(board -> boardConverter.toDTO(board))
    .collect(Collectors.toList());
```

#### 4. Java 17 특징
- **Text Blocks**: SQL 쿼리 작성 시 활용 가능
- **Pattern Matching for instanceof**: 타입 체크 및 캐스팅 간소화
- **Records**: DTO 클래스 대체 가능 (현재는 Lombok 사용)

---

## 📝 핵심 정리

### Spring Boot 활용
- **Auto Configuration**: `@SpringBootApplication`
- **Spring Data JPA**: Repository 인터페이스
- **Spring Security**: JWT 필터 + SecurityConfig
- **Spring Scheduler**: `@Scheduled` 어노테이션
- **Spring Cache**: Redis 캐싱

### 트랜잭션 관리
- **AOP 기반**: 프록시 패턴
- **격리 수준**: READ_COMMITTED
- **전파 속성**: REQUIRED
- **읽기 전용**: `@Transactional(readOnly = true)`

### 필터 체인
- **JwtAuthenticationFilter**: 토큰 검증
- **SecurityConfig**: 경로별 권한 설정
- **공개/보호 API**: permitAll() vs authenticated()

### 스케줄러
- **인기글 스냅샷**: 매일 18:30, 매주 월요일 18:30
- **통계 집계**: 매일 18:30
- **제재 해제**: 매일 자정
- **케어 만료**: 매시간 정각
