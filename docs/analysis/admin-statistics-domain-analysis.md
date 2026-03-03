# 관리자 통계 - 도메인 관점 분석

> **목적**: "데이터 집계 및 통계 분석 기능"이 **로직 구현 전**에 도메인적으로 올바르게 정의되었는지 검토

---

## 1. 기능 정의 (요구사항)

> ✅ **"데이터 집계 및 통계 분석 기능"**  
> 사용자 활동 및 서비스 이용 데이터를 기반으로 집계 및 통계 정보를 제공하는 기능

이걸 도메인 관점에서 풀면:

| 용어 | 도메인적 의미 |
|------|---------------|
| **사용자 활동** | 사용자가 플랫폼에서 수행한 행위 (가입, 로그인, 게시, 댓글, 지원, 참여 등) |
| **서비스 이용** | 플랫폼이 제공하는 서비스의 실제 사용량 (요청 수, 완료 수, 매출 등) |
| **집계** | 일/주/월 단위로 수치를 요약 |
| **통계 정보** | 관리자가 운영/의사결정에 활용할 수 있는 지표 |

---

## 2. Petory 도메인 맵 (통계와의 관계)

| 도메인 | 핵심 엔티티 | "사용자 활동" 예시 | "서비스 이용" 예시 |
|--------|-------------|-------------------|-------------------|
| **User** | Users | 가입, 로그인 | - |
| **Board** | Board | 게시글 작성 | 게시글 수 |
| **Care** | CareRequest, CareApplication | 요청 작성, 지원, 완료 | 요청 수, 완료 수, 매출 |
| **Meetup** | Meetup, MeetupParticipants | 모임 생성, 참여 | 모임 수, 참여 수 |
| **MissingPet** | MissingPetBoard | 실종 제보 작성 | 제보 수 |
| **Payment** | PetCoinTransaction | 충전, 결제 | 매출 |
| **Location** | LocationReview | 리뷰 작성 | 리뷰 수 |
| **Report** | Report | 신고 | 신고 수 |
| **Activity** | (통합 조회) | 게시글·댓글 등 통합 | - |

---

## 3. 현재 통계 모델의 도메인 커버리지

### 3.1 포함된 것

| 지표 | 대응 도메인 | 도메인적 의미 |
|------|------------|---------------|
| newUsers | User | 사용자 유입 |
| activeUsers | User | 일일 활성 사용자(DAU) |
| newPosts | Board | 커뮤니티 활동량 |
| newCareRequests | Care | 펫케어 서비스 수요 |
| completedCares | Care | 펫케어 서비스 이행량 |
| totalRevenue | Payment (미구현) | 수익 |

### 3.2 빠진 것 (도메인 관점)

| 도메인 | 예상 지표 | 비고 |
|--------|----------|------|
| **Meetup** | 생성 모임 수, 참여 수 | "산책 & 오프라인 모임"은 핵심 기능 중 하나 |
| **MissingPet** | 실종 제보 수 | "실종 골든타임"은 중요 기능 |
| **Care** | CareApplication 수 | 지원 건수 = 매칭 시도량 |
| **Board** | 댓글 수 | Activity와 연계, 커뮤니티 활성도 |
| **Report** | 신고 수 | 운영 관리용 |
| **Location** | 리뷰 수 | 위치 서비스 이용량 |

---

## 4. 도메인 정합성 검토

### 4.1 "사용자 활동" 관점

| 활동 유형 | Activity 도메인 | Statistics 도메인 | 정합성 |
|----------|----------------|------------------|--------|
| 가입 | - | newUsers ✓ | ✓ |
| 로그인 | - | activeUsers ✓ | ✓ |
| 게시글 작성 | BOARD, CARE_REQUEST, MISSING_PET | newPosts(Board만), newCareRequests ✓ | ⚠️ MissingPet 미포함 |
| 댓글 작성 | CARE_COMMENT, COMMENT, MISSING_COMMENT | - | ❌ 미포함 |
| 펫케어 지원 | - | - | ❌ 미포함 |
| 모임 참여 | - | - | ❌ 미포함 |

**Activity** 도메인은 게시글·댓글·케어·실종·리뷰를 통합하는데, **Statistics**는 Board·CareRequest만 집계하고 MissingPet·Meetup·댓글은 빠져 있음.

### 4.2 "서비스 이용" 관점

| 서비스 | 현재 통계 | 도메인적 기대 |
|--------|----------|---------------|
| 펫케어 | 요청·완료 ✓ | ✓ 적절 |
| 커뮤니티 | 게시글 ✓ | 댓글·반응 등 추가 가능 |
| 산책 모임 | - | 모임·참여 수 미포함 |
| 실종 제보 | - | 제보 수 미포함 |
| 위치 서비스 | - | 리뷰 수 미포함 |
| 결제 | totalRevenue 0 | 구현 예정 |

README의 "핵심 기능"에 **Meetup**, **MissingPet**이 포함되지만, 통계에는 반영되지 않음.

### 4.3 관리자 의사결정 관점

| 관점 | 현재 제공 | 도메인적 필요 |
|------|----------|---------------|
| 성장 | 신규 가입, DAU | ✓ |
| 커뮤니티 | 게시글 | 댓글·실종 제보 |
| 핵심 서비스(Care) | 요청·완료 | ✓ |
| 부가 서비스(Meetup) | - | 모임·참여 |
| 운영 | - | 신고 수 |
| 수익 | 0(미구현) | 매출 |

---

## 5. 결론

### 5.1 도메인적 정합성

- **정의된 부분**: User, Board, Care의 일부는 "사용자 활동"과 "서비스 이용"에 잘 맞게 정의됨.
- **부족한 부분**: Meetup, MissingPet, 댓글, 신고 등이 빠져 있어, **플랫폼 전체**를 기준으로 한 "데이터 집계 및 통계 분석" 정의와는 완전히 맞지 않음.

### 5.2 "데이터 집계 및 통계 분석 기능" 재정의 제안

| 구분 | 현재 | 제안 |
|------|------|------|
| 범위 | User + Board + Care | **User + Board + Care + Meetup + MissingPet + (선택) Report** |
| 활동 | 가입, 로그인, 게시, 케어 요청 | + 댓글, 모임 참여, 실종 제보 |
| 서비스 이용 | 케어 요청/완료 | + 모임 생성/참여, 실종 제보 |

### 5.3 우선순위

| 우선순위 | 도메인 | 지표 | 이유 |
|---------|--------|------|------|
| 1 | Meetup | newMeetups, meetupParticipants | 핵심 기능, README에 명시 |
| 2 | MissingPet | newMissingPetReports | 핵심 기능, README에 명시 |
| 3 | Report | newReports | 운영 관리 |
| 4 | Board/Comment | newComments | 커뮤니티 활성도 세분화 |
| 5 | Care | newCareApplications | 매칭 시도량 |

### 5.4 최종 정리

- **로직**: 현재 구현은 잘 설계되어 있음.
- **도메인**: "데이터 집계 및 통계 분석" 정의와 현재 모델의 **범위가 일치하지 않음**.
- **권장**: Meetup, MissingPet, Report 중 최소 1~2개를 통계에 추가하면, 도메인 정의와 일치하는 "데이터 집계 및 통계 분석 기능"이 됨.

---

## 6. "매주/월간 얼마나 이루어졌는지" 필요성 분석

> Meetup, MissingPet, Report 도메인에 **주간/월간 집계 통계**가 실제로 필요한지 검토

### 6.1 Meetup

| 항목 | 내용 |
|------|------|
| **지표** | 주간 생성 모임 수, 참여 수 |
| **관리자 사용처** | AdminMeetupController – 목록 조회, 상태 변경, 삭제 (개별 처리) |
| **대시보드에서의 의미** | "산책 모임 기능이 얼마나 쓰이는가" |

**필요성: ✅ 높음**

- Care와 같은 **핵심 서비스**로, "서비스 이용량"을 보는 게 자연스러움.
- "매주 모임 몇 개 생성됐는지" → 기능 활성도, 홍보/개선 여부 판단에 활용 가능.
- 일별 집계 후 프론트에서 7일 합산하면 주간 수치 제공 가능.

---

### 6.2 MissingPet (실종 제보)

| 항목 | 내용 |
|------|------|
| **지표** | 주간 실종 제보 수 |
| **관리자 사용처** | AdminMissingPetController – 목록, 상태 변경, 삭제/복구 (개별 처리) |
| **대시보드에서의 의미** | "실종 제보 기능 사용량" |

**필요성: ⚠️ 낮음~선택**

- **해석 애매**: 제보 수가 많다 = 기능 활성화 vs 실제 실종 증가.
- **성격**: "성장 지표"라기보다 **사회적 기능**에 가까움.
- **관리자 관점**: 개별 제보는 이미 AdminMissingPet에서 처리. 대시보드에 "주간 제보 수"가 있어도 의사결정에 큰 영향은 제한적.
- **대안**: "FOUND/RESOLVED 처리율" 같은 **처리 품질 지표**가 더 의미 있을 수 있음.
- **결론**: 추가해도 되지만, Meetup·Report보다 우선순위는 낮음.

---

### 6.3 Report (신고)

| 항목 | 내용 |
|------|------|
| **지표** | 주간 신고 접수 수 |
| **관리자 사용처** | AdminReportController – 목록, 상세, 처리 (개별 처리) |
| **대시보드에서의 의미** | "관리자 업무량·모니터링" |

**필요성: ✅ 높음 (관점이 다름)**

- Meetup/Care와 달리 **성장 지표**가 아니라 **운영 지표**.
- **활용**:
  - 주간 신고 수 → 처리 인력·업무량 파악
  - 급증 시 → 이슈·악용 가능성 점검
  - PENDING 비율 → 처리 지연 모니터링
- AdminReport는 이미 목록·필터를 제공하지만, **시계열 추이**는 별도 통계가 있어야 파악 가능.
- **결론**: 운영 관점에서 **필요함**.

---

### 6.4 요약

| 도메인 | 주간/월간 집계 필요성 | 성격 | 권장 |
|--------|----------------------|------|------|
| **Meetup** | ✅ 필요 | 성장·활성도 | 추가 권장 |
| **MissingPet** | ⚠️ 선택 | 기능 사용량 (해석 애매) | 우선순위 낮음 |
| **Report** | ✅ 필요 | 운영·업무량 | 추가 권장 |

**최종**: Meetup과 Report는 "매주/월간 얼마나 이루어졌는지" 통계가 **의미 있음**. MissingPet은 선택 사항.

---

## 7. 구현 작업 가이드 (Meetup + Report 통계 추가)

### 7.1 작업 범위

| 레이어 | 변경 파일 | 내용 |
|--------|----------|------|
| Entity | `DailyStatistics.java` | 필드 3개 추가 |
| DB | 마이그레이션 | 컬럼 3개 추가 |
| Repository | Meetup, MeetupParticipants, Report | count 메서드 추가 |
| Service | StatisticsScheduler, StatisticsService | 집계 로직 추가 |
| Frontend | SystemDashboardSection.js | 요약 카드·차트 추가 |

### 7.2 백엔드 상세

#### 7.2.1 DailyStatistics 엔티티

```java
// 추가 필드
@Builder.Default
@Column(name = "new_meetups")
private Integer newMeetups = 0;

@Builder.Default
@Column(name = "meetup_participants")
private Integer meetupParticipants = 0;

@Builder.Default
@Column(name = "new_reports")
private Integer newReports = 0;
```

#### 7.2.2 DB 마이그레이션

```sql
ALTER TABLE dailystatistics
  ADD COLUMN new_meetups INT DEFAULT 0,
  ADD COLUMN meetup_participants INT DEFAULT 0,
  ADD COLUMN new_reports INT DEFAULT 0;
```

- 기존 행: 기본값 0 적용
- Backfill: `POST /statistics/init?days=30` 호출 시 새 로직으로 재집계 (기존 날짜는 `findByStatDate` 존재 시 스킵되므로, **기존 데이터는 유지**됨. 새 날짜만 새 필드 포함)

#### 7.2.3 Repository 메서드 추가

| Repository | 메서드 | 용도 |
|------------|--------|------|
| MeetupRepository | `long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end)` | 해당 일 생성된 모임 수 |
| MeetupParticipantsRepository | `long countByJoinedAtBetween(LocalDateTime start, LocalDateTime end)` | 해당 일 참여한 인원 수 |
| ReportRepository | `long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end)` | 해당 일 접수된 신고 수 |

- **Meetup**: `SpringDataJpaMeetupRepository`에 `countByCreatedAtBetween` (Meetup extends BaseTimeEntity)
- **MeetupParticipants**: `SpringDataJpaMeetupParticipantsRepository`에 `countByJoinedAtBetween` (joinedAt 필드)
- **Report**: `SpringDataJpaReportRepository`에 `countByCreatedAtBetween` (Report에 createdAt)

#### 7.2.4 StatisticsScheduler / StatisticsService

- `aggregateStatisticsForDate()`: 위 3개 count 호출 후 `DailyStatistics` 빌드에 포함
- `calculateTodayStatistics()`: 동일 로직 추가
- 의존성: `MeetupRepository`, `MeetupParticipantsRepository`, `ReportRepository` 주입

### 7.3 프론트엔드 상세

#### 7.3.1 요약 카드

```javascript
// summary 상태 확장
setSummary({
  newUsers: latest.newUsers,
  newPosts: latest.newPosts,
  newCareRequests: latest.newCareRequests,
  newMeetups: latest.newMeetups ?? 0,      // 추가
  meetupParticipants: latest.meetupParticipants ?? 0,  // 추가
  newReports: latest.newReports ?? 0,       // 추가
  activeUsers: latest.activeUsers,
  totalRevenue: latest.totalRevenue
});
```

- 카드 추가: "새 모임", "모임 참여", "신규 신고" (또는 기존 카드 재배치)

#### 7.3.2 차트

- **라인차트**: `newMeetups`, `newReports` 라인 추가 (선택)
- **바차트**: `newMeetups`, `newReports` 바 추가 (선택)
- `meetupParticipants`는 요약 카드만으로도 충분할 수 있음

### 7.4 작업 순서

1. **DB 마이그레이션** – 컬럼 추가
2. **Entity** – DailyStatistics 필드 추가
3. **Repository** – Meetup, MeetupParticipants, Report에 count 메서드 추가
4. **StatisticsScheduler** – `aggregateStatisticsForDate()` 수정
5. **StatisticsService** – `calculateTodayStatistics()` 수정
6. **프론트엔드** – 요약 카드·차트 수정
7. **Backfill** (선택) – 기존 행은 스킵되므로 새 컬럼은 0. **과거 구간에 새 지표를 채우려면** 해당 기간 `dailystatistics` 삭제 후 `POST /statistics/init?days=30` 호출

### 7.5 주의사항

- **Soft Delete**: Meetup은 `isDeleted` 있음. Board/Care와 동일하게 **삭제 포함** count. 필요 시 `AndIsDeletedFalse` 조건 추가.
- **호환성**: 프론트에서 `?? 0` 등으로 null 대비. 백엔드 배포 전 프론트 배포 시 기존 응답에 새 필드 없어도 에러 없도록 처리.
