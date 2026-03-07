package fit.iuh.cnm_project_be.message.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MessageDto {

    private Long id;
    private UUID conversationId;
    private UUID senderId;
    private String content;
    private Instant createdAt;
}