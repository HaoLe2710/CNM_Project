package fit.iuh.cnm_project_be.user.repository;

import fit.iuh.cnm_project_be.user.entity.Friendship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.docker.compose.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class FriendshipRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                create table if not exists friendships (
                    id uuid primary key,
                    user_id uuid not null,
                    friend_id uuid not null,
                    is_close_friend boolean not null default false,
                    close_friend_note varchar(255),
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone,
                    deleted_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("delete from friendships");
    }

    @Test
    void findByUserIdAndFriendIdSuccess() {
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        Friendship saved = saveFriendship(userId, friendId, true, "Bạn thân");

        Optional<Friendship> result = friendshipRepository.findByUserIdAndFriendIdAndDeletedAtIsNull(userId, friendId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
        assertThat(result.get().isCloseFriend()).isTrue();
    }

    @Test
    void findAllCloseFriendsReturnsOnlyCloseAndActiveRows() {
        UUID userId = UUID.randomUUID();
        UUID closeFriendId = UUID.randomUUID();
        UUID normalFriendId = UUID.randomUUID();
        UUID deletedCloseFriendId = UUID.randomUUID();

        saveFriendship(userId, closeFriendId, true, "A");
        saveFriendship(userId, normalFriendId, false, null);

        Friendship deletedClose = saveFriendship(userId, deletedCloseFriendId, true, "B");
        deletedClose.setDeletedAt(Instant.parse("2026-05-29T09:00:00Z"));
        friendshipRepository.saveAndFlush(deletedClose);

        List<Friendship> result = friendshipRepository
                .findByUserIdAndCloseFriendTrueAndDeletedAtIsNullOrderByCreatedAtDesc(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFriendId()).isEqualTo(closeFriendId);
    }

    private Friendship saveFriendship(UUID userId, UUID friendId, boolean closeFriend, String note) {
        Friendship friendship = new Friendship();
        friendship.setUserId(userId);
        friendship.setFriendId(friendId);
        friendship.setCloseFriend(closeFriend);
        friendship.setCloseFriendNote(note);
        friendship.setCreatedAt(Instant.parse("2026-05-29T08:00:00Z"));
        return friendshipRepository.saveAndFlush(friendship);
    }
}
