# Petory 프로젝트 문서 목록

Petory 프로젝트의 주요 기능 및 구현 내용을 정리한 문서 모음입니다.

---

## 📚 문서 목록

### 🏗️ 아키텍처 및 설계

1. **[Petory 코드 흐름 빠른 파악 가이드](./CODE_FLOW_GUIDE.md)**
   - 프로젝트 코드 흐름을 빠르게 이해하기 위한 실전 가이드
   - 진입점 파악, 인증 흐름, 도메인별 구조 설명

2. **[데이터베이스 엔티티 매핑 검증](./데이터베이스_엔티티_매핑_검증.md)**
   - 전체 데이터베이스 테이블과 JPA 엔티티 간 매핑 검증
   - 발견된 문제점 및 수정 사항 정리

---

### 👤 사용자 관리

3. **[사용자 소프트 삭제 및 관리자 패널 개선](./USER_SOFT_DELETE_AND_ADMIN_MANAGEMENT.md)**
   - 사용자 물리 삭제 → 소프트 삭제 변경
   - 관리자 패널 사용자 관리 기능 개선

4. **[유저 제한 및 제재 시스템](./USER_SANCTION_SYSTEM.md)**
   - 신고 처리와 연동된 자동 제재 시스템
   - 경고, 이용제한, 영구 차단 기능

5. **[삭제/밴된 사용자 콘텐츠 필터링 구현](./DELETED_USER_CONTENT_FILTERING.md)**
   - 삭제/밴된 사용자 콘텐츠 자동 필터링
   - 쿼리 레벨 필터링 구현

---

### 🐾 기능별 문서

6. **[반려동물 기능 문서](./PET_FEATURE_DOCUMENTATION.md)**
   - 반려동물 정보 등록 및 관리 기능
   - 펫케어 요청, 실종 제보 연동

---

### 🔔 알림 시스템

7. **[알림 시스템 전략 정리](./NOTIFICATION_STRATEGY.md)**
   - 댓글 작성 시 자동 알림 발송
   - Redis를 활용한 실시간 알림 관리

8. **[실시간 알림 시스템 구현 문서](./REALTIME_NOTIFICATION_IMPLEMENTATION.md)**
   - Server-Sent Events (SSE) 기반 실시간 알림
   - 폴링 방식에서 SSE 방식으로 개선

---

### ⚡ 성능 최적화

9. **[Redis 캐싱 전략 정리](./REDIS_CACHE_STRATEGY.md)**
   - 게시글 목록/상세 캐싱
   - 캐시 무효화 전략

10. **[서버 사이드 페이징 구현 가이드](./SERVER_SIDE_PAGING.md)**
    - 클라이언트 사이드 → 서버 사이드 페이징 전환
    - 초기 로딩 속도 개선

11. **[게시글 카운트 실시간 업데이트 구현](./BOARD_COUNT_REALTIME_UPDATE.md)**
    - 조회수, 좋아요 수, 댓글 수 실시간 업데이트
    - 인기 게시글 계산 정확성 향상

12. **[프론트엔드 게시글 데이터 구조 최적화](./FRONTEND_DATA_STRUCTURE_OPTIMIZATION.md)**
    - Array → Map + Array 조합으로 변경
    - O(n) → O(1) 성능 개선

---

### 📊 통계 및 관리

13. **[통계 집계 로직 구현 명세](./STATISTICS_LOGIC.md)**
    - 일일 통계 집계 및 DAU 추적
    - 배치 처리 및 실시간 보정 전략

14. **[관리자 통계 시스템 전략](./ADMIN_STATISTICS_STRATEGY.md)**
    - 관리자 대시보드용 통계 시스템
    - 일별 요약 데이터 집계 전략

---

### 🧪 테스트 및 개선

15. **[성능 테스트 및 문제 상황 재현 TODO](./PERFORMANCE_TESTING_TODO.md)**
    - 대량 데이터 생성, 동시성 테스트
    - 트랜잭션, 인덱스, Redis 테스트 계획

---



