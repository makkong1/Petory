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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentFileService {

    private final AttachmentFileRepository fileRepository;
    private final FileConverter fileConverter;

    public List<FileDTO> getAttachments(FileTargetType targetType, Long targetIdx) {
        if (targetType == null || targetIdx == null) {
            return List.of();
        }
        List<AttachmentFile> files = fileRepository.findByTargetTypeAndTargetIdx(targetType, targetIdx);
        return fileConverter.toDTOList(files);
    }

    @Transactional
    public void syncSingleAttachment(FileTargetType targetType, Long targetIdx, String filePath, String fileType) {
        if (targetType == null || targetIdx == null) {
            return;
        }

        fileRepository.deleteByTargetTypeAndTargetIdx(targetType, targetIdx);

        if (StringUtils.hasText(filePath)) {
            AttachmentFile attachment = AttachmentFile.builder()
                    .targetType(targetType)
                    .targetIdx(targetIdx)
                    .filePath(filePath)
                    .fileType(fileType)
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
}

