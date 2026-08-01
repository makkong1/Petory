# docs/ 전체 카탈로그 (347개 파일)

> 이 문서는 `docs/` 아래 **모든 파일**(347개, `.DS_Store` 포함)을 10개 서브에이전트가 나눠 병렬로 **전부 실제로 읽고** 한 줄씩 요약한 결과다. 추측·목차만 보고 쓴 요약이 아니라 파일 본문을 근거로 작성했다.
>
> **이 문서 vs 기존 두 문서**
> - `README.md` — 프로젝트 개요(기술스택·기능·아키텍처 요약). 이 카탈로그는 그 아래 실제 문서 347개 각각이 무슨 내용인지 훑는 용도.
> - `INDEX.md` — **자동 생성 파일**(`scripts/docs_index.py` 재생성, 직접 수정 금지). frontmatter 붙은 증거문서 33건만 날짜순·도메인별로 기계적으로 나열하는 별개 용도. 이 카탈로그와 성격이 달라 건드리지 않았다.
> - `핵심성과_분석.md` — 이력서/포트폴리오 수치의 **정본**. 카탈로그 안 여러 문서에 흩어진 수치가 서로 다르면 이 파일이 기준.
>
> **다음 단계(계획만, 아직 미착수)**: 이 카탈로그를 근거로 도메인 / 아키텍처 / 성능개선(+ 필요시 세분화) 축으로 문서를 다시 통합 정리하고, 그 결과로 포트폴리오 레포(`makkong1-github.io`)도 재정리할 예정.

---

## 0. 먼저 훑을 것 — 중복/계열 관계로 발견된 것들

카탈로그를 만들며 서브에이전트들이 공통으로 짚은 패턴. 다음 단계(도메인/아키텍처/성능 통합)에서 우선 참고할 것.

- **같은 파일명이 도메인별로 반복되는 시리즈** (내용은 다름, 도메인명으로만 구분됨):
  - `refactoring/fetch-optimization/{board,care,location,meetup,payment,user}/Fetch 전략 개선 (Fetch Join vs Batch Size).md`
  - `refactoring/recordType/{board,chat,meetup,payment,report,user}/dto-record-refactoring.md`
  - `refactoring/exception/{admin,board,care,chat,file,location,meetup,notification,payment,report,statistics,user}/*예외처리.md` (00/01 개요 + 도메인별 12개)
- **같은 주제를 다른 관점/깊이로 다루는 쌍** (중복이 아니라 상호보완):
  - `architecture/report/신고 및 제재 아키텍처.md`(구조 요약) ↔ `architecture/user/신고 및 제재 시스템 아키텍처.md`(코드 레벨 상세)
  - `architecture/notification/알림 시스템 아키텍처.md`(구조 요약) ↔ `architecture/user/알림 시스템 아키텍처.md`(코드 레벨 상세)
  - `domains/*.md`(정본) ↔ `domain-page-drafts/*-domain-v2-content.md`(포트폴리오 페이지용 파생 점검 초안, workflow.md가 제작 절차)
  - `superpowers/plans/*`(실행계획) ↔ `superpowers/specs/*`(설계 스펙) — 대부분 날짜+제목 1:1 짝
- **파이프라인(순서가 있는 문서 묶음)**:
  - `analysis/query-audit/`: `00-plan` → 도메인별 실측(`board`/`care`/`meetup`/`etc-domains`/`admin`) → `fixes-2026-07-14` → `99-summary`
  - `refactoring/meetup/`: `nearby-meetups/`, `participants-query/`, `subquery-optimization/` 각각 before→after→comparison 3부작
  - `refactoring/petRecommendation/`: nlp-traffic-policy(설계→구현) → pet-recommendation-refactoring(2차 리뷰) → ai-intent-analysis-transition → intent-action-routing-proposal → signal-to-user-gap 순 시간순 스토리
  - `concurrency/evidence/`, `refactoring/{board,care,chat,location,missing-pet}/evidence/n-plus-one-reverify-2026-07-12.md`: 2026-07-12에 git worktree로 과거 커밋을 실제 재현·재검증한 시리즈
- **보관용(archive), 대체된 것으로 보이는 문서**: `refactoring/location/archive/` 5건 — 이후 `주변서비스-알고리즘-설계안.md`/`주변서비스-현행vs설계안-비교.md`/`지도-결과-안정성-리팩토링.md`로 대체된 과거 리팩토링 기록.
- **메타 선별 문서** (이미 "무엇이 중요한지" 골라놓은 문서, 통합 작업 시 그대로 활용 가능): `refactoring/portfolio-refactoring-troubleshooting-selection.md`, `refactoring/Petory-Backend-Refactoring-vs-Troubleshooting.md`, `analysis/리팩토링-순서-2026-07.md`(세대 지도), `interview/concepts/17_도메인별_공부법.md`.
- **오래되어 최신 상태와 어긋난 것으로 보이는 문서**: `md/md파일들 목록.md`(존재하지 않는 파일 링크 다수), `interview/면접_기술질문_리스트.md`(concepts/ 시리즈로 대체됨), `md/# 통계 집계 로직 구현 명세.md`(이후 `domains/statistics.md`의 login_events 전환 이전 버전).
- **macOS 메타파일(`.DS_Store`, 총 11개)** — 내용 없음, 카탈로그 완결성을 위해 존재만 기록.

---

## 1. 최상위 파일

- `README.md` — Petory 프로젝트 개요 문서. 기술스택(Spring Boot 3.5.7/React 19), 주요기능, 아키텍처, 14개 도메인 목록, 동시성·성능 최적화 포인트와 상세문서 링크 인덱스.
- `INDEX.md` — 자동생성 파일(`scripts/docs_index.py`로 재생성, 직접수정 금지). frontmatter 붙은 문서 33건을 날짜순·도메인별로 집계한 증거 인덱스이며, 수치의 정본은 `핵심성과_분석.md`라고 명시.
- `domain-page-template.md` — 포트폴리오 프론트(`*DomainV2.jsx`) 페이지 공통 구조 레퍼런스 문서. 섹션 구성(pillars/intro/design/limits/docs), 라우팅 규칙, 신규 도메인 페이지 추가 체크리스트 정리.
- `핵심성과_분석.md` — 이력서·포트폴리오 수치의 정본(single source of truth) 문서. N+1 해결·동시성 제어·인덱스 최적화·의존성 순환제거·전체 쿼리감사 등 7개 핵심성과를 근거 링크와 함께 순위화, 과거 수치 불일치(Chat N+1 21→4 vs 41→4) 정정 이력 포함.
- `.DS_Store` — macOS 메타파일. 내용 없음.

---

## 2. domains/ — 도메인 정본 문서

- `domains/.DS_Store` — 내용 없음 (macOS 메타파일).
- `domains/activity.md` — 전용 엔티티 없이 Care/Board/MissingPet 데이터를 합성하는 읽기 전용 타임라인이며, 메모리 페이징과 REVIEWS 미수집 등 한계를 정리.
- `domains/admin.md` — Facade+감사로그 경로와 도메인 서비스 직접호출 경로가 공존하는 운영자 게이트웨이 구조, `/api/admin`·`/api/master` 권한 경계를 정리.
- `domains/board.md` — 목록/댓글 배치조회, 조회수 `insertIgnore`, 인기글 스냅샷, 검색 분기, 반응 API의 body `userId` 한계까지 코드 기준으로 정리.
- `domains/care.md` — 채팅 거래확정→CareApplication→에스크로 흐름, SPATIAL 인덱스 근처조회, 에스크로 실패 시 롤백되지 않는 한계를 명시.
- `domains/chat.md` — `ConversationCreatorService` 생성규칙 중앙화, unread 원자적 갱신, 읽음처리 단순화, `CARE_APPLICATION` confirmDeal 미완성 한계.
- `domains/file.md` — 로컬 파일시스템 저장 + `FileTargetType` 폴리모픽 첨부, 배치조회로 N+1 완화, 물리파일 삭제 미연동 등 한계.
- `domains/location.md` — lat/lng 반경검색 우선분기, `ST_Within`/`ST_Distance_Sphere`, `size=300` 고정, 지역계층검색 비활성 상태를 코드 기준으로 정리.
- `domains/meetup.md` — 비관적락+조건부 UPDATE 참가 동시성, `afterCommit` 이벤트 채팅방 생성, 근처조회 2단계 쿼리, 홈추천 점수식.
- `domains/missingpet.md` — (코드는 `domain/board` 소속이나 별도 문서화) 목록/상세/댓글 API 분리, 댓글 일괄삭제 bulk update, 채팅 연결 경량화(작성자 projection).
- `domains/notification.md` — MySQL 영구저장+Redis 50개/24h 캐시+SSE+FCM 4단 파이프라인, 서버 메모리 SSE 한계와 트랜잭션 밖 발송.
- `domains/payment.md` — 펫코인 잔액/에스크로 비관적락 동시성 제어, PG 미연동 시뮬레이션 충전, 에스크로 실패가 Care 롤백으로 이어지지 않는 리스크.
- `domains/recommendation.md` — 게시글/케어/위치검색 이벤트→Python NLP 분석→signal 저장(원문 미저장)→추천카드 흐름과 threshold/TTL 표, confidence 해석.
- `domains/report.md` — `targetType+targetIdx` 폴리모픽 신고, WARN/SUSPEND 시 UserSanction 연동, 콘텐츠 신고의 제재대상 ID 매핑 위험.
- `domains/statistics.md` — 일/주/월 스냅샷과 `login_events` 기반 DAU 전환(2026-06-28) 이력, merge 방식 집계, Critical 버그 4건 수정완료 기록.
- `domains/user.md` — JWT+DB Refresh, OAuth 계정연결, 휴면계정 배치, 제재상태 동기화까지 인증 기반 도메인 전체를 코드 기준으로 정리.

### domains/domain/

- `domains/domain/도메인-구조-개요.md` — 백엔드 domain 패키지별 한 줄 요약·대표 엔티티, 프론트 components/api 대응표, ERD 개념도(mermaid)를 담은 온보딩용 구조 개요.
- `domains/domain/도메인별-데이터-구조.md` — 도메인별 테이블/필드/enum을 JPA 엔티티 기준으로 정리한 스키마 레퍼런스. 위 구조개요 문서와 쌍을 이루는 상세본.

---

## 3. domain-page-drafts/ — 포트폴리오 페이지 초안

> 각 파일은 대응하는 `domains/*.md`를 근거로 `*DomainV2.jsx` 페이지 콘텐츠를 점검·교정하는 지시서에 가깝다. `workflow.md`가 제작 절차.

- `domain-page-drafts/board-domain-v2-content.md` — `BoardDomainV2.jsx` 점검 초안. `domains/board.md`와 내용이 거의 일치, 댓글 배치조회 태그 추가·조회수 스니펫을 `insertIgnore` 기준으로 교정 지시.
- `domain-page-drafts/care-domain-v2-content.md` — `CareDomainV2.jsx` 점검 초안. "생성 시 body userId 신뢰" 같은 구식 표현을 교정하도록 지시.
- `domain-page-drafts/chat-domain-v2-content.md` — `ChatDomainV2.jsx` 점검 초안. `relatedType` 재사용 조건, SimpleBroker 확장 한계 등 보완 문구 제시.
- `domain-page-drafts/location-domain-v2-content.md` — `LocationDomainV2.jsx` "재작성 필요" 판정. 구버전 JSX의 지역계층검색·JSON적재 문구가 부정확함을 지적.
- `domain-page-drafts/meetup-domain-v2-content.md` — `MeetupDomainV2.jsx` 점검 초안. "Haversine+Bounding Box" 표현을 `ST_Within`/`ST_Distance_Sphere`로 정정.
- `domain-page-drafts/missingpet-domain-v2-content.md` — `MissingPetDomainV2.jsx` 점검 초안. 관리자 컨트롤러 경로 정정과 홈추천 카드 추가 여부 제안.
- `domain-page-drafts/query-audit-page-content.md` — 도메인별 페이지와 별개로 "쿼리 실측 감사" 과정 자체(성과가 아닌 실수 6건)를 보여주는 포트폴리오 페이지 초안. `analysis/query-audit/` 실측 커밋 근거만 사용.
- `domain-page-drafts/recommendation-domain-v2-content.md` — `RecommendationDomainV2.jsx` 점검 초안. threshold/TTL 단순화 표현을 domain×urgency 표로 교정, 건강알림 카드 추가 제안.
- `domain-page-drafts/user-domain-v2-content.md` — `UserDomainV2.jsx` 점검 초안. "SUSPEND_USER→addBan()" 등 실제와 다른 문구 제거, 아키텍처 문서 링크 파일명 교정 지시.
- `domain-page-drafts/workflow.md` — 위 9개 도메인 v2 초안 제작 절차(도메인 문서→아키텍처 문서→리팩토링 문서→DomainV2 페이지 순), 도메인별 진행상태 표 포함.

---

## 4. architecture/ — 아키텍처 문서

- `architecture/관리자 대시보드 & 통계 시스템 아키텍처.md` — 통계 집계·대시보드 연동. daily/weekly/monthly 배치, 결제 즉시반영(AFTER_COMMIT), Redis todayStats 1분 캐시, 프론트 DTO 불일치 등 운영 리스크표.
- `architecture/전체 아키텍처.md` — 프로젝트 전체 DDD/레이어드 구조 개관. 10개 핵심 도메인, 기술스택, 데이터흐름, 보안·성능 전략을 총망라한 최상위 아키텍처 문서.
- `architecture/시스템_아키텍처_다이어그램.md` — 위와 거의 동일한 내용을 Mermaid 다이어그램 중심으로 재구성. 배포 아키텍처(제안), API/WebSocket 엔드포인트 목록 포함해 "전체 아키텍처.md"와 상당 부분 중복.
- `architecture/홈화면-랭킹-알고리즘.md` — 홈 화면 4개 섹션(실종/주변서비스/모임/커뮤니티)의 병렬 API 호출과 각 도메인별 랭킹 점수식(거리·임박도·최신성 등).
- `architecture/.DS_Store` — macOS 메타파일. 내용 없음.
- `architecture/# Petory 한국어 케어 의도 분석 기반 주변 서비스 추천 시스템 계.ini` — (실제로는 텍스트 설계서) 로컬 개발 기준 Spring+FastAPI+MySQL 3단 구조, NLP 의도분석→추천 흐름과 phase별 구현 로드맵.
- `architecture/erd.md` — 전체 DB ERD(Mermaid), 테이블 상세 스키마, 인덱스/파티셔닝/백업 전략까지 포함한 데이터베이스 설계 문서.
- `architecture/domain-relationships.md` — 도메인 간 연관관계(Users 중심), 트랜잭션 경계, 캐스케이드, N+1 해결, 이벤트 기반 개선안을 코드 예시로 설명.
- `architecture/overview.md` — 레이어드 아키텍처와 도메인 표준 구조, 트랜잭션/캐시/보안 원칙을 개념적으로 정리한 아키텍처 개요.
- `architecture/Redis_캐싱_전략.md` — Redis의 실제 사용처(통계/알림/이메일인증/추천 dedup)와 설정만 있고 미사용인 캐시(boardList 등)를 구분해 현재 상태를 정확히 기록.

### 도메인별 architecture/

- `architecture/activity/사용자 활동 타임라인 아키텍처.md` — Activity는 저장 테이블 없이 Care/Board/MissingPet을 조회 시점에 합성하는 read model. 메모리 정렬/페이징 구조와 userId 파라미터 권한검증 미비 지적.
- `architecture/admin/관리자 운영 아키텍처.md` — Admin은 facade 기반(감사로그 있음)과 도메인 서비스 직접호출(감사로그 없음) 경로가 혼재. 권한 구조, AdminAuditLog, 프론트-백엔드 계약 불일치 정리.
- `architecture/board/커뮤니티 게시판 아키텍처.md` — 게시글/댓글/반응/조회수/인기글 스냅샷의 전체 흐름. batch 조회로 N+1 방지, 상세 캐시 비활성 이유, 인기글 popularityScore 산식과 fallback 순서.
- `architecture/care/펫케어 코인 관련 흐름.md` — 자유시장+가이드 제공 가격정책 설계(포트폴리오 관점)와 실제 코드 동작(최소코인 미검증 등) 차이를 명시한 정책+현황 혼합 문서.
- `architecture/care/펫 케어 & 매칭 아키텍처.md` — 케어 요청 생성→채팅 거래확정→에스크로→완료/취소→리뷰 전체 흐름. 거래확정 시 에스크로 실패가 롤백 안 되는 점 등 설계상 주의점.
- `architecture/chat/채팅 시스템 설계.md` — Conversation/ChatMessage 구조, STOMP+SockJS WebSocket 인증, unread 원자적 증가, 제재 사용자 채팅 처리(안내 플래그, 메시지 마스킹 없음).
- `architecture/file/첨부파일 저장 & 연결 아키텍처.md` — 로컬 파일시스템 저장과 도메인별 syncSingleAttachment 연결 분리 구조. 배치 조회로 N+1 방지, 관리자 삭제는 DB row만 삭제(물리파일 미삭제).
- `architecture/location/위치 기반 서비스 아키텍처.md` — 통합 지도(주변서비스/모임/펫케어)의 프론트 조합형 BFF 부재 구조. 반경검색(ST_Within+ST_Distance_Sphere), stable 정렬 정책, geocoding 프록시.
- `architecture/location/위치서비스_공공데이터_CSV_배치_임포트_구현.md` — 2026-05 CSV 배치 임포트 구현 기록(1000건 배치, REQUIRES_NEW 분리, 세션오염 해결). 상단에 2026-07 기준 최신 구조와의 차이 정정 명시.
- `architecture/meetup/산책 & 오프라인 모임 아키텍처.md` — 모임 생성/참가 동시성 제어(비관적 락+조건부 UPDATE+복합PK), 채팅방 비동기 생성/복구, 제재 주최자·참가자 후속처리.
- `architecture/missingpet/실종 제보 아키텍처.md` — 실종 제보 게시글/목격댓글/채팅시작 흐름. domain/board 패키지에 물리적으로 섞여 있다는 점과 홈 추천 bounding box+Haversine 계산.
- `architecture/notification/알림 시스템 아키텍처.md` — 도메인 이벤트를 MySQL/Redis/SSE/FCM으로 분기하는 전달계층. DB저장과 Redis/SSE/FCM이 outbox 없이 같은 메서드에서 호출되는 비원자성 명시. (↔ `user/알림 시스템 아키텍처.md`와 상세도만 다름, §0 참고)
- `architecture/payment/펫코인 결제 아키텍처.md` — 잔액 변경(findByIdForUpdate 비관적락)+거래기록+에스크로 HOLD/지급/환불 구조. 통계 기록이 2026-07 이벤트화(AFTER_COMMIT)로 결제 트랜잭션에서 분리된 점 강조.
- `architecture/recommendation/반려생활 추천 & NLP 아키텍처.md` — Spring↔FastAPI 분리 구조. signal 저장(TTL/threshold), petIntentExecutor 트래픽제어, Redis dedup fail-closed 등 NLP 연동 장애격리 정책.
- `architecture/report/신고 및 제재 아키텍처.md` — Report(신고 접수/처리)와 UserSanctionService(제재) 연동 구조도 중심 요약. 신고처리와 콘텐츠 조치가 분리되어 있다는 설계 경계 강조. (↔ `user/신고 및 제재 시스템 아키텍처.md`와 상세도만 다름, §0 참고)
- `architecture/user/제재 상태 도메인 영향 작업 목록 2026-06-28.md` — 코드 수정 전 작성된 제재상태(SUSPENDED/BANNED)가 Care/Meetup/Chat 등에 미치는 영향 분석과 작업목록. 이후 구현 현황(06-30, 07-01 보정) 추적기록까지 포함.
- `architecture/user/신고 및 제재 시스템 아키텍처.md` — User 도메인 관점 ReportService/UserSanctionService 코드(Java 스니펫)와 시퀀스를 상세히 다루는 구현 레퍼런스.
- `architecture/user/사용자 인증 및 프로필 아키텍처.md` — JWT 인증(access15분/refresh1일), OAuth2, 프로필 조합(GET /api/users/me), 펫 관리, 휴면계정, 제재 연결까지 User 도메인 인증/프로필 전반.
- `architecture/user/이메일 인증 시스템 아키텍처.md` — 단일 토큰+purpose enum 기반 통합 이메일 인증 설계. 1단계(로그인만)/2단계(이메일인증 필수) 권한정책과 API 엔드포인트.
- `architecture/user/알림 시스템 아키텍처.md` — NotificationService/SSE 서비스의 Java 코드 스니펫과 시퀀스를 상세히 담은 구현 레퍼런스.

---

## 5. analysis/ — 분석·감사 문서

### analysis 루트

- `analysis/리팩토링-순서-2026-07.md` — 2026-07-06, "리팩토링 세대 지도": 같은 문제를 여러 번 고친 사례들의 최신 세대 vs 옛 세대 정리. N+1(Board/MissingPet/Chat), 동시성, 검색, 순환의존 제거 등 5개 사례별 "현재 코드=유일 기준" 정리.
- `analysis/admin-logic-placement-decision-2026-07.md` — 2026-07-11, 어드민 로직을 user→admin 도메인으로 옮기자는 제안을 검토 후 "현상 유지" 결정(projection DTO 이동 시 순환의존 발생).
- `analysis/admin-statistics-domain-analysis.md` — "데이터 집계 및 통계 분석" 기능의 도메인 정합성 검토. Meetup/MissingPet/Report/댓글 집계 누락 지적, Meetup+Report 추가 권장.
- `analysis/admin-ui-unification.md` — 관리자 페이지 UI 비교 분석. 사용자 관리만 다른 관리 섹션과 구조가 다름을 지적, 통합 방안 3가지(A~C) 제안.
- `analysis/backend-full-domain-review-2026-07.md` — 13개 도메인·463개 파일 룰 기반 3차 코드 리뷰. Critical 4건(EAGER fetch, private @Transactional, 트랜잭션 내 FCM I/O, WebSocket 인가 부재) 전부 조치 완료.
- `analysis/board-deep-page-2026-07.md` — 2026-07-15, board 깊은 페이지 지연조인+author_visible 비정규화 개선 전후 실측(EXPLAIN/COUNT/k6). 깊은 페이지 24~32ms(구코드 133ms), 페이지 결손 23.8% 검증.
- `analysis/domain-dependency-refactoring-2026-07.md` — 2026-07-05/06, 도메인 간 순환 의존(user 허브화) 개선 설계+실행. 역방향 의존 4종 분류 처방, Step1~4+6 적용 완료, user는 완전한 leaf가 됨.
- `analysis/job-fit-gap-analysis-2026-07.md` — 2026-07-11, 채용공고 대비 기술스택 갭 분석. QueryDSL/모니터링(Grafana)/AWS 실사용 부족을 우선 학습 과제로 판정.
- `analysis/query-plan-monitoring-design.md` — 2026-07-14(v2), board 쿼리 계획 감시 일반화 설계. v1 근거 오류를 실API 재검증(COUNT 180,003행/141ms 미해결 발견). 표본이 API 1개뿐이라는 한계 명시.
- `analysis/scalability-maintainability-review-2026-07.md` — 2026-07-05, 확장성·유지보수성 종합 리뷰(465 Java+108 JS 파일). Critical 2건(user 허브화 순환의존, 수평확장 막는 SSE/파일/배치 3지점).
- `analysis/team-role-analysis.md` — 2026-04-10, 가상 3인 팀 역할 분배 보고서. SecurityConfig 인증 허점, 채팅 읽음처리 미완성 등 실제 이슈 다수 포함.

### analysis/dependency-graph/

- `analysis/dependency-graph/before-2026-07-06.txt` — 도메인 간 import 의존성 그래프 스냅샷(리팩토링 전). user→care/location/meetup/report 역방향 엣지 포함.
- `analysis/dependency-graph/after-step1-4-2026-07-06.txt` — 같은 그래프의 Step1~4 적용 후 스냅샷. before와 diff하면 리팩토링 효과가 드러남.

### analysis/entity-schema/

- `analysis/entity-schema/00-overview.md` — 엔티티 스키마 분석 시리즈(01~04) 개요. 28개 엔티티 목록, 인덱스·정규화·N+1·트랜잭션 핵심 요약.
- `analysis/entity-schema/01-index-analysis.md` — 도메인별 인덱스 현황과 권장 추가 인덱스 정리(carerequest/careapplication/pet_coin_transaction/notifications 등 우선순위 표).
- `analysis/entity-schema/02-normalization-analysis.md` — 도메인별 1NF~3NF 정규화 수준 분석. Board/Meetup/Conversation/DailyStatistics 등 의도적 비정규화 정리, 대부분 3NF 준수로 결론.
- `analysis/entity-schema/03-n-plus-one-strategy.md` — N+1 해결 전략: 단건/단일 컬렉션은 Fetch Join, 페이징/다중 컬렉션은 `@BatchSize`. 도메인별 적용 현황 표.
- `analysis/entity-schema/04-transaction-concurrency.md` — 트랜잭션 관리(서비스 레이어 `@Transactional`, readOnly 기본)와 동시성 제어(DB 유니크, 원자적 UPDATE, 비관적 락) 사례 정리.
- `analysis/entity-schema/evidence/query-baseline-2026-07-13.md` — 대량 시드(board 5만/comment 15만) 실측 baseline. board 목록 매 페이지 5만행 filesort(0.09~0.17s) 원인(선택도 오판)을 히스토그램으로 해결(0.00s).
- `analysis/entity-schema/evidence/denormalization-consistency-2026-07-13.md` — 반정규화 카운터 7종 실측. 코드는 정합이나 DB 데이터는 대량 더미 생성으로 오염(view_count 총합 515만 vs 로그 17행).

### analysis/location/

- `analysis/location/위치-기반-서비스-상세-분석.md` — 2026-02-03, 위치기반서비스 백엔드/프론트엔드 구현을 `.cursorrules` 대비 점검. 백엔드는 양호, 프론트는 다수 문제 지적 후 대부분 해결.
- `analysis/location/상세-분석-재점검-결과.md` — 위 상세분석의 "문제" 지적들을 도메인 문서와 대조해 재평가, 표현을 순화.

### analysis/query-audit/

> 파이프라인: `00-plan.md`(방법론) → 도메인별 실측(`board`/`care`/`meetup`/`etc-domains`/`admin`) → `fixes-2026-07-14.md`(처방+회귀테스트) → `99-summary.md`(종합).

- `analysis/query-audit/00-plan.md` — 2026-07-14, 전체 쿼리 감사 방법론. "실제 API를 curl로 호출" 원칙, 3-패스 스캔, 스케줄러 OFF·`max_digest_length` 확장 등 선행조건.
- `analysis/query-audit/board-2026-07-14.md` — board 실측. 신규 발견: 깊은 페이지 목록 100,000행 검사/0행 반환/129ms, 자동생성 COUNT 60,001행/호출. N+1·과잉락 없음.
- `analysis/query-audit/care-2026-07-14.md` — care 실측. 치명: 검색 엔드포인트 HTTP 500(FULLTEXT 없음), 인덱스 3개뿐(전부 PK/FK)이라 목록·주변검색 전부 풀스캔+filesort.
- `analysis/query-audit/meetup-2026-07-14.md` — meetup 실측. `@BatchSize` 정상 작동(N+1 아님) 확인, 진짜 원인은 검색/주변 엔드포인트에 페이징이 없어 500건씩 반환.
- `analysis/query-audit/etc-domains-2026-07-14.md` — chat/location/user/notification/payment/report/file + 스케줄러 실측. `/api/pets/type/{type}` 무제한 반환(7,667건)이 최악 발견.
- `analysis/query-audit/admin-2026-07-14.md` — admin(+statistics) 실측. 프로젝트 유일의 진짜 N+1 발견(`/api/admin/care-requests` 20건→60쿼리). statistics는 가장 깨끗함.
- `analysis/query-audit/fixes-2026-07-14.md` — 처방 1~6 적용 결과. care 검색 500→200, admin care 66→7쿼리, meetup 검색 583ms→43ms 등. 회귀테스트 8개.
- `analysis/query-audit/99-summary.md` — 전체 12개 도메인·엔드포인트 62개 실측 종합. N+1은 admin 1건뿐, 진짜 문제는 "페이징 없음", 도메인별 인덱스 성숙도 극과 극.

---

## 6. concurrency/ — 동시성 제어

- `concurrency/concurrency-strategy-master.md` — 2026-07-07, 동시성 8개 시나리오를 4전략(비관적 락/원자적 UPDATE/DB Unique/트랜잭션 경계)으로 분류한 마스터 문서. Meetup 속도 벤치마크(비관적 락 2.4ms vs 원자적 UPDATE 8.4ms)는 §부록에 **인용 금지 사유와 함께** 참고 기록으로만 보존.
- `concurrency/evidence/race-condition-reverify-2026-07-12.md` — Meetup/PetCoin/Care 동시성 테스트 재실행+worktree 재현. PetCoin Lost Update 재현·해결 확인, Meetup 최초버그(a549eb33)는 인원초과가 아니라 Deadlock이었음을 새로 발견.
- `concurrency/transaction-concurrency-cases.md` — 트랜잭션 관리(cascade delete, 댓글수 동기화, readOnly)와 동시성 제어(조회수·반응 중복방지, 원자적 증가) 실제 코드 사례 모음.

---

## 7. db_concept/ — 면접용 DB 하이라이트

- `db_concept/db-concept-highlights-board.md` — Board N+1 해결(301→3쿼리), 인덱스 설계, 인기글 스냅샷(CompletableFuture 병렬화), FULLTEXT 검색 어필 포인트.
- `db_concept/db-concept-highlights-care.md` — Care N+1 3단계 해결(2400→4-5쿼리), 비관적 락 거래확정 Race Condition·에스크로 동시성, 인덱스 설계.
- `db_concept/db-concept-highlights-chat.md` — unreadCount 원자적 증가, 읽음처리 최적화, 채팅목록 N+1 배치조회, FULLTEXT 2단계 검색, REQUIRES_NEW self-invocation 해결.
- `db_concept/db-concept-highlights-location.md` — ST_Within+ST_Distance_Sphere 이중필터 공간쿼리, 검색분기 우선순위, review_count 캐시, 초기로드 개선(22,699→1,026건).
- `db_concept/db-concept-highlights-meetup.md` — 원자적 UPDATE 동시성, JOIN FETCH/EntityGraph/BatchSize N+1 해결, BETWEEN 조건 인덱스 활용(2958→117행).
- `db_concept/db-concept-highlights-missing_pet.md` — N+1 해결(105→3쿼리), 배치 소프트삭제 UPDATE, orphanRemoval과 Soft Delete 충돌 분석·해결.
- `db_concept/db-concept-highlights-user.md` — Refresh Token DB이중검증, 경고횟수 원자적증가, PetCoin 비관적락, socialUsers BatchSize N+1, OAuth2 동시로그인 DB Unique 방어.
- `db_concept/real-mysql-interview-stories.md` — Real MySQL 면접용 짧은 스토리 3개(N+1 배치조회, 게시글 인덱스+프로젝션, 공간검색 SPATIAL INDEX 튜닝).

---

## 8. deployment/ — 배포

- `deployment/README.md` — 배포 문서 목차+아키텍처 다이어그램. 5컨테이너(nginx/app/mysql/redis/nlp-server) 구조와 빠른시작 안내.
- `deployment/00-macos-local.md` — macOS 로컬 Docker 가이드. Apple Silicon `-jammy` 태그 필수, 포트충돌·CRLF 등 맥 특유 이슈.
- `deployment/01-deployment-strategy.md` — Docker+Nginx 리버스프록시 배포 전략 개요. 현재는 Rolling Update, Blue-Green은 향후 과제.
- `deployment/02-docker-configuration.md` — Dockerfile(멀티스테이지 jammy)·docker-compose.yml(5서비스) 실제 구성. 스키마는 Flyway가 기동 시 적용.
- `deployment/03-cicd-pipeline.md` — GitHub Actions CI/CD 예시. 현재 레포는 테스트·빌드까지만 있고 이미지 push·자동배포(CD)는 미구축.
- `deployment/04-nginx-configuration.md` — nginx.conf/default.conf 예시, SSL·Rate Limiting·캐싱·프록시 설정.
- `deployment/05-environment-variables.md` — `.env.example` 전문과 docker-compose 매핑. OAuth2/JWT_SECRET 미설정 시 부팅 실패 등 필수 변수 강조.
- `deployment/06-deployment-process.md` — 수동 배포 8단계, 롤백은 git checkout 방식(레지스트리 push 미구축).
- `deployment/07-monitoring-logging.md` — Actuator+Prometheus 모니터링, docker stats/logs 수집. 대부분 "선택" 항목으로 미구현 참고 자료.
- `deployment/08-troubleshooting.md` — 포트충돌·arm64 빌드실패 등, 2026-07-13 Flyway 도입으로 스키마 4사본 분열 문제 해결 기록.
- `deployment/09-mobile-capacitor.md` — React 앱을 Capacitor로 감싸 Android/iOS 빌드하는 가이드. FCM 푸시 흐름, iOS Pods 트러블슈팅.

---

## 9. performance/ — 성능 측정·최적화

- `performance/query-optimization.md` — N+1해결(배치조회/FetchJoin)·인덱스전략·캐싱·커서페이징 등 백엔드 성능 기법 종합 가이드.
- `performance/real-mysql-application-log.md` — Real MySQL 학습주제를 Petory 실제 케이스에 매핑한 4주 학습로그 템플릿(대부분 미완성 기록).
- `performance/performance-testing/care_request_dummy_data.sql` — petory_test DB에 COMPLETED 상태 CareRequest 1000건 랜덤 생성.
- `performance/performance-testing/dummy_data_insert_improved.sql` — 대용량 부하테스트용(Users 1만, Board 10만, Comment 100만, BoardReaction 50만) 배치 삽입.
- `performance/performance-testing/dummy_data_insert.sql` — 소규모 버전 더미데이터(Users 1000, Board 5000) 삽입 초안.
- `performance/performance-testing/generate_meetup_dummy_data.sql` — Meetup 1000~5000건 생성 프로시저.
- `performance/performance-testing/truncate_test_db.sql` — petory_test 주요 테이블 TRUNCATE 스크립트.
- `performance/performance-testing/k6/nearby-loadtest-results.md` — 2026-07-11, nearby API before/after 실측. 소규모 p95 78→37ms(-52%), 대용량 p95 1.75s→57.5ms(~30배).
- `performance/performance-testing/k6/nearby-loadtest.js` — GET /api/meetups/nearby 부하테스트 k6 스크립트.

---

## 10. troubleshooting/ — 문제 발견·원인·해결 기록

- `troubleshooting/.DS_Store` — 내용 없음.

### board/

- `troubleshooting/board/admin-search-specification-bug.md` — 관리자 검색 500 에러 2건(CLOB lower(), FULLTEXT SQL 오류) → FunctionContributor로 해결완료.
- `troubleshooting/board/code-duplication-mapping.md` — BoardService 매핑 로직 중복·관리자 목록 메모리 필터링. 공통 메서드 추출은 완료, DB 레벨 필터링은 미완료(부분해결).
- `troubleshooting/board/performance-optimization.md` — 게시글 목록 N+1 해결완료: 301→3쿼리(-99%), 745ms→30ms. (재검증은 `refactoring/board/evidence/` 참고)

### care/

- `troubleshooting/care/care-deal-confirmation-race-condition.md` — 거래 양측 동시확정 Stuck State 발견 → Conversation 비관적 락으로 해결완료.
- `troubleshooting/care/care-domain-technical-analysis.md` — 거래확정 동시성+목록조회 N+1 요약. 쿼리 -99.8%, 응답 -94%. 해결완료.
- `troubleshooting/care/care-request-n-plus-one-analysis.md` — 비페이징 케어요청 N+1 단계별 해결: 2400→4-5쿼리(-99.8%). (재검증은 `refactoring/care/evidence/` 참고)
- `troubleshooting/care/care-request-paging-n-plus-one.md` — 페이징 경로 applications N+1. @BatchSize(50)만 완료, JOIN FETCH 대안 미완료(부분해결).
- `troubleshooting/care/potential-issues.md` — 권한검증 부재는 해결완료. 스케줄러 자동완료·상태전이 검증 없음은 미해결(권고 수준).

### chat/

- `troubleshooting/chat/n-plus-one-conversationparticipant.md` — 케이스B(getMyConversations) 수정완료, 케이스A(단건조회 반복)는 방향만 제시(부분해결).
- `troubleshooting/chat/read-status-performance.md` — markAsRead() 전체조회 로직 제거로 해결완료. 트랜잭션 범위 축소.

### location/

- `troubleshooting/location/current-implementation-analysis.md` — 지도 UX 문제(즉시호출/마커500개/동기화 약함) 종합점수 5.4/10. 미해결, 로드맵만 제시.
- `troubleshooting/location/initial-load-performance.md` — 초기 로드 전체조회→10km 반경검색 전환. 조회량 -95.5%, 시간 -52.8%. (재검증은 `refactoring/location/evidence/` 참고)
- `troubleshooting/location/map-ux-improvement.md` — 지도 UX 문제 원인분석과 "지도는 탐색UI" 원칙 기반 개선안(제안 단계, 미해결).
- `troubleshooting/location/search-strategy-comparison.md` — 위치기반 vs 시군구 검색 성능 비교: 시군구가 5-6배 빠름. 설계 결정 문서.

### meetup/

- `troubleshooting/meetup/n-plus-one-query-issue.md` — 모임목록 organizer N+1(75쿼리) JOIN FETCH로 해결완료(75→1쿼리).
- `troubleshooting/meetup/race-condition-participants.md` — 동시참가 인원초과를 원자적 UPDATE+DB CHECK 제약으로 해결완료. 5가지 대안 비교.

### missing-pet/

- `troubleshooting/missing-pet/n-plus-one-query-issue.md` — 게시글목록 댓글 N+1(103회) 해결완료: 207→3쿼리(-97%).
- `troubleshooting/missing-pet/orphanRemoval-soft-delete-analysis.md` — orphanRemoval=true와 Soft Delete 충돌 분석. 권고 단계(미해결로 보임).
- `troubleshooting/missing-pet/performance-measurement-results.md` — N+1 실측: 207쿼리/571ms/11MB → 3쿼리/79ms/4MB. 목표 달성.
- `troubleshooting/missing-pet/potential-issues.md` — N+1은 해결됨. 권한검증 부족, orphanRemoval 충돌 등은 미해결로 남음.

### payment/

- `troubleshooting/payment/payment-troubleshooting-analysis.md` — Critical 6개(롤백 누락, Race Condition, 환불 미구현 등) 모두 해결완료(2026-01-28).
- `troubleshooting/payment/payment-troubleshooting-detailed.md` — 위 분석의 실제 구현 상세(코드 diff, 시퀀스). 비관적 락 적용 등 전부 해결완료.

### petRecommendation/

- `troubleshooting/petRecommendation/nlp-server-issues-2026-06-09.md` — NLP 서버 품질/계약 이슈 6건 모두 수정완료.
- `troubleshooting/petRecommendation/pet-recommendation-bugs-2026-05-31.md` — dangling signal, 인가누락 등 5건 모두 수정완료.
- `troubleshooting/petRecommendation/pettype-422-silent-drop-2026-06-10.md` — Java/Python petType enum 불일치로 signal 조용히 드롭. normalizePetType()으로 수정완료.

### users/

- `troubleshooting/users/conversation-service-performance-optimization.md` — 채팅방목록 N+1 해결완료: 21→4쿼리(-80.95%).
- `troubleshooting/users/login-n-plus-one-issue.md` — 로그인 시 채팅방목록 N+1 해결완료: 21→4쿼리, 305ms→55ms.
- `troubleshooting/users/sanction-auth-gap-2026-06-28.md` — 제재 우회 6건 중 A1~A4·A6 수정완료, A5는 부분해결.
- `troubleshooting/users/soft-delete-nickname-reuse.md` — 탈퇴회원 닉네임 재사용 불가 문제, isDeleted 필터 추가로 해결완료.

---

## 11. refactoring/ — 리팩토링 기록 (109개, 최대 폴더)

### 루트 단일 파일

- `refactoring/.DS_Store` / `refactoring/user/.DS_Store` — macOS 메타파일, 내용 없음.
- `refactoring/authentication-principal-refactoring.md` — `CustomUserDetails` 도입 1차 완료(JWT 필터 중복 파싱 제거는 미완).
- `refactoring/dto-to-record.md` — DTO를 record로 전환하는 기준 정리. Response DTO는 record 우선, Request/빌더 필요시 Lombok 유지.
- `refactoring/entity-encapsulation-backlog.md` — `@Setter` 남용 문제 0~7단계 전부 완료 + 리뷰 후 Critical 4건 추가 수정완료.
- `refactoring/JWT-토큰-리팩토링-백로그.md` — Access TTL 단일화만 반영 완료, Refresh 회전 등은 미반영 백로그.
- `refactoring/Petory-Backend-Refactoring-vs-Troubleshooting.md` — Location/Meetup/Payment/Statistics Fix1~10 대부분 완료 + 도메인별 리팩토링 백로그.
- `refactoring/portfolio-refactoring-troubleshooting-selection.md` — 100개+ 문서 중 포트폴리오 본문 4개(N+1, 동시성, Location 초기로드, 보안/인가) 선별 기준.

### admin/

- `refactoring/admin/2026-04-18-admin-domain-redesign.md` — Admin P0 버그 2건 수정 + Facade 레이어, 감사 로그 DB 영속화 완료.
- `refactoring/admin/2026-05-04-admin-auth-contract-hardening.md` — 삭제된 관리자 계정 인증 차단, 문서-코드 계약 불일치 정리 계획(완료 표시 없음).

### board/

- `refactoring/board/board-backend-performance-optimization.md` — Admin 전체 메모리 로드 등 15개 항목 대부분 완료, BoardViewLog existsBy만 미적용.
- `refactoring/board/board-popularity-snapshot-batch-analysis.md` — 인기글 스냅샷 배치 집계 O(n) 최적화 여지 발견(3단계 개선안 제안).
- `refactoring/board/board-popularity-snapshot-batch-refactoring.md` — CompletableFuture 병렬화, LIKE 전용 쿼리, 통합 DTO 적용 완료.
- `refactoring/board/board-popularity-snapshot-n-plus-one-refactoring.md` — 스냅샷 30개마다 getAttachments 개별호출(31쿼리)→배치 2쿼리로 전환, 테스트 8/8 PASS.
- `refactoring/board/comment-reaction-query/troubleshooting.md` — 댓글 반응 2N 쿼리(41쿼리)→배치조회 3~4쿼리로 해결.
- `refactoring/board/evidence/n-plus-one-reverify-2026-07-12.md` — Board 목록 N+1 재검증(301→3쿼리, 561ms→55ms), worktree로 실제 커밋 재실행 확인.

### care/

- `refactoring/care/care-payment-code-review-2026-04-14.md` — Critical 6건(트랜잭션 과대, @PreAuthorize 누락 등) 전부 수정완료.
- `refactoring/care/care-payment-refactoring-2026-04-14.md` — 위 리뷰 8개 항목의 개선코드+상태 정리, 전부 완료.
- `refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md` — Care 목록 N+1 재검증(101→2쿼리), file 테이블 인덱스 부재 추가 발견 후 해결.

### chat/

- `refactoring/chat/chat-backend-security-transaction-2026-04-14.md` — IDOR, 참여자 검증 부재, self-invocation, N+1, LIKE 검색 전부 개선 완료.
- `refactoring/chat/chat-code-review-2026-04-14.md` — 위 조치의 원본 리뷰(Critical 5, Warning 4), D1만 보류.
- `refactoring/chat/evidence/n-plus-one-reverify-2026-07-12.md` — 채팅방 목록 N+1 재검증, 실제 41→4(-90.2%) 확인.

### ds-algorithm/

- `refactoring/ds-algorithm/validation-matrix.md` — 도메인별 자료구조/알고리즘 개선 후보 검증 백로그, petRecommendation 태그매칭만 테스트 통과.

### exception/ (00/01 개요 + 도메인별 12개)

- `refactoring/exception/.DS_Store` — 내용 없음.
- `refactoring/exception/00-exception-analysis-overview.md` — 도메인별 예외 리팩토링 현황표, 공통 문제, HTTP 매핑 원칙.
- `refactoring/exception/01-exception-handling-structure.md` — GlobalExceptionHandler 처리 우선순위 8단계, ApiException 통합 원칙.
- `refactoring/exception/admin/관리자예외처리.md` — Admin 예외 체계 도입 제안.
- `refactoring/exception/board/보드예외처리.md` — BoardNotFoundException 등으로 통일(대부분 완료).
- `refactoring/exception/care/케어예외처리.md` — CareNotFound 등 5종 예외 도입 계획(미완료).
- `refactoring/exception/chat/채팅예외처리.md` — Conversation/ChatMessage 예외 통합 예정(미완료).
- `refactoring/exception/file/파일예외처리.md` — FileNotFound/Storage 등 4종 예외 정리 예정(미완료).
- `refactoring/exception/location/위치예외처리.md` — LocationServiceNotFound 등 5종 예외 정리 예정(미완료).
- `refactoring/exception/meetup/모임예외처리.md` — MeetupNotFound 등 개선 예정(미완료, 실제로는 상당 부분 반영됨).
- `refactoring/exception/notification/알림예외처리.md` — NotificationNotFound/Forbidden 2종으로 단순 정리(미완료).
- `refactoring/exception/payment/결제예외처리.md` — PetCoin 관련 5종 예외 정리 예정(미완료).
- `refactoring/exception/report/신고예외처리.md` — Report 관련 5종 예외 정리 예정(미완료).
- `refactoring/exception/statistics/통계예외처리.md` — 현재 예외 발생 코드 자체 없음, 확장 시 검토만 기록.
- `refactoring/exception/user/유저예외처리.md` — UserNotFound·Banned·Suspended 등 12종 예외로 완전 정리(전부 완료).

### fetch-optimization/

- `refactoring/fetch-optimization/.DS_Store` — 내용 없음.
- `refactoring/fetch-optimization/README.md` — Fetch 전략 규칙 총론: 단건은 Fetch Join, 페이징은 `@BatchSize(50)`.
- `refactoring/fetch-optimization/board/Fetch 전략 개선 (Fetch Join vs Batch Size).md` — Board: 단건 상세·Admin 페이징에 Fetch Join 필요, 나머지는 이미 적용됨.
- `refactoring/fetch-optimization/care/Fetch 전략 개선 (Fetch Join vs Batch Size).md` — Care: 단건 완료, 페이징은 @BatchSize(50)로 100명 기준 3쿼리 수렴.
- `refactoring/fetch-optimization/location/Fetch 전략 개선 (Fetch Join vs Batch Size).md` — Location: N+1 없음 확인 + 리뷰 관련은 JOIN FETCH 적용 완료.
- `refactoring/fetch-optimization/meetup/Fetch 전략 개선 (Fetch Join vs Batch Size).md` — Meetup: 단건·목록·참여자 전부 Fetch Join/BatchSize 적용 완료.
- `refactoring/fetch-optimization/payment/Fetch 전략 개선 (Fetch Join vs Batch Size).md` — Payment: EntityGraph·findByIdWithUser 적용, release/refund는 락 우선이라 낮은 우선순위.
- `refactoring/fetch-optimization/user/Fetch 전략 개선 (Fetch Join vs Batch Size).md` — User: 단건 상세는 Fetch Join 필요(미적용), 관리자 페이징은 이미 BatchSize 적용.
- `refactoring/fetch-optimization/catesian/cartesian-product-verification.md` — 다중 `@OneToMany` 카타시안 위험도 점검(대부분 낮음~중간). 폴더명 `catesian`은 오타.
- `refactoring/fetch-optimization/column-projection-review.md` — 컬럼/연관 오버페칭 축. Board/Users/CareRequest projection 적용, Report·LocationServiceReview는 unpaged→페이징+projection.
- `refactoring/fetch-optimization/evidence/measurement-2026-07-10.md` — 위 실측 원자료(2026-07-10). Board 응답시간 -25%, Users -15%, Care -74%.

### location/ (루트, archive/, evidence/)

- `refactoring/location/키워드-검색-품질-검증.md` — FULLTEXT 인덱스가 쿼리와 완전히 일치함을 검증(2026-02-04).
- `refactoring/location/지도-결과-안정성-리팩토링.md` — 2026-05-29, 지도 이동만으로 결과 흔들리던 문제 해결(캐시키 분리, size 고정 300, stable 정렬).
- `refactoring/location/지도-검색-워크플로우-정리.md` — 지도 뷰 상태와 검색 상태 분리, "이 지역 검색" 명시적 확정 UX 도입 범위 정의.
- `refactoring/location/주변서비스-알고리즘-설계안.md` — 2026-04-12, 처음부터 설계한다면의 설계안(SearchMode 모델링, 복합 스코어, 커서 페이지네이션).
- `refactoring/location/주변서비스-현행vs설계안-비교.md` — 위 설계안 대비 실제 구현 결과 대조(위치 우선 분기 적용, POLYGON bbox 유지 등).
- `refactoring/location/archive/검색-분기-및-카테고리-필터-통합.md` — (보관용) 검색 우선순위/카테고리 필터 통합. 이후 설계안 문서로 대체.
- `refactoring/location/archive/거리-계산-중복-제거.md` — (보관용) 백엔드/프론트 중복 거리계산 제거.
- `refactoring/location/archive/주변서비스-정렬-옵션-추가.md` — (보관용) distance 기본 정렬 전환 목표 정의.
- `refactoring/location/archive/주변서비스-후속-쿼리-리팩토링.md` — (보관용) reviews 서브쿼리 비용·카테고리 필터 복잡성 문제 제기.
- `refactoring/location/archive/상태-관리-개선.md` — (보관용) useState 24개+useRef 6개 → useReducer 3개로 통합 완료.
- `refactoring/location/evidence/initial-load-reverify-2026-07-12.md` — 초기로드 재검증: 22.4MB/531.8ms → 100KB/50.9ms(-99.6%/-91.9%).
- `refactoring/location/location-domain-potential-issues-refactoring.md` — 잠재이슈 4건 중 3건 해결, 반경검색 POLYGON 근사 위험은 모니터링 대기.

### meetup/ (루트, nearby-meetups/, participants-query/, subquery-optimization/)

- `refactoring/meetup/.DS_Store` — 내용 없음.
- `refactoring/meetup/backend-도메인-로직-점검.md` — Critical 2건, High 7건 중 대부분 반영 완료.
- `refactoring/meetup/db-review-2026-05-09.md` — organizer/participants EAGER→LAZY 전환 등 반영 완료(adf3bc4), 일부 미반영.
- `refactoring/meetup/duplicate-query-removal.md` — joinMeetup 중복 findById() 2회 호출을 refresh()로 대체.
- `refactoring/meetup/frontend-performance-optimization.md` — MeetupPage.js(2889줄) 등 프론트 성능 이슈 Critical~Low 12개.
- `refactoring/meetup/meetup-backend-performance-optimization.md` — 백엔드 전체 성능 이슈 총괄(하위 실험 폴더 결과 링크).
- `refactoring/meetup/recovery-scheduler-n-plus-one.md` — 복구 스케줄러가 N+1이 아니었음을 재검증(최초 가설 오진).
- `refactoring/meetup/refactoring-summary.md` — 백엔드 19개·프론트 4개 파일 분석 결과 종합 인덱스.
- `refactoring/meetup/stream-operation-refactoring.md` — Stream toDTO 변환 로직 공통 메서드 추출(가독성 개선, 성능 영향 없음).
- `refactoring/meetup/transaction-annotation-guide.md` — @Transactional을 Service 계층에 두는 것이 맞다는 가이드.
- `refactoring/meetup/nearby-meetups/explain-queries.sql` — Bounding Box vs 공간인덱스 EXPLAIN용 SQL 원본.
- `refactoring/meetup/nearby-meetups/index-analysis.md` — idx_meetup_location 등 인덱스 현황과 전환 근거.
- `refactoring/meetup/nearby-meetups/performance-comparison.md` — 486→273ms(-43.8%), 스캔행 2958→117(-96%).
- `refactoring/meetup/participants-query/performance-comparison-participants.md` — PrepareStatement 102→2(-98%), 실행시간은 102→178ms 증가(트레이드오프).
- `refactoring/meetup/participants-query/performance-results-participants-after.md` — 적용 후 실측 상세(178ms, PrepareStatement 2개).
- `refactoring/meetup/participants-query/performance-results-participants-before.md` — 베이스라인(102ms, PrepareStatement 102개).
- `refactoring/meetup/subquery-optimization/서브쿼리 최적화.md` — 서브쿼리→LEFT JOIN+GROUP BY 전환, 156→57ms(-63.5%).
- `refactoring/meetup/subquery-optimization/explain-results.md` — 리팩토링 후에도 풀스캔·임시테이블 발생 확인, 추가 최적화 여지.
- `refactoring/meetup/subquery-optimization/performance-comparison.md` — 156→57ms, 메모리 -89.5%, PrepareStatement 동일(N+1 아니었음 확인).
- `refactoring/meetup/subquery-optimization/performance-results-before.md` — 서브쿼리 방식 베이스라인(156ms, PrepareStatement 6개).

### missing-pet/

- `refactoring/missing-pet/evidence/n-plus-one-reverify-2026-07-12.md` — 267→4쿼리(-98.5%) 재검증, 커버링 인덱스로 개별조회 100회를 배치 1회로 대체.
- `refactoring/missing-pet/missing-pet-backend-performance-optimization.md` — Admin 전체로드→페이징, 댓글 배치 UPDATE 등 대부분 완료.

### mobile/

- `refactoring/mobile/fcm-security-and-bugfix.md` — FCM 버그·보안 이슈 7건 중 6건 수정완료(2026-05-09), 포그라운드 인앱 알림만 미적용.

### notification/

- `refactoring/notification/notification-read-performance-optimization.md` — 전체읽음 102개 statement→JPQL bulk UPDATE 1개(-99%).

### payment/

- `refactoring/payment/payment-backend-performance-optimization.md` — 메모리 페이징·N+1 등 10개 항목 중 대부분 완료.
- `refactoring/payment/petcoin-service-race-condition.md` — findById(락없음)→findByIdForUpdate로 동시충전 문제 해결.

### petRecommendation/ (시간순 스토리)

- `refactoring/petRecommendation/pet-recommendation-nlp-traffic-policy-2026-05-31.md` — Location 검색 "호출정책 부족" vs 게시글/케어 작성 "버스트 백로그" 문제 구분, bounded executor·TTL dedup 설계.
- `refactoring/petRecommendation/nlp-traffic-policy-impl-2026-05-31.md` — 위 설계 구현: bounded ThreadPoolTaskExecutor + Redis TTL dedup 필터, 테스트 32/32 PASS.
- `refactoring/petRecommendation/pet-recommendation-refactoring-2026-05-31.md` — 2차 코드리뷰 R1~R9 중 7건 완료·1건 미적용·1건 문서화만.
- `refactoring/petRecommendation/ai-intent-analysis-transition-2026-06-10.md` — 하드코딩 룰→AI/LLM 전환 검토, 안전가드 유지+fallback 유지 Phase1~5 MVP안(구현 전 설계).
- `refactoring/petRecommendation/intent-action-routing-proposal-2026-06-09.md` — intentDomain을 targetAction(care/meetup/missingPet)로 확장하는 안, Phase0~4 설계.
- `refactoring/petRecommendation/signal-to-user-gap-two-problems-2026-06-10.md` — signal 저장돼도 전달 안 되는 문제 발견, SSE 알림 MVP 제안(구현 대기).

### querydsl/

- `refactoring/querydsl/00-admin-user-search-querydsl-decision.md` — 관리자 유저 검색 QueryDSL 전환 결정 근거. 성능이득은 modest, 채용공고 대비 목적이 큼.
- `refactoring/querydsl/01-before-after-sql-evidence.md` — before/after SQL 로그 증거. null 파라미터 조건 완전 제거 실증.

### recordType/

- `refactoring/recordType/.DS_Store` — 내용 없음.
- `refactoring/recordType/board/board-search-optimization.md` — LIKE 검색→FULLTEXT 통합, NICKNAME 검색 2쿼리→JOIN 1쿼리.
- `refactoring/recordType/board/dto-record-refactoring.md` — Board DTO record 전환 7개, 나머지는 필드과다로 미전환.
- `refactoring/recordType/chat/dto-record-refactoring.md` — Chat DTO record 전환 2개, 나머지 미전환.
- `refactoring/recordType/meetup/dto-record-refactoring.md` — Meetup DTO record 전환 1개(MeetupParticipantsDTO), MeetupDTO는 미전환.
- `refactoring/recordType/payment/dto-record-refactoring.md` — Payment DTO record 전환 2개, PetCoinTransactionDTO는 미전환.
- `refactoring/recordType/report/dto-record-refactoring.md` — Report DTO record 전환 1개, 나머지는 이미 `@Value`거나 가변이라 미전환.
- `refactoring/recordType/user/dto-record-refactoring.md` — User DTO record 전환 3개, UsersDTO/PetDTO는 필드과다로 미전환.

### statistics/

- `refactoring/statistics/statistics-domain-review-2026-06-28.md` — Critical 4건(DAU 원천오류, 결제-배치 충돌, self-invocation, race) 전부 수정완료.
- `refactoring/statistics/statistics-refactoring-2026-06-28.md` — 위 리뷰의 실제 코드 수정 기록(login_events 신설, REQUIRES_NEW 분리, 비관적락).

### user/

- `refactoring/user/admin-delete-optimization/sequence-diagram.md` — deleteUser() 권한검증 전체조회→role 프로젝션 1쿼리로 축소.
- `refactoring/user/auth-duplicate-query/sequence-diagram.md` — login()/refreshAccessToken() 중복 재조회 제거.
- `refactoring/user/social-users-query/troubleshooting.md` — socialUsers Lazy N+1(101쿼리)→@BatchSize(50)로 3쿼리.
- `refactoring/user/user-backend-performance-optimization.md` — 14개 항목 중 대부분 완료, addWarning 중복조회·인덱스·캐싱만 미적용.

---

## 12. interview/ — 면접 대비 자료

- `interview/면접_기술질문_리스트.md` — 구버전 면접 질문 마스터 목차(최신 concepts/ 문서로 대체된 참고용).
- `interview/.DS_Store` — 내용 없음.
- `interview/ds-algorithm-checklist.md` — 도메인별 실사용 자료구조·알고리즘 우선순위표. petRecommendation만 분석완료, 나머지는 미분석.
- `interview/petory-backend-면접-코테-정리.md` — 실제 면접 질문 12개와 답변+정답 가이드.

### interview/flows/

> `domain-page-drafts/`(포트폴리오 V2 페이지 문구 점검)와 `interview/concepts/`(개념 횡단 정리) 둘 다 채우지 않는 자리 — 포트폴리오 흐름도 페이지(`/domains/flows`) 시퀀스 하나하나를 [코드 근거] + [포트폴리오 대응 위치·딥링크] + [예상질문·답변] 3단으로 도메인별 1파일씩 정리.

- `interview/flows/README.md` — 폴더 목적, 기존 문서와의 역할 구분, 도메인별 진행 현황표.
- `interview/flows/care.md` — Care 거래 확정 흐름(`confirmCareDeal` 비관적 락 → `CareApplication` 승인 → 에스크로 생성). 5개 설계 변천사(원인→해결→결과 형식) 포함 — Stuck State, 스케줄러 트랜잭션 경계, `updateStatus` 이중 락 회귀, 문서-코드 불일치 발견까지.
- `interview/flows/board.md` — Board 목록/상세/댓글/인기글 흐름. 3개 설계 변천사(N+1 301→3쿼리, 상세 캐시가 조회수 버그를 만든 사례, "인덱스 있는데도 안 쓰인" 옵티마이저 히스토그램 사건) + 반응 API의 userId 신뢰 문제 등 알려진 한계 포함.
- `interview/flows/chat.md` — Chat(Care/MissingPet/Meetup 연계) 채팅방 생성·메시지·읽음 흐름. 4개 설계 변천사(IDOR 수정, self-invocation으로 REQUIRES_NEW 무시되던 버그, 읽음 처리가 죽은 코드였던 사례, N+1 41→4쿼리 + 측정도구 자체의 버그 발견) 포함.
- `interview/flows/missing-pet.md` — Missing Pet 제보·목격 댓글·채팅 연결 흐름. N+1(267→4쿼리) + `orphanRemoval=true`가 소프트 삭제 정책과 충돌하던 사례 + Pageable 정렬이 하드코딩된 JPQL에 조용히 무시되는 한계 포함.
- `interview/flows/meetup.md` — Meetup 모임 생성·참가·채팅방 흐름. 3개 설계 변천사(참가 정원 동시성 — 진단이 두 번 뒤집힌 사례: 정원초과 예상→실제론 데드락→3중 방어로 정착, 참가취소 Lost Update, 채팅방 생성 3단계 안전망) 포함.
- `interview/flows/location.md` — Location 주변서비스 검색·리뷰·평점 흐름. 4개 설계 변천사(무제한 전체조회→반경조회, DB시간보다 HTTP시간 격차가 컸던 이유, 카테고리 필터 Java메모리→SQL 통합, 메서드명-실제동작 불일치, saveBatch self-invocation — Chat과 동일 패턴) 포함.
- `interview/flows/recommendation.md` — Recommendation Signal 수집·NLP 분석·추천 카드 흐름. 2개 설계 변천사(이벤트 리스너가 트랜잭션 커밋 전에 실행되던 문제, petType 422 무음 드롭 — 장애격리 원칙 자체가 만든 침묵 버그) 포함.
- `interview/flows/user.md` — User JWT 인증·프로필·제재 흐름. 2개 설계 변천사(제재된 사용자가 기존 토큰으로 계속 접근 가능했던 취약점 A1~A6 + SUSPENDED 신고 예외 정책, 로그인/refresh 중복 조회 제거) 포함. 도메인 8개 전체 완료.

### interview/concepts/

- `interview/concepts/_진행상태.md` — 문서 전수 점검 체크리스트. 01~14 점검 완료, 2차 점검(2026-06-18), 15·16 신규 추가.
- `interview/concepts/00_목차.md` — concepts/ 전체 목차 및 우선순위(★~★★★) 분류.
- `interview/concepts/01_DB_인덱스.md` — B-Tree/FULLTEXT/Spatial/Unique 인덱스 정리.
- `interview/concepts/02_공간쿼리_Haversine.md` — Location/Meetup 2단계 공간인덱스 전략과 MissingPet 바운딩박스 비교.
- `interview/concepts/03_동시성_제어.md` — 원자적 UPDATE/비관적 락/DB 제약 3계층 전략, worktree 실측 사례.
- `interview/concepts/04_JPA_N+1.md` — Fetch Join/@EntityGraph/@BatchSize/2-Query패턴/JPQL Bulk UPDATE 정리.
- `interview/concepts/05_알고리즘_점수설계.md` — 추천/모임/실종신고/게시판 인기도 가중합 점수 설계.
- `interview/concepts/06_실시간통신_SSE_WebSocket.md` — SSE(알림) vs WebSocket/STOMP(채팅) 선택 이유와 구조.
- `interview/concepts/07_이벤트_트랜잭션_배치.md` — afterCommit() 패턴, petRecommendation 이벤트 체인, 통계 배치, 커스텀 AOP.
- `interview/concepts/08_Redis_캐시.md` — Redis 4가지 실사용 용도와 RedisTemplate 구성, boardDetail 캐시 제거 배경.
- `interview/concepts/09_보안_JWT_인증.md` — Access/Refresh JWT 구조, JwtAuthenticationFilter DB 재조회 흐름.
- `interview/concepts/10_PG결제_PetCoin_Escrow.md` — PG 미연동 현황과 PetCoin 에스크로 패턴, 비관적 락 지점.
- `interview/concepts/11_주변서비스_CareRequest.md` — CareRequest 상태전이와 에스크로 연동, FULLTEXT 2-Query 패턴.
- `interview/concepts/12_모임_Meetup.md` — 모임 참가 3중 동시성 방어, afterCommit() 채팅방 생성+복구.
- `interview/concepts/13_NLP_서버_FastAPI.md` — FastAPI NLP 서버 분리 이유, Kiwi+ko-sroberta 하이브리드 규칙기반.
- `interview/concepts/14_CS_기본기.md` — HTTP/TCP/WebSocket vs SSE/CORS/데드락 등을 프로젝트 코드와 연결.
- `interview/concepts/15_Spring_Java.md` — IoC/DI, AOP, self-invocation, GC 등을 프로젝트 코드와 연결.
- `interview/concepts/16_추가질문_일반개념.md` — N+1/동시성/캐시 후속질문 + 프로젝트에 없는 일반개념(CAP, CQRS 등) 비교.
- `interview/concepts/17_도메인별_공부법.md` — 8개 그룹 우선순위별 면접 공부 순서와 도메인 연관 지도.
- `interview/concepts/18_자료구조.md` — Array/Stack/HashMap/Tree/Heap/Graph/Trie를 프로젝트 코드와 연결.
- `interview/concepts/19_프로젝트_자료구조_선택.md` — List/Page/Map/Set/Enum 등 실제 선택 근거.
- `interview/concepts/20_백엔드_면접_학습로드맵.md` — 선행개념 의존관계 기준 학습순서와 2~3주 스퍼트 플랜.
- `interview/concepts/21_백엔드_취업_기본기_체크리스트.md` — 4단계 기준 프로젝트 코드 근거 유무 판정.
- `interview/concepts/23_쿼리_옵티마이저_통계와_실행계획.md` — 게시판 목록 쿼리 "인덱스 있는데도 안 쓰인" 사건 분석, A/B/A 인과증명.

### interview/concepts/db-infra/

- `interview/concepts/db-infra/00_목차.md` — DB/인프라 심화 스터디 세트 목차(7개 문서).
- `interview/concepts/db-infra/01_인덱스_설계_심화.md` — 복합인덱스 컬럼순서, 커버링 인덱스 손익분기점.
- `interview/concepts/db-infra/02_정규화_트랜잭션_동시성.md` — 비정규화 캐시필드 깨지는 경로, REPEATABLE_READ 한계, 데드락 조건.
- `interview/concepts/db-infra/03_커넥션풀_용량설계.md` — HikariCP maximum-pool-size=20 근거, 고갈 시 진단.
- `interview/concepts/db-infra/04_도커_JVM_컨테이너.md` — Dockerfile 멀티스테이지, -XX:MaxRAMPercentage=75, healthcheck 근거.
- `interview/concepts/db-infra/05_네트워크_리버스프록시.md` — nginx API/WebSocket 타임아웃 차등, Rate Limiting 미구현.
- `interview/concepts/db-infra/06_장애시나리오_통합.md` — 4개 장애 시나리오를 문제→원인→해결→트레이드오프로 정리.
- `interview/concepts/db-infra/07_확장성_다음단계.md` — 트래픽 100배 사고실험 병목 우선순위, k6/Read Replica 대응방향.

---

## 13. 자료구조/알고리즘/ — CS 이론 + 도메인 알고리즘

- `자료구조/알고리즘/자료구조-알고리즘-핵심정리.md` — CS 자료구조·알고리즘 복잡도 요약 + Petory 실사용 Java 컬렉션 정리.
- `자료구조/알고리즘/README.md` — CS 이론(`../algorithm/`)과 Petory 비즈니스 알고리즘·도메인 구조 문서 안내.
- `자료구조/알고리즘/algorithm/00-algorithm-overview.md` — Petory "알고리즘" 정의와 도메인별 핵심 알고리즘 목록 인덱스.
- `자료구조/알고리즘/algorithm/board/커뮤니티-알고리즘.md` — 인기 게시글 점수 공식, 스냅샷 4단계 fallback, 배치 집계 흐름.
- `자료구조/알고리즘/algorithm/care/케어-알고리즘.md` — CareRequest 상태전이와 에스크로 지급/환불 연동.
- `자료구조/알고리즘/algorithm/location/위치-알고리즘.md` — Haversine 거리 공식, 지역 계층 검색 우선순위, ST_Distance_Sphere 2단계 검색.
- `자료구조/알고리즘/algorithm/meetup/모임-알고리즘.md` — Haversine 반경 검색, 인원 원자적 증가, 모임 상태전이.
- `자료구조/알고리즘/algorithm/notification/알림-알고리즘.md` — Redis+MySQL 이중 저장, 병합(mergeNotifications)·읽음 처리 규칙.
- `자료구조/알고리즘/algorithm/payment/펫코인-알고리즘.md` — 에스크로 상태전이, 비관적 락 동시성 제어.
- `자료구조/알고리즘/algorithm/statistics/통계-알고리즘.md` — 매일 18:30 배치 Daily Summary 패턴, 집계 항목 9종.
- `자료구조/알고리즘/algorithm/user/제재-알고리즘.md` — 경고 3회 자동제한, 만료 자동해제, 신고 자동제재 매핑.

---

## 14. agent/

- `agent/멀티-에이전트-설계.md` — 관리자 신고보조(Report Assist, 구현완료)와 사용자 주변서비스 추천(설계 단계) 2개 에이전트 설계안(Ollama 로컬 LLM).

---

## 15. md/ — 초기 구현 기록(레거시)

- `md/md파일들 목록.md` — md/ 폴더 자체 인덱스(일부 링크는 현재 존재하지 않아 최신 상태 아님).
- `md/밴된 사용자 콘텐츠 필터링 구현.md` — 삭제/밴/정지 사용자 콘텐츠를 쿼리 레벨(JOIN)로 숨기는 구현.
- `md/게시글 카운트 실시간 업데이트 구현.md` — likeCount/commentCount 즉시 갱신 구현.
- `md/채팅_시스템_구현_차이점.md` — 채팅 설계 문서 대비 실제 구현 차이 표.
- `md/코드흐름가이드.md` — 신규 온보딩용 "3일 안에 흐름 파악" 가이드.
- `md/# 사용자 소프트 삭제 및 관리자 패널 개선.md` — 물리삭제→소프트삭제 전환(2026-07 재설계 후기 메모 포함).
- `md/# 성능 테스트 및 문제 상황 재현 TODO.md` — 10개 항목 실습형 TODO(계획서, 결과 아님).
- `md/# 통계 집계 로직 구현 명세.md` — 초기 통계 스케줄러 명세(`domains/statistics.md`의 login_events 전환 이전 버전).
- `md/# 실시간 알림 시스템 구현 문서.md` — 폴링→SSE 전환 배경과 구현 상세.
- `md/# 서버 사이드 페이징 구현 가이드.md` — Board 클라이언트 전체로드→서버 페이징 전환 가이드.
- `md/# 프론트엔드 게시글 데이터 구조 최적화.md` — 게시글 목록 상태 Array→Map+Array 전환(O(n)→O(1)).
- `md/# 반려동물 기능 문서.md` — Pet/PetVaccination 엔티티, File 연동 이미지 관리, Care/실종제보 연동.

---

## 16. sql/

- `sql/carerequest-schedule-fields.sql` — carerequest 테이블에 schedule_mode·estimated_duration_minutes 컬럼 추가 마이그레이션.

---

## 17. superpowers/ — 계획(plans) / 설계(specs) 짝

> 대부분 날짜+제목이 일치하는 plan↔spec 쌍. spec은 설계, plan은 실행계획(먼저 만들어지는 순서는 케이스마다 다름).

### superpowers/plans/

- `superpowers/.DS_Store` — 내용 없음.
- `superpowers/plans/2026-04-18-admin-domain-redesign.md` — Admin Facade 도입+P0버그 수정+페이징 전환 계획.
- `superpowers/plans/2026-04-18-statistics-redesign.md` — DailyStatistics→Daily/Weekly/Monthly 3단계 재설계 계획.
- `superpowers/plans/2026-04-19-actuator-monitoring.md` — Actuator+Admin UI를 동일앱에 내장하는 계획.
- `superpowers/plans/2026-05-08-mobile-capacitor.md` — React 웹앱을 Capacitor로 감싸 Android 배포하는 계획.
- `superpowers/plans/2026-05-13-homepage-redesign.md` — 홈 첫 리디자인 계획(탭시스템 유지).
- `superpowers/plans/2026-05-13-pages-redesign-responsive.md` — 나머지 페이지 반응형 하이브리드(사이드바/하단탭) 전환 계획.
- `superpowers/plans/2026-05-14-auth-pages-redesign.md` — 로그인/회원가입 split-screen 재설계 계획.
- `superpowers/plans/2026-05-16-homepage-redesign.md` — 5/13 버전을 대체하는 두 번째 홈 리디자인(세로배치 4섹션) 계획.
- `superpowers/plans/2026-05-25-location-file-import.md` — Python 배치 JSON→Spring upsert LocationImportService 신설 계획.
- `superpowers/plans/2026-05-26-location-import-upsert.md` — duplicate-skip→source-aware upsert 전환 계획.
- `superpowers/plans/2026-05-30-phase5-6-pet-intent-signal.md` — 반려생활 의도 signal(Phase5)+장소 행동로그 추천반영(Phase6) 계획.
- `superpowers/plans/2026-06-11-interview-concepts-review.md` — interview/concepts 14개 문서를 코드와 대조·갱신하는 5단계 점검 계획.
- `superpowers/plans/2026-07-09-dormant-account.md` — 휴면계정 배치 전환+본인 재활성화 구현계획.
- `superpowers/plans/2026-07-15-board-deep-page-pagination.md` — board 깊은페이지 author_visible 비정규화+지연조인 완료 계획.

### superpowers/specs/

- `superpowers/specs/2026-04-18-statistics-redesign.md` — 통계 재설계 설계 스펙(승인됨). plans 동일제목과 1:1 짝.
- `superpowers/specs/2026-04-19-actuator-monitoring-design.md` — Actuator+Admin Server 아키텍처 설계(실제 구현은 다르게 갈라짐).
- `superpowers/specs/2026-05-13-homepage-redesign-design.md` — 홈 리디자인 스펙(이후 05-16 버전으로 대체됨).
- `superpowers/specs/2026-05-13-pages-redesign-responsive.md` — 반응형 페이지 스펙. plans 동일제목과 1:1 짝.
- `superpowers/specs/2026-05-14-auth-pages-redesign-design.md` — Auth split-screen 설계 스펙(승인 2026-05-14).
- `superpowers/specs/2026-05-16-homepage-redesign-design.md` — 홈 세로배치 재설계 스펙.
- `superpowers/specs/2026-07-09-dormant-account-design.md` — 휴면계정 설계 스펙(2026-07-09).
- `superpowers/specs/2026-07-15-board-deep-page-pagination-design.md` — board 깊은페이지 설계 확정본(2026-07-15), 타도메인 미적용 사유(§5) 포함.
