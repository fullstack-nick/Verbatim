package io.verbatim.translation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TranslationClient {

    TranslationResult translate(TranslationContext context);

    record SourceSegment(UUID id, String text) {
    }

    record TranslationContext(
        UUID documentId,
        String sourceLocale,
        String targetLocale,
        Map<String, Object> projectRules,
        List<Map<String, Object>> terminology,
        List<Map<String, Object>> translationMemory,
        List<SourceSegment> segments,
        List<String> documentInstructions
    ) {
    }

    record TargetSegment(UUID id, String text) {
    }

    record Usage(long inputTokens, long cachedInputTokens, long outputTokens, long reasoningTokens) {
        public static Usage empty() {
            return new Usage(0, 0, 0, 0);
        }
    }

    record TranslationResult(
        List<TargetSegment> translations,
        Usage usage,
        String providerThreadId,
        String provider,
        boolean fallbackUsed,
        long durationMillis
    ) {
    }
}
