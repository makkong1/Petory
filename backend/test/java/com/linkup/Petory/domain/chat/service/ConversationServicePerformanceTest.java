package com.linkup.Petory.domain.chat.service;

import com.linkup.Petory.domain.chat.converter.ChatMessageConverter;
import com.linkup.Petory.domain.chat.converter.ConversationConverter;
import com.linkup.Petory.domain.chat.converter.ConversationParticipantConverter;
import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.entity.*;
import com.linkup.Petory.domain.chat.repository.ChatMessageRepository;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConversationServicePerformanceTest {

        @Autowired
        private ConversationRepository conversationRepository;

        @Autowired
        private ConversationParticipantRepository participantRepository;

        @Autowired
        private ChatMessageRepository chatMessageRepository;

        @Autowired
        private UsersRepository usersRepository;

        @Autowired
        private ConversationConverter conversationConverter;

        @Autowired
        private ConversationParticipantConverter participantConverter;

        @Autowired
        private ChatMessageConverter messageConverter;

        @PersistenceContext
        private EntityManager entityManager;

        private Users testUser;
        private List<Users> otherUsers;
        private List<Conversation> testConversations;
        private static final int CONVERSATION_COUNT = 10; // 채팅방 10개 기준
        private static final int PARTICIPANTS_PER_CONVERSATION = 3; // 채팅방당 참여자 3명
        private static final int MESSAGES_PER_CONVERSATION = 20; // 채팅방당 메시지 20개

        @BeforeEach
        void setUp() {
                // 테스트 사용자 생성
                testUser = Users.builder()
                                .id("test_user")
                                .username("테스트사용자")
                                .email("test@test.com")
                                .password("password")
                                .role(com.linkup.Petory.domain.user.entity.Role.USER)
                                .status(com.linkup.Petory.domain.user.entity.UserStatus.ACTIVE)
                                .isDeleted(false)
                                .build();
                testUser = usersRepository.save(testUser);

                // 다른 사용자들 생성
                otherUsers = new ArrayList<>();
                for (int i = 0; i < PARTICIPANTS_PER_CONVERSATION * CONVERSATION_COUNT; i++) {
                        Users user = Users.builder()
                                        .id("other_user_" + i)
                                        .username("다른사용자" + i)
                                        .email("other" + i + "@test.com")
                                        .password("password")
                                        .role(com.linkup.Petory.domain.user.entity.Role.USER)
                                        .status(com.linkup.Petory.domain.user.entity.UserStatus.ACTIVE)
                                        .isDeleted(false)
                                        .build();
                        otherUsers.add(usersRepository.save(user));
                }

                // 채팅방 생성
                testConversations = new ArrayList<>();
                int userIndex = 0;
                for (int i = 0; i < CONVERSATION_COUNT; i++) {
                        Conversation conversation = Conversation.builder()
                                        .conversationType(ConversationType.DIRECT)
                                        .title("테스트 채팅방 " + i)
                                        .status(ConversationStatus.ACTIVE)
                                        .isDeleted(false)
                                        .build();
                        conversation = conversationRepository.save(conversation);

                        // 참여자 생성 (테스트 사용자 + 다른 사용자들)
                        ConversationParticipant testUserParticipant = ConversationParticipant.builder()
                                        .conversation(conversation)
                                        .user(testUser)
                                        .status(ParticipantStatus.ACTIVE)
                                        .unreadCount(i % 3) // 읽지 않은 메시지 수 다양하게
                                        .isDeleted(false)
                                        .build();
                        participantRepository.save(testUserParticipant);

                        for (int j = 0; j < PARTICIPANTS_PER_CONVERSATION - 1; j++) {
                                ConversationParticipant participant = ConversationParticipant.builder()
                                                .conversation(conversation)
                                                .user(otherUsers.get(userIndex++))
                                                .status(ParticipantStatus.ACTIVE)
                                                .unreadCount(0)
                                                .isDeleted(false)
                                                .build();
                                participantRepository.save(participant);
                        }

                        // 메시지 생성 (각 채팅방당 20개)
                        List<ChatMessage> messages = new ArrayList<>();
                        for (int j = 0; j < MESSAGES_PER_CONVERSATION; j++) {
                                ChatMessage message = ChatMessage.builder()
                                                .conversation(conversation)
                                                .sender(j % 2 == 0 ? testUser : otherUsers.get(userIndex - 1))
                                                .content("메시지 " + j)
                                                .messageType(MessageType.TEXT)
                                                .isDeleted(false)
                                                .build();
                                                // BaseTimeEntity가 createdAt을 자동 관리하므로 수동 설정 불가
                                                // 시간 순서는 실제 저장 시 자동으로 설정됨
                                messages.add(chatMessageRepository.save(message));
                        }

                        // 마지막 메시지 시간 업데이트
                        conversation.setLastMessageAt(messages.get(messages.size() - 1).getCreatedAt());
                        conversation.setLastMessagePreview("메시지 " + (MESSAGES_PER_CONVERSATION - 1));
                        conversation = conversationRepository.save(conversation);

                        testConversations.add(conversation);
                }

                entityManager.flush();
                entityManager.clear();
        }

        /**
         * 수정 전 버전: N+1 문제가 있는 로직
         */
        private List<ConversationDTO> getMyConversationsBefore(Long userId) {
                List<Conversation> conversations = conversationRepository
                                .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);

                return conversations.stream()
                                .map(conv -> {
                                        ConversationDTO dto = conversationConverter.toDTO(conv);

                                        // N+1 문제: 각 채팅방마다 개별 쿼리
                                        ConversationParticipant myParticipant = participantRepository
                                                        .findByConversationIdxAndUserIdx(conv.getIdx(), userId)
                                                        .orElse(null);

                                        if (myParticipant != null) {
                                                dto.setUnreadCount(myParticipant.getUnreadCount());
                                        }

                                        // N+1 문제: 각 채팅방마다 개별 쿼리
                                        List<ConversationParticipant> participants = participantRepository
                                                        .findByConversationIdxAndStatus(conv.getIdx(),
                                                                        ParticipantStatus.ACTIVE);
                                        if (participants != null) {
                                                dto.setParticipants(participantConverter.toDTOList(participants));
                                        }

                                        // 메모리 부하: 모든 메시지 로드
                                        if (conv.getMessages() != null && !conv.getMessages().isEmpty()) {
                                                conv.getMessages().stream()
                                                                .max((m1, m2) -> m1.getCreatedAt()
                                                                                .compareTo(m2.getCreatedAt()))
                                                                .ifPresent(lastMessage -> {
                                                                        dto.setLastMessage(messageConverter
                                                                                        .toDTO(lastMessage));
                                                                });
                                        }

                                        return dto;
                                })
                                .collect(Collectors.toList());
        }

        /**
         * 수정 후 버전: 최적화된 로직
         */
        private List<ConversationDTO> getMyConversationsAfter(Long userId) {
                List<Conversation> conversations = conversationRepository
                                .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);

                if (conversations.isEmpty()) {
                        return new ArrayList<>();
                }

                List<Long> conversationIdxs = conversations.stream()
                                .map(Conversation::getIdx)
                                .collect(Collectors.toList());

                // 배치 조회: 현재 사용자의 참여자 정보
                List<ConversationParticipant> myParticipants = participantRepository
                                .findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);
                Map<Long, ConversationParticipant> myParticipantMap = myParticipants.stream()
                                .collect(Collectors.toMap(
                                                p -> p.getConversation().getIdx(),
                                                p -> p,
                                                (existing, replacement) -> existing));

                // 배치 조회: 모든 활성 참여자 정보
                List<ConversationParticipant> allParticipants = participantRepository
                                .findParticipantsByConversationIdxsAndStatus(conversationIdxs,
                                                ParticipantStatus.ACTIVE);
                Map<Long, List<ConversationParticipant>> participantsMap = allParticipants.stream()
                                .collect(Collectors.groupingBy(p -> p.getConversation().getIdx()));

                // 배치 조회: 각 채팅방의 최신 메시지
                List<ChatMessage> latestMessages = chatMessageRepository
                                .findLatestMessagesByConversationIdxs(conversationIdxs);
                Map<Long, ChatMessage> latestMessageMap = latestMessages.stream()
                                .collect(Collectors.toMap(
                                                m -> m.getConversation().getIdx(),
                                                m -> m,
                                                (existing, replacement) -> existing));

                return conversations.stream()
                                .map(conv -> {
                                        ConversationDTO dto = conversationConverter.toDTO(conv);

                                        ConversationParticipant myParticipant = myParticipantMap.get(conv.getIdx());
                                        if (myParticipant != null) {
                                                dto.setUnreadCount(myParticipant.getUnreadCount());
                                        }

                                        List<ConversationParticipant> participants = participantsMap.getOrDefault(
                                                        conv.getIdx(),
                                                        new ArrayList<>());
                                        if (!participants.isEmpty()) {
                                                dto.setParticipants(participantConverter.toDTOList(participants));
                                        }

                                        ChatMessage lastMessage = latestMessageMap.get(conv.getIdx());
                                        if (lastMessage != null) {
                                                dto.setLastMessage(messageConverter.toDTO(lastMessage));
                                        }

                                        return dto;
                                })
                                .collect(Collectors.toList());
        }

        @Test
        @DisplayName("채팅방 목록 조회 성능 비교: 수정 전 vs 수정 후")
        void comparePerformanceBeforeAndAfter() {
                Long userId = testUser.getIdx();

                // Hibernate 통계 활성화
                Session session = entityManager.unwrap(Session.class);
                Statistics statistics = session.getSessionFactory().getStatistics();
                statistics.setStatisticsEnabled(true);
                statistics.clear();

                // ========== 수정 전 버전 테스트 ==========
                System.out.println("\n========== 수정 전 버전 테스트 시작 ==========");

                // 메모리 측정 시작
                Runtime runtime = Runtime.getRuntime();
                System.gc(); // GC 실행하여 정확한 측정
                long memoryBeforeBefore = runtime.totalMemory() - runtime.freeMemory();

                // 시간 측정 시작
                long startTimeBefore = System.currentTimeMillis();

                // 쿼리 수 측정 시작
                long queryCountBefore = statistics.getQueryExecutionCount();

                List<ConversationDTO> resultBefore = getMyConversationsBefore(userId);

                // 측정 종료
                long endTimeBefore = System.currentTimeMillis();
                long queryCountAfterBefore = statistics.getQueryExecutionCount();
                System.gc();
                long memoryAfterBefore = runtime.totalMemory() - runtime.freeMemory();

                long executionTimeBefore = endTimeBefore - startTimeBefore;
                long queryCountBeforeTotal = queryCountAfterBefore - queryCountBefore;
                long memoryUsedBefore = memoryAfterBefore - memoryBeforeBefore;

                System.out.println("\n" + "=".repeat(50));
                System.out.println("수정 전 버전 결과");
                System.out.println("=".repeat(50));
                System.out.println(String.format("실행 시간: %,d ms", executionTimeBefore));
                System.out.println(String.format("쿼리 수: %,d 개", queryCountBeforeTotal));
                System.out.println(String.format("메모리 사용량: %,d bytes (%.2f MB)",
                                memoryUsedBefore, memoryUsedBefore / (1024.0 * 1024.0)));
                System.out.println(String.format("조회된 채팅방 수: %,d 개", resultBefore.size()));

                // 결과 검증
                assertThat(resultBefore).hasSize(CONVERSATION_COUNT);

                // 통계 초기화
                statistics.clear();
                entityManager.clear();

                // ========== 수정 후 버전 테스트 ==========
                System.out.println("\n========== 수정 후 버전 테스트 시작 ==========");

                // 메모리 측정 시작
                System.gc();
                long memoryBeforeAfter = runtime.totalMemory() - runtime.freeMemory();

                // 시간 측정 시작
                long startTimeAfter = System.currentTimeMillis();

                // 쿼리 수 측정 시작
                long queryCountStartAfter = statistics.getQueryExecutionCount();

                List<ConversationDTO> resultAfter = getMyConversationsAfter(userId);

                // 측정 종료
                long endTimeAfter = System.currentTimeMillis();
                long queryCountEndAfter = statistics.getQueryExecutionCount();
                System.gc();
                long memoryAfterAfter = runtime.totalMemory() - runtime.freeMemory();

                long executionTimeAfter = endTimeAfter - startTimeAfter;
                long queryCountAfterTotal = queryCountEndAfter - queryCountStartAfter;
                long memoryUsedAfter = memoryAfterAfter - memoryBeforeAfter;

                System.out.println("\n" + "=".repeat(50));
                System.out.println("수정 후 버전 결과");
                System.out.println("=".repeat(50));
                System.out.println(String.format("실행 시간: %,d ms", executionTimeAfter));
                System.out.println(String.format("쿼리 수: %,d 개", queryCountAfterTotal));
                System.out.println(String.format("메모리 사용량: %,d bytes (%.2f MB)",
                                memoryUsedAfter, memoryUsedAfter / (1024.0 * 1024.0)));
                System.out.println(String.format("조회된 채팅방 수: %,d 개", resultAfter.size()));

                // 결과 검증
                assertThat(resultAfter).hasSize(CONVERSATION_COUNT);

                // ========== 성능 비교 결과 ==========
                System.out.println("\n" + "=".repeat(50));
                System.out.println("성능 비교 결과");
                System.out.println("=".repeat(50));
                System.out.println(String.format("테스트 환경: 채팅방 %,d개, 참여자 %d명/채팅방, 메시지 %,d개/채팅방",
                                CONVERSATION_COUNT, PARTICIPANTS_PER_CONVERSATION, MESSAGES_PER_CONVERSATION));
                System.out.println();

                // 실행 시간 비교
                System.out.println("┌─────────────────────────────────────────┐");
                System.out.println("│ 실행 시간 비교                           │");
                System.out.println("├─────────────────────────────────────────┤");
                System.out.println(String.format("│ 수정 전: %,15d ms              │", executionTimeBefore));
                System.out.println(String.format("│ 수정 후: %,15d ms              │", executionTimeAfter));
                if (executionTimeBefore > 0) {
                        long timeReduction = executionTimeBefore - executionTimeAfter;
                        double timeImprovement = ((double) timeReduction / executionTimeBefore) * 100;
                        System.out.println(String.format("│ 절감량:  %,15d ms              │", timeReduction));
                        System.out.println(String.format("│ 개선율:  %,15.2f%%              │", timeImprovement));
                }
                System.out.println("└─────────────────────────────────────────┘");
                System.out.println();

                // 쿼리 수 비교
                System.out.println("┌─────────────────────────────────────────┐");
                System.out.println("│ 쿼리 수 비교                             │");
                System.out.println("├─────────────────────────────────────────┤");
                System.out.println(String.format("│ 수정 전: %,15d 개              │", queryCountBeforeTotal));
                System.out.println(String.format("│ 수정 후: %,15d 개              │", queryCountAfterTotal));
                if (queryCountBeforeTotal > 0) {
                        long queryReduction = queryCountBeforeTotal - queryCountAfterTotal;
                        double queryImprovement = ((double) queryReduction / queryCountBeforeTotal) * 100;
                        System.out.println(String.format("│ 절감량:  %,15d 개              │", queryReduction));
                        System.out.println(String.format("│ 개선율:  %,15.2f%%              │", queryImprovement));
                }
                System.out.println("└─────────────────────────────────────────┘");
                System.out.println();

                // 메모리 사용량 비교
                System.out.println("┌─────────────────────────────────────────┐");
                System.out.println("│ 메모리 사용량 비교                       │");
                System.out.println("├─────────────────────────────────────────┤");
                System.out.println(String.format("│ 수정 전: %,15d bytes (%.2f MB)  │",
                                memoryUsedBefore, memoryUsedBefore / (1024.0 * 1024.0)));
                System.out.println(String.format("│ 수정 후: %,15d bytes (%.2f MB)  │",
                                memoryUsedAfter, memoryUsedAfter / (1024.0 * 1024.0)));
                if (memoryUsedBefore > 0) {
                        long memoryReduction = memoryUsedBefore - memoryUsedAfter;
                        double memoryImprovement = ((double) memoryReduction / memoryUsedBefore) * 100;
                        System.out.println(String.format("│ 절감량:  %,15d bytes (%.2f MB)  │",
                                        memoryReduction, memoryReduction / (1024.0 * 1024.0)));
                        System.out.println(String.format("│ 개선율:  %,15.2f%%              │", memoryImprovement));
                }
                System.out.println("└─────────────────────────────────────────┘");
                System.out.println();

                // 예상 쿼리 수 (이론적)
                System.out.println("┌─────────────────────────────────────────┐");
                System.out.println("│ 예상 쿼리 수 (이론적)                    │");
                System.out.println("├─────────────────────────────────────────┤");
                int expectedBefore = 1 + (CONVERSATION_COUNT * 2) + CONVERSATION_COUNT;
                System.out.println(String.format("│ 수정 전: 1 + %,d + %,d = %,d 개        │",
                                CONVERSATION_COUNT * 2, CONVERSATION_COUNT, expectedBefore));
                System.out.println("│ 수정 후: 1 + 1 + 1 + 1 = 4 개            │");
                System.out.println("└─────────────────────────────────────────┘");

                // 통계 비활성화
                statistics.setStatisticsEnabled(false);
        }
}
