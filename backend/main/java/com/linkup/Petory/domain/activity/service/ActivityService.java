package com.linkup.Petory.domain.activity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.activity.dto.ActivityDTO;
import com.linkup.Petory.domain.activity.dto.ActivityPageResponseDTO;
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
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.NonNull;
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
        private final UsersRepository usersRepository;

        public List<ActivityDTO> getUserActivities(long userId) {
                System.out.println("=== [ActivityService] getUserActivities 호출됨 - userId: " + userId + " ===");
                Users user = usersRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("User not found"));

                List<ActivityDTO> activities = new ArrayList<>();

                // 펫케어 요청
                System.out.println("=== [ActivityService] 펫케어 요청 조회 시작 ===");
                List<CareRequest> careRequests = careRequestRepository
                                .findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
                System.out.println("=== [ActivityService] 펫케어 요청 조회 완료: " + careRequests.size() + "개 ===");
                activities.addAll(careRequests.stream()
                                .map(cr -> {
                                        try {
                                                return ActivityDTO.builder()
                                                                .idx(cr.getIdx())
                                                                .type("CARE_REQUEST")
                                                                .title(cr.getTitle())
                                                                .content(cr.getDescription())
                                                                .createdAt(cr.getCreatedAt())
                                                                .status(cr.getStatus() != null ? cr.getStatus().name()
                                                                                : null)
                                                                .deleted(cr.getIsDeleted())
                                                                .deletedAt(cr.getDeletedAt())
                                                                .build();
                                        } catch (Exception e) {
                                                return ActivityDTO.builder()
                                                                .idx(cr.getIdx())
                                                                .type("CARE_REQUEST")
                                                                .title(cr.getTitle())
                                                                .content(cr.getDescription())
                                                                .createdAt(cr.getCreatedAt())
                                                                .status(null)
                                                                .deleted(cr.getIsDeleted())
                                                                .deletedAt(cr.getDeletedAt())
                                                                .build();
                                        }
                                })
                                .collect(Collectors.toList()));

                // 커뮤니티 게시글
                System.out.println("=== [ActivityService] 커뮤니티 게시글 조회 시작 ===");
                List<Board> boards = boardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
                System.out.println("=== [ActivityService] 커뮤니티 게시글 조회 완료: " + boards.size() + "개 ===");
                activities.addAll(boards.stream()
                                .map(b -> {
                                        try {
                                                return ActivityDTO.builder()
                                                                .idx(b.getIdx())
                                                                .type("BOARD")
                                                                .title(b.getTitle())
                                                                .content(b.getContent())
                                                                .createdAt(b.getCreatedAt())
                                                                .status(b.getStatus() != null ? b.getStatus().name()
                                                                                : null)
                                                                .deleted(b.getIsDeleted())
                                                                .deletedAt(b.getDeletedAt())
                                                                .build();
                                        } catch (Exception e) {
                                                return ActivityDTO.builder()
                                                                .idx(b.getIdx())
                                                                .type("BOARD")
                                                                .title(b.getTitle())
                                                                .content(b.getContent())
                                                                .createdAt(b.getCreatedAt())
                                                                .status(null)
                                                                .deleted(b.getIsDeleted())
                                                                .deletedAt(b.getDeletedAt())
                                                                .build();
                                        }
                                })
                                .collect(Collectors.toList()));

                // 실종 제보 게시글
                System.out.println("=== [ActivityService] 실종 제보 게시글 조회 시작 ===");
                List<MissingPetBoard> missingPetBoards = missingPetBoardRepository
                                .findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
                System.out.println("=== [ActivityService] 실종 제보 게시글 조회 완료: " + missingPetBoards.size() + "개 ===");
                activities.addAll(missingPetBoards.stream()
                                .map(mb -> {
                                        try {
                                                return ActivityDTO.builder()
                                                                .idx(mb.getIdx())
                                                                .type("MISSING_PET")
                                                                .title(mb.getTitle())
                                                                .content(mb.getContent())
                                                                .createdAt(mb.getCreatedAt())
                                                                .status(mb.getStatus() != null ? mb.getStatus().name()
                                                                                : null)
                                                                .deleted(mb.getIsDeleted())
                                                                .deletedAt(mb.getDeletedAt())
                                                                .build();
                                        } catch (Exception e) {
                                                return ActivityDTO.builder()
                                                                .idx(mb.getIdx())
                                                                .type("MISSING_PET")
                                                                .title(mb.getTitle())
                                                                .content(mb.getContent())
                                                                .createdAt(mb.getCreatedAt())
                                                                .status(null)
                                                                .deleted(mb.getIsDeleted())
                                                                .deletedAt(mb.getDeletedAt())
                                                                .build();
                                        }
                                })
                                .collect(Collectors.toList()));

                // 펫케어 댓글
                System.out.println("=== [ActivityService] 펫케어 댓글 조회 시작 ===");
                List<CareRequestComment> careComments = careRequestCommentRepository
                                .findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
                System.out.println("=== [ActivityService] 펫케어 댓글 조회 완료: " + careComments.size() + "개 ===");
                activities.addAll(careComments.stream()
                                .map(cc -> {
                                        CareRequest cr = cc.getCareRequest();
                                        return ActivityDTO.builder()
                                                        .idx(cc.getIdx())
                                                        .type("CARE_COMMENT")
                                                        .title(null)
                                                        .content(cc.getContent())
                                                        .createdAt(cc.getCreatedAt())
                                                        .status(cc.getIsDeleted() ? "DELETED" : "ACTIVE")
                                                        .deleted(cc.getIsDeleted())
                                                        .deletedAt(cc.getDeletedAt())
                                                        .relatedId(cr != null ? cr.getIdx() : null)
                                                        .relatedTitle(cr != null ? cr.getTitle() : null)
                                                        .build();
                                })
                                .collect(Collectors.toList()));

                // 커뮤니티 댓글
                System.out.println("=== [ActivityService] 커뮤니티 댓글 조회 시작 ===");
                List<Comment> comments = commentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
                System.out.println("=== [ActivityService] 커뮤니티 댓글 조회 완료: " + comments.size() + "개 ===");
                activities.addAll(comments.stream()
                                .map(c -> {
                                        Board b = c.getBoard();
                                        try {
                                                return ActivityDTO.builder()
                                                                .idx(c.getIdx())
                                                                .type("COMMENT")
                                                                .title(null)
                                                                .content(c.getContent())
                                                                .createdAt(c.getCreatedAt())
                                                                .status(c.getStatus() != null ? c.getStatus().name()
                                                                                : null)
                                                                .deleted(c.getIsDeleted())
                                                                .deletedAt(c.getDeletedAt())
                                                                .relatedId(b != null ? b.getIdx() : null)
                                                                .relatedTitle(b != null ? b.getTitle() : null)
                                                                .build();
                                        } catch (Exception e) {
                                                return ActivityDTO.builder()
                                                                .idx(c.getIdx())
                                                                .type("COMMENT")
                                                                .title(null)
                                                                .content(c.getContent())
                                                                .createdAt(c.getCreatedAt())
                                                                .status(null)
                                                                .deleted(c.getIsDeleted())
                                                                .deletedAt(c.getDeletedAt())
                                                                .relatedId(b != null ? b.getIdx() : null)
                                                                .relatedTitle(b != null ? b.getTitle() : null)
                                                                .build();
                                        }
                                })
                                .collect(Collectors.toList()));

                // 실종 제보 댓글
                System.out.println("=== [ActivityService] 실종 제보 댓글 조회 시작 ===");
                List<MissingPetComment> missingComments = missingPetCommentRepository
                                .findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
                System.out.println("=== [ActivityService] 실종 제보 댓글 조회 완료: " + missingComments.size() + "개 ===");
                activities.addAll(missingComments.stream()
                                .map(mc -> {
                                        MissingPetBoard mb = mc.getBoard();
                                        return ActivityDTO.builder()
                                                        .idx(mc.getIdx())
                                                        .type("MISSING_COMMENT")
                                                        .title(null)
                                                        .content(mc.getContent())
                                                        .createdAt(mc.getCreatedAt())
                                                        .status(mc.getIsDeleted() ? "DELETED" : "ACTIVE")
                                                        .deleted(mc.getIsDeleted())
                                                        .deletedAt(mc.getDeletedAt())
                                                        .relatedId(mb != null ? mb.getIdx() : null)
                                                        .relatedTitle(mb != null ? mb.getTitle() : null)
                                                        .build();
                                })
                                .collect(Collectors.toList()));

                // 최신순 정렬 (null-safe)
                activities.sort((a, b) -> {
                        if (a.getCreatedAt() == null && b.getCreatedAt() == null)
                                return 0;
                        if (a.getCreatedAt() == null)
                                return 1; // null은 뒤로
                        if (b.getCreatedAt() == null)
                                return -1; // null은 뒤로
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                });

                System.out.println("=== [ActivityService] 전체 활동 조회 완료: 총 " + activities.size() + "개 ===");
                return activities;
        }

        // 페이징 지원 메서드
        public ActivityPageResponseDTO getUserActivitiesWithPaging(long userId, String filter, int page, int size) {
                System.out.println("=== [ActivityService] getUserActivitiesWithPaging 호출됨 - userId: " + userId
                                + ", filter: " + filter + ", page: " + page + ", size: " + size + " ===");

                // 전체 활동 가져오기
                List<ActivityDTO> allActivities = getUserActivities(userId);

                // 필터링 적용
                List<ActivityDTO> filteredActivities = filterActivities(allActivities, filter);

                // 필터별 개수 계산
                long allCount = allActivities.size();
                long postsCount = allActivities.stream()
                                .filter(a -> isPostType(a.getType()))
                                .count();
                long commentsCount = allActivities.stream()
                                .filter(a -> isCommentType(a.getType()))
                                .count();
                long reviewsCount = allActivities.stream()
                                .filter(a -> "LOCATION_REVIEW".equals(a.getType()))
                                .count();

                // 페이징 적용
                Pageable pageable = PageRequest.of(page, size);
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), filteredActivities.size());

                List<ActivityDTO> pageContent = filteredActivities.subList(start, end);
                Page<ActivityDTO> activityPage = new PageImpl<>(pageContent, pageable, filteredActivities.size());

                return ActivityPageResponseDTO.builder()
                                .activities(activityPage.getContent())
                                .totalCount(activityPage.getTotalElements())
                                .totalPages(activityPage.getTotalPages())
                                .currentPage(page)
                                .pageSize(size)
                                .hasNext(activityPage.hasNext())
                                .hasPrevious(activityPage.hasPrevious())
                                .allCount(allCount)
                                .postsCount(postsCount)
                                .commentsCount(commentsCount)
                                .reviewsCount(reviewsCount)
                                .build();
        }

        private List<ActivityDTO> filterActivities(List<ActivityDTO> activities, String filter) {
                if (filter == null || "ALL".equals(filter)) {
                        return activities;
                }

                switch (filter) {
                        case "POSTS":
                                return activities.stream()
                                                .filter(a -> isPostType(a.getType()))
                                                .collect(Collectors.toList());
                        case "COMMENTS":
                                return activities.stream()
                                                .filter(a -> isCommentType(a.getType()))
                                                .collect(Collectors.toList());
                        case "REVIEWS":
                                return activities.stream()
                                                .filter(a -> "LOCATION_REVIEW".equals(a.getType()))
                                                .collect(Collectors.toList());
                        default:
                                return activities;
                }
        }

        private boolean isPostType(String type) {
                return "CARE_REQUEST".equals(type)
                                || "BOARD".equals(type)
                                || "MISSING_PET".equals(type);
        }

        private boolean isCommentType(String type) {
                return "CARE_COMMENT".equals(type)
                                || "COMMENT".equals(type)
                                || "MISSING_COMMENT".equals(type);
        }
}
