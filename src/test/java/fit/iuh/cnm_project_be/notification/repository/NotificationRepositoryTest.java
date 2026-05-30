package fit.iuh.cnm_project_be.notification.repository;

import fit.iuh.cnm_project_be.notification.entity.Notification;
import fit.iuh.cnm_project_be.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.docker.compose.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class NotificationRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                create table if not exists notifications (
                    id uuid primary key,
                    recipient_id uuid not null,
                    actor_id uuid,
                    type varchar(80) not null,
                    title varchar(255) not null,
                    body text,
                    target_type varchar(50),
                    target_id uuid,
                    conversation_id uuid,
                    message_id bigint,
                    post_id uuid,
                    comment_id uuid,
                    metadata json,
                    dedup_key varchar(255),
                    read_at timestamp with time zone,
                    created_at timestamp with time zone not null,
                    expires_at timestamp with time zone,
                    deleted_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("create unique index if not exists uk_notifications_dedup_key_test on notifications(dedup_key)");
        jdbcTemplate.execute("delete from notifications");
    }

    @Test
    void findByRecipientNewestFirst() {
        UUID userId = UUID.randomUUID();
        Notification older = save(userId, "Older", Instant.parse("2026-05-28T01:00:00Z"), null);
        Notification newest = save(userId, "Newest", Instant.parse("2026-05-28T02:00:00Z"), null);
        save(UUID.randomUUID(), "Other user", Instant.parse("2026-05-28T03:00:00Z"), null);

        List<Notification> notifications = notificationRepository.findCurrentPage(
                userId,
                false,
                Instant.parse("2026-05-28T04:00:00Z"),
                PageRequest.of(0, 10)
        );

        assertThat(notifications).extracting(Notification::getId)
                .containsExactly(newest.getId(), older.getId());
    }

    @Test
    void findByRecipientUnreadOnly() {
        UUID userId = UUID.randomUUID();
        Notification unread = save(userId, "Unread", Instant.parse("2026-05-28T01:00:00Z"), null);
        save(userId, "Read", Instant.parse("2026-05-28T02:00:00Z"), Instant.parse("2026-05-28T02:30:00Z"));

        List<Notification> notifications = notificationRepository.findCurrentPage(
                userId,
                true,
                Instant.parse("2026-05-28T04:00:00Z"),
                PageRequest.of(0, 10)
        );

        assertThat(notifications).extracting(Notification::getId).containsExactly(unread.getId());
    }

    @Test
    void countUnreadExcludesReadDeletedAndExpired() {
        UUID userId = UUID.randomUUID();
        save(userId, "Unread", Instant.parse("2026-05-28T01:00:00Z"), null);
        save(userId, "Read", Instant.parse("2026-05-28T01:01:00Z"), Instant.parse("2026-05-28T01:30:00Z"));
        Notification deleted = save(userId, "Deleted", Instant.parse("2026-05-28T01:02:00Z"), null);
        deleted.setDeletedAt(Instant.parse("2026-05-28T01:40:00Z"));
        notificationRepository.save(deleted);
        Notification expired = save(userId, "Expired", Instant.parse("2026-05-28T01:03:00Z"), null);
        expired.setExpiresAt(Instant.parse("2026-05-28T02:00:00Z"));
        notificationRepository.save(expired);

        long count = notificationRepository.countUnread(userId, Instant.parse("2026-05-28T03:00:00Z"));

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void dedupKeyUnique() {
        UUID userId = UUID.randomUUID();
        save(userId, "One", Instant.parse("2026-05-28T01:00:00Z"), null, "same-key");

        Notification duplicate = build(userId, "Two", Instant.parse("2026-05-28T02:00:00Z"), null);
        duplicate.setDedupKey("same-key");

        assertThatThrownBy(() -> {
            notificationRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Notification save(UUID recipientId, String title, Instant createdAt, Instant readAt) {
        return save(recipientId, title, createdAt, readAt, null);
    }

    private Notification save(UUID recipientId, String title, Instant createdAt, Instant readAt, String dedupKey) {
        Notification notification = build(recipientId, title, createdAt, readAt);
        notification.setDedupKey(dedupKey);
        return notificationRepository.saveAndFlush(notification);
    }

    private Notification build(UUID recipientId, String title, Instant createdAt, Instant readAt) {
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setType(NotificationType.SYSTEM_NOTICE);
        notification.setTitle(title);
        notification.setCreatedAt(createdAt);
        notification.setReadAt(readAt);
        return notification;
    }
}
