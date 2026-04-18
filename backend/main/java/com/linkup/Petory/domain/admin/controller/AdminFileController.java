package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.repository.AttachmentFileRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminFileController {

    private final AttachmentFileRepository fileRepository;
    private final AttachmentFileService attachmentFileService;

    @GetMapping
    public ResponseEntity<Page<FileDTO>> listFiles(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                fileRepository.findAllForAdmin(targetType, q, PageRequest.of(page, size))
                        .map(f -> FileDTO.builder()
                                .idx(f.getIdx())
                                .targetType(f.getTargetType())
                                .targetIdx(f.getTargetIdx())
                                .filePath(f.getFilePath())
                                .fileType(f.getFileType())
                                .createdAt(f.getCreatedAt())
                                .downloadUrl(attachmentFileService.buildDownloadUrl(f.getFilePath()))
                                .build())
        );
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        fileRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

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
}
