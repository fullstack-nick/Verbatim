package io.verbatim.review;

import java.util.Map;
import java.util.UUID;

public final class ReviewModels {

    private ReviewModels() {
    }

    public record Finding(
        String code,
        String severity,
        String message,
        Integer pageNumber,
        UUID segmentId,
        Map<String, Object> metadata
    ) {
        public Finding(String code, String severity, String message, Integer pageNumber, UUID segmentId) {
            this(code, severity, message, pageNumber, segmentId, Map.of());
        }
    }
}
