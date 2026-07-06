# Petory 도메인 의존 구조 개선 설계서

> 작성일: 2026-07-05
> 대상: `backend/main/java/com/linkup/Petory/domain` 15개 도메인
> 전제: 다른 개선 항목(수평 확장·CI·거대 클래스)은 제외하고 **도메인 간 의존 구조**만 다룬다.
> 방법: import 의존성 그래프 + 실제 호출부 코드 확인. "이벤트로 끊어라" 같은 일반론이 아니라, **역방향 의존을 성격별로 분류**하고 각각에 맞는 처방을 제시한다.

---

## 1. 핵심 통찰: 순환은 "종류가 다른 4개 문제"의 뭉치다

`user`가 허브가 되어 있다는 1차 진단은 맞다. 그러나 실제 호출부를 열어보면, 역방향 의존(`user → 타 도메인`)은 **성격이 전혀 다른 4가지**가 섞여 있고, 각각 해법이 다르다. 하나의 처방("전부 이벤트")으로는 안 되고, 오히려 과잉 설계가 된다.

또한 **이 프로젝트에는 이미 올바른 패턴의 선례가 존재한다.** 새 아키텍처를 발명할 필요가 없다. 기존 선례를 확장하면 된다:

- `activity` 도메인 → board·care·user에 의존하지만, **아무도 activity에 의존하지 않음** = 올바른 "집계 leaf"
- `meetup/event/UserSanctionMeetupEventListener` → user의 제재 이벤트를 meetup이 수신 = 올바른 "이벤트 역전"
- `admin` Facade → 여러 도메인을 감싸되 도메인 로직을 침범하지 않음 = 올바른 "오케스트레이션 층"

---

## 2. 먼저 할 일: 도메인을 3계층으로 분류

순환을 없애는 규칙은 하나다 — **의존은 항상 "더 안정적이고 일반적인" 방향으로만 흐른다.** 이를 위해 15개 도메인을 3계층으로 나눈다.

### 계층 A — 집계/오케스트레이션 (많이 의존해도 됨, 아무도 의존 안 함)
`admin`, `activity`, 그리고 **앞으로 만들 `profile`**
→ 이 계층의 존재 이유가 "여러 도메인을 조합"하는 것이므로, 여러 도메인을 import하는 게 정상이다. 단 **아무도 이 계층을 import하면 안 된다.**

### 계층 B — 핵심 도메인 (서로 순환 금지)
`user`, `care`, `board`, `meetup`, `chat`, `payment`, `location`, `report`, `petRecommendation`, `statistics`
→ 이들 사이의 순환이 진짜 문제다. 서로는 **이벤트 또는 ID 참조로만** 연결한다.

### 계층 C — 공용 서브도메인 (모두가 의존해도 됨, 아무에게도 의존 안 함)
`file`, `notification`, `common`
→ 순수 leaf. **검증 결과 `file`은 실제로 어떤 도메인도 import하지 않는 완전한 leaf다.** 따라서 `user → file`, `care → file` 같은 의존은 **순환이 아니며 고칠 필요가 없다.** (여기에 리팩터링 노력을 쓰지 말 것.)

> **목표 그래프**: C ← B ← A. 화살표는 "의존 방향". A는 B·C를 알고, B는 C를 알고, C는 아무도 모른다. 같은 계층(특히 B) 내부에서는 이벤트/ID로만 소통.

---

## 3. 역방향 의존 4종 분류와 처방

`user`가 타 도메인을 import하는 실제 위치는 다음 4곳뿐이다:

| # | 위치 | import 대상 | 성격 | 처방 |
|---|------|-------------|------|------|
| 1 | `UserProfileController`, `UserProfileWithReviewsDTO` | care·location·meetup의 **Service/DTO** | **읽기 집계** | 계층 A로 이동 (`profile`) |
| 2 | `UserSanctionService` | `report.entity.ReportActionType` | **공유 타입(enum)** | 공용 위치로 추출 |
| 3 | `PetConverter`, `PetService` | `file.*` | **공용 서브도메인** | 그대로 둠 (문제 아님) |
| 4 | (해당 없음 — 아래 §4 별도 순환) | — | — | — |

### 처방 1 — 읽기 집계: `profile` 집계 계층 신설 (가장 임팩트 큼)

**문제의 본질**: `UserProfileController.getMyProfile()`은 프로필 페이지를 그리려고 care 리뷰 + location 리뷰 + meetup 이력을 **동기적으로 읽어 조합**한다(코드 75~80행). 이건 쓰기 흐름이 아니라 **읽기 조합**이므로 이벤트로 풀 수 없다 — 이벤트는 쓰기/알림용이다.

**왜 이벤트가 오답인가**: "프로필 조회 시 리뷰를 이벤트로 받는다"는 건 동기 읽기를 비동기로 바꾸는 것이라 의미가 없다. 조회는 즉시 데이터가 필요하다.

**올바른 해법**: 이 컨트롤러는 애초에 `user` 도메인의 소속이 아니다. 여러 도메인을 조합하는 **집계 계층(계층 A)** 소속이다. `admin`·`activity`가 이미 하는 일과 정확히 같다.

```
Before:  user/controller/UserProfileController  →  care, location, meetup   (B가 B를 sideways 의존 → 순환)

After:   profile/controller/UserProfileController →  user, care, location, meetup   (A가 B를 정상 의존)
         user 도메인은 이제 care/location/meetup을 전혀 모름
```

- 신규 패키지 `domain/profile/` 생성 (계층 A)
- `UserProfileController`, `UserProfileWithReviewsDTO`를 그대로 이동
- `usersService`는 계속 주입받되, care/location/meetup Service도 여기서 주입 → **정상 방향 의존**
- 결과: `user → care`, `user → location`, `user → meetup` 3개 역방향 엣지가 한 번에 사라짐

> 실질적으로 코드 이동에 가깝고 로직 변경이 거의 없어 **위험 대비 효과가 가장 크다.** 여기부터 시작.

### 처방 2 — 공유 타입: `ReportActionType` enum 추출

**문제**: `UserSanctionService`가 제재 사유를 표현하려고 `report.entity.ReportActionType` enum 하나 때문에 report 도메인 전체에 컴파일 의존한다.

**해법**: 이 enum은 report와 user가 **공유하는 어휘**다. 소유권을 재배치한다. 두 선택지:

- (a) `common`(계층 C)으로 enum 이동 → 양쪽이 공용 타입으로 참조. 가장 단순.
- (b) 제재가 "신고 처리 결과로 발생"하는 쓰기 흐름이라면, report가 `ReportActionDecidedEvent`를 발행하고 user의 제재 서비스가 수신 (처방 3과 동일 기법).

> 지금은 enum 참조뿐이므로 **(a)가 최소 변경.** 다만 "신고 → 제재" 흐름을 나중에 이벤트로 정리한다면 (b)가 더 깔끔해진다. **선례 있음**: 이미 `user 제재 → meetup 탈퇴`는 `UserSanctionMeetupEventListener`로 이벤트 역전되어 있다. 같은 결로 "report → user 제재"도 이벤트화 가능.

### 처방 3 — `file`은 건드리지 않는다

`PetConverter`/`PetService`가 `file.AttachmentFileService`, `file.FileTargetType`을 쓰는 건 정상이다. `file`은 검증된 순수 leaf(계층 C)이고, 첨부파일은 모든 도메인이 쓰는 공용 기능이다. **여기에 이벤트나 추상화를 넣는 건 과잉 설계다.** 명시적으로 "고치지 않는다"고 기록한다.

---

## 4. `user` 외의 순환 — 별도 처방 (코드 본문 검증 완료)

> 이 절은 초안 작성 후 **각 순환의 호출부·엔티티 매핑·트랜잭션 범위를 실제 코드로 재검증**하여 정정한 버전이다. 초안에서 §4.2·4.3 처방이 틀렸고, `statistics` 3노드 순환(§4.4)을 누락했다.

### 4.1 `care ↔ payment` — 방향마다 성격이 다름 → 수용

```
payment.PetCoinEscrow (엔티티) → care.CareRequest, care.CareApplication   [@OneToOne, FK]
care.CareRequestService        → payment.PetCoinEscrowService              [거래 완료/취소 오케스트레이션]
```

두 방향을 코드로 확인한 결과 **성격이 완전히 다르다**:

- **`payment → care` (데이터 모델 결합):** `PetCoinEscrow`가 `@OneToOne(fetch=LAZY) @JoinColumn(name="care_request_idx", unique=true)`로 `CareRequest`를 물고 있다(엔티티 파일 46~52행). 에스크로는 존재 자체가 특정 케어 거래에 1:1 종속된다. **이벤트로 풀 수 없다** — 데이터 모델이기 때문. 끊으려면 `@OneToOne` → `Long careRequestIdx`로 바꿔야 하고 JPA 탐색 편의를 잃는다.
- **`care → payment` (money-critical 오케스트레이션):** `CareRequestService`가 거래 완료 시 `releaseToProvider()`, 취소 시 `refundToRequester()`를 호출(348~365행). 이는 **거래 완료 표시와 트랜잭션 정합성이 반드시 함께 보장돼야 하는 돈 흐름**이다. `AFTER_COMMIT` 비동기 이벤트로 바꾸면 "거래는 완료됐는데 코인은 안 나감" 정합성 사고가 난다.

**결론: 수용(의도된 애그리거트).** 한 방향은 데이터모델 FK라 이벤트 불가, 다른 방향은 money-critical이라 이벤트 위험이다. care와 payment를 "함께 배포·함께 변경되는 결제-거래 애그리거트"로 문서에 명시하고 순환을 의도된 결합으로 수용한다. ID 참조 전환은 **실제 서비스 분리를 할 때만** 가치 있다. 여기에 비용을 쓰는 건 CLAUDE.md의 "단순함 우선"에 어긋난다.

### 4.2 `chat ↔ meetup` — DIP 포트 인터페이스 (※초안 처방 정정)

```
meetup → chat   (모임 생성 시 그룹 채팅방 생성 — 오케스트레이션, 정상)
chat.ConversationService.joinMeetupChat() → meetup.MeetupParticipantsRepository   ← 순환의 원인
```

**초안의 "참여자 데이터를 chat 테이블에 복사" 처방은 틀렸다.** 문제의 호출(`ConversationService` 322~326행)은 `joinMeetupChat` — 유저가 채팅방에 **들어오기 전에** "이 사람이 모임 멤버인가"를 검증한다. 들어오기 전이니 chat의 참여자 테이블(`ConversationParticipant`)엔 당연히 없다. 즉 "모임 멤버 여부"는 **본질적으로 meetup이 답할 권한 질문**이라 데이터 복사로 풀리지 않는다.

**올바른 해법 — 의존성 역전(DIP):** chat이 자기 패키지에 좁은 포트 인터페이스(예: `MeetupMembershipPort { boolean isMember(Long meetupIdx, Long userId); }`)를 정의하고, meetup이 이를 구현한다. 컴파일 의존은 `meetup → chat`(구현이 인터페이스를 참조) 한 방향만 남아 순환이 끊긴다. 대안으로 `joinMeetupChat` 오케스트레이션 자체를 meetup 쪽으로 옮겨도 된다.

### 4.3 `location ↔ petRecommendation` — 이벤트 클래스 소유권 이동 (※초안 처방 정정)

초안엔 "확인 후 한쪽 정리"라고 추정으로 썼으나, 실물은 훨씬 간단하다:

```
location.LocationServiceService → petRecommendation.event.LocationSearchPerformedEvent   [발행]
petRecommendation.PetRecommendationService → location.entity/repository                   [읽기]
```

location은 **이미 이벤트를 발행**하고 있다. 문제는 **이벤트 클래스의 소유권이 거꾸로**라는 것 — 발행자(location)가 수신자(petRecommendation)의 이벤트 클래스에 컴파일 의존한다.

**해법:** `LocationSearchPerformedEvent`를 **발행자(location) 또는 `common`으로 이동**. 그러면 location이 petRecommendation을 아예 import하지 않게 되어, **클래스 하나 이동으로 순환이 끊긴다.** 저비용·저위험.

### 4.4 `statistics → care → payment → statistics` — 3노드 순환 (초안 누락, 잠재 버그 동반)

두 초안 모두 놓친 순환이다. 범인은 이 한 줄:

```java
// payment/service/PetCoinEscrowService.java:105  (releaseToProvider, @Transactional)
statisticsService.recordPayment(BigDecimal.valueOf(escrow.getAmount()));
```

`payment → statistics`(결제액 기록) + `statistics → care`(집계 위해 `CareRequestRepository` 읽기, 전부 `countBy...` 읽기 전용) + `care → payment` = 3노드 순환.

**동반된 잠재 버그:** `recordPayment`(통계 누적)가 `releaseToProvider`의 **코인 지급(`payoutCoins`, 98행)과 같은 트랜잭션 안**에 있다. 즉 통계 기록이 실패하면 **실제 코인 지급까지 롤백**된다. 통계는 non-critical한데 money 흐름을 인질로 잡고 있는 셈.

**해법 — `PaymentRecordedEvent` + `@TransactionalEventListener(AFTER_COMMIT)`:**
1. `payment → statistics` 컴파일 엣지가 사라져 3노드 순환이 끊긴다.
2. non-critical 통계가 money 트랜잭션에서 분리돼 **위 잠재 버그가 사라진다.**
3. `statistics → care`가 읽기 전용임을 확인했으므로, 이 엣지만 빼면 statistics는 **누구에게도 쓰기로 의존받지 않는 순수 집계 도메인(계층 A)** 이 된다.

> **도출 원칙:** 크로스도메인 쓰기는 "트리거 작업에 트랜잭션적으로 필수적이지 않을 때만" 이벤트로 뺀다. `payment→statistics`(recordPayment)는 non-critical이라 이벤트화가 안전하고 이득이지만, `care→payment`(releaseToProvider)는 money-critical이라 동기 결합을 유지한다. "쓰기는 다 이벤트로"가 왜 틀린지를 같은 프로젝트의 두 사례가 직접 보여준다.

---

## 5. 목표 의존 그래프 (After)

```
계층 A (집계)      admin,  activity,  profile,  statistics*
                      │        │         │          (*payment→statistics 이벤트화 후 편입)
                      ▼        ▼         ▼
계층 B (핵심)   user  care  board  meetup  chat  payment  location  report  petRecommendation
                  └──── 서로는 이벤트/ID로만 ────┘   (care↔payment는 의도된 애그리거트로 수용)
                      │
                      ▼
계층 C (공용)         file,  notification,  common
```

규칙 요약:
1. A는 B·C를 자유롭게 의존한다. **누구도 A를 의존하지 않는다.**
2. B는 C를 자유롭게 의존한다. **B끼리는 이벤트/ID 참조로만.**
3. C는 아무도 의존하지 않는다. (순수 leaf)

---

## 6. 실행 순서 (위험 낮은 순 → 효과 큰 순)

| 단계 | 작업 | 없어지는 엣지 | 위험 | 규모 |
|------|------|---------------|------|------|
| 1 | `profile` 계층 신설, `UserProfileController`/`UserProfileWithReviewsDTO` 이동 | `user→care`, `user→location`, `user→meetup` | 낮음(코드 이동) | 소 |
| 2 | `ReportActionType`을 `common`으로 이동 (또는 이벤트화) | `user→report` | 낮음 | 소 |
| 3 | `LocationSearchPerformedEvent`를 location/common으로 이동 | `location→petRecommendation` | 낮음(클래스 1개) | 소 |
| 4 | `recordPayment`를 `PaymentRecordedEvent`로 이벤트화 | `payment→statistics` (+ 잠재 버그 수정) | 낮음 | 소 |
| 5 | `chat`에 `MeetupMembershipPort` 도입, meetup이 구현(DIP) | `chat→meetup` | 중 | 소~중 |
| 6 | `care↔payment`를 "결제-거래 애그리거트"로 문서화(수용) | (수용) | 없음 | 문서만 |

**1·2단계만 끝내도 `user` 도메인은 완전한 leaf가 되어** 순환 그래프의 핵심 매듭이 풀린다(역방향 의존이 profile 이동 3곳 + enum 1곳뿐임을 코드로 확인). 3·4단계는 각각 클래스/이벤트 하나로 값싸게 순환을 끊으며, 4단계는 money 트랜잭션의 잠재 버그까지 함께 없앤다. 여기까지가 비용 대비 효과의 대부분이다.

### 검증 방법
각 단계 후 다음으로 순환이 실제로 줄었는지 확인:

```bash
# 도메인 간 import 의존성 재추출 (분석 시 사용한 스크립트와 동일 원리)
cd backend/main/java/com/linkup/Petory/domain
for src in */; do
  s=${src%/}
  grep -rho "import com.linkup.Petory.domain.[a-zA-Z]*" "$s" \
    | sed 's/.*domain\.//' | sort -u | grep -v "^$s$" \
    | sed "s/^/$s -> /"
done
```

컴파일 검증은 기존 CI(`./gradlew compileJava`)로 충분하다. 도메인 이동은 순수 구조 변경이므로 **기존 테스트가 그대로 통과해야 한다**(회귀 감지 역할).

---

## 7. 면접 서사

- **문제 인식**: "import 의존성 그래프를 뽑아 보니 user가 leaf가 아니라 허브였고, 순환이 성격 다른 5종으로 섞여 있었다. 게다가 그래프만 봐선 안 보이던 `statistics→care→payment→statistics` 3노드 순환이 호출부를 열어보니 드러났다."
- **분류의 힘**: "전부 이벤트로 끊는 대신, 읽기 집계는 집계 계층 분리, 공유 enum은 공용 추출, non-critical 쓰기만 이벤트, 권한 질문은 DIP 포트, 엔티티 FK+money 흐름은 의도된 애그리거트로 수용 — 성격별로 다른 처방을 적용했다."
- **원칙의 발견**: "크로스도메인 쓰기를 이벤트로 뺄지는 '트리거 작업에 트랜잭션적으로 필수적인가'로 갈렸다. `recordPayment`(통계)는 non-critical이라 이벤트화가 순환 제거 + 잠재 버그(통계 실패 시 코인 지급 롤백) 수정까지 됐지만, `releaseToProvider`(에스크로 지급)는 money-critical이라 동기 결합을 유지했다."
- **선례 존중**: "이미 `activity` 집계 도메인과 `UserSanctionMeetupEventListener` 이벤트 역전이 있었기에, 새 패턴을 발명하지 않고 기존 패턴을 확장했다."
- **절제와 정정**: "`file`은 순수 leaf라 손대지 않았고, care↔payment는 억지 분리가 정합성을 해쳐 수용했다. 초안에서 chat↔meetup을 '데이터 복사'로 풀려 했으나, join 시점엔 그 데이터가 없다는 걸 코드로 확인하고 DIP로 정정했다 — 처방을 코드로 재검증하는 과정 자체가 설계다."

---

## 8. 실행 결과 (2026-07-06)

**적용 범위: Step 1~4 + 6** (Step 5 chat↔meetup DIP는 후속 과제로 보류).

| 단계 | 작업 | 결과 |
|------|------|------|
| 1 | `domain/profile/` 계층 신설, `UserProfileController`·`UserProfileWithReviewsDTO` 이동 | `user→care`, `user→location`, `user→meetup` 3엣지 제거 ✅ |
| 2 | `ReportActionType`을 `report.entity` → `common`으로 이동 (참조 8곳 갱신) | `user→report` 제거 ✅ |
| 3 | `LocationSearchPerformedEvent`를 `petRecommendation.event` → `location.event`(발행자 소유)로 이동 | `location→petRecommendation` 제거 ✅ |
| 4 | `recordPayment` 직접 호출 → `PaymentRecordedEvent` + `@TransactionalEventListener(AFTER_COMMIT, REQUIRES_NEW)` | `payment→statistics` 제거 + **통계가 코인 지급 트랜잭션에서 분리(잠재버그 수정)** ✅ |
| 6 | `care↔payment`를 결제-거래 애그리거트로 수용 | `payment/package-info.java`에 in-code 문서화 ✅ |

**검증 (import 그래프 재추출):**
- 스냅샷: `docs/analysis/dependency-graph/before-2026-07-06.txt`, `after-step1-4-2026-07-06.txt`
- 제거된 엣지: `user→care/location/meetup`, `user→report`, `location→petRecommendation`, `payment→statistics` (정확히 6개, 목표와 일치)
- 새 엣지: `profile→care/location/meetup/user`, `statistics→payment` (모두 A→B 정방향)
- **`user` = 완전한 leaf**(common·file만 의존), **`statistics` = 유입 엣지 0의 순수 집계 도메인**, **`statistics→care→payment→statistics` 3노드 순환 해소**
- 남은 2-node 순환: `care↔payment`(의도 수용), `chat↔meetup`(Step 5 보류)
- `./gradlew compileJava` 통과, `./gradlew test` 전체 통과 (구조 이동 회귀 없음)
