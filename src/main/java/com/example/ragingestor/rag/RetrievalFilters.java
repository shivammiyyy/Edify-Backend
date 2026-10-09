package com.example.ragingestor.rag;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public record RetrievalFilters(String department, String documentType, List<String> tags, UUID ownerId) {

    private static final Pattern SAFE_TAG = Pattern.compile("[a-zA-Z0-9 _-]{1,64}");

    public RetrievalFilters {
        department = clean(department);
        documentType = clean(documentType);
        tags = tags == null ? List.of() : tags.stream().map(String::trim).filter(tag -> !tag.isBlank())
                .map(String::toLowerCase).distinct().toList();
        if (tags.stream().anyMatch(tag -> !SAFE_TAG.matcher(tag).matches())) {
            throw new IllegalArgumentException("Tags may contain letters, numbers, spaces, underscores, and hyphens only");
        }
    }

    public static RetrievalFilters empty() {
        return new RetrievalFilters(null, null, List.of(), null);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
