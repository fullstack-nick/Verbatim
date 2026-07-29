package io.verbatim.document;

import io.verbatim.review.ReviewModels.Finding;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PdfCompositionService {

    private final ObjectMapper objectMapper;

    public PdfCompositionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompositionResult compose(
        Path source,
        Path output,
        BigDecimal minimumFontScale,
        List<CompositionSegment> segments
    ) {
        List<Finding> findings = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(source.toFile());
            InputStream regularFile = font("NotoSans-Regular.ttf");
            InputStream boldFile = font("NotoSans-Bold.ttf")) {
            PDFont regular = PDType0Font.load(pdf, regularFile, true);
            PDFont bold = PDType0Font.load(pdf, boldFile, true);
            Map<Integer, List<CompositionSegment>> pages = new HashMap<>();
            for (CompositionSegment segment : segments) {
                pages.computeIfAbsent(segment.pageNumber(), ignored -> new ArrayList<>()).add(segment);
            }

            for (Map.Entry<Integer, List<CompositionSegment>> entry : pages.entrySet()) {
                int pageIndex = entry.getKey() - 1;
                if (pageIndex < 0 || pageIndex >= pdf.getNumberOfPages()) {
                    continue;
                }
                PDPage page = pdf.getPage(pageIndex);
                List<CompositionSegment> pageSegments = entry.getValue().stream()
                    .sorted(Comparator.comparing(CompositionSegment::blockOrder))
                    .toList();
                BufferedImage preview = pageSegments.isEmpty()
                    ? null
                    : ImageIO.read(pageSegments.getFirst().sourceRender().toFile());
                try (PDPageContentStream content = new PDPageContentStream(
                    pdf,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )) {
                    for (CompositionSegment segment : pageSegments) {
                        if (segment.targetText() == null || segment.targetText().isBlank()) {
                            continue;
                        }
                        JsonNode box = objectMapper.readTree(segment.boundingBoxJson());
                        JsonNode style = objectMapper.readTree(segment.styleJson());
                        float x = (float) box.path("x").asDouble();
                        float top = (float) box.path("y").asDouble();
                        float width = Math.max(2, (float) box.path("width").asDouble());
                        float height = Math.max(2, (float) box.path("height").asDouble());
                        float sourceSize = Math.max(6, (float) style.path("fontSize").asDouble(10));
                        boolean ocr = style.path("ocr").asBoolean(false);
                        PDFont font = style.path("bold").asBoolean(false) ? bold : regular;
                        String target = printable(segment.targetText());
                        float availableWidth = ocr ? width * 1.08f : width;
                        float scale = Math.min(
                            1f,
                            availableWidth / Math.max(1f, textWidth(font, sourceSize, target))
                        );
                        float minimum = minimumFontScale.floatValue();
                        if (scale < minimum) {
                            findings.add(new Finding(
                                "TEXT_OVERFLOW",
                                "ERROR",
                                "Translated text does not fit at the minimum font scale.",
                                segment.pageNumber(),
                                segment.segmentId(),
                                Map.of(
                                    "requiredScale", round(scale),
                                    "minimumScale", minimumFontScale,
                                    "boxWidth", width
                                )
                            ));
                            scale = minimum;
                        }
                        float fontSize = sourceSize * scale;
                        Color background = sampleColor(preview, page, x, top, width, height, true);
                        Color foreground = sampleColor(preview, page, x, top, width, height, false);
                        if (luminance(background) < 125) {
                            foreground = Color.WHITE;
                        }
                        float coverTop = Math.max(0, top - sourceSize * (ocr ? 0.45f : 0.25f));
                        float coverHeight = height + sourceSize * (ocr ? 1.0f : 0.60f);
                        float coverX = Math.max(0, x - (ocr ? sourceSize * 0.25f : 1.5f));
                        float coverWidth = ocr
                            ? Math.min(page.getMediaBox().getWidth() - coverX, width * 1.12f + sourceSize * 0.5f)
                            : width + 3;
                        float pdfY = page.getMediaBox().getHeight() - coverTop - coverHeight;

                        content.setNonStrokingColor(background);
                        content.addRect(coverX, pdfY, coverWidth, coverHeight);
                        content.fill();

                        content.beginText();
                        content.setNonStrokingColor(foreground);
                        content.setFont(font, fontSize);
                        content.newLineAtOffset(
                            x,
                            page.getMediaBox().getHeight() - top - (ocr ? sourceSize * 0.85f : height)
                        );
                        content.showText(target);
                        content.endText();
                    }
                }
            }

            Files.createDirectories(output.getParent());
            pdf.save(output.toFile());
            return new CompositionResult(output, findings);
        } catch (IOException exception) {
            throw new IllegalStateException("The translated PDF could not be composed.", exception);
        }
    }

    private InputStream font(String name) {
        InputStream stream = getClass().getResourceAsStream("/fonts/" + name);
        if (stream == null) {
            throw new IllegalStateException("Bundled font is missing: " + name);
        }
        return stream;
    }

    private float textWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(value) / 1000f * size;
    }

    private String printable(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private Color sampleColor(
        BufferedImage image,
        PDPage page,
        float x,
        float top,
        float width,
        float height,
        boolean background
    ) {
        if (image == null) {
            return Color.WHITE;
        }
        float scaleX = image.getWidth() / page.getMediaBox().getWidth();
        float scaleY = image.getHeight() / page.getMediaBox().getHeight();
        int left = clamp(Math.round(x * scaleX), 0, image.getWidth() - 1);
        int right = clamp(Math.round((x + width) * scaleX), left + 1, image.getWidth());
        int y1 = clamp(Math.round(top * scaleY), 0, image.getHeight() - 1);
        int y2 = clamp(Math.round((top + height) * scaleY), y1 + 1, image.getHeight());
        if (background) {
            return sampleBorderBackground(image, left, right, y1, y2);
        }
        List<Integer> red = new ArrayList<>();
        List<Integer> green = new ArrayList<>();
        List<Integer> blue = new ArrayList<>();
        int stepX = Math.max(1, (right - left) / 24);
        int stepY = Math.max(1, (y2 - y1) / 8);
        for (int py = y1; py < y2; py += stepY) {
            for (int px = left; px < right; px += stepX) {
                Color color = new Color(image.getRGB(px, py));
                red.add(color.getRed());
                green.add(color.getGreen());
                blue.add(color.getBlue());
            }
        }
        if (red.isEmpty()) {
            return background ? Color.WHITE : Color.BLACK;
        }
        List<Color> colors = new ArrayList<>();
        for (int index = 0; index < red.size(); index++) {
            colors.add(new Color(red.get(index), green.get(index), blue.get(index)));
        }
        colors.sort(Comparator.comparingDouble(this::luminance));
        int sampleCount = Math.max(1, colors.size() / 8);
        int totalRed = 0;
        int totalGreen = 0;
        int totalBlue = 0;
        for (int index = 0; index < sampleCount; index++) {
            totalRed += colors.get(index).getRed();
            totalGreen += colors.get(index).getGreen();
            totalBlue += colors.get(index).getBlue();
        }
        return new Color(totalRed / sampleCount, totalGreen / sampleCount, totalBlue / sampleCount);
    }

    private Color sampleBorderBackground(
        BufferedImage image,
        int left,
        int right,
        int top,
        int bottom
    ) {
        List<Integer> red = new ArrayList<>();
        List<Integer> green = new ArrayList<>();
        List<Integer> blue = new ArrayList<>();
        int above = clamp(top - 3, 0, image.getHeight() - 1);
        int below = clamp(bottom + 3, 0, image.getHeight() - 1);
        int step = Math.max(1, (right - left) / 24);
        for (int x = left; x < right; x += step) {
            addColor(image, x, above, red, green, blue);
            addColor(image, x, below, red, green, blue);
        }
        int middleY = clamp((top + bottom) / 2, 0, image.getHeight() - 1);
        addColor(image, clamp(left - 3, 0, image.getWidth() - 1), middleY, red, green, blue);
        addColor(image, clamp(right + 3, 0, image.getWidth() - 1), middleY, red, green, blue);
        red.sort(Integer::compareTo);
        green.sort(Integer::compareTo);
        blue.sort(Integer::compareTo);
        int middle = red.size() / 2;
        return new Color(red.get(middle), green.get(middle), blue.get(middle));
    }

    private void addColor(
        BufferedImage image,
        int x,
        int y,
        List<Integer> red,
        List<Integer> green,
        List<Integer> blue
    ) {
        Color color = new Color(image.getRGB(x, y));
        red.add(color.getRed());
        green.add(color.getGreen());
        blue.add(color.getBlue());
    }

    private double luminance(Color color) {
        return color.getRed() * 0.2126 + color.getGreen() * 0.7152 + color.getBlue() * 0.0722;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private double round(float value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record CompositionSegment(
        UUID segmentId,
        int pageNumber,
        int blockOrder,
        String targetText,
        String boundingBoxJson,
        String styleJson,
        Path sourceRender
    ) {
    }

    public record CompositionResult(Path output, List<Finding> findings) {
    }
}
