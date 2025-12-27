package com.linkup.Petory.domain.chat.service;

import com.linkup.Petory.domain.chat.entity.*;
import com.linkup.Petory.domain.chat.repository.ChatMessageRepository;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatMessageService.markAsRead() 성능 문제 테스트
 * 
 * 문제 상황:
 * - markAsRead() 메서드에서 채팅방의 모든 메시지를 조회하고 Java에서 필터링
 * - 메시지가 많을수록 성능 급격히 저하 (수천~수만 건 조회)
 * - DB 부하 증가 및 메모리 사용량 증가
 * 
 * 테스트 목적:
 * - 7000건의 메시지로 문제 상황 재현
 * - 시간, 메모리 사용량 등 상세 성능 측정
 * - 문제 발생 로직 확인
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatMessageServiceReadStatusPerformanceTest {

        @Autowired
        private ChatMessageService chatMessageService;

        @Autowired
        private ChatMessageRepository chatMessageRepository;

        @Autowired
        private ConversationRepository conversationRepository;

        @Autowired
        private ConversationParticipantRepository participantRepository;

        @Autowired
        private UsersRepository usersRepository;

        @PersistenceContext
        private EntityManager entityManager;

        private Conversation testConversation;
        private Users testUser;
        private Users otherUser;
        private ConversationParticipant testParticipant;
        private List<ChatMessage> testMessages;
        private static final int MESSAGE_COUNT = 7000; // 7000건의 메시지

        @BeforeEach
        void setUp() {
                long timestamp = System.currentTimeMillis();

                // 테스트 사용자 생성
                testUser = Users.builder()
                                .id("test_user_" + timestamp)
                                .username("테스트사용자_" + timestamp)
                                .email("test_" + timestamp + "@test.com")
                                .password("password")
                                .role(Role.USER)
                                .status(UserStatus.ACTIVE)
                                .emailVerified(true)
                                .isDeleted(false)
                                .build();
                testUser = usersRepository.save(testUser);

                // 다른 사용자 생성 (메시지 발신자용)
                otherUser = Users.builder()
                                .id("other_user_" + timestamp)
                                .username("다른사용자_" + timestamp)
                                .email("other_" + timestamp + "@test.com")
                                .password("password")
                                .role(Role.USER)
                                .status(UserStatus.ACTIVE)
                                .emailVerified(true)
                                .isDeleted(false)
                                .build();
                otherUser = usersRepository.save(otherUser);

                // 채팅방 생성
                testConversation = Conversation.builder()
                                .conversationType(ConversationType.DIRECT)
                                .title("성능 테스트 채팅방")
                                .status(ConversationStatus.ACTIVE)
                                .isDeleted(false)
                                .build();
                testConversation = conversationRepository.save(testConversation);

                // 참여자 생성 (테스트 사용자)
                testParticipant = ConversationParticipant.builder()
                                .conversation(testConversation)
                                .user(testUser)
                                .role(ParticipantRole.MEMBER)
                                .status(ParticipantStatus.ACTIVE)
                                .unreadCount(MESSAGE_COUNT / 2) // 읽지 않은 메시지 수
                                .isDeleted(false)
                                .build();
                testParticipant = participantRepository.save(testParticipant);

                // 다른 사용자도 참여자로 추가
                ConversationParticipant otherParticipant = ConversationParticipant.builder()
                                .conversation(testConversation)
                                .user(otherUser)
                                .role(ParticipantRole.MEMBER)
                                .status(ParticipantStatus.ACTIVE)
                                .unreadCount(0)
                                .isDeleted(false)
                                .build();
                participantRepository.save(otherParticipant);

                // 7000건의 메시지 생성 (배치 insert)
                System.out.println("\n=== 테스트 데이터 생성 시작 ===");
                System.out.println("메시지 생성 중: " + MESSAGE_COUNT + "건");

                Runtime runtime = Runtime.getRuntime();
                System.gc();
                long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
                long startTime = System.currentTimeMillis();

                testMessages = new ArrayList<>();
                int batchSize = 500; // 배치 크기

                for (int i = 0; i < MESSAGE_COUNT; i++) {
                        // 메시지 발신자: i가 짝수면 otherUser, 홀수면 testUser
                        Users sender = (i % 2 == 0) ? otherUser : testUser;

                        ChatMessage message = ChatMessage.builder()
                                        .conversation(testConversation)
                                        .sender(sender)
                                        .content("테스트 메시지 " + i)
                                        .messageType(MessageType.TEXT)
                                        .isDeleted(false)
                                        .build();
                        // BaseTimeEntity가 createdAt을 자동 관리하므로 수동 설정 불가
                        // 시간 순서는 실제 저장 시 자동으로 설정됨

                        testMessages.add(message);

                        // 배치로 저장 (500건씩)
                        if ((i + 1) % batchSize == 0 || (i + 1) == MESSAGE_COUNT) {
                                chatMessageRepository.saveAll(testMessages);
                                entityManager.flush();
                                entityManager.clear();
                                testMessages.clear();

                                // 진행 상황 출력
                                if ((i + 1) % 1000 == 0 || (i + 1) == MESSAGE_COUNT) {
                                        System.out.println("  진행: " + (i + 1) + "/" + MESSAGE_COUNT + "건 생성 완료");
                                }
                        }
                }

                long endTime = System.currentTimeMillis();
                System.gc();
                long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

                long dataCreationTime = endTime - startTime;
                long memoryUsed = memoryAfter - memoryBefore;

                System.out.println("메시지 생성 완료");
                System.out.println("생성 시간: " + dataCreationTime + " ms");
                System.out.println("메모리 사용량: " + (memoryUsed / (1024 * 1024)) + " MB");
                System.out.println("============================\n");

                // 최신 메시지로 채팅방 업데이트
                List<ChatMessage> lastMessages = chatMessageRepository
                                .findByConversationIdxOrderByCreatedAtDesc(testConversation.getIdx());
                if (!lastMessages.isEmpty()) {
                        ChatMessage lastMessage = lastMessages.get(0);
                        testConversation.setLastMessageAt(lastMessage.getCreatedAt());
                        testConversation.setLastMessagePreview(lastMessage.getContent());
                        conversationRepository.save(testConversation);
                }

                entityManager.flush();
                entityManager.clear();
        }

        @Test
        @DisplayName("markAsRead() 성능 문제 재현: 전체 메시지 조회 후 Java 필터링")
        void testMarkAsReadPerformanceIssue() {
                Long conversationIdx = testConversation.getIdx();
                Long userId = testUser.getIdx();
                List<ChatMessage> messages = chatMessageRepository
                                .findByConversationIdxOrderByCreatedAtDesc(conversationIdx);
                Long lastMessageIdx = messages.isEmpty() ? null : messages.get(0).getIdx();

                System.out.println("\n" + "=".repeat(70));
                System.out.println("⚠️  markAsRead() 성능 문제 재현 테스트");
                System.out.println("=".repeat(70));
                System.out.println("채팅방 ID: " + conversationIdx);
                System.out.println("사용자 ID: " + userId);
                System.out.println("메시지 수: " + MESSAGE_COUNT + "건");
                System.out.println("마지막 메시지 ID: " + lastMessageIdx);
                System.out.println();
                System.out.println("💡 문제: markAsRead() 내부에서 불필요하게 전체 메시지를 조회하고 있습니다!");
                System.out.println("   위치: ChatMessageService.markAsRead() 171-189줄");
                System.out.println("   코드: findByConversationIdxOrderByCreatedAtDesc() → 전체 메시지 조회");
                System.out.println();

                entityManager.flush();
                entityManager.clear();

                // ========== 1단계: 불필요한 로직 제외하고 필요한 부분만 측정 ==========
                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│ [1단계] 필수 로직만 실행 (불필요한 전체 조회 제외)          │");
                System.out.println("└──────────────────────────────────────────────────────────┘");

                Runtime runtime = Runtime.getRuntime();
                System.gc();
                Thread.yield();
                long memoryBeforeEssential = runtime.totalMemory() - runtime.freeMemory();
                long startTimeEssential = System.nanoTime();

                // 필수 로직만: 참여자 조회 및 업데이트
                ConversationParticipant essentialParticipant = participantRepository
                                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));
                essentialParticipant.setUnreadCount(0);
                if (lastMessageIdx != null) {
                        ChatMessage lastMessage = chatMessageRepository.findById(lastMessageIdx).orElse(null);
                        if (lastMessage != null) {
                                essentialParticipant.setLastReadMessage(lastMessage);
                                essentialParticipant.setLastReadAt(LocalDateTime.now());
                        }
                }
                participantRepository.save(essentialParticipant);

                long endTimeEssential = System.nanoTime();
                System.gc();
                Thread.yield();
                long memoryAfterEssential = runtime.totalMemory() - runtime.freeMemory();
                long timeEssential = endTimeEssential - startTimeEssential;
                long memoryEssential = memoryAfterEssential - memoryBeforeEssential;

                System.out.println(String.format("  실행 시간: %,d ms (%.3f 초)", timeEssential / 1_000_000,
                                timeEssential / 1_000_000_000.0));
                System.out.println(String.format("  메모리 사용: %.2f MB", memoryEssential / (1024.0 * 1024.0)));
                System.out.println();

                entityManager.flush();
                entityManager.clear();

                // ========== 2단계: 문제가 되는 불필요한 전체 메시지 조회 부분만 측정 ==========
                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│ [2단계] ⚠️ 문제가 되는 불필요한 로직만 실행                │");
                System.out.println("│         (전체 메시지 조회 + Java 필터링)                   │");
                System.out.println("└──────────────────────────────────────────────────────────┘");

                System.gc();
                Thread.yield();
                long memoryBeforeProblem = runtime.totalMemory() - runtime.freeMemory();
                long startTimeProblem = System.nanoTime();

                // 문제가 되는 로직: markAsRead()의 171-189줄 부분 재현
                ChatMessage lastMessage = lastMessageIdx != null
                                ? chatMessageRepository.findById(lastMessageIdx).orElse(null)
                                : null;
                LocalDateTime lastMessageTime = lastMessage != null ? lastMessage.getCreatedAt() : LocalDateTime.now();

                // ⚠️ 문제: 전체 메시지 조회 (7000건 모두 조회!)
                List<ChatMessage> allMessages = chatMessageRepository
                                .findByConversationIdxOrderByCreatedAtDesc(conversationIdx);

                // ⚠️ 문제: Java에서 필터링 (DB에서 가져온 데이터를 Java에서 처리)
                List<ChatMessage> unreadMessages = allMessages.stream()
                                .filter(m -> m.getCreatedAt().isBefore(lastMessageTime)
                                                && !m.getSender().getIdx().equals(userId))
                                .collect(Collectors.toList());

                // ⚠️ MessageReadStatus가 제거되어 이 로직도 제거됨
                // 실제로는 MessageReadStatus 저장은 안 하지만, 로직은 실행됨
                // Users user = usersRepository.findById(userId).orElseThrow();
                // for (ChatMessage message : unreadMessages) {
                //         if (!readStatusRepository.existsByMessageAndUser(message, user)) {
                //                 // readStatusRepository.save(...); // 주석 처리되어 실제 저장은 안 함
                //         }
                // }

                long endTimeProblem = System.nanoTime();
                System.gc();
                Thread.yield();
                long memoryAfterProblem = runtime.totalMemory() - runtime.freeMemory();
                long timeProblem = endTimeProblem - startTimeProblem;
                long memoryProblem = memoryAfterProblem - memoryBeforeProblem;

                System.out.println(String.format("  전체 메시지 조회: %,d건", allMessages.size()));
                System.out.println(String.format("  필터링된 메시지: %,d건", unreadMessages.size()));
                System.out.println(String.format("  실행 시간: %,d ms (%.3f 초)", timeProblem / 1_000_000,
                                timeProblem / 1_000_000_000.0));
                System.out.println(String.format("  메모리 사용: %.2f MB", memoryProblem / (1024.0 * 1024.0)));
                System.out.println();

                entityManager.flush();
                entityManager.clear();

                // ========== 3단계: 전체 markAsRead() 실행 ==========
                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│ [3단계] 전체 markAsRead() 메서드 실행                    │");
                System.out.println("└──────────────────────────────────────────────────────────┘");

                System.gc();
                Thread.yield();
                long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
                long startTimeMillis = System.currentTimeMillis();

                chatMessageService.markAsRead(conversationIdx, userId, lastMessageIdx);

                long endTimeMillis = System.currentTimeMillis();
                long executionTimeMillis = endTimeMillis - startTimeMillis;

                System.gc();
                Thread.yield();
                long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
                long memoryUsed = memoryAfter - memoryBefore;

                System.out.println(String.format("  실행 시간: %,d ms (%.3f 초)", executionTimeMillis,
                                executionTimeMillis / 1000.0));
                System.out.println(String.format("  메모리 사용: %.2f MB", memoryUsed / (1024.0 * 1024.0)));
                System.out.println();

                // ========== 결과 비교 및 분석 ==========
                System.out.println("=".repeat(70));
                System.out.println("📊 성능 비교 분석");
                System.out.println("=".repeat(70));
                System.out.println();

                // 비교 테이블
                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│                    실행 시간 비교                         │");
                System.out.println("├──────────────────────────────────────────────────────────┤");
                System.out.println(String.format("│ [필수 로직만]        %,15d ms (%.3f 초)            │",
                                timeEssential / 1_000_000, timeEssential / 1_000_000_000.0));
                System.out.println(String.format("│ [불필요한 로직]      %,15d ms (%.3f 초)            │",
                                timeProblem / 1_000_000, timeProblem / 1_000_000_000.0));
                System.out.println(String.format("│ [전체 markAsRead()]  %,15d ms (%.3f 초)            │",
                                executionTimeMillis, executionTimeMillis / 1000.0));

                if (timeProblem > timeEssential) {
                        double overhead = (timeProblem / (double) timeEssential);
                        System.out.println(
                                        String.format("│                                                           │"));
                        System.out.println(String.format("│ ⚠️  불필요한 로직이 필수 로직보다 약 %.1f배 더 느립니다!        │", overhead));
                }
                System.out.println("└──────────────────────────────────────────────────────────┘");
                System.out.println();

                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│                    메모리 사용 비교                       │");
                System.out.println("├──────────────────────────────────────────────────────────┤");
                System.out.println(String.format("│ [필수 로직만]        %,15.2f MB                      │",
                                memoryEssential / (1024.0 * 1024.0)));
                System.out.println(String.format("│ [불필요한 로직]      %,15.2f MB                      │",
                                memoryProblem / (1024.0 * 1024.0)));
                System.out.println(String.format("│ [전체 markAsRead()]  %,15.2f MB                      │",
                                memoryUsed / (1024.0 * 1024.0)));
                System.out.println("└──────────────────────────────────────────────────────────┘");
                System.out.println();

                // 문제 분석
                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│ ⚠️  문제 분석                                             │");
                System.out.println("├──────────────────────────────────────────────────────────┤");
                System.out.println("│                                                          │");
                System.out.println("│ 문제 발생 위치: ChatMessageService.markAsRead() 171-189줄│");
                System.out.println("│                                                          │");
                System.out.println("│ 1. 전체 메시지 조회:                                      │");
                System.out.println("│    - " + MESSAGE_COUNT + "건의 메시지를 모두 DB에서 조회                  │");
                System.out.println("│    - findByConversationIdxOrderByCreatedAtDesc() 사용   │");
                System.out.println("│                                                          │");
                System.out.println("│ 2. Java에서 필터링:                                       │");
                System.out.println("│    - DB에서 가져온 데이터를 Java 스트림으로 필터링        │");
                System.out.println("│    - 메모리에 모든 데이터를 로드한 후 처리                │");
                System.out.println("│                                                          │");
                System.out.println("│ 3. 불필요한 로직:                                         │");
                System.out.println("│    - MessageReadStatus 기록 로직이 있으나 실제로 사용 안 함│");
                System.out.println("│    - 주석 처리되어 있지만 전체 조회는 여전히 실행됨       │");
                System.out.println("│                                                          │");
                System.out.println("│ 📈 영향:                                                  │");
                System.out.println("│    • 메시지가 많을수록 성능 급격히 저하 (O(n))            │");
                System.out.println("│    • DB 부하 증가 (대량 데이터 조회)                      │");
                System.out.println("│    • 메모리 사용량 증가 (전체 메시지 로드)                │");
                System.out.println("│    • 응답 시간 증가 (사용자 경험 저하)                    │");
                System.out.println("│                                                          │");
                System.out.println("└──────────────────────────────────────────────────────────┘");
                System.out.println();

                // 해결 방안
                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│ 💡 해결 방안                                              │");
                System.out.println("├──────────────────────────────────────────────────────────┤");
                System.out.println("│                                                          │");
                System.out.println("│ 1. 불필요한 로직 제거 (171-189줄 전체 삭제):              │");
                System.out.println("│    - 전체 메시지 조회 로직 제거                          │");
                System.out.println("│    - MessageReadStatus 기록 로직 제거 (사용 안 함)        │");
                System.out.println("│    - 필수 로직만 유지 (참여자 업데이트만)                │");
                System.out.println("│                                                          │");
                System.out.println("│ 2. 예상 효과:                                             │");
                long timeEssentialMillis = timeEssential / 1_000_000;
                int improvementPercent = executionTimeMillis > 0
                                ? (int) ((1 - (timeEssentialMillis / (double) executionTimeMillis)) * 100)
                                : 0;
                System.out.println("│    • 실행 시간: " + String.format("%,d ms → %,d ms (약 %d%% 개선)",
                                executionTimeMillis, timeEssentialMillis, improvementPercent));
                System.out.println("│    • 메모리 사용량: 대폭 감소                            │");
                System.out.println("│    • DB 부하: 전체 조회 쿼리 제거                        │");
                System.out.println("│                                                          │");
                System.out.println("└──────────────────────────────────────────────────────────┘");
                System.out.println();

                // 검증
                ConversationParticipant participant = participantRepository
                                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                                .orElse(null);

                assertThat(participant).isNotNull();
                assertThat(participant.getUnreadCount()).isEqualTo(0); // 읽음 처리 후 0이어야 함
                assertThat(participant.getLastReadMessage()).isNotNull(); // 마지막 읽은 메시지 설정됨

                System.out.println("=".repeat(70));
                System.out.println("테스트 완료");
                System.out.println("=".repeat(70));
                System.out.println("✅ 읽음 처리 정상 완료");
                System.out.println("✅ unreadCount: " + participant.getUnreadCount());
                System.out.println("✅ lastReadMessage 설정됨");
                System.out.println();
        }

        @Test
        @DisplayName("전체 메시지 조회 쿼리 성능 측정")
        void testFindAllMessagesPerformance() {
                Long conversationIdx = testConversation.getIdx();

                System.out.println("\n" + "=".repeat(70));
                System.out.println("전체 메시지 조회 쿼리 성능 테스트");
                System.out.println("=".repeat(70));
                System.out.println("채팅방 ID: " + conversationIdx);
                System.out.println("메시지 수: " + MESSAGE_COUNT + "건");
                System.out.println();

                // 메모리 측정 시작
                Runtime runtime = Runtime.getRuntime();
                System.gc();
                Thread.yield();
                long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

                // 시간 측정 시작
                long startTime = System.nanoTime();

                // 문제 발생 쿼리: 전체 메시지 조회
                List<ChatMessage> allMessages = chatMessageRepository
                                .findByConversationIdxOrderByCreatedAtDesc(conversationIdx);

                // 시간 측정 종료
                long endTime = System.nanoTime();
                long executionTimeNanos = endTime - startTime;
                long executionTimeMillis = executionTimeNanos / 1_000_000;

                // 메모리 측정 종료
                System.gc();
                Thread.yield();
                long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
                long memoryUsed = memoryAfter - memoryBefore;

                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│ 쿼리 실행 결과                                             │");
                System.out.println("├──────────────────────────────────────────────────────────┤");
                System.out.println(String.format("│ 조회된 메시지 수: %,20d 건                      │", allMessages.size()));
                System.out.println(
                                String.format("│ 실행 시간:        %,20d ms                      │", executionTimeMillis));
                System.out.println(String.format("│ 메모리 사용량:    %,15d bytes (%,8.2f MB)  │",
                                memoryUsed, memoryUsed / (1024.0 * 1024.0)));
                System.out.println("└──────────────────────────────────────────────────────────┘");
                System.out.println();

                assertThat(allMessages).hasSize(MESSAGE_COUNT);
        }
}
