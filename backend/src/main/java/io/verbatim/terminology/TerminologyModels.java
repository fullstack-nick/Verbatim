package io.verbatim.terminology;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public final class TerminologyModels {

    private TerminologyModels() {
    }

    public record TranslationInput(
        @NotBlank String locale,
        @NotBlank String text,
        @NotBlank String usage
    ) {
    }

    public record CreateTermRequest(
        @NotBlank String sourceLocale,
        @NotBlank String sourceTerm,
        @NotBlank String matchingType,
        @NotBlank String caseMode,
        @NotBlank String translationPreference,
        @NotEmpty List<@Valid TranslationInput> translations
    ) {
    }

    public record TranslationView(UUID id, String locale, String text, String usage) {
    }

    public record TermView(
        UUID id,
        String sourceLocale,
        String sourceTerm,
        String matchingType,
        String caseMode,
        String translationPreference,
        List<TranslationView> translations
    ) {
    }
}
