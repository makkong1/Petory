# Place Candidate Promotion System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 수집된 장소 후보(place_candidates)를 4-gate 판정 엔진으로 자동 검증 후 확정 장소(places)로 승격하고, Admin API 5개로 관리자 검수를 지원한다.

**Architecture:** 새 `domain/place/` 패키지에 엔티티·리포지토리·판정 엔진·Admin API를 구성한다. LocationImportService의 신규 INSERT 경로를 차단하고, pet-data-api가 place_candidates 적재 전용 엔드포인트를 호출하도록 변경한다.

**Tech Stack:** Spring Boot 3.5.7 / Java 17 / JPA+Hibernate / MySQL 8.0 / JUnit 5+Mockito / Python 3

---

## 파일 구조

### 신규 생성 (Petory)

```
backend/main/java/com/linkup/Petory/domain/place/
  entity/
    PlaceStatus.java
    CandidateDecisionStatus.java
    Place.java
    PlaceCandidate.java
    PlaceFact.java
  repository/
    PlaceRepository.java
    PlaceCandidateRepository.java
    PlaceFactRepository.java
  service/
    NameQualityChecker.java
    GeoUtil.java
    StringSimilarityUtil.java
    PublicDataMatcher.java
    PlaceCandidateJudgmentService.java
    PlaceJudgmentScheduler.java
    PlaceCandidateAdminService.java
    PlaceAdminService.java
  controller/
    PlaceCandidateAdminController.java
    PlaceAdminController.java
  dto/
    PlaceCandidateDto.java
    PlaceDto.java
    CandidateApproveRequest.java
    CandidateRejectRequest.java
    BatchIngestRequest.java

backend/test/java/com/linkup/Petory/domain/place/
  service/
    NameQualityCheckerTest.java
    GeoUtilTest.java
    PlaceCandidateJudgmentServiceTest.java
  controller/
    PlaceCandidateAdminControllerTest.java
    PlaceAdminControllerTest.java
```

### 수정 (Petory)

```
domain/location/repository/SpringDataJpaLocationServiceRepository.java  (+bounding box query)
domain/location/service/LocationImportService.java                       (+write guard)
```

### 신규 생성 (pet-data-api)

```
app/ingestion/petory_client.py   (HTTP 클라이언트)
```

### 수정 (pet-data-api)

```
app/ingestion/exporter.py        (신규 엔드포인트 호출)
app/platform/core/config.py      (+PETORY_INGEST_URL, +PETORY_INGEST_TOKEN)
```

### DB 마이그레이션

```
docs/migration/db/place_tables_v1.sql
```

---

## Task 1: DB 마이그레이션 SQL

**Files:**
- Create: `docs/migration/db/place_tables_v1.sql`

- [ ] **Step 1: SQL 파일 작성**

```sql
-- docs/migration/db/place_tables_v1.sql

CREATE TABLE places (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                        VARCHAR(150) NOT NULL,
    address                     VARCHAR(255),
    lat                         DOUBLE,
    lng                         DOUBLE,
    category                    VARCHAR(100),
    -- MVP: category = category3(소분류). category1/2 계층은 2단계 마이그레이션에서 추가.
    status                      ENUM('PENDING','ACTIVE','INACTIVE') NOT NULL DEFAULT 'PENDING',
    primary_source              VARCHAR(50),
    confidence                  FLOAT,
    legacy_locationservice_id   BIGINT NULL,
    activated_by                VARCHAR(100) NULL,
    activated_at                DATETIME NULL,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_places_status_confidence (status, confidence DESC),
    INDEX idx_places_legacy_ls_id      (legacy_locationservice_id)
);

CREATE TABLE place_candidates (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    raw_name                    VARCHAR(255),
    raw_address                 VARCHAR(255),
    lat                         DOUBLE,
    lng                         DOUBLE,
    collected_from              VARCHAR(100),
    evidence_text               TEXT,
    confidence_score            FLOAT,
    decision_status             ENUM('PENDING','AUTO_APPROVED','ADMIN_APPROVED','NEEDS_REVIEW','REJECTED')
                                    NOT NULL DEFAULT 'PENDING',
    decision_reason             TEXT,
    score_breakdown             JSON,
    matched_place_id            BIGINT NULL,
    matched_locationservice_id  BIGINT NULL,
    rejection_reason            VARCHAR(255) NULL,
    collected_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by                 VARCHAR(100) NULL,
    reviewed_at                 DATETIME NULL,

    INDEX idx_candidates_status_score (decision_status, confidence_score DESC),
    INDEX idx_candidates_dedup        (raw_name(100), raw_address(100)),
    INDEX idx_candidates_collected    (collected_at),

    CONSTRAINT fk_candidates_place
        FOREIGN KEY (matched_place_id) REFERENCES places (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidates_locationservice
        FOREIGN KEY (matched_locationservice_id) REFERENCES locationservice (idx) ON DELETE SET NULL
);

CREATE TABLE place_facts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id        BIGINT NOT NULL,
    fact_type       VARCHAR(100),
    value_text      TEXT,
    value_json      JSON NULL,
    source          VARCHAR(100),
    confidence      FLOAT,
    observed_at     DATE,

    INDEX idx_facts_place_type (place_id, fact_type),

    CONSTRAINT fk_facts_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE
);
```

- [ ] **Step 2: DB에 직접 실행 후 테이블 확인**

```bash
mysql -u root -p petory < docs/migration/db/place_tables_v1.sql
mysql -u root -p petory -e "SHOW TABLES LIKE 'place%';"
```

Expected:
```
place_candidates
place_facts
places
```

- [ ] **Step 3: Commit**

```bash
git add docs/migration/db/place_tables_v1.sql
git commit -m "feat(place): place_candidates/places/place_facts 테이블 생성 SQL"
```

---

## Task 2: 열거형 + 엔티티

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/entity/PlaceStatus.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/entity/CandidateDecisionStatus.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/entity/Place.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/entity/PlaceCandidate.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/entity/PlaceFact.java`

- [ ] **Step 1: 열거형 2개 작성**

`PlaceStatus.java`:
```java
package com.linkup.Petory.domain.place.entity;

public enum PlaceStatus {
    PENDING, ACTIVE, INACTIVE
}
```

`CandidateDecisionStatus.java`:
```java
package com.linkup.Petory.domain.place.entity;

public enum CandidateDecisionStatus {
    PENDING, AUTO_APPROVED, ADMIN_APPROVED, NEEDS_REVIEW, REJECTED
}
```

- [ ] **Step 2: Place 엔티티 작성**

```java
package com.linkup.Petory.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "places", indexes = {
    @Index(name = "idx_places_status_confidence", columnList = "status, confidence DESC"),
    @Index(name = "idx_places_legacy_ls_id", columnList = "legacy_locationservice_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Place {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String address;

    private Double lat;
    private Double lng;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PlaceStatus status = PlaceStatus.PENDING;

    @Column(name = "primary_source", length = 50)
    private String primarySource;

    private Double confidence;

    @Column(name = "legacy_locationservice_id")
    private Long legacyLocationserviceId;

    @Column(name = "activated_by", length = 100)
    private String activatedBy;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: PlaceCandidate 엔티티 작성**

```java
package com.linkup.Petory.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "place_candidates", indexes = {
    @Index(name = "idx_candidates_status_score", columnList = "decision_status, confidence_score DESC"),
    @Index(name = "idx_candidates_dedup", columnList = "raw_name, raw_address"),
    @Index(name = "idx_candidates_collected", columnList = "collected_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlaceCandidate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_name") private String rawName;
    @Column(name = "raw_address") private String rawAddress;
    private Double lat;
    private Double lng;

    @Column(name = "collected_from", length = 100)
    private String collectedFrom;

    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false)
    @Builder.Default
    private CandidateDecisionStatus decisionStatus = CandidateDecisionStatus.PENDING;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "score_breakdown", columnDefinition = "JSON")
    private String scoreBreakdown;

    @Column(name = "matched_place_id")
    private Long matchedPlaceId;

    @Column(name = "matched_locationservice_id")
    private Long matchedLocationserviceId;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "collected_at", nullable = false)
    @Builder.Default
    private LocalDateTime collectedAt = LocalDateTime.now();

    @Column(name = "reviewed_by", length = 100) private String reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
}
```

- [ ] **Step 4: PlaceFact 엔티티 작성**

```java
package com.linkup.Petory.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "place_facts", indexes = {
    @Index(name = "idx_facts_place_type", columnList = "place_id, fact_type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlaceFact {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "fact_type", length = 100)
    private String factType;

    @Column(name = "value_text", columnDefinition = "TEXT")
    private String valueText;

    @Column(name = "value_json", columnDefinition = "JSON")
    private String valueJson;

    @Column(length = 100)
    private String source;

    private Double confidence;

    @Column(name = "observed_at")
    private LocalDate observedAt;
}
```

- [ ] **Step 5: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/
git commit -m "feat(place): Place/PlaceCandidate/PlaceFact 엔티티 추가"
```

---

## Task 3: 리포지토리

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/repository/PlaceRepository.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/repository/PlaceCandidateRepository.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/repository/PlaceFactRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java`

- [ ] **Step 1: PlaceRepository 작성**

```java
package com.linkup.Petory.domain.place.repository;

import com.linkup.Petory.domain.place.entity.Place;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Page<Place> findByStatus(PlaceStatus status, Pageable pageable);
}
```

- [ ] **Step 2: PlaceCandidateRepository 작성**

```java
package com.linkup.Petory.domain.place.repository;

import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

    Page<PlaceCandidate> findByDecisionStatus(CandidateDecisionStatus status, Pageable pageable);

    List<PlaceCandidate> findByDecisionStatus(CandidateDecisionStatus status);

    int countByRawNameAndRawAddress(String rawName, String rawAddress);

    @Query("SELECT COUNT(DISTINCT c.collectedFrom) FROM PlaceCandidate c " +
           "WHERE c.rawName = :name AND c.rawAddress = :address")
    int countDistinctSourcesByRawNameAndAddress(
        @Param("name") String rawName, @Param("address") String rawAddress);
}
```

- [ ] **Step 3: PlaceFactRepository 작성**

```java
package com.linkup.Petory.domain.place.repository;

import com.linkup.Petory.domain.place.entity.PlaceFact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceFactRepository extends JpaRepository<PlaceFact, Long> {
}
```

- [ ] **Step 4: SpringDataJpaLocationServiceRepository에 bounding box 쿼리 추가**

파일: `backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java`

기존 `@Query` 목록의 마지막 쿼리 다음에 추가:

```java
@Query(value = "SELECT * FROM locationservice " +
               "WHERE latitude BETWEEN :minLat AND :maxLat " +
               "AND longitude BETWEEN :minLng AND :maxLng " +
               "AND is_deleted = 0",
       nativeQuery = true)
List<LocationService> findInBoundingBox(
    @Param("minLat") double minLat, @Param("maxLat") double maxLat,
    @Param("minLng") double minLng, @Param("maxLng") double maxLng);

@Query("SELECT ls FROM LocationService ls " +
       "WHERE ls.name LIKE CONCAT(:prefix, '%') AND ls.isDeleted = false")
List<LocationService> findByNamePrefix(@Param("prefix") String prefix);
```

- [ ] **Step 5: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/repository/ \
        backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java
git commit -m "feat(place): 리포지토리 추가 및 locationservice bounding box 쿼리 추가"
```

---

## Task 4: NameQualityChecker

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/NameQualityChecker.java`
- Create: `backend/test/java/com/linkup/Petory/domain/place/service/NameQualityCheckerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.linkup.Petory.domain.place.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.linkup.Petory.domain.place.service.NameQualityChecker.NameCheckResult.*;

class NameQualityCheckerTest {

    private final NameQualityChecker checker = new NameQualityChecker();

    @Test void hardBlacklistReturnsHardReject() {
        assertThat(checker.check("식사")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("기념일")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("맛집")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("주말")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("데이트")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("공원")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("산책")).isEqualTo(HARD_REJECT);
    }

    @Test void softBlacklistReturnsSoftRisk() {
        assertThat(checker.check("프렌치")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("벚꽃")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("감성")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("라운지")).isEqualTo(SOFT_RISK);
        assertThat(checker.check("살롱")).isEqualTo(SOFT_RISK);
    }

    @Test void twoCharsOrLessReturnsHardReject() {
        assertThat(checker.check("강")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("AB")).isEqualTo(HARD_REJECT);
    }

    @Test void nullOrEmptyReturnsHardReject() {
        assertThat(checker.check(null)).isEqualTo(HARD_REJECT);
        assertThat(checker.check("")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("   ")).isEqualTo(HARD_REJECT);
    }

    @Test void specialCharsOnlyReturnsHardReject() {
        assertThat(checker.check("!!??")).isEqualTo(HARD_REJECT);
        assertThat(checker.check("---")).isEqualTo(HARD_REJECT);
    }

    @Test void goodNameReturnsOk() {
        assertThat(checker.check("38도씨식당")).isEqualTo(OK);
        assertThat(checker.check("개떼놀이터")).isEqualTo(OK);
        assertThat(checker.check("23플래터")).isEqualTo(OK);
    }

    @Test void isGoodQualityRequiresFourChars() {
        assertThat(checker.isGoodQuality("개떼놀이터")).isTrue();
        assertThat(checker.isGoodQuality("강아")).isFalse();
        assertThat(checker.isGoodQuality("맛집")).isFalse(); // hard blacklist
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.place.service.NameQualityCheckerTest" 2>&1 | tail -5
```

Expected: FAILED (class not found)

- [ ] **Step 3: NameQualityChecker 구현**

```java
package com.linkup.Petory.domain.place.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.Set;

@Component
public class NameQualityChecker {

    public enum NameCheckResult { HARD_REJECT, SOFT_RISK, OK }

    private static final Set<String> HARD_BLACKLIST = Set.of(
        "식사", "기념일", "맛집", "주말", "데이트", "공원", "산책"
    );

    private static final Set<String> SOFT_BLACKLIST = Set.of(
        "프렌치", "벚꽃", "감성", "라운지", "살롱"
    );

    public NameCheckResult check(String name) {
        if (!StringUtils.hasText(name)) return NameCheckResult.HARD_REJECT;
        String t = name.trim();
        if (HARD_BLACKLIST.contains(t)) return NameCheckResult.HARD_REJECT;
        if (SOFT_BLACKLIST.contains(t)) return NameCheckResult.SOFT_RISK;
        if (t.length() <= 2) return NameCheckResult.HARD_REJECT;
        if (t.matches("[^가-힣a-zA-Z0-9]+")) return NameCheckResult.HARD_REJECT;
        return NameCheckResult.OK;
    }

    /** 4글자 이상 + OK 상태. Gate 2 경로B / Gate 3 name_quality 점수에 사용. */
    public boolean isGoodQuality(String name) {
        return check(name) == NameCheckResult.OK
            && name != null && name.trim().length() >= 4;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.place.service.NameQualityCheckerTest"
```

Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 5: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/service/NameQualityChecker.java \
        backend/test/java/com/linkup/Petory/domain/place/service/NameQualityCheckerTest.java
git commit -m "feat(place): NameQualityChecker — hard/soft blacklist 판정"
```

---

## Task 5: GeoUtil + StringSimilarityUtil

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/GeoUtil.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/StringSimilarityUtil.java`
- Create: `backend/test/java/com/linkup/Petory/domain/place/service/GeoUtilTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.linkup.Petory.domain.place.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilTest {

    @Test void samePointIsZeroDistance() {
        assertThat(GeoUtil.haversineMeters(37.5, 126.9, 37.5, 126.9)).isEqualTo(0.0);
    }

    @Test void knownDistanceIsWithinMargin() {
        // 서울시청 → 광화문 약 550m
        double dist = GeoUtil.haversineMeters(37.5665, 126.9780, 37.5759, 126.9769);
        assertThat(dist).isBetween(500.0, 650.0);
    }

    @Test void boundingBoxDeltaCoversRadius() {
        double delta = GeoUtil.latLngDeltaForMeters(500);
        assertThat(delta).isBetween(0.004, 0.006);
    }

    @Test void stringSimilarityIdentical() {
        assertThat(StringSimilarityUtil.normalized("38도씨식당", "38도씨식당")).isEqualTo(1.0);
    }

    @Test void stringSimilarityHighSimilar() {
        double sim = StringSimilarityUtil.normalized("38도씨식당", "38도씨 식당");
        assertThat(sim).isGreaterThan(0.8);
    }

    @Test void stringSimilarityLowDifferent() {
        double sim = StringSimilarityUtil.normalized("동물병원", "강아지카페");
        assertThat(sim).isLessThan(0.5);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.place.service.GeoUtilTest" 2>&1 | tail -3
```

Expected: FAILED (class not found)

- [ ] **Step 3: GeoUtil 구현**

```java
package com.linkup.Petory.domain.place.service;

public final class GeoUtil {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtil() {}

    /** Haversine 공식으로 두 좌표 사이 거리(미터) 계산. */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** meters 반경을 커버하는 lat/lng 오프셋(도). bounding box 사전 필터에 사용. */
    public static double latLngDeltaForMeters(double meters) {
        return meters / EARTH_RADIUS_M * (180.0 / Math.PI);
    }
}
```

- [ ] **Step 4: StringSimilarityUtil 구현**

```java
package com.linkup.Petory.domain.place.service;

public final class StringSimilarityUtil {

    private StringSimilarityUtil() {}

    /**
     * Levenshtein 거리 기반 정규화 유사도 [0.0, 1.0].
     * 1.0 = 동일, 0.0 = 완전히 다름.
     */
    public static double normalized(String a, String b) {
        if (a == null && b == null) return 1.0;
        if (a == null || b == null) return 0.0;
        String s1 = a.trim().toLowerCase();
        String s2 = b.trim().toLowerCase();
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshtein(s1, s2) / maxLen;
    }

    private static int levenshtein(String s, String t) {
        int m = s.length(), n = t.length();
        int[] prev = new int[n + 1], curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                curr[j] = s.charAt(i - 1) == t.charAt(j - 1)
                    ? prev[j - 1]
                    : 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[n];
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.place.service.GeoUtilTest"
```

Expected: 6 tests passed

- [ ] **Step 6: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/service/GeoUtil.java \
        backend/main/java/com/linkup/Petory/domain/place/service/StringSimilarityUtil.java \
        backend/test/java/com/linkup/Petory/domain/place/service/GeoUtilTest.java
git commit -m "feat(place): GeoUtil(Haversine) + StringSimilarityUtil(Levenshtein)"
```

---

## Task 6: PublicDataMatcher

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/PublicDataMatcher.java`

- [ ] **Step 1: MatchResult inner class 포함 PublicDataMatcher 작성**

```java
package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.SpringDataJpaLocationServiceRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicDataMatcher {

    private final SpringDataJpaLocationServiceRepository lsRepo;

    @Getter
    public static class MatchResult {
        private final Long locationServiceId;
        private final double nameSimilarity;
        private final double distanceMeters;

        public MatchResult(Long locationServiceId, double nameSimilarity, double distanceMeters) {
            this.locationServiceId = locationServiceId;
            this.nameSimilarity = nameSimilarity;
            this.distanceMeters = distanceMeters;
        }
    }

    /**
     * strong match: 이름 유사도 ≥ 0.85 AND (주소 정규화 일치 OR 좌표 150m 이내)
     */
    public Optional<MatchResult> findStrongMatch(String name, String address, Double lat, Double lng) {
        List<LocationService> candidates = loadCandidates(name, lat, lng, 500.0);
        for (LocationService ls : candidates) {
            double nameSim = StringSimilarityUtil.normalized(name, ls.getName());
            if (nameSim < 0.85) continue;
            boolean addressMatch = StringUtils.hasText(address)
                && StringUtils.hasText(ls.getAddress())
                && normalizeAddress(address).equals(normalizeAddress(ls.getAddress()));
            boolean coordMatch = lat != null && lng != null
                && ls.getLatitude() != null && ls.getLongitude() != null
                && GeoUtil.haversineMeters(lat, lng, ls.getLatitude(), ls.getLongitude()) <= 150.0;
            if (addressMatch || coordMatch) {
                double dist = (lat != null && ls.getLatitude() != null)
                    ? GeoUtil.haversineMeters(lat, lng, ls.getLatitude(), ls.getLongitude())
                    : Double.MAX_VALUE;
                return Optional.of(new MatchResult(ls.getIdx(), nameSim, dist));
            }
        }
        return Optional.empty();
    }

    /**
     * medium match: 이름 유사도 0.6~0.85 OR 좌표 150~500m 이내
     */
    public Optional<MatchResult> findMediumMatch(String name, String address, Double lat, Double lng) {
        List<LocationService> candidates = loadCandidates(name, lat, lng, 500.0);
        MatchResult best = null;
        for (LocationService ls : candidates) {
            double nameSim = StringSimilarityUtil.normalized(name, ls.getName());
            double dist = (lat != null && ls.getLatitude() != null)
                ? GeoUtil.haversineMeters(lat, lng, ls.getLatitude(), ls.getLongitude())
                : Double.MAX_VALUE;
            boolean nameInRange = nameSim >= 0.6 && nameSim < 0.85;
            boolean coordInRange = dist >= 150.0 && dist <= 500.0;
            if (nameInRange || coordInRange) {
                if (best == null || nameSim > best.getNameSimilarity()) {
                    best = new MatchResult(ls.getIdx(), nameSim, dist);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private List<LocationService> loadCandidates(String name, Double lat, Double lng, double radiusMeters) {
        if (lat != null && lng != null) {
            double delta = GeoUtil.latLngDeltaForMeters(radiusMeters);
            return lsRepo.findInBoundingBox(lat - delta, lat + delta, lng - delta, lng + delta);
        }
        // 좌표 없으면 이름 첫 2글자로 pre-filter
        String prefix = (name != null && name.length() >= 2) ? name.substring(0, 2) : "";
        return StringUtils.hasText(prefix) ? lsRepo.findByNamePrefix(prefix) : List.of();
    }

    private String normalizeAddress(String address) {
        if (address == null) return "";
        // 공백 제거 + 번지/호 표기 통일
        return address.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("([0-9]+)가", "$1")
            .toLowerCase();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava 2>&1 | grep -E "error|warning|BUILD"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/service/PublicDataMatcher.java
git commit -m "feat(place): PublicDataMatcher — strong/medium match 판정"
```

---

## Task 7: PlaceCandidateJudgmentService (4-gate 엔진)

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/PlaceCandidateJudgmentService.java`
- Create: `backend/test/java/com/linkup/Petory/domain/place/service/PlaceCandidateJudgmentServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.linkup.Petory.domain.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.place.entity.*;
import com.linkup.Petory.domain.place.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceCandidateJudgmentServiceTest {

    @Mock PlaceCandidateRepository candidateRepo;
    @Mock PlaceRepository placeRepo;
    @Mock PublicDataMatcher matcher;

    // NameQualityChecker는 의존성 없음 → 실제 인스턴스 사용
    private final NameQualityChecker nameChecker = new NameQualityChecker();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlaceCandidateJudgmentService sut;

    private PlaceCandidate candidate(String name, String address, Double lat, Double lng) {
        return PlaceCandidate.builder()
            .rawName(name).rawAddress(address).lat(lat).lng(lng)
            .collectedFrom("PET_DATA_API").build();
    }

    @BeforeEach void setUp() {
        // @InjectMocks 미사용: NameQualityChecker·ObjectMapper를 직접 주입
        sut = new PlaceCandidateJudgmentService(
            candidateRepo, placeRepo, nameChecker, matcher, objectMapper);
        when(matcher.findStrongMatch(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(matcher.findMediumMatch(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(candidateRepo.countByRawNameAndRawAddress(any(), any())).thenReturn(0);
        when(candidateRepo.countDistinctSourcesByRawNameAndAddress(any(), any())).thenReturn(0);
        when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubPlaceSave(long id) {
        when(placeRepo.save(any())).thenAnswer(inv -> {
            Place p = inv.getArgument(0);
            return Place.builder().id(id).name(p.getName())
                .status(PlaceStatus.PENDING).confidence(p.getConfidence()).build();
        });
    }

    @Test void hardBlacklist_isRejected() {
        PlaceCandidate c = candidate("식사", "서울 마포구 어딘가", 37.5, 126.9);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
        verify(placeRepo, never()).save(any());
    }

    @Test void noAddressAndNoCoords_isRejected() {
        PlaceCandidate c = candidate("개떼놀이터", null, null, null);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
    }

    @Test void softBlacklist_withAddress_isNeedsReview() {
        // 프렌치(soft) + 주소O + medium match 없음 → score=0.0+0.2+0.1=0.3
        // canAutoApprove 불가(risk_flag) → score=0.3 ≥ 0.3 → NEEDS_REVIEW
        PlaceCandidate c = candidate("프렌치", "서울 마포구 어딘가", 37.5, 126.9);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.NEEDS_REVIEW);
    }

    @Test void softBlacklist_withStrongMatch_isAutoApproved() {
        // soft blacklist여도 strong match → Gate 2 통과
        stubPlaceSave(1L);
        PlaceCandidate c = candidate("프렌치", "서울 마포구 어딘가", 37.5, 126.9);
        when(matcher.findStrongMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(99L, 0.9, 100.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.AUTO_APPROVED);
        assertThat(c.getMatchedLocationserviceId()).isEqualTo(99L);
    }

    @Test void strongMatchPublicData_isAutoApproved() {
        stubPlaceSave(1L);
        PlaceCandidate c = candidate("38도씨식당", "서울 마포구 와우산로17길 19-17", 37.549, 126.921);
        when(matcher.findStrongMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(33538L, 0.95, 50.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.AUTO_APPROVED);
        assertThat(c.getConfidenceScore()).isEqualTo(0.9);
        assertThat(c.getMatchedLocationserviceId()).isEqualTo(33538L);
    }

    @Test void gate4_altBusinessDetected_isNeedsReview() {
        // 이름O + 주소O + 좌표O + medium match O → 기본 score=0.7
        // alt_biz -0.3 → score=0.4, canAutoApprove=false → NEEDS_REVIEW
        PlaceCandidate c = candidate("큰강아지카페",
            "경기도 남양주시 진건읍 사릉로280번길 7 2층 봉맨션", 37.65, 127.19);
        when(matcher.findMediumMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(34136L, 0.7, 200.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.NEEDS_REVIEW);
        verify(placeRepo, never()).save(any());
    }

    @Test void gate4_highScore_allConditionsMet_isAutoApproved() {
        // name(0.1) + addr(0.2) + coord(0.1) + medium(0.3) = 0.7, 조건 전부 충족
        stubPlaceSave(2L);
        PlaceCandidate c = candidate("개떼놀이터",
            "경기도 남양주시 진건읍 사릉로280번길 7", 37.65, 127.19);
        when(matcher.findMediumMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(34136L, 0.7, 200.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.AUTO_APPROVED);
    }

    @Test void gate1_twoCharsName_isRejected() {
        // 2글자 → Gate 1 패턴 reject
        PlaceCandidate c = candidate("개떼", "경기도 어딘가", 37.65, 127.19);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
    }

    @Test void gate4_scoreBelow0p3_isRejected() {
        // name(0.1) + coord(0.1) + no addr + no medium = 0.2 < 0.3 → REJECTED
        PlaceCandidate c = candidate("개떼놀이터", null, 37.65, 127.19);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.place.service.PlaceCandidateJudgmentServiceTest" 2>&1 | tail -3
```

Expected: FAILED

- [ ] **Step 3: PlaceCandidateJudgmentService 구현**

```java
package com.linkup.Petory.domain.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.place.entity.*;
import com.linkup.Petory.domain.place.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceCandidateJudgmentService {

    private final PlaceCandidateRepository candidateRepo;
    private final PlaceRepository placeRepo;
    private final NameQualityChecker nameChecker;
    private final PublicDataMatcher publicDataMatcher;
    private final ObjectMapper objectMapper;

    private static final Pattern ALT_BUSINESS_PATTERN = Pattern.compile(
        "(?:B?\\d+층|지하\\s*\\d+층)\\s+([가-힣a-zA-Z0-9]+(?:\\s[가-힣a-zA-Z0-9]+)?)\\s*$"
    );

    @Transactional
    public void judge(PlaceCandidate candidate) {
        Map<String, Object> bd = new LinkedHashMap<>();
        String rawName = candidate.getRawName() != null ? candidate.getRawName().trim() : null;
        String rawAddress = candidate.getRawAddress();
        Double lat = candidate.getLat();
        Double lng = candidate.getLng();

        // === Gate 1: Hard Reject + Risk Flag ===
        NameQualityChecker.NameCheckResult nameResult = nameChecker.check(rawName);
        if (nameResult == NameQualityChecker.NameCheckResult.HARD_REJECT) {
            doReject(candidate, "hard_reject:name", bd); return;
        }
        boolean hasAddress = StringUtils.hasText(rawAddress);
        boolean hasCoords = lat != null && lng != null;
        if (!hasAddress && !hasCoords) {
            doReject(candidate, "hard_reject:no_address_no_coords", bd); return;
        }
        boolean riskFlag = (nameResult == NameQualityChecker.NameCheckResult.SOFT_RISK);
        bd.put("risk_flag", riskFlag);

        // === Gate 2: Strong Match (risk_flag 있어도 통과 가능) ===
        Optional<PublicDataMatcher.MatchResult> strong =
            publicDataMatcher.findStrongMatch(rawName, rawAddress, lat, lng);
        if (strong.isPresent()) {
            doAutoApprove(candidate, 0.9, strong.get().getLocationServiceId(),
                "strong_match:public_data", bd); return;
        }
        // Path B: self-trust
        if (!riskFlag && nameChecker.isGoodQuality(rawName) && hasAddress && hasCoords) {
            int dup = candidateRepo.countByRawNameAndRawAddress(rawName, rawAddress);
            int src = candidateRepo.countDistinctSourcesByRawNameAndAddress(rawName, rawAddress);
            if (dup >= 3 || src >= 2) {
                doAutoApprove(candidate, 0.9, null, "strong_match:self_trust", bd); return;
            }
        }

        // === Gate 3: Scoring ===
        double score = 0.0;

        double nq = nameChecker.isGoodQuality(rawName) ? 0.1 : 0.0;
        bd.put("name_quality", nq); score += nq;

        double addrScore = hasAddress ? 0.2 : 0.0;
        bd.put("road_address", addrScore); score += addrScore;

        double coordScore = hasCoords ? 0.1 : 0.0;
        bd.put("coord_exists", coordScore); score += coordScore;

        boolean nameInAddr = hasAddress && rawName != null && rawAddress.contains(rawName);
        double niaScore = nameInAddr ? 0.2 : 0.0;
        bd.put("name_in_address", niaScore); score += niaScore;

        boolean altBiz = detectAltBusiness(rawName, rawAddress);
        double altPenalty = altBiz ? -0.3 : 0.0;
        bd.put("alt_business_detected", altPenalty); score += altPenalty;

        Optional<PublicDataMatcher.MatchResult> medium =
            publicDataMatcher.findMediumMatch(rawName, rawAddress, lat, lng);
        double medScore = medium.isPresent() ? 0.3 : 0.0;
        bd.put("public_medium_match", medScore); score += medScore;

        int dup = candidateRepo.countByRawNameAndRawAddress(
            rawName != null ? rawName : "", rawAddress != null ? rawAddress : "");
        double dupBoost = Math.min(Math.log(dup + 1) * 0.1, 0.2);
        bd.put("duplicate_boost", dupBoost); score += dupBoost;

        score = Math.round(score * 1000.0) / 1000.0;
        bd.put("total", score);

        // === Gate 4: Threshold ===
        candidate.setConfidenceScore(score);

        boolean mediumNameOk = !medium.isPresent() || medium.get().getNameSimilarity() >= 0.6;
        boolean canAutoApprove = score >= 0.6 && nq > 0 && !altBiz && !riskFlag && mediumNameOk;

        if (canAutoApprove) {
            Long lsId = medium.map(PublicDataMatcher.MatchResult::getLocationServiceId).orElse(null);
            doAutoApprove(candidate, score, lsId, "threshold_passed", bd);
        } else if (score >= 0.3) {
            doNeedsReview(candidate, "score_below_auto_threshold", bd);
        } else {
            doReject(candidate, "score_too_low", bd);
        }
    }

    private void doAutoApprove(PlaceCandidate c, double score, Long lsId,
                                String reason, Map<String, Object> bd) {
        bd.put("gate", score == 0.9 ? "GATE2_STRONG_MATCH" : "GATE4_THRESHOLD");
        bd.put("decision", "AUTO_APPROVED");

        Place place = placeRepo.save(Place.builder()
            .name(c.getRawName()).address(c.getRawAddress())
            .lat(c.getLat()).lng(c.getLng())
            .status(PlaceStatus.PENDING)
            .primarySource(c.getCollectedFrom())
            .confidence(score)
            .build());

        c.setDecisionStatus(CandidateDecisionStatus.AUTO_APPROVED);
        c.setConfidenceScore(score);
        c.setDecisionReason(reason);
        c.setScoreBreakdown(toJson(bd));
        c.setMatchedPlaceId(place.getId());
        c.setMatchedLocationserviceId(lsId);
        candidateRepo.save(c);
        log.info("[Judgment] AUTO_APPROVED id={} name={} reason={}", c.getId(), c.getRawName(), reason);
    }

    private void doNeedsReview(PlaceCandidate c, String reason, Map<String, Object> bd) {
        bd.put("decision", "NEEDS_REVIEW");
        c.setDecisionStatus(CandidateDecisionStatus.NEEDS_REVIEW);
        c.setDecisionReason(reason);
        c.setScoreBreakdown(toJson(bd));
        candidateRepo.save(c);
    }

    private void doReject(PlaceCandidate c, String reason, Map<String, Object> bd) {
        bd.put("decision", "REJECTED");
        c.setDecisionStatus(CandidateDecisionStatus.REJECTED);
        c.setDecisionReason(reason);
        c.setRejectionReason(reason);
        c.setScoreBreakdown(toJson(bd));
        candidateRepo.save(c);
    }

    private boolean detectAltBusiness(String rawName, String rawAddress) {
        if (!StringUtils.hasText(rawAddress) || !StringUtils.hasText(rawName)) return false;
        Matcher m = ALT_BUSINESS_PATTERN.matcher(rawAddress.trim());
        if (m.find()) {
            String detected = m.group(1).trim();
            return !detected.equals(rawName.trim());
        }
        return false;
    }

    private String toJson(Map<String, Object> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.place.service.PlaceCandidateJudgmentServiceTest"
```

Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 5: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/service/PlaceCandidateJudgmentService.java \
        backend/test/java/com/linkup/Petory/domain/place/service/PlaceCandidateJudgmentServiceTest.java
git commit -m "feat(place): PlaceCandidateJudgmentService — 4-gate 판정 엔진"
```

---

## Task 8: PlaceJudgmentScheduler

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/PlaceJudgmentScheduler.java`

- [ ] **Step 1: 스케줄러 작성**

```java
package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import com.linkup.Petory.domain.place.repository.PlaceCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceJudgmentScheduler {

    private final PlaceCandidateRepository candidateRepo;
    private final PlaceCandidateJudgmentService judgmentService;

    /** 5분마다 PENDING 후보를 일괄 판정. fixedDelay로 앞 배치 완료 후 다음 시작 보장. */
    @Scheduled(fixedDelayString = "${place.judgment.delay-ms:300000}")
    public void runJudgment() {
        List<PlaceCandidate> pending = candidateRepo.findByDecisionStatus(CandidateDecisionStatus.PENDING);
        if (pending.isEmpty()) return;
        log.info("[PlaceJudgmentScheduler] 판정 시작 count={}", pending.size());
        int ok = 0, err = 0;
        for (PlaceCandidate c : pending) {
            try { judgmentService.judge(c); ok++; }
            catch (Exception e) { log.error("[PlaceJudgmentScheduler] 판정 실패 id={}", c.getId(), e); err++; }
        }
        log.info("[PlaceJudgmentScheduler] 완료 ok={} err={}", ok, err);
    }
}
```

- [ ] **Step 2: `@EnableScheduling` 확인**

`PetoryApplication.java`에 `@EnableScheduling`이 없으면 추가:

```java
@EnableScheduling  // 이미 있으면 그대로
@SpringBootApplication
public class PetoryApplication { ... }
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/service/PlaceJudgmentScheduler.java \
        backend/main/java/com/linkup/Petory/PetoryApplication.java
git commit -m "feat(place): PlaceJudgmentScheduler — 5분 주기 PENDING 후보 자동 판정"
```

---

## Task 9: Admin API — 후보 검수 (DTOs + Service + Controller)

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/dto/PlaceCandidateDto.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/dto/CandidateApproveRequest.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/dto/CandidateRejectRequest.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/PlaceCandidateAdminService.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/controller/PlaceCandidateAdminController.java`
- Create: `backend/test/java/com/linkup/Petory/domain/place/controller/PlaceCandidateAdminControllerTest.java`

- [ ] **Step 1: DTOs 작성**

`PlaceCandidateDto.java`:
```java
package com.linkup.Petory.domain.place.dto;

import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PlaceCandidateDto {
    private Long id;
    private String rawName, rawAddress;
    private Double lat, lng;
    private String collectedFrom, evidenceText, decisionReason;
    private Double confidenceScore;
    private CandidateDecisionStatus decisionStatus;
    private Long matchedPlaceId, matchedLocationserviceId;
    private String collectedAt;

    public static PlaceCandidateDto from(PlaceCandidate c) {
        return PlaceCandidateDto.builder()
            .id(c.getId()).rawName(c.getRawName()).rawAddress(c.getRawAddress())
            .lat(c.getLat()).lng(c.getLng()).collectedFrom(c.getCollectedFrom())
            .evidenceText(c.getEvidenceText()).decisionReason(c.getDecisionReason())
            .confidenceScore(c.getConfidenceScore()).decisionStatus(c.getDecisionStatus())
            .matchedPlaceId(c.getMatchedPlaceId())
            .matchedLocationserviceId(c.getMatchedLocationserviceId())
            .collectedAt(c.getCollectedAt() != null ? c.getCollectedAt().toString() : null)
            .build();
    }
}
```

`CandidateApproveRequest.java`:
```java
package com.linkup.Petory.domain.place.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class CandidateApproveRequest {
    private String overrideName;
    private String overrideAddress;
    private String overrideCategory;
    private Double overrideLat;
    private Double overrideLng;
}
```

`CandidateRejectRequest.java`:
```java
package com.linkup.Petory.domain.place.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class CandidateRejectRequest {
    private String rejectionReason;
}
```

- [ ] **Step 2: PlaceCandidateAdminService 작성**

```java
package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.dto.CandidateApproveRequest;
import com.linkup.Petory.domain.place.dto.CandidateRejectRequest;
import com.linkup.Petory.domain.place.dto.PlaceCandidateDto;
import com.linkup.Petory.domain.place.entity.*;
import com.linkup.Petory.domain.place.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlaceCandidateAdminService {

    private final PlaceCandidateRepository candidateRepo;
    private final PlaceRepository placeRepo;

    public Page<PlaceCandidateDto> listByStatus(CandidateDecisionStatus status, Pageable pageable) {
        return candidateRepo.findByDecisionStatus(status, pageable).map(PlaceCandidateDto::from);
    }

    @Transactional
    public PlaceCandidateDto approve(Long id, CandidateApproveRequest req, String adminUsername) {
        PlaceCandidate c = candidateRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // idempotent: 이미 place가 연결된 경우 기존 반환
        if (c.getMatchedPlaceId() != null) {
            return PlaceCandidateDto.from(c);
        }

        // 상태 guard
        if (c.getDecisionStatus() != CandidateDecisionStatus.PENDING
            && c.getDecisionStatus() != CandidateDecisionStatus.NEEDS_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "approve 허용 상태: PENDING, NEEDS_REVIEW. 현재: " + c.getDecisionStatus());
        }

        String name = Optional.ofNullable(req.getOverrideName())
            .filter(StringUtils::hasText).orElse(c.getRawName());
        String address = Optional.ofNullable(req.getOverrideAddress())
            .filter(StringUtils::hasText).orElse(c.getRawAddress());
        String category = Optional.ofNullable(req.getOverrideCategory())
            .filter(StringUtils::hasText).orElse(null);
        Double lat = req.getOverrideLat() != null ? req.getOverrideLat() : c.getLat();
        Double lng = req.getOverrideLng() != null ? req.getOverrideLng() : c.getLng();

        Place place = placeRepo.save(Place.builder()
            .name(name).address(address).lat(lat).lng(lng).category(category)
            .status(PlaceStatus.PENDING)
            .primarySource(c.getCollectedFrom())
            .confidence(c.getConfidenceScore())
            .build());

        c.setDecisionStatus(CandidateDecisionStatus.ADMIN_APPROVED);
        c.setMatchedPlaceId(place.getId());
        c.setReviewedBy(adminUsername);
        c.setReviewedAt(LocalDateTime.now());
        candidateRepo.save(c);
        return PlaceCandidateDto.from(c);
    }

    @Transactional
    public PlaceCandidateDto reject(Long id, CandidateRejectRequest req, String adminUsername) {
        PlaceCandidate c = candidateRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (c.getDecisionStatus() != CandidateDecisionStatus.PENDING
            && c.getDecisionStatus() != CandidateDecisionStatus.NEEDS_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "reject 허용 상태: PENDING, NEEDS_REVIEW. 현재: " + c.getDecisionStatus());
        }

        c.setDecisionStatus(CandidateDecisionStatus.REJECTED);
        c.setRejectionReason(req.getRejectionReason());
        c.setReviewedBy(adminUsername);
        c.setReviewedAt(LocalDateTime.now());
        candidateRepo.save(c);
        return PlaceCandidateDto.from(c);
    }
}
```

- [ ] **Step 3: PlaceCandidateAdminController 작성**

```java
package com.linkup.Petory.domain.place.controller;

import com.linkup.Petory.domain.place.dto.*;
import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.service.PlaceCandidateAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/place-candidates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class PlaceCandidateAdminController {

    private final PlaceCandidateAdminService service;

    @GetMapping
    public ResponseEntity<Page<PlaceCandidateDto>> list(
        @RequestParam(defaultValue = "NEEDS_REVIEW") CandidateDecisionStatus status,
        @PageableDefault(size = 20, sort = "confidenceScore", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listByStatus(status, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PlaceCandidateDto> approve(
        @PathVariable Long id,
        @RequestBody(required = false) CandidateApproveRequest req,
        Authentication auth
    ) {
        if (req == null) req = new CandidateApproveRequest();
        return ResponseEntity.ok(service.approve(id, req, auth.getName()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PlaceCandidateDto> reject(
        @PathVariable Long id,
        @RequestBody CandidateRejectRequest req,
        Authentication auth
    ) {
        return ResponseEntity.ok(service.reject(id, req, auth.getName()));
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/dto/ \
        backend/main/java/com/linkup/Petory/domain/place/service/PlaceCandidateAdminService.java \
        backend/main/java/com/linkup/Petory/domain/place/controller/PlaceCandidateAdminController.java
git commit -m "feat(place): Admin API — 후보 검수 (list/approve/reject)"
```

---

## Task 10: Admin API — Places 관리 (Service + Controller)

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/dto/PlaceDto.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/PlaceAdminService.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/controller/PlaceAdminController.java`

- [ ] **Step 1: PlaceDto 작성**

```java
package com.linkup.Petory.domain.place.dto;

import com.linkup.Petory.domain.place.entity.Place;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PlaceDto {
    private Long id;
    private String name, address, category;
    private Double lat, lng, confidence;
    private PlaceStatus status;
    private String primarySource;
    private Long legacyLocationserviceId;
    private String createdAt;

    public static PlaceDto from(Place p) {
        return PlaceDto.builder()
            .id(p.getId()).name(p.getName()).address(p.getAddress())
            .category(p.getCategory()).lat(p.getLat()).lng(p.getLng())
            .confidence(p.getConfidence()).status(p.getStatus())
            .primarySource(p.getPrimarySource())
            .legacyLocationserviceId(p.getLegacyLocationserviceId())
            .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null)
            .build();
    }
}
```

- [ ] **Step 2: PlaceAdminService 작성**

```java
package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.dto.PlaceDto;
import com.linkup.Petory.domain.place.entity.Place;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import com.linkup.Petory.domain.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PlaceAdminService {

    private final PlaceRepository placeRepo;

    public Page<PlaceDto> listByStatus(PlaceStatus status, Pageable pageable) {
        return placeRepo.findByStatus(status, pageable).map(PlaceDto::from);
    }

    @Transactional
    public PlaceDto activate(Long id, String adminUsername) {
        Place place = placeRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (place.getStatus() != PlaceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "activate 허용 상태: PENDING. 현재: " + place.getStatus());
        }

        place.setStatus(PlaceStatus.ACTIVE);
        place.setActivatedBy(adminUsername);
        place.setActivatedAt(LocalDateTime.now());
        placeRepo.save(place);
        return PlaceDto.from(place);
    }
}
```

- [ ] **Step 3: PlaceAdminController 작성**

```java
package com.linkup.Petory.domain.place.controller;

import com.linkup.Petory.domain.place.dto.PlaceDto;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import com.linkup.Petory.domain.place.service.PlaceAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/places")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class PlaceAdminController {

    private final PlaceAdminService service;

    @GetMapping
    public ResponseEntity<Page<PlaceDto>> list(
        @RequestParam(defaultValue = "PENDING") PlaceStatus status,
        @PageableDefault(size = 20, sort = "confidence", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listByStatus(status, pageable));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<PlaceDto> activate(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.activate(id, auth.getName()));
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/dto/PlaceDto.java \
        backend/main/java/com/linkup/Petory/domain/place/service/PlaceAdminService.java \
        backend/main/java/com/linkup/Petory/domain/place/controller/PlaceAdminController.java
git commit -m "feat(place): Admin API — PENDING places 조회 및 ACTIVE 전환"
```

---

## Task 11: 배치 적재 엔드포인트 + locationservice write guard

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/place/dto/BatchIngestRequest.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/service/PlaceCandidateIngestService.java`
- Create: `backend/main/java/com/linkup/Petory/domain/place/controller/PlaceCandidateIngestController.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/service/LocationImportService.java`

- [ ] **Step 1: BatchIngestRequest + IngestService 작성**

`BatchIngestRequest.java`:
```java
package com.linkup.Petory.domain.place.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter @NoArgsConstructor
public class BatchIngestRequest {

    private List<CandidateItem> candidates;

    @Getter @NoArgsConstructor
    public static class CandidateItem {
        private String name;
        private String address;
        private Double lat;
        private Double lng;
        private String category;
        private String phone;
        private String collectedFrom;
        private String evidenceText;
    }
}
```

`PlaceCandidateIngestService.java`:
```java
package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.dto.BatchIngestRequest;
import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import com.linkup.Petory.domain.place.repository.PlaceCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceCandidateIngestService {

    private final PlaceCandidateRepository candidateRepo;

    @Transactional
    public int ingest(BatchIngestRequest request) {
        List<PlaceCandidate> toSave = new ArrayList<>();
        for (BatchIngestRequest.CandidateItem item : request.getCandidates()) {
            if (!StringUtils.hasText(item.getName())) continue;
            // 같은 name+address 조합이 이미 PENDING/NEEDS_REVIEW면 중복 적재 방지
            if (StringUtils.hasText(item.getAddress())
                && candidateRepo.countByRawNameAndRawAddress(item.getName(), item.getAddress()) > 0) {
                continue;
            }
            toSave.add(PlaceCandidate.builder()
                .rawName(item.getName())
                .rawAddress(item.getAddress())
                .lat(item.getLat())
                .lng(item.getLng())
                .collectedFrom(item.getCollectedFrom() != null ? item.getCollectedFrom() : "PET_DATA_API")
                .evidenceText(item.getEvidenceText())
                .decisionStatus(CandidateDecisionStatus.PENDING)
                .build());
        }
        candidateRepo.saveAll(toSave);
        log.info("[PlaceCandidateIngest] 적재 완료 requested={} saved={}", request.getCandidates().size(), toSave.size());
        return toSave.size();
    }
}
```

- [ ] **Step 2: IngestController 작성**

```java
package com.linkup.Petory.domain.place.controller;

import com.linkup.Petory.domain.place.dto.BatchIngestRequest;
import com.linkup.Petory.domain.place.service.PlaceCandidateIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/place-candidates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class PlaceCandidateIngestController {

    private final PlaceCandidateIngestService ingestService;

    @PostMapping("/batch-ingest")
    public ResponseEntity<Map<String, Object>> batchIngest(@RequestBody BatchIngestRequest request) {
        int saved = ingestService.ingest(request);
        return ResponseEntity.ok(Map.of("saved", saved, "total", request.getCandidates().size()));
    }
}
```

- [ ] **Step 3: LocationImportService write guard 추가**

`LocationImportService.java`의 `processEntries()` 내 `else` 브랜치(신규 INSERT 경로)에 가드 추가:

기존:
```java
} else {
    batch.add(locationImportConverter.toEntity(dto));
```

변경 후:
```java
} else {
    // [WRITE GUARD] 신규 후보는 place_candidates로만 적재.
    // pet-data-api는 POST /api/admin/place-candidates/batch-ingest를 사용할 것.
    log.warn("[LocationImportService] 신규 장소 INSERT 차단 name={} address={} — place_candidates 사용",
        dto.getName(), dto.getAddress());
    skipped++;
    continue;
    // 아래 batch.add() 라인은 이 continue로 도달하지 않음
    // batch.add(locationImportConverter.toEntity(dto));
```

> ⚠️ 기존 공공데이터 CSV import(`PublicDataLocationService`)는 별도 서비스이므로 영향 없음.

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/place/dto/BatchIngestRequest.java \
        backend/main/java/com/linkup/Petory/domain/place/service/PlaceCandidateIngestService.java \
        backend/main/java/com/linkup/Petory/domain/place/controller/PlaceCandidateIngestController.java \
        backend/main/java/com/linkup/Petory/domain/location/service/LocationImportService.java
git commit -m "feat(place): batch-ingest 엔드포인트 추가 + locationservice 신규 INSERT 차단"
```

---

## Task 12: pet-data-api Python 수정

**Files:**
- Create: `app/ingestion/petory_client.py` (in pet-data-api repo)
- Modify: `app/platform/core/config.py`
- Modify: `app/ingestion/exporter.py`

- [ ] **Step 1: config.py에 설정 추가**

`/Users/maknkkong/project/pet-data-api/app/platform/core/config.py` 의 `Settings` 클래스에 추가:

```python
# Petory backend — place_candidates 적재 엔드포인트
PETORY_INGEST_URL: str = "http://localhost:8080/api/admin/place-candidates/batch-ingest"
PETORY_INGEST_TOKEN: str = ""  # Basic Auth 또는 Bearer token; 환경변수 PETORY_INGEST_TOKEN으로 주입
```

- [ ] **Step 2: petory_client.py 작성**

```python
# /Users/maknkkong/project/pet-data-api/app/ingestion/petory_client.py
import logging
import httpx
from app.platform.core.config import settings

_log = logging.getLogger(__name__)


async def ingest_candidates(dtos: list[dict]) -> int:
    """place_candidates 배치 적재. 성공 시 저장된 수 반환, 실패 시 0."""
    if not dtos:
        return 0

    payload = {"candidates": dtos}
    headers = {}
    if settings.PETORY_INGEST_TOKEN:
        headers["Authorization"] = f"Bearer {settings.PETORY_INGEST_TOKEN}"

    try:
        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.post(settings.PETORY_INGEST_URL, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            saved = data.get("saved", 0)
            _log.info("petory_ingest ok sent=%d saved=%d", len(dtos), saved)
            return saved
    except Exception as exc:
        _log.error("petory_ingest failed sent=%d err=%s", len(dtos), exc)
        return 0
```

- [ ] **Step 3: exporter.py 수정 — 신규 엔드포인트 호출**

`collect_popular_for_cli` 함수 반환 전에 Petory 적재 호출 추가:

기존 exporter.py의 `collect_popular_for_cli` 함수 마지막 `return result` 위에:

```python
    # Petory place_candidates 적재
    if result:
        from app.ingestion.petory_client import ingest_candidates
        import asyncio
        # dto → CandidateItem 형식 변환 (이미 popular_dict_to_dto 결과)
        candidate_items = [
            {
                "name": d.get("name"),
                "address": d.get("address"),
                "lat": d.get("lat"),
                "lng": d.get("lng"),
                "category": d.get("category"),
                "phone": d.get("phone"),
                "collectedFrom": "PET_DATA_API",
            }
            for d in result
        ]
        asyncio.run(ingest_candidates(candidate_items))

    return result
```

- [ ] **Step 4: pet-data-api 동작 확인 (Petory 서버 기동 후)**

```bash
cd /Users/maknkkong/project/pet-data-api
python -c "
import asyncio
from app.ingestion.petory_client import ingest_candidates
result = asyncio.run(ingest_candidates([{
  'name': '테스트카페',
  'address': '서울특별시 마포구 테스트로 1',
  'lat': 37.55, 'lng': 126.92,
  'category': 'cafe', 'phone': None, 'collectedFrom': 'PET_DATA_API'
}]))
print('saved:', result)
"
```

Expected: `saved: 1` (Petory 서버가 떠 있는 경우)

- [ ] **Step 5: Commit (pet-data-api 저장소에서)**

```bash
cd /Users/maknkkong/project/pet-data-api
git add app/ingestion/petory_client.py app/ingestion/exporter.py app/platform/core/config.py
git commit -m "feat: place_candidates 배치 적재 — Petory /batch-ingest 엔드포인트 연동"
```

---

## Task 13: 전체 통합 테스트 + Commit

- [ ] **Step 1: Petory 전체 테스트 실행**

```bash
cd /Users/maknkkong/project/Petory
./gradlew test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL (기존 테스트 깨지지 않음)

- [ ] **Step 2: 서버 기동 후 Admin API 수동 확인**

```bash
./gradlew bootRun &
sleep 10

# NEEDS_REVIEW 큐 조회 (JWT 토큰 필요)
curl -s -H "Authorization: Bearer <admin-token>" \
  "http://localhost:8080/api/admin/place-candidates?status=NEEDS_REVIEW" | jq .

# PENDING places 조회
curl -s -H "Authorization: Bearer <admin-token>" \
  "http://localhost:8080/api/admin/places?status=PENDING" | jq .
```

Expected: 200 OK, paginated empty list (데이터 없는 경우)

- [ ] **Step 3: 최종 Commit**

```bash
git add .
git commit -m "feat(place): Place Candidate Promotion System MVP 완료

- place_candidates/places/place_facts 테이블 및 엔티티
- 4-gate 자동 판정 엔진 (NameQualityChecker, PublicDataMatcher, JudgmentService)
- PlaceJudgmentScheduler (5분 주기 PENDING 처리)
- Admin API 5개 (후보 list/approve/reject, places list/activate)
- BatchIngest 엔드포인트 (pet-data-api 연동)
- LocationImportService 신규 INSERT 차단"
```
