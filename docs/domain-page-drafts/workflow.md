# 도메인 페이지 작성 워크플로우

> 목적: `*DomainV2.jsx` 페이지를 만들기 전에 원천 문서를 어떤 순서로 정리할지 고정한다.  
> 원칙: 문서는 참고 자료이고, 최종 판단 기준은 현재 코드다.

---

## 작업 순서

### 1. 도메인 문서 정리

먼저 `docs/domains/<domain>.md`를 현재 코드 기준의 단일 진실로 정리한다.

포함할 내용:

- 도메인의 책임과 범위
- 현재 API와 주요 파라미터
- Controller → Service → Repository 흐름
- 핵심 엔티티와 DTO
- 현재 구현된 기능
- 현재 남은 한계

주의할 점:

- 오래된 구현 설명은 현재 명세처럼 남기지 않는다.
- 리팩토링 이전 문제는 도메인 문서가 아니라 troubleshooting/refactoring 문서로 보낸다.
- 코드에 없는 기능은 “구현됨”으로 쓰지 않는다.

### 2. 아키텍처 문서 정리

그다음 `docs/architecture/` 문서를 정리한다.

포함할 내용:

- 프론트엔드 모듈과 백엔드 API 연결
- 백엔드 레이어 구조
- DB, Redis, 외부 API 등 인프라 연동
- 다른 도메인과의 관계
- 주요 데이터 흐름

역할:

- 도메인 문서가 “이 도메인이 무엇을 하는가”라면,
- 아키텍처 문서는 “어떤 모듈들이 어떻게 연결되는가”를 설명한다.

### 3. 리팩토링/트러블슈팅 문서 정리

`docs/refactoring/`, `docs/troubleshooting/` 문서는 히스토리 문서로 유지한다.

포함할 내용:

- 기존 문제
- 선택한 해결책
- 검토했지만 선택하지 않은 대안
- 적용 범위
- 검증 결과
- 남은 리스크

주의할 점:

- 현재 동작 명세를 중복해서 길게 쓰지 않는다.
- 현재 명세가 필요하면 `docs/domains/<domain>.md` 또는 아키텍처 문서를 링크한다.
- 오래된 코드 파일명이나 이전 흐름은 “과거 문제” 맥락에서만 사용한다.

### 4. DomainV2 페이지 작성

마지막으로 `docs/domain-page-template.md` 구조에 맞춰 포트폴리오용 페이지 내용을 만든다.

작성 기준:

- 도메인 문서에서 현재 기능과 API를 가져온다.
- 아키텍처 문서에서 전체 흐름과 모듈 관계를 가져온다.
- 리팩토링/트러블슈팅 문서에서 기술 결정, Before/After, 한계와 개선점을 가져온다.
- 코드로 다시 검증한 내용만 확정 문구로 쓴다.

DomainV2 페이지의 역할:

- 원본 문서가 아니라 요약/전시용 페이지다.
- 구현 상세를 모두 복사하지 않는다.
- “무슨 문제를 어떤 기술 결정으로 해결했는지”가 빠르게 보여야 한다.

---

## 도메인별 반복 체크리스트

1. 관련 코드 위치 확인
2. `docs/domains/<domain>.md` 최신화
3. 관련 `docs/architecture/` 문서 최신화
4. 관련 refactoring/troubleshooting 문서의 현재 명세 중복 제거
5. `docs/domain-page-drafts/<domain>-domain-v2-content.md` 작성
6. 필요하면 실제 `*DomainV2.jsx`로 변환

---

## 현재 진행

| 도메인          | 상태                                                                                                         | 산출물                                                                                                                                         |
| --------------- | ------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Location        | 도메인 문서·아키텍처 문서 재작성, DomainV2 내용안 초안 작성                                                  | `docs/domains/location.md`, `docs/architecture/location/위치 기반 서비스 아키텍처.md`, `docs/domain-page-drafts/location-domain-v2-content.md` |
| User            | 도메인 문서 재작성, 인증·프로필 아키텍처 문서 추가                                                           | `docs/domains/user.md`, `docs/architecture/user/사용자 인증 및 프로필 아키텍처.md`                                                             |
| Board + Comment | 도메인 문서 재작성, 커뮤니티 게시판 아키텍처 문서 추가. MissingPet 계열은 제외                               | `docs/domains/board.md`, `docs/architecture/board/커뮤니티 게시판 아키텍처.md`                                                                 |
| Care            | 도메인 문서 재작성, 펫 케어 & 매칭 아키텍처 문서 재작성. Payment는 연동 지점으로만 기술                      | `docs/domains/care.md`, `docs/architecture/care/펫 케어 & 매칭 아키텍처.md`                                                                    |
| Payment         | 도메인 문서 재작성, 펫코인 결제 아키텍처 문서 추가. Care/Chat은 호출 경계로만 기술                           | `docs/domains/payment.md`, `docs/architecture/payment/펫코인 결제 아키텍처.md`                                                                 |
| MissingPet      | 도메인 문서 재작성, 실종 제보 아키텍처 문서 추가. 일반 Board와 분리해 기술                                   | `docs/domains/missingpet.md`, `docs/architecture/missingpet/실종 제보 아키텍처.md`                                                             |
| Meetup          | 도메인 문서 재작성, 산책 & 오프라인 모임 아키텍처 문서 재작성                                                | `docs/domains/meetup.md`, `docs/architecture/meetup/산책 & 오프라인 모임 아키텍처.md`                                                          |
| Chat            | 도메인 문서 재작성, 채팅 시스템 아키텍처 문서 재작성. Care/Meetup/MissingPet 연동과 현재 거래 확정 한계 반영 | `docs/domains/chat.md`, `docs/architecture/chat/채팅 시스템 설계.md`                                                                           |
| Recommendation  | 도메인 문서 재작성, petory-nlp-server 포함 반려생활 추천 & NLP 아키텍처 문서 추가                            | `docs/domains/recommendation.md`, `docs/architecture/recommendation/반려생활 추천 & NLP 아키텍처.md`                                           |
| File            | 도메인 문서 재작성, 첨부파일 저장 & 연결 아키텍처 문서 추가                                                   | `docs/domains/file.md`, `docs/architecture/file/첨부파일 저장 & 연결 아키텍처.md`                                                              |
| Notification    | 도메인 문서 재작성, SSE/Redis/FCM 알림 시스템 아키텍처 문서 추가                                              | `docs/domains/notification.md`, `docs/architecture/notification/알림 시스템 아키텍처.md`                                                       |
| Report          | 도메인 문서 재작성, 신고 및 제재 아키텍처 문서 추가                                                           | `docs/domains/report.md`, `docs/architecture/report/신고 및 제재 아키텍처.md`                                                                  |
| Admin           | 도메인 문서 재작성, 관리자 운영 아키텍처 문서 추가                                                            | `docs/domains/admin.md`, `docs/architecture/admin/관리자 운영 아키텍처.md`                                                                     |
| Redis           | 공통 Redis 캐시·임시 데이터 아키텍처 문서 재작성. 실제 사용 흐름과 설정만 남은 캐시를 분리해 기술             | `docs/architecture/Redis_캐싱_전략.md`                                                                                                         |
| Statistics      | 도메인 문서와 관리자 대시보드·통계 아키텍처 문서 재작성. 집계 흐름, Redis 캐시, 프론트 연동 갭, 매출 집계 리스크 반영 | `docs/domains/statistics.md`, `docs/architecture/관리자 대시보드 & 통계 시스템 아키텍처.md`                                                    |
| Activity        | 도메인 문서 재작성, 사용자 활동 타임라인 아키텍처 문서 추가. 저장형 로그가 아닌 다중 도메인 read model 구조로 기술 | `docs/domains/activity.md`, `docs/architecture/activity/사용자 활동 타임라인 아키텍처.md`                                                       |
| Home Ranking    | 홈화면 4개 섹션 랭킹 아키텍처 문서 재작성. 위치·실종·모임·커뮤니티별 점수식, 폴백, 프론트 노출 수 반영       | `docs/architecture/홈화면-랭킹-알고리즘.md`                                                                                                    |
