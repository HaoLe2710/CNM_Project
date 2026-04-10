package fit.iuh.cnm_project_be.call.dto;

import fit.iuh.cnm_project_be.call.enums.CallType;

import java.util.UUID;

public record InitiateCallRequest(
        UUID calleeId,
        CallType type    // VOICE hoặc VIDEO
) {}