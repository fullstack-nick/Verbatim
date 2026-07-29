package io.verbatim.document;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

final class PdfTextBlockExtractor extends PDFTextStripper {

    private final List<TextBlock> blocks = new ArrayList<>();

    PdfTextBlockExtractor() throws IOException {
        setSortByPosition(true);
        setShouldSeparateByBeads(true);
    }

    List<TextBlock> extract(PDDocument document, int pageNumber) throws IOException {
        blocks.clear();
        setStartPage(pageNumber);
        setEndPage(pageNumber);
        getText(document);
        return List.copyOf(blocks);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        String cleaned = normalize(text);
        if (cleaned.isBlank() || positions.isEmpty()) {
            return;
        }

        float x = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = 0;
        float bottom = 0;
        float fontSize = 0;
        String fontName = "Source";
        for (TextPosition position : positions) {
            x = Math.min(x, position.getXDirAdj());
            top = Math.min(top, position.getYDirAdj() - position.getHeightDir());
            right = Math.max(right, position.getXDirAdj() + position.getWidthDirAdj());
            bottom = Math.max(bottom, position.getYDirAdj() + 1);
            fontSize = Math.max(fontSize, position.getFontSizeInPt());
            if (position.getFont() != null && position.getFont().getName() != null) {
                fontName = position.getFont().getName();
            }
        }

        blocks.add(new TextBlock(
            blocks.size() + 1,
            cleaned,
            scale(x),
            scale(Math.max(0, top)),
            scale(Math.max(1, right - x)),
            scale(Math.max(1, bottom - top)),
            scale(Math.max(6, fontSize)),
            fontName,
            fontName.toLowerCase().contains("bold")
        ));
    }

    private String normalize(String text) {
        return text == null ? "" : text
            .replace("\u0000", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private BigDecimal scale(float value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    record TextBlock(
        int order,
        String text,
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height,
        BigDecimal fontSize,
        String fontName,
        boolean bold
    ) {
    }
}
