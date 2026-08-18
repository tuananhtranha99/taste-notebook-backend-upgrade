package com.tastenotebook.dto;

import com.tastenotebook.domain.Language;
import com.tastenotebook.domain.PhraseKeyword;
import com.tastenotebook.domain.Sentiment;

public class KeywordResponse {
    public Long id;
    public String phrase;
    public Language language;
    public Sentiment sentiment;
    public Integer priority;
    public Integer intensity;

    public KeywordResponse() {}

    public static KeywordResponse from(PhraseKeyword k) {
        KeywordResponse r = new KeywordResponse();
        r.id = k.getId();
        r.phrase = k.getPhrase();
        r.language = k.getLanguage();
        r.sentiment = k.getSentiment();
        r.priority = k.getPriority();
        r.intensity = k.getIntensity();
        return r;
    }
}
