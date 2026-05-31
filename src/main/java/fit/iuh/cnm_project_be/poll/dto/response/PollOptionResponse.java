package fit.iuh.cnm_project_be.poll.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PollOptionResponse {
    private UUID id;
    private String text;
    private int position;
    private long voteCount;
    private boolean votedByMe;
    private List<UUID> voterIds;
}
