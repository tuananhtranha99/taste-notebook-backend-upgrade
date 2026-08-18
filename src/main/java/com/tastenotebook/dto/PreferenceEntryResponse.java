package com.tastenotebook.dto;

import com.tastenotebook.domain.Category;
import com.tastenotebook.domain.PreferenceEntry;
import com.tastenotebook.domain.Sentiment;
import java.time.Instant;

public class PreferenceEntryResponse {
    public Long id;
    public Long friendId;
    public Category category;
    public String item;
    public Sentiment sentiment;
    public Integer intensity;
    public String rawText;
    public Instant createdAt;
    public boolean duplicate; // true when this endpoint returned an already-existing entry

    public PreferenceEntryResponse() {}

    public static PreferenceEntryResponse from(PreferenceEntry e) {
        return from(e, false);
    }

    public static PreferenceEntryResponse from(PreferenceEntry e, boolean duplicate) {
        PreferenceEntryResponse r = new PreferenceEntryResponse();
        r.id = e.getId();
        r.friendId = e.getFriend().getId();
        r.category = e.getCategory();
        r.item = e.getItem();
        r.sentiment = e.getSentiment();
        r.intensity = e.getIntensity();
        r.rawText = e.getRawText();
        r.createdAt = e.getCreatedAt();
        r.duplicate = duplicate;
        return r;
    }
}
