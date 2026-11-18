package com.linkup.Petory.domain.location.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.location.dto.SeoulPetNewsCrawlResponse;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncSource;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncStatus;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.entity.PetNews;
import com.linkup.Petory.domain.location.entity.PetNews.NewsCategory;
import com.linkup.Petory.domain.location.repository.LocationSyncLogRepository;
import com.linkup.Petory.domain.location.repository.PetNewsRepository;

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

    @Transactional
    public LocationSyncLog sync(SyncTriggerType triggerType, int maxPages) {
        LocalDateTime startedAt = LocalDateTime.now();
        SeoulPetNewsCrawlResponse response = client.fetch(maxPages);

        Set<String> seen = new HashSet<>();
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
                petNewsRepository.save(toEntity(item));
                inserted++;
            } catch (Exception e) {
                // 한 행이 전체를 무너뜨리지 않게 한다. TourAPI 에서 긴 안내문 1건이 배치 전체를
                // rollback-only 로 만들어 960건을 날린 적이 있다(PetTourSyncService 주석).
                failed++;
                log.warn("소식 적재 실패 sourceNo={} title={} error={}",
                        item.sourceNo(), item.title(), e.getMessage());
            }
        }

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
