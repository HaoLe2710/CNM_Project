package fit.iuh.cnm_project_be.poll.dto.response;

import fit.iuh.cnm_project_be.poll.enums.PollStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PollResponse {
    private UUID id;
    private UUID conversationId;
    private UUID creatorId;
    private String question;
    private boolean multipleChoice;
    private boolean anonymous;
    private PollStatus status;
    private Instant expiresAt;
    private Instant closedAt;
    private long totalVotes;
    private List<UUID> myOptionIds;
    private List<PollOptionResponse> options;
    private Instant createdAt;
    private Instant updatedAt;
}
