package com.tastenotebook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tastenotebook.domain.Category;
import com.tastenotebook.domain.PreferenceEntry;
import com.tastenotebook.domain.Sentiment;
import com.tastenotebook.dto.TasteCheckResult;
import com.tastenotebook.repository.PreferenceEntryRepository;
import org.springframework.stereotype.Service;

@Service
public class TasteCheckService {

    private final PreferenceEntryRepository preferenceEntryRepository;
    private final AiClient aiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public TasteCheckService(PreferenceEntryRepository preferenceEntryRepository, AiClient aiClient) {
        this.preferenceEntryRepository = preferenceEntryRepository;
        this.aiClient = aiClient;
    }

    public TasteCheckResult check(Long friendId, String dish) {
        // Only FOOD entries are relevant for a dish match check.
        String likes = joinItems(friendId, Sentiment.LIKE);
        String dislikes = joinItems(friendId, Sentiment.DISLIKE);

        String prompt = """
                Bạn giúp đánh giá xem một món ăn mới có hợp khẩu vị một người hay không,
                dựa trên danh sách món họ đã từng thích và không thích.

                Món họ THÍCH: %s
                Món họ KHÔNG THÍCH: %s

                Món cần kiểm tra: "%s"

                Hãy suy luận dựa trên nguyên liệu, hương vị, kết cấu, phong cách món ăn
                (không chỉ so khớp tên chính xác). Ví dụ nếu họ không thích "rau mùi" thì
                một món có chứa rau mùi trong tên/thành phần cũng nên bị đánh giá là không hợp.

                Chỉ trả về JSON thuần, đúng định dạng:
                {"verdict": "phù hợp" | "không phù hợp" | "không chắc", "reason": "<giải thích ngắn gọn 1-2 câu bằng tiếng Việt>"}
                """.formatted(
                    likes.isBlank() ? "(chưa có dữ liệu)" : likes,
                    dislikes.isBlank() ? "(chưa có dữ liệu)" : dislikes,
                    dish
                );

        try {
            String raw = aiClient.generate(prompt);
            return mapper.readValue(raw, TasteCheckResult.class);
        } catch (Exception e) {
            return new TasteCheckResult("không chắc", "Không thể kiểm tra lúc này (" + e.getMessage() + ")");
        }
    }

    private String joinItems(Long friendId, Sentiment sentiment) {
        return preferenceEntryRepository
                .findByFriendIdAndCategoryAndSentimentOrderByCreatedAtAsc(friendId, Category.FOOD, sentiment)
                .stream()
                .map(PreferenceEntry::getItem)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
