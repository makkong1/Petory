# Petory 아키텍처 개요

## 전체 시스템 구조

```
┌─────────────────────────────────────────────────────────┐
│                     Frontend (React)                     │
│                   - SPA (Single Page App)                │
│                   - REST API 통신                         │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP/HTTPS
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  Spring Boot Backend                     │
│                                                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │           Security Layer (JWT)                   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Controller Layer                     │   │
│  │  (REST API, Request/Response 처리)                │   │
│  └──────────────────────────────────────────────────┘   │
│                                                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │               Service Layer                       │   │
│  │  (비즈니스 로직, 트랜잭션 관리)                   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │             Repository Layer                      │   │
│  │  (Spring Data JPA, 쿼리 메서드)                   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                           │
└────────────────────┬────────────────────────────────────┘
                     │ JDBC
                     ▼
┌─────────────────────────────────────────────────────────┐
│                    MySQL Database                        │
│              (관계형 데이터베이스)                        │
└─────────────────────────────────────────────────────────┘
```

## 도메인 구조

Petory 백엔드는 **도메인 주도 설계(DDD)** 원칙을 따라 도메인별로 패키지를 구성합니다.

### 도메인 분류

#### 1. 핵심 비즈니스 도메인

```
domain/
├── user/           # 사용자 및 반려동물 관리
├── board/          # 커뮤니티 게시판
├── care/           # 펫케어 요청/지원
├── payment/        # 펫코인 결제, 에스크로
├── chat/           # 실시간 채팅 (Conversation, ChatMessage)
├── location/       # 위치 기반 서비스
├── meetup/         # 오프라인 모임
├── report/         # 신고 및 제재
└── notification/   # 알림
```

#### 2. 지원 도메인

```
domain/
├── file/           # 파일 관리
├── activity/       # 활동 로그
├── statistics/     # 통계
└── admin/          # 관리자 API (Admin* Controller)
```

### 각 도메인의 표준 구조

```
domain/[domain-name]/
├── controller/     # REST API 엔드포인트
├── service/        # 비즈니스 로직
├── repository/     # 데이터 액세스
├── entity/         # JPA 엔티티
├── dto/            # 데이터 전송 객체
├── converter/      # Entity ↔ DTO 변환
└── scheduler/      # (옵션) 스케줄링 작업
```

## 레이어별 책임

### 1. Controller Layer
- **책임**: HTTP 요청/응답 처리
- **주요 작업**:
  - 요청 파라미터 검증
  - Service 호출
  - 응답 포맷 변환
  - 예외 처리
- **어노테이션**: `@RestController`, `@RequestMapping`

### 2. Service Layer
- **책임**: 비즈니스 로직 구현
- **주요 작업**:
  - 도메인 로직 실행
  - 트랜잭션 관리 (`@Transactional`)
  - 여러 Repository 조율
  - 도메인 간 협업
- **어노테이션**: `@Service`, `@Transactional`

### 3. Repository Layer
- **책임**: 데이터 액세스
- **주요 작업**:
  - CRUD 연산
  - 커스텀 쿼리 메서드
  - JPA 쿼리 작성
- **인터페이스**: `JpaRepository<Entity, ID>`

### 4. Entity Layer
- **책임**: 도메인 모델 정의
- **주요 작업**:
  - 테이블 매핑
  - 연관관계 정의
  - 비즈니스 메서드 (도메인 로직)
- **어노테이션**: `@Entity`, `@Table`, `@ManyToOne`, 등

### 5. DTO Layer
- **책임**: 데이터 전송
- **주요 작업**:
  - API 요청/응답 객체
  - 불필요한 정보 노출 방지
  - 순환 참조 방지
- **어노테이션**: `@Data`, `@Builder`

### 6. Converter Layer
- **책임**: Entity ↔ DTO 변환
- **주요 작업**:
  - 변환 로직 중앙화
  - 매핑 로직 재사용
- **어노테이션**: `@Component`

## 크로스 커팅 관심사 (Cross-Cutting Concerns)

### 1. 보안 (Security)
- **위치**: `global/security/`, `filter/`
- **기능**:
  - JWT 인증/인가
  - Spring Security 설정
  - 권한 검사
  - CORS 처리

### 2. 예외 처리 (Exception Handling)
- **위치**: `global/exception/`
- **기능**:
  - 전역 예외 핸들러
  - 커스텀 예외 정의
  - 일관된 에러 응답

### 3. 설정 (Configuration)
- **위치**: `global/security/` (RedisConfig, SecurityConfig 등), `application.properties`
- **기능**:
  - JPA 설정
  - 캐시 설정
  - 스케줄러 설정
  - 파일 업로드 설정

## 주요 기술 스택 상세

### Spring Boot 기능 활용

#### 1. Spring Data JPA
- **쿼리 메서드**: 메서드 이름으로 쿼리 자동 생성
- **@Query**: 복잡한 쿼리는 JPQL/Native Query 사용
- **페이징**: `Pageable`과 `Page` 활용

```java
Page<Board> findByCategoryAndIsDeletedFalse(
    String category, 
    Pageable pageable
);
```

#### 2. Spring Cache
- **@Cacheable**: 조회 결과 캐싱
- **@CacheEvict**: 캐시 무효화
- **캐시 전략**: 게시글 상세, 인기글 목록 등

```java
@Cacheable(value = "boardDetail", key = "#idx")
public BoardDTO getBoard(long idx) { ... }
```

#### 3. Spring Scheduler
- **@Scheduled**: 주기적 작업 실행
- **Cron 표현식**: 정확한 시간 설정
- **용도**: 인기글 스냅샷, 만료 요청 처리

```java
@Scheduled(cron = "0 30 18 * * ?")
public void generateWeeklyPopularitySnapshots() { ... }
```

#### 4. Spring Async
- **@Async**: 비동기 메서드 실행
- **@EnableAsync**: 비동기 기능 활성화
- **용도**: 알림 발송, 통계 수집 등

#### 5. Spring Security
- **JWT 인증**: 토큰 기반 인증
- **권한 검사**: `@PreAuthorize`
- **필터 체인**: 인증/인가 필터

### JPA/Hibernate 최적화

#### 1. Fetch 전략
- **LAZY**: 지연 로딩 (기본값)
- **EAGER**: 즉시 로딩 (필요 시만)

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_idx")
private Users user;
```

#### 2. 영속성 컨텍스트
- **1차 캐시**: 같은 트랜잭션 내 중복 조회 방지
- **변경 감지**: Dirty Checking
- **쓰기 지연**: Batch Insert/Update

#### 3. 연관관계 관리
- **양방향 관계**: 순환 참조 주의
- **Cascade**: 영속성 전이
- **OrphanRemoval**: 고아 객체 제거

```java
@OneToMany(mappedBy = "board", cascade = CascadeType.ALL)
private List<Comment> comments;
```

## 데이터 흐름

### 조회 요청 플로우

```
Client Request
    ↓
Controller (요청 검증)
    ↓
Service (비즈니스 로직)
    ↓
Repository (DB 조회)
    ↓
Entity → Converter → DTO
    ↓
Controller (응답 반환)
    ↓
Client Response
```

### 생성/수정 요청 플로우

```
Client Request (DTO)
    ↓
Controller (요청 검증)
    ↓
Service (@Transactional 시작)
    ↓
Converter (DTO → Entity)
    ↓
Repository (DB 저장)
    ↓
@Transactional 커밋
    ↓
Cache Eviction (캐시 무효화)
    ↓
Entity → Converter → DTO
    ↓
Controller (응답 반환)
    ↓
Client Response
```

## 트랜잭션 관리

### 기본 원칙
- **읽기 전용**: `@Transactional(readOnly = true)` 사용
- **쓰기 작업**: `@Transactional` 사용
- **격리 수준**: 기본값 (READ_COMMITTED) 사용
- **전파 수준**: 기본값 (REQUIRED) 사용

### 트랜잭션 경계
- **Service 메서드 단위**: 하나의 비즈니스 로직 = 하나의 트랜잭션
- **예외 발생 시 롤백**: RuntimeException 발생 시 자동 롤백

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본 읽기 전용
public class BoardService {
    
    @Transactional  // 쓰기 작업만 명시
    public BoardDTO createBoard(BoardDTO dto) {
        // ...
    }
}
```

## 캐시 전략

### 캐시 대상
1. **게시글 상세**: 조회 빈도 높음
2. **인기글 목록**: 계산 비용 높음
3. **위치 서비스 목록**: 변경 빈도 낮음

### 캐시 무효화 시점
- 데이터 생성/수정/삭제 시
- 관련 데이터 변경 시 (예: 댓글 추가 → 게시글 캐시 무효화)

### 주의사항
- 캐시 일관성 유지
- 메모리 사용량 모니터링
- 적절한 만료 시간 설정

## 성능 고려사항

### 1. N+1 문제 해결
- **배치 조회**: IN 절로 한 번에 조회
- **페치 조인**: JPQL의 `JOIN FETCH`
- **DTO 프로젝션**: 필요한 컬럼만 조회

### 2. 인덱스 최적화
- **복합 인덱스**: 자주 함께 조회되는 컬럼
- **커버링 인덱스**: 인덱스만으로 쿼리 완성
- **인덱스 힌트**: 복잡한 쿼리에서 명시

### 3. 쿼리 최적화
- **페이징**: 대량 데이터 조회 시 필수
- **조건 최적화**: WHERE 절 인덱스 활용
- **조인 최소화**: 불필요한 조인 제거

## 보안 고려사항

### 1. 인증/인가
- JWT 토큰 검증
- 리프레시 토큰 관리
- 권한별 접근 제어

### 2. 데이터 보호
- 비밀번호 암호화 (BCrypt)
- 개인정보 마스킹
- 소프트 삭제 (완전 삭제 방지)

### 3. 입력 검증
- DTO 레벨 검증 (`@Valid`)
- SQL Injection 방지 (JPA 파라미터 바인딩)
- XSS 방지 (입력 sanitization)

## 확장성 고려사항

### 수평 확장 (Scale-Out)
- **무상태 서버**: 세션 정보는 DB/Redis에 저장
- **로드 밸런싱**: 여러 인스턴스로 트래픽 분산
- **분산 캐시**: Redis 등 외부 캐시 사용

### 수직 확장 (Scale-Up)
- **DB 성능 향상**: 인덱싱, 쿼리 최적화
- **JVM 튜닝**: 힙 크기, GC 설정
- **커넥션 풀**: HikariCP 설정 최적화

### 마이크로서비스 전환 가능성
- 도메인별 독립 배포 가능
- API Gateway 도입
- 서비스 간 통신 (REST/gRPC)

