# 정규화 분석

## 1. 정규화 수준 기준

### 1NF (제1정규형)
- 모든 컬럼이 atomic value (원자값)
- 반복 그룹 없음

### 2NF (제2정규형)
- 1NF + 부분 함수 종속 제거
- 비주요 속성이 후보키 전체에 종속

### 3NF (제3정규형)
- 2NF + 이행 종속 제거
- 비주요 속성이 다른 비주요 속성에 종속되지 않음

### BCNF (보이스-코드 정규형)
- 3NF + 모든 결정자가 후보키

---

## 2. 도메인별 정규화 분석

### 2.1 User 도메인

#### users
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ 모든 컬럼 원자값 |
| **2NF** | ✅ 단일 PK(idx), 부분 종속 없음 |
| **3NF** | ✅ 이행 종속 없음 |
| **비정규화** | `pet_coin_balance` — PetCoinTransaction 집계와 동기화 필요 (의도적 비정규화) |

**개선**: `pet_coin_balance`는 실시간 잔액 조회를 위한 반정규화. 트랜잭션과의 일관성 유지가 중요.

#### socialuser
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | `provider_data` (JSON TEXT) — Provider별 원본 데이터. 비구조화 데이터 저장용으로 허용 |

#### user_sanctions
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ `report_idx` — Report 테이블 FK인데 Long으로만 저장. 참조 무결성 제약 없음 |

**개선**: `report_idx`를 `@ManyToOne Report`로 변경 또는 FK 제약 추가 검토.

#### pets
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |

#### pet_vaccinations
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |

---

### 2.2 Board 도메인

#### board
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **의도적 비정규화** |
| **비정규화** | `view_count`, `like_count`, `dislike_count`, `comment_count` — 집계값을 엔티티에 저장 |

**이유**: board_reaction, comment 등 집계 시 N+1/COUNT 쿼리 방지를 위한 성능 최적화.

**위험**: BoardReaction, Comment 추가/삭제 시 Board 엔티티 업데이트 필요. 트랜잭션 일관성 유지 필수.

#### comment
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |

#### board_reaction, comment_reaction
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | (board_idx, user_idx) / (comment_idx, user_idx) UK로 중복 방지 |

#### MissingPetBoard
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ `species`, `breed` — Pet 엔티티와 중복 가능 |
| **비고** | 실종/목격 게시판 특성상 당시 시점 정보 저장. Pet과 분리 유지가 합리적 |

#### board_view_log
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | `board_id`/`user_id` — 엔티티와 DBML 컬럼명 불일치 (board_idx, user_idx) |

#### board_popularity_snapshot
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **의도적 비정규화** |
| **비정규화** | `like_count`, `comment_count`, `view_count` — Board 스냅샷 시점 집계값 |

**이유**: 과거 인기순 조회용. 원본 Board와 독립적으로 저장.

---

### 2.3 Care 도메인

#### carerequest
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |

#### careapplication
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | CareRequest : CareApplication = 1:N. 하나의 요청에 한 명만 ACCEPTED. 비즈니스 규칙으로 처리 |

#### carereview
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | CareApplication : CareReview = 1:1. UK(care_application_idx) 권장 |

#### carerequest_comment
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | BaseTimeEntity 미상속, `createdAt` 직접 관리. 일관성 위해 BaseTimeEntity 상속 검토 |

---

### 2.4 Meetup 도메인

#### meetup
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **의도적 비정규화** |
| **비정규화** | `currentParticipants` — MeetupParticipants COUNT와 동기화 필요 |

**이유**: 참여자 수 조회 시 N+1/COUNT 방지.

#### meetupparticipants
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ (복합 PK) |
| **3NF** | ✅ |

---

### 2.5 Chat 도메인

#### conversation
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **의도적 비정규화** |
| **비정규화** | `last_message_at`, `last_message_preview` — ChatMessage에서 파생 |

**이유**: 대화 목록 조회 시 최신 메시지 정보를 매번 JOIN하지 않기 위함.

**위험**: 메시지 추가/삭제 시 Conversation 업데이트 필요.

#### conversationparticipant
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | `lastReadMessage` — ChatMessage FK. 읽음 위치 추적 |

#### chatmessage
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | `replyToMessage` — self-reference |

---

### 2.6 Payment 도메인

#### pet_coin_transaction
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | `balance_before`, `balance_after` — 감사용. Users.pet_coin_balance와 동기화 |

#### pet_coin_escrow
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | CareRequest : PetCoinEscrow = 1:1. UK(care_request_idx) |

---

### 2.7 Report 도메인

#### report
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **다형적 참조** |
| **비고** | `target_type` + `target_idx` — Board, Comment, MissingPetBoard 등 다형적 참조 |

**정규화 관점**: target_type별로 별도 테이블이면 3NF에 더 가깝지만, 신고 대상 타입이 늘어날 때마다 스키마 변경이 필요. 현재 구조는 실용적 타협.

---

### 2.8 Notification 도메인

#### notifications
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **다형적 참조** |
| **비고** | `related_type` (String), `related_id` — Board, Comment 등 다형적 참조 |

**개선**: `related_type`을 Enum으로 관리하여 일관성 확보. FK는 어렵지만 타입 제한은 가능.

---

### 2.9 File 도메인

#### file (AttachmentFile)
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **다형적 참조** |
| **비고** | `target_type` + `target_idx` — Board, Comment, CareRequest, MissingPetBoard, Pet 등 |

**정규화 관점**: target_type별로 별도 테이블이면 3NF에 가깝지만, 첨부 파일 관리의 통합성과 확장성을 위해 현재 구조 유지가 합리적.

---

### 2.10 Location 도메인

#### locationservice
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ `category1`, `category2`, `category3` — 계층 구조. 별도 Category 테이블로 분리 가능 |
| **비고** | 공공데이터 연동. 카테고리 구조가 외부 스키마에 종속됨 |

**개선**: 카테고리 분리가 필요하면 `category` 테이블 도입 후 FK로 연결. 현재는 공공데이터 호환성 우선.

#### locationservicereview
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ✅ |
| **비고** | LocationService.rating — LocationServiceReview 집계와 동기화 필요 (의도적 비정규화) |

---

### 2.11 Statistics 도메인

#### dailystatistics
| 항목 | 분석 |
|------|------|
| **1NF** | ✅ |
| **2NF** | ✅ |
| **3NF** | ⚠️ **의도적 비정규화** |
| **비정규화** | `new_users`, `new_posts`, `completed_cares` 등 — 집계값을 컬럼으로 저장 |

**이유**: 일별 통계 대시보드. 원본 테이블에서 매번 집계하는 것보다 스냅샷 조회가 효율적.

---

## 3. 정규화 요약

### 3.1 의도적 비정규화 (성능 최적화)

| 엔티티 | 비정규화 필드 | 목적 |
|--------|---------------|------|
| Board | view_count, like_count, dislike_count, comment_count | 집계 쿼리 최소화 |
| Meetup | currentParticipants | 참여자 수 조회 최적화 |
| Conversation | last_message_at, last_message_preview | 대화 목록 조회 시 JOIN 제거 |
| BoardPopularitySnapshot | like_count, comment_count, view_count | 과거 인기순 스냅샷 |
| DailyStatistics | new_users, new_posts 등 | 일별 통계 스냅샷 |
| Users | pet_coin_balance | 잔액 조회 최적화 |
| LocationService | rating | 평균 평점 캐싱 |

### 3.2 다형적 참조 (Polymorphic)

| 엔티티 | 컬럼 | 타입 | 비고 |
|--------|------|------|------|
| Report | target_type, target_idx | ReportTargetType, Long | Board, Comment, MissingPetBoard 등 |
| Notification | related_type, related_id | String, Long | BOARD, CARE_REQUEST 등 |
| File (AttachmentFile) | target_type, target_idx | FileTargetType, Long | BOARD, COMMENT 등 |

**정규화 대안**: target_type별 별도 테이블 (report_board, report_comment 등) — 스키마 복잡도 증가. 현재 구조 유지 권장.

### 3.3 개선 권장사항

| 항목 | 우선순위 | 설명 |
|------|----------|------|
| UserSanction.report_idx | 중 | Report FK 또는 제거 검토 |
| BoardViewLog 컬럼명 | 중 | board_id/user_id vs board_idx/user_idx 일관화 |
| CareRequestComment | 낮 | BaseTimeEntity 상속으로 일관성 |
| Notification.relatedType | 낮 | Enum 타입으로 제한 |
| LocationService 카테고리 | 낮 | 별도 Category 테이블 (선택) |

---

## 4. 정규화 vs 성능 트레이드오프

| 패턴 | 정규화 | 비정규화 | Petory 선택 |
|------|--------|----------|-------------|
| 집계값 | 매번 COUNT/SUM | 엔티티에 저장 | 비정규화 (Board, Meetup 등) |
| 최신 메시지 | JOIN ChatMessage | Conversation에 캐시 | 비정규화 (Conversation) |
| 다형적 참조 | target별 테이블 | target_type + target_idx | 다형적 참조 유지 |
| 잔액/평점 | 트랜잭션에서 계산 | 엔티티에 저장 | 비정규화 (Users, LocationService) |

**결론**: Petory는 대부분 3NF를 준수하되, 조회 성능을 위해 **선택적 비정규화**를 적용한 구조로 설계되어 있음. 집계/캐시 필드의 동기화 로직이 비즈니스 레이어에 명확히 구현되어 있는지 확인 필요.
