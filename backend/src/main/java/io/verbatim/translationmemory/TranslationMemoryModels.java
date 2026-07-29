package io.verbatim.translationmemory;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TranslationMemoryModels {

    private TranslationMemoryModels() {
    }

    public record CreateMemoryRequest(
        @NotBlank String sourceLocale,
        @NotBlank String targetLocale,
        @NotBlank String sourceText,
        @NotBlank String targetText
    ) {
    }

    public record MemoryView(
        UUID id,
        String sourceLocale,
        String targetLocale,
        String sourceText,
        String targetText,
        OffsetDateTime approvedAt
    ) {
    }

    public record MemorySuggestion(
        UUID id,
        String sourceText,
        String targetText,
        double similarity
    ) {
    }
}
