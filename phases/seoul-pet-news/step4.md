# Step 4: Java 적재 — 멱등 upsert + `LocationSyncLog` + 관리자 엔드포인트 + 스케줄러

## 목표
Python 크롤러를 호출해 받은 JSON 을 `pet_news` 에 **멱등하게** 적재하고, 실행 이력을 `location_sync_log` 에 남긴다.

## 배경 / 기존 자산
- `PublicDataSyncService.sync(...)` 는 **`locationservice` 전용**이다(`BiConsumer<PublicDataLocationDTO, LocationService>`). 그대로 못 쓴다. **패턴만 복사한다.**
- `LocationSyncLog` 는 컬럼이 일반적이라 **그대로 재사용한다**. `SyncSource.SEOUL_PET_NEWS` 는 Step 2 에서 추가됨.
  - `skipped_invalid` = Python 이 버린 행 + 파싱 실패
  - `skipped_unchanged` = 이미 있던 행 (**멱등하게 정상 동작했다는 신호**, V22 주석 참고)
  - `skipped_duplicate` = 같은 실행 안에서 중복된 `sourceNo`
- 스케줄러 cron 은 **property 로 뺀다** (`PublicDataSyncScheduler` 전례). 기존 슬롯 18:10·18:30·19:10·자정·정각을 피해 **20:10** 을 쓴다.
- 스케줄링 on/off 는 `SchedulingConfig(petory.scheduling.enabled)` 가 중앙 제어한다. 새 플래그를 만들지 않는다.

## 변경 파일

### 1. `.../domain/location/repository/PetNewsRepository.java` + `SpringDataJpaPetNewsRepository.java` (신규)
- 기존 `LocationSyncLogRepository` 와 같은 어댑터 구조를 따른다.
- 필요한 메서드는 둘뿐: `existsBySourceAndSourceNo(...)`, `save(...)`, 그리고 Step 5 용 `findTop5ByOrderByPublishedAtDesc()`.

### 2. `.../domain/location/service/SeoulPetNewsClient.java` (신규)
- Spring `RestClient` (프로젝트 기존 방식, `PetTourApiClient` 참고).
- base url 은 property: `petory.nlp.base-url` (이미 있으면 재사용, 없으면 추가).
- `GET /crawl/seoul-pet-news?max_pages={n}` → record `PetNewsItem(String sourceNo, String title, String dept, LocalDate publishedAt, String category, String sourceUrl)` 리스트로 역직렬화.
- 타임아웃 넉넉히(크롤러가 페이지당 1초 쉰다 — 22페이지면 25초 이상). **기본 호출은 1페이지**라 짧다.

### 3. `.../domain/location/service/SeoulPetNewsSyncService.java` (신규)
```
sync(SyncTriggerType trigger, int maxPages):
  started = now
  resp = client.fetch(maxPages)            // 실패해도 예외를 던지지 않는다
  for item in resp.items:
      if seenInThisRun(sourceNo): skippedDuplicate++ ; continue
      if exists(SEOUL_PRESS, sourceNo):    skippedUnchanged++ ; continue
      save(PetNews)                        ; inserted++
  log = LocationSyncLog.builder()... source(SEOUL_PET_NEWS) ...
```
- **갱신(update)은 하지 않는다.** 보도자료는 사후 수정이 드물고, 제목이 바뀌어도 알림을 다시 보낼 이유가 없다. `updated` 는 항상 0.
- `status` = 전부 성공이면 SUCCESS / 일부 실패면 PARTIAL / 크롤러가 error 를 돌려주면 FAILED.
- 신규 적재된 행 목록을 반환한다 → Step 5 의 알림 발송이 이걸 받는다.

### 4. `.../domain/location/controller/LocationServiceAdminController.java` (수정)
- `POST /api/admin/locations/sync-pet-news?maxPages=1` 추가. **기존 sync 엔드포인트와 같은 권한 애노테이션**을 그대로 쓴다(ADMIN/MASTER).
- 최초 전량 적재는 `maxPages=22` 로 한 번 수동 실행한다.

### 5. `.../domain/location/service/PublicDataSyncScheduler.java` (수정)
```java
@Scheduled(cron = "${petory.location.pet-news-sync-cron:0 10 20 * * *}")
public void runDailyPetNewsSync() { ... try/catch 로 감싼다 ... }
```
- 매일 1페이지(10건)만 본다. 신규는 어차피 맨 앞에 쌓인다.
- 기존 두 메서드와 같은 로그 포맷을 쓴다.

## 하지 않을 것
- 재시도·백오프 (`spring-retry` 가 있지만 하루 1회 작업이라 다음 날 실행이 곧 재시도다)
- 상세 페이지 조회
- `Page.map` 사용 금지 (CLAUDE.md) — 이번 단계엔 목록 변환 자체가 없다

## AC (Acceptance Criteria)

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava

# nlp-server 를 띄운 상태에서, 스케줄러는 끄고 기동
./gradlew bootRun --args='--petory.scheduling.enabled=false'

# 1) 수동 1회 — inserted > 0, 로그 1건
curl -X POST 'http://localhost:8080/api/admin/locations/sync-pet-news?maxPages=1' -H "Authorization: Bearer $MASTER_TOKEN"

# 2) ⭐ 멱등 — 곧바로 한 번 더. inserted=0, skipped_unchanged=n 이어야 한다. 아니면 UNIQUE 설계가 틀린 것이다
curl -X POST 'http://localhost:8080/api/admin/locations/sync-pet-news?maxPages=1' -H "Authorization: Bearer $MASTER_TOKEN"

# 3) 이력 확인
mysql -u petory_app -p petory -e \
 "SELECT source,status,total_fetched,inserted,skipped,skipped_unchanged,failed,started_at \
  FROM location_sync_log WHERE source='SEOUL_PET_NEWS' ORDER BY idx DESC LIMIT 2;"

# 4) nlp-server 를 내린 상태로 호출 → 앱이 죽지 않고 status=FAILED 로그가 남아야 한다
```
