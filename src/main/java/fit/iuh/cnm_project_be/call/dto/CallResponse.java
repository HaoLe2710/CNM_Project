package fit.iuh.cnm_project_be.call.dto;

import java.util.UUID;

public record CallResponse(
    UUID callId,
    String channel,
    String sfuUrl
) {}