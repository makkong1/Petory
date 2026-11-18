# Step 5: 알림 + 지도 배너 + 프로필 자치구 입력

## 목표
새로 적재된 소식을 **서울 거주 사용자에게 알림 1건**으로 보내고, 지도 화면 진입 시 **안 읽은 것만 배너로** 한 번 보여준다.

## 배경 / 기존 자산 (새로 만들 게 거의 없다)
- `NotificationType` 은 **DB 가 varchar 라 값 추가에 마이그레이션이 필요 없다** (enum 주석에 명시).
- `Notification` 에 `isRead`·`relatedId`·`relatedType` 이 이미 있다 → 배너 노출 상태를 **새로 만들지 않는다**.
- 알림은 SSE 실시간 푸시 + Redis(최신 50개, TTL 24h)/MySQL 이중 저장 후 병합(중복 제거)이 이미 돈다.
- **게시판은 만들지 않는다.** 배너를 닫아도 **기존 알림 목록 화면**에서 다시 본다. 카드를 누르면 원문(`#view/<번호>`)으로 나간다.
- Step 1 결론: 보도자료는 서울시 전체 단위다 → 대상은 **「`region_sigungu` 가 설정된 사용자」(= 서울 거주)**. 자치구 단위 매칭은 구청 소스가 열릴 때까지 보류.

## 변경 파일

### 1. `.../domain/notification/entity/NotificationType.java` (수정)
- `PET_NEWS` 추가 + 한 줄 주석. **마이그레이션 없음.**

### 2. `.../domain/location/service/SeoulPetNewsSyncService.java` (수정)
- Step 4 가 반환한 **신규 행에 대해서만** 알림을 만든다 (이미 있던 행은 `skipped_unchanged` 라 대상이 아니다 → **재발송이 구조적으로 막힌다**).
- 대상: `region_sigungu IS NOT NULL` 인 사용자.
- `relatedType = "PET_NEWS"`, `relatedId = petNews.idx`.
- 🔴 **한 번에 여러 건이 적재되면 알림을 건마다 보내지 않는다.** 최초 전량 적재(22페이지 = 217건)에서 사용자당 217개가 날아간다.
  → **1회 실행에서 신규가 3건을 넘으면 「새 소식 N건」 요약 알림 1건**만 보낸다. `relatedId` 는 가장 최근 1건.
  → `# ponytail: 3건 상한. 요약 알림을 눌렀을 때 목록이 필요해지면 그때 조회 API 를 만든다`

### 3. `.../domain/user/...` 프로필 자치구 (수정)
- `UsersDTO` 에 `regionSigungu` 추가 → `UsersConverter` 양방향 1줄씩 → `UsersService` 의 기존 프로필 수정 메서드에 `if (dto.getRegionSigungu() != null)` 한 줄 (기존 `location` 처리와 같은 자리, 같은 모양).
- 프론트 프로필 화면에 **서울 25개 자치구 select** 하나. 그 외 지역은 선택지가 없다(현재 소스가 서울뿐이라 솔직하게).

### 4. 프론트 — 지도 배너
- 지도 화면 진입 시 **안 읽은 `PET_NEWS` 알림이 있으면** 상단 배너 1개.
- 닫으면 해당 알림을 읽음 처리(**기존 알림 읽음 API 재사용**). 새 상태·쿠키·localStorage 를 만들지 않는다.
- 게시글 화면에는 띄우지 않는다.

## 하지 않을 것
- 소식 전용 목록 화면/게시판
- 자치구별 필터 (소스가 서울 전체라 의미 없음)
- 중성화 등 반려동물 조건 매칭 — **해당하는 공고가 이 소스에 없다** (Step 1: 지원사업 공고는 구청에 있고 구청은 막혀 있다)

## AC (Acceptance Criteria)

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava && ./gradlew test

# 1) region_sigungu 가 설정된 계정 1개, 안 된 계정 1개를 두고 수동 동기화 실행
curl -X POST 'http://localhost:8080/api/admin/locations/sync-pet-news?maxPages=1' -H "Authorization: Bearer $MASTER_TOKEN"

# 2) ⭐ 설정된 계정에만 알림이 생겼는지
mysql -u petory_app -p petory -e \
 "SELECT u.region_sigungu, n.type, n.title, n.is_read FROM notifications n \
  JOIN users u ON u.idx = n.user_idx WHERE n.type='PET_NEWS' ORDER BY n.idx DESC LIMIT 5;"

# 3) ⭐ 재발송 없음 — 같은 동기화를 한 번 더 돌리고 위 쿼리의 행 수가 그대로인지
# 4) 신규 4건 이상인 실행에서는 알림이 1건(요약)으로 묶였는지
# 5) 지도 진입 → 배너 1회 노출 → 닫기 → 새로고침해도 안 뜨고, 알림 목록에는 남아 있는지
```
