# 어드민 로직 도메인 배치 — 현상 유지 결정 기록

> 작성일: 2026-07-11
> 기준 브랜치: dev
> 상태: **결정 완료 — 현상 유지 (user 도메인 어드민 잔재 이동 안 함)**
> 관련: [`docs/refactoring/querydsl/00-admin-user-search-querydsl-decision.md`](../refactoring/querydsl/00-admin-user-search-querydsl-decision.md)

---

## 배경

"user 도메인에 있는 어드민 관련 로직을 admin 도메인으로 옮기자"는 제안에서 출발해, 실제 이동 가치와 부작용을 점검했다. 결론은 **옮기지 않는다(현상 유지)**. 이 문서는 그 이유를 남겨, 향후 같은 질문("왜 어드민 DTO가 user 도메인에 있나?")이 재점화될 때 재분석 비용을 없애기 위한 것이다.

## 현재 구조 (이미 올바르게 분리된 부분)

- **어드민 컨트롤러 11개** — 전부 `domain/admin/controller`에 위치.
- **어드민 오케스트레이션** — `domain/admin/service/AdminUserFacade` 등 Facade가 감사로그(`AdminAuditService`) + 어드민 전용 검증("MASTER 삭제 불가", "ADMIN 역할만 지정 가능" 등)을 보유하고, 실제 CRUD는 user 도메인 `UsersService`/`UsersRepository`에 위임.
- 의존성 방향: **admin → user** (consumer → core). 올바른 방향.

## user 도메인에 남아있는 어드민 잔재

1. `user/dto/AdminUserListDTO`, `AdminUserPageResponseDTO`
2. `UsersRepository.findAllForAdmin`, `findAdminUserListItems`
3. `UsersService.updateUserStatus`, `getUserWithPets` (주석에 "관리자용" 명시)

## 왜 옮기지 않는가 — 핵심: 순환 의존성 함정

`AdminUserListDTO`는 **user 리포지토리 JPQL의 생성자 projection 대상**이다:

```java
// SpringDataJpaUsersRepository.java
@Query("SELECT new com.linkup.Petory.domain.user.dto.AdminUserListDTO(...) FROM Users u ...")
Page<AdminUserListDTO> findAdminUserListItems(...);
```

이 DTO를 admin 도메인으로 옮기면 → user 리포지토리가 admin 패키지를 import → admin은 이미 user에 의존하므로 **admin ⇄ user 순환 의존성**이 생긴다. 즉 순진한 "Admin* 전부 admin으로 이동"은 구조를 **더 나쁘게** 만든다.

또한 리포지토리 쿼리(`findAllForAdmin` 등)는 `Users` 엔티티를 조회하므로, 애그리거트 루트의 리포지토리 곁에 사는 것이 자연스럽다. projection DTO가 `user.dto`에 있는 것도 흔한 Spring 레이아웃이다. → **현재 배치는 충분히 방어 가능**.

## 검토했으나 채택하지 않은 대안

- **읽기측 통째로 admin 이전 (QueryDSL 커스텀 리포지토리를 admin 도메인에 구현)**: 사이클 없이 단방향을 유지하면서 QueryDSL 도입까지 동시 달성하는 "깔끔한" 안. 다만 작업량이 크고, 리포지토리 쿼리를 엔티티 도메인 밖으로 빼는 것이 이 프로젝트의 다른 도메인 관례와도 어긋난다. → **현 시점 채택 안 함.** QueryDSL은 이전 결정문서대로 user 도메인 안에서 진행.

## 남은 별도 이슈 (이번 결정 범위 밖, 추후 판단)

전체 도메인 스캔 결과 어드민 배치 불일치 항목:

- **`payment/controller/AdminPaymentController`** — 유일하게 admin 도메인 밖에 있는 어드민 컨트롤러. 나머지 11개는 admin 도메인에 있으므로 **명백한 불일치**. 컨트롤러라 projection 사이클 함정이 없어 이동 부작용이 적음. → 별도 작업으로 admin 도메인 이전 검토 가치 있음.
- `report/dto/AdminReportPageResponseDTO`, `location/service/LocationServiceAdminService` — user와 동일한 상황(도메인 곁에 붙은 어드민 DTO/서비스). projection 사이클 함정이 동일하게 적용될 수 있어, 이동 시 개별 의존성 확인 필요. 현 시점 현상 유지.

## 결론

user 도메인 어드민 잔재는 **현상 유지**한다. 근거는 (1) projection DTO 이동 시 순환 의존성, (2) 리포지토리 쿼리는 엔티티 곁이 자연스러움, (3) 현재도 Facade로 오케스트레이션이 이미 admin 도메인에 분리되어 있음. 별도로 `AdminPaymentController`의 위치 불일치만 추후 단독 검토 대상으로 남긴다.
