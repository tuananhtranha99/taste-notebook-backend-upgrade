package com.tastenotebook.service;

import com.tastenotebook.domain.PhraseKeyword;
import com.tastenotebook.dto.ParseResult;
import com.tastenotebook.repository.PhraseKeywordRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Detects like/dislike sentiment (and intensity) in free text using a simple,
 * fast substring match against the configurable phrase_keywords table. No
 * AI/network call — this runs in-process so it's effectively instant.
 *
 * Matching is case-insensitive and accent-insensitive for Vietnamese (so
 * "khong thich" / "không thích" / "KHÔNG THÍCH" all match the same entry).
 * Longer, more specific phrases are tried first (by priority, then length)
 * so "rất thích" wins over the shorter "thích".
 */
@Service
public class PhraseParsingService {

    private static final List<String> FILLER_PREFIXES = List.of(
            "món ", "mon ", "the ", "a ", "an ", "cái ", "cai "
    );

    private final PhraseKeywordRepository phraseKeywordRepository;

    public PhraseParsingService(PhraseKeywordRepository phraseKeywordRepository) {
        this.phraseKeywordRepository = phraseKeywordRepository;
    }

    public ParseResult parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ParseResult.noMatch();
        }
        String normalizedText = TextNormalizer.normalize(rawText);

        List<PhraseKeyword> keywords = phraseKeywordRepository.findAllByOrderByPriorityDesc();
        keywords.sort(
                Comparator.comparing((PhraseKeyword k) -> k.getPriority() == null ? 0 : k.getPriority())
                        .thenComparing(k -> TextNormalizer.normalize(k.getPhrase()).length())
                        .reversed()
        );

        for (PhraseKeyword kw : keywords) {
            String normalizedPhrase = TextNormalizer.normalize(kw.getPhrase());
            if (normalizedPhrase.isBlank()) continue;
            int idx = normalizedText.indexOf(normalizedPhrase);
            if (idx >= 0) {
                String item = extractItem(rawText, idx, normalizedPhrase.length());
                if (!item.isBlank()) {
                    int intensity = kw.getIntensity() == null ? 3 : kw.getIntensity();
                    return ParseResult.of(kw.getSentiment(), item, kw.getPhrase(), intensity);
                }
            }
        }
        return ParseResult.noMatch();
    }

    private String extractItem(String rawText, int matchStartInNormalized, int matchedLength) {
        String before = matchStartInNormalized <= rawText.length()
                ? rawText.substring(0, Math.min(matchStartInNormalized, rawText.length())) : "";
        int endIdx = Math.min(matchStartInNormalized + matchedLength, rawText.length());
        String after = rawText.substring(Math.min(endIdx, rawText.length()));

        String item = (before + " " + after)
                .replaceAll("[,.!?;:]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String lowerItem = item.toLowerCase();
        for (String filler : FILLER_PREFIXES) {
            if (lowerItem.startsWith(filler)) {
                item = item.substring(filler.length()).trim();
                lowerItem = item.toLowerCase();
            }
        }
        return item;
    }
}
