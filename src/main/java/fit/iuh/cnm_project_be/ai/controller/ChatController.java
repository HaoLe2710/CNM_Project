package fit.iuh.cnm_project_be.ai.controller;

import fit.iuh.cnm_project_be.ai.service.AiService;
import fit.iuh.cnm_project_be.ai.service.ChatSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AiService aiService;

    private final ChatSummaryService summaryService;

    @PostMapping("")
    public String chat(@RequestBody String message) {
        return aiService.ask(message);
    }

    @GetMapping("/summary/{conversationId}")
    public String getSummary(
            @PathVariable UUID conversationId,
            @RequestParam UUID userId) {

        return summaryService.getUnreadSummary(conversationId, userId);
    }
}
