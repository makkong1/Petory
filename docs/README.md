# Petory 백엔드 아키텍처 문서

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [아키텍처 개요](./architecture/overview.md)
3. [도메인별 상세 문서](#도메인별-상세-문서)
4. [성능 최적화](./performance/query-optimization.md)
5. [동시성 제어](./concurrency/control-strategies.md)
6. [도메인별 백엔드 성능 최적화 리팩토링](#도메인별-백엔드-성능-최적화-리팩토링)

## 프로젝트 개요

**Petory**는 반려동물 통합 플랫폼으로, 게시판, 펫케어 요청, 실종 동물 찾기, 오프라인 모임 등 다양한 기능을 제공합니다.

### 기술 스택

- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **ORM**: Spring Data JPA (Hibernate)
- **Database**: MySQL
- **Cache**: Redis (Spring Cache)
- **Security**: Spring Security + JWT
- **Scheduling**: Spring Scheduler
- **실시간**: WebSocket (STOMP)

### 핵심 기능

- 게시판 (커뮤니티, 인기글 스냅샷)
- 펫케어 요청/지원 시스템
- 펫코인 결제 (에스크로, Care/Chat 연동, 동시성 제어)
- 실종 동물 신고 및 찾기
- 위치 기반 서비스 (병원, 카페 등)
- 오프라인 모임
- 사용자 신고 및 제재 시스템
- 알림 시스템
- 통계 수집

## 도메인별 상세 문서

### 핵심 도메인

| 도메인 | 설명 | 문서 링크 |
|--------|------|----------|
| **User** | 사용자, 반려동물, 소셜 로그인, 제재 관리 | [상세보기](./domains/user.md) |
| **Board** | 커뮤니티 게시판, 댓글, 반응, 인기글 | [상세보기](./domains/board.md) |
| **Care** | 펫케어 요청, 지원, 댓글, 리뷰 | [상세보기](./domains/care.md) |
| **Payment** | 펫코인 결제, 에스크로, Care/Chat 연동 | [상세보기](./domains/payment.md) |
| **Missing Pet** | 실종 동물 신고 및 관리 | [상세보기](./domains/missing-pet.md) |
| **Location** | 위치 기반 서비스, 리뷰 | [상세보기](./domains/location.md) |
| **Meetup** | 오프라인 모임 | [상세보기](./domains/meetup.md) |
| **Report** | 신고 및 제재 시스템 | [상세보기](./domains/report.md) |
| **Notification** | 알림 시스템 | [상세보기](./domains/notification.md) |

### 지원 도메인

| 도메인 | 설명 | 문서 링크 |
|--------|------|----------|
| **File** | 파일 업로드/다운로드 | [상세보기](./domains/file.md) |
| **Activity** | 사용자 활동 로그 | [상세보기](./domains/activity.md) |
| **Statistics** | 일별 통계 수집 | [상세보기](./domains/statistics.md) |

## 아키텍처 특징

### 1. 레이어드 아키텍처
- **Controller**: REST API 엔드포인트
- **Service**: 비즈니스 로직
- **Repository**: 데이터 액세스
- **Entity**: JPA 엔티티
- **DTO**: 데이터 전송 객체
- **Converter**: Entity ↔ DTO 변환

### 2. 도메인 주도 설계 (DDD)
- 도메인별 패키지 구조로 응집도 향상
- 명확한 도메인 경계와 책임 분리

### 3. 성능 최적화 전략
- **캐싱**: Spring Cache를 활용한 조회 성능 향상
- **배치 쿼리**: N+1 문제 해결을 위한 IN 절 배치 조회
- **비동기 처리**: @EnableAsync를 통한 비동기 작업
- **스케줄링**: 주기적 작업 자동화

### 4. 보안
- JWT 기반 인증/인가
- Spring Security 통합
- 소프트 삭제를 통한 데이터 보존
- 사용자 제재 시스템

## 성능 최적화 포인트

상세한 내용은 [성능 최적화 문서](./performance/query-optimization.md)를 참조하세요.

### 주요 최적화 사항

1. **N+1 문제 해결**
   - `BoardService.mapBoardsWithReactionsBatch()`: 반응(좋아요/싫어요) 배치 조회
   - IN 절 배치 크기 제한 (500개 단위)

2. **캐시 전략**
   - `@Cacheable`: 단일 게시글 조회
   - `@CacheEvict`: 생성/수정/삭제 시 캐시 무효화

3. **인덱싱 전략**
   - 복합 인덱스를 통한 쿼리 성능 향상

## 동시성 제어 포인트

상세한 내용은 [동시성 제어 문서](./concurrency/control-strategies.md)를 참조하세요.

### 주요 동시성 이슈

1. **게시글 조회수 증가**
   - 문제: 동시 조회 시 조회수 부정확
   - 해결: `BoardViewLog`를 통한 중복 조회 방지

2. **좋아요/싫어요 처리**
   - 문제: 동시 클릭 시 중복 반응
   - 해결: 유니크 제약 조건 + 예외 처리

3. **모임 참여자 수 관리**
   - 문제: 최대 인원 초과 가능
   - 해결: 원자적 UPDATE (`incrementParticipantsIfAvailable`)

4. **펫케어 지원 승인**
   - 문제: 동시 지원 승인 시 중복 선택
   - 해결 방안: 트랜잭션 격리 수준 조정 필요

5. **펫코인 결제**
   - 문제: 잔액 차감·에스크로 상태 변경·중복 지급/환불 시 Race Condition
   - 해결: 비관적 락(`findByIdForUpdate`, `findByCareRequestForUpdate`), 지급/환불 실패 시 상태 변경 롤백  

## 도메인별 백엔드 성능 최적화 리팩토링

- [Board](./refactoring/board/board-backend-performance-optimization.md) - N+1, 메모리 페이징, Admin 필터링
- [User](./refactoring/user/user-backend-performance-optimization.md) - Auth 중복 조회, Admin 삭제, SocialUsers N+1
- [Payment](./refactoring/payment/payment-backend-performance-optimization.md) - 펫코인 Race Condition, DB 페이징, N+1
- [Meetup](./refactoring/meetup/meetup-backend-performance-optimization.md) - 인근 모임 인메모리 필터링, 참여자 N+1

## 다이어그램

- [도메인 간 연관관계](./architecture/domain-relationships.md)
- [데이터베이스 ERD](./architecture/erd.md)

