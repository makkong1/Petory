# 인덱스 분석

## 1. 분석 기준

- **엔티티**: JPA `@Entity` 클래스
- **스키마**: `docs/migration/db/dbml_schema.dbml` 및 마이그레이션 SQL
- **쿼리 패턴**: Repository 메서드, 서비스 레이어 사용 패턴

---

## 2. 도메인별 인덱스 현황

### 2.1 User 도메인

#### users
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| PK | idx | PRIMARY | |
| UK | id | UNIQUE | 로그인 아이디 |
| UK | username | UNIQUE | |
| UK | nickname | UNIQUE | |
| UK | email | UNIQUE | |

**권장 추가 인덱스** (docs 참고):
```sql
CREATE INDEX idx_users_status ON users(status, suspended_until);
CREATE INDEX idx_users_deleted ON users(is_deleted, deleted_at);
CREATE INDEX idx_users_refresh_token ON users(refresh_token);
```

#### socialuser
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | (provider, provider_id) | UNIQUE | OAuth2 로그인 조회 |
| IX | users_idx | INDEX | FK |
| IX | provider | INDEX | |

**엔티티**: `@UniqueConstraint` 없음 → DBML에만 정의. 마이그레이션 확인 필요.

#### user_sanctions
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | user_idx | INDEX | FK, 사용자별 제재 조회 |
| IX | ends_at | INDEX | 만료된 제재 필터링 |

#### pets
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | user_idx | INDEX | FK |
| IX | is_deleted | INDEX | 소프트 삭제 필터 |
| IX | pet_type | INDEX | |
| IX | breed | INDEX | |

#### pet_vaccinations
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | pet_idx | INDEX | FK |
| IX | is_deleted | INDEX | |

---

### 2.2 Board 도메인

#### board
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | (is_deleted, created_at DESC) | INDEX | 전체 목록 최신순 |
| IX | (category, is_deleted, created_at DESC) | INDEX | 카테고리별 |
| IX | (user_idx, is_deleted, created_at DESC) | INDEX | 사용자별 |
| FT | (title, content) | FULLTEXT | ngram 검색 |

**엔티티**: `@Index` 없음. 마이그레이션 SQL로 관리.

#### comment
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | board_idx | INDEX | FK (Hibernate 기본) |
| IX | user_idx | INDEX | FK |

**권장 추가**:
```sql
CREATE INDEX idx_comment_board_deleted_created 
ON comment(board_idx, is_deleted, created_at ASC);
```

#### board_reaction
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | (board_idx, user_idx) | UNIQUE | 엔티티 @UniqueConstraint |

#### comment_reaction
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | (comment_idx, user_idx) | UNIQUE | 엔티티 @UniqueConstraint |

#### MissingPetBoard
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | DBML에 인덱스 미정의 |

**권장 추가** (위치/상태 기반 조회):
```sql
CREATE INDEX idx_missing_pet_board_status ON MissingPetBoard(status);
CREATE INDEX idx_missing_pet_board_user ON MissingPetBoard(user_idx);
CREATE INDEX idx_missing_pet_board_lat_lng ON MissingPetBoard(latitude, longitude);
```

#### MissingPetComment
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | (board_idx, is_deleted) | INDEX | 마이그레이션 SQL 존재 |

#### board_view_log
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | (board_id, user_id) | UNIQUE | 엔티티 @UniqueConstraint |

**주의**: 엔티티는 `board_id`, `user_id` 사용. DB 스키마와 컬럼명 일치 여부 확인 필요. (일부 DB는 `board_idx`, `user_idx` 사용)

#### board_popularity_snapshot
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | (period_type, period_start_date, period_end_date, ranking) | INDEX | |
| IX | (period_type, period_end_date DESC, ranking ASC) | INDEX | 최근 스냅샷 |
| IX | board_id | INDEX | FK |

---

### 2.3 Care 도메인

#### carerequest
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | DBML에 인덱스 미정의 |

**권장 추가**:
```sql
CREATE INDEX idx_carerequest_user_created ON carerequest(user_idx, created_at);
CREATE INDEX idx_carerequest_status ON carerequest(status);
CREATE INDEX idx_carerequest_status_deleted ON carerequest(status, is_deleted);
```

#### careapplication
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | |

**권장 추가**:
```sql
CREATE INDEX idx_careapplication_care_request ON careapplication(care_request_idx);
CREATE INDEX idx_careapplication_provider ON careapplication(provider_idx);
CREATE UNIQUE INDEX uk_careapplication_request_provider 
ON careapplication(care_request_idx, provider_idx);  -- 중복 지원 방지
```

#### carereview
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | |

**권장 추가**:
```sql
CREATE UNIQUE INDEX uk_carereview_application ON carereview(care_application_idx);
CREATE INDEX idx_carereview_reviewee ON carereview(reviewee_idx);
```

#### carerequest_comment
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | |

**권장 추가**:
```sql
CREATE INDEX idx_carerequest_comment_request ON carerequest_comment(care_request_idx, created_at);
```

---

### 2.4 Meetup 도메인

#### meetup
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | status | INDEX | |
| IX | date | INDEX | |
| IX | (latitude, longitude) | INDEX | 위치 기반 조회 |
| IX | (date, status) | INDEX | 복합 |

#### meetupparticipants
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| PK | (meetup_idx, user_idx) | PRIMARY | 복합 PK |

**권장 추가**:
```sql
CREATE INDEX idx_meetupparticipants_user ON meetupparticipants(user_idx);
CREATE INDEX idx_meetupparticipants_meetup_joined ON meetupparticipants(meetup_idx, joined_at);
```

---

### 2.5 Chat 도메인

#### conversation
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | (conversation_type, status, last_message_at) | INDEX | DBML |
| IX | (related_type, related_idx) | INDEX | |
| IX | (is_deleted, deleted_at) | INDEX | |

#### chatmessage
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | (conversation_idx, created_at) | INDEX | 메시지 목록 |
| IX | (sender_idx, created_at) | INDEX | |
| IX | (message_type, created_at) | INDEX | |
| IX | (is_deleted, deleted_at) | INDEX | |
| FT | content | FULLTEXT | 검색 |

#### conversationparticipant
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | (conversation_idx, user_idx) | UNIQUE | DBML |
| IX | (user_idx, status, unread_count) | INDEX | |
| IX | conversation_idx | INDEX | |
| IX | (user_idx, unread_count) | INDEX | |

**엔티티**: `@UniqueConstraint` 없음. DB 스키마에 반영 여부 확인 필요.

---

### 2.6 Payment 도메인

#### pet_coin_transaction
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | DBML에 인덱스 미정의 |

**권장 추가**:
```sql
CREATE INDEX idx_pet_coin_transaction_user_created 
ON pet_coin_transaction(user_idx, created_at DESC);
CREATE INDEX idx_pet_coin_transaction_related 
ON pet_coin_transaction(related_type, related_idx);
```

#### pet_coin_escrow
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | care_request_idx | UNIQUE | @JoinColumn(unique=true) |

**권장 추가**:
```sql
CREATE INDEX idx_pet_coin_escrow_requester ON pet_coin_escrow(requester_idx);
CREATE INDEX idx_pet_coin_escrow_provider ON pet_coin_escrow(provider_idx);
CREATE INDEX idx_pet_coin_escrow_status ON pet_coin_escrow(status);
```

---

### 2.7 Report 도메인

#### report
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | (target_type, target_idx, reporter_idx) | UNIQUE | 엔티티 @UniqueConstraint |

**권장 추가**:
```sql
CREATE INDEX idx_report_target ON report(target_type, target_idx);
CREATE INDEX idx_report_status ON report(status, created_at DESC);
CREATE INDEX idx_report_reporter ON report(reporter_idx, created_at DESC);
```

---

### 2.8 Notification 도메인

#### notifications
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | DBML에 인덱스 미정의 |

**권장 추가**:
```sql
CREATE INDEX idx_notification_user_created ON notifications(user_idx, created_at DESC);
CREATE INDEX idx_notification_user_read ON notifications(user_idx, is_read, created_at DESC);
```

---

### 2.9 File 도메인

#### file
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| (없음) | - | - | |

**권장 추가**:
```sql
CREATE INDEX idx_file_target ON file(target_type, target_idx);
```

---

### 2.10 Location 도메인

#### locationservice
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | (latitude, longitude) | INDEX | docs/migration/db/indexes.sql |
| IX | (rating DESC) | INDEX | |
| IX | (category, rating DESC) | INDEX | |
| FT | (name, description) | FULLTEXT | |
| IX | (name, address) | INDEX | |
| IX | is_deleted | INDEX | 소프트 삭제 |

**참고**: `idx_locationservice_deleted_rating` vs `idx_locationservice_sido_deleted_rating` 선택 이슈 → `docs/migration/db/index/locationservice_deleted_rating_index_analysis.md`

#### locationservicereview
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| IX | (service_idx, created_at) | INDEX | |
| IX | (user_idx, created_at) | INDEX | |
| IX | (service_idx, user_idx) | INDEX | |

---

### 2.11 Statistics 도메인

#### dailystatistics
| 인덱스 | 컬럼 | 유형 | 비고 |
|--------|------|------|------|
| UK | stat_date | UNIQUE | 일별 1건 |

---

## 3. 인덱스 미적용/누락 요약

| 테이블 | 누락 인덱스 | 우선순위 |
|--------|-------------|----------|
| carerequest | user_idx, status, (status, is_deleted) | 높음 |
| careapplication | care_request_idx, provider_idx | 높음 |
| carereview | care_application_idx (UK), reviewee_idx | 중간 |
| carerequest_comment | care_request_idx, created_at | 중간 |
| pet_coin_transaction | user_idx, created_at | 높음 |
| notifications | user_idx, created_at, (user_idx, is_read) | 높음 |
| file | (target_type, target_idx) | 중간 |
| MissingPetBoard | status, user_idx, (latitude, longitude) | 중간 |

---

## 4. 권장 마이그레이션 우선순위

1. **높음**: 사용자별/상태별 조회가 많은 테이블 (carerequest, notifications, pet_coin_transaction)
2. **중간**: FK 기반 조인 및 목록 조회 (careapplication, comment, file)
3. **낮음**: 데이터량이 적거나 조회 빈도가 낮은 테이블
