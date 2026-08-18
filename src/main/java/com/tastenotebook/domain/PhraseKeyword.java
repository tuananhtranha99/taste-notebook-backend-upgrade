package com.tastenotebook.domain;

import jakarta.persistence.*;

/**
 * A configurable phrase used to detect sentiment (like/dislike) in free text,
 * in Vietnamese or English. Stored in the DB so new phrases can be added
 * without redeploying code (see PhraseKeywordController for CRUD).
 *
 * Matching is plain substring matching (case-insensitive, accent-insensitive
 * for Vietnamese) — no AI involved, so it's fast.
 */
@Entity
@Table(name = "phrase_keywords")
public class PhraseKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The phrase to look for, e.g. "không thích", "really like", "ghét". */
    @Column(nullable = false)
    private String phrase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sentiment sentiment;

    /**
     * Longer / more specific phrases should be matched first so "rất thích"
     * doesn't get shadowed by a shorter "thích" entry. Higher = checked first.
     * If left null, phrase length is used automatically as a tiebreaker.
     */
    @Column(nullable = false)
    private Integer priority = 0;

    /** How strong this phrase is, 1 (mild) to 5 (extreme), e.g. "ghét"=4, "dị ứng"=5. */
    @Column(nullable = false)
    private Integer intensity = 3;

    public PhraseKeyword() {}

    public PhraseKeyword(String phrase, Language language, Sentiment sentiment, Integer priority) {
        this.phrase = phrase;
        this.language = language;
        this.sentiment = sentiment;
        this.priority = priority;
    }

    public PhraseKeyword(String phrase, Language language, Sentiment sentiment, Integer priority, Integer intensity) {
        this.phrase = phrase;
        this.language = language;
        this.sentiment = sentiment;
        this.priority = priority;
        this.intensity = intensity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhrase() { return phrase; }
    public void setPhrase(String phrase) { this.phrase = phrase; }
    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }
    public Sentiment getSentiment() { return sentiment; }
    public void setSentiment(Sentiment sentiment) { this.sentiment = sentiment; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getIntensity() { return intensity; }
    public void setIntensity(Integer intensity) { this.intensity = intensity; }
}
