package fit.iuh.cnm_project_be.admin.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class AdminDashboardSummaryResponse {
    private long totalUsers;
    private long activeUsers;
    private long bannedUsers;
    private long totalPosts;
    private long totalPublicVideos;
    private long pendingReports;
    private long reportsLast24h;
    private List<AdminAlertResponse> alerts;
    private Instant generatedAt;
}

