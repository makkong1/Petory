package com.linkup.Petory.domain.file.dto;

import com.linkup.Petory.domain.file.entity.FileTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
/** 첨부파일 응답 DTO. 파일 경로·다운로드 URL·대상 도메인 유형·원본 파일명을 포함한다. */
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
    private String downloadUrl;
}

