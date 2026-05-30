package fit.iuh.cnm_project_be.cloud.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CloudFilePageResponse {
    private List<CloudFileResponse> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private boolean hasMore;
}
