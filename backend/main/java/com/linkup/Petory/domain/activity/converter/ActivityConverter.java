package com.linkup.Petory.domain.activity.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.activity.dto.ActivityDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;

/**
 * 활동 타임라인용 — 여러 도메인 엔티티를 통합 {@link ActivityDTO}로 변환한다.
 */
@Component
public class ActivityConverter {

        public ActivityDTO toActivityDto(CareRequest cr) {
                return ActivityDTO.builder()
                                .idx(cr.getIdx())
                                .type("CARE_REQUEST")
                                .title(cr.getTitle())
                                .content(cr.getDescription())
                                .createdAt(cr.getCreatedAt())
                                .status(cr.getStatus() != null ? cr.getStatus().name() : null)
                                .deleted(cr.getIsDeleted())
                                .deletedAt(cr.getDeletedAt())
                                .build();
        }

        public ActivityDTO toActivityDto(Board b) {
                return ActivityDTO.builder()
                                .idx(b.getIdx())
                                .type("BOARD")
                                .title(b.getTitle())
                                .content(b.getContent())
                                .createdAt(b.getCreatedAt())
                                .status(b.getStatus() != null ? b.getStatus().name() : null)
                                .deleted(b.getIsDeleted())
                                .deletedAt(b.getDeletedAt())
                                .build();
        }

        public ActivityDTO toActivityDto(MissingPetBoard mb) {
                return ActivityDTO.builder()
                                .idx(mb.getIdx())
                                .type("MISSING_PET")
                                .title(mb.getTitle())
                                .content(mb.getContent())
                                .createdAt(mb.getCreatedAt())
                                .status(mb.getStatus() != null ? mb.getStatus().name() : null)
                                .deleted(mb.getIsDeleted())
                                .deletedAt(mb.getDeletedAt())
                                .build();
        }

        public ActivityDTO toActivityDto(CareRequestComment cc) {
                CareRequest cr = cc.getCareRequest();
                return ActivityDTO.builder()
                                .idx(cc.getIdx())
                                .type("CARE_COMMENT")
                                .title(null)
                                .content(cc.getContent())
                                .createdAt(cc.getCreatedAt())
                                .status(Boolean.TRUE.equals(cc.getIsDeleted()) ? "DELETED" : "ACTIVE")
                                .deleted(cc.getIsDeleted())
                                .deletedAt(cc.getDeletedAt())
                                .relatedId(cr != null ? cr.getIdx() : null)
                                .relatedTitle(cr != null ? cr.getTitle() : null)
                                .build();
        }

        public ActivityDTO toActivityDto(Comment c) {
                Board b = c.getBoard();
                return ActivityDTO.builder()
                                .idx(c.getIdx())
                                .type("COMMENT")
                                .title(null)
                                .content(c.getContent())
                                .createdAt(c.getCreatedAt())
                                .status(c.getStatus() != null ? c.getStatus().name() : null)
                                .deleted(c.getIsDeleted())
                                .deletedAt(c.getDeletedAt())
                                .relatedId(b != null ? b.getIdx() : null)
                                .relatedTitle(b != null ? b.getTitle() : null)
                                .build();
        }

        public ActivityDTO toActivityDto(MissingPetComment mc) {
                MissingPetBoard mb = mc.getBoard();
                return ActivityDTO.builder()
                                .idx(mc.getIdx())
                                .type("MISSING_COMMENT")
                                .title(null)
                                .content(mc.getContent())
                                .createdAt(mc.getCreatedAt())
                                .status(Boolean.TRUE.equals(mc.getIsDeleted()) ? "DELETED" : "ACTIVE")
                                .deleted(mc.getIsDeleted())
                                .deletedAt(mc.getDeletedAt())
                                .relatedId(mb != null ? mb.getIdx() : null)
                                .relatedTitle(mb != null ? mb.getTitle() : null)
                                .build();
        }
}
