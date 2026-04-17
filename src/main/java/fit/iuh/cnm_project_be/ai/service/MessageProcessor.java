package fit.iuh.cnm_project_be.ai.service;

import fit.iuh.cnm_project_be.ai.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MessageProcessor {

    // map DTO → AI DTO
    public List<MessageAiDto> mapToAiDto(List<MessageDto> messages) {
        return messages.stream()
                .map(m -> new MessageAiDto(
                        m.getSenderName(),
                        m.getContent()
                ))
                .toList();
    }

    // group theo sender
    public List<MessageAiDto> groupBySender(List<MessageAiDto> messages) {

        Map<String, StringBuilder> map = new LinkedHashMap<>();

        for (MessageAiDto m : messages) {

            map.putIfAbsent(m.getSender(), new StringBuilder());

            map.get(m.getSender())
                    .append(m.getContent())
                    .append(" ");
        }

        return map.entrySet()
                .stream()
                .map(e -> new MessageAiDto(
                        e.getKey(),
                        e.getValue().toString()
                ))
                .toList();
    }
}