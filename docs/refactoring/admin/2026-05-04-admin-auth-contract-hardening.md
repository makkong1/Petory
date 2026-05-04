# Admin 인증/계약 경계 강화 (2026-05-04)

## 문제

1. **삭제 계정 인증 경계 붕괴**: `UsersDetailsServiceImpl`, `AuthenticatedUserIdResolver`가 삭제 사용자(`isDeleted=true`)를 별도로 차단하지 않아, 소프트 삭제된 ADMIN/MASTER도 기존 JWT 또는 재로그인으로 관리자 API 접근이 가능하다.
2. **Admin 문서-코드 계약 불일치**: `docs/domains/admin.md`는 "Controller → Facade → 도메인 서비스"와 "모든 쓰기 행위 감사 로그"를 전제로 쓰여 있지만, 실제 `AdminFileController`, `AdminBoardController`, `AdminReportController` 등은 직접 서비스/레포지토리를 호출하거나 감사 로그 없이 쓰기 작업을 수행한다.
3. **Admin API 동작 불일치**: 문서에는 `GET /api/admin/meetups?status&q...` 필터 계약이 있으나 실제 구현은 `status`, `q`를 무시한다.
4. **복구/상세 조회 흐름 단절**: 관리자 목록에서 삭제된 케어 요청은 조회 가능하지만, 상세 조회는 일반 사용자용 조회 로직을 재사용해 404가 발생한다.
5. **테스트 공백**: admin/report/file/system 경로에는 문서 계약, 권한 경계, 감사 로그, soft delete 동작을 고정하는 회귀 테스트가 부족하다.

## 왜 지금 고치나

- 지금 프로젝트는 기능 breadth보다 **운영 품질 마감**이 더 중요한 단계다.
- 면접에서 설명 가치가 높은 포인트는 "기능이 많다"보다 **권한 경계, soft delete, 감사 로그, 문서 계약을 실제로 정리한 경험**이다.
- 현재 상태는 compile은 되지만, 운영자 도메인에서 **보안 경계와 계약 일관성**이 느슨하다.

## 목표 상태

1. 삭제되었거나 관리자 작업이 불가능해야 하는 계정은 인증/인가 단계에서 일관되게 차단된다.
2. `docs/domains/admin.md`는 현재 코드의 실제 계약만 설명한다.
3. admin 도메인의 핵심 쓰기 경로는 감사 로그 정책과 facade 책임이 명확하다.
4. 관리자 상세 조회/복구 API는 문서대로 동작하거나, 문서를 실제 동작에 맞게 축소한다.
5. admin/report/file/system 쪽 핵심 경계는 테스트로 회귀 방지된다.

## 작업 범위

### 1. 인증/권한/삭제 일관성

- `UsersDetailsServiceImpl`
- `AuthenticatedUserIdResolver`
- 필요 시 로그인/토큰 검증 경로
- soft delete된 사용자/관리자 계정의 인증 가능 여부 정리

### 2. Admin 도메인 계약 정렬

- `docs/domains/admin.md` 현행화
- facade 책임과 실제 controller 의존 구조 비교
- 문서에 적힌 필터/복구/감사 로그 계약과 실제 구현 정합성 수정

### 3. Admin 기능 마감

- meetup 목록 필터 계약 처리 또는 문서 축소
- 삭제된 care request 상세 조회/복구 흐름 정리
- file/report/system 경로의 감사/책임 분리 점검

### 4. 테스트 보강

- admin 권한/삭제 계정 접근 차단
- admin API 계약 테스트
- report/file/system 회귀 테스트

## 비범위

- 새로운 관리자 기능 추가
- 프론트엔드 UI 개편
- 전 도메인 문서 전체 현행화
- recommendation/location 중복 경로 정리 작업 본체

## 우선순위

1. 삭제 계정 인증 차단
2. admin 문서-코드 계약 정렬
3. admin API 동작 보정
4. 회귀 테스트 추가

## 작업 순서

1. 현재 admin/auth 경계와 문서 계약을 다시 점검한다.
2. `docs/domains/admin.md`와 실제 코드의 차이를 목록화한다.
3. 삭제 계정 인증 차단과 admin 경계 관련 코드를 수정한다.
4. admin/report/file/system 테스트를 추가한다.
5. 마지막으로 `docs/domains/admin.md`를 최종 상태에 맞춰 다시 정리한다.

## 검증 기준

- `./gradlew compileJava`
- 관련 단위/통합 테스트 통과
- 삭제된 관리자 계정이 인증 또는 관리자 API 호출에 실패함
- `docs/domains/admin.md`의 엔드포인트/권한/책임 구조가 실제 코드와 일치함

## 기대 효과

- 운영자 도메인의 보안 경계가 명확해진다.
- soft delete와 인증 체계가 충돌하지 않는다.
- admin 문서가 면접 준비용 아키텍처 설명 자료로 바로 사용 가능해진다.
- 이후 recommendation/location 정리 전까지, 운영자 도메인을 안정된 기준점으로 삼을 수 있다.
