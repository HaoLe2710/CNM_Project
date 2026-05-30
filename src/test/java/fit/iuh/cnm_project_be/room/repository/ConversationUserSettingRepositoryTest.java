package fit.iuh.cnm_project_be.room.repository;

import fit.iuh.cnm_project_be.room.entity.ConversationUserSetting;
import fit.iuh.cnm_project_be.room.enums.GroupConversationLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.docker.compose.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ConversationUserSettingRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationUserSettingRepository conversationUserSettingRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                create table if not exists conversation_user_settings (
                    id bigserial primary key,
                    conversation_id uuid not null,
                    user_id uuid not null,
                    muted_at timestamp with time zone null,
                    muted_until timestamp with time zone null,
                    last_muted_at timestamp with time zone null,
                    archived_at timestamp with time zone null,
                    pinned_at timestamp with time zone null,
                    notification_level varchar(32) null,
                    custom_name varchar(100) null,
                    background_type varchar(16) null,
                    background_color varchar(32) null,
                    background_image_url varchar(500) null,
                    group_label varchar(50) null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone null
                )
                """);
        jdbcTemplate.execute("delete from conversation_user_settings");
    }

    @Test
    void findByUserIdAndConversationIdInReturnsMatchingRows() {
        UUID userId = UUID.randomUUID();
        UUID conversationIdA = UUID.randomUUID();
        UUID conversationIdB = UUID.randomUUID();
        UUID conversationIdC = UUID.randomUUID();

        saveSetting(userId, conversationIdA, GroupConversationLabel.WORK);
        saveSetting(userId, conversationIdB, GroupConversationLabel.FAMILY);
        saveSetting(UUID.randomUUID(), conversationIdC, GroupConversationLabel.WORK);

        List<ConversationUserSetting> result = conversationUserSettingRepository.findByUserIdAndConversationIdIn(
                userId,
                List.of(conversationIdA, conversationIdC));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getConversationId()).isEqualTo(conversationIdA);
        assertThat(result.getFirst().getGroupLabel()).isEqualTo(GroupConversationLabel.WORK);
    }

    @Test
    void findByUserIdAndGroupLabelReturnsOnlyMatchingLabel() {
        UUID userId = UUID.randomUUID();
        UUID workConversationId = UUID.randomUUID();
        UUID studyConversationId = UUID.randomUUID();

        saveSetting(userId, workConversationId, GroupConversationLabel.WORK);
        saveSetting(userId, studyConversationId, GroupConversationLabel.STUDY);

        List<ConversationUserSetting> result = conversationUserSettingRepository.findByUserIdAndGroupLabel(
                userId,
                GroupConversationLabel.WORK);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getConversationId()).isEqualTo(workConversationId);
        assertThat(result.getFirst().getGroupLabel()).isEqualTo(GroupConversationLabel.WORK);
    }

    private void saveSetting(UUID userId, UUID conversationId, GroupConversationLabel groupLabel) {
        ConversationUserSetting setting = new ConversationUserSetting();
        setting.setUserId(userId);
        setting.setConversationId(conversationId);
        setting.setGroupLabel(groupLabel);
        conversationUserSettingRepository.saveAndFlush(setting);
    }
}
