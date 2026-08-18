package com.tastenotebook.dto;

public class TasteCheckResult {
    public String verdict;   // "phù hợp" | "không phù hợp" | "không chắc"
    public String reason;

    public TasteCheckResult() {}
    public TasteCheckResult(String verdict, String reason) {
        this.verdict = verdict;
        this.reason = reason;
    }
}
