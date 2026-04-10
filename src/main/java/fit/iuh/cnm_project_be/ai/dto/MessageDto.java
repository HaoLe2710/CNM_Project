package fit.iuh.cnm_project_be.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor


public class MessageDto {
    private Long id;
    private UUID conversationId;
    private String senderName;
    private String content;
    private Instant createdAt;
}
