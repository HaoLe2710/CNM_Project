package fit.iuh.cnm_project_be.ai.tool;

import fit.iuh.cnm_project_be.ai.dto.MessageAiDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatSummaryTool {

    @Tool(description = "Summarize unread chat messages")
    public String summarizeMessages(List<MessageAiDto> messages) {

        StringBuilder input = new StringBuilder();

        for (MessageAiDto m : messages) {
            input.append(m.getSender())
                 .append(": ")
                 .append(m.getContent())
                 .append("\n");
        }

        return input.toString();
    }
}