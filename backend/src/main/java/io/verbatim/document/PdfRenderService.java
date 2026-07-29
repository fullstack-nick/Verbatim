package io.verbatim.document;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PdfRenderService {

    private final float dpi;

    public PdfRenderService(@Value("${verbatim.pdf.render-dpi:160}") float dpi) {
        this.dpi = dpi;
    }

    public List<Path> render(Path pdfPath, Path outputDirectory) {
        try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<Path> pages = new ArrayList<>();
            for (int index = 0; index < pdf.getNumberOfPages(); index++) {
                Path target = outputDirectory.resolve("page-%04d.png".formatted(index + 1));
                BufferedImage image = renderer.renderImageWithDPI(index, dpi, ImageType.RGB);
                ImageIO.write(image, "png", target.toFile());
                pages.add(target);
            }
            return pages;
        } catch (IOException exception) {
            throw new IllegalStateException("The translated PDF could not be rendered.", exception);
        }
    }
}
