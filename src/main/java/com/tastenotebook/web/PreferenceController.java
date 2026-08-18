package com.tastenotebook.web;

import com.tastenotebook.domain.Category;
import com.tastenotebook.domain.PreferenceEntry;
import com.tastenotebook.dto.PreferenceEntryResponse;
import com.tastenotebook.dto.Requests;
import com.tastenotebook.repository.PreferenceEntryRepository;
import com.tastenotebook.service.PreferenceEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PreferenceController {

    private final PreferenceEntryRepository preferenceEntryRepository;
    private final PreferenceEntryService preferenceEntryService;

    public PreferenceController(PreferenceEntryRepository preferenceEntryRepository,
                                 PreferenceEntryService preferenceEntryService) {
        this.preferenceEntryRepository = preferenceEntryRepository;
        this.preferenceEntryService = preferenceEntryService;
    }

    @GetMapping("/friends/{friendId}/entries")
    public List<PreferenceEntryResponse> list(@PathVariable Long friendId,
                                               @RequestParam(required = false) String category) {
        List<PreferenceEntry> entries = (category == null || category.isBlank())
                ? preferenceEntryRepository.findByFriendIdOrderByCreatedAtAsc(friendId)
                : preferenceEntryRepository.findByFriendIdAndCategoryOrderByCreatedAtAsc(
                        friendId, Category.valueOf(category.trim().toUpperCase()));
        return entries.stream().map(PreferenceEntryResponse::from).toList();
    }

    /**
     * Parses free text (Vietnamese or English) using the configurable
     * phrase_keywords table — pure string matching, no AI call, so this
     * responds essentially instantly. If the same item already exists for
     * this friend/category/sentiment, no duplicate row is inserted.
     */
    @PostMapping("/friends/{friendId}/entries")
    public PreferenceEntryResponse add(@PathVariable Long friendId, @Valid @RequestBody Requests.NewEntryRequest req) {
        return preferenceEntryService.addFromText(friendId, req.text, req.category);
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable Long entryId) {
        if (!preferenceEntryRepository.existsById(entryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found");
        }
        preferenceEntryRepository.deleteById(entryId);
        return ResponseEntity.noContent().build();
    }
}
