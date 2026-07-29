package io.verbatim.project;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import io.verbatim.common.ApiException;
import io.verbatim.project.ProjectModels.CreateProjectRequest;
import io.verbatim.project.ProjectModels.ProjectView;
import io.verbatim.project.ProjectModels.RuleInput;
import io.verbatim.project.ProjectModels.RuleSetView;
import io.verbatim.project.ProjectModels.RuleView;
import io.verbatim.project.ProjectModels.UpdateRulesRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProjectService {

    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BigDecimal DEFAULT_MINIMUM_FONT_SCALE = new BigDecimal("0.78");

    private final DSLContext database;
    private final ObjectMapper objectMapper;

    public ProjectService(DSLContext database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    public List<ProjectView> list() {
        var project = table(name("project")).as("p");
        var document = table(name("document")).as("d");
        return database
            .select(
                field(name("p", "id"), UUID.class),
                field(name("p", "name"), String.class),
                field(name("p", "default_source_locale"), String.class),
                field(name("p", "default_target_locale"), String.class),
                field(name("p", "rule_set_version"), Integer.class),
                field(name("p", "minimum_font_scale"), BigDecimal.class),
                field(name("p", "created_at"), OffsetDateTime.class),
                field("count(d.id)", Long.class)
            )
            .from(project)
            .leftJoin(document).on(field(name("d", "project_id")).eq(field(name("p", "id"))))
            .groupBy(
                field(name("p", "id")),
                field(name("p", "name")),
                field(name("p", "default_source_locale")),
                field(name("p", "default_target_locale")),
                field(name("p", "rule_set_version")),
                field(name("p", "minimum_font_scale")),
                field(name("p", "created_at"))
            )
            .orderBy(field(name("p", "created_at")).desc())
            .fetch(record -> toProject(record, record.get(7, Long.class)));
    }

    public ProjectView get(UUID projectId) {
        Record record = database.select()
            .from(table(name("project")))
            .where(field(name("id"), UUID.class).eq(projectId))
            .fetchOne();
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found.");
        }
        Long count = database.selectCount()
            .from(table(name("document")))
            .where(field(name("project_id"), UUID.class).eq(projectId))
            .fetchOne(0, Long.class);
        return toProject(record, count == null ? 0 : count);
    }

    @Transactional
    public ProjectView create(CreateProjectRequest request) {
        UUID projectId = UUID.randomUUID();
        BigDecimal minimumScale = request.minimumFontScale() == null
            ? DEFAULT_MINIMUM_FONT_SCALE
            : request.minimumFontScale();
        database.insertInto(table(name("project")))
            .columns(
                field(name("id")),
                field(name("workspace_id")),
                field(name("name")),
                field(name("default_source_locale")),
                field(name("default_target_locale")),
                field(name("minimum_font_scale"))
            )
            .values(
                projectId,
                WORKSPACE_ID,
                request.name().trim(),
                request.defaultSourceLocale(),
                request.defaultTargetLocale(),
                minimumScale
            )
            .execute();
        return get(projectId);
    }

    public RuleSetView getRules(UUID projectId) {
        ProjectView project = get(projectId);
        List<RuleView> rules = database.select()
            .from(table(name("project_rule")))
            .where(field(name("project_id"), UUID.class).eq(projectId))
            .and(field(name("version"), Integer.class).eq(project.ruleSetVersion()))
            .and(field(name("active"), Boolean.class).isTrue())
            .orderBy(field(name("rule_type")), field(name("name")))
            .fetch(this::toRule);
        return new RuleSetView(projectId, project.ruleSetVersion(), project.minimumFontScale(), rules);
    }

    @Transactional
    public RuleSetView updateRules(UUID projectId, UpdateRulesRequest request) {
        ProjectView project = get(projectId);
        int nextVersion = project.ruleSetVersion() + 1;
        BigDecimal minimumScale = request.minimumFontScale() == null
            ? project.minimumFontScale()
            : request.minimumFontScale();

        database.update(table(name("project_rule")))
            .set(field(name("active")), false)
            .where(field(name("project_id"), UUID.class).eq(projectId))
            .and(field(name("active"), Boolean.class).isTrue())
            .execute();

        for (RuleInput rule : request.rules()) {
            database.insertInto(table(name("project_rule")))
                .columns(
                    field(name("id")),
                    field(name("project_id")),
                    field(name("version")),
                    field(name("rule_type")),
                    field(name("source_locale")),
                    field(name("target_locale")),
                    field(name("name")),
                    field(name("value_json")),
                    field(name("active"))
                )
                .values(
                    UUID.randomUUID(),
                    projectId,
                    nextVersion,
                    rule.type(),
                    rule.sourceLocale(),
                    rule.targetLocale(),
                    rule.name(),
                    JSONB.valueOf(objectMapper.writeValueAsString(
                        rule.value() == null ? Map.of() : rule.value()
                    )),
                    true
                )
                .execute();
        }

        database.update(table(name("project")))
            .set(field(name("rule_set_version")), nextVersion)
            .set(field(name("minimum_font_scale")), minimumScale)
            .set(field(name("updated_at")), OffsetDateTime.now())
            .where(field(name("id"), UUID.class).eq(projectId))
            .execute();
        return getRules(projectId);
    }

    private ProjectView toProject(Record record, long documentCount) {
        return new ProjectView(
            record.get("id", UUID.class),
            record.get("name", String.class),
            record.get("default_source_locale", String.class),
            record.get("default_target_locale", String.class),
            record.get("rule_set_version", Integer.class),
            record.get("minimum_font_scale", BigDecimal.class),
            record.get("created_at", OffsetDateTime.class),
            documentCount
        );
    }

    private RuleView toRule(Record record) {
        JSONB json = record.get("value_json", JSONB.class);
        Map<String, Object> value = objectMapper.readValue(
            json == null ? "{}" : json.data(),
            new TypeReference<>() {
            }
        );
        return new RuleView(
            record.get("id", UUID.class),
            record.get("version", Integer.class),
            record.get("rule_type", String.class),
            record.get("name", String.class),
            record.get("source_locale", String.class),
            record.get("target_locale", String.class),
            value
        );
    }
}
