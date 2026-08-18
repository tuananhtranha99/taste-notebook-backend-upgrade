package com.tastenotebook.web;

import com.tastenotebook.dto.SuggestionResult;
import com.tastenotebook.service.FavoriteSuggestionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends/{friendId}/suggest")
public class SuggestController {

    private final FavoriteSuggestionService favoriteSuggestionService;

    public SuggestController(FavoriteSuggestionService favoriteSuggestionService) {
        this.favoriteSuggestionService = favoriteSuggestionService;
    }

    @PostMapping
    public SuggestionResult suggest(@PathVariable Long friendId) {
        return favoriteSuggestionService.suggest(friendId);
    }
}
