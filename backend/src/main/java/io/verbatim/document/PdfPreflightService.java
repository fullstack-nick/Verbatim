package io.verbatim.document;

import io.verbatim.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PdfPreflightService {

    private final float renderDpi;

    public PdfPreflightService(@Value("${verbatim.pdf.render-dpi:160}") float renderDpi) {
        this.renderDpi = renderDpi;
    }

    public PreflightResult analyze(Path source, Path renderDirectory) {
        try (PDDocument pdf = Loader.loadPDF(source.toFile())) {
            if (pdf.isEncrypted()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ENCRYPTED_PDF_UNSUPPORTED",
                    "Encrypted PDFs are not supported."
                );
            }
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<PreflightPage> pages = new ArrayList<>();
            int digital = 0;
            int scanned = 0;
            int mixed = 0;
            for (int index = 0; index < pdf.getNumberOfPages(); index++) {
                PDPage page = pdf.getPage(index);
                List<PdfTextBlockExtractor.TextBlock> blocks =
                    new PdfTextBlockExtractor().extract(pdf, index + 1);
                String text = normalizeText(
                    blocks.stream().map(PdfTextBlockExtractor.TextBlock::text)
                        .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right)
                );
                boolean imageDominant = hasLargeRaster(page);
                String pageType = text.length() < 12
                    ? "SCANNED"
                    : imageDominant ? "MIXED" : "DIGITAL";
                if ("DIGITAL".equals(pageType)) {
                    digital++;
                } else if ("SCANNED".equals(pageType)) {
                    scanned++;
                } else {
                    mixed++;
                }
                Path renderPath = renderDirectory.resolve("page-%04d.png".formatted(index + 1));
                BufferedImage image = renderer.renderImageWithDPI(index, renderDpi, ImageType.RGB);
                ImageIO.write(image, "png", renderPath.toFile());
                pages.add(new PreflightPage(
                    index + 1,
                    pageType,
                    scale(page.getMediaBox().getWidth()),
                    scale(page.getMediaBox().getHeight()),
                    page.getRotation(),
                    text,
                    blocks,
                    renderPath
                ));
            }
            return new PreflightResult(pdf.getNumberOfPages(), digital, scanned, mixed, pages);
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "PDF_ANALYSIS_FAILED",
                "The uploaded file is not a readable supported PDF."
            );
        }
    }

    private String normalizeText(String text) {
        return text == null ? "" : text
            .replace("\u0000", "")
            .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private boolean hasLargeRaster(PDPage page) throws IOException {
        if (page.getResources() == null) {
            return false;
        }
        for (var name : page.getResources().getXObjectNames()) {
            PDXObject object = page.getResources().getXObject(name);
            if (object instanceof PDImageXObject image
                && (long) image.getWidth() * image.getHeight() >= 500_000L) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal scale(float value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public record PreflightPage(
        int pageNumber,
        String pageType,
        BigDecimal width,
        BigDecimal height,
        int rotation,
        String extractedText,
        List<PdfTextBlockExtractor.TextBlock> blocks,
        Path renderPath
    ) {
    }

    public record PreflightResult(
        int pageCount,
        int digitalPageCount,
        int scannedPageCount,
        int mixedPageCount,
        List<PreflightPage> pages
    ) {
    }
}
