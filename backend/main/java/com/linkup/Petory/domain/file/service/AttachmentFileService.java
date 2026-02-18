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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentFileService {

    private final AttachmentFileRepository fileRepository;
    private final FileConverter fileConverter;
    private final FileStorageService fileStorageService;

    public List<FileDTO> getAttachments(FileTargetType targetType, Long targetIdx) {
        if (targetType == null || targetIdx == null) {
            return List.of();
        }
        List<AttachmentFile> files = fileRepository.findByTargetTypeAndTargetIdx(targetType, targetIdx);
        return fileConverter.toDTOList(files).stream()
                .map(this::withDownloadUrl)
                .collect(Collectors.toList());
    }

    /**
     * 여러 타겟의 첨부파일을 한 번에 조회 (배치 조회)
     * 반환값: Map<TargetIdx, List<FileDTO>>
     */
    public java.util.Map<Long, List<FileDTO>> getAttachmentsBatch(FileTargetType targetType, List<Long> targetIndices) {
        if (targetType == null || targetIndices == null || targetIndices.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        
        List<AttachmentFile> files = fileRepository.findByTargetTypeAndTargetIdxIn(targetType, targetIndices);
        
        // AttachmentFile을 targetIdx별로 그룹화한 후 FileDTO로 변환
        return files.stream()
                .collect(Collectors.groupingBy(
                    AttachmentFile::getTargetIdx,
                    Collectors.mapping(
                        file -> withDownloadUrl(fileConverter.toDTO(file)),
                        Collectors.toList()
                    )
                ));
    }

    @Transactional
    public void syncSingleAttachment(FileTargetType targetType, Long targetIdx, String filePath, String fileType) {
        if (targetType == null || targetIdx == null) {
            return;
        }

        fileRepository.deleteByTargetTypeAndTargetIdx(targetType, targetIdx);

        String normalizedPath = normalizeFilePath(filePath);
        if (StringUtils.hasText(normalizedPath)) {
            String resolvedFileType = resolveMimeType(normalizedPath, fileType);
            AttachmentFile attachment = AttachmentFile.builder()
                    .targetType(targetType)
                    .targetIdx(targetIdx)
                    .filePath(normalizedPath)
                    .fileType(resolvedFileType)
                    .build();
            fileRepository.save(attachment);
        }
    }

    @Transactional
    public void deleteAll(FileTargetType targetType, Long targetIdx) {
        if (targetType == null || targetIdx == null) {
            return;
        }
        fileRepository.deleteByTargetTypeAndTargetIdx(targetType, targetIdx);
    }

    public String normalizeFilePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }

        String trimmed = rawPath.trim();
        String decoded = decodeIfNecessary(extractRelativePath(trimmed));
        if (!StringUtils.hasText(decoded)) {
            return null;
        }
        return decoded.replace("\\", "/");
    }

    private String extractRelativePath(String value) {
        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            if (StringUtils.hasText(query)) {
                for (String param : query.split("&")) {
                    int idx = param.indexOf('=');
                    if (idx > -1) {
                        String key = param.substring(0, idx);
                        if ("path".equals(key)) {
                            return param.substring(idx + 1);
                        }
                    }
                }
            }

            String uriPath = uri.getPath();
            if (StringUtils.hasText(uriPath)) {
                String marker = "/uploads/";
                int markerIndex = uriPath.indexOf(marker);
                if (markerIndex >= 0) {
                    return uriPath.substring(markerIndex + marker.length());
                }
                if (uriPath.startsWith("/")) {
                    return uriPath.substring(1);
                }
                return uriPath;
            }
        } catch (IllegalArgumentException ignored) {
            // treat as plain string
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

    public String buildDownloadUrl(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/uploads/file")
                .queryParam("path", relativePath)
                .toUriString();
    }

    /**
     * 첨부파일 목록에서 첫 번째 파일의 다운로드 URL 추출
     * [리팩토링] BoardService, CommentService, MissingPetBoardService, MissingPetCommentService 중복 제거 → 공통화
     * @param attachments 첨부파일 목록 (null/empty 허용)
     * @return downloadUrl이 있으면 반환, 없으면 filePath로 buildDownloadUrl 생성
     */
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
