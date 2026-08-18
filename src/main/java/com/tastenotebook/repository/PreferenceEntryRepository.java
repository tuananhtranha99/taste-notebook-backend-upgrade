package com.tastenotebook.repository;

import com.tastenotebook.domain.Category;
import com.tastenotebook.domain.PreferenceEntry;
import com.tastenotebook.domain.Sentiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreferenceEntryRepository extends JpaRepository<PreferenceEntry, Long> {
    List<PreferenceEntry> findByFriendIdOrderByCreatedAtAsc(Long friendId);
    List<PreferenceEntry> findByFriendIdAndCategoryOrderByCreatedAtAsc(Long friendId, Category category);
    List<PreferenceEntry> findByFriendIdAndCategoryAndSentimentOrderByCreatedAtAsc(
            Long friendId, Category category, Sentiment sentiment);
    void deleteByFriendId(Long friendId);
}
