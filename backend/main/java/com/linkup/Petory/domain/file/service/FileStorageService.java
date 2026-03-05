package com.linkup.Petory.domain.file.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.linkup.Petory.domain.file.exception.FileNotFoundException;
import com.linkup.Petory.domain.file.exception.FileStorageException;
import com.linkup.Petory.domain.file.exception.FileUploadValidationException;
import com.linkup.Petory.domain.file.exception.FileValidationException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FileStorageService {

    private final Path uploadLocation;
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".webp",
            ".jfif");

    public FileStorageService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadLocation);
        } catch (IOException ex) {
            throw FileStorageException.initFailed(ex);
        }
    }

    public String storeImage(MultipartFile file, String... pathSegments) {
        if (file == null || file.isEmpty()) {
            throw FileValidationException.emptyFile();
        }

        String rawFilename = file.getOriginalFilename();
        String originalFilename = rawFilename != null
                ? StringUtils.cleanPath(rawFilename)
                : "image";
        String extension = extractExtension(originalFilename);
        validateFile(file, extension);

        Path targetDirectory;
        try {
            targetDirectory = resolveTargetDirectory(pathSegments);
        } catch (IOException ex) {
            log.error("업로드 경로 디렉터리 생성에 실패했습니다.", ex);
            throw FileStorageException.prepareFailed(ex);
        }
        String filename = generateFileName(extension);
        Path targetLocation = targetDirectory.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            Path relativePath = uploadLocation.relativize(targetLocation);
            return normalizeRelativePath(relativePath);
        } catch (IOException ex) {
            log.error("파일 저장에 실패했습니다. filename={}", originalFilename, ex);
            throw FileStorageException.saveFailed(ex);
        }
    }

    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = resolveStoragePath(relativePath);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (MalformedURLException ex) {
            log.error("파일 로드에 실패했습니다. path={}", relativePath, ex);
        }
        throw FileNotFoundException.forPath(relativePath);
    }

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

    private String generateFileName(String extension) {
        String datePrefix = LocalDate.now().toString().replace("-", "");
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return datePrefix + "_" + randomPart + extension.toLowerCase();
    }

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

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > -1) {
            return filename.substring(dotIndex);
        }
        return "";
    }

    private String normalizeRelativePath(Path relativePath) {
        String path = relativePath.toString();
        return path.replace("\\", "/");
    }

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
    }
}
