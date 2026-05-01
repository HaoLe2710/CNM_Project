package fit.iuh.cnm_project_be.group_call.dto;

import fit.iuh.cnm_project_be.group_call.enums.GroupCallType;

import java.util.UUID;

public record InitiateGroupCallRequest(
    UUID conversationId,
    GroupCallType type
) {}
