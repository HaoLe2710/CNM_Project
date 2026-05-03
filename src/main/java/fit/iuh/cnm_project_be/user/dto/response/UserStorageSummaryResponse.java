package fit.iuh.cnm_project_be.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserStorageSummaryResponse {
    private double zaloDataMb;
    private double cacheMb;
    private double largeFilesMb;
    private double chatDataMb;
    private double otherDataMb;
    private double deviceTotalMb;
    private String note;
}
