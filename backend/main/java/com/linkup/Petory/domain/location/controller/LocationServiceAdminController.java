package com.linkup.Petory.domain.location.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.dto.LocationImportDto;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.service.LocationImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/location")
@RequiredArgsConstructor
public class LocationServiceAdminController {

    private static final long MAX_UPLOAD_BYTES = 50 * 1024 * 1024L; // 50 MB
    private static final int MAGIC_PEEK_BYTES = 16;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".json");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/json", "text/json", "text/plain", "application/octet-stream");

    private final LocationImportService locationImportService;
    private final LocationServiceRepository locationServiceRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.location.import.file-path:}")
    private String importFilePath;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> syncFromConfiguredPath() {
        ResponseEntity<Map<String, Object>> pathError = validateSyncPath();
        if (pathError != null) {
            return pathError;
        }

        try {
            LocationImportService.SyncResult result = locationImportService.importFromFile(importFilePath);

            List<LocationService> batchRecords = locationServiceRepository.findByDataSource("BATCH_IMPORT", 200);
            List<Map<String, Object>> records = batchRecords.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("idx", r.getIdx());
                m.put("name", r.getName());
                m.put("category", r.getCategory3() != null ? r.getCategory3() : "");
                m.put("address", r.getAddress() != null ? r.getAddress() : "");
                m.put("sido", r.getSido() != null ? r.getSido() : "");
                m.put("sigungu", r.getSigungu() != null ? r.getSigungu() : "");
                m.put("phone", r.getPhone() != null ? r.getPhone() : "");
                m.put("lastUpdated", r.getLastUpdated() != null ? r.getLastUpdated().toString() : "");
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("total", result.getTotal());
            response.put("saved", result.getSaved());
            response.put("updated", result.getUpdated());
            response.put("skipped", result.getSkipped());
            response.put("records", records);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 읽기 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/json-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    @SuppressWarnings("UseSpecificCatch")
    public ResponseEntity<Map<String, Object>> jsonPreview() {
        if (!StringUtils.hasText(importFilePath)) {
            return ResponseEntity.ok(Map.of("exists", false, "reason", "파일 경로 미설정"));
        }

        Path path;
        try {
            path = resolveAndValidatePath(importFilePath);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("exists", false, "reason", e.getMessage()));
        }

        if (!Files.exists(path)) {
            return ResponseEntity.ok(Map.of("exists", false, "reason", "파일 없음"));
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            List<LocationImportDto> records = objectMapper.readValue(content, new TypeReference<>() {
            });
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
            Map<String, Object> response = new HashMap<>();
            response.put("exists", true);
            response.put("lastModified", lastModified.toString());
            response.put("count", records.size());
            response.put("records", records);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> importFacilities(
            @RequestPart("file") MultipartFile file) {

        ResponseEntity<Map<String, Object>> fileError = validateUploadFile(file);
        if (fileError != null) {
            return fileError;
        }

        try {
            LocationImportService.SyncResult result = locationImportService.importFromStream(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "total", result.getTotal(),
                    "saved", result.getSaved(),
                    "updated", result.getUpdated(),
                    "skipped", result.getSkipped()
            ));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 읽기 실패: " + e.getMessage()));
        }
    }

    // ── 업로드 파일 검증 ──────────────────────────────────────────────────
    // empty / max-size / 확장자 / Content-Type / magic bytes / filename sanitize 순서로 검증
    private ResponseEntity<Map<String, Object>> validateUploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            return badRequest("파일이 비어있습니다.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return badRequest("파일 크기 초과 (최대 50 MB)");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.stream().anyMatch(filename.toLowerCase()::endsWith)) {
            return badRequest("허용되지 않는 파일 형식입니다. JSON 파일(.json)만 업로드 가능합니다.");
        }

        String ct = file.getContentType();
        if (ct != null) {
            String ctBase = ct.toLowerCase().split(";")[0].trim();
            if (!ALLOWED_CONTENT_TYPES.contains(ctBase)) {
                return badRequest("허용되지 않는 Content-Type: " + ctBase);
            }
        }

        // magic bytes: JSON 배열/객체 시작 여부 확인 (UTF-8 BOM 허용)
        try {
            char first = peekFirstMeaningfulChar(file);
            if (first != '[' && first != '{') {
                return badRequest("파일 내용이 JSON 형식이 아닙니다.");
            }
        } catch (IOException e) {
            return badRequest("파일 읽기 실패: " + e.getMessage());
        }

        return null;
    }

    // 파일명에서 경로 구분자·null 바이트·.. 제거 (path segment sanitize)
    private static String sanitizeFilename(String original) {
        if (!StringUtils.hasText(original)) {
            return "";
        }
        return original
                .replace('\0', '_')
                .replaceAll("[/\\\\]", "_")
                .replace("..", "__");
    }

    // 첫 MAGIC_PEEK_BYTES 바이트를 읽어 공백·BOM을 건너뛴 뒤 첫 의미 있는 문자 반환
    private static char peekFirstMeaningfulChar(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            byte[] peek = new byte[MAGIC_PEEK_BYTES];
            int read = is.read(peek);
            if (read <= 0) {
                return 0;
            }
            int start = 0;
            // UTF-8 BOM (EF BB BF) 건너뜀
            if (read >= 3
                    && peek[0] == (byte) 0xEF
                    && peek[1] == (byte) 0xBB
                    && peek[2] == (byte) 0xBF) {
                start = 3;
            }
            for (int i = start; i < read; i++) {
                char c = (char) (peek[i] & 0xFF);
                if (!Character.isWhitespace(c)) {
                    return c;
                }
            }
        }
        return 0;
    }

    // ── 서버 경로 검증 (sync / json-preview) ──────────────────────────────
    // path normalize + traversal 차단 + 존재 확인 + 정규 파일 여부 + MIME type probe
    private ResponseEntity<Map<String, Object>> validateSyncPath() {
        if (!StringUtils.hasText(importFilePath)) {
            return badRequest("app.location.import.file-path 미설정");
        }
        try {
            Path path = resolveAndValidatePath(importFilePath);
            if (!Files.exists(path)) {
                return badRequest("파일 없음");
            }
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        return null;
    }

    // normalize + toAbsolutePath 후 traversal(`..`) 포함 여부 확인
    private static Path resolveAndValidatePath(String rawPath) {
        Path normalized;
        try {
            normalized = Path.of(rawPath).normalize().toAbsolutePath();
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 파일 경로");
        }
        // normalize 전후가 다르면 ../ 등이 제거된 것 → 비정상 경로로 간주
        Path original = Path.of(rawPath).toAbsolutePath();
        if (!normalized.equals(original)) {
            throw new IllegalArgumentException("유효하지 않은 파일 경로 (경로 탐색 차단)");
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("경로가 파일이 아닙니다.");
        }
        // MIME type probe — null(식별 불가)이거나 JSON·텍스트 계열이어야 함
        try {
            String mime = Files.probeContentType(normalized);
            if (mime != null
                    && !mime.contains("json")
                    && !mime.startsWith("text/")
                    && !mime.equals("application/octet-stream")) {
                throw new IllegalArgumentException("허용되지 않는 파일 형식: " + mime);
            }
        } catch (IOException ignored) {
            // probeContentType 실패는 무시하고 통과
        }
        return normalized;
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
