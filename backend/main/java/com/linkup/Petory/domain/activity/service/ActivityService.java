package com.linkup.Petory.domain.activity.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.activity.converter.ActivityConverter;
import com.linkup.Petory.domain.activity.dto.ActivityDTO;
import com.linkup.Petory.domain.activity.dto.ActivityPageResponseDTO;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.care.repository.CareRequestCommentRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 사용자의 케어 요청·게시글·댓글 등 여러 도메인 활동 내역을 통합 조회하는 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ActivityService {

        private final CareRequestRepository careRequestRepository;
        private final BoardRepository boardRepository;
        private final MissingPetBoardRepository missingPetBoardRepository;
        private final CareRequestCommentRepository careRequestCommentRepository;
        private final CommentRepository commentRepository;
        private final MissingPetCommentRepository missingPetCommentRepository;
        private final UsersRepository usersRepository;
        private final ActivityConverter activityConverter;

        public List<ActivityDTO> getUserActivities(long userId) {
                Users user = usersRepository.findById(userId)
                                .orElseThrow(UserNotFoundException::new);

                return Stream.of(
                                careRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                                                .map(activityConverter::toActivityDto),
                                boardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                                                .map(activityConverter::toActivityDto),
                                missingPetBoardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                                                .map(activityConverter::toActivityDto),
                                careRequestCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user)
                                                .stream().map(activityConverter::toActivityDto),
                                commentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                                                .map(activityConverter::toActivityDto),
                                missingPetCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user)
                                                .stream().map(activityConverter::toActivityDto))
                                .flatMap(s -> s)
                                .sorted(Comparator.comparing(ActivityDTO::getCreatedAt,
                                                Comparator.nullsLast(Comparator.reverseOrder())))
                                .toList();
        }

        @SuppressWarnings("null")
        public ActivityPageResponseDTO getUserActivitiesWithPaging(long userId, String filter, int page, int size) {
                List<ActivityDTO> allActivities = getUserActivities(userId);
                List<ActivityDTO> filteredActivities = filterActivities(allActivities, filter);

                long allCount = allActivities.size();
                long postsCount = 0, commentsCount = 0, reviewsCount = 0;
                for (ActivityDTO a : allActivities) {
                        if (isPostType(a.getType()))
                                postsCount++;
                        else if (isCommentType(a.getType()))
                                commentsCount++;
                        else if ("LOCATION_REVIEW".equals(a.getType()))
                                reviewsCount++;
                }

                Pageable pageable = PageRequest.of(page, size);
                int totalFiltered = filteredActivities.size();
                int start = Math.min((int) pageable.getOffset(), totalFiltered);
                int end = Math.min(start + pageable.getPageSize(), totalFiltered);
                List<ActivityDTO> pageContent = start >= end
                                ? Collections.emptyList()
                                : filteredActivities.subList(start, end);
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

                return switch (filter) {
                        case "POSTS" -> activities.stream()
                                        .filter(a -> isPostType(a.getType()))
                                        .toList();
                        case "COMMENTS" -> activities.stream()
                                        .filter(a -> isCommentType(a.getType()))
                                        .toList();
                        case "REVIEWS" -> activities.stream()
                                        .filter(a -> "LOCATION_REVIEW".equals(a.getType()))
                                        .toList();
                        default -> activities;
                };
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
