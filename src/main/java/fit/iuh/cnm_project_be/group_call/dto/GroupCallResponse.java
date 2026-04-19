package fit.iuh.cnm_project_be.group_call.dto;

import fit.iuh.cnm_project_be.group_call.enums.GroupCallStatus;
import fit.iuh.cnm_project_be.group_call.enums.GroupCallType;

import java.util.UUID;

public record GroupCallResponse(
    UUID groupCallId,
    UUID conversationId,
    String channel,
    String sfuUrl,
    GroupCallStatus status,
    GroupCallType type
) {}
