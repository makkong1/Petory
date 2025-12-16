# 🐾 Petory (페토리)
> **데이터 기반의 반려동물 케어 & 커뮤니티 통합 플랫폼**

![Project Status](https://img.shields.io/badge/Status-Active-success) ![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-green) ![React](https://img.shields.io/badge/React-19-blue)

---

## 📖 프로젝트 소개
**Petory**는 반려동물 보호자를 위한 **통합 플랫폼**입니다.
단순한 정보 공유를 넘어, **위치 기반 서비스 매칭**, **실시간 케어 요청**, 그리고 **데이터 기반의 관리자 대시보드**를 통해 체계적인 반려동물 생태계를 구축하는 것을 목표로 합니다.

---

## 🛠 Tech Stack

### Backend
- **Core**: Java 17, Spring Boot 3.5.7
- **Database**: MySQL 8.0 (JPA/Hibernate)
- **Cache**: Redis
  - 알림 버퍼링: 최신 알림 50개를 24시간 TTL로 캐싱하여 실시간 조회 성능 향상
  - 게시글 캐싱: Spring Cache를 통한 게시글 상세 조회 캐싱 (`@Cacheable`)
  - 이메일 인증: 회원가입 전 이메일 인증 상태 임시 저장 (24시간 TTL)
- **Security**: Spring Security, JWT (Access Token + Refresh Token)
- **Build**: Gradle
- **Scheduling**: Spring Scheduler (@Scheduled)
- **Async**: Spring Async (@Async)

### Frontend
- **Framework**: React 19
- **Styling**: Styled-components
- **HTTP Client**: Axios
- **Visualization**: Recharts (Admin Dashboard)

---

## 🌟 핵심 기능 (Key Features)

### 위치 기반 서비스 (LBS)
- **반경 검색**: MySQL `ST_Distance_Sphere` 함수를 활용한 반경 기반 위치 검색 (미터 단위)
- **지역 계층 검색**: 시도 > 시군구 > 읍면동 > 도로명 우선순위로 지역별 서비스 검색
- **거리 계산**: Haversine 공식을 통한 두 좌표 간 거리 계산 (미터 단위)
- **카카오맵 API 연동**: 
  - 키워드 기반 장소 검색 (반려동물 관련 시설)
  - 주소-좌표 변환(Geocoding) 및 좌표-주소 변환(역지오코딩)
- **네이버맵 API 연동**: 길찾기(Directions API) 및 역지오코딩
- **공공데이터 연동**: CSV 배치 업로드를 통한 대량 위치 데이터 수집
- **캐싱**: 인기 위치 서비스 조회 결과 캐싱 (카테고리별 상위 10개)

### 펫 케어 & 매칭 (Pet Care)
- **생애주기 관리**: 요청 -> 지원 -> 매칭 -> 케어 수행 -> 리뷰로 이어지는 전체 프로세스 구현
- **신뢰 시스템**: 상호 리뷰 및 평점 시스템을 통한 펫시터 검증
- **상태 관리**: OPEN, IN_PROGRESS, COMPLETED, CANCELLED 상태 전이 로직

### 커뮤니티 & 실종 제보
- **블라인드 처리**: 신고 누적 시 자동으로 콘텐츠를 가리는 유해 콘텐츠 필터링 로직
- **실종 골든타임**: 지도 기반의 직관적인 실종/목격 위치 공유
- **Magazine + Smart Grid 레이아웃**: 공지사항, 이미지 게시글, 텍스트 게시글을 크기별로 구분하여 표시
- **인기 게시글 추적**: 조회수, 반응 수 기반 인기 게시글 스냅샷 생성

### 관리자 대시보드 & 통계 시스템 (Admin Dashboard)
- **효율적인 데이터 집계**: 실시간 쿼리 부하를 줄이기 위해 `DailyStatistics` 테이블을 설계하여 일별 핵심 지표(가입자, 게시글 등)를 요약 저장
- **시각화**: Recharts를 활용하여 일별 성장 추이(Line Chart) 및 서비스 활성화 지표(Bar Chart) 시각화
- **통합 관리**: 신고(Report), 유저, 콘텐츠, 케어 서비스를 한곳에서 제어하는 중앙 집중형 관리자 페이지

### 알림 시스템 (Notification System)
- **이중 저장소 전략**:
    - **Redis**: 최신 알림 50개를 24시간 TTL로 캐싱하여 실시간 조회 속도 극대화
    - **MySQL**: 모든 알림을 영구 저장하여 이력 관리 및 안정성 보장
    - **병합 전략**: Redis와 DB 데이터를 병합하여 중복 제거 후 최신순 정렬
- **다양한 알림 유형**: 댓글, 케어 요청, 실종 제보 등 도메인별 이벤트 트리거 구현
- **실시간 알림**: SSE(Server-Sent Events)를 통한 실시간 알림 푸시

### 신고 및 제재 시스템 (Report & Sanction System)
- **다양한 신고 타입**: 게시글, 댓글, 실종 제보, 유저 신고 지원
- **자동 제재 시스템**: 
    - 경고 3회 누적 시 자동 이용제한 3일 적용
    - 관리자 수동 제재 (경고, 이용제한, 영구 차단)
- **제재 이력 관리**: 모든 제재 이력을 `UserSanction` 테이블에 기록
- **자동 해제**: 스케줄러를 통한 만료된 이용제한 자동 해제
- **동시성 제어**: DB 레벨 원자적 증가 쿼리로 여러 관리자가 동시에 경고 부여 시에도 경고 횟수 정확성 보장

---

## 🏗️ System Architecture & Strategy

### 📊 통계 데이터 처리 전략
> **Problem**: 데이터가 누적될수록 `COUNT(*)` 기반의 실시간 통계 쿼리는 DB 성능에 영향을 줄 수 있습니다.
>
> **Solution**: **[Daily Summary Pattern]**
> 매일 자정 배치 작업을 통해 전날의 데이터를 집계하여 `DailyStatistics` 테이블에 요약 저장합니다. 이를 통해 데이터 양이 늘어나도 대시보드 조회 성능을 일정하게 유지합니다.
> ([상세 전략 문서 보기](./md/ADMIN_STATISTICS_STRATEGY.md))

### 🔔 알림 시스템 아키텍처
> **Strategy**: 알림은 생성 직후 조회가 빈번하고, 일정 시간이 지나면 조회 빈도가 낮아지는 특성이 있습니다.
> 따라서 Redis를 캐시로 사용하여 최신 알림 조회 성능을 높이고, DB 부하를 분산시키는 구조를 채택했습니다.
> ([상세 전략 문서 보기](./md/NOTIFICATION_STRATEGY.md))

### 🔐 인증 및 보안 아키텍처
- **JWT 기반 인증**: Access Token (15분) + Refresh Token (1일) 이중 토큰 전략
- **소셜 로그인**: OAuth2 기반 Google, Naver 로그인 지원
  - 일반 로그인과 동일한 JWT 토큰 발급 방식
  - 기존 계정과 소셜 계정 자동 연결 (이메일 기반)
  - 소셜 로그인 사용자는 자동으로 이메일 인증 완료 처리
- **Spring Security**: Method-level Security (`@PreAuthorize`)를 통한 세밀한 권한 제어
- **Role 기반 접근 제어**: USER, SERVICE_PROVIDER, ADMIN, MASTER 역할 구분
- **제재된 유저 로그인 차단**: 로그인 시 유저 상태(ACTIVE, SUSPENDED, BANNED) 확인

### ✉️ 이메일 인증 시스템 아키텍처
> **핵심 원칙**: 이메일 인증은 **하나의 통합 시스템**으로 구현하며, 용도(purpose)만 분리합니다.
>
> **구현 전략**:
> 1. **단일 인증 토큰 생성**: 이메일 인증 링크에 포함된 토큰은 하나의 표준 형식
> 2. **용도(Purpose) 분리**: 토큰에 `purpose` 파라미터 포함 (PASSWORD_RESET, PET_CARE, MEETUP 등)
> 3. **서비스 레벨 권한 체크**: 각 서비스(펫케어, 모임 등)에서 `emailVerified` 필드 확인
> 4. **통합 인증 처리**: 인증 링크 클릭 시 `emailVerified = true`로 업데이트 (용도 무관)
> 5. **회원가입 전 인증 지원**: Redis를 활용하여 회원가입 전 이메일 인증 상태 임시 저장 (24시간 TTL)
>
> **권한 정책**:
> - **1단계 (로그인만)**: 모든 조회, 게시글/댓글 작성 (유입용, 참여 유도용)
> - **2단계 (이메일 인증 필수)**: 게시글/댓글 수정/삭제, 실종 제보, 리뷰 작성, 모임/펫케어 서비스, 비밀번호 변경 (책임 있는 행동)
>
> **특징**:
> - 소셜 로그인 사용자는 자동으로 이메일 인증 완료 (Google: `email_verified`, Naver: 기본 `true`)
> - 일반 회원가입 사용자는 이메일 인증 링크 클릭 필요
> - 회원가입 전 이메일 인증 가능 (Redis에 임시 저장 후 회원가입 시 적용)
>
> ([상세 아키텍처 문서 보기](./docs/architecture/이메일%20인증%20시스템%20아키텍처.md))

### 📁 파일 관리 시스템
- **통합 파일 관리**: `AttachmentFile` 엔티티를 통한 모든 도메인의 파일 통합 관리
- **타입별 분리**: BOARD, COMMENT, MISSING_PET 등 타입별 파일 분리 저장
- **동기화 메커니즘**: 게시글/댓글 생성/수정 시 파일 자동 동기화

---

## 📂 Project Structure

```
Petory/
├── backend/
│   └── main/
│       ├── java/com/linkup/Petory/
│       │   ├── domain/              # 도메인별 패키지
│       │   │   ├── activity/        # 활동 통계
│       │   │   ├── board/           # 커뮤니티 게시판
│       │   │   │   ├── controller/  # BoardController, MissingPetBoardController
│       │   │   │   ├── service/     # BoardService, CommentService, ReactionService
│       │   │   │   ├── entity/      # Board, Comment, BoardReaction 등
│       │   │   │   └── repository/  # JPA Repository
│       │   │   ├── care/            # 펫 케어 서비스
│       │   │   │   ├── controller/  # CareRequestController
│       │   │   │   ├── service/     # CareRequestService
│       │   │   │   └── entity/      # CareRequest, CareApplication, CareReview
│       │   │   ├── location/        # 위치 기반 서비스
│       │   │   │   ├── controller/  # LocationServiceController, GeocodingController
│       │   │   │   └── service/     # LocationServiceService
│       │   │   ├── meetup/          # 모임 서비스
│       │   │   ├── notification/    # 알림 시스템
│       │   │   ├── report/          # 신고 관리
│       │   │   ├── statistics/      # 통계/대시보드
│       │   │   ├── user/            # 유저 관리
│       │   │   │   ├── controller/  # AuthController, UsersController
│       │   │   │   ├── service/     # AuthService, UsersService, UserSanctionService
│       │   │   │   ├── entity/      # Users, UserSanction
│       │   │   │   └── scheduler/   # UserSanctionScheduler
│       │   │   └── file/            # 파일 관리
│       │   ├── filter/              # JwtAuthenticationFilter
│       │   ├── global/              # 전역 설정
│       │   │   ├── security/        # SecurityConfig
│       │   │   ├── exception/       # GlobalExceptionHandler
│       │   │   └── common/          # 공통 응답 DTO
│       │   └── util/                # JwtUtil 등 유틸리티
│       └── resources/
│           ├── application.properties
│           └── db/                   # SQL 스크립트
└── frontend/
    └── src/
        ├── components/               # React 컴포넌트
        ├── api/                      # Axios API 모듈
        ├── contexts/                 # React Context (Auth, Theme)
        └── styles/                   # 테마 설정
```

---

## 🔌 주요 API 엔드포인트

### 인증 (Authentication)
```
POST   /api/auth/login          # 로그인 (Access Token + Refresh Token 발급)
POST   /api/auth/register       # 회원가입
POST   /api/auth/refresh        # Access Token 갱신
POST   /api/auth/logout         # 로그아웃
POST   /api/auth/validate       # 토큰 검증
```

### 커뮤니티 (Community)
```
GET    /api/boards              # 게시글 목록 조회
GET    /api/boards/{id}         # 게시글 상세 조회
POST   /api/boards              # 게시글 작성
PUT    /api/boards/{id}         # 게시글 수정
DELETE /api/boards/{id}         # 게시글 삭제
POST   /api/boards/{id}/reactions  # 반응 추가 (좋아요 등)
GET    /api/comments            # 댓글 목록 조회
POST   /api/comments            # 댓글 작성
```

### 실종 제보 (Missing Pet)
```
GET    /api/missing-pets        # 실종 제보 목록
GET    /api/missing-pets/{id}   # 실종 제보 상세
POST   /api/missing-pets        # 실종 제보 작성
PUT    /api/missing-pets/{id}   # 실종 제보 수정
```

### 펫 케어 요청 (Care Request)
```
GET    /api/care-requests       # 케어 요청 목록
GET    /api/care-requests/{id}  # 케어 요청 상세
POST   /api/care-requests       # 케어 요청 작성
POST   /api/care-requests/{id}/applications  # 케어 지원 신청
```

### 위치 기반 서비스 (Location Service)
```
GET    /api/location-services  # 주변 서비스 조회 (반경 검색)
GET    /api/location-services/{id}  # 서비스 상세
POST   /api/location-services  # 서비스 등록
GET    /api/geocoding/address  # 주소 -> 좌표 변환
```

### 모임 (Meetup)
```
GET    /api/meetups             # 모임 목록
POST   /api/meetups             # 모임 생성
GET    /api/meetups/nearby      # 주변 모임 조회
POST   /api/meetups/{id}/participants  # 모임 참여
```

### 알림 (Notification)
```
GET    /api/notifications       # 알림 목록 조회
PUT    /api/notifications/{id}/read  # 알림 읽음 처리
GET    /api/notifications/stream  # SSE 실시간 알림 스트림
```

### 신고 (Report)
```
POST   /api/reports             # 신고 제출
GET    /api/reports             # 신고 목록 조회 (관리자)
GET    /api/reports/{id}        # 신고 상세 조회
POST   /api/reports/{id}/handle # 신고 처리 (관리자)
```

### 관리자 (Admin)
```
GET    /api/admin/users         # 유저 목록 조회
GET    /api/admin/statistics    # 통계 데이터 조회
GET    /api/admin/boards        # 게시글 관리
```

---

## 🗄️ 데이터베이스 설계 핵심

### 주요 엔티티
- **Users**: 유저 정보, 역할(ROLE), 제재 상태(ACTIVE/SUSPENDED/BANNED)
- **Board**: 커뮤니티 게시글 (카테고리, 상태, 조회수, 반응 수)
- **Comment**: 댓글 (게시글/실종제보 댓글 통합 관리)
- **CareRequest**: 펫 케어 요청 (상태, 위치, 기간)
- **LocationService**: 위치 기반 서비스 (POINT 타입으로 좌표 저장)
- **Notification**: 알림 (타입별 분류, 읽음 상태)
- **Report**: 신고 (타입별 분류, 처리 상태)
- **UserSanction**: 유저 제재 이력 (경고, 이용제한, 영구 차단)
- **DailyStatistics**: 일별 통계 요약 (배치 작업으로 집계)

### 인덱스 전략
- **공간 인덱스**: `LocationService`의 `location` 필드에 SPATIAL INDEX 적용
- **복합 인덱스**: `(target_type, target_idx, reporter_idx)` 등 자주 조회되는 조합에 인덱스 적용
- **타임스탬프 인덱스**: 생성일 기준 정렬이 빈번한 테이블에 인덱스 적용

---

## 🔒 보안 구현

### JWT 인증
- **Access Token**: 15분 유효기간, 모든 API 요청에 포함
- **Refresh Token**: 1일 유효기간, DB에 저장하여 관리
- **토큰 갱신**: Refresh Token으로 Access Token 자동 갱신

### 권한 제어
- **@PreAuthorize**: 메서드 레벨 권한 제어
  - `hasAnyRole('ADMIN','MASTER')`: 관리자 전용
  - `isAuthenticated()`: 로그인 사용자 전용
- **Role 계층**: USER < SERVICE_PROVIDER < ADMIN < MASTER

### 제재 시스템
- **로그인 차단**: 제재된 유저(BANNED, SUSPENDED)는 로그인 불가
- **자동 해제**: 스케줄러를 통한 만료된 이용제한 자동 해제

---

## ⚡ 성능 최적화 전략

### 캐싱 전략
- **알림 버퍼링**: Redis에 최신 알림 50개를 24시간 TTL로 저장하여 실시간 조회 성능 향상
- **게시글 캐싱**: `@Cacheable`을 통한 게시글 상세 조회 캐싱 (Redis 기반)
- **이메일 인증**: 회원가입 전 이메일 인증 상태를 Redis에 임시 저장 (24시간 TTL)
- **캐시 키 전략**: `notification:{userId}`, `boardDetail:{boardId}`, `email_verification:pre_registration:{email}` 등 도메인별 키 분리

### 배치 작업
- **일별 통계 집계**: 매일 자정 `DailyStatistics` 테이블 업데이트
- **인기 게시글 스냅샷**: 주기적으로 인기 게시글 스냅샷 생성
- **만료 제재 해제**: 매일 자정 만료된 이용제한 자동 해제

### 쿼리 최적화
- **공간 인덱스**: 위치 기반 검색 성능 향상
- **페이징 처리**: 대량 데이터 조회 시 페이징 적용
- **지연 로딩**: `@ManyToOne(fetch = FetchType.LAZY)` 적용
- **N+1 문제 해결**: IN 절을 활용한 배치 조회 (500개 단위)
  - 효과: 1000개 게시글 기준 2001개 쿼리 → 3개 쿼리로 감소 (99.8% 개선)

---

## 🔄 트랜잭션 관리

### 트랜잭션 전략
- **@Transactional**: Service 레이어에서 트랜잭션 경계 명확히 설정
- **읽기 전용 최적화**: `@Transactional(readOnly = true)` 기본값으로 설정 후 쓰기 작업만 명시
- **격리 수준**: 기본값(REPEATABLE_READ) 사용, 필요 시 명시적 설정
- **롤백 정책**: RuntimeException 발생 시 자동 롤백

### 주요 사례
1. **게시글 삭제 시 댓글 일괄 삭제**: 게시글과 댓글을 하나의 트랜잭션으로 처리하여 데이터 일관성 유지
2. **댓글 추가 시 게시글 카운트 동기화**: 댓글 저장과 카운트 업데이트를 원자적으로 처리
3. **펫케어 요청 생성 시 펫 소유자 검증**: 펫 검증과 요청 저장을 같은 트랜잭션에서 처리

**상세 사례**: [트랜잭션 관리 & 동시성 제어 사례](./docs/concurrency/transaction-concurrency-cases.md#트랜잭션-관리-사례)

---

## 🔒 동시성 제어

### 구현 완료 ✅
- **조회수 중복 방지**: `BoardViewLog`를 통한 사용자별 1회 조회 제한
- **반응 중복 방지**: Unique 제약조건으로 중복 좋아요/싫어요 방지
- **스케줄러 중복 실행 방지**: ShedLock을 통한 분산 환경 대응
- **제재 시스템 동시성 해결**: DB 레벨 원자적 증가 쿼리로 경고 횟수 정확성 보장
- **소셜 로그인 동시성 해결**: DB UNIQUE 제약조건으로 중복 계정 생성 방지

### 주요 사례
1. **게시글 조회수 중복 방지**: BoardViewLog 테이블로 사용자당 1회만 조회수 증가
2. **좋아요/싫어요 중복 방지**: Unique 제약조건 + 예외 처리로 동시 클릭 시 하나만 저장
3. **댓글 수 동기화**: UPDATE 쿼리로 원자적 증가 (개선 계획)
4. **제재 시스템 경고 횟수 동시성 문제**
   - **문제**: 여러 관리자가 동시에 경고 부여 시 Lost Update 발생 가능
   - **해결**: `incrementWarningCount()` 메서드로 DB 레벨 원자적 증가 쿼리 사용
   - **효과**: 동시 요청 시에도 경고 횟수 정확성 보장, 자동 이용제한 정확히 한 번만 적용
5. **소셜 로그인 중복 계정 생성 방지**
   - **문제**: 같은 소셜 계정으로 동시 로그인 시 Race Condition으로 중복 계정 생성 가능
   - **해결**: DB UNIQUE 제약조건 (`Users.email`, `SocialUser.provider+providerId`) + 트랜잭션 활용
   - **효과**: 동시 요청 시에도 하나의 계정만 생성되며, 기존 계정과의 연결도 정확하게 처리

**상세 사례**: [트랜잭션 관리 & 동시성 제어 사례](./docs/concurrency/transaction-concurrency-cases.md#동시성-제어-사례)

---

## 🚀 개발 환경 설정

### 필수 요구사항
- Java 17+
- MySQL 8.0+
- Redis 6.0+
- Gradle 7.0+

---

## 📚 상세 문서

### 백엔드 아키텍처
- [백엔드 아키텍처 문서](./docs/README.md) - 도메인별 상세 설명, ERD, 성능 최적화
- [트랜잭션 관리 & 동시성 제어 사례](./docs/concurrency/transaction-concurrency-cases.md) - 실제 코드 기반 사례

### 전략 문서
- [통계 시스템 전략](./md/ADMIN_STATISTICS_STRATEGY.md)
- [알림 시스템 전략](./md/NOTIFICATION_STRATEGY.md)
- [이메일 인증 시스템 아키텍처](./docs/architecture/이메일%20인증%20시스템%20아키텍처.md)
- [실시간 알림 구현](./docs/md/#%20실시간%20알림%20시스템%20구현%20문서.md)
- [Redis 캐싱 전략](./docs/architecture/Redis_캐싱_전략.md)
- [유저 제재 시스템](./docs/architecture/#%20유저%20제한%20및%20제재%20시스템.md)
- [코드 플로우 가이드](./docs/md/코드흐름가이드.md)

---

## 📝 라이선스
이 프로젝트는 개인 포트폴리오 프로젝트입니다.

---

## 👨‍💻 개발자
백엔드 개발자 포트폴리오 프로젝트
