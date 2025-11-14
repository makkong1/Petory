package com.linkup.Petory.domain.file.converter;

import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.AttachmentFile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FileConverter {

    public FileDTO toDTO(AttachmentFile file) {
        if (file == null) {
            return null;
        }
        return FileDTO.builder()
                .idx(file.getIdx())
                .targetType(file.getTargetType())
                .targetIdx(file.getTargetIdx())
                .filePath(file.getFilePath())
                .fileType(file.getFileType())
                .createdAt(file.getCreatedAt())
                .build();
    }

    public AttachmentFile toEntity(FileDTO dto) {
        if (dto == null) {
            return null;
        }
        return AttachmentFile.builder()
                .idx(dto.getIdx())
                .targetType(dto.getTargetType())
                .targetIdx(dto.getTargetIdx())
                .filePath(dto.getFilePath())
                .fileType(dto.getFileType())
                .createdAt(dto.getCreatedAt())
                .build();
    }

    public List<FileDTO> toDTOList(List<AttachmentFile> files) {
        return files.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}

