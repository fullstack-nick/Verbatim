package io.verbatim.translationmemory;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import io.verbatim.project.ProjectService;
import io.verbatim.translationmemory.TranslationMemoryModels.CreateMemoryRequest;
import io.verbatim.translationmemory.TranslationMemoryModels.MemoryView;
import io.verbatim.translationmemory.TranslationMemoryModels.MemorySuggestion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TranslationMemoryService {

    private final DSLContext database;
    private final ProjectService projects;
    private final EmbeddingClient embeddings;

    public TranslationMemoryService(
        DSLContext database,
        ProjectService projects,
        EmbeddingClient embeddings
    ) {
        this.database = database;
        this.projects = projects;
        this.embeddings = embeddings;
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
        database.execute(
            """
            INSERT INTO translation_memory_entry(
                id, project_id, source_locale, target_locale,
                source_text, target_text, source_embedding, approved_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?)
            """,
            id,
            projectId,
            request.sourceLocale(),
            request.targetLocale(),
            request.sourceText(),
            request.targetText(),
            vectorLiteral(embeddings.embed(request.sourceText())),
            approvedAt
        );
        return new MemoryView(
            id,
            request.sourceLocale(),
            request.targetLocale(),
            request.sourceText(),
            request.targetText(),
            approvedAt
        );
    }

    public List<MemorySuggestion> suggestions(
        UUID projectId,
        String sourceLocale,
        String targetLocale,
        String sourceText,
        int limit
    ) {
        projects.get(projectId);
        Field<Double> distance = field(
            "{0} <=> {1}::vector",
            Double.class,
            field(name("source_embedding")),
            DSL.val(vectorLiteral(embeddings.embed(sourceText)))
        );
        return database.select(
                field(name("id"), UUID.class),
                field(name("source_text"), String.class),
                field(name("target_text"), String.class),
                DSL.one().minus(distance).as("similarity")
            )
            .from(table(name("translation_memory_entry")))
            .where(field(name("project_id"), UUID.class).eq(projectId))
            .and(field(name("source_locale"), String.class).eq(sourceLocale))
            .and(field(name("target_locale"), String.class).eq(targetLocale))
            .and(field(name("source_embedding")).isNotNull())
            .orderBy(distance)
            .limit(Math.max(1, Math.min(limit, 20)))
            .fetch(record -> new MemorySuggestion(
                record.get(0, UUID.class),
                record.get(1, String.class),
                record.get(2, String.class),
                record.get(3, Double.class)
            ));
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(vector[index]);
        }
        return value.append(']').toString();
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
