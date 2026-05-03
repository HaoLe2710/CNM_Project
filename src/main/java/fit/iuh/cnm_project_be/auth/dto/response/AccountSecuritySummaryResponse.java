package fit.iuh.cnm_project_be.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AccountSecuritySummaryResponse {
    private int issueCount;
    private boolean hasIssues;
    private String securityStrength;
    private String email;
    private String phone;
    private Map<String, Object> accountSecurity;
    private ZaloLockSettingsResponse zaloLock;
    private List<SecurityCheckResponse> checks;
    private List<SecurityIssueResponse> issues;
}
