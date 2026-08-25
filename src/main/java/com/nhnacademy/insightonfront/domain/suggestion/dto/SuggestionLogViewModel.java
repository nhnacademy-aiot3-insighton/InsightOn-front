package com.nhnacademy.insightonfront.domain.suggestion.dto;

import java.time.OffsetDateTime;

public record SuggestionLogViewModel(
        Long suggestionLogId,
        String locationName,
        String title,
        String suggestionText,
        Boolean isAccepted,
        String actionSummary,
        OffsetDateTime createdAt
) {
}
