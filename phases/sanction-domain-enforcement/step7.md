# Step 7 — 문서 현행화

## 목적

Step 1~6 구현 완료 후, 실제 변경된 코드를 기준으로 관련 도메인 문서를 현행화한다.

**전제**: Step 1~6이 모두 완료된 상태.

## 갱신 대상 문서

### 1. `docs/domains/care.md`
아래 내용을 반영:
- 제재 사용자 케어 요청 생성 차단 정책
- SUSPENDED 요청자 OPEN 케어: 공개 조회 비노출 (상태 유지)
- BANNED 요청자 OPEN 케어: 이벤트 후 CANCELLED 전환
- IN_PROGRESS 케어 자동 완료 보류 (요청자 제재 시)
- nearby 쿼리 사용자 상태 필터 추가 사실
- Care 댓글/지원 생성 차단

### 2. `docs/domains/meetup.md`
아래 내용을 반영:
- 제재 사용자 모임 생성/참가 차단
- 주최자 제재 시 RECRUITING 모임 CANCELLED (SUSPENDED/BANNED 모두)
- 참가자 제재 시 참가 취소 + 채팅 참여 LEFT
- 정지 해제 후 자동 복구 없음 (새로 참가해야 함)

### 3. `docs/domains/chat.md`
아래 내용을 반영:
- 제재 사용자 메시지 전송 차단 (REST + WebSocket)
- ConversationDTO의 hasSanctionedParticipant 플래그 설명
- confirmCareDeal 제재 차단
- 기존 메시지 유지, 채팅방 닫지 않음

### 4. 원본 작업 목록 문서
`docs/architecture/user/제재 상태 도메인 영향 작업 목록 2026-06-28.md`에 구현 완료 상태 표시:
- 산출 순서 각 항목에 "✅ 완료" 표시 추가

## 문서 작성 기준

- `/docs-sync` 스킬에 따라 코드 사실 먼저 확인 후 문서 작성
- 구현된 내용만 작성. 아직 구현되지 않은 2차 과제는 기존 "후속 결정 또는 2차 과제" 절에 유지

## AC (Acceptance Criteria)

```bash
# 문서 파일이 실제 변경 코드와 일치하는지 확인
# 주요 체크포인트:
# - docs/domains/care.md: 제재 정책 절 존재
# - docs/domains/meetup.md: 주최자/참가자 제재 정책 절 존재
# - docs/domains/chat.md: hasSanctionedParticipant 설명 존재
# - 작업 목록 문서: 완료 항목에 체크 표시
```
