CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE workspace (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    default_source_locale TEXT NOT NULL,
    default_target_locale TEXT NOT NULL,
    rule_set_version INTEGER NOT NULL DEFAULT 1,
    minimum_font_scale NUMERIC(4, 2) NOT NULL DEFAULT 0.78,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project_rule (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    rule_type TEXT NOT NULL,
    source_locale TEXT,
    target_locale TEXT,
    name TEXT NOT NULL,
    value_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX project_rule_project_version_idx
    ON project_rule(project_id, version);

CREATE TABLE term_entry (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    source_locale TEXT NOT NULL,
    source_term TEXT NOT NULL,
    matching_type TEXT NOT NULL,
    case_mode TEXT NOT NULL,
    translation_preference TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE term_translation (
    id UUID PRIMARY KEY,
    term_entry_id UUID NOT NULL REFERENCES term_entry(id) ON DELETE CASCADE,
    locale TEXT NOT NULL,
    text TEXT NOT NULL,
    usage TEXT NOT NULL
);

CREATE INDEX term_entry_project_idx ON term_entry(project_id, source_locale);
CREATE INDEX term_translation_locale_idx ON term_translation(term_entry_id, locale);

CREATE TABLE translation_memory_entry (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    source_locale TEXT NOT NULL,
    target_locale TEXT NOT NULL,
    source_text TEXT NOT NULL,
    target_text TEXT NOT NULL,
    source_embedding vector(384),
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX translation_memory_scope_idx
    ON translation_memory_entry(project_id, source_locale, target_locale);

CREATE INDEX translation_memory_embedding_idx
    ON translation_memory_entry USING hnsw (source_embedding vector_cosine_ops);

CREATE TABLE document (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    source_filename TEXT NOT NULL,
    source_path TEXT NOT NULL,
    source_checksum TEXT NOT NULL,
    source_locale TEXT NOT NULL,
    target_locale TEXT NOT NULL,
    page_count INTEGER,
    digital_page_count INTEGER NOT NULL DEFAULT 0,
    scanned_page_count INTEGER NOT NULL DEFAULT 0,
    mixed_page_count INTEGER NOT NULL DEFAULT 0,
    state TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX document_project_idx ON document(project_id, created_at DESC);

CREATE TABLE document_page (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL,
    page_type TEXT NOT NULL,
    width NUMERIC(12, 4) NOT NULL,
    height NUMERIC(12, 4) NOT NULL,
    rotation INTEGER NOT NULL DEFAULT 0,
    render_path TEXT,
    ocr_confidence NUMERIC(5, 4),
    UNIQUE(document_id, page_number)
);

CREATE TABLE segment (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    page_id UUID NOT NULL REFERENCES document_page(id) ON DELETE CASCADE,
    block_order INTEGER NOT NULL,
    block_type TEXT NOT NULL,
    source_text TEXT NOT NULL,
    target_text TEXT,
    bounding_box JSONB NOT NULL,
    style_json JSONB NOT NULL,
    extraction_method TEXT NOT NULL,
    confidence NUMERIC(5, 4),
    version INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX segment_document_order_idx
    ON segment(document_id, page_id, block_order);

CREATE TABLE document_revision (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    project_rule_set_version INTEGER NOT NULL,
    state TEXT NOT NULL,
    output_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    UNIQUE(document_id, revision_number)
);

CREATE TABLE revision_instruction (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    revision_id UUID REFERENCES document_revision(id) ON DELETE SET NULL,
    scope TEXT NOT NULL,
    scope_reference TEXT,
    effect TEXT NOT NULL,
    message TEXT NOT NULL,
    promoted_to_project BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workflow_job (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    revision_id UUID REFERENCES document_revision(id) ON DELETE CASCADE,
    job_type TEXT NOT NULL,
    state TEXT NOT NULL,
    current_stage TEXT NOT NULL,
    progress_current INTEGER NOT NULL DEFAULT 0,
    progress_total INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    expected_document_version INTEGER NOT NULL,
    expected_rule_set_version INTEGER NOT NULL,
    error_code TEXT,
    error_message TEXT,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX workflow_job_claim_idx
    ON workflow_job(state, available_at, created_at);

CREATE TABLE review (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    revision_id UUID REFERENCES document_revision(id) ON DELETE CASCADE,
    segment_id UUID REFERENCES segment(id) ON DELETE CASCADE,
    segment_version INTEGER,
    rule_set_version INTEGER NOT NULL,
    result TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE review_finding (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES review(id) ON DELETE CASCADE,
    code TEXT NOT NULL,
    severity TEXT NOT NULL,
    message TEXT NOT NULL,
    page_number INTEGER,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE layout_finding (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES document_revision(id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL,
    segment_id UUID REFERENCES segment(id) ON DELETE SET NULL,
    code TEXT NOT NULL,
    severity TEXT NOT NULL,
    message TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE ai_invocation (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    revision_id UUID REFERENCES document_revision(id) ON DELETE SET NULL,
    stage TEXT NOT NULL,
    batch_number INTEGER,
    provider_thread_id TEXT,
    context_hash TEXT NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    cached_input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    reasoning_output_tokens BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    state TEXT NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY,
    scope TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    resource_id UUID NOT NULL,
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(scope, idempotency_key)
);

INSERT INTO workspace(id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Local workspace');
