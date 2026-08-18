package com.tastenotebook.dto;

import com.tastenotebook.domain.Sentiment;

/** Result of rule-based text parsing. matched=false means no keyword phrase was found. */
public class ParseResult {
    private boolean matched;
    private Sentiment sentiment;
    private String item;
    private String matchedPhrase;
    private int intensity = 3;

    public static ParseResult noMatch() {
        ParseResult r = new ParseResult();
        r.matched = false;
        return r;
    }

    public static ParseResult of(Sentiment sentiment, String item, String matchedPhrase, int intensity) {
        ParseResult r = new ParseResult();
        r.matched = true;
        r.sentiment = sentiment;
        r.item = item;
        r.matchedPhrase = matchedPhrase;
        r.intensity = intensity;
        return r;
    }

    public boolean isMatched() { return matched; }
    public Sentiment getSentiment() { return sentiment; }
    public String getItem() { return item; }
    public String getMatchedPhrase() { return matchedPhrase; }
    public int getIntensity() { return intensity; }
}
