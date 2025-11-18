package com.linkup.Petory.domain.activity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.activity.dto.ActivityDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.care.repository.CareRequestCommentRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;
import com.linkup.Petory.domain.location.repository.LocationServiceReviewRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final CareRequestRepository careRequestRepository;
    private final BoardRepository boardRepository;
    private final MissingPetBoardRepository missingPetBoardRepository;
    private final CareRequestCommentRepository careRequestCommentRepository;
    private final CommentRepository commentRepository;
    private final MissingPetCommentRepository missingPetCommentRepository;
    private final LocationServiceReviewRepository locationServiceReviewRepository;
    private final UsersRepository usersRepository;

    public List<ActivityDTO> getUserActivities(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<ActivityDTO> activities = new ArrayList<>();

        // 펫케어 요청
        List<CareRequest> careRequests = careRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        activities.addAll(careRequests.stream()
                .map(cr -> ActivityDTO.builder()
                        .idx(cr.getIdx())
                        .type("CARE_REQUEST")
                        .title(cr.getTitle())
                        .content(cr.getDescription())
                        .createdAt(cr.getCreatedAt())
                        .status(cr.getStatus() != null ? cr.getStatus().name() : null)
                        .deleted(cr.getIsDeleted())
                        .deletedAt(cr.getDeletedAt())
                        .build())
                .collect(Collectors.toList()));

        // 커뮤니티 게시글
        List<Board> boards = boardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        activities.addAll(boards.stream()
                .map(b -> ActivityDTO.builder()
                        .idx(b.getIdx())
                        .type("BOARD")
                        .title(b.getTitle())
                        .content(b.getContent())
                        .createdAt(b.getCreatedAt())
                        .status(b.getStatus() != null ? b.getStatus().name() : null)
                        .deleted(b.getIsDeleted())
                        .deletedAt(b.getDeletedAt())
                        .build())
                .collect(Collectors.toList()));

        // 실종 제보 게시글
        List<MissingPetBoard> missingPetBoards = missingPetBoardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        activities.addAll(missingPetBoards.stream()
                .map(mb -> ActivityDTO.builder()
                        .idx(mb.getIdx())
                        .type("MISSING_PET")
                        .title(mb.getTitle())
                        .content(mb.getContent())
                        .createdAt(mb.getCreatedAt())
                        .status(mb.getStatus() != null ? mb.getStatus().name() : null)
                        .deleted(mb.getIsDeleted())
                        .deletedAt(mb.getDeletedAt())
                        .build())
                .collect(Collectors.toList()));

        // 펫케어 댓글
        List<CareRequestComment> careComments = careRequestCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        activities.addAll(careComments.stream()
                .map(cc -> {
                    CareRequest cr = cc.getCareRequest();
                    return ActivityDTO.builder()
                            .idx(cc.getIdx())
                            .type("CARE_COMMENT")
                            .title(null)
                            .content(cc.getContent())
                            .createdAt(cc.getCreatedAt())
                            .status(cc.getStatus() != null ? cc.getStatus().name() : null)
                            .deleted(cc.getIsDeleted())
                            .deletedAt(cc.getDeletedAt())
                            .relatedId(cr != null ? cr.getIdx() : null)
                            .relatedTitle(cr != null ? cr.getTitle() : null)
                            .build();
                })
                .collect(Collectors.toList()));

        // 커뮤니티 댓글
        List<Comment> comments = commentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        activities.addAll(comments.stream()
                .map(c -> {
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
                })
                .collect(Collectors.toList()));

        // 실종 제보 댓글
        List<MissingPetComment> missingComments = missingPetCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        activities.addAll(missingComments.stream()
                .map(mc -> {
                    MissingPetBoard mb = mc.getBoard();
                    return ActivityDTO.builder()
                            .idx(mc.getIdx())
                            .type("MISSING_COMMENT")
                            .title(null)
                            .content(mc.getContent())
                            .createdAt(mc.getCreatedAt())
                            .status(mc.getStatus() != null ? mc.getStatus().name() : null)
                            .deleted(mc.getIsDeleted())
                            .deletedAt(mc.getDeletedAt())
                            .relatedId(mb != null ? mb.getIdx() : null)
                            .relatedTitle(mb != null ? mb.getTitle() : null)
                            .build();
                })
                .collect(Collectors.toList()));

        // 주변서비스 리뷰
        List<LocationServiceReview> reviews = locationServiceReviewRepository.findByUserIdxOrderByCreatedAtDesc(userId);
        activities.addAll(reviews.stream()
                .map(r -> ActivityDTO.builder()
                        .idx(r.getIdx())
                        .type("LOCATION_REVIEW")
                        .title(null)
                        .content(r.getComment())
                        .createdAt(r.getCreatedAt())
                        .status(null)
                        .deleted(false)
                        .deletedAt(null)
                        .relatedId(r.getService() != null ? r.getService().getIdx() : null)
                        .relatedTitle(r.getService() != null ? r.getService().getName() : null)
                        .build())
                .collect(Collectors.toList()));

        // 최신순 정렬
        activities.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        return activities;
    }
}

