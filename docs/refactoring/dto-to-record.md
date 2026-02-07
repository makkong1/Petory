# DTO → record 리팩토링

## 출발점

- **질문**: 요청/응답 DTO에 record 타입이 적합하다는 추천(GPT)을 받음
- **근거**: record는 Java 16+ 불변 데이터 캐리어로, DTO의 역할(데이터 전달)과 잘 맞음
- **적용 사례**: `BoardPopularitySnapshotDTO`를 record로 변경 (2026.01)

## Lombok DTO vs record DTO

| 구분 | Lombok DTO | record DTO |
|------|-----------|------------|
| 불변성 | 가변 (setter 있음) | **불변** (생성 후 변경 불가) |
| 코드량 | `@Data` `@Builder` 등 다수 | 한 번에 정의 |
| 의도 | 데이터 클래스(모호) | **데이터 전달용**(명확) |
| Jackson | getter/setter 기반 | accessor 기반 (동일 동작) |

### 장점 (record)

- **불변성** → 생성 후 수정 불가, 의도치 않은 변경 방지
- **코드 간결** → Lombok 의존 감소, 필드만으로 접근자·equals·hashCode·toString 자동 생성
- **의도 표현** → “읽기 전용 데이터”가 타입으로 드러남
- **스레드 안전** → 불변 객체는 공유 시 상대적으로 안전

### 단점/주의 (record)

- **빌더 없음** → 필드 많으면 생성자 길어짐 (static 팩토리로 보완)
- **상속 불가** → DTO 계층 구조 필요 시 부적합
- **부분 수정 불가** → 값 변경 시 새 인스턴스 생성 필요

## 직렬화 흐름 (참고)

```
서비스 → Controller → ResponseEntity(body)
                         ↓
         HttpMessageConverter (인터페이스)
                         ↓
         MappingJackson2HttpMessageConverter (구현체)
                         ↓
         ObjectMapper.writeValue() → JSON 문자열
                         ↓
         record의 snapshotId(), boardId() 등 accessor 호출
```

## 적용하면 좋은 경우 ✅

- **Response DTO** (조회·응답용) → 읽기 전용, 불변성 필요
- **단순 데이터 캐리어** → 필드만 있고 비즈니스 로직 없음
- **서비스 내부 전달 객체** → 메서드 간 데이터 전달 (예: `PeriodRange`, `BoardScore`)

## 적용하면 안 좋은 경우 ❌

- **Request DTO + Jackson 역직렬화** → JSON 키·필드명 불일치 시 `@JsonProperty` 등 추가 작업 필요
- **빌더 패턴이 필수** → 10개 이상 필드, 선택 필드 많은 경우 Lombok `@Builder`가 유리
- **상속/다형성 필요** → 부모 DTO를 상속하는 응답 구조
- **가변 수정 필요** → 도메인에서 `dto.setXxx()` 등으로 계속 갱신하는 경우
- **JPA 엔티티** → record는 엔티티로 사용 불가 (proxy, lazy loading 등 미지원)

## 적용 방침

1. **Response DTO** → 새로 만드는 DTO는 record 우선
2. **Request DTO** → Jackson 역직렬화(생성자) 확인 후 record 적용
3. **기존 DTO** → 점진적 전환, 새 DTO부터 record 사용

## 적용 예시

- `BoardPopularitySnapshotDTO` (응답 전용) → record로 전환 완료
- Board 도메인 일괄 리팩토링 → `docs/refactoring/board/dto-record-refactoring.md` 참고
- Converter: `.builder()` → `new DTO(...)`, `dto.getXxx()` → `dto.xxx()`
