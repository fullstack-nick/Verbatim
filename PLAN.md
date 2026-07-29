# Verbatim - Product and Implementation Plan

## Implementation status (v0.1)

The runnable v0.1 release implements the complete local workflow described by
the core promise: project controls, digital and printed-scan ingestion, Codex
translation/OCR/visual review, deterministic QA, same-page PDF composition,
revision chat, approval-gated translation memory, token reporting, and export.

The plan remains intentionally broader than v0.1. Automatic locale detection,
targeted layout-only regeneration, cancellation/ETA controls, per-page
checkpoints across a process restart, and additional script-shaping suites are
the next hardening track rather than hidden claims about the current build.
The exact verified behavior and release boundaries are recorded in
[README.md](README.md) and [docs/VALIDATION.md](docs/VALIDATION.md).

## 1. Product summary

Verbatim is a local, project-aware PDF translation workstation.

It translates digitally generated and high-quality scanned PDFs while preserving the source document's page structure and visual style. It combines project terminology, translation memory, user-defined translation rules, deterministic linguistic QA, PDF layout reconstruction, and Codex-assisted visual review.

The product is intentionally local-first and distributed as a public GitHub repository. Users run Verbatim on their own machine and authenticate their local Codex installation with either their ChatGPT/Codex account or an OpenAI API key.

### Core promise

> Translate a PDF into a selected target language, preserve its page count and visual structure, show exactly which rules influenced the translation, and let the user iteratively improve the result before approving and exporting it.

## 2. Product principles

1. **Preserve the document, not only the words.**
   Typography, hierarchy, spacing, images, tables, charts, headers, footers, and page geometry are part of the result.

2. **Keep linguistic steering visible.**
   Terminology, style guidance, translation memory, and document instructions are visible and editable in the frontend.

3. **Keep deterministic checks authoritative.**
   Codex translates and supplies visual or linguistic observations. Deterministic validation decides whether required technical properties hold.

4. **Never hide an unacceptable compromise.**
   If translated text cannot fit above the configured minimum font scale, Verbatim flags the page. It does not silently shrink text into unreadability or add pages.

5. **Treat the database as the source of truth.**
   Project rules, document state, revisions, prompts, findings, and usage are versioned in the application. Model conversation history is never the only copy of important context.

6. **Make long jobs resumable.**
   Page and segment batches are checkpointed. A failure late in a 100-page document must not restart completed work.

7. **Require human approval before learning.**
   A translated document contributes entries to translation memory only after explicit approval.

## 3. Release scope

### Included

- Local, single-user application with one workspace and multiple projects.
- React, TypeScript, and Tailwind frontend.
- Java 25 and Spring Boot 4 backend.
- Digitally generated PDFs with selectable text.
- High-quality scanned PDFs containing printed text.
- PDFs containing a mixture of digital and scanned pages.
- Headings, paragraphs, lists, tables, images, charts, basic vector graphics, headers, and footers.
- Source-language selection and optional automatic detection.
- Target-language selection.
- Initial production support for Latin, Cyrillic, and Greek scripts.
- Architecture that allows later support for RTL, CJK, and other scripts.
- Project-level terminology, translation rules, style guidance, and translation memory.
- Document-specific instructions and a revision chat.
- Asynchronous processing with progress, approximate remaining time, cancellation, retry, and resume.
- Token usage reporting by document, revision, stage, and Codex invocation.
- Side-by-side source and translated PDF review.
- PDF export and structured QA findings.

### Explicitly excluded from the first release

- Website translation.
- Handwriting recognition.
- Translation of text printed over photographs, illustrations, diagrams, or heavily textured backgrounds.
- Arbitrary image editing.
- A general-purpose PDF editor.
- User invitations, permissions, billing, SSO, and hosted multi-tenancy.
- Full AWS infrastructure.
- Multiple deployable microservices.
- Model training.
- Guaranteed support for every language or writing system.
- Automatic addition to translation memory without human approval.
- A hard user-configured token budget that pauses a document.

Text over unsupported image backgrounds is detected when possible and produces a structured finding rather than an unreliable translation.

## 4. Hard product invariants

- Output page count equals source page count.
- Output page dimensions equal source page dimensions.
- The source and target locales are recorded on every document.
- Every document revision records the project rule-set version used to create it.
- Every translated segment remains linked to its page and layout block.
- No approved output contains clipped text, overlapping text blocks, unreadable glyphs, or content outside page bounds.
- The configured minimum font scale is never crossed automatically.
- Translation-memory retrieval is scoped to the same project and language pair.
- An old asynchronous result never overwrites a newer segment or document revision.
- Project rules are changed only through explicit project-rule actions.
- Chat instructions default to document scope.
- Promoting a chat instruction to a project rule requires explicit confirmation.
- Low-confidence OCR is visible to the user.
- Unresolved deterministic errors produce a flagged result.

## 5. Primary user journey

1. The user creates a project.
2. The user chooses default source and target locales.
3. The user optionally configures:
   - Terminology and never-translate rules.
   - Preferred, admitted, discouraged, and obsolete translations.
   - Tone, formality, audience, capitalization, punctuation, number, date, and currency rules.
   - Free-form translation guidance.
   - Translation-memory entries.
   - Minimum font scale and font fallback policy.
4. The user uploads a PDF.
5. Verbatim classifies every page as `DIGITAL`, `SCANNED`, or `MIXED`.
6. Verbatim extracts or recognizes text, layout, reading order, and style.
7. The user confirms or overrides the detected source language and chooses the target language.
8. Verbatim creates a document brief and divides the content into translation batches.
9. Codex translates each batch using the applicable project rules, terminology, translation memory, document brief, and nearby context.
10. Deterministic linguistic QA reviews the translated segments.
11. Verbatim reconstructs the PDF without changing its page count.
12. Deterministic layout checks and Codex visual review inspect rendered output pages.
13. The user reviews progress, findings, token usage, and a side-by-side comparison.
14. The user can request changes through document chat.
15. Verbatim regenerates only the affected translations or layouts and creates a new immutable revision.
16. The user explicitly approves a revision.
17. Approved source-target pairs are added to translation memory.
18. The user exports the approved PDF and may export the structured QA report.

## 6. Translation control model

### 6.1 Project-level configuration

Project configuration applies to future document revisions until changed.

It contains:

- Default locales and permitted target locales.
- Structured terminology.
- Never-translate rules.
- Translation preferences.
- Locale-specific style guidance.
- General project translation instructions.
- Translation memory.
- Font fallbacks.
- Minimum font scale.

All changes create a new immutable `project_rule_set_version`.

Changing project rules does not silently rewrite completed documents. The user may explicitly regenerate a document with the latest project rules.

### 6.2 Document-level instructions

Document instructions apply only to one document. Examples:

- "Use a formal tone in this report."
- "Keep product names in English."
- "Translate the appendix less formally."

They are stored independently from project rules and are included in every applicable regeneration of that document.

### 6.3 Revision instructions

A chat message becomes a structured revision instruction with one of these scopes:

- Selected block or segment.
- Selected page.
- Current document - the default.
- Future project documents - only after explicit promotion.

Instructions are also classified by effect:

- `TRANSLATION`
- `LAYOUT`
- `BOTH`

A layout-only instruction must not rerun translation. A local terminology correction should regenerate only affected segments and pages.

### 6.4 Effective-instructions view

Before translation or regeneration, the frontend exposes an "Effective translation instructions" view containing:

- Project rule-set version.
- Applicable terminology.
- Applicable style rules.
- Document instructions.
- Relevant translation-memory entries.
- Applicable revision instructions.

At segment level, a "Why this translation?" view shows which of these inputs influenced the result.

## 7. Document and PDF processing

### 7.1 Page classification

Classification happens per page rather than per document:

- `DIGITAL`: usable PDF text objects exist.
- `SCANNED`: the page is primarily a raster image without usable text.
- `MIXED`: meaningful digital text and rasterized document content coexist.

### 7.2 Normalized document model

Both ingestion paths produce the same internal model:

```text
Document
  Page
    LayoutBlock
      TextLine
        TextSpan
```

The normalized model records:

- Page number, width, height, and rotation.
- Block type and reading order.
- Bounding boxes.
- Source text.
- Font family or estimated font category.
- Font size, weight, style, and color.
- Alignment and writing direction.
- Line height, character spacing, and paragraph spacing.
- OCR confidence and extraction method.
- Relationships to tables, lists, captions, headers, and footers.

### 7.3 Digital-page pipeline

1. Inspect the PDF and embedded fonts.
2. Extract text spans, glyph positions, styles, and reading order.
3. Detect semantic layout blocks.
4. Preserve non-text content and geometry.
5. Convert layout blocks into translation segments.
6. Recompose translated text into the original page geometry.
7. Use a raster-background fallback only for unsupported content-stream structures.
8. Emit `RASTER_FALLBACK_USED` when fallback changes output characteristics.

### 7.4 Scanned-page pipeline

1. Render the page at a configured high resolution.
2. Detect orientation and deskew when necessary.
3. Detect printed-text regions.
4. Run OCR and retain word-level bounding boxes and confidence.
5. Group words into lines and semantic layout blocks.
6. Detect normal paper backgrounds, borders, table lines, images, and unsupported textured regions.
7. Remove source text while reconstructing the underlying background.
8. Estimate font category, size, weight, color, and alignment.
9. Typeset selectable translated text over the cleaned page background.
10. Flag uncertain OCR, damaged backgrounds, and unsupported image text.
11. Render and inspect the reconstructed page.

### 7.5 Text fitting

The fitting algorithm applies adjustments in a deterministic order:

1. Preserve original font properties and size.
2. Recalculate line wrapping.
3. Use available whitespace inside the original block.
4. Adjust paragraph and line spacing within configured tolerances.
5. Reduce font size without crossing the minimum font scale.
6. Try a compatible font fallback if glyph coverage requires it.
7. Produce `TEXT_OVERFLOW` if the content still does not fit.

The algorithm never adds a page automatically.

### 7.6 PDF skill

The repository will contain:

```text
.agents/skills/verbatim-pdf-layout/
  SKILL.md
  scripts/
  references/
```

The skill adapts the PDF render-and-verify workflow for Verbatim. It requires Codex to:

- Render every materially changed page to PNG.
- Inspect the latest revision, not a stale render.
- Verify typography, spacing, margins, tables, images, charts, headers, footers, and page transitions.
- Reject clipping, overlap, broken layout, unreadable glyphs, or corrupted assets.
- Return structured findings using the application's JSON schema.
- Stop after the configured maximum layout attempts and flag unresolved pages.
- Keep intermediate artifacts under `tmp/pdfs/`.
- Write final PDF artifacts under `output/pdf/`.

The skill guides Codex. Deterministic application code and scripts still perform extraction, rendering, composition, measurement, and comparison.

## 8. Multilingual design

### 8.1 Locale model

- Store locales as BCP 47 tags.
- Store source and target locale on each document.
- Scope terms and translation-memory entries by locale.
- Allow source-language auto-detection with confidence and manual override.
- Validate that OCR data and target fonts support the selected scripts before starting an expensive translation.

### 8.2 Initial script support

The first release supports:

- Latin.
- Cyrillic.
- Greek.

The architecture must not encode assumptions that block:

- Right-to-left scripts.
- CJK line breaking.
- Indic shaping.
- Vertical text.

These remain later capability increments and require their own acceptance suites.

### 8.3 Capability reporting

The frontend displays available language combinations based on:

- Translation-provider support.
- Installed OCR language packs.
- Available fonts and glyph coverage.
- Renderer capabilities.

Unsupported combinations are not presented as fully supported merely because they can be selected as strings.

## 9. Large-document strategy

A complete document is never sent to Codex as one prompt.

### 9.1 Document analysis

After extraction, Verbatim creates a versioned `DocumentBrief` containing:

- Subject and likely audience.
- Tone and formality.
- Names, product names, and abbreviations.
- Recurring document-specific terminology.
- Section hierarchy.
- Warnings about ambiguous or low-confidence content.

### 9.2 Batching

- Divide segments using a configurable token target.
- Keep semantic sections together when practical.
- Include limited preceding and following context.
- Include only matched terminology and relevant translation-memory entries.
- Checkpoint every completed batch.
- Apply bounded parallelism.
- Retry batches independently.

### 9.3 Consistency pass

After batch translation, run a document-wide consistency check for:

- A source term translated differently without justification.
- Names or abbreviations changed inconsistently.
- Tone or formality drift.
- Heading hierarchy drift.
- Number, date, or punctuation inconsistencies.

### 9.4 Resume behavior

Every job step is durable. Restarting the application resumes from the latest completed checkpoint. A failure on page 87 must not repeat successful work for pages 1 through 86.

## 10. Codex integration

### 10.1 Provider boundaries

Domain code depends on interfaces:

```java
public interface TranslationClient {
    TranslationResult translate(TranslationContext context);
}

public interface LinguisticReviewClient {
    LinguisticReview review(ReviewContext context);
}

public interface VisualReviewClient {
    VisualReview review(VisualReviewContext context);
}
```

The first adapters use the locally installed Codex CLI.

### 10.2 Local invocation

The Java backend invokes Codex non-interactively with:

- Ephemeral runs for bounded operations.
- Read-only sandboxing.
- Image inputs for visual review.
- JSONL event streaming.
- JSON Schema structured output.
- A repository-root working directory so the Verbatim PDF skill is discoverable.

The application checks `codex login status`. It never reads, copies, displays, or stores the user's Codex authentication file.

The default application data path is `.verbatim-data/`, which is excluded from Git. It is configurable for users who prefer another local storage location.

### 10.3 Context isolation

The document chat is not one indefinitely growing Codex thread.

Every invocation receives a canonical, bounded context package built from database state:

- Source segments.
- Nearby context.
- Project rule-set snapshot.
- Document brief.
- Matched terminology.
- Relevant translation-memory suggestions.
- Document and revision instructions.
- Previous target text for revision requests.
- Required structured output schema.

This makes runs reproducible and limits context pollution.

### 10.4 Invocation audit

Each Codex call stores:

- Stage and batch.
- Thread identifier when available.
- Prompt or context hash.
- Rule-set and document revision versions.
- Input, cached-input, output, and reasoning token usage.
- Duration.
- Exit state and retry count.
- Structured result location.

Sensitive document content remains local except for the content necessarily sent to the authenticated model provider.

## 11. Linguistic QA

### 11.1 Placeholder parity

Initially support:

```text
%{username}
{name}
%s
%d
```

Compare source and target placeholder multisets and detect:

- Missing placeholders.
- Extra placeholders.
- Duplicated placeholders.

### 11.2 HTML tag parity

Support a narrow safe subset:

```html
<strong>
<em>
<a>
<br>
```

Compare tag names and counts. Do not attempt to validate arbitrary HTML.

### 11.3 Terminology

Matching:

- `EXACT`
- `PREFIX`
- `FUZZY`

Case:

- `SENSITIVE`
- `INSENSITIVE`

Translation preference:

- `TRANSLATE`
- `NEVER_TRANSLATE`

Target usage:

- `PREFERRED`
- `ADMITTED`
- `NOT_RECOMMENDED`
- `OBSOLETE`

Behavior:

- Preferred: pass.
- Admitted: pass.
- Not recommended: warning.
- Obsolete: error.
- Required translation missing: error.
- Never-translate source changed: error.

### 11.4 Additional document checks

- Inconsistent translation across the document.
- Missing or duplicated numbers.
- Unsupported glyphs.
- Suspicious untranslated content.
- OCR uncertainty.
- Reading-order uncertainty.

Translation-memory similarity provides context. It does not independently determine pass or fail.

## 12. Layout and visual QA

### 12.1 Deterministic checks

- Page count and dimensions.
- Text inside expected bounding boxes.
- Text collision and clipping.
- Minimum font scale.
- Glyph availability.
- Block alignment and reading order.
- Header and footer consistency.
- Image, chart, table, border, and background placement.
- Non-text differences outside expected text masks.
- Background reconstruction quality.

### 12.2 Region-aware comparison

A full-page pixel diff is not sufficient because translated text is expected to change.

Verbatim compares:

1. **Immutable regions:** images, borders, backgrounds, charts, and non-text geometry outside text masks.
2. **Text geometry:** bounding boxes, alignment, font scale, line count, spacing, collisions, and overflow.
3. **Perceptual output:** source and target page renders reviewed by Codex using a structured visual-review schema.

### 12.3 Bounded iteration

Codex may suggest structured adjustments, but application code validates and applies them.

A page has a configured maximum number of layout attempts. An unresolved page becomes `LAYOUT_FLAGGED`; the application does not loop indefinitely.

## 13. Translation memory and terminology caching

### 13.1 Translation memory

- Approved source-target pairs only.
- Scoped by project, source locale, and target locale.
- Source embeddings stored with pgvector.
- Retrieve the three nearest approved entries during translation and review.
- Similarity suggestions never leak across projects or language pairs.

### 13.2 Embeddings

```java
public interface EmbeddingClient {
    float[] embed(String text);
}
```

Tests use a deterministic fake. Production uses a configured provider adapter.

### 13.3 Redis

Redis is used only to cache active project terminology and rule material for a target locale.

Example:

```text
project-rules:{projectId}:{sourceLocale}:{targetLocale}:v{ruleSetVersion}
```

A project-rule update creates a new version and therefore a new cache key. PostgreSQL remains authoritative.

## 14. Application architecture

### 14.1 Technology stack

Backend:

- Java 25.
- Spring Boot 4.
- jOOQ.
- PostgreSQL.
- pgvector.
- Redis.
- Flyway.
- Testcontainers.
- OpenAPI and Swagger UI.
- Micrometer metrics.

Frontend:

- React.
- TypeScript.
- Tailwind CSS.
- Vite.
- PDF side-by-side viewer.
- Server-Sent Events for progress.

PDF tooling:

- Java PDF processing adapter.
- Poppler rendering for verification.
- Python helper scripts where they provide more reliable PDF, image, or OCR processing.
- A local OCR adapter with replaceable implementation.
- Embedded Unicode font families with explicit licensing.

Deployment:

- Docker Compose for PostgreSQL/pgvector and Redis.
- Backend and built frontend run locally as one application.
- No hosted infrastructure is required.

### 14.2 Modular monolith packages

```text
workspace/
project/
document/
segment/
terminology/
translationmemory/
translation/
linguisticqa/
pdf/
ocr/
layout/
visualqa/
revision/
workflow/
infrastructure/
```

Modules communicate through application services and explicit domain types, not by directly querying one another's tables.

## 15. Core data model

### Project data

```text
workspace
project
project_rule_set
project_rule
term_entry
term_translation
translation_memory_entry
```

### Document data

```text
document
document_page
layout_block
text_line
text_span
segment
document_brief
document_instruction
```

### Revision and review data

```text
document_revision
revision_instruction
review
review_finding
layout_finding
visual_review
```

### Workflow and AI data

```text
workflow_job
job_checkpoint
ai_invocation
idempotency_record
```

All stored file paths are relative to the configured application data directory. Checksums detect accidental file changes.

## 16. State machines

### 16.1 Workflow job

```text
QUEUED
ANALYZING
EXTRACTING
BUILDING_DOCUMENT_BRIEF
TRANSLATING
LINGUISTIC_QA
COMPOSING
LAYOUT_QA
VISUAL_QA
AWAITING_APPROVAL
COMPLETED
FLAGGED
FAILED
CANCELLED
STALE
```

### 16.2 Segment

```text
DRAFT
NEEDS_REVIEW
QA_PASSED
QA_FLAGGED
STALE
```

### 16.3 Document revision

```text
GENERATING
READY
FLAGGED
APPROVED
SUPERSEDED
```

Infrastructure failures remain separate from linguistic and layout outcomes.

## 17. Concurrency, idempotency, and retries

- Claim PostgreSQL jobs with `FOR UPDATE SKIP LOCKED`.
- Require idempotency keys for document translation and regeneration commands.
- Store segment, document revision, and project rule-set versions on jobs.
- Update a result only if the expected version remains current.
- Mark an outdated result `STALE`.
- Retry transient Codex, database, rendering, and OCR failures with bounded exponential backoff.
- Do not automatically retry deterministic validation failures.
- Keep retry count and last failure structured and visible.

## 18. API surface

Initial REST endpoints:

```http
POST   /api/projects
GET    /api/projects
GET    /api/projects/{projectId}

GET    /api/projects/{projectId}/rules
PUT    /api/projects/{projectId}/rules

POST   /api/projects/{projectId}/terms
PUT    /api/projects/{projectId}/terms/{termId}
DELETE /api/projects/{projectId}/terms/{termId}

POST   /api/projects/{projectId}/translation-memory
GET    /api/projects/{projectId}/translation-memory

POST   /api/projects/{projectId}/documents
GET    /api/projects/{projectId}/documents
GET    /api/projects/{projectId}/documents/{documentId}

POST   /api/projects/{projectId}/documents/{documentId}/translate
POST   /api/projects/{projectId}/documents/{documentId}/cancel
GET    /api/projects/{projectId}/documents/{documentId}/events

GET    /api/projects/{projectId}/documents/{documentId}/revisions
GET    /api/projects/{projectId}/documents/{documentId}/revisions/{revisionId}
POST   /api/projects/{projectId}/documents/{documentId}/revisions
POST   /api/projects/{projectId}/documents/{documentId}/revisions/{revisionId}/approve

GET    /api/projects/{projectId}/documents/{documentId}/findings
GET    /api/projects/{projectId}/documents/{documentId}/usage
GET    /api/projects/{projectId}/documents/{documentId}/export
```

PDF upload uses multipart form data. Translation and revision requests require `Idempotency-Key`.

## 19. Frontend plan

### 19.1 Projects screen

- Project cards.
- Default locales.
- Recent documents.
- Active and flagged jobs.

### 19.2 Project configuration

- Terminology table.
- Translation usage and never-translate controls.
- Style and tone controls.
- Free-form guidance.
- Translation-memory browser.
- Font and minimum-scale policy.
- Rule-set history.
- Effective-instructions preview.

### 19.3 New document flow

- PDF upload.
- Preflight summary.
- Digital, scanned, and mixed page counts.
- Source-language detection and confidence.
- Source-language override.
- Target-language selector.
- Unsupported-capability warnings.
- Start translation.

### 19.4 Document workspace

- Source and translated PDFs side by side.
- Synchronized page navigation and zoom.
- Toggleable layout-block overlays.
- Findings panel that navigates to a page and block.
- Stage progress and approximate remaining time.
- Token usage by stage.
- Revision history and comparison.
- Approve and export actions.

### 19.5 Document chat

- Defaults to document scope.
- Optional selected-block or selected-page scope.
- Shows whether a request affects translation, layout, or both.
- Shows affected segments and pages before regeneration.
- Requires confirmation before promoting an instruction to project scope.
- Supports regenerate, compare, revert, and approve.

The frontend is intentionally not a free-form PDF editor.

## 20. Progress and token reporting

Progress is expressed through real counters:

```text
Analyzing pages              100 / 100
Extracting text              100 / 100
Translating segments         438 / 612
Linguistic QA                390 / 612
Composing pages               61 / 100
Visual QA                     54 / 100
```

An estimated completion time appears only after enough samples exist for a rolling estimate. Before that, the UI displays `Estimating...`.

Token usage includes:

- Input tokens.
- Cached input tokens.
- Output tokens.
- Reasoning output tokens.
- Totals by translation, linguistic review, visual review, and user revisions.

Verbatim reports usage but does not implement a hard per-document token budget. Provider rate limits or usage exhaustion pause jobs safely and expose a retryable state.

## 21. Privacy and security

- All files and metadata are local by default.
- `.verbatim-data/`, temporary renders, credentials, and generated documents are Git-ignored.
- Verbatim never reads or stores Codex credential files.
- Prompts use only the document content and project data required for the current batch.
- Logs do not include full source or translated text by default.
- Uploaded filenames are sanitized.
- PDF size, page count, decompression, and parsing limits protect local resources.
- External links or active content inside PDFs are never executed.
- Generated PDFs do not preserve executable PDF JavaScript.
- Exported artifacts record the source checksum and revision identifier in metadata or a sidecar report.

## 22. Testing and evaluation

### 22.1 Unit tests

- Placeholder multiset comparison.
- HTML tag comparison.
- Terminology matching.
- Rule versioning.
- Text fitting.
- Locale and script capability checks.
- Revision scope.
- Job state transitions.
- Usage aggregation.

### 22.2 Integration tests

Use Testcontainers for:

- PostgreSQL with pgvector.
- Redis.
- Flyway migrations.
- jOOQ queries.
- `FOR UPDATE SKIP LOCKED` concurrency.
- Idempotency.
- Stale-result handling.
- Versioned cache behavior.
- Project and language-pair isolation.

Codex and embeddings use deterministic fakes in most automated tests.

### 22.3 Golden PDF tests

For every fixture:

- Render source and output pages.
- Verify page dimensions and count.
- Compare immutable regions.
- Check text blocks, overflow, collision, and glyphs.
- Store approved golden measurements and selected page renders.
- Produce visual diffs on failure.

### 22.4 Synthetic corpus

Generate digital PDFs containing:

- Multiple fonts and sizes.
- Long headings.
- Paragraphs and lists.
- Columns.
- Tables.
- Charts and images.
- Headers, footers, and page numbers.
- Placeholders and terminology cases.
- Deliberate overflow and collision cases.

Create scanned variants with controlled:

- Blur.
- Skew.
- Noise.
- Contrast.
- Compression.
- Resolution.

Synthetic fixtures retain ground-truth text, blocks, and coordinates.

### 22.5 Public real-world corpus

- DocLayNet for diverse digital layouts, annotations, page PDFs, and text coordinates.
- FUNSD for noisy annotated scanned forms.
- Library of Congress Selected Digitized Books for long, public-domain real-world scans.

Dataset download scripts and pinned manifests are stored in the repository. Large datasets and documents are not committed.

### 22.6 Performance tests

- 1-page smoke document.
- 10-page mixed-layout document.
- 100-page digital document.
- 100-page scanned document.
- Restart and resume during translation.
- Restart and resume during composition.
- Bounded parallel batch processing.
- Translation-memory search with a large generated dataset.
- Inspected `EXPLAIN ANALYZE` plan for pgvector retrieval.

### 22.7 Frontend end-to-end tests

- Project creation and rule editing.
- Upload and preflight.
- Progress updates.
- Findings navigation.
- Document chat scoping.
- Regeneration and revision comparison.
- Approval and export.

## 23. Implementation roadmap

Every phase must end with working tests and documentation. Later phases build on completed behavior rather than replacing prototypes.

### Phase 0 - Repository foundation

Deliver:

- Spring Boot backend skeleton.
- React, TypeScript, Tailwind, and Vite frontend skeleton.
- Docker Compose for PostgreSQL/pgvector and Redis.
- Flyway and jOOQ generation.
- OpenAPI, metrics, formatting, linting, and test conventions.
- Application data-directory abstraction.
- Root `README.md`.
- Repository `AGENTS.md`.
- Verbatim PDF skill skeleton.

Exit criteria:

- One command starts infrastructure.
- Backend and frontend run locally.
- CI runs backend and frontend tests.

### Phase 1 - Project and workflow core

Deliver:

- Workspace and project schema.
- Project locale configuration.
- Versioned project rules.
- Segment and document revision versioning.
- PostgreSQL job queue.
- Idempotency records.
- Stale-result handling.
- Project configuration REST API.

Exit criteria:

- Duplicate commands do not create duplicate jobs.
- Two workers safely claim different jobs.
- Old results cannot overwrite new versions.

### Phase 2 - Small digital PDF ingestion

Deliver:

- PDF upload and local artifact storage.
- PDF preflight and page classification.
- Digital text and geometry extraction.
- Normalized document-layout model.
- Initial segment creation.
- Source-language selection and detection adapter.
- Synthetic 1- to 5-page fixtures.

Exit criteria:

- Extracted segments map back to page blocks.
- Page count, dimensions, reading order, and basic typography are recorded.

### Phase 3 - Project translation controls

Deliver:

- Terminology storage and matching.
- Never-translate rules.
- Style and free-form project guidance.
- Document instructions.
- Rule-set version history.
- Redis versioned cache.
- Project configuration frontend.
- Effective-instructions preview.

Exit criteria:

- A document revision records and uses one immutable rule-set version.
- Updating rules changes the cache key and affects only future revisions.

### Phase 4 - Codex translation and usage

Deliver:

- `TranslationClient`.
- Local Codex CLI adapter.
- Authentication status diagnostics.
- JSON Schema translation results.
- Document brief generation.
- Bounded segment batching.
- Invocation audit and token aggregation.
- Translation progress events.

Exit criteria:

- A small digital PDF is translated through Codex.
- Every call has structured output, status, duration, and token usage.
- No model thread is the sole source of translation instructions.

### Phase 5 - Linguistic QA and translation memory

Deliver:

- Placeholder and HTML checks.
- Terminology findings.
- Consistency checks.
- Translation-memory storage.
- Embedding interface and deterministic fake.
- pgvector similarity query.
- Review persistence and segment state transitions.

Exit criteria:

- Original QA definition-of-done cases pass.
- Results never leak across projects or language pairs.
- Only explicitly approved translations enter memory.

### Phase 6 - Digital PDF composition and visual QA

Deliver:

- Deterministic text-fitting engine.
- Digital-page composition.
- Font coverage and fallback checks.
- Render-to-PNG scripts.
- Region-aware deterministic comparison.
- `VisualReviewClient` and Codex image review.
- Bounded layout-adjustment loop.
- Side-by-side PDF viewer and findings navigation.

Exit criteria:

- Page count and dimensions remain unchanged.
- No passing output contains clipping, collisions, or unsupported glyphs.
- Unresolved layout problems are flagged.

### Phase 7 - Revision chat

Deliver:

- Document-scoped chat.
- Selected block and page scopes.
- Translation/layout/both classification.
- Affected-content preview.
- Partial regeneration.
- Immutable document revisions.
- Compare, revert, approve, and project-rule promotion.

Exit criteria:

- Chat defaults to document scope.
- Layout-only changes do not rerun translation.
- Project rules change only after explicit confirmation.

### Phase 8 - Latin, Cyrillic, and Greek hardening

Deliver:

- Locale capability registry.
- OCR-pack and font capability checks.
- Representative language-pair fixtures for all three scripts.
- Font fallback bundles and licensing documentation.
- Script-specific line-breaking tests.

Exit criteria:

- Supported locale pairs are accurately reported.
- Representative documents for all three script families pass linguistic and layout validation.

### Phase 9 - Scanned PDF support

Deliver:

- Printed-text OCR adapter.
- Word boxes and OCR confidence.
- Block grouping and reading order.
- Deskew and orientation handling.
- Normal-background text removal.
- Background reconstruction.
- Font-style estimation.
- Selectable translated-text overlay.
- Unsupported image-region detection and findings.
- Scanned synthetic and real-world fixtures.

Exit criteria:

- High-quality printed scans on normal backgrounds can be translated and exported.
- Handwriting and text over unsupported backgrounds are flagged.
- Low-confidence OCR is never silently accepted.

### Phase 10 - Large-document resilience

Deliver:

- Page and segment checkpoints.
- Resume after process restart.
- Bounded parallelism.
- Cancellation.
- Rolling ETA.
- Batch retry controls.
- 100-page performance fixtures.
- Database and pgvector query-plan inspection.

Exit criteria:

- 100-page digital and scanned jobs complete or resume without restarting successful work.
- Progress and token usage remain accurate.
- Memory consumption stays bounded by batches rather than total document size.

### Phase 11 - Evaluation and public release

Deliver:

- Pinned synthetic and public dataset manifests.
- Reproducible evaluation runner.
- Layout, OCR, linguistic, and performance reports.
- Installation and Codex-authentication guide.
- Supported-language and PDF capability matrix.
- Privacy and limitation documentation.
- Example project and sample PDFs.
- Public-repository cleanup and license review.

Exit criteria:

- A new user can clone, configure, run, translate, revise, approve, and export a supported PDF by following the README.
- All release acceptance criteria pass.

## 24. Definition of done

Verbatim's first complete release is done when:

- A user can create multiple projects with independent rules and translation memory.
- Source language can be detected, selected, and overridden.
- Target language can be selected from accurately reported capabilities.
- Representative Latin, Cyrillic, and Greek documents work.
- Digital, scanned, and mixed PDFs are classified and processed.
- Handwriting and unsupported image text are flagged rather than guessed.
- Page count and dimensions always remain unchanged.
- A configured minimum font scale is respected.
- Missing placeholders produce `QA_FLAGGED`.
- Admitted terms pass and obsolete terms are flagged.
- Never-translate terms remain unchanged.
- Low-confidence OCR produces structured findings.
- Passing PDFs contain no clipped text, collisions, or unreadable glyphs.
- Terminology and translation-memory results never leak across projects or language pairs.
- Repeating an idempotent request does not create another job.
- Stale reviews cannot update newer document versions.
- Updating project rules produces and uses a new cached version.
- Chat defaults to document scope.
- Promoting a chat instruction to a project rule requires confirmation.
- Regeneration creates a revision and preserves earlier revisions.
- Layout-only requests do not unnecessarily rerun translation.
- Token usage is visible by stage and revision.
- A 100-page job can resume from durable checkpoints.
- Human approval is required before translation-memory insertion.
- The approved translated PDF and QA report can be exported.
- Integration tests run against real PostgreSQL/pgvector and Redis containers.
- The translation-memory query has an inspected `EXPLAIN ANALYZE` plan.
- The README explains local Codex authentication, asynchronous processing, deterministic-first QA, privacy, supported inputs, and known limitations.

## 25. Principal risks and mitigations

### OCR mistakes become translation mistakes

Mitigation:

- Store word-level confidence.
- Surface uncertainty.
- Block approval on severe OCR findings.
- Allow targeted source-text correction before regeneration.

### Translated text expansion breaks layout

Mitigation:

- Deterministic fitting order.
- Minimum font scale.
- Partial regeneration.
- Structured overflow findings.
- Hard page-count invariant.

### Scanned-background cleanup damages content

Mitigation:

- Limit the first release to normal document backgrounds.
- Preserve detected lines and borders.
- Compare immutable regions.
- Flag uncertain or textured regions.

### Broad language claims exceed actual rendering support

Mitigation:

- Capability registry.
- Script-family acceptance suites.
- Frontend shows only verified support.
- Add RTL, CJK, and other scripts incrementally.

### Long documents become inconsistent

Mitigation:

- Versioned document brief.
- Project terminology.
- Relevant translation memory.
- Neighboring batch context.
- Document-wide consistency QA.

### Model context becomes polluted

Mitigation:

- Fresh bounded invocations.
- Canonical context assembled from database state.
- Prompt and context hashes.
- Immutable revisions.

### Visual review is nondeterministic

Mitigation:

- Deterministic layout checks remain authoritative.
- Codex findings are structured.
- Adjustments are validated by application code.
- Retry loops are bounded.

### Subscription or provider limits interrupt work

Mitigation:

- Durable checkpoints.
- Retryable paused state.
- Accurate token reporting.
- No loss of completed batches.

## 26. Architectural decisions to record during implementation

Create short Architecture Decision Records when implementation begins for:

- Local Codex CLI integration versus another provider adapter.
- Digital PDF object preservation and raster fallback policy.
- OCR engine selection.
- Background reconstruction implementation.
- Text shaping and font fallback strategy.
- Document batching and parallelism defaults.
- Raw prompt retention policy.
- Dataset licensing and fixture retention.

The decisions may refine implementation details but must preserve the product invariants and release scope defined in this plan.
