package com.linkup.Petory.domain.file.service;

import com.linkup.Petory.domain.file.converter.FileConverter;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.repository.AttachmentFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentFileService {

    private final AttachmentFileRepository fileRepository;
    private final FileConverter fileConverter;
    private final FileStorageService fileStorageService;

    /** 단일 타겟의 첨부파일 목록을 다운로드 URL 포함하여 조회한다. */
    public List<FileDTO> getAttachments(FileTargetType targetType, Long targetIdx) {
        if (targetType == null || targetIdx == null) {
            return List.of();
        }
        return fileRepository.findByTargetTypeAndTargetIdx(targetType, targetIdx).stream()
                .map(file -> withDownloadUrl(fileConverter.toDTO(file)))
                .toList();
    }

    /** 여러 타겟의 첨부파일을 한 번에 조회한다. 반환값은 Map&lt;targetIdx, List&lt;FileDTO&gt;&gt;. */
    public Map<Long, List<FileDTO>> getAttachmentsBatch(FileTargetType targetType, List<Long> targetIndices) {
        if (targetType == null || targetIndices == null || targetIndices.isEmpty()) {
            return Collections.emptyMap();
        }
        return fileRepository.findByTargetTypeAndTargetIdxIn(targetType, targetIndices).stream()
                .collect(Collectors.groupingBy(
                        AttachmentFile::getTargetIdx,
                        Collectors.mapping(
                                file -> withDownloadUrl(fileConverter.toDTO(file)),
                                Collectors.toList()
                        )
                ));
    }

    /** 기존 첨부파일을 모두 삭제하고 새 파일 1개로 교체한다. filePath가 없으면 삭제만 한다. */
    @Transactional
    public void syncSingleAttachment(FileTargetType targetType, Long targetIdx, String filePath, String fileType) {
        if (targetType == null || targetIdx == null) {
            return;
        }
        fileRepository.deleteByTargetTypeAndTargetIdx(targetType, targetIdx);
        String normalizedPath = normalizeFilePath(filePath);
        if (StringUtils.hasText(normalizedPath)) {
            AttachmentFile attachment = AttachmentFile.builder()
                    .targetType(targetType)
                    .targetIdx(targetIdx)
                    .filePath(normalizedPath)
                    .fileType(resolveMimeType(normalizedPath, fileType))
                    .build();
            fileRepository.save(attachment);
        }
    }

    /** 타겟에 속한 첨부파일 레코드를 전부 삭제한다. */
    @Transactional
    public void deleteAll(FileTargetType targetType, Long targetIdx) {
        if (targetType == null || targetIdx == null) {
            return;
        }
        fileRepository.deleteByTargetTypeAndTargetIdx(targetType, targetIdx);
    }

    /** URL·절대경로·상대경로 등 다양한 형태의 입력을 uploads/ 기준 상대경로로 정규화한다. */
    public String normalizeFilePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }
        String decoded = decodeIfNecessary(extractRelativePath(rawPath.trim()));
        if (!StringUtils.hasText(decoded)) {
            return null;
        }
        return decoded.replace("\\", "/");
    }

    /** ?path= 쿼리 파라미터 → /uploads/ 경로 → 절대경로 순으로 상대경로를 추출한다. */
    private String extractRelativePath(String value) {
        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            if (StringUtils.hasText(query)) {
                for (String param : query.split("&")) {
                    int idx = param.indexOf('=');
                    if (idx > -1 && "path".equals(param.substring(0, idx))) {
                        return param.substring(idx + 1);
                    }
                }
            }
            String uriPath = uri.getPath();
            if (StringUtils.hasText(uriPath)) {
                int markerIndex = uriPath.indexOf("/uploads/");
                if (markerIndex >= 0) {
                    return uriPath.substring(markerIndex + "/uploads/".length());
                }
                if (uriPath.startsWith("/")) {
                    return uriPath.substring(1);
                }
                if (uriPath.startsWith("uploads/")) {
                    return uriPath.substring("uploads/".length());
                }
                return uriPath;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return value;
    }

    private String decodeIfNecessary(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    /** providedType이 없으면 실제 파일을 읽어 MIME 타입을 감지한다. 감지 실패 시 null 반환. */
    private String resolveMimeType(String relativePath, String providedType) {
        if (StringUtils.hasText(providedType)) {
            return providedType.toLowerCase();
        }
        try {
            Path storagePath = fileStorageService.resolveStoragePath(relativePath);
            if (Files.exists(storagePath)) {
                String detected = Files.probeContentType(storagePath);
                if (StringUtils.hasText(detected)) {
                    return detected.toLowerCase();
                }
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
        return null;
    }

    private FileDTO withDownloadUrl(FileDTO dto) {
        if (dto == null) {
            return null;
        }
        dto.setDownloadUrl(buildDownloadUrl(dto.getFilePath()));
        return dto;
    }

    /** 상대경로를 /api/uploads/file?path=... 형태의 다운로드 URL로 변환한다. */
    public String buildDownloadUrl(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/uploads/file")
                .queryParam("path", relativePath)
                .toUriString();
    }

    /** 첨부파일 목록에서 첫 번째 파일의 다운로드 URL을 반환한다. 목록이 비어 있으면 null. */
    public String extractPrimaryFileUrl(List<? extends FileDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        FileDTO primary = attachments.get(0);
        if (primary == null) {
            return null;
        }
        if (StringUtils.hasText(primary.getDownloadUrl())) {
            return primary.getDownloadUrl();
        }
        return buildDownloadUrl(primary.getFilePath());
    }
}
