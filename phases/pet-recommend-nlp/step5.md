# Step 5: Spring DTO + PetIntentClient + app.pet-intent 설정

## 목표
Spring 서버에서 Python NLP 서버를 호출하기 위한 DTO, HTTP 클라이언트, 프로퍼티 설정을 구현한다.

## 배경
- Spring Boot 3.5.7 (Java 17), 패키지: `com.linkup.Petory`
- Python 서버 주소: `http://localhost:8000` (로컬 개발 환경)
- 설정 키: `app.pet-intent.base-url`, `app.pet-intent.timeout-ms`
- `application.properties`는 gitignore됨 → 직접 추가 필요
- `application-dev.properties`가 있으면 거기에 추가, 없으면 `application.properties`에 추가
- Spring WebClient 또는 RestTemplate 사용 (프로젝트에 이미 WebClient가 있으면 그것 사용)
- 도메인 패키지: `domain/petRecommendation/`

## 프로젝트 구조 확인 포인트
- 기존 `domain/` 패키지들 확인: `board`, `care`, `chat`, `location`, `meetup`, `user` 등
- 기존 외부 HTTP 호출 코드가 있으면 같은 방식으로 구현 (WebClient vs RestTemplate)

## 생성할 파일

### `backend/main/resources/application-dev.properties`에 추가

```properties
# Python NLP Server
app.pet-intent.base-url=http://localhost:8000
app.pet-intent.timeout-ms=3000
```

> `application-dev.properties`가 없으면 `application.properties`에 추가

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/dto/PetIntentAnalyzeRequest.java`

```java
package com.linkup.Petory.domain.petRecommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PetIntentAnalyzeRequest {
    private String text;
    private String petType;
}
```

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/dto/PetIntentAnalyzeResponse.java`

```java
package com.linkup.Petory.domain.petRecommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetIntentAnalyzeResponse {
    private String intentDomain;
    private String intent;
    private List<String> recommendedCategories;
    private double confidence;
    private List<String> keywords;
    private List<String> intentTags;
    private String urgency;
    private String message;
    private List<String> suggestedCategories;
}
```

### `backend/main/java/com/linkup/Petory/domain/petRecommendation/client/PetIntentClient.java`

```java
package com.linkup.Petory.domain.petRecommendation.client;

import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeRequest;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Optional;

@Slf4j
@Component
public class PetIntentClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PetIntentClient(
            @Value("${app.pet-intent.base-url:http://localhost:8000}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    public Optional<PetIntentAnalyzeResponse> analyze(String text, String petType) {
        try {
            PetIntentAnalyzeRequest req = PetIntentAnalyzeRequest.builder()
                    .text(text)
                    .petType(petType)
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PetIntentAnalyzeRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<PetIntentAnalyzeResponse> resp = restTemplate.postForEntity(
                    baseUrl + "/api/pet-intent/analyze",
                    entity,
                    PetIntentAnalyzeResponse.class
            );
            return Optional.ofNullable(resp.getBody());
        } catch (RestClientException e) {
            log.warn("[PetIntentClient] Python 서버 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
```

## Acceptance Criteria

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava
# 기대: BUILD SUCCESSFUL
```

> `RestTemplate`이 이미 Bean으로 등록된 경우 재사용. 아니면 위처럼 직접 생성.
> WebClient가 이미 프로젝트에서 쓰이고 있으면 WebClient로 교체해도 무방.
