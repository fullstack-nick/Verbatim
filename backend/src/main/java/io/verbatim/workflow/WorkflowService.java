package io.verbatim.workflow;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import io.verbatim.common.ApiException;
import io.verbatim.document.PdfCompositionService;
import io.verbatim.document.PdfCompositionService.CompositionResult;
import io.verbatim.document.PdfCompositionService.CompositionSegment;
import io.verbatim.infrastructure.StorageService;
import io.verbatim.project.ProjectModels.RuleInput;
import io.verbatim.project.ProjectModels.UpdateRulesRequest;
import io.verbatim.project.ProjectService;
import io.verbatim.review.DeterministicReviewService;
import io.verbatim.review.ReviewModels.Finding;
import io.verbatim.terminology.TerminologyCacheService;
import io.verbatim.terminology.TerminologyModels.TermView;
import io.verbatim.translation.TranslationClient.SourceSegment;
import io.verbatim.translation.TranslationClient.TargetSegment;
import io.verbatim.translation.TranslationClient.TranslationContext;
import io.verbatim.translation.TranslationClient.TranslationResult;
import io.verbatim.translation.TranslationGateway;
import io.verbatim.translation.CodexVisionOcrClient;
import io.verbatim.translation.CodexVisionOcrClient.OcrRegion;
import io.verbatim.translation.CodexVisionOcrClient.OcrResult;
import io.verbatim.workflow.WorkflowModels.AddInstructionRequest;
import io.verbatim.workflow.WorkflowModels.FindingView;
import io.verbatim.workflow.WorkflowModels.InstructionView;
import io.verbatim.workflow.WorkflowModels.JobView;
import io.verbatim.workflow.WorkflowModels.RevisionView;
import io.verbatim.workflow.WorkflowModels.StartTranslationRequest;
import io.verbatim.workflow.WorkflowModels.StartTranslationResponse;
import io.verbatim.workflow.WorkflowModels.UsageView;
import io.verbatim.translationmemory.TranslationMemoryModels.CreateMemoryRequest;
import io.verbatim.translationmemory.TranslationMemoryService;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorkflowService {

    private final DSLContext database;
    private final ProjectService projects;
    private final TerminologyCacheService termCache;
    private final TranslationGateway translation;
    private final CodexVisionOcrClient ocr;
    private final TranslationMemoryService translationMemory;
    private final DeterministicReviewService review;
    private final PdfCompositionService compositor;
    private final StorageService storage;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public WorkflowService(
        DSLContext database,
        ProjectService projects,
        TerminologyCacheService termCache,
        TranslationGateway translation,
        CodexVisionOcrClient ocr,
        TranslationMemoryService translationMemory,
        DeterministicReviewService review,
        PdfCompositionService compositor,
        StorageService storage,
        ObjectMapper objectMapper,
        @Value("${verbatim.workflow.max-attempts:3}") int maxAttempts
    ) {
        this.database = database;
        this.projects = projects;
        this.termCache = termCache;
        this.translation = translation;
        this.ocr = ocr;
        this.translationMemory = translationMemory;
        this.review = review;
        this.compositor = compositor;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public StartTranslationResponse start(
        UUID projectId,
        UUID documentId,
        String idempotencyKey,
        StartTranslationRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED",
                "An Idempotency-Key header is required."
            );
        }
        Record document = requireDocument(projectId, documentId);
        String scope = documentId + ":translation";
        JSONB existing = database.select(field(name("response_json"), JSONB.class))
            .from(table(name("idempotency_record")))
            .where(field(name("scope"), String.class).eq(scope))
            .and(field(name("idempotency_key"), String.class).eq(idempotencyKey))
            .fetchOne(field(name("response_json"), JSONB.class));
        if (existing != null) {
            return objectMapper.readValue(existing.data(), StartTranslationResponse.class);
        }

        int currentVersion = document.get("version", Integer.class);
        int expectedVersion = currentVersion + 1;
        int ruleVersion = projects.get(projectId).ruleSetVersion();
        Integer lastRevision = database.select(
                DSL.max(field(name("revision_number"), Integer.class))
            )
            .from(table(name("document_revision")))
            .where(field(name("document_id"), UUID.class).eq(documentId))
            .fetchOne(0, Integer.class);
        int revisionNumber = (lastRevision == null ? 0 : lastRevision) + 1;
        UUID revisionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        database.update(table(name("document")))
            .set(field(name("version")), expectedVersion)
            .set(field(name("state")), "QUEUED")
            .set(field(name("updated_at")), OffsetDateTime.now())
            .where(field(name("id"), UUID.class).eq(documentId))
            .and(field(name("version"), Integer.class).eq(currentVersion))
            .execute();
        database.insertInto(table(name("document_revision")))
            .columns(
                field(name("id")),
                field(name("document_id")),
                field(name("revision_number")),
                field(name("project_rule_set_version")),
                field(name("state"))
            )
            .values(revisionId, documentId, revisionNumber, ruleVersion, "QUEUED")
            .execute();
        for (String instruction : request.instructions()) {
            if (instruction != null && !instruction.isBlank()) {
                insertInstruction(documentId, revisionId, "DOCUMENT", instruction.trim(), false);
            }
        }
        database.insertInto(table(name("workflow_job")))
            .columns(
                field(name("id")),
                field(name("document_id")),
                field(name("revision_id")),
                field(name("job_type")),
                field(name("state")),
                field(name("current_stage")),
                field(name("progress_total")),
                field(name("expected_document_version")),
                field(name("expected_rule_set_version"))
            )
            .values(
                jobId,
                documentId,
                revisionId,
                "TRANSLATE_DOCUMENT",
                "QUEUED",
                "QUEUED",
                6,
                expectedVersion,
                ruleVersion
            )
            .execute();
        StartTranslationResponse response = new StartTranslationResponse(jobId, revisionId, "QUEUED");
        database.insertInto(table(name("idempotency_record")))
            .columns(
                field(name("id")),
                field(name("scope")),
                field(name("idempotency_key")),
                field(name("resource_id")),
                field(name("response_json"))
            )
            .values(
                UUID.randomUUID(),
                scope,
                idempotencyKey,
                jobId,
                JSONB.valueOf(objectMapper.writeValueAsString(response))
            )
            .execute();
        return response;
    }

    public JobView getJob(UUID projectId, UUID documentId, UUID jobId) {
        requireDocument(projectId, documentId);
        Record record = database.select()
            .from(table(name("workflow_job")))
            .where(field(name("id"), UUID.class).eq(jobId))
            .and(field(name("document_id"), UUID.class).eq(documentId))
            .fetchOne();
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
        }
        return toJob(record);
    }

    public List<RevisionView> revisions(UUID projectId, UUID documentId) {
        requireDocument(projectId, documentId);
        return database.select()
            .from(table(name("document_revision")))
            .where(field(name("document_id"), UUID.class).eq(documentId))
            .orderBy(field(name("revision_number")).desc())
            .fetch(record -> toRevision(projectId, documentId, record));
    }

    public RevisionView revision(UUID projectId, UUID documentId, UUID revisionId) {
        requireDocument(projectId, documentId);
        Record record = requireRevision(documentId, revisionId);
        return toRevision(projectId, documentId, record);
    }

    public Resource download(UUID projectId, UUID documentId, UUID revisionId) {
        requireDocument(projectId, documentId);
        Record revision = requireRevision(documentId, revisionId);
        String outputPath = revision.get("output_path", String.class);
        if (outputPath == null) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "REVISION_NOT_READY",
                "The translated PDF is not ready yet."
            );
        }
        return storage.resource(outputPath);
    }

    @Transactional
    public RevisionView approve(UUID projectId, UUID documentId, UUID revisionId) {
        Record document = requireDocument(projectId, documentId);
        Record revision = requireRevision(documentId, revisionId);
        if (revision.get("output_path", String.class) == null) {
            throw new ApiException(HttpStatus.CONFLICT, "REVISION_NOT_READY", "Revision is not ready.");
        }
        OffsetDateTime approvedAt = OffsetDateTime.now();
        database.update(table(name("document_revision")))
            .set(field(name("state")), "APPROVED")
            .set(field(name("approved_at")), approvedAt)
            .where(field(name("id"), UUID.class).eq(revisionId))
            .execute();
        database.update(table(name("document")))
            .set(field(name("state")), "APPROVED")
            .set(field(name("updated_at")), approvedAt)
            .where(field(name("id"), UUID.class).eq(documentId))
            .execute();
        database.select(
                field(name("source_text"), String.class),
                field(name("target_text"), String.class)
            )
            .from(table(name("segment")))
            .where(field(name("document_id"), UUID.class).eq(documentId))
            .and(field(name("target_text"), String.class).isNotNull())
            .and(field(name("target_text"), String.class).ne(""))
            .forEach(pair -> translationMemory.create(projectId, new CreateMemoryRequest(
                document.get("source_locale", String.class),
                document.get("target_locale", String.class),
                pair.get(0, String.class),
                pair.get(1, String.class)
            )));
        return revision(projectId, documentId, revisionId);
    }

    @Transactional
    public InstructionView addInstruction(
        UUID projectId,
        UUID documentId,
        AddInstructionRequest request
    ) {
        requireDocument(projectId, documentId);
        if (request.promoteToProject()) {
            var current = projects.getRules(projectId);
            List<RuleInput> rules = new ArrayList<>();
            current.rules().forEach(rule -> rules.add(new RuleInput(
                rule.type(),
                rule.name(),
                rule.sourceLocale(),
                rule.targetLocale(),
                rule.value()
            )));
            rules.add(new RuleInput(
                "TRANSLATION_INSTRUCTION",
                "Chat instruction",
                null,
                null,
                Map.of("instruction", request.message())
            ));
            projects.updateRules(projectId, new UpdateRulesRequest(rules, current.minimumFontScale()));
        }
        UUID id = insertInstruction(
            documentId,
            null,
            request.promoteToProject() ? "PROJECT" : "DOCUMENT",
            request.message().trim(),
            request.promoteToProject()
        );
        Record stored = database.select()
            .from(table(name("revision_instruction")))
            .where(field(name("id"), UUID.class).eq(id))
            .fetchOne();
        return new InstructionView(
            id,
            stored.get("scope", String.class),
            stored.get("message", String.class),
            Boolean.TRUE.equals(stored.get("promoted_to_project", Boolean.class)),
            stored.get("created_at", OffsetDateTime.class)
        );
    }

    JobClaim claimNext() {
        return database.transactionResult(configuration -> {
            DSLContext transaction = DSL.using(configuration);
            Record record = transaction.select()
                .from(table(name("workflow_job")))
                .where(field(name("state"), String.class).eq("QUEUED"))
                .and(field(name("available_at"), OffsetDateTime.class).le(OffsetDateTime.now()))
                .orderBy(field(name("created_at")))
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOne();
            if (record == null) {
                return null;
            }
            UUID id = record.get("id", UUID.class);
            transaction.update(table(name("workflow_job")))
                .set(field(name("state")), "RUNNING")
                .set(field(name("current_stage")), "LOADING_CONTEXT")
                .set(field(name("attempts")), field(name("attempts"), Integer.class).add(1))
                .set(field(name("started_at")), OffsetDateTime.now())
                .where(field(name("id"), UUID.class).eq(id))
                .execute();
            return new JobClaim(
                id,
                record.get("document_id", UUID.class),
                record.get("revision_id", UUID.class),
                record.get("expected_document_version", Integer.class),
                record.get("expected_rule_set_version", Integer.class),
                record.get("attempts", Integer.class) + 1
            );
        });
    }

    void process(JobClaim job) {
        Record document = database.select()
            .from(table(name("document")))
            .where(field(name("id"), UUID.class).eq(job.documentId()))
            .fetchOne();
        if (document == null) {
            throw new IllegalStateException("Document disappeared while processing.");
        }
        UUID projectId = document.get("project_id", UUID.class);
        updateProgress(job.id(), "EXTRACTING_SCANNED_TEXT", 1);
        extractScannedText(job);
        updateProgress(job.id(), "TRANSLATING", 2);
        List<SegmentData> segments = loadSegments(job.documentId());
        List<SegmentData> translatable = segments.stream()
            .filter(item -> item.sourceText() != null && !item.sourceText().isBlank())
            .toList();
        if (translatable.isEmpty()) {
            throw new IllegalStateException("No readable text regions were found in the PDF.");
        }
        List<TermView> terms = termCache.active(
            projectId,
            document.get("target_locale", String.class)
        );
        var ruleSet = projects.getRules(projectId);
        List<String> instructions = loadInstructions(job.documentId(), job.revisionId());
        List<Map<String, Object>> memory = loadMemory(
            projectId,
            document.get("source_locale", String.class),
            document.get("target_locale", String.class),
            translatable.stream()
                .limit(8)
                .map(SegmentData::sourceText)
                .reduce("", (left, right) -> left + "\n" + right)
        );
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("version", ruleSet.version());
        rules.put("minimumFontScale", ruleSet.minimumFontScale());
        rules.put("rules", ruleSet.rules());
        TranslationContext context = new TranslationContext(
            job.documentId(),
            document.get("source_locale", String.class),
            document.get("target_locale", String.class),
            rules,
            terms.stream().map(term -> objectMapper.convertValue(
                term,
                new TypeReference<Map<String, Object>>() {
                }
            )).toList(),
            memory,
            translatable.stream().map(item -> new SourceSegment(item.id(), item.sourceText())).toList(),
            instructions
        );
        TranslationResult translated = translation.translate(context);
        saveInvocation(job, translated);
        Map<UUID, String> targets = new HashMap<>();
        for (TargetSegment item : translated.translations()) {
            targets.put(item.id(), item.text());
        }
        for (SegmentData segment : translatable) {
            String target = targets.get(segment.id());
            if (target == null) {
                throw new IllegalStateException("The translation omitted segment " + segment.id());
            }
            database.update(table(name("segment")))
                .set(field(name("target_text")), target)
                .set(field(name("status")), "NEEDS_REVIEW")
                .set(field(name("updated_at")), OffsetDateTime.now())
                .where(field(name("id"), UUID.class).eq(segment.id()))
                .execute();
        }

        updateProgress(job.id(), "DETERMINISTIC_REVIEW", 3);
        List<Finding> findings = new ArrayList<>();
        for (SegmentData segment : translatable) {
            findings.addAll(review.review(
                segment.id(),
                segment.pageNumber(),
                segment.sourceText(),
                targets.get(segment.id()),
                document.get("target_locale", String.class),
                terms
            ));
        }
        saveReview(job, translatable, findings);

        updateProgress(job.id(), "COMPOSING_PDF", 4);
        Path output = storage.revisionPdf(job.documentId(), job.revisionId());
        CompositionResult composition = compositor.compose(
            storage.resolve(document.get("source_path", String.class)),
            output,
            ruleSet.minimumFontScale(),
            translatable.stream().map(segment -> new CompositionSegment(
                segment.id(),
                segment.pageNumber(),
                segment.blockOrder(),
                targets.get(segment.id()),
                segment.boundingBox(),
                segment.style(),
                storage.resolve(segment.renderPath())
            )).toList()
        );
        findings.addAll(composition.findings());
        saveLayoutFindings(job.revisionId(), composition.findings());
        updateProgress(job.id(), "FINALIZING", 5);

        String result = findings.stream().anyMatch(item -> "ERROR".equals(item.severity()))
            ? "QA_FLAGGED"
            : "QA_PASSED";
        int updated = database.update(table(name("document")))
            .set(field(name("state")), result)
            .set(field(name("updated_at")), OffsetDateTime.now())
            .where(field(name("id"), UUID.class).eq(job.documentId()))
            .and(field(name("version"), Integer.class).eq(job.expectedDocumentVersion()))
            .execute();
        if (updated == 0) {
            result = "STALE";
        }
        database.update(table(name("document_revision")))
            .set(field(name("state")), result)
            .set(field(name("output_path")), storage.relative(output))
            .where(field(name("id"), UUID.class).eq(job.revisionId()))
            .execute();
        database.update(table(name("workflow_job")))
            .set(field(name("state")), "COMPLETED")
            .set(field(name("current_stage")), result)
            .set(field(name("progress_current")), 6)
            .set(field(name("completed_at")), OffsetDateTime.now())
            .where(field(name("id"), UUID.class).eq(job.id()))
            .execute();
    }

    void fail(JobClaim job, RuntimeException failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (job.attempts() < maxAttempts) {
            database.update(table(name("workflow_job")))
                .set(field(name("state")), "QUEUED")
                .set(field(name("current_stage")), "RETRY_SCHEDULED")
                .set(field(name("error_code")), "WORKFLOW_ATTEMPT_FAILED")
                .set(field(name("error_message")), abbreviate(message))
                .set(field(name("available_at")), OffsetDateTime.now().plusSeconds(job.attempts() * 3L))
                .where(field(name("id"), UUID.class).eq(job.id()))
                .execute();
            return;
        }
        database.update(table(name("workflow_job")))
            .set(field(name("state")), "FAILED")
            .set(field(name("current_stage")), "FAILED")
            .set(field(name("error_code")), "WORKFLOW_FAILED")
            .set(field(name("error_message")), abbreviate(message))
            .set(field(name("completed_at")), OffsetDateTime.now())
            .where(field(name("id"), UUID.class).eq(job.id()))
            .execute();
        database.update(table(name("document_revision")))
            .set(field(name("state")), "FAILED")
            .where(field(name("id"), UUID.class).eq(job.revisionId()))
            .execute();
        database.update(table(name("document")))
            .set(field(name("state")), "FAILED")
            .where(field(name("id"), UUID.class).eq(job.documentId()))
            .and(field(name("version"), Integer.class).eq(job.expectedDocumentVersion()))
            .execute();
    }

    private Record requireDocument(UUID projectId, UUID documentId) {
        projects.get(projectId);
        Record record = database.select()
            .from(table(name("document")))
            .where(field(name("id"), UUID.class).eq(documentId))
            .and(field(name("project_id"), UUID.class).eq(projectId))
            .fetchOne();
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found.");
        }
        return record;
    }

    private Record requireRevision(UUID documentId, UUID revisionId) {
        Record record = database.select()
            .from(table(name("document_revision")))
            .where(field(name("id"), UUID.class).eq(revisionId))
            .and(field(name("document_id"), UUID.class).eq(documentId))
            .fetchOne();
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REVISION_NOT_FOUND", "Revision not found.");
        }
        return record;
    }

    private UUID insertInstruction(
        UUID documentId,
        UUID revisionId,
        String scope,
        String message,
        boolean promoted
    ) {
        UUID id = UUID.randomUUID();
        database.insertInto(table(name("revision_instruction")))
            .columns(
                field(name("id")),
                field(name("document_id")),
                field(name("revision_id")),
                field(name("scope")),
                field(name("effect")),
                field(name("message")),
                field(name("promoted_to_project"))
            )
            .values(id, documentId, revisionId, scope, "NEXT_REVISION", message, promoted)
            .execute();
        return id;
    }

    private List<SegmentData> loadSegments(UUID documentId) {
        var segment = table(name("segment")).as("s");
        var page = table(name("document_page")).as("p");
        return database.select(
                field(name("s", "id"), UUID.class),
                field(name("s", "block_order"), Integer.class),
                field(name("s", "source_text"), String.class),
                field(name("s", "bounding_box"), JSONB.class),
                field(name("s", "style_json"), JSONB.class),
                field(name("p", "page_number"), Integer.class),
                field(name("p", "render_path"), String.class)
            )
            .from(segment)
            .join(page).on(field(name("p", "id")).eq(field(name("s", "page_id"))))
            .where(field(name("s", "document_id"), UUID.class).eq(documentId))
            .orderBy(field(name("p", "page_number")), field(name("s", "block_order")))
            .fetch(record -> new SegmentData(
                record.get(0, UUID.class),
                record.get(1, Integer.class),
                record.get(2, String.class),
                record.get(3, JSONB.class).data(),
                record.get(4, JSONB.class).data(),
                record.get(5, Integer.class),
                record.get(6, String.class)
            ));
    }

    private void extractScannedText(JobClaim job) {
        List<PageData> pages = database.select(
                field(name("id"), UUID.class),
                field(name("page_number"), Integer.class),
                field(name("width"), BigDecimal.class),
                field(name("height"), BigDecimal.class),
                field(name("render_path"), String.class)
            )
            .from(table(name("document_page")))
            .where(field(name("document_id"), UUID.class).eq(job.documentId()))
            .and(field(name("page_type"), String.class).in("SCANNED", "MIXED"))
            .andExists(database.selectOne()
                .from(table(name("segment")))
                .where(field(name("page_id"), UUID.class).eq(field(name("document_page", "id"), UUID.class)))
                .and(field(name("extraction_method"), String.class).eq("OCR_PENDING")))
            .orderBy(field(name("page_number")))
            .fetch(record -> new PageData(
                record.get(0, UUID.class),
                record.get(1, Integer.class),
                record.get(2, BigDecimal.class),
                record.get(3, BigDecimal.class),
                record.get(4, String.class)
            ));
        for (PageData page : pages) {
            OcrResult result = ocr.read(storage.resolve(page.renderPath()));
            database.deleteFrom(table(name("segment")))
                .where(field(name("page_id"), UUID.class).eq(page.id()))
                .and(field(name("extraction_method"), String.class).eq("OCR_PENDING"))
                .execute();
            int order = 0;
            for (OcrRegion region : result.regions()) {
                String[] lines = region.text().split("\\R");
                double lineHeight = region.height() / Math.max(1, lines.length);
                for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                    String text = lines[lineIndex].trim();
                    if (text.isBlank()) {
                        continue;
                    }
                    order++;
                    BigDecimal x = scaled(page.width(), region.x() / 1000.0);
                    BigDecimal y = scaled(
                        page.height(),
                        (region.y() + lineIndex * lineHeight) / 1000.0
                    );
                    BigDecimal width = scaled(page.width(), region.width() / 1000.0);
                    BigDecimal height = scaled(page.height(), lineHeight / 1000.0);
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
                            job.documentId(),
                            page.id(),
                            order,
                            "TEXT",
                            text,
                            JSONB.valueOf("""
                                {"x":%s,"y":%s,"width":%s,"height":%s}
                                """.formatted(x, y, width, height).trim()),
                            JSONB.valueOf("""
                                {"fontFamily":"Noto Sans","fontSize":%s,"bold":%s,"fontScale":1.0,"ocr":true}
                                """.formatted(region.fontSize(), region.bold()).trim()),
                            "CODEX_VISION",
                            new BigDecimal("0.90"),
                            "NEEDS_REVIEW"
                        )
                        .execute();
                }
            }
            saveOcrInvocation(job, page.pageNumber(), result);
        }
    }

    private void saveOcrInvocation(JobClaim job, int pageNumber, OcrResult result) {
        database.insertInto(table(name("ai_invocation")))
            .columns(
                field(name("id")),
                field(name("document_id")),
                field(name("revision_id")),
                field(name("stage")),
                field(name("batch_number")),
                field(name("provider_thread_id")),
                field(name("context_hash")),
                field(name("input_tokens")),
                field(name("cached_input_tokens")),
                field(name("output_tokens")),
                field(name("reasoning_output_tokens")),
                field(name("duration_ms")),
                field(name("state"))
            )
            .values(
                UUID.randomUUID(),
                job.documentId(),
                job.revisionId(),
                "OCR:CODEX_VISION",
                pageNumber,
                result.providerThreadId(),
                Integer.toHexString(result.regions().hashCode()),
                result.usage().inputTokens(),
                result.usage().cachedInputTokens(),
                result.usage().outputTokens(),
                result.usage().reasoningTokens(),
                result.durationMillis(),
                "COMPLETED"
            )
            .execute();
    }

    private BigDecimal scaled(BigDecimal dimension, double fraction) {
        return dimension.multiply(BigDecimal.valueOf(fraction)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private List<String> loadInstructions(UUID documentId, UUID revisionId) {
        return database.select(field(name("message"), String.class))
            .from(table(name("revision_instruction")))
            .where(field(name("document_id"), UUID.class).eq(documentId))
            .and(
                field(name("revision_id"), UUID.class).isNull()
                    .or(field(name("revision_id"), UUID.class).eq(revisionId))
            )
            .orderBy(field(name("created_at")))
            .fetch(field(name("message"), String.class));
    }

    private List<Map<String, Object>> loadMemory(
        UUID projectId,
        String sourceLocale,
        String targetLocale,
        String sourceText
    ) {
        return translationMemory.suggestions(
            projectId,
            sourceLocale,
            targetLocale,
            sourceText,
            3
        ).stream().map(suggestion -> Map.<String, Object>of(
            "source", suggestion.sourceText(),
            "target", suggestion.targetText(),
            "similarity", suggestion.similarity()
        )).toList();
    }

    private void saveInvocation(JobClaim job, TranslationResult translated) {
        database.insertInto(table(name("ai_invocation")))
            .columns(
                field(name("id")),
                field(name("document_id")),
                field(name("revision_id")),
                field(name("stage")),
                field(name("provider_thread_id")),
                field(name("context_hash")),
                field(name("input_tokens")),
                field(name("cached_input_tokens")),
                field(name("output_tokens")),
                field(name("reasoning_output_tokens")),
                field(name("duration_ms")),
                field(name("state")),
                field(name("error_message"))
            )
            .values(
                UUID.randomUUID(),
                job.documentId(),
                job.revisionId(),
                "TRANSLATION:" + translated.provider(),
                translated.providerThreadId(),
                Integer.toHexString(translated.translations().hashCode()),
                translated.usage().inputTokens(),
                translated.usage().cachedInputTokens(),
                translated.usage().outputTokens(),
                translated.usage().reasoningTokens(),
                translated.durationMillis(),
                translated.fallbackUsed() ? "FALLBACK" : "COMPLETED",
                translated.fallbackUsed() ? "Codex was unavailable; demo fallback was used." : null
            )
            .execute();
    }

    private void saveReview(JobClaim job, List<SegmentData> segments, List<Finding> findings) {
        Map<UUID, List<Finding>> bySegment = new HashMap<>();
        findings.forEach(item -> bySegment.computeIfAbsent(item.segmentId(), ignored -> new ArrayList<>()).add(item));
        for (SegmentData segment : segments) {
            UUID reviewId = UUID.randomUUID();
            List<Finding> segmentFindings = bySegment.getOrDefault(segment.id(), List.of());
            String result = segmentFindings.stream().anyMatch(item -> "ERROR".equals(item.severity()))
                ? "QA_FLAGGED"
                : "QA_PASSED";
            database.insertInto(table(name("review")))
                .columns(
                    field(name("id")),
                    field(name("document_id")),
                    field(name("revision_id")),
                    field(name("segment_id")),
                    field(name("segment_version")),
                    field(name("rule_set_version")),
                    field(name("result")),
                    field(name("completed_at"))
                )
                .values(
                    reviewId,
                    job.documentId(),
                    job.revisionId(),
                    segment.id(),
                    1,
                    job.expectedRuleSetVersion(),
                    result,
                    OffsetDateTime.now()
                )
                .execute();
            for (Finding finding : segmentFindings) {
                database.insertInto(table(name("review_finding")))
                    .columns(
                        field(name("id")),
                        field(name("review_id")),
                        field(name("code")),
                        field(name("severity")),
                        field(name("message")),
                        field(name("page_number")),
                        field(name("metadata_json"))
                    )
                    .values(
                        UUID.randomUUID(),
                        reviewId,
                        finding.code(),
                        finding.severity(),
                        finding.message(),
                        finding.pageNumber(),
                        JSONB.valueOf(objectMapper.writeValueAsString(finding.metadata()))
                    )
                    .execute();
            }
            database.update(table(name("segment")))
                .set(field(name("status")), result)
                .where(field(name("id"), UUID.class).eq(segment.id()))
                .execute();
        }
    }

    private void saveLayoutFindings(UUID revisionId, List<Finding> findings) {
        for (Finding finding : findings) {
            database.insertInto(table(name("layout_finding")))
                .columns(
                    field(name("id")),
                    field(name("revision_id")),
                    field(name("page_number")),
                    field(name("segment_id")),
                    field(name("code")),
                    field(name("severity")),
                    field(name("message")),
                    field(name("metadata_json"))
                )
                .values(
                    UUID.randomUUID(),
                    revisionId,
                    finding.pageNumber(),
                    finding.segmentId(),
                    finding.code(),
                    finding.severity(),
                    finding.message(),
                    JSONB.valueOf(objectMapper.writeValueAsString(finding.metadata()))
                )
                .execute();
        }
    }

    private void updateProgress(UUID jobId, String stage, int current) {
        database.update(table(name("workflow_job")))
            .set(field(name("current_stage")), stage)
            .set(field(name("progress_current")), current)
            .where(field(name("id"), UUID.class).eq(jobId))
            .execute();
    }

    private JobView toJob(Record record) {
        return new JobView(
            record.get("id", UUID.class),
            record.get("document_id", UUID.class),
            record.get("revision_id", UUID.class),
            record.get("state", String.class),
            record.get("current_stage", String.class),
            record.get("progress_current", Integer.class),
            record.get("progress_total", Integer.class),
            record.get("attempts", Integer.class),
            record.get("error_code", String.class),
            record.get("error_message", String.class),
            record.get("created_at", OffsetDateTime.class),
            record.get("started_at", OffsetDateTime.class),
            record.get("completed_at", OffsetDateTime.class)
        );
    }

    private RevisionView toRevision(UUID projectId, UUID documentId, Record record) {
        UUID revisionId = record.get("id", UUID.class);
        List<FindingView> findings = loadFindings(revisionId);
        Record usage = database.select(
                DSL.coalesce(DSL.sum(field(name("input_tokens"), Long.class)), 0L),
                DSL.coalesce(DSL.sum(field(name("cached_input_tokens"), Long.class)), 0L),
                DSL.coalesce(DSL.sum(field(name("output_tokens"), Long.class)), 0L),
                DSL.coalesce(DSL.sum(field(name("reasoning_output_tokens"), Long.class)), 0L),
                DSL.coalesce(DSL.sum(field(name("duration_ms"), Long.class)), 0L)
            )
            .from(table(name("ai_invocation")))
            .where(field(name("revision_id"), UUID.class).eq(revisionId))
            .fetchOne();
        String output = record.get("output_path", String.class);
        return new RevisionView(
            revisionId,
            record.get("revision_number", Integer.class),
            record.get("state", String.class),
            output == null ? null : "/api/projects/%s/documents/%s/revisions/%s/download"
                .formatted(projectId, documentId, revisionId),
            record.get("created_at", OffsetDateTime.class),
            record.get("approved_at", OffsetDateTime.class),
            findings,
            new UsageView(
                usage.get(0, Long.class),
                usage.get(1, Long.class),
                usage.get(2, Long.class),
                usage.get(3, Long.class),
                usage.get(4, Long.class)
            )
        );
    }

    private List<FindingView> loadFindings(UUID revisionId) {
        var review = table(name("review")).as("r");
        var finding = table(name("review_finding")).as("f");
        var segment = table(name("segment")).as("s");
        List<FindingView> results = database.select(
                field(name("f", "id"), UUID.class),
                field(name("f", "code"), String.class),
                field(name("f", "severity"), String.class),
                field(name("f", "message"), String.class),
                field(name("f", "page_number"), Integer.class),
                field(name("r", "segment_id"), UUID.class)
            )
            .from(finding)
            .join(review).on(field(name("r", "id")).eq(field(name("f", "review_id"))))
            .leftJoin(segment).on(field(name("s", "id")).eq(field(name("r", "segment_id"))))
            .where(field(name("r", "revision_id"), UUID.class).eq(revisionId))
            .fetch(record -> new FindingView(
                record.get(0, UUID.class),
                record.get(1, String.class),
                record.get(2, String.class),
                record.get(3, String.class),
                record.get(4, Integer.class),
                record.get(5, UUID.class)
            ));
        results.addAll(database.select()
            .from(table(name("layout_finding")))
            .where(field(name("revision_id"), UUID.class).eq(revisionId))
            .fetch(item -> new FindingView(
                item.get("id", UUID.class),
                item.get("code", String.class),
                item.get("severity", String.class),
                item.get("message", String.class),
                item.get("page_number", Integer.class),
                item.get("segment_id", UUID.class)
            )));
        return results;
    }

    private String abbreviate(String message) {
        return message.length() <= 1_000 ? message : message.substring(0, 1_000);
    }

    record JobClaim(
        UUID id,
        UUID documentId,
        UUID revisionId,
        int expectedDocumentVersion,
        int expectedRuleSetVersion,
        int attempts
    ) {
    }

    private record SegmentData(
        UUID id,
        int blockOrder,
        String sourceText,
        String boundingBox,
        String style,
        int pageNumber,
        String renderPath
    ) {
    }

    private record PageData(
        UUID id,
        int pageNumber,
        BigDecimal width,
        BigDecimal height,
        String renderPath
    ) {
    }
}
