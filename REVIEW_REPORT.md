# Petory 코드 리뷰 보고서

> 작성일: 2026-04-10  
> 분석 대상: Spring Boot 3.5.7 (Java 17) 백엔드 + React 19 프론트엔드

---

## 종합 등급 요약

| 팀원 | 담당 영역 | 등급 |
|------|----------|------|
| 이보안 | 보안 (Security) | **D** |
| 김성능 | 성능 (Performance) | **B** |
| 박스타일 | 코드 스타일 (Style) | **C** |
| | **전체 평균** | **C+** |

---

## 보안 평가 보고서 (팀원: 이보안)

### 종합 등급: D (위험 수준)

### 🔴 위험 (Critical)

1. **하드코딩된 민감 정보 노출**
   - API 키, OAuth2 Secret, 이메일 비밀번호, JWT Secret, Redis 비밀번호가 설정 파일에 평문 저장
   - `backend/main/resources/application.properties:45, 50-51, 97-98, 104-105, 129-130, 82`
   - 영향: 소스 코드 유출 시 외부 서비스 침해, 데이터베이스 접근 가능

2. **CORS 설정 오류 — 모든 origin 허용**
   - 모든 출처(`*`)에서 모든 메서드/헤더 허용, 자격증명(credentials) 활성화 동시 설정
   - `global/security/SecurityConfig.java:108-111`
   - 영향: CSRF 공격, 세션 하이재킹, 크로스사이트 요청 조작 가능

3. **쿼리 파라미터를 통한 JWT 토큰 전송**
   - JWT 토큰을 URL 쿼리 파라미터(`?token=xxx`)로 허용
   - `filter/JwtAuthenticationFilter.java:44-45`
   - 영향: 브라우저 히스토리/로그에 토큰 기록, 중간자 공격, 토큰 탈취 위험

4. **permitAll() vs SecurityConfig catch-all 충돌**
   - `GET /api/boards`에 `@PreAuthorize("permitAll()")` 설정이 있으나, SecurityConfig의 `/api/**` catch-all이 `authenticated()` 요구하여 실제로 인증이 강제됨
   - `global/security/SecurityConfig.java:78`, `domain/board/controller/BoardController.java:44,54,62`

5. **입력 검증 누락 (@Valid 부재)**
   - 컨트롤러에 `@Valid` 애노테이션 없음 — 악의적 입력 검증 불가
   - `domain/board/controller/BoardController.java:72,79,124,127`
   - `domain/care/controller/CareRequestController.java:71,78`
   - 영향: XSS, 버퍼 오버플로우 등

6. **LIKE 쿼리 입력 길이/특수문자 검증 부재**
   - `domain/location/repository/SpringDataJpaLocationServiceRepository.java:33-37`
   - `domain/care/repository/SpringDataJpaCareRequestRepository.java:111`
   - 영향: 정규표현식 DoS, DB 성능 저하

### 🟠 경고 (Warning)

1. **IDOR — 다른 사용자 프로필 열람 가능**
   - `/api/users/{userId}/profile` 엔드포인트가 인증만 확인, 대상 사용자 동일 여부 미확인
   - `domain/user/controller/UserProfileController.java:265-278`
   - 다른 사용자의 이메일, 전화, 주소 등 민감 정보 열람 가능

2. **예외 핸들러에서 내부 정보 노출 가능**
   - `e.getMessage()` 직접 응답 → 프로덕션에서 내부 스택 정보 노출 위험
   - `global/exception/GlobalExceptionHandler.java:151`

3. **show-sql=true 설정 — SQL 로그에 민감 데이터 노출**
   - `backend/main/resources/application.properties:24`
   - 프로덕션에서 `false`로 변경 필요

4. **Refresh Token 블랙리스트 부재**
   - 로그아웃 후에도 만료 전까지 탈취된 Refresh Token으로 재발급 가능

5. **WebSocket 핸드셰이크 인증 방식 불명확**
   - `global/websocket/config/WebSocketConfig.java:37-41`
   - 쿠키/세션 인증 우선 후 JWT 폴백 구조가 모호함

6. **System.out.println 프로덕션 코드 잔존**
   - `domain/care/controller/CareRequestController.java:72,100`

### ✅ 잘된 점

- 비관적 락으로 펫코인 동시성 제어 (`domain/payment/service/PetCoinService.java:50,97,152,201`)
- BCrypt 비밀번호 암호화 (`global/security/PasswordEncoderConfig.java:12-14`)
- Access Token 만료 시간 15분 (`util/JwtUtil.java:23`)
- JPQL 파라미터 바인딩으로 SQL Injection 방지
- JWT HMAC-SHA 서명 사용, 세션 비활성화(STATELESS) 설정

### 개선 권고 (우선순위)

```
1. [즉시] 모든 민감 정보 → 환경변수 또는 Secrets Manager로 이동
   jwt.secret=${JWT_SECRET}, spring.datasource.password=${DB_PASSWORD}

2. [즉시] CORS에서 특정 도메인만 허용
   setAllowedOriginPatterns(Arrays.asList("https://yourdomain.com"))

3. [즉시] 쿼리 파라미터 토큰 수신 코드 제거 (Authorization 헤더만 허용)

4. [즉시] SecurityConfig URL 패턴 명시적으로 정리 (permitAll 경로 우선 선언)

5. [단기] 모든 @RequestBody에 @Valid 추가 + DTO에 Bean Validation 애노테이션

6. [단기] UserProfileController IDOR 수정 — 공개/비공개 필드 분리

7. [단기] Refresh Token Redis 블랙리스트 구현

8. [중기] LIKE 쿼리 입력 길이 제한 (100자 이하)

9. [중기] show-sql=false (운영 환경)

10. [중기] 비밀번호 복잡도 정책 추가
```

---

## 성능 평가 보고서 (팀원: 김성능)

### 종합 등급: B

### 🔴 심각 (Critical)

1. **MissingPetBoard Entity FetchType 미지정 — N+1 쿼리 위험**
   - `@ManyToOne`에 FetchType 미지정 (기본값 EAGER), 게시글 조회마다 User 즉시 로딩
   - `domain/board/entity/MissingPetBoard.java:45-47`

2. **NotificationEntity FetchType 미지정 — 대량 알림 조회 시 N+1**
   - `domain/notification/entity/Notification.java:37-39`

3. **관리자 게시글 조회에서 전체 메모리 로드 후 필터링**
   - `BoardService.getAdminBoardsWithPaging()`가 전체 게시글을 List로 메모리에 로드 후 필터링
   - `domain/board/service/BoardService.java:84-107`
   - 데이터 증가 시 OOM 위험 및 응답 시간 급증

### 🟠 개선 필요 (Major)

1. **Notification Redis/DB 병합 로직 비효율**
   - Redis와 DB에서 각각 조회 후 merge하는 이중 쿼리 구조
   - `domain/notification/service/NotificationService.java:79-100`

2. **SSE 연결 누수 가능성**
   - 네트워크 강제 단절 시 ConcurrentHashMap의 연결이 정리되지 않을 수 있음
   - `domain/notification/service/NotificationSseService.java:19-45`

3. **Board 게시글 캐시 비활성화 상태**
   - `BoardService.getAllBoards()`의 `@Cacheable`이 주석 처리 상태
   - `domain/board/service/BoardService.java:54-58`
   - 반복 조회마다 DB 쿼리 발생

4. **Location Repository에서 SELECT * 사용**
   - native query로 전체 컬럼 조회 — 불필요한 대용량 컬럼 포함
   - `domain/location/repository/SpringDataJpaLocationServiceRepository.java:19-84`

5. **CareRequest 페이징 + 컬렉션 FETCH JOIN 조합 문제**
   - 카르테시안 곱 발생 가능
   - `domain/care/repository/SpringDataJpaCareRequestRepository.java:34-35`

### 🟡 검토 권장 (Minor)

1. **ConversationService 4개 배치 쿼리 순차 실행** — 병렬화 가능 (`domain/chat/service/ConversationService.java:68-135`)
2. **비관적 락 대기 시간 모니터링** — 동시 결제 수 증가 시 응답 지연 가능 (`domain/payment/service/PetCoinService.java:49-77`)
3. **CareRequest @BatchSize(50)과 FETCH JOIN 중복 설정** (`domain/care/entity/CareRequest.java:79-81`)

### ✅ 잘된 점

- 주요 엔티티(Board, ChatMessage 등) FetchType.LAZY 설정
- 비관적 락으로 코인/에스크로 동시성 제어 (`domain/payment/service/PetCoinEscrowService.java:88-120`)
- StatisticsService에 @Cacheable 적용으로 관리자 대시보드 부하 감소 (`domain/statistics/service/StatisticsService.java:93-121`)
- fetch join을 통한 명시적 1회 쿼리 보장
- ConversationService 배치 조회로 N+1 방지

### 개선 권고 (우선순위)

| 우선순위 | 항목 | 예상 효과 | 난이도 |
|----------|------|----------|--------|
| 1 | MissingPetBoard/Notification FetchType.LAZY 명시 | N+1 제거, 20-30% 향상 | 낮음 |
| 2 | 관리자 조회 Specification 기반 DB 필터링 | OOM 방지, 응답 50% 단축 | 중간 |
| 3 | Location Projection 도입 | 네트워크 30-50% 감소 | 중간 |
| 4 | Notification Redis Sorted Set 전략 | 알림 QPS 3배 | 중간 |
| 5 | SSE 하트비트 + 주기적 연결 정리 | 메모리 누수 방지 | 낮음 |
| 6 | ConversationService CompletableFuture 병렬화 | 응답 단축 | 낮음 |
| 7 | Board @Cacheable 재활성화 (조회수 비동기 처리) | DB 부하 감소 | 중간 |

> 권고사항 반영 시 전체 응답 시간 30-40% 단축, DB 쿼리 부하 20-30% 감소 예상

---

## 코드 스타일 평가 보고서 (팀원: 박스타일)

### 종합 등급: C

### 백엔드 발견 사항

#### 🔴 심각한 문제

1. **System.out.println 디버그 코드 잔존**
   - `domain/care/controller/CareRequestController.java:72,100`
   - 로깅은 `log.info()` / `log.debug()`로 통일 필요

2. **Magic Number/String 하드코딩**
   - `"ROLE_ADMIN"`, `"ROLE_MASTER"` 문자열 반복 사용 (`domain/care/service/CareRequestService.java:63`)
   - 페이징 기본값(size=20) 여러 곳 분산

#### 🟠 개선 필요

1. **Service 클래스의 과도한 책임 (God Object)**
   - `CareRequestService` (362줄): 조회·생성·수정·삭제·상태변경·검색·권한검증 모두 담당
   - `UsersService`, `MissingPetBoardService` 동일 문제

2. **권한 검증 로직 중복 분산**
   - `isAdmin()` 메서드가 여러 Service에 중복 구현
   - `getCurrentUserId()` 메서드가 Controller마다 반복 구현 (`CareRequestController.java:30-42`)

3. **응답 형태 불일치**
   - 일부는 `ResponseEntity.ok()`, 일부는 직접 반환
   - DTO 변환 방식이 Converter 사용 / 수동 매핑으로 혼재

4. **이메일 인증 확인 로직 중복**
   - 여러 Service에서 동일한 이메일 인증 확인 코드 반복

#### 🟡 권장 개선

1. **주석 부족** — CareRequestService 상태 변경 로직(에스크로 처리, 결제 실패 롤백) 설명 주석 없음 (275-347줄)
2. **@Deprecated 방치** — `CareRequestRepository.java:85-86`
3. **주석 처리된 미사용 코드** — `UsersService.java:47`의 `// private final PetService petService;`

#### ✅ 잘된 점

- Controller → Service → Repository → Entity 4계층 분리 일관성
- 도메인별 커스텀 예외 (CareValidationException, UserNotFoundException 등) 체계화
- Repository 도메인 인터페이스 + JPA 어댑터 분리로 추상화 구현
- Entity → DTO 변환 Converter 패턴 사용
- Slf4j 구조화 로깅 일관 적용

---

### 프론트엔드 발견 사항

#### 🔴 심각한 문제

1. **console.log 디버그 코드 대량 잔존**
   - `frontend/src/components/Chat/ChatRoom.js:154,163,185` 등 다수
   - 중앙화된 로거 또는 dev 모드 한정 활성화 필요

2. **localStorage 토큰 저장 — XSS 취약**
   - `apiClient.js`에서 localStorage에 직접 접근 (getToken, setToken)
   - HttpOnly 쿠키 또는 메모리 저장소로 전환 권고

3. **ChatRoom 컴포넌트 상태 과부화**
   - `useState` 20개 이상 선언 — 컴포넌트 분해 및 상태 관리 라이브러리 도입 필요

#### 🟠 개선 필요

1. **거대 컴포넌트 (300줄 초과)**
   - `ChatRoom.js` 600줄 이상: 메시지·이미지·거래·리뷰 기능 혼재
   - `RegisterForm.js` 200줄 이상: 회원가입 + 반려동물 관리 혼재

2. **useEffect 의존성 배열 문제**
   - `ChatRoom.js:125-130` — `fetchConversation()`, `fetchMessages()` 등 의존성 명확화 필요

3. **Error Boundary 미구현**
   - 컴포넌트 오류 시 전체 앱 먹통 위험

4. **API 오류 처리 불균형**
   - 401/403만 처리, 5xx 오류나 네트워크 오류 일관 처리 미흡

#### ✅ 잘된 점

- AuthContext/ThemeContext 명확 분리
- `apiClient.js` 인터셉터로 토큰 자동 주입 및 401 refresh 처리
- WebSocket stompClientRef cleanup 구현 (`ChatRoom.js:207-212`)
- styled-components로 props 기반 동적 스타일링

### 개선 권고 (우선순위)

```
백엔드:
1. [즉시] System.out.println → log.info() 전환
2. [즉시] Magic String → RoleConstants/Enum 상수화
3. [단기] getCurrentUserId() / isAdmin() 중앙화 (SecurityUtil, @Aspect AOP)
4. [단기] CareRequestService → CareApplicationService + CareReviewService + CareAuthorizationService 분리
5. [중기] 응답 형태 ResponseEntity 통일, DTO 변환 Converter 일원화

프론트엔드:
1. [즉시] console.log 제거 또는 dev 모드 한정 활성화
2. [즉시] localStorage → HttpOnly 쿠키 또는 메모리 저장소 전환
3. [단기] ChatRoom.js → MessageList, ImageUpload, DealConfirmation, ReviewModal 분리
4. [단기] Custom Hook (useChat, useReview, useCareRequest) 추출
5. [중기] Zustand 또는 Redux Toolkit 상태 관리 도입
6. [중기] TypeScript 점진적 마이그레이션 (.js → .tsx)
7. [중기] Error Boundary 구현
```

---

## 통합 종합 권고사항

### 즉시 조치 필요 (이번 스프린트)

| # | 항목 | 담당 영역 | 심각도 |
|---|------|----------|--------|
| 1 | 민감 정보 환경변수 이동 (JWT, DB, API 키) | 보안 | 🔴 Critical |
| 2 | CORS 특정 도메인만 허용 | 보안 | 🔴 Critical |
| 3 | JWT 쿼리 파라미터 수신 제거 | 보안 | 🔴 Critical |
| 4 | 모든 @RequestBody에 @Valid 추가 | 보안 | 🔴 Critical |
| 5 | MissingPetBoard/Notification FetchType.LAZY 명시 | 성능 | 🔴 Critical |
| 6 | System.out.println 전면 제거 | 스타일 | 🔴 |
| 7 | 프론트엔드 console.log 정리 | 스타일 | 🔴 |

### 단기 조치 (다음 스프린트)

| # | 항목 | 담당 영역 |
|---|------|----------|
| 8 | SecurityConfig URL 패턴 명시적 정리 | 보안 |
| 9 | UserProfileController IDOR 수정 | 보안 |
| 10 | Refresh Token Redis 블랙리스트 | 보안 |
| 11 | 관리자 게시글 조회 Specification 전환 | 성능 |
| 12 | getCurrentUserId / isAdmin 중앙화 | 스타일 |
| 13 | ChatRoom.js 컴포넌트 분해 | 스타일 |
| 14 | localStorage 토큰 → HttpOnly 쿠키 | 보안+스타일 |

### 중기 조치 (1개월 이내)

| # | 항목 | 담당 영역 |
|---|------|----------|
| 15 | Location Projection 도입 | 성능 |
| 16 | Notification Redis Sorted Set 전략 | 성능 |
| 17 | SSE 하트비트 + 연결 정리 | 성능 |
| 18 | CareRequestService 도메인 분해 | 스타일 |
| 19 | TypeScript 점진적 마이그레이션 | 스타일 |
| 20 | Error Boundary 구현 | 스타일 |
| 21 | 비밀번호 복잡도 정책 추가 | 보안 |
| 22 | show-sql=false (운영 환경) | 보안 |

---

*이 보고서는 이보안(보안), 김성능(성능), 박스타일(코드 스타일) 팀원이 독립적으로 분석한 결과를 통합한 것입니다.*
