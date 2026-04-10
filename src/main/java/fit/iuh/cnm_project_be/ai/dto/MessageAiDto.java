package fit.iuh.cnm_project_be.ai.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageAiDto {
    private String sender;
    private String content;
}