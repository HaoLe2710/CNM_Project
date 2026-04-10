package fit.iuh.cnm_project_be.ai.service;

import fit.iuh.cnm_project_be.ai.dto.*;
import fit.iuh.cnm_project_be.ai.repository.AiMessageRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatSummaryService {

    private final AiMessageRepository aiMessageRepository;
    private final MessageProcessor processor;
    private final AiSummaryService aiService;

    public ChatSummaryService(AiMessageRepository aiMessageRepository,
                              MessageProcessor processor,
                              AiSummaryService aiService) {
        this.aiMessageRepository = aiMessageRepository;
        this.processor = processor;
        this.aiService = aiService;
    }

    public String getUnreadSummary(UUID conversationId, UUID userId) {
        // 1. Đếm số lượng tin nhắn chưa đọc
        int unreadCount = aiMessageRepository.countUnreadMessages(conversationId, userId);

        // TRƯỜNG HỢP 0: Không có tin nhắn nào chưa đọc
        if (unreadCount == 0) {
            return "Bạn không có tin nhắn chưa đọc nào trong cuộc hội thoại này.";
        }

        // TRƯỜNG HỢP 1: Có từ 1 đến 5 tin nhắn (Hiển thị tin nhắn cuối cùng)
        if (unreadCount < 5) {
            MessageDto lastMessage = aiMessageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId, userId);

            // Kiểm tra an toàn (null-safe)
            if (lastMessage == null) {
                return "Có " + unreadCount + " tin nhắn chưa đọc, nhưng không thể tải nội dung chi tiết.";
            }

            return String.format("%s: %s", lastMessage.getSenderName(), lastMessage.getContent());
        }

        // TRƯỜNG HỢP 2: Trên 5 tin nhắn (Sử dụng AI để tóm tắt)
        // Lấy tối đa 20 tin nhắn gần nhất để tóm tắt
        List<MessageDto> messages = aiMessageRepository.findRecentUnreadMessages(conversationId, userId, 20);

        if (messages == null || messages.isEmpty()) {
            return "Bạn có " + unreadCount + " tin nhắn mới nhưng hệ thống gặp lỗi khi truy xuất dữ liệu.";
        }

        // Đảo ngược danh sách để tin nhắn cũ ở trên, tin mới ở dưới (đúng trình tự hội thoại)
        Collections.reverse(messages);

        // Pipeline xử lý dữ liệu cho AI
        List<MessageAiDto> aiDtos = processor.mapToAiDto(messages);
        aiDtos = processor.groupBySender(aiDtos); // Gộp tin nhắn của cùng một người gửi cho ngắn gọn

        // Chuyển danh sách thành một chuỗi văn bản duy nhất (Dùng joining sẽ sạch hơn reduce)
        String formattedContent = aiDtos.stream()
                .map(m -> m.getSender() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        // Gọi AI để thực hiện tóm tắt nội dung
        return aiService.summarize(formattedContent);
    }
}