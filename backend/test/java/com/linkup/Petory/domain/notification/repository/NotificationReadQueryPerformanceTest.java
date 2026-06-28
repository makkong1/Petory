package com.linkup.Petory.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.linkup.Petory.domain.notification.entity.Notification;
import com.linkup.Petory.domain.notification.entity.NotificationType;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.SpringDataJpaUsersRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationReadQueryPerformanceTest {

    private static final int NOTIFICATION_COUNT = 100;

    @Autowired
    private SpringDataJpaNotificationRepository notificationRepository;

    @Autowired
    private SpringDataJpaUsersRepository usersRepository;

    @Autowired
    private EntityManager entityManager;

    private Long userId;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("DROP TEMPORARY TABLE IF EXISTS notifications").executeUpdate();
        entityManager.createNativeQuery("""
                CREATE TEMPORARY TABLE notifications (
                    idx BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_idx BIGINT NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    content VARCHAR(500),
                    related_id BIGINT,
                    related_type VARCHAR(255),
                    is_read BOOLEAN NOT NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL
                )
                """).executeUpdate();

        long unique = System.nanoTime();
        Users user = usersRepository.saveAndFlush(Users.builder()
                .id("notification_perf_" + unique)
                .username("notification_perf_" + unique)
                .email("notification_perf_" + unique + "@test.petory.local")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
        userId = user.getIdx();

        List<Notification> notifications = IntStream.range(0, NOTIFICATION_COUNT)
                .mapToObj(i -> Notification.builder()
                        .user(user)
                        .type(NotificationType.BOARD_COMMENT)
                        .title("성능 테스트 알림 " + i)
                        .content("읽음 처리 쿼리 비교")
                        .isRead(false)
                        .build())
                .toList();
        notificationRepository.saveAllAndFlush(notifications);
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        statistics = session.getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @AfterEach
    void tearDown() {
        entityManager.createNativeQuery("DROP TEMPORARY TABLE IF EXISTS notifications").executeUpdate();
    }

    @Test
    @DisplayName("전체 읽음 100개: 기존 행별 변경 102 statements, bulk UPDATE 1 statement")
    void compareLegacyRowByRowUpdateAndBulkUpdate() {
        Users user = entityManager.find(Users.class, userId);
        List<Notification> unread = entityManager.createQuery("""
                SELECT n
                  FROM Notification n
                 WHERE n.user = :user
                   AND n.isRead = false
                 ORDER BY n.createdAt DESC
                """, Notification.class)
                .setParameter("user", user)
                .getResultList();

        unread.forEach(notification -> notification.setIsRead(true));
        entityManager.flush();

        long legacyStatements = statistics.getPrepareStatementCount();
        assertThat(unread).hasSize(NOTIFICATION_COUNT);
        assertThat(legacyStatements).isEqualTo(NOTIFICATION_COUNT + 2L);

        entityManager.createQuery("""
                UPDATE Notification n
                   SET n.isRead = false
                 WHERE n.user.idx = :userId
                """)
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.clear();
        statistics.clear();

        int updated = notificationRepository.markAllAsReadByUserId(userId);
        long bulkStatements = statistics.getPrepareStatementCount();

        assertThat(updated).isEqualTo(NOTIFICATION_COUNT);
        assertThat(bulkStatements).isEqualTo(1L);
    }
}
