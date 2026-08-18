package com.tastenotebook.service;

import com.tastenotebook.domain.*;
import com.tastenotebook.dto.ParseResult;
import com.tastenotebook.dto.PreferenceEntryResponse;
import com.tastenotebook.repository.FriendRepository;
import com.tastenotebook.repository.PreferenceEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class PreferenceEntryService {

    private final FriendRepository friendRepository;
    private final PreferenceEntryRepository preferenceEntryRepository;
    private final PhraseParsingService phraseParsingService;

    public PreferenceEntryService(FriendRepository friendRepository,
                                   PreferenceEntryRepository preferenceEntryRepository,
                                   PhraseParsingService phraseParsingService) {
        this.friendRepository = friendRepository;
        this.preferenceEntryRepository = preferenceEntryRepository;
        this.phraseParsingService = phraseParsingService;
    }

    /**
     * Parses free text (VI/EN, rule-based, no AI) and saves it as a preference
     * entry. If an entry with the same (friend, category, sentiment, item —
     * compared accent/case-insensitively) already exists, no new row is
     * inserted; instead the existing row's intensity is updated to the newly
     * parsed value and returned with duplicate=true.
     */
    public PreferenceEntryResponse addFromText(Long friendId, String text, String categoryStr) {
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend not found"));

        Category category = parseCategory(categoryStr);

        ParseResult result = phraseParsingService.parse(text);
        if (!result.isMatched()) {
            throw new UnclearTextException(
                    "Không nhận diện được rõ món ăn/điều gì đó hoặc thái độ thích/không thích.");
        }

        String normalizedNewItem = TextNormalizer.normalize(result.getItem());
        List<PreferenceEntry> existing = preferenceEntryRepository
                .findByFriendIdAndCategoryAndSentimentOrderByCreatedAtAsc(friendId, category, result.getSentiment());

        Optional<PreferenceEntry> duplicate = existing.stream()
                .filter(e -> TextNormalizer.normalize(e.getItem()).equals(normalizedNewItem))
                .findFirst();

        if (duplicate.isPresent()) {
            PreferenceEntry entry = duplicate.get();
            if (!entry.getIntensity().equals(result.getIntensity())) {
                entry.setIntensity(result.getIntensity());
                entry.setRawText(text);
                preferenceEntryRepository.save(entry);
            }
            return PreferenceEntryResponse.from(entry, true);
        }

        PreferenceEntry entry = new PreferenceEntry(
                friend, category, result.getItem(), result.getSentiment(), result.getIntensity(), text);
        preferenceEntryRepository.save(entry);
        return PreferenceEntryResponse.from(entry, false);
    }

    private Category parseCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) return Category.FOOD;
        try {
            return Category.valueOf(categoryStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category: " + categoryStr);
        }
    }
}
