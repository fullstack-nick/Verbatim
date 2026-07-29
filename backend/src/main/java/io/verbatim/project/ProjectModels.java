package io.verbatim.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProjectModels {

    private ProjectModels() {
    }

    public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank String defaultSourceLocale,
        @NotBlank String defaultTargetLocale,
        @DecimalMin("0.50") @DecimalMax("1.00") BigDecimal minimumFontScale
    ) {
    }

    public record ProjectView(
        UUID id,
        String name,
        String defaultSourceLocale,
        String defaultTargetLocale,
        int ruleSetVersion,
        BigDecimal minimumFontScale,
        OffsetDateTime createdAt,
        long documentCount
    ) {
    }

    public record RuleInput(
        @NotBlank String type,
        @NotBlank String name,
        String sourceLocale,
        String targetLocale,
        Map<String, Object> value
    ) {
    }

    public record UpdateRulesRequest(
        @NotEmpty List<@Valid RuleInput> rules,
        @DecimalMin("0.50") @DecimalMax("1.00") BigDecimal minimumFontScale
    ) {
    }

    public record RuleView(
        UUID id,
        int version,
        String type,
        String name,
        String sourceLocale,
        String targetLocale,
        Map<String, Object> value
    ) {
    }

    public record RuleSetView(
        UUID projectId,
        int version,
        BigDecimal minimumFontScale,
        List<RuleView> rules
    ) {
    }
}
