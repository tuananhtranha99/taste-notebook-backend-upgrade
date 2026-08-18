package com.tastenotebook.web;

import com.tastenotebook.domain.Language;
import com.tastenotebook.domain.PhraseKeyword;
import com.tastenotebook.domain.Sentiment;
import com.tastenotebook.dto.KeywordResponse;
import com.tastenotebook.dto.Requests;
import com.tastenotebook.repository.PhraseKeywordRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Lets you add/remove phrases like "khá thích", "really like", "not into"
 * without redeploying — these are the words the rule-based parser looks for.
 * Also used by the Settings tab in the frontend.
 */
@RestController
@RequestMapping("/api/keywords")
public class KeywordController {

    private final PhraseKeywordRepository phraseKeywordRepository;

    public KeywordController(PhraseKeywordRepository phraseKeywordRepository) {
        this.phraseKeywordRepository = phraseKeywordRepository;
    }

    @GetMapping
    public List<KeywordResponse> list() {
        return phraseKeywordRepository.findAllByOrderByPriorityDesc()
                .stream().map(KeywordResponse::from).toList();
    }

    @PostMapping
    public KeywordResponse create(@Valid @RequestBody Requests.NewKeywordRequest req) {
        try {
            PhraseKeyword kw = new PhraseKeyword(
                    req.phrase.trim(),
                    Language.valueOf(req.language.toUpperCase()),
                    Sentiment.valueOf(req.sentiment.toUpperCase()),
                    req.priority == null ? 0 : req.priority,
                    req.intensity == null ? 3 : Math.max(1, Math.min(5, req.intensity))
            );
            return KeywordResponse.from(phraseKeywordRepository.save(kw));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "language phải là VI/EN, sentiment phải là LIKE/DISLIKE");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!phraseKeywordRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Keyword not found");
        }
        phraseKeywordRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
