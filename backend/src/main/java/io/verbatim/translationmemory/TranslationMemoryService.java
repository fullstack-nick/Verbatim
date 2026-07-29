package io.verbatim.translationmemory;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import io.verbatim.project.ProjectService;
import io.verbatim.translationmemory.TranslationMemoryModels.CreateMemoryRequest;
import io.verbatim.translationmemory.TranslationMemoryModels.MemoryView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TranslationMemoryService {

    private final DSLContext database;
    private final ProjectService projects;

    public TranslationMemoryService(DSLContext database, ProjectService projects) {
        this.database = database;
        this.projects = projects;
    }

    public List<MemoryView> list(UUID projectId) {
        projects.get(projectId);
        return database.select()
            .from(table(name("translation_memory_entry")))
            .where(field(name("project_id"), UUID.class).eq(projectId))
            .orderBy(field(name("approved_at")).desc())
            .limit(200)
            .fetch(this::toView);
    }

    @Transactional
    public MemoryView create(UUID projectId, CreateMemoryRequest request) {
        projects.get(projectId);
        UUID id = UUID.randomUUID();
        OffsetDateTime approvedAt = OffsetDateTime.now();
        database.insertInto(table(name("translation_memory_entry")))
            .columns(
                field(name("id")),
                field(name("project_id")),
                field(name("source_locale")),
                field(name("target_locale")),
                field(name("source_text")),
                field(name("target_text")),
                field(name("approved_at"))
            )
            .values(
                id,
                projectId,
                request.sourceLocale(),
                request.targetLocale(),
                request.sourceText(),
                request.targetText(),
                approvedAt
            )
            .execute();
        return new MemoryView(
            id,
            request.sourceLocale(),
            request.targetLocale(),
            request.sourceText(),
            request.targetText(),
            approvedAt
        );
    }

    private MemoryView toView(Record record) {
        return new MemoryView(
            record.get("id", UUID.class),
            record.get("source_locale", String.class),
            record.get("target_locale", String.class),
            record.get("source_text", String.class),
            record.get("target_text", String.class),
            record.get("approved_at", OffsetDateTime.class)
        );
    }
}
