package com.tastenotebook.dto;

import jakarta.validation.constraints.NotBlank;

public class Requests {

    public static class NewFriendRequest {
        @NotBlank
        public String name;
    }

    public static class NewEntryRequest {
        @NotBlank
        public String text;
        /** "FOOD" | "GIFT" | "ACTIVITY" | "OTHER" — defaults to FOOD if omitted. */
        public String category;
    }

    public static class CheckDishRequest {
        @NotBlank
        public String dish;
    }

    public static class NewKeywordRequest {
        @NotBlank
        public String phrase;
        @NotBlank
        public String language;   // "VI" | "EN"
        @NotBlank
        public String sentiment;  // "LIKE" | "DISLIKE"
        public Integer priority = 0;
        public Integer intensity = 3; // 1-5 stars
    }
}
