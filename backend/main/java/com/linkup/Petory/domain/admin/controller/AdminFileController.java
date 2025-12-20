package com.linkup.Petory.domain.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.repository.AttachmentFileRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;

import lombok.RequiredArgsConstructor;

/**
 * 파일 관리 컨트롤러 (관리자용)
 * - ADMIN과 MASTER 모두 접근 가능
 * - 파일 목록 조회, 삭제
 * - 타겟별 파일 조회
 */
@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminFileController {

    private final AttachmentFileRepository attachmentFileRepository;
    private final AttachmentFileService attachmentFileService;

    /**
     * 파일 목록 조회 (필터링 지원)
     */
    @GetMapping
    public ResponseEntity<List<FileDTO>> listFiles(
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetIdx", required = false) Long targetIdx,
            @RequestParam(value = "q", required = false) String q) {
        
        List<com.linkup.Petory.domain.file.entity.AttachmentFile> files;
        
        // 타겟 타입과 ID로 필터링
        if (targetType != null && targetIdx != null) {
            try {
                FileTargetType type = FileTargetType.valueOf(targetType.toUpperCase());
                files = attachmentFileRepository.findByTargetTypeAndTargetIdx(type, targetIdx);
            } catch (IllegalArgumentException e) {
                files = List.of();
            }
        } else {
            // 전체 조회 후 메모리에서 필터링
            files = attachmentFileRepository.findAll();
            if (targetType != null) {
                try {
                    FileTargetType type = FileTargetType.valueOf(targetType.toUpperCase());
                    files = files.stream()
                            .filter(f -> f.getTargetType() == type)
                            .collect(Collectors.toList());
                } catch (IllegalArgumentException e) {
                    files = List.of();
                }
            }
        }
        
        // FileDTO로 변환
        List<FileDTO> fileDTOs = files.stream()
                .map(file -> {
                    FileDTO dto = FileDTO.builder()
                            .idx(file.getIdx())
                            .targetType(file.getTargetType())
                            .targetIdx(file.getTargetIdx())
                            .filePath(file.getFilePath())
                            .fileType(file.getFileType())
                            .createdAt(file.getCreatedAt())
                            .downloadUrl(attachmentFileService.buildDownloadUrl(file.getFilePath()))
                            .build();
                    return dto;
                })
                .collect(Collectors.toList());
        
        // 검색어 필터
        if (q != null && !q.isBlank()) {
            String keyword = q.toLowerCase();
            fileDTOs = fileDTOs.stream()
                    .filter(f -> (f.getFilePath() != null && f.getFilePath().toLowerCase().contains(keyword))
                            || (f.getFileType() != null && f.getFileType().toLowerCase().contains(keyword)))
                    .collect(Collectors.toList());
        }
        
        return ResponseEntity.ok(fileDTOs);
    }

    /**
     * 특정 타겟의 파일 조회
     */
    @GetMapping("/target")
    public ResponseEntity<List<FileDTO>> getFilesByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetIdx) {
        try {
            FileTargetType type = FileTargetType.valueOf(targetType.toUpperCase());
            return ResponseEntity.ok(attachmentFileService.getAttachments(type, targetIdx));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 파일 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        attachmentFileRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 타겟의 모든 파일 삭제
     */
    @DeleteMapping("/target")
    public ResponseEntity<Void> deleteFilesByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetIdx) {
        try {
            FileTargetType type = FileTargetType.valueOf(targetType.toUpperCase());
            attachmentFileService.deleteAll(type, targetIdx);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 파일 통계 조회
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getFileStatistics() {
        long totalFiles = attachmentFileRepository.count();
        
        Map<String, Long> filesByType = attachmentFileRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                    file -> file.getTargetType() != null ? file.getTargetType().name() : "UNKNOWN",
                    Collectors.counting()
                ));
        
        return ResponseEntity.ok(Map.of(
            "totalFiles", totalFiles,
            "filesByType", filesByType
        ));
    }
}

