package fit.iuh.cnm_project_be.cloud.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class CloudFileAnalysisResponse {
    private UUID fileId;
    private String mediaUrl;
    private Instant timeCaptured;
    private String uploadStatus;
    private String fileSizeLabel;
    private String transmissionStatus;
    private String analysisStatus;
    private String detectedDisease;
    private String severityLevel;
    private Map<String, Object> rawAnalysis;
    private Instant createdAt;
    private Instant updatedAt;
}
