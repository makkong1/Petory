# Step 2: V25 스키마 + `pet_news` 엔티티 + `users` 자치구 컬럼

## 목표
수집 결과를 담을 테이블과 엔티티를 만든다. **화면은 만들지 않는다**(게시판이 아니다 — 사용자는 알림·배너로만 본다).

## 배경 / 가드레일 (CLAUDE.md)
- 스키마 정본은 `backend/main/resources/db/migration/V*.sql` 뿐이다. **적용된 V1~V24 는 절대 수정 금지** (Flyway 체크섬).
- 현재 최신은 **V24** (`V24__pet_tour_place_fields.sql`). 새 파일은 **V25**.
- `ddl-auto=validate` 라 엔티티와 스키마가 어긋나면 **앱이 기동에 실패한다**. 둘을 같이 고친다.
- Step 1 결론: 본문은 저장하지 않는다. 제목·부서·등록일·원문 링크·분류만 저장한다.

## 변경 파일

### 1. `backend/main/resources/db/migration/V25__pet_news.sql` (신규)

```sql
-- 서울시 보도자료 「반려동물」 소식. 본문은 저장하지 않고 원문 링크로 보낸다.
CREATE TABLE pet_news (
    idx            BIGINT       NOT NULL AUTO_INCREMENT,
    source         VARCHAR(30)  NOT NULL COMMENT '수집 출처. 현재 SEOUL_PRESS 뿐',
    source_no      VARCHAR(20)  NOT NULL COMMENT '원문 게시물 번호(upperNo). 멱등 키',
    title          VARCHAR(500) NOT NULL,
    dept           VARCHAR(100)          COMMENT '담당부서. 예: 동물보호과',
    category       VARCHAR(20)  NOT NULL COMMENT 'EVENT/EDUCATION/SAFETY/SUPPORT',
    published_at   DATE         NOT NULL COMMENT '원문 등록일',
    source_url     VARCHAR(500) NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (idx),
    UNIQUE KEY uk_pet_news_source (source, source_no),
    KEY idx_pet_news_published (published_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 알림 대상 판정용. 보도자료는 서울시 전체 단위라 지금은 「값이 있으면 서울 거주」로만 쓴다.
-- 구청 소스가 열리면 그대로 자치구 매칭에 쓴다.
ALTER TABLE users ADD COLUMN region_sigungu VARCHAR(20) NULL COMMENT '자치구. 예: 중랑구';
```

- `uk_pet_news_source` 가 **멱등의 유일한 근거다**. 이게 없으면 Step 4 의 재실행이 중복을 쌓는다.
- `idx_pet_news_published` 는 최신순 조회용.

### 2. `backend/main/java/com/linkup/Petory/domain/location/entity/PetNews.java` (신규)
- `LocationSyncLog` 와 같은 스타일: `@Entity`, `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder` private 생성자.
- 필드: `idx, source, sourceNo, title, dept, category(enum STRING), publishedAt(LocalDate), sourceUrl, createdAt`
- `enum NewsCategory { EVENT, EDUCATION, SAFETY, SUPPORT }` 를 엔티티 안에 둔다 (`LocationSyncLog.SyncStatus` 전례).

### 3. `backend/main/java/com/linkup/Petory/domain/location/entity/LocationSyncLog.java` (수정)
- `SyncSource` enum 에 `SEOUL_PET_NEWS` 추가. **컬럼이 VARCHAR(30) 이라 마이그레이션 불필요** (V24 에서 nullable 로 추가된 그대로).

### 4. `backend/main/java/com/linkup/Petory/domain/user/entity/Users.java` (수정)
- `private String regionSigungu;` 추가 (`@Column(name = "region_sigungu", length = 20)`).
- **다른 필드·메서드는 건드리지 않는다.**

## 하지 않을 것
- 화면·컨트롤러·DTO (Step 5 에서 필요한 것만)
- 마감일 컬럼 — 목록에 없고 본문 파싱은 상세 요청이 행마다 1회씩 필요하다. `PetTourSyncService` 주석에 적힌 사고(일일 한도 소진·전량 롤백)와 같은 구조라 **처음부터 넣지 않는다.**

## AC (Acceptance Criteria)

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava

# Flyway 적용 + ddl-auto=validate 통과 확인 (엔티티와 스키마가 어긋나면 여기서 죽는다)
./gradlew bootRun --args='--petory.scheduling.enabled=false'
# 기동 로그에 "Migrating schema `petory` to version 25" 와 정상 기동이 보이면 성공. 확인 후 종료.

# 테이블·인덱스 확인
mysql -u petory_app -p petory -e "SHOW CREATE TABLE pet_news\G SHOW COLUMNS FROM users LIKE 'region_sigungu';"
```
