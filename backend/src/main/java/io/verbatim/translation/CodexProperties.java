package io.verbatim.translation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "verbatim.codex")
public record CodexProperties(
    String executable,
    boolean enabled,
    boolean allowFallback,
    int timeoutSeconds,
    String translationSchema,
    String ocrSchema,
    String visualReviewSchema,
    boolean visualReviewEnabled,
    int visualReviewMaxPages
) {
}
