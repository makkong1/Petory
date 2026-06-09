# Step 5 — signal_interaction_log 테이블 설계 (Phase 0.5)

## 목표

추천 카드 클릭 로그를 위한 `signal_interaction_log` 테이블과 JPA 엔티티/레포지토리를 생성한다.
이번 단계에서는 데이터 수집 인프라만 준비하며, 서비스/컨트롤러/프론트 연결은 포함하지 않는다.

## 배경

- `PlaceInteractionLog`는 `location_idx`가 NOT NULL 필수 → signal 카드 클릭 저장에 재사용 불가
- 추천 카드는 특정 장소가 아닌 카테고리/액션으로 이동하는 구조
- CLICKED / DISMISSED / CONVERTED 세 타입으로 클릭 행동을 분류
- 이 로그는 추후 threshold 튜닝 및 카드 문구 개선의 데이터 근거가 됨

## 생성 파일

### 1. SQL migration

```
backend/main/resources/sql/migration/signal-interaction-log-table.sql
```

```sql
CREATE TABLE IF NOT EXISTS signal_interaction_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_idx         BIGINT       NOT NULL,
    signal_id        BIGINT       NOT NULL COMMENT 'user_pet_intent_signal.id',
    intent_domain    VARCHAR(50)  NOT NULL,
    target_tab       VARCHAR(30)  NULL     COMMENT 'location | care | meetup | missingPet',
    target_category  VARCHAR(100) NULL,
    interaction_type VARCHAR(20)  NOT NULL COMMENT 'CLICKED | DISMISSED | CONVERTED',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_signal_log_user    (user_idx, created_at),
    INDEX idx_signal_log_signal  (signal_id),
    INDEX idx_signal_log_domain  (intent_domain, interaction_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='추천 카드 상호작용 로그 — threshold 튜닝 및 카드 문구 개선 근거';
```

---

### 2. `SignalInteractionLog.java` 엔티티

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/entity/SignalInteractionLog.java
```

```java
package com.linkup.Petory.domain.petRecommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signal_interaction_log", indexes = {
    @Index(name = "idx_signal_log_user",   columnList = "user_idx, created_at"),
    @Index(name = "idx_signal_log_signal", columnList = "signal_id"),
    @Index(name = "idx_signal_log_domain", columnList = "intent_domain, interaction_type")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalInteractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Column(name = "intent_domain", nullable = false, length = 50)
    private String intentDomain;

    @Column(name = "target_tab", length = 30)
    private String targetTab;

    @Column(name = "target_category", length = 100)
    private String targetCategory;

    @Column(name = "interaction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InteractionType interactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum InteractionType {
        CLICKED,    // 추천 카드 클릭 (카테고리 검색/액션 진입)
        DISMISSED,  // 추천 카드 닫기/숨기기
        CONVERTED   // 작성/검색 완료까지 추적 (추후 추가)
    }
}
```

---

### 3. `SignalInteractionLogRepository.java`

```
backend/main/java/com/linkup/Petory/domain/petRecommendation/repository/SignalInteractionLogRepository.java
```

```java
package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.SignalInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalInteractionLogRepository extends JpaRepository<SignalInteractionLog, Long> {
}
```

## 검증

```bash
./gradlew compileJava
```

SQL은 로컬 MySQL에 직접 실행:
```sql
CREATE TABLE IF NOT EXISTS signal_interaction_log ( ... );
SHOW TABLES LIKE 'signal_interaction_log';
```

서비스/컨트롤러 연결은 Phase 2(targetTab 라우팅) 구현 시 함께 추가한다.
