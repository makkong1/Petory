# Activity 도메인 - 포트폴리오 상세 설명

## 1. 기능 설명

### 1.1 도메인 개요
- **역할**: 사용자 활동 로그 수집 도메인으로, 통계 및 분석을 위한 활동 데이터를 저장합니다.
- **주요 기능**: 
  - 활동 로그 생성 (게시글 작성, 댓글 작성, 좋아요 등)
  - 사용자별 활동 로그 조회
  - 활동 유형별 통계
  - 비동기 처리

### 1.2 기능 시연
> **스크린샷/영상 링크**: [기능 작동 영상 또는 스크린샷 추가]

#### 주요 기능 1: 활동 로그 수집
- **설명**: 사용자의 다양한 활동을 로그로 기록합니다.
- **사용자 시나리오**: 
  1. 게시글 작성 → 활동 로그 기록
  2. 댓글 작성 → 활동 로그 기록
  3. 좋아요 클릭 → 활동 로그 기록
  4. 활동 통계 조회
- **스크린샷/영상**: 

---

## 2. 서비스 로직 설명

### 2.1 핵심 비즈니스 로직

#### 로직 1: 사용자 활동 조회
**구현 위치**: `ActivityService.getUserActivities()` (Lines 46-251)

**핵심 로직**:
- **다양한 활동 타입 지원**:
  - `CARE_REQUEST`: 펫케어 요청
  - `BOARD`: 커뮤니티 게시글
  - `MISSING_PET`: 실종 제보 게시글
  - `CARE_COMMENT`: 펫케어 댓글
  - `COMMENT`: 커뮤니티 댓글
  - `MISSING_COMMENT`: 실종 제보 댓글
- **최신순 정렬**: `createdAt` 기준 내림차순 정렬
- **Soft Delete 필터링**: 삭제된 활동은 제외 (`isDeleted = false`)

#### 로직 2: 활동 조회 (페이징)
**구현 위치**: `ActivityService.getUserActivitiesWithPaging()` (Lines 254-297)

**핵심 로직**:
- **필터링**: `ALL`, `POSTS`, `COMMENTS`, `REVIEWS` 필터 지원
- **필터별 개수**: 전체, 게시글, 댓글, 리뷰 개수 제공
- **페이징**: Spring Data 페이징 지원

---

## 3. 아키텍처 설명

### 3.1 도메인 구조
**참고**: Activity 도메인은 별도의 Activity 엔티티를 사용하지 않고, 각 도메인의 엔티티를 직접 조회합니다.

```
domain/activity/
  ├── controller/
  │   └── ActivityController.java
  ├── service/
  │   └── ActivityService.java
  └── dto/
      ├── ActivityDTO.java
      └── ActivityPageResponseDTO.java
```

---

## 4. 트러블슈팅

---

## 5. 성능 최적화

### 5.1 DB 최적화

#### 인덱스 전략
```sql
-- 사용자별 활동 조회
CREATE INDEX idx_activity_user_created 
ON activity(user_idx, created_at DESC);

-- 활동 유형별 조회
CREATE INDEX idx_activity_type_created 
ON activity(activity_type, created_at DESC);
```

---

## 6. 핵심 포인트 요약

### 기술적 하이라이트
1. **통합 활동 조회**: 여러 도메인의 활동을 통합하여 조회 (펫케어 요청, 게시글, 댓글 등)
2. **필터링**: 활동 타입별 필터링 지원 (전체, 게시글, 댓글, 리뷰)
3. **페이징**: Spring Data 페이징 지원
4. **최신순 정렬**: `createdAt` 기준 내림차순 정렬
5. **Soft Delete 필터링**: 삭제된 활동은 제외
