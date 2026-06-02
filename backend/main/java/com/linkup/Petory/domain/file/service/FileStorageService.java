package com.linkup.Petory.domain.file.service;

import com.linkup.Petory.domain.file.exception.FileNotFoundException;
import com.linkup.Petory.domain.file.exception.FileStorageException;
import com.linkup.Petory.domain.file.exception.FileUploadValidationException;
import com.linkup.Petory.domain.file.exception.FileValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path uploadLocation;

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/jfif");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".jfif");

    public FileStorageService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadLocation);
        } catch (IOException ex) {
            throw FileStorageException.initFailed(ex);
        }
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    /** 이미지 파일을 검증하고 pathSegments 하위 디렉터리에 저장한 뒤 상대경로를 반환한다. */
    public String storeImage(MultipartFile file, String... pathSegments) {
        if (file == null || file.isEmpty()) {
            throw FileValidationException.emptyFile();
        }
        String rawFilename = file.getOriginalFilename();
        String originalFilename = rawFilename != null ? StringUtils.cleanPath(rawFilename) : "image";
        String extension = extractExtension(originalFilename);
        validateFile(file, extension);

        Path targetDirectory;
        try {
            targetDirectory = resolveTargetDirectory(pathSegments);
        } catch (IOException ex) {
            log.error("업로드 경로 디렉터리 생성에 실패했습니다.", ex);
            throw FileStorageException.prepareFailed(ex);
        }
        Path targetLocation = targetDirectory.resolve(generateFileName(extension));
        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return normalizeRelativePath(uploadLocation.relativize(targetLocation));
        } catch (IOException ex) {
            log.error("파일 저장에 실패했습니다. filename={}", originalFilename, ex);
            throw FileStorageException.saveFailed(ex);
        }
    }

    /** 상대경로로 저장된 파일을 스프링 Resource로 로드한다. 파일이 없으면 FileNotFoundException. */
    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = resolveStoragePath(relativePath);
            @SuppressWarnings("null")
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (MalformedURLException ex) {
            log.error("파일 로드에 실패했습니다. path={}", relativePath, ex);
        }
        throw FileNotFoundException.forPath(relativePath);
    }

    /** 상대경로를 절대 저장경로로 변환한다. 경로 순회 공격(../) 시 FileValidationException. */
    public Path resolveStoragePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw FileValidationException.emptyPath();
        }
        Path filePath = uploadLocation.resolve(relativePath).normalize();
        if (!filePath.startsWith(uploadLocation)) {
            throw FileValidationException.invalidPath(relativePath);
        }
        return filePath;
    }

    // -------------------------------------------------------------------------
    // private: 저장 헬퍼
    // -------------------------------------------------------------------------

    /** pathSegments를 sanitize하여 업로드 루트 아래 대상 디렉터리를 생성하고 반환한다. */
    private Path resolveTargetDirectory(String... pathSegments) throws IOException {
        Path directory = uploadLocation;
        if (pathSegments != null) {
            for (String segment : pathSegments) {
                String sanitized = sanitizeSegment(segment);
                if (!sanitized.isBlank()) {
                    directory = directory.resolve(sanitized);
                }
            }
        }
        Files.createDirectories(directory);
        return directory;
    }

    private String sanitizeSegment(String segment) {
        if (segment == null) {
            return "";
        }
        String sanitized = segment.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.contains("..")) {
            sanitized = sanitized.replace("..", "_");
        }
        return sanitized;
    }

    /** 날짜 접두사 + UUID로 고유 파일명을 생성한다. 예: 20260602_abc123.jpg */
    private String generateFileName(String extension) {
        String datePrefix = LocalDate.now().toString().replace("-", "");
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return datePrefix + "_" + randomPart + extension.toLowerCase();
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > -1 ? filename.substring(dotIndex) : "";
    }

    private String normalizeRelativePath(Path relativePath) {
        return relativePath.toString().replace("\\", "/");
    }

    // -------------------------------------------------------------------------
    // private: 검증 헬퍼
    // -------------------------------------------------------------------------

    /** 파일 크기·MIME 타입·확장자·실제 이미지 내용을 순서대로 검증한다. */
    private void validateFile(MultipartFile file, String extension) {
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw FileUploadValidationException.sizeExceeded();
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw FileUploadValidationException.invalidContentType();
        }
        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw FileUploadValidationException.invalidExtension();
        }
        verifyImageContent(file);
    }

    private void verifyImageContent(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ct.equalsIgnoreCase("image/webp")) {
            verifyWebpMagicBytes(file);
            return;
        }
        try (InputStream is = file.getInputStream()) {
            if (ImageIO.read(is) == null) {
                throw FileUploadValidationException.invalidContentType();
            }
        } catch (IOException ex) {
            throw FileUploadValidationException.invalidContentType();
        }
    }

    // ImageIO does not support WebP natively — verify RIFF/WEBP header directly
    private void verifyWebpMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            if (is.read(header) < 12
                    || header[0] != 0x52 || header[1] != 0x49 || header[2] != 0x46 || header[3] != 0x46
                    || header[8] != 0x57 || header[9] != 0x45 || header[10] != 0x42 || header[11] != 0x50) {
                throw FileUploadValidationException.invalidContentType();
            }
        } catch (IOException ex) {
            throw FileUploadValidationException.invalidContentType();
        }
    }
}
