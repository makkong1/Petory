package com.linkup.Petory.domain.admin.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.repository.AttachmentFileRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFileFacade {

    private final AttachmentFileRepository fileRepository;
    private final AttachmentFileService attachmentFileService;
    private final AdminAuditService auditService;

    public Page<FileDTO> getFiles(String targetType, String keyword, int page, int size) {
        return fileRepository.findAllForAdmin(targetType, keyword, PageRequest.of(page, size))
                .map(f -> FileDTO.builder()
                        .idx(f.getIdx())
                        .targetType(f.getTargetType())
                        .targetIdx(f.getTargetIdx())
                        .filePath(f.getFilePath())
                        .fileType(f.getFileType())
                        .createdAt(f.getCreatedAt())
                        .downloadUrl(attachmentFileService.buildDownloadUrl(f.getFilePath()))
                        .build());
    }

    public List<FileDTO> getFilesByTarget(String targetType, Long targetIdx) {
        FileTargetType type = parseTargetType(targetType);
        return attachmentFileService.getAttachments(type, targetIdx);
    }

    @Transactional
    public void deleteFile(Long id, Long adminIdx) {
        AttachmentFile file = fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 파일입니다."));
        fileRepository.deleteById(id);
        auditService.log(adminIdx, "FILE_DELETE", "FILE", id,
                "targetType=" + file.getTargetType() + ",targetIdx=" + file.getTargetIdx());
    }

    @Transactional
    public void deleteFilesByTarget(String targetType, Long targetIdx, Long adminIdx) {
        FileTargetType type = parseTargetType(targetType);
        attachmentFileService.deleteAll(type, targetIdx);
        auditService.log(adminIdx, "FILE_BULK_DELETE", "FILE", targetIdx,
                "targetType=" + type + ",targetIdx=" + targetIdx);
    }

    private FileTargetType parseTargetType(String targetType) {
        try {
            return FileTargetType.valueOf(targetType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 targetType 입니다.");
        }
    }
}
