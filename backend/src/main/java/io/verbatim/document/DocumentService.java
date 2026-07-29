package io.verbatim.document;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import io.verbatim.common.ApiException;
import io.verbatim.document.DocumentModels.DocumentDetail;
import io.verbatim.document.DocumentModels.DocumentView;
import io.verbatim.document.DocumentModels.PageView;
import io.verbatim.document.DocumentModels.SegmentView;
import io.verbatim.document.PdfPreflightService.PreflightPage;
import io.verbatim.document.PdfPreflightService.PreflightResult;
import io.verbatim.infrastructure.StorageService;
import io.verbatim.infrastructure.StorageService.StoredFile;
import io.verbatim.project.ProjectModels.ProjectView;
import io.verbatim.project.ProjectService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DSLContext database;
    private final ProjectService projects;
    private final StorageService storage;
    private final PdfPreflightService preflight;

    public DocumentService(
        DSLContext database,
        ProjectService projects,
        StorageService storage,
        PdfPreflightService preflight
    ) {
        this.database = database;
        this.projects = projects;
        this.storage = storage;
        this.preflight = preflight;
    }

    public List<DocumentView> list(UUID projectId) {
        projects.get(projectId);
        return database.select()
            .from(table(name("document")))
            .where(field(name("project_id"), UUID.class).eq(projectId))
            .orderBy(field(name("created_at")).desc())
            .fetch(this::toDocument);
    }

    public DocumentDetail get(UUID projectId, UUID documentId) {
        DocumentView document = requireDocument(projectId, documentId);
        List<PageView> pages = database.select()
            .from(table(name("document_page")))
            .where(field(name("document_id"), UUID.class).eq(documentId))
            .orderBy(field(name("page_number")))
            .fetch(record -> toPage(projectId, record));
        List<SegmentView> segments = database.select()
            .from(table(name("segment")))
            .where(field(name("document_id"), UUID.class).eq(documentId))
            .orderBy(field(name("page_id")), field(name("block_order")))
            .fetch(this::toSegment);
        return new DocumentDetail(document, pages, segments);
    }

    @Transactional
    public DocumentDetail upload(
        UUID projectId,
        String sourceLocale,
        String targetLocale,
        MultipartFile file
    ) {
        ProjectView project = projects.get(projectId);
        validatePdf(file);
        UUID documentId = UUID.randomUUID();
        String effectiveSourceLocale = sourceLocale == null || sourceLocale.isBlank()
            ? project.defaultSourceLocale()
            : sourceLocale;
        String effectiveTargetLocale = targetLocale == null || targetLocale.isBlank()
            ? project.defaultTargetLocale()
            : targetLocale;

        try {
            StoredFile stored = storage.storeSource(
                projectId,
                documentId,
                file.getOriginalFilename(),
                file.getInputStream()
            );
            database.insertInto(table(name("document")))
                .columns(
                    field(name("id")),
                    field(name("project_id")),
                    field(name("source_filename")),
                    field(name("source_path")),
                    field(name("source_checksum")),
                    field(name("source_locale")),
                    field(name("target_locale")),
                    field(name("state"))
                )
                .values(
                    documentId,
                    projectId,
                    file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename(),
                    stored.relativePath(),
                    stored.sha256(),
                    effectiveSourceLocale,
                    effectiveTargetLocale,
                    "ANALYZING"
                )
                .execute();

            PreflightResult result = preflight.analyze(
                storage.resolve(stored.relativePath()),
                storage.documentRenderDirectory(documentId)
            );
            persistPages(documentId, result);
            database.update(table(name("document")))
                .set(field(name("page_count")), result.pageCount())
                .set(field(name("digital_page_count")), result.digitalPageCount())
                .set(field(name("scanned_page_count")), result.scannedPageCount())
                .set(field(name("mixed_page_count")), result.mixedPageCount())
                .set(field(name("state")), "READY_TO_TRANSLATE")
                .set(field(name("updated_at")), OffsetDateTime.now())
                .where(field(name("id"), UUID.class).eq(documentId))
                .execute();
            return get(projectId, documentId);
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PDF_UPLOAD_FAILED",
                "The PDF upload could not be completed."
            );
        }
    }

    public Resource source(UUID projectId, UUID documentId) {
        requireDocument(projectId, documentId);
        String path = database.select(field(name("source_path"), String.class))
            .from(table(name("document")))
            .where(field(name("id"), UUID.class).eq(documentId))
            .fetchOne(field(name("source_path"), String.class));
        return storage.resource(path);
    }

    public Resource pagePreview(UUID projectId, UUID documentId, int pageNumber) {
        requireDocument(projectId, documentId);
        String path = database.select(field(name("render_path"), String.class))
            .from(table(name("document_page")))
            .where(field(name("document_id"), UUID.class).eq(documentId))
            .and(field(name("page_number"), Integer.class).eq(pageNumber))
            .fetchOne(field(name("render_path"), String.class));
        if (path == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAGE_NOT_FOUND", "Page not found.");
        }
        return storage.resource(path);
    }

    private void persistPages(UUID documentId, PreflightResult result) {
        for (PreflightPage page : result.pages()) {
            UUID pageId = UUID.randomUUID();
            String renderPath = storage.relative(page.renderPath());
            database.insertInto(table(name("document_page")))
                .columns(
                    field(name("id")),
                    field(name("document_id")),
                    field(name("page_number")),
                    field(name("page_type")),
                    field(name("width")),
                    field(name("height")),
                    field(name("rotation")),
                    field(name("render_path"))
                )
                .values(
                    pageId,
                    documentId,
                    page.pageNumber(),
                    page.pageType(),
                    page.width(),
                    page.height(),
                    page.rotation(),
                    renderPath
                )
                .execute();

            if ("DIGITAL".equals(page.pageType()) && !page.blocks().isEmpty()) {
                for (PdfTextBlockExtractor.TextBlock block : page.blocks()) {
                    insertSegment(
                        documentId,
                        pageId,
                        block.order(),
                        block.text(),
                        "PDF_TEXT",
                        BigDecimal.ONE,
                        "{\"x\":%s,\"y\":%s,\"width\":%s,\"height\":%s}".formatted(
                            block.x(),
                            block.y(),
                            block.width(),
                            block.height()
                        ),
                        "{\"fontFamily\":\"%s\",\"fontSize\":%s,\"bold\":%s,\"fontScale\":1.0}"
                            .formatted(
                                block.fontName().replace("\"", ""),
                                block.fontSize(),
                                block.bold()
                            )
                    );
                }
            } else {
                insertSegment(
                    documentId,
                    pageId,
                    1,
                    "",
                    "OCR_PENDING",
                    null,
                    "{\"x\":0,\"y\":0,\"width\":%s,\"height\":%s}".formatted(
                        page.width(),
                        page.height()
                    ),
                    "{\"fontFamily\":\"source\",\"fontScale\":1.0}"
                );
            }
        }
    }

    private void insertSegment(
        UUID documentId,
        UUID pageId,
        int order,
        String sourceText,
        String extractionMethod,
        BigDecimal confidence,
        String boundingBoxJson,
        String styleJson
    ) {
        database.insertInto(table(name("segment")))
            .columns(
                field(name("id")),
                field(name("document_id")),
                field(name("page_id")),
                field(name("block_order")),
                field(name("block_type")),
                field(name("source_text")),
                field(name("bounding_box")),
                field(name("style_json")),
                field(name("extraction_method")),
                field(name("confidence")),
                field(name("status"))
            )
            .values(
                UUID.randomUUID(),
                documentId,
                pageId,
                order,
                "TEXT",
                sourceText,
                JSONB.valueOf(boundingBoxJson),
                JSONB.valueOf(styleJson),
                extractionMethod,
                confidence,
                sourceText.isBlank() ? "DRAFT" : "NEEDS_REVIEW"
            )
            .execute();
    }

    private DocumentView requireDocument(UUID projectId, UUID documentId) {
        Record record = database.select()
            .from(table(name("document")))
            .where(field(name("id"), UUID.class).eq(documentId))
            .and(field(name("project_id"), UUID.class).eq(projectId))
            .fetchOne();
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found.");
        }
        return toDocument(record);
    }

    private DocumentView toDocument(Record record) {
        return new DocumentView(
            record.get("id", UUID.class),
            record.get("project_id", UUID.class),
            record.get("source_filename", String.class),
            record.get("source_locale", String.class),
            record.get("target_locale", String.class),
            record.get("state", String.class),
            record.get("version", Integer.class),
            record.get("page_count", Integer.class),
            record.get("digital_page_count", Integer.class),
            record.get("scanned_page_count", Integer.class),
            record.get("mixed_page_count", Integer.class),
            record.get("created_at", OffsetDateTime.class)
        );
    }

    private PageView toPage(UUID projectId, Record record) {
        UUID documentId = record.get("document_id", UUID.class);
        int pageNumber = record.get("page_number", Integer.class);
        return new PageView(
            record.get("id", UUID.class),
            pageNumber,
            record.get("page_type", String.class),
            record.get("width", BigDecimal.class),
            record.get("height", BigDecimal.class),
            record.get("rotation", Integer.class),
            "/api/projects/" + projectId + "/documents/" + documentId
                + "/pages/" + pageNumber + "/preview",
            record.get("ocr_confidence", BigDecimal.class)
        );
    }

    private SegmentView toSegment(Record record) {
        return new SegmentView(
            record.get("id", UUID.class),
            record.get("page_id", UUID.class),
            record.get("block_order", Integer.class),
            record.get("block_type", String.class),
            record.get("source_text", String.class),
            record.get("target_text", String.class),
            record.get("status", String.class),
            record.get("version", Integer.class),
            record.get("confidence", BigDecimal.class)
        );
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_REQUIRED", "Choose a PDF to upload.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_REQUIRED", "Only PDF files are supported.");
        }
    }
}
