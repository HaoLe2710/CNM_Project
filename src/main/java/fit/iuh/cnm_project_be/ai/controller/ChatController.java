package fit.iuh.cnm_project_be.ai.controller;


import fit.iuh.cnm_project_be.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final AiService aiService;

    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return aiService.ask(message);
    }

}
