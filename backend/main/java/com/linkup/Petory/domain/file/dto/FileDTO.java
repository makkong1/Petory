package com.linkup.Petory.domain.file.dto;

import com.linkup.Petory.domain.file.entity.FileTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDTO {
    private Long idx;
    private FileTargetType targetType;
    private Long targetIdx;
    private String filePath;
    private String fileType;
    private LocalDateTime createdAt;
}

