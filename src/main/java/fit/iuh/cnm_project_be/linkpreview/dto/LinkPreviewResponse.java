package fit.iuh.cnm_project_be.linkpreview.dto;

public record LinkPreviewResponse(
        String title,
        String description,
        String image,
        String url,
        String host
) {
}
