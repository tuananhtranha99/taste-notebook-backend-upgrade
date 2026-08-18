package com.tastenotebook.dto;

public class SuggestionResult {
    public String dish;
    public String reason;
    public boolean feasible; // false if there wasn't enough sensible data to combine

    public SuggestionResult() {}
    public SuggestionResult(String dish, String reason, boolean feasible) {
        this.dish = dish;
        this.reason = reason;
        this.feasible = feasible;
    }
}
