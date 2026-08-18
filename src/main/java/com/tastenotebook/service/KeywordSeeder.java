package com.tastenotebook.service;

import com.tastenotebook.domain.Language;
import com.tastenotebook.domain.PhraseKeyword;
import com.tastenotebook.domain.Sentiment;
import com.tastenotebook.repository.PhraseKeywordRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Populates a sensible default set of like/dislike phrases (Vietnamese +
 * English), each with an intensity from 1 (mild) to 5 (extreme), the first
 * time the app runs against an empty database. After that, manage phrases
 * via the /api/keywords endpoints (or the Settings tab in the frontend)
 * instead of editing code — that's the whole point of storing them in the DB.
 */
@Component
public class KeywordSeeder implements CommandLineRunner {

    private final PhraseKeywordRepository repo;

    public KeywordSeeder(PhraseKeywordRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;

        List<PhraseKeyword> defaults = List.of(
                // --- Vietnamese: dislike (priority checked before shorter forms) ---
                new PhraseKeyword("không thích", Language.VI, Sentiment.DISLIKE, 20, 3),
                new PhraseKeyword("không hề thích", Language.VI, Sentiment.DISLIKE, 21, 3),
                new PhraseKeyword("chẳng thích", Language.VI, Sentiment.DISLIKE, 20, 3),
                new PhraseKeyword("không ưa", Language.VI, Sentiment.DISLIKE, 15, 3),
                new PhraseKeyword("ghét", Language.VI, Sentiment.DISLIKE, 10, 4),
                new PhraseKeyword("rất ghét", Language.VI, Sentiment.DISLIKE, 20, 5),
                new PhraseKeyword("cực ghét", Language.VI, Sentiment.DISLIKE, 20, 5),
                new PhraseKeyword("không thích ăn", Language.VI, Sentiment.DISLIKE, 22, 3),
                new PhraseKeyword("dị ứng", Language.VI, Sentiment.DISLIKE, 10, 5),
                new PhraseKeyword("sợ ăn", Language.VI, Sentiment.DISLIKE, 10, 4),

                // --- Vietnamese: like ---
                new PhraseKeyword("thích", Language.VI, Sentiment.LIKE, 5, 3),
                new PhraseKeyword("khá thích", Language.VI, Sentiment.LIKE, 20, 3),
                new PhraseKeyword("thích ăn", Language.VI, Sentiment.LIKE, 12, 3),
                new PhraseKeyword("rất thích", Language.VI, Sentiment.LIKE, 20, 4),
                new PhraseKeyword("cực thích", Language.VI, Sentiment.LIKE, 20, 5),
                new PhraseKeyword("yêu thích", Language.VI, Sentiment.LIKE, 15, 4),
                new PhraseKeyword("khoái", Language.VI, Sentiment.LIKE, 10, 3),
                new PhraseKeyword("mê", Language.VI, Sentiment.LIKE, 10, 5),
                new PhraseKeyword("mê mẩn", Language.VI, Sentiment.LIKE, 15, 5),

                // --- English: dislike ---
                new PhraseKeyword("don't like", Language.EN, Sentiment.DISLIKE, 20, 3),
                new PhraseKeyword("do not like", Language.EN, Sentiment.DISLIKE, 20, 3),
                new PhraseKeyword("doesn't like", Language.EN, Sentiment.DISLIKE, 20, 3),
                new PhraseKeyword("really don't like", Language.EN, Sentiment.DISLIKE, 22, 4),
                new PhraseKeyword("not a fan of", Language.EN, Sentiment.DISLIKE, 20, 3),
                new PhraseKeyword("can't stand", Language.EN, Sentiment.DISLIKE, 20, 4),
                new PhraseKeyword("hate", Language.EN, Sentiment.DISLIKE, 10, 4),
                new PhraseKeyword("hates", Language.EN, Sentiment.DISLIKE, 10, 4),
                new PhraseKeyword("dislike", Language.EN, Sentiment.DISLIKE, 10, 3),
                new PhraseKeyword("dislikes", Language.EN, Sentiment.DISLIKE, 10, 3),
                new PhraseKeyword("allergic to", Language.EN, Sentiment.DISLIKE, 15, 5),

                // --- English: like ---
                new PhraseKeyword("like", Language.EN, Sentiment.LIKE, 5, 3),
                new PhraseKeyword("likes", Language.EN, Sentiment.LIKE, 5, 3),
                new PhraseKeyword("really like", Language.EN, Sentiment.LIKE, 20, 4),
                new PhraseKeyword("really likes", Language.EN, Sentiment.LIKE, 20, 4),
                new PhraseKeyword("fan of", Language.EN, Sentiment.LIKE, 12, 4),
                new PhraseKeyword("enjoy", Language.EN, Sentiment.LIKE, 10, 3),
                new PhraseKeyword("enjoys", Language.EN, Sentiment.LIKE, 10, 3),
                new PhraseKeyword("love", Language.EN, Sentiment.LIKE, 10, 5),
                new PhraseKeyword("loves", Language.EN, Sentiment.LIKE, 10, 5)
        );

        repo.saveAll(defaults);
    }
}
