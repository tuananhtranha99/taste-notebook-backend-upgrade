package com.tastenotebook.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A single preference note for a friend: a food they like/dislike, or a
 * non-food note (gift idea, activity they enjoy, etc.), with an intensity
 * (1-5 stars) derived from the phrase that was matched when it was parsed.
 */
@Entity
@Table(name = "preference_entries")
public class PreferenceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private Friend friend;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category = Category.FOOD;

    @Column(nullable = false)
    private String item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sentiment sentiment;

    /** 1-5 stars, derived from the matched phrase's configured intensity. */
    @Column(nullable = false)
    private Integer intensity = 3;

    /** The raw text the user originally typed, kept for reference/debugging. */
    @Column(name = "raw_text")
    private String rawText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public PreferenceEntry() {}

    public PreferenceEntry(Friend friend, Category category, String item, Sentiment sentiment,
                            Integer intensity, String rawText) {
        this.friend = friend;
        this.category = category;
        this.item = item;
        this.sentiment = sentiment;
        this.intensity = intensity;
        this.rawText = rawText;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Friend getFriend() { return friend; }
    public void setFriend(Friend friend) { this.friend = friend; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public Sentiment getSentiment() { return sentiment; }
    public void setSentiment(Sentiment sentiment) { this.sentiment = sentiment; }
    public Integer getIntensity() { return intensity; }
    public void setIntensity(Integer intensity) { this.intensity = intensity; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
