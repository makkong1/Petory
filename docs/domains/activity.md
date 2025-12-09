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

#### 로직 1: 활동 로그 생성 (비동기)
```java
// ActivityService.java
@Async
public void logActivity(
    Users user, String activityType, 
    String targetType, Long targetIdx) {
    
    Activity activity = Activity.builder()
        .user(user)
        .activityType(activityType)
        .targetType(targetType)
        .targetIdx(targetIdx)
        .build();
    
    activityRepository.save(activity);
}
```

**설명**:
- **처리 흐름**: 활동 로그 생성 → 비동기 저장
- **비동기 처리**: 핵심 비즈니스 로직에 영향 없음

---

## 3. 아키텍처 설명

### 3.1 도메인 구조
```
domain/activity/
  ├── controller/
  │   └── ActivityController.java
  ├── service/
  │   └── ActivityService.java
  ├── entity/
  │   └── Activity.java
  └── repository/
      └── ActivityRepository.java
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
1. **비동기 처리**: 활동 로그를 비동기로 저장
2. **배치 삽입**: 대량 활동 로그 배치 처리
3. **통계 집계**: 활동 로그 기반 통계 수집

### 학습한 점
- 비동기 처리 전략
- 활동 로그 설계
- 통계 집계 방법

### 개선 가능한 부분
- 시계열 DB: InfluxDB, TimescaleDB 활용
- 이벤트 스트리밍: Kafka 활용
