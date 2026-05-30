package com.linkup.Petory.domain.location.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.service.LocationImportService;

/**
 * LocationServiceAdminController 경로 검증 버그 회귀 테스트
 *
 * 버그: resolveAndValidatePath가 Files.isRegularFile 체크를 먼저 실행하므로 미존재 파일에 대해 "파일
 * 없음"이 아닌 "경로가 파일이 아닙니다." 반환 (dead code)
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceAdminControllerPathTest {

    @Mock
    private LocationImportService locationImportService;
    @Mock
    private LocationServiceRepository locationServiceRepository;

    @InjectMocks
    private LocationServiceAdminController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
    }

    @Test
    @DisplayName("미존재 파일 경로: '파일 없음' 대신 '경로가 파일이 아닙니다.' 반환 (dead code 확인)")
    void 미존재파일_에러메시지_파일아님_반환() {
        // given: 존재하지 않는 파일 경로
        String nonExistentPath = tempDir.resolve("not-exist.json").toAbsolutePath().toString();
        ReflectionTestUtils.setField(controller, "importFilePath", nonExistentPath);

        // when
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response
                = (ResponseEntity<Map<String, Object>>) controller.jsonPreview("");

        // then: ★ 버그 — "파일 없음" 분기가 dead code여서 실제로는 이 메시지 반환
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("exists")).isEqualTo(false);

        String reason = (String) response.getBody().get("reason");
        // "파일 없음"이 아닌 "경로가 파일이 아닙니다." 반환 → 운영자 혼란 유발
        assertThat(reason).isEqualTo("경로가 파일이 아닙니다.");
        assertThat(reason).isNotEqualTo("파일 없음"); // dead code 확인
    }

    @Test
    @DisplayName("존재하는 파일: 정상 응답 반환")
    void 존재파일_정상응답() throws IOException {
        // given: 실제 존재하는 JSON 파일
        Path jsonFile = tempDir.resolve("import.json");
        Files.writeString(jsonFile,
                "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0,\"category\":\"grooming\"}]");
        ReflectionTestUtils.setField(controller, "importFilePath", jsonFile.toAbsolutePath().toString());

        // when
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response
                = (ResponseEntity<Map<String, Object>>) controller.jsonPreview("");

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("exists")).isEqualTo(true);
        assertThat(response.getBody().get("count")).isEqualTo(1);
    }

    @Test
    @DisplayName("파일 경로 미설정: exists=false, reason='파일 경로/디렉토리 미설정' 반환")
    void 경로미설정_exists_false() {
        ReflectionTestUtils.setField(controller, "importFilePath", "");

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response
                = (ResponseEntity<Map<String, Object>>) controller.jsonPreview("");

        assertThat(response.getBody().get("exists")).isEqualTo(false);
        assertThat(response.getBody().get("reason")).isEqualTo("파일 경로/디렉토리 미설정");
    }
}
