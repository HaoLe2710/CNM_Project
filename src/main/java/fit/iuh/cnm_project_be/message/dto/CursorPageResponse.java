package fit.iuh.cnm_project_be.message.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CursorPageResponse<T> {

    private List<T> items;
    private String nextCursor;
    private boolean hasMore;
}
