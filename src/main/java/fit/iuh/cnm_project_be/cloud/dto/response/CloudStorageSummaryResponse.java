package fit.iuh.cnm_project_be.cloud.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CloudStorageSummaryResponse {
    private long totalBytes;
    private long activeBytes;
    private long trashBytes;
    private long usedBytes;
    private long quotaBytes;
    private long remainingBytes;
    private double usagePercent;
    private long totalFiles;
    private long totalFolders;
    private Map<String, Long> byType;
}
