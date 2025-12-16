# Care 도메인 - 포트폴리오 상세 설명

## 1. 기능 설명

### 1.1 도메인 개요
- **역할**: 펫케어 요청/지원 시스템으로, 반려동물 돌봄이 필요한 사용자와 돌봄을 제공할 수 있는 사용자를 연결합니다.
- **주요 기능**: 
  - 펫케어 요청 생성/조회/수정/삭제
  - 펫케어 지원 (지원자 모집)
  - 지원 승인/거절 (1명만 승인 가능)
  - 펫케어 상태 관리 (OPEN → IN_PROGRESS → COMPLETED)
  - 펫케어 리뷰 시스템
  - 날짜 지난 요청 자동 완료 (스케줄러)

### 1.2 기능 시연
> **스크린샷/영상 링크**: [기능 작동 영상 또는 스크린샷 추가]

#### 주요 기능 1: 펫케어 요청 및 지원
- **설명**: 사용자가 펫케어 요청을 생성하고, 다른 사용자들이 지원할 수 있습니다.
- **사용자 시나리오**: 
  1. 펫케어 요청 생성 (제목, 설명, 날짜, 펫 정보)
  2. 여러 사용자가 지원
  3. 요청자가 1명만 승인
  4. 승인 시 상태 변경 (OPEN → IN_PROGRESS)
- **스크린샷/영상**: 

#### 주요 기능 1-1: 채팅 후 거래 확정 및 완료
- **설명**: 펫케어 요청자가 서비스 제공자와 채팅을 시작한 후, 양쪽 모두 거래를 확정하면 펫케어 서비스가 시작되고, 서비스 완료 후 완료 처리할 수 있습니다.
- **사용자 시나리오**:
  1. 펫케어 요청 생성 (OPEN 상태)
  2. 서비스 제공자가 "채팅하기" 버튼 클릭하여 채팅방 생성
  3. 채팅방에서 가격, 시간, 서비스 내용 등 조건 협의
  4. 양쪽 모두 "거래 확정" 버튼 클릭
  5. 양쪽 모두 확정 시 자동으로:
     - CareApplication 생성 및 ACCEPTED 상태로 설정
     - CareRequest 상태 변경 (OPEN → IN_PROGRESS)
  6. 서비스 진행 (IN_PROGRESS 상태)
  7. 서비스 완료 후 채팅방에서 "서비스 완료" 버튼 클릭
  8. CareRequest 상태 변경 (IN_PROGRESS → COMPLETED)
- **스크린샷/영상**: 

#### 주요 기능 2: 펫케어 리뷰 시스템
- **설명**: 펫케어 완료 후 요청자가 돌봄 제공자에게 리뷰를 작성할 수 있습니다.
- **사용자 시나리오**:
  1. 펫케어 완료 (COMPLETED 상태)
  2. 요청자가 리뷰 작성 (평점 1-5, 내용)
  3. 평균 평점 계산 및 표시
- **스크린샷/영상**: 

---

## 2. 서비스 로직 설명

### 2.1 핵심 비즈니스 로직

#### 로직 1: 채팅 후 거래 확정 (양쪽 모두 확인 시 자동 승인)
**구현 위치**: `ConversationService.confirmCareDeal()` (Lines 545-652)

**핵심 로직**:
- 펫케어 관련 채팅방인지 확인 (`RelatedType.CARE_REQUEST` 또는 `CARE_APPLICATION`)
- 사용자의 거래 확정 처리 (`dealConfirmed`, `dealConfirmedAt` 설정)
- 양쪽 모두 확정했는지 확인 (2명 참여자 모두 `dealConfirmed = true`)
- 양쪽 모두 확정 시:
  - `CareRequest` 상태가 `OPEN`인 경우에만 처리
  - 제공자 찾기 (요청자가 아닌 참여자)
  - `CareApplication` 생성 또는 승인 (`ACCEPTED` 상태)
  - `CareRequest` 상태를 `IN_PROGRESS`로 변경

**동시성 제어**: `@Transactional`로 트랜잭션 보장

**설명**:
- **처리 흐름**: 채팅방 조회 → 사용자 거래 확정 처리 → 양쪽 모두 확정 확인 → 지원 승인 및 상태 변경
- **주요 판단 기준**: 양쪽 모두 거래 확정해야만 서비스 시작
- **동시성 제어**: 트랜잭션으로 처리하여 안전성 보장

#### 로직 2: 날짜 지난 요청 자동 완료
**구현 위치**: `CareRequestScheduler.updateExpiredCareRequests()` (Lines 32-60)

**스케줄러 설정**:
- 매 시간 정각 실행: `@Scheduled(cron = "0 0 * * * ?")`
- 매일 자정에도 실행: `@Scheduled(cron = "0 0 0 * * ?")` (더 정확한 처리를 위해)

**핵심 로직**:
- 날짜가 지났고 `OPEN` 또는 `IN_PROGRESS` 상태인 요청 조회
- 상태를 `COMPLETED`로 변경
- `saveAll()`로 일괄 저장

**설명**:
- **처리 흐름**: 만료된 요청 조회 → 상태 변경 → 저장
- **주요 판단 기준**: 날짜가 지났고 OPEN 또는 IN_PROGRESS 상태

### 2.2 서비스 메서드 구조

#### CareRequestService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `createCareRequest()` | 펫케어 요청 생성 | 이메일 인증 확인, 펫 소유자 확인, 상태 OPEN |
| `getAllCareRequests()` | 요청 목록 조회 | 상태 필터링, 위치 필터링, 작성자 활성 상태 확인 |
| `getCareRequest()` | 단일 요청 조회 | 펫 정보 포함 조회 |
| `updateCareRequest()` | 요청 수정 | 펫 정보 업데이트 지원 |
| `deleteCareRequest()` | 요청 삭제 | Soft Delete |
| `getMyCareRequests()` | 내 요청 목록 | 사용자별 요청 조회 |
| `updateStatus()` | 상태 변경 | OPEN → IN_PROGRESS → COMPLETED |
| `searchCareRequests()` | 요청 검색 | 제목/내용 검색 |

#### CareReviewService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `createReview()` | 리뷰 작성 | COMPLETED 상태 확인, 평균 평점 계산 |
| `getAverageRating()` | 평균 평점 조회 | 캐싱 적용 |

### 2.3 트랜잭션 처리
- **트랜잭션 범위**: 
  - 요청 생성/수정/삭제: `@Transactional`
  - 거래 확정: `@Transactional` (동시성 제어 필요)
  - 조회 메서드: `@Transactional(readOnly = true)`
- **격리 수준**: 기본값 (READ_COMMITTED)
- **이메일 인증**: 요청 생성 시 이메일 인증 확인 (`EmailVerificationRequiredException`)

---

## 3. 아키텍처 설명

### 3.1 도메인 구조
```
domain/care/
  ├── controller/
  │   ├── CareRequestController.java
  │   └── CareRequestCommentController.java
  ├── service/
  │   ├── CareRequestService.java
  │   ├── CareReviewService.java
  │   └── CareRequestScheduler.java
  ├── entity/
  │   ├── CareRequest.java
  │   ├── CareApplication.java
  │   ├── CareReview.java
  │   └── CareRequestComment.java
  └── repository/
      ├── CareRequestRepository.java
      └── CareReviewRepository.java
```

### 3.2 엔티티 구조

#### CareRequest (펫케어 요청)
```java
@Entity
@Table(name = "carerequest")
public class CareRequest extends BaseTimeEntity {
    private Long idx;
    private Users user;                    // 요청자
    private Pet pet;                       // 관련 펫 (선택사항)
    private String title;                   // 제목
    @Lob
    private String description;            // 설명
    private LocalDateTime date;            // 날짜
    @Builder.Default
    private CareRequestStatus status = CareRequestStatus.OPEN;  // 상태 (OPEN, IN_PROGRESS, COMPLETED)
    @Builder.Default
    private Boolean isDeleted = false;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;       // BaseTimeEntity에서 상속
    private LocalDateTime updatedAt;       // BaseTimeEntity에서 상속
    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareApplication> applications; // 지원 목록
    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareRequestComment> comments;   // 댓글 목록
}
```

#### CareApplication (펫케어 지원)
```java
@Entity
@Table(name = "careapplication")
public class CareApplication extends BaseTimeEntity {
    private Long idx;
    private CareRequest careRequest;        // 펫케어 요청
    private Users provider;                 // 케어 제공자
    @Builder.Default
    private CareApplicationStatus status = CareApplicationStatus.PENDING;  // 상태 (PENDING, ACCEPTED, REJECTED)
    @Lob
    private String message;                 // 지원 메시지
    private LocalDateTime createdAt;       // BaseTimeEntity에서 상속
    private LocalDateTime updatedAt;       // BaseTimeEntity에서 상속
}
```

#### CareReview (펫케어 리뷰)
```java
@Entity
@Table(name = "carereview")
public class CareReview extends BaseTimeEntity {
    private Long idx;
    private CareApplication careApplication; // 펫케어 지원
    private Users reviewer;                 // 리뷰 작성자 (요청자)
    private Users reviewee;                 // 리뷰 대상 (제공자)
    private int rating;                     // 평점 (1-5)
    @Lob
    private String comment;                 // 리뷰 내용
    private LocalDateTime createdAt;        // BaseTimeEntity에서 상속
    private LocalDateTime updatedAt;        // BaseTimeEntity에서 상속
}
```

#### CareRequestComment (펫케어 요청 댓글)
```java
@Entity
@Table(name = "carerequest_comment")
public class CareRequestComment {
    private Long idx;
    private CareRequest careRequest;        // 펫케어 요청
    private Users user;                     // 작성자
    @Lob
    private String content;                 // 내용
    private LocalDateTime createdAt;
    @Builder.Default
    private Boolean isDeleted = false;
    private LocalDateTime deletedAt;
}
```

### 3.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    Users ||--o{ CareRequest : "요청"
    CareRequest ||--o{ CareApplication : "지원"
    CareRequest ||--o| CareReview : "리뷰"
    CareRequest ||--o{ CareRequestComment : "댓글"
    Users ||--o{ CareApplication : "지원"
    Users ||--o{ CareReview : "작성"
```

### 3.4 API 설계
| 엔드포인트 | Method | 설명 |
|-----------|--------|------|
| `/api/care-requests` | GET | 요청 목록 (status, location 파라미터 지원) |
| `/api/care-requests/{id}` | GET | 단일 요청 조회 |
| `/api/care-requests` | POST | 요청 생성 (이메일 인증 필요) |
| `/api/care-requests/{id}` | PUT | 요청 수정 |
| `/api/care-requests/{id}` | DELETE | 요청 삭제 |
| `/api/care-requests/my-requests` | GET | 내 요청 목록 (userId 파라미터) |
| `/api/care-requests/{id}/status` | PATCH | 상태 변경 (status 파라미터) |
| `/api/care-requests/search` | GET | 요청 검색 (keyword 파라미터) |
| `/api/chat/conversations/{conversationIdx}/confirm-deal` | POST | 거래 확정 (채팅방에서) |
| `/api/care/reviews` | POST | 리뷰 작성 |

---

## 4. 트러블슈팅

---

## 5. 성능 최적화

### 5.1 DB 최적화

#### 인덱스 전략
```sql
-- 상태별 조회
CREATE INDEX idx_care_request_status ON carerequest(status, is_deleted, date DESC);

-- 사용자별 조회
CREATE INDEX idx_care_request_user ON carerequest(user_idx, is_deleted, created_at DESC);

-- 스케줄러 쿼리 최적화
CREATE INDEX idx_care_request_date_status ON carerequest(date, status);
```

### 5.2 애플리케이션 레벨 최적화

#### 캐싱 전략
```java
// 평균 평점 캐싱
@Cacheable(value = "userRating", key = "#userId")
public double getAverageRating(long userId) {
    return careReviewRepository.calculateAverageRating(userId);
}
```

#### N+1 문제 해결
```java
// Fetch Join 사용
@Query("SELECT cr FROM CareRequest cr " +
       "JOIN FETCH cr.user " +
       "LEFT JOIN FETCH cr.pet " +
       "WHERE cr.isDeleted = false")
List<CareRequest> findAllWithUserAndPet();
```

---

## 6. 핵심 포인트 요약

### 기술적 하이라이트
1. **거래 확정 동시성 제어**: 트랜잭션으로 안전성 보장, 양쪽 모두 확인 시 자동 승인
2. **자동 완료 스케줄러**: 날짜 지난 요청 자동 완료 (매 시간 정각 + 매일 자정)
3. **이메일 인증**: 요청 생성 시 이메일 인증 확인
4. **Soft Delete**: 요청 및 댓글 삭제 시 Soft Delete 적용
5. **N+1 문제 해결**: Fetch Join 사용 (`findByIdWithPet`)

