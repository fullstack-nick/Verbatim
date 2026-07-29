package io.verbatim.document;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DocumentModels {

    private DocumentModels() {
    }

    public record DocumentView(
        UUID id,
        UUID projectId,
        String sourceFilename,
        String sourceLocale,
        String targetLocale,
        String state,
        int version,
        Integer pageCount,
        int digitalPageCount,
        int scannedPageCount,
        int mixedPageCount,
        OffsetDateTime createdAt
    ) {
    }

    public record PageView(
        UUID id,
        int pageNumber,
        String pageType,
        BigDecimal width,
        BigDecimal height,
        int rotation,
        String previewUrl,
        BigDecimal ocrConfidence
    ) {
    }

    public record SegmentView(
        UUID id,
        UUID pageId,
        int blockOrder,
        String blockType,
        String sourceText,
        String targetText,
        String status,
        int version,
        BigDecimal confidence
    ) {
    }

    public record DocumentDetail(
        DocumentView document,
        List<PageView> pages,
        List<SegmentView> segments
    ) {
    }
}
