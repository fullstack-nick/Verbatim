package io.verbatim.translation;

import io.verbatim.translation.TranslationClient.SourceSegment;
import io.verbatim.translation.TranslationClient.TargetSegment;
import io.verbatim.translation.TranslationClient.TranslationContext;
import io.verbatim.translation.TranslationClient.TranslationResult;
import io.verbatim.translation.TranslationClient.Usage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TranslationGateway implements TranslationClient {

    private static final Logger log = LoggerFactory.getLogger(TranslationGateway.class);

    private final CodexCliTranslationClient codex;
    private final CodexProperties properties;

    public TranslationGateway(CodexCliTranslationClient codex, CodexProperties properties) {
        this.codex = codex;
        this.properties = properties;
    }

    @Override
    public TranslationResult translate(TranslationContext context) {
        try {
            return codex.translate(context);
        } catch (RuntimeException failure) {
            if (!properties.allowFallback()) {
                throw failure;
            }
            log.warn("Codex translation unavailable; using visible demo fallback", failure);
            List<TargetSegment> targets = context.segments().stream()
                .map(this::fallback)
                .toList();
            return new TranslationResult(
                targets,
                Usage.empty(),
                null,
                "DEMO_FALLBACK",
                true,
                0
            );
        }
    }

    private TargetSegment fallback(SourceSegment source) {
        String text = source.text()
            .replace("Welcome to", "Willkommen bei")
            .replace("Open Settings to continue.", "Öffne Einstellungen, um fortzufahren.")
            .replace("A document keeps its shape", "Ein Dokument behält seine Form")
            .replace("Review stages", "Prüfschritte")
            .replace("What drives a translation", "Was eine Übersetzung steuert");
        return new TargetSegment(source.id(), text);
    }
}
