package fit.iuh.cnm_project_be.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiSummaryService {

    private final ChatClient chatClient;

    public AiSummaryService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String summarize(String content) {
        return chatClient.prompt()
                // 1. Thiết lập vai diễn và phong cách (System Message)
                .system("""
                    Bạn là trợ lý ảo thân thiện. Nhiệm vụ của bạn là tóm tắt tin nhắn chat chưa đọc.
                    
                    NGUYÊN TẮC:
                    1. Ngôn ngữ: Tự nhiên, như một người bạn đang kể lại câu chuyện.
                    2. Tập trung vào 'Ý định' và 'Hành động' thay vì 'Số lượng tin nhắn'.
                    3. Xưng hô bằng tên riêng của người gửi để tạo cảm giác gần gũi.
                    
                    VÍ DỤ:
                    - Thay vì: 'Anh Nam gửi 2 tin nhắn hỏi về báo cáo.'
                    - Hãy viết: 'Anh Nam đang hối bạn gửi bản báo cáo gấp để kịp họp chiều nay đó.'
                    
                    - Thay vì: 'Bạn Lan nhắn tin về việc đi du lịch Đà Lạt.'
                    - Hãy viết: 'Lan đang rất hào hứng rủ bạn đi Đà Lạt cuối tháng này và đang đợi bạn chốt lịch.'
                """)
                // 2. Đưa dữ liệu thực tế vào (User Message)
                .user(u -> u.text("Đây là các tin nhắn chưa đọc:\n {content}")
                        .param("content", content))
                .call()
                .content();
    }
}