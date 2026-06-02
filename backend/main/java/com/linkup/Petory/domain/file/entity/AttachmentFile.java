package com.linkup.Petory.domain.file.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "file")
@Getter
/** 첨부파일 엔티티. 도메인 유형(targetType)과 대상 ID(targetIdx)로 어떤 도메인 레코드에 속하는지 식별한다. */
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentFile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private FileTargetType targetType;

    @Column(name = "target_idx", nullable = false)
    private Long targetIdx;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(name = "file_type", length = 50)
    private String fileType;

}

