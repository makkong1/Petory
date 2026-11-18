package com.linkup.Petory.domain.location.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.dto.SeoulPetNewsCrawlResponse;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncSource;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncStatus;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.entity.PetNews;
import com.linkup.Petory.domain.location.entity.PetNews.NewsCategory;
import com.linkup.Petory.domain.location.repository.LocationSyncLogRepository;
import com.linkup.Petory.domain.location.repository.PetNewsRepository;
import com.linkup.Petory.domain.notification.entity.NotificationType;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 크롤러가 긁어온 서울시 보도자료 소식을 {@code pet_news} 에 적재한다.
 *
 * <p>
 * {@link PublicDataSyncService#sync} 를 재사용하지 못한다 — 그 엔진은 {@code locationservice}
 * 전용이다({@code BiConsumer<PublicDataLocationDTO, LocationService>}). 가져온 것은 패턴뿐이다.
 *
 * <h2>갱신하지 않는다</h2>
 * 이미 있는 행은 건드리지 않고 {@code skippedUnchanged} 로 센다. 보도자료는 사후 수정이 드물고,
 * 제목이 바뀌었다고 알림을 다시 보낼 이유가 없다. 그래서 {@code updated} 는 항상 0 이다.
 * <b>이것이 「신규 행에만 알림을 만든다」(Step 5)의 전제다</b> — 재발송이 코드가 아니라 구조로 막힌다.
 *
 * <h2>이력의 skip 사유를 이 소스에 맞게 읽는 법</h2>
 * <ul>
 * <li>{@code skippedInvalid} — 쓸 수 없던 행. 두 가지가 합쳐진다. ① 행 구조가 깨져 파싱 실패
 * ② <b>제목에 반려동물 신호어가 없어 거른 행</b>. 후자가 정상적으로 많다 — 검색(srchText)이 제목이
 * 아니라 본문까지 훑어 무관한 보도자료가 섞여 들어오기 때문이다(실측 20건 중 5건).
 * 둘의 내역은 합쳐 저장하되 <b>실행 로그에는 따로 찍는다</b>. ①이 늘면 사이트 구조가 바뀐 것이다.</li>
 * <li>{@code skippedDuplicate} — 같은 실행 안에서 중복된 번호. 0 이 정상이다.</li>
 * <li>{@code skippedUnchanged} — 이미 적재된 행. <b>높으면 멱등하게 잘 돈 것이다</b>(V22 주석과 같은 뜻).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoulPetNewsSyncService {

    private final SeoulPetNewsClient client;
    private final PetNewsRepository petNewsRepository;
    private final LocationSyncLogRepository syncLogRepository;
    private final UsersRepository usersRepository;
    private final NotificationService notificationService;

    /**
     * 한 실행에서 신규가 이보다 많으면 건별 대신 요약 알림 1건을 보낸다.
     * 최초 전량 적재(22페이지)에서 사용자당 수백 개가 날아가는 것을 막는다.
     */
    // ponytail: 3건 상한. 요약 알림을 눌렀을 때 목록이 필요해지면 그때 조회 API 를 만든다.
    private static final int NOTIFY_ITEM_LIMIT = 3;

    /**
     * 🔴 <b>{@code @Transactional} 을 붙이지 않는다.</b> 이 메서드의 첫 일이 크롤러 HTTP 호출이고,
     * 크롤러는 페이지마다 1초를 쉰다(22 페이지면 25초 이상). 트랜잭션을 걸면 그 시간 내내
     * DB 커넥션을 쥔 채 외부 사이트를 기다리게 된다.
     *
     * <p>
     * 행 단위로 독립이라 묶을 이유도 없다 — {@code save()} 는 각자 트랜잭션으로 돌고,
     * 한 행이 실패해도 나머지는 남으며 다음 실행이 이어받는다.
     */
    public LocationSyncLog sync(SyncTriggerType triggerType, int maxPages) {
        LocalDateTime startedAt = LocalDateTime.now();
        SeoulPetNewsCrawlResponse response = client.fetch(maxPages);

        Set<String> seen = new HashSet<>();
        List<PetNews> insertedRows = new ArrayList<>();
        int inserted = 0;
        int skippedDuplicate = 0;
        int skippedUnchanged = 0;
        int failed = 0;

        for (SeoulPetNewsCrawlResponse.Item item : response.safeItems()) {
            try {
                if (!seen.add(item.sourceNo())) {
                    skippedDuplicate++;
                    continue;
                }
                if (petNewsRepository.existsBySourceAndSourceNo(item.source(), item.sourceNo())) {
                    skippedUnchanged++;
                    continue;
                }
                insertedRows.add(petNewsRepository.save(toEntity(item)));
                inserted++;
            } catch (Exception e) {
                // 한 행이 전체를 무너뜨리지 않게 한다. TourAPI 에서 긴 안내문 1건이 배치 전체를
                // rollback-only 로 만들어 960건을 날린 적이 있다(PetTourSyncService 주석).
                failed++;
                log.warn("소식 적재 실패 sourceNo={} title={} error={}",
                        item.sourceNo(), item.title(), e.getMessage());
            }
        }

        notifyNewRows(insertedRows);

        int skippedInvalid = response.skippedInvalid() + response.skippedUnclassified();
        int skipped = skippedInvalid + skippedDuplicate + skippedUnchanged;

        log.info("서울시 보도자료 동기화: 신규={} 이미있음={} 중복={} 거름(파싱실패={} 반려동물아님={}) 실패={} error={}",
                inserted, skippedUnchanged, skippedDuplicate,
                response.skippedInvalid(), response.skippedUnclassified(), failed, response.error());

        return syncLogRepository.save(LocationSyncLog.builder()
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .status(resolveStatus(response, inserted, failed))
                .source(SyncSource.SEOUL_PET_NEWS)
                .triggerType(triggerType)
                .totalFetched(response.fetched())
                .inserted(inserted)
                .updated(0)
                .skipped(skipped)
                .skippedInvalid(skippedInvalid)
                .skippedDuplicate(skippedDuplicate)
                .skippedUnchanged(skippedUnchanged)
                .failed(failed)
                .errorMessage(response.error())
                .build());
    }

    /**
     * 새로 적재된 소식만 알린다.
     *
     * <p>
     * <b>재발송이 구조로 막힌다</b> — 이미 있던 행은 위에서 {@code skippedUnchanged} 로 빠져
     * 여기까지 오지 않는다. 중복 방지 로직이 따로 없는 이유다.
     *
     * <p>
     * 대상은 자치구를 설정한 사용자다. 현재 소스(서울시 보도자료)는 서울시 전체 단위라
     * 자치구로 좁히지 않고 「설정했다 = 서울 거주」로만 쓴다. 미설정(NULL)에게는 보내지 않는다 —
     * 동의 없이 알림이 가면 안 된다.
     *
     * <p>
     * 알림 생성이 실패해도 적재까지 되돌리지 않는다. 소식은 이미 들어갔고, 다음 실행은
     * 그 행을 신규로 보지 않으므로 <b>그 건의 알림은 영영 안 간다</b>. 소식 1건을 놓치는 쪽이
     * 수집 전체를 롤백하는 것보다 낫다.
     */
    // ponytail: 대상 전체를 메모리로 올리고 1인당 1건씩 만든다. 지금은 서울 사용자가 적어
    //           문제가 없지만, 대상이 수천 명이 되면 일 1회 N건 루프가 된다 — 그때는
    //           사용자별 발송이 아니라 「읽을 때 합성」(소식 목록 + 마지막 확인 시각)으로 바꾼다.
    private void notifyNewRows(List<PetNews> insertedRows) {
        if (insertedRows.isEmpty()) {
            return;
        }
        List<Long> targets = usersRepository.findIdxWithRegionSigungu();
        if (targets.isEmpty()) {
            log.info("소식 알림 대상 없음 — 자치구를 설정한 사용자가 없다. 신규={}", insertedRows.size());
            return;
        }

        // 목록이 최신순으로 오지만 그 순서에 기대지 않는다 — 요약 알림이 가리킬 1건은 날짜로 고른다.
        PetNews latest = insertedRows.stream()
                .max(Comparator.comparing(PetNews::getPublishedAt))
                .orElse(insertedRows.get(0));
        boolean summarize = insertedRows.size() > NOTIFY_ITEM_LIMIT;

        for (Long userIdx : targets) {
            try {
                if (summarize) {
                    notificationService.createNotification(userIdx, NotificationType.PET_NEWS,
                            "새로운 반려동물 소식 " + insertedRows.size() + "건",
                            latest.getTitle(), latest.getIdx(), "PET_NEWS");
                } else {
                    for (PetNews news : insertedRows) {
                        notificationService.createNotification(userIdx, NotificationType.PET_NEWS,
                                "새로운 반려동물 소식", news.getTitle(), news.getIdx(), "PET_NEWS");
                    }
                }
            } catch (Exception e) {
                log.warn("소식 알림 발송 실패 userIdx={} error={}", userIdx, e.getMessage());
            }
        }
        log.info("소식 알림 발송: 대상={}명 신규={}건 방식={}",
                targets.size(), insertedRows.size(), summarize ? "요약 1건" : "건별");
    }

    /**
     * 크롤러가 error 를 돌려줬는데 건진 것도 없으면 FAILED, 일부라도 돌았으면 PARTIAL.
     * 행 적재가 깨진 경우도 PARTIAL 이다 — 다음 실행이 이어받는다.
     */
    private SyncStatus resolveStatus(SeoulPetNewsCrawlResponse response, int inserted, int failed) {
        boolean crawlerFailed = response.error() != null;
        if (crawlerFailed && inserted == 0) {
            return SyncStatus.FAILED;
        }
        if (crawlerFailed || failed > 0) {
            return SyncStatus.PARTIAL;
        }
        return SyncStatus.SUCCESS;
    }

    private PetNews toEntity(SeoulPetNewsCrawlResponse.Item item) {
        return PetNews.builder()
                .source(item.source())
                .sourceNo(item.sourceNo())
                .title(item.title())
                .dept(item.dept())
                .category(NewsCategory.valueOf(item.category()))
                .publishedAt(item.publishedAt())
                .sourceUrl(item.sourceUrl())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
