# 도메인 엔티티 스키마 분석 개요

> Petory 프로젝트의 JPA 엔티티를 기반으로 **인덱스**와 **정규화** 관점에서 분석한 문서입니다.

## 문서 구성

| 문서 | 설명 |
|------|------|
| [01-index-analysis.md](./01-index-analysis.md) | 테이블별 인덱스 현황, 권장 인덱스, 쿼리 패턴 분석 |
| [02-normalization-analysis.md](./02-normalization-analysis.md) | 정규화 수준, 중복/비정규화, 개선 권장사항 |
| [03-n-plus-one-strategy.md](./03-n-plus-one-strategy.md) | N+1 해결 전략 (Fetch Join vs Batch Size), 도메인별 적용 |
| [04-transaction-concurrency.md](./04-transaction-concurrency.md) | 트랜잭션 관리 & 동시성 제어 (락, 원자적 UPDATE, DB 제약) |

## 엔티티 목록 (28개)

| 도메인 | 엔티티 | 테이블명 |
|--------|--------|----------|
| **User** | Users, SocialUser, UserSanction, Pet, PetVaccination | users, socialuser, user_sanctions, pets, pet_vaccinations |
| **Board** | Board, Comment, BoardReaction, CommentReaction, MissingPetBoard, MissingPetComment, BoardViewLog, BoardPopularitySnapshot | board, comment, board_reaction, comment_reaction, MissingPetBoard, MissingPetComment, board_view_log, board_popularity_snapshot |
| **Care** | CareRequest, CareApplication, CareReview, CareRequestComment | carerequest, careapplication, carereview, carerequest_comment |
| **Meetup** | Meetup, MeetupParticipants | meetup, meetupparticipants |
| **Chat** | Conversation, ConversationParticipant, ChatMessage | conversation, conversationparticipant, chatmessage |
| **Payment** | PetCoinTransaction, PetCoinEscrow | pet_coin_transaction, pet_coin_escrow |
| **Report** | Report | report |
| **Notification** | Notification | notifications |
| **File** | AttachmentFile | file |
| **Location** | LocationService, LocationServiceReview | locationservice, locationservicereview |
| **Statistics** | DailyStatistics | dailystatistics |

## 핵심 요약

### 인덱스
- **Unique 제약**: SocialUser(provider+providerId), board_reaction(board+user), comment_reaction(comment+user), board_view_log(board+user), report(target_type+target_idx+reporter_idx), MeetupParticipants(meetup+user PK)
- **권장 추가**: Board, Comment, CareRequest, Notification, PetCoinTransaction 등 FK/조회 패턴 기반 인덱스
- **주의**: BoardViewLog 엔티티의 `board_id`/`user_id` vs DBML `board_idx`/`user_idx` 컬럼명 불일치

### 정규화
- **3NF 수준**: 대부분의 엔티티가 3NF 준수
- **의도적 비정규화**: Board(view_count, like_count, dislike_count, comment_count), Meetup(currentParticipants) — 성능을 위한 집계 필드
- **개선 여지**: Notification.relatedType 문자열, File.target_type+target_idx 다형적 참조

### N+1 & 동시성
- **N+1**: Fetch Join(단건/단일 컬렉션) + Batch Size(페이징/다중 컬렉션)
- **동시성**: DB Unique 제약, 원자적 UPDATE, 비관적 락(거래 확정)
