package com.tastenotebook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tastenotebook.domain.Category;
import com.tastenotebook.domain.PreferenceEntry;
import com.tastenotebook.domain.Sentiment;
import com.tastenotebook.dto.SuggestionResult;
import com.tastenotebook.repository.PreferenceEntryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FavoriteSuggestionService {

    private final PreferenceEntryRepository preferenceEntryRepository;
    private final AiClient aiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public FavoriteSuggestionService(PreferenceEntryRepository preferenceEntryRepository, AiClient aiClient) {
        this.preferenceEntryRepository = preferenceEntryRepository;
        this.aiClient = aiClient;
    }

    public SuggestionResult suggest(Long friendId) {
        // Only FOOD likes are combined into a dish suggestion.
        List<PreferenceEntry> likeEntries = preferenceEntryRepository
                .findByFriendIdAndCategoryAndSentimentOrderByCreatedAtAsc(friendId, Category.FOOD, Sentiment.LIKE);
        String likes = likeEntries.stream().map(PreferenceEntry::getItem).collect(Collectors.joining(", "));

        if (likes.isBlank()) {
            return new SuggestionResult(null, "Chưa có món nào trong danh sách thích để gợi ý.", false);
        }

        String prompt = """
                Bạn là một đầu bếp sáng tạo nhưng thực tế. Dưới đây là danh sách các món / nguyên liệu
                mà một người YÊU THÍCH: %s

                Nhiệm vụ: đề xuất MỘT món ăn cụ thể, có thật và hợp lý trên thực tế, kết hợp một phần
                hoặc toàn bộ các món/nguyên liệu ưa thích ở trên theo cách hài hòa về hương vị.

                Quy tắc quan trọng:
                - CHỈ kết hợp những món/nguyên liệu có thể đi chung với nhau trong ẩm thực thực tế
                  (hương vị, kết cấu, văn hóa ẩm thực phải hợp lý).
                - KHÔNG bắt buộc phải dùng tất cả các món trong danh sách — chỉ chọn những món
                  thật sự hợp nhau. Ví dụ: "sữa chua" + "kiều mạch" + "nha đam" hợp lý vì đều
                  là các món ăn nhẹ/tráng miệng thanh mát; nhưng "sữa chua" + "pizza" thì KHÔNG hợp,
                  không nên ghép.
                - Nếu không tìm được sự kết hợp nào thực sự hợp lý, hãy trả về feasible=false
                  thay vì cố ghép một món kỳ quặc.
                - Tên món đề xuất phải là tên món ăn thật, cụ thể, không mơ hồ.

                Chỉ trả về JSON thuần, đúng định dạng:
                {"dish": "<tên món cụ thể, để rỗng nếu feasible=false>", "reason": "<giải thích ngắn gọn 1-2 câu tại sao món này hợp lý>", "feasible": true|false}
                """.formatted(likes);

        try {
            String raw = aiClient.generate(prompt);
            return mapper.readValue(raw, SuggestionResult.class);
        } catch (Exception e) {
            return new SuggestionResult(null, "Không thể tạo gợi ý lúc này (" + e.getMessage() + ")", false);
        }
    }
}
