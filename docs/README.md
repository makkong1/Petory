# Petory 프로젝트 문서

## 프로젝트 개요

**Petory**는 반려동물 케어 & 커뮤니티 통합 플랫폼.  
Spring Boot 3.5.7 (Java 17) 백엔드 + React 19 프론트엔드 + Capacitor 모바일 앱.

---

## 기술 스택

### 백엔드
| 항목 | 내용 |
|------|------|
| Framework | Spring Boot 3.5.7 |
| Language | Java 17 |
| ORM | Spring Data JPA (Hibernate) |
| Database | MySQL 8.0 |
| Cache | Redis (알림 캐시, 게시글 캐시, 이메일 인증) |
| Security | Spring Security + JWT + OAuth2 (Google, Naver) |
| 실시간 | WebSocket (STOMP) — 채팅 / SSE — 알림 |
| 푸시 알림 | Firebase Admin SDK 9.3.0 (FCM) |
| 모니터링 | Spring Boot Admin 3.3.3 |

### 프론트엔드 / 모바일
| 항목 | 내용 |
|------|------|
| Framework | React 19 |
| 스타일 | styled-components 6 |
| 상태관리 | Context API (Auth, Theme) |
| HTTP | Axios (JWT 인터셉터, 토큰 자동 갱신) |
| 모바일 | Capacitor 8 (Android / iOS WebView 래핑) |
| 토큰 저장 | localStorage + @capacitor/preferences (앱) |

---

## 주요 기능

- **커뮤니티 게시판** — 게시글, 댓글, 좋아요/싫어요, 인기글 스냅샷
- **펫케어 요청** — 케어 요청/지원, 리뷰, 스케줄러 자동 상태 변경
- **펫코인 결제** — 에스크로 방식, 비관적 락으로 동시성 제어
- **실종 동물** — 신고 등록, 제보 댓글, 위치 기반 조회
- **위치 기반 서비스** — 반경 내 병원·카페 조회, 리뷰
- **오프라인 모임** — 모임 생성/참여, 그룹 채팅 자동 생성
- **1:1 채팅** — WebSocket(STOMP), 케어 매칭 시 자동 생성
- **알림** — SSE 실시간(앱 열림) + FCM 푸시(앱 꺼짐)
- **신고 및 제재** — 신고 처리, 경고/정지/밴 제재
- **통계** — 일/주/월별 배치 집계 (Daily Summary Pattern)
- **관리자** — 도메인별 CRUD, 통계 대시보드, 감사 로그

---

## 아키텍처

### 레이어 구조
```
Controller → Service → Repository → Entity
                ↕
              DTO / Converter
```

### 인증 흐름
```
요청 → JwtAuthenticationFilter → SecurityConfig
     → JWT 검증 → SecurityContext 저장
     → @PreAuthorize 권한 체크
```

역할 계층: `USER < SERVICE_PROVIDER < ADMIN < MASTER`

### 알림 흐름
```
이벤트 발생 → NotificationService.createNotification()
               ├─ DB 저장 + Redis 캐시 (최대 50개, TTL 24h)
               ├─ SSE 발송 (앱 열려있을 때)
               └─ FCM 발송 (앱 꺼져있을 때)
```

### 결제 흐름
```
케어 요청 생성 → 코인 차감 → 에스크로 HOLD
     ↓ 완료
에스크로 → 제공자 지급 (비관적 락)
     ↓ 취소
에스크로 → 요청자 환불
```

---

## 도메인 목록 (14개)

| 도메인 | 설명 |
|--------|------|
| `user` | 사용자, 반려동물, 소셜 로그인, 제재 |
| `board` | 커뮤니티 게시판, 댓글, 반응, 인기글 |
| `care` | 펫케어 요청, 지원, 댓글, 리뷰 |
| `payment` | 펫코인 결제, 에스크로 |
| `chat` | 1:1 / 그룹 채팅 (WebSocket) |
| `notification` | SSE 알림, FCM 토큰 관리 |
| `location` | 위치 기반 서비스, 리뷰 |
| `meetup` | 오프라인 모임 |
| `report` | 신고 및 제재 |
| `statistics` | 일/주/월 통계 배치 |
| `file` | 파일 업로드/다운로드 |
| `activity` | 사용자 활동 로그 |
| `admin` | 관리자 기능, 감사 로그 |
| `common` | 공통 유틸 |

---

## 동시성 제어 포인트

| 대상 | 방식 |
|------|------|
| 펫코인 잔액 차감 | 비관적 락 (`findByIdForUpdate`) |
| 에스크로 상태 변경 | 비관적 락 (`findByCareRequestForUpdate`) |
| 모임 참여자 수 | DB 원자적 UPDATE (`incrementParticipantsIfAvailable`) |
| 경고 횟수 증가 | DB 원자적 UPDATE |
| 게시글 조회수 | `BoardViewLog` 중복 방지 |

---

## 성능 최적화 포인트

- N+1 해결: `@EntityGraph`, fetch join, IN절 배치 조회
- 캐싱: `@Cacheable` 게시글 상세, Redis 알림 목록
- 페이징: `Pageable` 기반 커서 페이징
- 통계: 실시간 쿼리 대신 자정 배치 집계

---

## 상세 문서

| 문서 | 링크 |
|------|------|
| 아키텍처 개요 | [overview.md](./architecture/overview.md) |
| 도메인 연관관계 | [domain-relationships.md](./architecture/domain-relationships.md) |
| ERD | [erd.md](./architecture/erd.md) |
| 성능 최적화 | [query-optimization.md](./performance/query-optimization.md) |
| 동시성 제어 | [control-strategies.md](./concurrency/control-strategies.md) |
| 모바일 앱 & FCM | [09-mobile-capacitor.md](./deployment/09-mobile-capacitor.md) |
| 배포 가이드 | [deployment/](./deployment/) |
| 도메인 페이지 작성 워크플로우 | [workflow.md](./domain-page-drafts/workflow.md) |
