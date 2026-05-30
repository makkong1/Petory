# Phase 5-6 Pet Intent Signal & Interaction Log 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자 반려생활 의도 signal 수집/조회(Phase 5)와 장소 행동 로그 기반 popularity_score 추천 반영(Phase 6)을 구현한다.

**Architecture:**
Phase 5: 게시글/케어요청/위치검색 생성 시 Spring Event 발행 → `@Async` 리스너가 Python NLP 호출 → confidence ≥ 0.6 이면 `UserPetIntentSignal` 저장 → `/api/pet-recommend/signals` 엔드포인트로 최근 signal 기반 추천 카드 제공.
Phase 6: 장소 상세보기/길찾기/즐겨찾기 이벤트를 `PlaceInteractionLog`에 저장 → 30일 집계로 `popularityScore` 산출 → `PetRecommendScoreCalculator`의 `W_PLACE(0.35)` 가중치에 반영.

**Tech Stack:** Spring Boot 3.5.7 / JPA / `ApplicationEventPublisher` / `@Async` / JSON column / Mockito

---

## 파일 목록

### 신규 생성
| 파일 | 역할 |
|------|------|
| `domain/petRecommendation/entity/UserPetIntentSignal.java` | signal 저장 엔티티 |
| `domain/petRecommendation/repository/UserPetIntentSignalRepository.java` | signal 조회/저장 |
| `domain/petRecommendation/service/UserPetIntentSignalService.java` | signal 저장·조회 로직 |
| `domain/petRecommendation/service/PetIntentSignalEventListener.java` | @Async 이벤트 → Python → 저장 |
| `domain/petRecommendation/dto/UserPetIntentSignalResponse.java` | signal 카드 응답 DTO |
| `domain/petRecommendation/event/CommunityPostCreatedEvent.java` | 게시글 생성 이벤트 |
| `domain/petRecommendation/event/CareRequestCreatedEvent.java` | 케어요청 생성 이벤트 |
| `domain/petRecommendation/event/LocationSearchPerformedEvent.java` | 위치검색 수행 이벤트 |
| `domain/petRecommendation/entity/PlaceInteractionLog.java` | 장소 행동 로그 엔티티 |
| `domain/petRecommendation/repository/PlaceInteractionLogRepository.java` | 장소 행동 집계 |
| `domain/petRecommendation/service/PlaceInteractionService.java` | 행동 저장·popularity 계산 |

### 수정
| 파일 | 변경 내용 |
|------|----------|
| `controller/PetRecommendationController.java` | `/signals` GET + `/interact` POST 추가 |
| `scoring/PetRecommendScoreCalculator.java` | `placeScore` 파라미터 수신·반영 |
| `dto/PetRecommendFacilityDto.java` | `popularityScore` 필드 추가 |
| `service/PetRecommendationService.java` | `PlaceInteractionService` 연동 |
| `domain/board/service/BoardService.java` | `CommunityPostCreatedEvent` 발행 |
| `domain/care/service/CareRequestService.java` | `CareRequestCreatedEvent` 발행 |
| `domain/location/service/LocationServiceService.java` | `LocationSearchPerformedEvent` 발행 |

---

## Task 1: UserPetIntentSignal 엔티티 + 리포지토리

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/entity/UserPetIntentSignal.java`
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/repository/UserPetIntentSignalRepository.java`

- [ ] **Step 1: 엔티티 작성**

```java
// UserPetIntentSignal.java
package com.linkup.Petory.domain.petRecommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_pet_intent_signal", indexes = {
        @Index(name = "idx_user_signal_active", columnList = "user_idx, expires_at, created_at"),
        @Index(name = "idx_signal_source",      columnList = "source_type, source_id")
})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserPetIntentSignal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;   // COMMUNITY | CARE | LOCATION_SEARCH

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "intent_domain", nullable = false, length = 50)
    private String intentDomain;

    @Column(name = "intent", nullable = false, length = 50)
    private String intent;

    @Column(name = "recommended_categories", columnDefinition = "JSON")
    private String recommendedCategories;  // JSON 배열 문자열

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "intent_tags", columnDefinition = "JSON")
    private String intentTags;  // JSON 배열 문자열

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
```

- [ ] **Step 2: 리포지토리 작성**

```java
// UserPetIntentSignalRepository.java
package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.UserPetIntentSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface UserPetIntentSignalRepository extends JpaRepository<UserPetIntentSignal, Long> {

    @Query("""
        SELECT s FROM UserPetIntentSignal s
        WHERE s.userIdx = :userIdx
          AND s.expiresAt > :now
        ORDER BY s.createdAt DESC
        """)
    List<UserPetIntentSignal> findActiveByUser(
            @Param("userIdx") Long userIdx,
            @Param("now") LocalDateTime now);
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 2: 이벤트 클래스 3개

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/event/CommunityPostCreatedEvent.java`
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/event/CareRequestCreatedEvent.java`
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/event/LocationSearchPerformedEvent.java`

- [ ] **Step 1: CommunityPostCreatedEvent**

```java
package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommunityPostCreatedEvent extends ApplicationEvent {
    private final Long userIdx;
    private final Long postId;
    private final String text;   // title + " " + content (원문 저장 X, 분석 후 버림)

    public CommunityPostCreatedEvent(Object source, Long userIdx, Long postId, String text) {
        super(source);
        this.userIdx = userIdx;
        this.postId  = postId;
        this.text    = text;
    }
}
```

- [ ] **Step 2: CareRequestCreatedEvent**

```java
package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CareRequestCreatedEvent extends ApplicationEvent {
    private final Long userIdx;
    private final Long careRequestId;
    private final String text;   // title + " " + description

    public CareRequestCreatedEvent(Object source, Long userIdx, Long careRequestId, String text) {
        super(source);
        this.userIdx        = userIdx;
        this.careRequestId  = careRequestId;
        this.text           = text;
    }
}
```

- [ ] **Step 3: LocationSearchPerformedEvent**

```java
package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class LocationSearchPerformedEvent extends ApplicationEvent {
    private final Long userIdx;
    private final String keyword;

    public LocationSearchPerformedEvent(Object source, Long userIdx, String keyword) {
        super(source);
        this.userIdx  = userIdx;
        this.keyword  = keyword;
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 3: UserPetIntentSignalService

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java`
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/dto/UserPetIntentSignalResponse.java`

- [ ] **Step 1: 응답 DTO**

```java
// UserPetIntentSignalResponse.java
package com.linkup.Petory.domain.petRecommendation.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter @Builder
public class UserPetIntentSignalResponse {
    private String intentDomain;
    private String intent;
    private List<String> recommendedCategories;
    private Double confidence;
    private List<String> intentTags;
    private String cardMessage;     // 노출 문구: "귀/피부 관련 고민이 있어 보여요."
}
```

- [ ] **Step 2: 서비스 작성**

```java
// UserPetIntentSignalService.java
package com.linkup.Petory.domain.petRecommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.entity.UserPetIntentSignal;
import com.linkup.Petory.domain.petRecommendation.repository.UserPetIntentSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPetIntentSignalService {

    private static final double CONFIDENCE_THRESHOLD = 0.6;
    private static final int    SIGNAL_TTL_DAYS      = 7;

    private final UserPetIntentSignalRepository signalRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveIfConfident(Long userIdx, String sourceType, Long sourceId,
                                PetIntentAnalyzeResponse analysis) {
        if (analysis == null || analysis.getConfidence() < CONFIDENCE_THRESHOLD) {
            log.debug("[Signal] confidence 미달 또는 분석 없음 — 저장 안 함. confidence={}",
                    analysis != null ? analysis.getConfidence() : "null");
            return;
        }
        try {
            String categoriesJson = objectMapper.writeValueAsString(analysis.getRecommendedCategories());
            String tagsJson       = objectMapper.writeValueAsString(analysis.getIntentTags());
            UserPetIntentSignal signal = UserPetIntentSignal.builder()
                    .userIdx(userIdx)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .intentDomain(analysis.getIntentDomain())
                    .intent(analysis.getIntent())
                    .recommendedCategories(categoriesJson)
                    .confidence(analysis.getConfidence())
                    .intentTags(tagsJson)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(SIGNAL_TTL_DAYS))
                    .build();
            signalRepository.save(signal);
            log.info("[Signal] 저장 완료 userIdx={} domain={} confidence={}",
                    userIdx, analysis.getIntentDomain(), analysis.getConfidence());
        } catch (Exception e) {
            log.warn("[Signal] JSON 직렬화 실패 — 저장 안 함", e);
        }
    }

    @Transactional(readOnly = true)
    public List<UserPetIntentSignalResponse> getActiveSignals(Long userIdx) {
        List<UserPetIntentSignal> signals =
                signalRepository.findActiveByUser(userIdx, LocalDateTime.now());
        return signals.stream()
                .map(this::toResponse)
                .toList();
    }

    private UserPetIntentSignalResponse toResponse(UserPetIntentSignal s) {
        List<String> categories = parseJson(s.getRecommendedCategories());
        List<String> tags       = parseJson(s.getIntentTags());
        String cardMessage = buildCardMessage(s.getIntentDomain(), categories);
        return UserPetIntentSignalResponse.builder()
                .intentDomain(s.getIntentDomain())
                .intent(s.getIntent())
                .recommendedCategories(categories)
                .confidence(s.getConfidence())
                .intentTags(tags)
                .cardMessage(cardMessage)
                .build();
    }

    private List<String> parseJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildCardMessage(String domain, List<String> categories) {
        String cat = categories.isEmpty() ? "주변 서비스" : categories.get(0);
        return switch (domain != null ? domain : "") {
            case "MEDICAL"          -> "최근 건강 관련 고민이 있어 보여요. [근처 " + cat + " 보기]";
            case "GROOMING"         -> "반려동물 미용이 필요해 보여요. [근처 " + cat + " 보기]";
            case "CAFE_DINING"      -> "반려동물과 나들이 어떠세요? [근처 " + cat + " 보기]";
            case "LODGING_TRAVEL"   -> "여행 계획 중이신가요? [근처 " + cat + " 보기]";
            case "SUPPLIES"         -> "반려동물 용품이 필요해 보여요. [근처 " + cat + " 보기]";
            default                 -> "최근 입력을 바탕으로 추천합니다. [근처 " + cat + " 보기]";
        };
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 4: PetIntentSignalEventListener

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java`

- [ ] **Step 1: 리스너 작성**

```java
package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetIntentSignalEventListener {

    private final PetIntentClient            petIntentClient;
    private final UserPetIntentSignalService signalService;

    @EventListener
    @Async
    public void handle(CommunityPostCreatedEvent event) {
        analyze(event.getUserIdx(), "COMMUNITY", event.getPostId(), event.getText());
    }

    @EventListener
    @Async
    public void handle(CareRequestCreatedEvent event) {
        analyze(event.getUserIdx(), "CARE", event.getCareRequestId(), event.getText());
    }

    @EventListener
    @Async
    public void handle(LocationSearchPerformedEvent event) {
        analyze(event.getUserIdx(), "LOCATION_SEARCH", null, event.getKeyword());
    }

    private void analyze(Long userIdx, String sourceType, Long sourceId, String text) {
        try {
            Optional<PetIntentAnalyzeResponse> result = petIntentClient.analyze(text, null);
            result.ifPresent(analysis ->
                    signalService.saveIfConfident(userIdx, sourceType, sourceId, analysis));
        } catch (Exception e) {
            log.warn("[SignalListener] 분석 실패 — 원 액션에 영향 없음. sourceType={} error={}",
                    sourceType, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 5: 컨트롤러 signal 엔드포인트 추가

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/petRecommendation/controller/PetRecommendationController.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java` (사용자 idx 조회 추가)

- [ ] **Step 1: 컨트롤러에 엔드포인트 추가**

기존 `PetRecommendationController.java` 파일에 다음을 추가한다:

```java
// 기존 import에 추가
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.service.UserPetIntentSignalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import java.util.List;

// 필드 추가 (기존 생성자 주입 유지)
private final UserPetIntentSignalService signalService;
private final UsersRepository usersRepository;

// 엔드포인트 추가
@GetMapping("/signals")
public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals(
        @AuthenticationPrincipal UserDetails userDetails) {
    Long userIdx = usersRepository.findActiveByIdString(userDetails.getUsername())
            .orElseThrow().getIdx();
    return ResponseEntity.ok(signalService.getActiveSignals(userIdx));
}
```

전체 파일을 다음으로 교체한다:

```java
package com.linkup.Petory.domain.petRecommendation.controller;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService;
import com.linkup.Petory.domain.petRecommendation.service.UserPetIntentSignalService;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pet-recommend")
@RequiredArgsConstructor
public class PetRecommendationController {

    private final PetRecommendationService     petRecommendationService;
    private final UserPetIntentSignalService   signalService;
    private final UsersRepository              usersRepository;

    @GetMapping
    public ResponseEntity<PetRecommendResponse> recommend(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam("text") String text,
            @RequestParam(name = "radius", defaultValue = "3000") int radius,
            @RequestParam(name = "petType", required = false) String petType) {

        return ResponseEntity.ok(
                petRecommendationService.recommend(text, lat, lng, radius, petType));
    }

    @GetMapping("/signals")
    public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userIdx = usersRepository.findActiveByIdString(userDetails.getUsername())
                .orElseThrow().getIdx();
        return ResponseEntity.ok(signalService.getActiveSignals(userIdx));
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 6: 기존 서비스에 이벤트 발행 추가

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java`

### 6-A: BoardService

- [ ] **Step 1: `BoardService.createBoard()` 수정**

`BoardService.java`에 필드와 이벤트 발행 추가:

```java
// 기존 필드에 추가
private final ApplicationEventPublisher eventPublisher;

// createBoard() 메서드의 return 직전(Board saved = boardRepository.save(board); 이후)에 추가:
eventPublisher.publishEvent(new CommunityPostCreatedEvent(
        this, user.getIdx(), saved.getIdx(),
        saved.getTitle() + " " + saved.getContent()));
```

`import`에 추가:
```java
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

### 6-B: CareRequestService

- [ ] **Step 2: `CareRequestService.createCareRequest()` 수정**

```java
// 기존 필드에 추가
private final ApplicationEventPublisher eventPublisher;

// CareRequest saved = careRequestRepository.save(builder.build()); 이후 return 직전에 추가:
eventPublisher.publishEvent(new CareRequestCreatedEvent(
        this, user.getIdx(), saved.getIdx(),
        saved.getTitle() + " " + saved.getDescription()));
```

`import`에 추가:
```java
import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

### 6-C: LocationServiceService

- [ ] **Step 3: `LocationServiceService.searchLocationServices()` 수정**

`LocationServiceService.java`에서 사용자 ID를 받을 수 없는 경우 null 처리로 발행한다. 메서드 시그니처에 `Long userIdx` 파라미터를 추가하거나, SecurityContextHolder에서 추출한다.

SecurityContextHolder 방식 (외과적 수정):
```java
// 기존 필드에 추가
private final ApplicationEventPublisher eventPublisher;

// searchLocationServices() 메서드 첫 줄에 추가:
publishSearchEvent(keyword);

// 메서드로 추가:
private void publishSearchEvent(String keyword) {
    if (!org.springframework.util.StringUtils.hasText(keyword)) return;
    try {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return;
        // userId → userIdx 조회는 비용이 있으므로 여기서는 username(loginId)만 전달
        // EventListener에서 조회하도록 이벤트에 loginId를 담는다
        eventPublisher.publishEvent(new LocationSearchPerformedEvent(this, null, keyword));
    } catch (Exception ignored) {}
}
```

> **Note:** `LocationSearchPerformedEvent`의 `userIdx`가 null인 경우 `PetIntentSignalEventListener`는 저장을 건너뛴다. 완전한 구현은 SignalEventListener에서 null 체크로 처리된다.

`PetIntentSignalEventListener.handle(LocationSearchPerformedEvent)`에 null 가드 추가:
```java
@EventListener
@Async
public void handle(LocationSearchPerformedEvent event) {
    if (event.getUserIdx() == null) return;   // 미인증 검색은 skip
    analyze(event.getUserIdx(), "LOCATION_SEARCH", null, event.getKeyword());
}
```

`import`에 추가:
```java
import com.linkup.Petory.domain.petRecommendation.event.LocationSearchPerformedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/petRecommendation/
git add backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java
git add backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java
git add backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java
git commit -m "feat(petRecommendation): Phase 5 — 사용자 반려생활 의도 signal 수집/조회"
```

---

## Task 7: PlaceInteractionLog 엔티티 + 리포지토리 (Phase 6)

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/entity/PlaceInteractionLog.java`
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/repository/PlaceInteractionLogRepository.java`

- [ ] **Step 1: 엔티티 작성**

```java
package com.linkup.Petory.domain.petRecommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "place_interaction_log", indexes = {
        @Index(name = "idx_place_interaction", columnList = "location_idx, created_at")
})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlaceInteractionLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx")
    private Long userIdx;

    @Column(name = "location_idx", nullable = false)
    private Long locationIdx;

    @Column(name = "interaction_type", nullable = false, length = 20)
    private String interactionType;   // VIEW | NAVIGATE | FAVORITE

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 리포지토리 작성**

```java
package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface PlaceInteractionLogRepository extends JpaRepository<PlaceInteractionLog, Long> {

    @Query("""
        SELECT p.locationIdx, COUNT(p) AS cnt
        FROM PlaceInteractionLog p
        WHERE p.locationIdx IN :locationIds
          AND p.createdAt >= :since
        GROUP BY p.locationIdx
        """)
    List<Object[]> countByLocationIdsSince(
            @Param("locationIds") List<Long> locationIds,
            @Param("since") LocalDateTime since);
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 8: PlaceInteractionService + 컨트롤러 엔드포인트

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PlaceInteractionService.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/petRecommendation/controller/PetRecommendationController.java`

- [ ] **Step 1: 서비스 작성**

```java
package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.petRecommendation.entity.PlaceInteractionLog;
import com.linkup.Petory.domain.petRecommendation.repository.PlaceInteractionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceInteractionService {

    private static final int POPULARITY_WINDOW_DAYS = 30;

    private final PlaceInteractionLogRepository logRepository;

    @Transactional
    public void record(Long userIdx, Long locationIdx, String interactionType) {
        logRepository.save(PlaceInteractionLog.builder()
                .userIdx(userIdx)
                .locationIdx(locationIdx)
                .interactionType(interactionType)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * 장소 목록의 popularity_score(0~1)를 계산한다.
     * log10(count + 1) / log10(1001) 로 정규화 (count=1000이면 score≈1.0).
     */
    @Transactional(readOnly = true)
    public Map<Long, Double> getPopularityScores(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) return Map.of();
        LocalDateTime since = LocalDateTime.now().minusDays(POPULARITY_WINDOW_DAYS);
        List<Object[]> rows = logRepository.countByLocationIdsSince(locationIds, since);
        return rows.stream().collect(Collectors.toMap(
                r -> (Long) r[0],
                r -> Math.min(Math.log10(((Number) r[1]).doubleValue() + 1) / Math.log10(1001), 1.0)
        ));
    }
}
```

- [ ] **Step 2: 컨트롤러에 `/interact` POST 추가**

`PetRecommendationController.java` 전체를 다음으로 교체:

```java
package com.linkup.Petory.domain.petRecommendation.controller;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.service.PetRecommendationService;
import com.linkup.Petory.domain.petRecommendation.service.PlaceInteractionService;
import com.linkup.Petory.domain.petRecommendation.service.UserPetIntentSignalService;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pet-recommend")
@RequiredArgsConstructor
public class PetRecommendationController {

    private final PetRecommendationService   petRecommendationService;
    private final UserPetIntentSignalService signalService;
    private final PlaceInteractionService    interactionService;
    private final UsersRepository            usersRepository;

    @GetMapping
    public ResponseEntity<PetRecommendResponse> recommend(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam("text") String text,
            @RequestParam(name = "radius", defaultValue = "3000") int radius,
            @RequestParam(name = "petType", required = false) String petType) {
        return ResponseEntity.ok(
                petRecommendationService.recommend(text, lat, lng, radius, petType));
    }

    @GetMapping("/signals")
    public ResponseEntity<List<UserPetIntentSignalResponse>> getSignals(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userIdx = usersRepository.findActiveByIdString(userDetails.getUsername())
                .orElseThrow().getIdx();
        return ResponseEntity.ok(signalService.getActiveSignals(userIdx));
    }

    @PostMapping("/interact")
    public ResponseEntity<Void> interact(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("locationIdx") Long locationIdx,
            @RequestParam("type") String interactionType) {
        Long userIdx = usersRepository.findActiveByIdString(userDetails.getUsername())
                .orElseThrow().getIdx();
        interactionService.record(userIdx, locationIdx, interactionType);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 9: PetRecommendFacilityDto + PetRecommendScoreCalculator 수정

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/petRecommendation/dto/PetRecommendFacilityDto.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/petRecommendation/scoring/PetRecommendScoreCalculator.java`

- [ ] **Step 1: PetRecommendFacilityDto에 `popularityScore` 추가**

```java
package com.linkup.Petory.domain.petRecommendation.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class PetRecommendFacilityDto {
    private Long id;
    private String name;
    private String address;
    private double distanceM;
    private double rating;
    private int reviewCount;
    private double finalScore;
    private List<String> matchReasons;
    private List<String> locationTags;
    private double popularityScore;   // Phase 6 추가
}
```

- [ ] **Step 2: PetRecommendScoreCalculator에서 `placeScore` 활성화**

`calcScore()` 메서드에서 `placeScore`를 `dto.getPopularityScore()`로 교체:

```java
package com.linkup.Petory.domain.petRecommendation.scoring;

import com.linkup.Petory.domain.petRecommendation.dto.PetRecommendFacilityDto;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class PetRecommendScoreCalculator {

    private static final double W_PLACE    = 0.35;
    private static final double W_TAG      = 0.30;
    private static final double W_DISTANCE = 0.20;
    private static final double W_RATING   = 0.10;
    private static final double W_REVIEW   = 0.05;

    public PetRecommendFacilityDto calcScore(
            PetRecommendFacilityDto dto, int radiusM, List<String> intentTags) {

        double placeScore    = dto.getPopularityScore();   // Phase 6 활성화
        double tagScore      = calcTagMatchScore(dto.getLocationTags(), intentTags);
        double distanceScore = calcDistanceScore(dto.getDistanceM(), radiusM);
        double ratingScore   = calcRatingScore(dto.getRating());
        double reviewScore   = calcReviewScore(dto.getReviewCount());

        double rawScore = placeScore    * W_PLACE
                        + tagScore      * W_TAG
                        + distanceScore * W_DISTANCE
                        + ratingScore   * W_RATING
                        + reviewScore   * W_REVIEW;

        double finalScore = Math.round(rawScore * 1000.0) / 10.0;

        List<String> matchReasons = buildMatchReasons(dto, distanceScore, ratingScore, intentTags);

        return PetRecommendFacilityDto.builder()
                .id(dto.getId())
                .name(dto.getName())
                .address(dto.getAddress())
                .distanceM(dto.getDistanceM())
                .rating(dto.getRating())
                .reviewCount(dto.getReviewCount())
                .popularityScore(placeScore)
                .finalScore(finalScore)
                .matchReasons(matchReasons)
                .build();
    }

    private double calcDistanceScore(double distanceM, int radiusM) {
        if (distanceM >= radiusM) return 0.0;
        return 1.0 - (distanceM / radiusM);
    }

    private double calcRatingScore(double rating) {
        return rating / 5.0;
    }

    private double calcReviewScore(int reviewCount) {
        if (reviewCount <= 0) return 0.0;
        return Math.min(Math.log10(reviewCount + 1) / Math.log10(1001), 1.0);
    }

    private double calcTagMatchScore(List<String> locationTags, List<String> intentTags) {
        if (locationTags == null || locationTags.isEmpty()
                || intentTags == null || intentTags.isEmpty()) return 0.0;
        long matched = intentTags.stream().filter(locationTags::contains).count();
        return (double) matched / intentTags.size();
    }

    private List<String> buildMatchReasons(
            PetRecommendFacilityDto dto, double distanceScore, double ratingScore,
            List<String> intentTags) {
        List<String> reasons = new ArrayList<>();
        if (distanceScore >= 0.7)       reasons.add("nearby");
        if (ratingScore   >= 0.8)       reasons.add("high_rating");
        if (dto.getReviewCount() >= 50) reasons.add("many_reviews");
        if (dto.getPopularityScore() >= 0.5) reasons.add("popular");
        if (intentTags != null && dto.getLocationTags() != null) {
            intentTags.stream()
                    .filter(t -> dto.getLocationTags().contains(t))
                    .map(t -> "tag_match:" + t)
                    .forEach(reasons::add);
        }
        if (reasons.isEmpty()) reasons.add("in_radius");
        return reasons;
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 10: PetRecommendationService에 popularity 연동

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetRecommendationService.java`

- [ ] **Step 1: `PlaceInteractionService` 주입 + `toDto()`에 popularityScore 추가**

```java
package com.linkup.Petory.domain.petRecommendation.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.petRecommendation.client.PetIntentClient;
import com.linkup.Petory.domain.petRecommendation.dto.*;
import com.linkup.Petory.domain.petRecommendation.scoring.PetRecommendScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetRecommendationService {

    private final PetIntentClient            petIntentClient;
    private final LocationServiceRepository  locationServiceRepository;
    private final PetRecommendScoreCalculator scoreCalculator;
    private final PlaceInteractionService    interactionService;   // Phase 6 추가

    public PetRecommendResponse recommend(
            String text, double lat, double lng, int radius, String petType) {

        Optional<PetIntentAnalyzeResponse> analysisOpt = petIntentClient.analyze(text, petType);

        if (analysisOpt.isEmpty()) {
            return fallbackRecommend(text, lat, lng, radius);
        }

        PetIntentAnalyzeResponse analysis = analysisOpt.get();
        List<String> categories = analysis.getRecommendedCategories();

        if (categories == null || categories.isEmpty()) {
            return PetRecommendResponse.builder()
                    .requestText(text)
                    .analysis(analysis)
                    .message(analysis.getMessage())
                    .facilities(List.of())
                    .build();
        }

        String primaryCategory = categories.get(0);
        List<LocationService> nearby = locationServiceRepository
                .findByRadius(lat, lng, (double) radius, null, primaryCategory, "distance", 20);

        // Phase 6: popularity 집계
        List<Long> locationIds = nearby.stream().map(LocationService::getIdx).toList();
        Map<Long, Double> popularityMap = interactionService.getPopularityScores(locationIds);

        List<String> intentTags = analysis.getIntentTags() != null ? analysis.getIntentTags() : List.of();
        List<PetRecommendFacilityDto> facilities = nearby.stream()
                .limit(10)
                .map(loc -> toDto(loc, lat, lng, intentTags, popularityMap))
                .map(dto -> scoreCalculator.calcScore(dto, radius, intentTags))
                .sorted(Comparator.comparingDouble(PetRecommendFacilityDto::getFinalScore).reversed())
                .toList();

        return PetRecommendResponse.builder()
                .requestText(text)
                .analysis(analysis)
                .message(analysis.getMessage())
                .facilities(facilities)
                .build();
    }

    PetRecommendResponse fallbackRecommend(String text, double lat, double lng, int radius) {
        log.warn("[PetRecommendationService] Python 서버 장애 — fallback 실행. text={}", text);
        String fallbackCategory = inferCategoryFromKeyword(text);
        List<LocationService> nearby = locationServiceRepository
                .findByRadius(lat, lng, (double) radius, null, fallbackCategory, "distance", 10);

        List<PetRecommendFacilityDto> facilities = nearby.stream()
                .map(loc -> toDto(loc, lat, lng, List.of(), Map.of()))
                .toList();

        return PetRecommendResponse.builder()
                .requestText(text)
                .analysis(null)
                .message("현재 반려생활 의도 분석 서버가 응답하지 않아 기본 검색 결과를 제공합니다.")
                .facilities(facilities)
                .build();
    }

    PetRecommendFacilityDto toDto(LocationService loc, double userLat, double userLng,
                                  List<String> intentTags, Map<Long, Double> popularityMap) {
        double distM = calcDistanceM(userLat, userLng,
                loc.getLatitude()  != null ? loc.getLatitude()  : 0,
                loc.getLongitude() != null ? loc.getLongitude() : 0);
        double popularity = popularityMap.getOrDefault(loc.getIdx(), 0.0);
        return PetRecommendFacilityDto.builder()
                .id(loc.getIdx())
                .name(loc.getName())
                .address(loc.getAddress())
                .distanceM(Math.round(distM * 10.0) / 10.0)
                .rating(loc.getRating()      != null ? loc.getRating()      : 0.0)
                .reviewCount(loc.getReviewCount() != null ? loc.getReviewCount() : 0)
                .finalScore(0.0)
                .matchReasons(List.of("nearby"))
                .locationTags(loc.getTagList())
                .popularityScore(popularity)
                .build();
    }

    private String inferCategoryFromKeyword(String text) {
        if (text.contains("병원") || text.contains("약국") || text.contains("아파") || text.contains("긁")) return "동물병원";
        if (text.contains("미용") || text.contains("털") || text.contains("목욕")) return "미용";
        if (text.contains("카페")) return "카페";
        if (text.contains("호텔") || text.contains("펜션")) return "호텔";
        return "동물병원";
    }

    private double calcDistanceM(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
```

- [ ] **Step 2: 전체 빌드 + 기존 테스트 확인**

```bash
./gradlew compileJava
./gradlew test --tests "*.PetoryApplicationTests"
```
Expected: BUILD SUCCESSFUL, contextLoads PASSED

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/petRecommendation/
git commit -m "feat(petRecommendation): Phase 6 — 장소 행동 로그 기반 popularity_score 추천 반영"
```

---

## 자기 검토 (Spec Coverage)

| 계획서 항목 | Task |
|-----------|------|
| UserPetIntentSignal 엔티티 + DDL | Task 1 (JPA DDL auto) |
| 3개 이벤트 클래스 | Task 2 |
| UserPetIntentSignalService (저장·조회) | Task 3 |
| PetIntentSignalEventListener @Async | Task 4 |
| /signals 엔드포인트 | Task 5 |
| 기존 서비스 이벤트 발행 | Task 6 |
| PlaceInteractionLog 엔티티 | Task 7 |
| PlaceInteractionService + /interact | Task 8 |
| placeScore 활성화 (W_PLACE=0.35) | Task 9 |
| PetRecommendationService 연동 | Task 10 |
| confidence 미달 → 저장 안 함 | Task 3 (CONFIDENCE_THRESHOLD=0.6) |
| 원문 저장 안 함 | Task 3 (analysisResult만 저장) |
| signal 만료 TTL 7일 | Task 3 (SIGNAL_TTL_DAYS=7) |
| 분석 실패 → 원 액션 영향 없음 | Task 4 (try-catch) |
| popular matchReason | Task 9 |
