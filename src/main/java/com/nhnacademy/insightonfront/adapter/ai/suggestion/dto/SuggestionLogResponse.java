package com.nhnacademy.insightonfront.adapter.ai.suggestion.dto;

import java.time.OffsetDateTime;

public record SuggestionLogResponse(
        Long suggestionLogId,
        Long groupId,
        Long locationId,
        String title,
        String suggestionText,
        Boolean isAccepted,
        String actionPayload,
        OffsetDateTime createdAt
) {
}
