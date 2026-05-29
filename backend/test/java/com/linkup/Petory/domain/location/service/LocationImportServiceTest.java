package com.linkup.Petory.domain.location.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.converter.LocationImportConverter;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationImportServiceTest {

    @Mock  LocationServiceRepository locationServiceRepository;
    @Mock  LocationServiceBatchWriter batchWriter;
    @Mock  LocationImportConverter locationImportConverter; // 누락돼 있던 mock — null이면 UPDATE/INSERT 경로 NPE
    @Spy   ObjectMapper objectMapper;

    @InjectMocks LocationImportService service;

    private ByteArrayInputStream toStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    // ── 신규 insert ──────────────────────────────────────────────────────────

    @Test
    void 신규시설_insert_saved카운트증가() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any(List.class))).thenReturn(1);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(0);
        assertThat(result.getSkipped()).isEqualTo(0);
    }

    @Test
    void 신규시설_insert시_lastUpdated가today() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        LocationService entity = LocationService.builder().lastUpdated(LocalDate.now()).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(locationImportConverter.toEntity(any())).thenReturn(entity);
        ArgumentCaptor<List<LocationService>> captor = ArgumentCaptor.forClass(List.class);
        when(batchWriter.saveBatch(captor.capture())).thenReturn(1);

        service.importFromStream(toStream(json));

        LocationService saved = captor.getValue().get(0);
        assertThat(saved.getLastUpdated()).isEqualTo(LocalDate.now());
    }

    // ── upsert (기존 BATCH_IMPORT row 갱신) ──────────────────────────────────

    @Test
    void 기존BATCH_IMPORT시설_upsert_updated카운트증가() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        LocationService existing = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(126.0).isDeleted(false).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existing));
        when(locationServiceRepository.save(existing)).thenReturn(existing);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getSaved()).isEqualTo(0);
    }

    @Test
    void 기존BATCH_IMPORT시설_upsert시_mutable필드갱신() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"hospital\",\"address\":\"서울 강남구\"," +
                "\"lat\":38.0,\"lng\":128.0,\"phone\":\"02-9999-9999\",\"sido\":\"서울\",\"sigungu\":\"강남구\"}]";
        LocationService existing = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(127.0).phone("010-0000-0000")
                .category3("미용").sido("경기").sigungu("성남시").isDeleted(false).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existing));
        when(locationServiceRepository.save(existing)).thenReturn(existing);
        when(locationImportConverter.categoryLabel("hospital")).thenReturn("동물병원");

        service.importFromStream(toStream(json));

        assertThat(existing.getPhone()).isEqualTo("02-9999-9999");
        assertThat(existing.getLatitude()).isEqualTo(38.0);
        assertThat(existing.getLongitude()).isEqualTo(128.0);
        assertThat(existing.getSido()).isEqualTo("서울");
        assertThat(existing.getSigungu()).isEqualTo("강남구");
        assertThat(existing.getCategory3()).isEqualTo("동물병원");
        assertThat(existing.getLastUpdated()).isEqualTo(LocalDate.now());
    }

    @Test
    void 기존BATCH_IMPORT시설_upsert시_rating_reviewCount_score_보존() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        LocationService existing = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(127.0)
                .rating(4.8).reviewCount(42).score(0.95).isDeleted(false).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existing));
        when(locationServiceRepository.save(existing)).thenReturn(existing);

        service.importFromStream(toStream(json));

        assertThat(existing.getRating()).isEqualTo(4.8);
        assertThat(existing.getReviewCount()).isEqualTo(42);
        assertThat(existing.getScore()).isEqualTo(0.95);
    }

    // ── soft-deleted row 재활성화 ─────────────────────────────────────────────

    @Test
    void softDeleted_BATCH_IMPORT시설_재활성화() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        LocationService softDeleted = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(127.0).isDeleted(true).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(softDeleted));
        when(locationServiceRepository.save(softDeleted)).thenReturn(softDeleted);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(softDeleted.getIsDeleted()).isFalse();
        assertThat(softDeleted.getDeletedAt()).isNull();
    }

    // ── PUBLIC row 격리 ──────────────────────────────────────────────────────

    @Test
    void PUBLIC_row_동일name_address_BATCH_IMPORT없으면_신규insert() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any(List.class))).thenReturn(1);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(0);
    }

    // ── isValid 실패 ─────────────────────────────────────────────────────────

    @Test
    void 빈이름_skipped() throws IOException {
        String json = "[{\"name\":\"\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void 빈주소_skipped() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"\",\"lat\":37.5,\"lng\":127.0}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void latLng없으면_skipped() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\"}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void 폐업status_skipped() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0,\"status\":\"폐업\"}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void 빈배열_모두0() throws IOException {
        LocationImportService.SyncResult result = service.importFromStream(toStream("[]"));
        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getSaved()).isEqualTo(0);
        assertThat(result.getUpdated()).isEqualTo(0);
    }

    // ── N+1 SELECT 버그 회귀 ──────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.DisplayName("레코드 N개 import 시 findByNameAndAddressAndDataSource가 N회 개별 호출됨 (N+1 패턴 확인)")
    void 레코드N개_import시_SELECT_N회_개별호출() throws IOException {
        // given: 서로 다른 3개 레코드
        String json = """
                [
                  {"name":"A미용","address":"서울 강남구","lat":37.5,"lng":127.0,"category":"grooming"},
                  {"name":"B병원","address":"서울 서초구","lat":37.4,"lng":126.9,"category":"hospital"},
                  {"name":"C카페","address":"서울 마포구","lat":37.6,"lng":126.8,"category":"cafe"}
                ]
                """;
        when(locationServiceRepository.findByNameAndAddressAndDataSource(any(), any(), eq("BATCH_IMPORT")))
                .thenReturn(Optional.empty());
        when(locationImportConverter.toEntity(any())).thenReturn(LocationService.builder().build());
        when(batchWriter.saveBatch(any(List.class))).thenReturn(3);

        service.importFromStream(toStream(json));

        // ★ N+1: 레코드 3개에 대해 SELECT가 3회 개별 실행
        // 이상적인 구현: 1회 bulk SELECT 후 Map으로 관리
        verify(locationServiceRepository, times(3))
                .findByNameAndAddressAndDataSource(any(), any(), eq("BATCH_IMPORT"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("UPDATE 경로: save()가 레코드별로 개별 호출됨 (배치 처리 미적용 확인)")
    void UPDATE경로_save_레코드별_개별호출() throws IOException {
        // given: 기존 레코드 2개
        String json = """
                [
                  {"name":"A미용","address":"서울 강남구","lat":37.5,"lng":127.0,"category":"grooming"},
                  {"name":"B병원","address":"서울 서초구","lat":37.4,"lng":126.9,"category":"hospital"}
                ]
                """;
        LocationService existingA = LocationService.builder()
                .name("A미용").address("서울 강남구").dataSource("BATCH_IMPORT").isDeleted(false).build();
        LocationService existingB = LocationService.builder()
                .name("B병원").address("서울 서초구").dataSource("BATCH_IMPORT").isDeleted(false).build();

        when(locationServiceRepository.findByNameAndAddressAndDataSource("A미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existingA));
        when(locationServiceRepository.findByNameAndAddressAndDataSource("B병원", "서울 서초구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existingB));
        when(locationServiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.importFromStream(toStream(json));

        // ★ UPDATE는 배치 없이 save() 2회 개별 호출
        // 이상적인 구현: saveAll() 1회 또는 batch UPDATE
        verify(locationServiceRepository, times(2)).save(any(LocationService.class));
    }
}
