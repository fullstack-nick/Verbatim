package io.verbatim.terminology;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import io.verbatim.project.ProjectService;
import io.verbatim.terminology.TerminologyModels.CreateTermRequest;
import io.verbatim.terminology.TerminologyModels.TermView;
import io.verbatim.terminology.TerminologyModels.TranslationInput;
import io.verbatim.terminology.TerminologyModels.TranslationView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminologyService {

    private final DSLContext database;
    private final ProjectService projects;

    public TerminologyService(DSLContext database, ProjectService projects) {
        this.database = database;
        this.projects = projects;
    }

    public List<TermView> list(UUID projectId) {
        projects.get(projectId);
        return database.select()
            .from(table(name("term_entry")))
            .where(field(name("project_id"), UUID.class).eq(projectId))
            .orderBy(field(name("source_term")))
            .fetch(this::toTerm);
    }

    @Transactional
    public TermView create(UUID projectId, CreateTermRequest request) {
        projects.get(projectId);
        UUID termId = UUID.randomUUID();
        database.insertInto(table(name("term_entry")))
            .columns(
                field(name("id")),
                field(name("project_id")),
                field(name("source_locale")),
                field(name("source_term")),
                field(name("matching_type")),
                field(name("case_mode")),
                field(name("translation_preference"))
            )
            .values(
                termId,
                projectId,
                request.sourceLocale(),
                request.sourceTerm(),
                request.matchingType(),
                request.caseMode(),
                request.translationPreference()
            )
            .execute();
        for (TranslationInput translation : request.translations()) {
            database.insertInto(table(name("term_translation")))
                .columns(
                    field(name("id")),
                    field(name("term_entry_id")),
                    field(name("locale")),
                    field(name("text")),
                    field(name("usage"))
                )
                .values(
                    UUID.randomUUID(),
                    termId,
                    translation.locale(),
                    translation.text(),
                    translation.usage()
                )
                .execute();
        }
        bumpRuleVersion(projectId);
        return database.select()
            .from(table(name("term_entry")))
            .where(field(name("id"), UUID.class).eq(termId))
            .fetchOne(this::toTerm);
    }

    @Transactional
    public void delete(UUID projectId, UUID termId) {
        int deleted = database.deleteFrom(table(name("term_entry")))
            .where(field(name("id"), UUID.class).eq(termId))
            .and(field(name("project_id"), UUID.class).eq(projectId))
            .execute();
        if (deleted > 0) {
            bumpRuleVersion(projectId);
        }
    }

    private TermView toTerm(Record record) {
        UUID termId = record.get("id", UUID.class);
        List<TranslationView> translations = database.select()
            .from(table(name("term_translation")))
            .where(field(name("term_entry_id"), UUID.class).eq(termId))
            .orderBy(field(name("locale")), field(name("usage")))
            .fetch(translation -> new TranslationView(
                translation.get("id", UUID.class),
                translation.get("locale", String.class),
                translation.get("text", String.class),
                translation.get("usage", String.class)
            ));
        return new TermView(
            termId,
            record.get("source_locale", String.class),
            record.get("source_term", String.class),
            record.get("matching_type", String.class),
            record.get("case_mode", String.class),
            record.get("translation_preference", String.class),
            translations
        );
    }

    private void bumpRuleVersion(UUID projectId) {
        database.update(table(name("project")))
            .set(
                field(name("rule_set_version"), Integer.class),
                field(name("rule_set_version"), Integer.class).add(1)
            )
            .set(field(name("updated_at")), OffsetDateTime.now())
            .where(field(name("id"), UUID.class).eq(projectId))
            .execute();
    }
}
