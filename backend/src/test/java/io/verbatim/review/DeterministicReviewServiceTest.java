package io.verbatim.review;

import static org.assertj.core.api.Assertions.assertThat;

import io.verbatim.terminology.TerminologyModels.TermView;
import io.verbatim.terminology.TerminologyModels.TranslationView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicReviewServiceTest {

    private final DeterministicReviewService reviewer = new DeterministicReviewService();

    @Test
    void reportsMissingPlaceholderAndChangedNeverTranslateTerm() {
        TermView lingoHub = new TermView(
            UUID.randomUUID(),
            "en-US",
            "LingoHub",
            "EXACT",
            "SENSITIVE",
            "NEVER_TRANSLATE",
            List.of(new TranslationView(UUID.randomUUID(), "de-DE", "LingoHub", "PREFERRED"))
        );

        var findings = reviewer.review(
            UUID.randomUUID(),
            1,
            "Welcome to LingoHub, %{username}.",
            "Willkommen bei Lingohub.",
            "de-DE",
            List.of(lingoHub)
        );

        assertThat(findings)
            .extracting(ReviewModels.Finding::code)
            .containsExactlyInAnyOrder("MISSING_PLACEHOLDER", "NEVER_TRANSLATE_TERM_CHANGED");
    }

    @Test
    void acceptsAdmittedTermButRejectsObsoleteOne() {
        TermView settings = new TermView(
            UUID.randomUUID(),
            "en-US",
            "Settings",
            "EXACT",
            "INSENSITIVE",
            "TRANSLATE",
            List.of(
                new TranslationView(UUID.randomUUID(), "de-DE", "Einstellungen", "PREFERRED"),
                new TranslationView(UUID.randomUUID(), "de-DE", "Konfiguration", "ADMITTED"),
                new TranslationView(UUID.randomUUID(), "de-DE", "Setup", "OBSOLETE")
            )
        );

        var admitted = reviewer.review(
            UUID.randomUUID(),
            1,
            "Open Settings.",
            "Öffne die Konfiguration.",
            "de-DE",
            List.of(settings)
        );
        var obsolete = reviewer.review(
            UUID.randomUUID(),
            1,
            "Open Settings.",
            "Öffne das Setup.",
            "de-DE",
            List.of(settings)
        );

        assertThat(admitted).isEmpty();
        assertThat(obsolete)
            .extracting(ReviewModels.Finding::code)
            .containsExactly("OBSOLETE_TERM");
    }
}
