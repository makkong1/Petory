# Petory 팀 역할 분배 분석 보고서

> 작성일: 2026-04-10  
> 기준 브랜치: dev  
> 코드 규모: 백엔드 Java 386개 파일 / 프론트엔드 JS 99개 파일

---

## 프로젝트 현황 요약

| 도메인 | 백엔드 완성도 | 프론트엔드 완성도 |
|--------|-------------|----------------|
| user / auth | ★★★★★ | ★★★★☆ |
| board / comment | ★★★★☆ | ★★★★☆ |
| care (케어 요청) | ★★★★☆ | ★★★☆☆ |
| chat (WebSocket) | ★★★☆☆ | ★★★☆☆ |
| payment (펫코인) | ★★★★☆ | ★★★☆☆ |
| meetup (산책모임) | ★★★★☆ | ★★★★☆ |
| location (위치서비스) | ★★★☆☆ | ★★★☆☆ |
| notification (SSE) | ★★★☆☆ | ★★★☆☆ |
| report / admin | ★★★★☆ | ★★★★☆ |
| statistics | ★★★☆☆ | ★★☆☆☆ |
| missing-pet | ★★★★☆ | ★★★★☆ |

---

## 팀원 1 — 로직 수정 담당

> **핵심 목표**: 현재 동작하지만 잘못되었거나 불안정한 로직을 수정한다.

### 1-1. SecurityConfig 인증 허점 수정 (HIGH)

**문제**: `GET /api/boards`에 `@PreAuthorize("permitAll()")` 어노테이션이 있어도  
SecurityConfig의 `.requestMatchers("/api/**").authenticated()` catch-all이 먼저 적용되어 실제로는 인증이 강제됨.

**파일**: `global/security/SecurityConfig.java`  
**수정 방향**: 공개 API는 `permitAll()` requestMatcher를 `/api/**` 앞에 명시적으로 등록. 또는 `@PreAuthorize`만으로 제어할 경우 `"/api/**"` 규칙을 `permitAll()`로 변경하고 메서드 레벨 보안에 위임.

---

### 1-2. 채팅 메시지 읽음 처리 완성 (HIGH)

**문제**: `ChatMessageService.java` (220줄) 에 읽음 처리(`isRead`) 관련 업데이트 로직이 불완전함.  
채팅방 입장 시 메시지를 일괄 읽음 처리하는 엔드포인트가 없고 프론트 `ChatRoom.js`에서 미읽음 배지 카운트가 항상 0으로 표시됨.

**파일**: `domain/chat/service/ChatMessageService.java`, `domain/chat/controller/`  
**수정 방향**: `/api/chats/{roomId}/read` POST 엔드포인트 추가, 입장 시 자동 호출.

---

### 1-3. CareRequest 상태 전이 검증 강화 (MEDIUM)

**문제**: `CareRequestService.java` (364줄)에서 상태 변경 시 유효하지 않은 전이(예: `COMPLETED → PENDING`)를 막는 검증이 없음. 상태 enum은 존재하나 전이 규칙이 서비스 메서드마다 중복·산발적으로 구현되어 있음.

**파일**: `domain/care/service/CareRequestService.java`  
**수정 방향**: 상태 전이 허용 맵을 enum이나 별도 validator 클래스로 추출, 서비스에서 단일 검증 메서드 호출.

---

### 1-4. 통계 배치 멱등성 보장 (MEDIUM)

**문제**: `StatisticsScheduler.java`가 매일 자정 실행되는데, 서버 재시작이 자정 전후에 발생하면 `DailyStatistics`가 중복 삽입될 수 있음.

**파일**: `domain/statistics/service/StatisticsScheduler.java`, `domain/statistics/service/StatisticsService.java`  
**수정 방향**: 배치 실행 전 당일 레코드 존재 여부를 확인하거나, 유니크 제약 + `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` 적용.

---

### 1-5. 알림 중복 제거 로직 재검토 (MEDIUM)

**문제**: `NotificationService.java` (253줄)에서 Redis와 MySQL 알림을 병합할 때 ID 기반으로 중복 제거를 수행하는데, Redis 캐시가 만료된 직후 조회 시 MySQL 데이터와 순서가 어긋남.

**파일**: `domain/notification/service/NotificationService.java`  
**수정 방향**: 병합 후 `createdAt` 기준 재정렬 보장, Redis 미스 시 MySQL 데이터만 반환하는 fallback 명확화.

---

### 1-6. 게시글 조회수 중복 카운트 방지 (LOW)

**문제**: `BoardService.java` (676줄)에서 조회수 증가 시 `BoardViewLog`를 활용하지만 비로그인 사용자(익명)의 동일 IP 중복 조회 방어 로직이 없음.

**파일**: `domain/board/service/BoardService.java`, `domain/board/entity/BoardViewLog.java`  
**수정 방향**: IP + 게시글 ID 기반 24시간 중복 체크를 `BoardViewLog`에 추가하거나 Redis TTL 키로 처리.

---

## 팀원 2 — 기능 추가 담당

> **핵심 목표**: 반려동물 플랫폼으로서 있어야 할 UX·서비스 기능을 신규 구현한다.

### 2-1. 펫 건강 일지 기능 (HIGH)

**배경**: 현재 `Pet` 엔티티에는 `PetVaccination`만 존재. 반려동물 케어 플랫폼의 핵심 차별화 포인트인 건강 기록 기능이 없음.

**구현 내용**:
- 백엔드: `domain/user/entity/PetHealthLog.java` (체중, 검진일, 메모, 사진), CRUD API `/api/pets/{petId}/health-logs`
- 프론트엔드: `MyProfilePage.js` 내 펫 카드에 건강 일지 탭 추가
- 연관: 파일 도메인 `AttachmentFileService` 재활용으로 이미지 첨부

---

### 2-2. 펫코인 자동 충전 / 정기 구독 (HIGH)

**배경**: `PetCoinService.java` (298줄)는 수동 충전만 지원. 케어 서비스 이용이 잦은 사용자에게 정기 구독 플랜이 없음.

**구현 내용**:
- 백엔드: `domain/payment/entity/CoinSubscription.java`, `PetCoinSubscriptionService.java`
- 스케줄러: 월 1회 자동 충전 배치 (`@Scheduled`)
- 프론트엔드: `PetCoinChargePage.js`에 구독 플랜 선택 UI 추가

---

### 2-3. 실시간 위치 공유 (산책 중) (HIGH)

**배경**: `UnifiedPetMapPage.js`에 케어·모임 지도가 있지만 산책 중 실시간 위치 공유는 없음. 모임 구성원 간 위치 공유는 안전 기능으로 활용 가능.

**구현 내용**:
- 백엔드: WebSocket STOMP 구독 경로 `/topic/walk/{meetupId}` 추가 (기존 WebSocket 인프라 재활용)
- 백엔드: `domain/meetup/controller/WalkLocationController.java`
- 프론트엔드: `MeetupLayer.js`에 참가자 실시간 위치 마커 표시

---

### 2-4. 알림 설정 (카테고리별 on/off) (MEDIUM)

**배경**: 현재 모든 알림이 강제 발송됨. `NotificationService.java`에 사용자별 설정 필터링이 없음.

**구현 내용**:
- 백엔드: `domain/notification/entity/NotificationSetting.java` (채팅·케어·모임·결제 카테고리별 boolean)
- 백엔드: API `/api/notifications/settings` GET/PUT
- 프론트엔드: `MyProfilePage.js`에 알림 설정 섹션 추가

---

### 2-5. 리뷰 신고 기능 (MEDIUM)

**배경**: `ReportService.java`가 게시글·댓글·사용자 신고를 지원하지만 케어 리뷰(`CareReview`) 신고는 `ReportTargetType` enum에 없음.

**구현 내용**:
- 백엔드: `ReportTargetType`에 `CARE_REVIEW` 추가, `ReportService`에 케어 리뷰 유효성 검증 분기 추가
- 프론트엔드: 케어 리뷰 카드에 신고 버튼 추가

---

### 2-6. 미아 반려동물 지도 핀 연동 (MEDIUM)

**배경**: `MissingPetBoardPage.js`는 리스트만 있고 지도와 연동이 없음. `UnifiedPetMapPage.js`에 실종 위치 핀을 표시하면 커뮤니티 제보 기능이 강화됨.

**구현 내용**:
- 프론트엔드: `UnifiedPetMapPage.js`에 실종 탭 추가, `MissingPetLayer.js` 신규 작성
- 백엔드: `MissingPetBoard`에 위도/경도 필드가 없으면 추가하고 API에 좌표 필터 쿼리 파라미터 노출

---

### 2-7. 펫 매칭 기능 (같이 산책할 친구 찾기) (LOW)

**배경**: 현재 모임은 수동 생성만 가능. 비슷한 크기·종·위치의 반려동물 보호자를 자동 추천하는 기능이 없음.

**구현 내용**:
- 백엔드: `domain/meetup/service/PetMatchingService.java` — `Pet` 정보 + 사용자 위치 기반 추천 알고리즘 (간단한 Rule-based)
- 프론트엔드: `HomePage.js`에 "이 지역 산책 친구" 추천 카드 섹션

---

## 팀원 3 — 점검 담당

> **핵심 목표**: 보안·성능·코드 품질·테스트 커버리지를 점검하고 개선 사항을 문서화한다.

### 3-1. JWT 토큰 보안 점검 (HIGH)

**점검 파일**: `util/JwtUtil.java`, `filter/JwtAuthenticationFilter.java`, `domain/user/service/AuthService.java`

**체크리스트**:
- [ ] `jwt.secret`이 충분한 엔트로피(≥256비트)인지 확인
- [ ] Access Token 만료 후 Refresh Token 재발급 시 이전 Refresh Token 무효화(`rotation`) 확인
- [ ] Refresh Token DB 저장 시 평문 저장 여부 확인 (해시 필요)
- [ ] `Authorization: Bearer` 헤더 외 URL 파라미터로 토큰 전달 여부 점검

---

### 3-2. 결제·에스크로 동시성 점검 (HIGH)

**점검 파일**: `domain/payment/service/PetCoinService.java`, `domain/payment/service/PetCoinEscrowService.java`

**체크리스트**:
- [ ] `findByIdForUpdate` 비관적 락 적용 범위가 충분한지 확인 (코인 충전, 송금, 에스크로 생성 모두 커버)
- [ ] 에스크로 생성과 코인 차감이 단일 트랜잭션인지 확인
- [ ] 결제 실패 시 롤백 경로 테스트
- [ ] PetCoin 잔액 음수 방어 로직 존재 여부

---

### 3-3. N+1 쿼리 점검 (HIGH)

**점검 대상**: `BoardService.java` (676줄), `MeetupService.java` (384줄), `CareRequestService.java` (364줄)

**방법**: `spring.jpa.show-sql=true` + 통합 테스트로 쿼리 수 측정

**체크리스트**:
- [ ] 게시글 목록 조회 시 작성자 User 정보 FETCH JOIN 여부
- [ ] 모임 목록 조회 시 참가자 수 서브쿼리/JOIN 여부 (`@ManyToMany` lazy 주의)
- [ ] 케어 요청 목록 조회 시 리뷰 집계 쿼리 개수
- [ ] `findAll()` 후 루프에서 getter 호출하는 패턴 제거

---

### 3-4. Redis 장애 내성 점검 (MEDIUM)

**점검 파일**: `global/security/RedisConfig.java`, `domain/notification/service/NotificationService.java`, `domain/board/service/BoardService.java`

**체크리스트**:
- [ ] Redis 연결 실패 시 `@Cacheable` 메서드가 예외 전파하는지 or fallback 동작하는지 확인
- [ ] SSE 알림 캐시(최신 50개) Redis 미스 시 MySQL 단독 서빙 경로 확인
- [ ] 이메일 인증 임시 저장 TTL 24h가 Redis 재시작 시 유실되는 시나리오 처리

---

### 3-5. 파일 업로드 보안 점검 (MEDIUM)

**점검 파일**: `domain/file/service/FileStorageService.java`, `domain/file/controller/`

**체크리스트**:
- [ ] 업로드 허용 MIME 타입 화이트리스트 존재 여부
- [ ] 파일명 Path Traversal 방어 (`../` 등 sanitize) 여부
- [ ] 업로드 파일 크기 제한 설정 여부 (`spring.servlet.multipart.max-file-size`)
- [ ] `GET /api/uploads/**`가 `permitAll()`인데 민감 파일 접근 가능한지 확인

---

### 3-6. 테스트 커버리지 현황 점검 (MEDIUM)

**점검 대상**: `backend/test/` 디렉터리

**체크리스트**:
- [ ] 현재 테스트 파일 수 / 커버리지 %를 Jacoco로 측정
- [ ] 결제·에스크로 도메인 단위 테스트 존재 여부 (가장 중요)
- [ ] SecurityConfig 인증 경로 통합 테스트 존재 여부
- [ ] `BoardService` 조회수 중복 방지 단위 테스트 존재 여부
- [ ] CI/CD 파이프라인에 테스트 단계 연결 여부

---

### 3-7. WebSocket 인증 점검 (LOW)

**점검 파일**: `global/websocket/security/WebSocketAuthChannelInterceptor.java`, `WebSocketAuthenticationInterceptor.java`

**체크리스트**:
- [ ] STOMP CONNECT 프레임에서 JWT 검증이 올바르게 동작하는지 확인
- [ ] 인증 실패 시 연결 강제 종료 여부 확인
- [ ] `/ws/**` 경로가 `permitAll()`인데 WebSocket 핸드쉐이크 이후 채널 레벨 인증이 반드시 실행되는지 확인

---

### 3-8. 프론트엔드 토큰 저장 방식 점검 (LOW)

**점검 파일**: `frontend/src/api/tokenStorage.js`, `frontend/src/contexts/AuthContext.js`

**체크리스트**:
- [ ] Access Token이 `localStorage`에 저장되는지 확인 (XSS 취약점 — `httpOnly` 쿠키 권장)
- [ ] Refresh Token 저장 위치 확인
- [ ] API 호출 실패(401) 시 자동 토큰 갱신(silent refresh) 로직 확인

---

## 우선순위 요약 (전체)

| 순위 | 담당 | 항목 | 영향도 |
|------|------|------|--------|
| 1 | 로직수정 | SecurityConfig 인증 허점 수정 | 보안 |
| 2 | 점검 | JWT 토큰 보안 점검 | 보안 |
| 3 | 점검 | 결제·에스크로 동시성 점검 | 데이터 정합성 |
| 4 | 로직수정 | 채팅 읽음 처리 완성 | UX |
| 5 | 기능추가 | 펫 건강 일지 기능 | 차별화 |
| 6 | 기능추가 | 실시간 위치 공유 | UX |
| 7 | 점검 | N+1 쿼리 점검 | 성능 |
| 8 | 로직수정 | CareRequest 상태 전이 검증 | 안정성 |
| 9 | 기능추가 | 펫코인 자동 충전 | 수익화 |
| 10 | 점검 | 파일 업로드 보안 점검 | 보안 |
