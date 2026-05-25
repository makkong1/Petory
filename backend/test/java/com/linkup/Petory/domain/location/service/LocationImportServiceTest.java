package com.linkup.Petory.domain.location.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationImportServiceTest {

    @Mock  LocationServiceRepository locationServiceRepository;
    @Mock  LocationServiceBatchWriter batchWriter;
    @Spy   ObjectMapper objectMapper;

    @InjectMocks LocationImportService service;

    private ByteArrayInputStream toStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importFromStream_savesValidEntry() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.existsByNameAndAddress("멍멍미용", "서울 강남구")).thenReturn(false);
        when(batchWriter.saveBatch(any(List.class))).thenReturn(1);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(0);
        assertThat(result.getDuplicate()).isEqualTo(0);
    }

    @Test
    void importFromStream_skipsDuplicate() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.existsByNameAndAddress("멍멍미용", "서울 강남구")).thenReturn(true);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getDuplicate()).isEqualTo(1);
        assertThat(result.getSaved()).isEqualTo(0);
    }

    @Test
    void importFromStream_skipsBlankName() throws IOException {
        String json = "[{\"name\":\"\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSkipped()).isEqualTo(1);
    }

    @Test
    void importFromStream_skipsMissingLatLng() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\"}]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSkipped()).isEqualTo(1);
    }

    @Test
    void importFromStream_skipsClosedStatus() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0,\"status\":\"폐업\"}]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSkipped()).isEqualTo(1);
    }

    @Test
    void importFromStream_skipsBlankAddress() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"\",\"lat\":37.5,\"lng\":127.0}]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSkipped()).isEqualTo(1);
    }

    @Test
    void importFromStream_emptyArray_returnsZeros() throws IOException {
        String json = "[]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getSaved()).isEqualTo(0);
    }
}
