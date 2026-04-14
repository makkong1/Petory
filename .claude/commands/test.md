# Test Generation Skill

## 트리거

사용자가 테스트 생성, 테스트 작성, 테스트 실행을 요청할 때 실행한다.
`/review`, `/refactor`, `/fix` 완료 후 자동으로 제안된다.

## 테스트 전략

### 변경 코드 기반 테스트 범위 자동 결정

| 변경된 파일 | 생성할 테스트 |
|-----------|-------------|
| `*Service.java` | 단위 테스트 (Mockito) |
| `*Repository.java` | 통합 테스트 (`@DataJpaTest`) |
| `*Controller.java` | API 테스트 (`@WebMvcTest`) |
| `*Entity.java` | 연관 테스트가 있으면 업데이트, 없으면 생성 안 함 |
| `*.js` / `*.jsx` | Jest 단위 테스트 |

### 필수 테스트 케이스 3종

모든 테스트는 반드시 아래 3가지를 포함한다:

```
1. ✅ 정상 케이스 (Happy Path)
   - 올바른 입력 → 기대 결과

2. ❌ 예외 케이스 (Exception Path)
   - 잘못된 입력, 권한 없음, 존재하지 않는 데이터
   - 기대하는 예외 타입과 메시지 검증

3. 🔲 경계값 (Boundary)
   - null, 빈 문자열, 0, 최대값
   - 페이징: page=0, size=1, 마지막 페이지
```

### 이 프로젝트 특화 테스트

#### 동시성 테스트
```java
@Test
void 동시_요청_시_데이터_정합성_보장() throws Exception {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                // 동시 호출
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();

    // 최종 상태 검증
}
```

적용 대상:
- 펫코인 충전/차감 (`@Lock(PESSIMISTIC_WRITE)`)
- 모임 참가 인원 증가 (원자적 UPDATE)
- 에스크로 상태 전환

#### 트랜잭션 테스트
```java
@Test
void 예외_발생_시_롤백_검증() {
    assertThrows(SomeException.class, () -> service.doSomething());
    // DB 상태가 변경 전으로 복원되었는지 검증
}
```

적용 대상:
- 결제 프로세스 중 실패
- 케어 요청 상태 전환 중 실패

## 동작 절차

### 1단계: 테스트 대상 분석

변경된 코드를 읽고 테스트가 필요한 메서드를 식별한다:

```
## 테스트 대상 분석

### 변경 파일
- `CareRequestService.java` → createCareRequest(), cancelCareRequest()

### 생성할 테스트
| # | 메서드 | 케이스 | 테스트명 |
|---|-------|-------|---------|
| 1 | createCareRequest | ✅ 정상 | 정상_케어요청_생성 |
| 2 | createCareRequest | ❌ 예외 | 존재하지않는_사용자_예외 |
| 3 | createCareRequest | ❌ 예외 | 중복_요청_예외 |
| 4 | createCareRequest | 🔲 경계 | 설명_빈문자열_검증 |
| 5 | cancelCareRequest | ✅ 정상 | 정상_케어요청_취소 |
| 6 | cancelCareRequest | ❌ 예외 | 이미_취소된_요청_예외 |
| 7 | cancelCareRequest | ❌ 예외 | 권한없는_사용자_취소_예외 |

→ 생성할까? (전부 / 번호 선택)
```

### 2단계: 테스트 코드 생성

#### 백엔드 테스트 위치
```
backend/test/java/com/linkup/Petory/domain/<domain>/
├── service/
│   └── <Service>Test.java          # 단위 테스트
├── repository/
│   └── <Repository>Test.java       # 통합 테스트
└── controller/
    └── <Controller>Test.java       # API 테스트
```

#### 테스트 네이밍 컨벤션
```java
@Test
@DisplayName("정상: 케어 요청 생성 시 채팅방 자동 생성")
void 정상_케어요청_생성_시_채팅방_자동생성() { }

@Test
@DisplayName("예외: 존재하지 않는 사용자로 요청 시 NotFoundException")
void 예외_존재하지않는_사용자_요청() { }

@Test
@DisplayName("경계: 제목이 빈 문자열이면 ValidationException")
void 경계_제목_빈문자열_검증() { }
```

### 3단계: 테스트 실행

```bash
# 특정 테스트 클래스 실행
./gradlew test --tests "com.linkup.Petory.domain.care.service.CareRequestServiceTest"

# 전체 테스트
./gradlew test
```

### 4단계: 결과 보고

```
## 테스트 결과

| # | 테스트 | 상태 | 비고 |
|---|-------|------|------|
| 1 | 정상_케어요청_생성 | ✅ PASS | |
| 2 | 존재하지않는_사용자_예외 | ✅ PASS | |
| 3 | 중복_요청_예외 | ❌ FAIL | UniqueConstraint 미적용 |
| 4 | 설명_빈문자열_검증 | ✅ PASS | |

### 실패 분석
- **테스트 3 실패**: `CareRequest` 엔티티에 `@UniqueConstraint` 누락
- **수정 필요**: Entity에 제약조건 추가

→ 실패 항목 수정할까? (/fix로 전환)
```

## 워크플로우 연계

- `/review` 완료 → `/test` 제안 (변경 코드 회귀 방지)
- `/refactor` 완료 → `/test` 제안 (리팩토링 검증)
- `/fix` 완료 → `/test` 제안 (수정 확인)
- 테스트 전부 통과 → `/commit` 제안

## 제약

- 테스트는 독립적이어야 한다 (테스트 간 순서 의존 금지).
- Mock은 필요한 최소한만 사용한다 (과도한 Mock = 의미 없는 테스트).
- DB 테스트는 `@Transactional` + 롤백으로 데이터 격리한다.
- 기존 테스트가 있으면 스타일을 맞춘다.
