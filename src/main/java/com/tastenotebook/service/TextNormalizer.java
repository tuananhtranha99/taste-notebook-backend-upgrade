package com.tastenotebook.service;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class TextNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private TextNormalizer() {}

    /** lowercase, trim, strip accents — used to compare items/phrases regardless of casing or diacritics. */
    public static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase().trim();
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String stripped = DIACRITICS.matcher(decomposed).replaceAll("");
        return stripped.replace('đ', 'd').replaceAll("\\s+", " ");
    }
}
