package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.location.dto.SeoulPetNewsCrawlResponse;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncStatus;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.entity.PetNews;
import com.linkup.Petory.domain.location.entity.PetNews.NewsCategory;
import com.linkup.Petory.domain.location.repository.PetNewsRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 서울시 보도자료 수집의 <b>멱등</b>을 지킨다.
 *
 * <p>
 * 이 기능은 「새로 적재된 행에만 알림을 만든다」를 전제로 재발송을 막는다. 멱등이 깨지면
 * 같은 소식이 매일 다시 적재되고, 사용자에게 매일 같은 알림이 간다. 그래서 두 축을 다 본다.
 *
 * <ol>
 * <li>서비스가 이미 있는 행을 {@code skippedUnchanged} 로 거르는가</li>
 * <li><b>DB 제약이 실제로 있는가</b> — 1번만 보면 UNIQUE 를 지워도 테스트가 통과한다(false green).
 * V25 의 {@code uk_pet_news_source} 를 되돌리면 2번이 깨진다.</li>
 * </ol>
 *
 * 크롤러는 {@link SeoulPetNewsClient} 를 대역으로 바꿔 외부 사이트를 치지 않는다 —
 * 서울시 응답에 따라 결과가 달라지면 이 테스트는 멱등을 증명하지 못한다.
 */
@SpringBootTest
@Transactional
class SeoulPetNewsSyncServiceTest {

    private static final String SOURCE = "SEOUL_PRESS";
    private static final String SOURCE_NO = "999999001";

    @Autowired SeoulPetNewsSyncService sut;
    @Autowired PetNewsRepository petNewsRepository;
    @MockitoBean SeoulPetNewsClient client;

    @PersistenceContext EntityManager em;

    private SeoulPetNewsCrawlResponse oneItem() {
        return new SeoulPetNewsCrawlResponse(SOURCE, 10, 0, 2, List.of(
                new SeoulPetNewsCrawlResponse.Item(SOURCE, SOURCE_NO,
                        "(석간) 서울시, 동물행복페스타 개최…500팀 모집", "동물보호과",
                        LocalDate.of(2026, 8, 25),
                        "https://www.seoul.go.kr/news/news_report.do?bbsNo=158&nttNo=" + SOURCE_NO,
                        "EVENT")),
                null);
    }

    @Test
    @DisplayName("같은 소식을 두 번 수집해도 두 번째는 적재하지 않는다")
    void sync_isIdempotent() {
        when(client.fetch(anyInt())).thenReturn(oneItem());

        LocationSyncLog first = sut.sync(SyncTriggerType.MANUAL, 1);
        assertThat(first.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(first.getInserted()).isEqualTo(1);
        assertThat(first.getSkippedUnchanged()).isZero();

        LocationSyncLog second = sut.sync(SyncTriggerType.MANUAL, 1);
        assertThat(second.getInserted()).isZero();
        assertThat(second.getSkippedUnchanged()).isEqualTo(1);

        // 거른 사유의 합계가 skipped 와 맞아야 한다(V22 에서 쪼갠 이유).
        assertThat(second.getSkipped()).isEqualTo(
                second.getSkippedInvalid() + second.getSkippedDuplicate() + second.getSkippedUnchanged());
        // 제목에 반려동물 신호어가 없어 크롤러가 거른 2건이 invalid 에 합쳐진다.
        assertThat(second.getSkippedInvalid()).isEqualTo(2);
    }

    @Test
    @DisplayName("UNIQUE(source, source_no) 제약이 DB 에 실제로 있다")
    void uniqueConstraintExists() {
        petNewsRepository.save(newsWith(SOURCE_NO));
        em.flush();

        // IDENTITY 키라 save() 가 그 자리에서 INSERT 를 날린다 — flush 를 기다리지 않고 여기서 터진다.
        assertThatThrownBy(() -> petNewsRepository.save(newsWith(SOURCE_NO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_pet_news_source");
    }

    @Test
    @DisplayName("크롤러가 실패하면 FAILED 로 남기고 적재하지 않는다")
    void sync_recordsFailure() {
        when(client.fetch(anyInt())).thenReturn(
                new SeoulPetNewsCrawlResponse(null, 0, 0, 0, List.of(), "ConnectionError: refused"));

        LocationSyncLog result = sut.sync(SyncTriggerType.MANUAL, 1);

        assertThat(result.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(result.getInserted()).isZero();
        assertThat(result.getErrorMessage()).contains("ConnectionError");
    }

    private PetNews newsWith(String sourceNo) {
        return PetNews.builder()
                .source(SOURCE).sourceNo(sourceNo)
                .title("테스트 소식").dept("동물보호과")
                .category(NewsCategory.NEWS)
                .publishedAt(LocalDate.of(2026, 1, 1))
                .sourceUrl("https://example.invalid/" + sourceNo)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
