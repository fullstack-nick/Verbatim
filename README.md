# Verbatim

Verbatim is a local-first, project-aware PDF translation workstation. It calls
the authenticated Codex CLI to translate printed documents, keeps every page
the same size and count, checks the result against visible terminology and
project rules, then lets a human review, revise, approve, and export the PDF.

The product is a modular monolith: a Java 25/Spring Boot 4 backend, a
React/TypeScript frontend, PostgreSQL with pgvector, Redis, and local file
storage.

## What works

- Digital, scanned, and mixed PDF preflight.
- Printed Latin, Cyrillic, and Greek text with bundled Noto Sans fonts.
- User-selected source and target locales.
- Project-scoped rules, minimum font scale, terminology, and translation memory.
- `PREFERRED`, `ADMITTED`, `NOT_RECOMMENDED`, `OBSOLETE`, and
  `NEVER_TRANSLATE` terminology behavior.
- Placeholder and simple HTML parity checks.
- Fresh, bounded Codex CLI invocations with JSON Schema output.
- Codex vision OCR for high-quality scanned document pages.
- Structured Codex source/target page comparison after PDF composition.
- Page-preserving PDF composition with structured overflow findings.
- PostgreSQL job queue using `FOR UPDATE SKIP LOCKED`.
- Idempotent start commands, bounded retries, stale-result protection, and
  versioned rule snapshots.
- Redis terminology cache keyed by project, locale, and rule-set version.
- pgvector translation-memory search scoped by project and language pair.
- Side-by-side review, findings, revision chat, token usage, approval, and PDF
  export in the frontend.
- Swagger UI, Actuator health/metrics, Flyway, and real pgvector/Redis
  Testcontainers.

The detailed product direction and implementation history are in
[PLAN.md](PLAN.md). It is a living plan, not a contract that overrides what the
working application teaches us.

## Prerequisites

- Docker Desktop
- Node.js 22+
- Java 25
- An installed Codex CLI authenticated with a Codex-capable ChatGPT account or
  API key

Install and authenticate Codex if needed:

```powershell
npm install -g @openai/codex
codex login
codex login status
```

Verbatim invokes that local CLI. It does not read, copy, display, or persist the
Codex credential file.

## Run locally

Start PostgreSQL/pgvector and Redis:

```powershell
docker compose -f backend/compose.yaml up -d
```

Start the backend from its directory so the default schema and storage paths
resolve correctly:

```powershell
Set-Location backend
$env:JAVA_HOME = "C:\path\to\jdk-25"
$env:VERBATIM_CODEX_EXECUTABLE = "codex"
.\mvnw.cmd spring-boot:run
```

On Windows, if `codex` is not resolved from a child process, set
`VERBATIM_CODEX_EXECUTABLE` to the absolute `codex.cmd` path. The backend runs
on `http://localhost:8081`; Swagger UI is at
`http://localhost:8081/swagger-ui.html`.

In another terminal:

```powershell
Set-Location frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## First translation

1. Create a project and choose its default locales.
2. Open project controls and add guidance or authoritative terms.
3. Upload a printed-text PDF.
4. Add an optional document-only instruction and choose **Translate**.
5. Watch the real workflow stages and token totals.
6. Review the two PDFs and structured findings.
7. Ask for a change to create another revision. The instruction stays within
   this document unless **Save as a project rule** is explicitly selected.
8. Approve the chosen revision. Only approval adds its pairs to translation
   memory.
9. Export the translated PDF.

To prove the pipeline without spending Codex usage, set
`VERBATIM_CODEX_ENABLED=false`. The clearly labeled demo fallback handles only
the bundled smoke fixture and is not a general translator. Set
`VERBATIM_CODEX_ALLOW_FALLBACK=false` when failure must remain failure.

## Why asynchronous?

OCR, translation, review, and PDF composition can take minutes on real
documents. A synchronous HTTP request would time out and make retries unsafe.
Verbatim records a durable PostgreSQL job and returns immediately; a worker
claims it transactionally, reports actual stages, and only publishes a result
if the document version is still current.

## Why deterministic checks run first

Codex is excellent at language and visual interpretation, but placeholders,
required terminology, page counts, and minimum font scale are invariants.
Those checks must be reproducible and machine-readable. Model context enriches
the translation; deterministic findings remain authoritative and never become
nondeterministic tests.

## Tests and builds

The intentionally small backend suite uses real pgvector PostgreSQL and Redis
containers:

```powershell
Set-Location backend
$env:JAVA_HOME = "C:\path\to\jdk-25"
.\mvnw.cmd test
```

Frontend type-check and production build:

```powershell
Set-Location frontend
npm run build
```

Generate the smoke PDFs:

```powershell
python scripts/generate_sample_pdfs.py
```

The repository PDF skill at
`.agents/skills/verbatim-pdf-layout/SKILL.md` defines the render-and-inspect
workflow used during development.

## Validation data and performance

`python scripts/download_validation_dataset.py` downloads a focused corpus of
actual text documents: digital papers/reports/forms and public-domain scanned
periodicals/manuals. Exact files, sources, sizes, and the single directory to
delete are documented in [docs/DATASETS.md](docs/DATASETS.md).

The generated 5,000-entry translation-memory benchmark and inspected HNSW plan
are documented in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Local data and privacy

- `.verbatim-data/` contains uploads, rendered pages, and generated revisions.
- `datasets/` contains optional downloaded or generated evaluation data.
- PostgreSQL and Redis use named Docker volumes.
- None of these paths are committed.

Document text and page images are sent only through the locally authenticated
Codex provider when a workflow needs them. Logs avoid full document content.
Uploaded filenames and storage paths are constrained, and embedded PDF active
content is never executed by Verbatim.

## Current boundaries

- Scans must be high quality, printed, and on ordinary paper/form/table
  backgrounds.
- Handwriting and text over photographs, diagrams, or heavily textured
  backgrounds are outside version-one support.
- Right-to-left, CJK, Indic shaping, and vertical writing need dedicated
  shaping and acceptance suites; the locale and provider boundaries are ready
  for those additions.
- The compositor preserves page geometry and non-text content, but unusual
  content streams and imprecise vision boxes can still produce layout
  findings. A flagged PDF is reviewable output, not a claim of pixel identity.
- Large documents are processed by a durable job, but the current release does
  not yet checkpoint individual Codex batches across a process restart.

## Repository map

```text
backend/       Spring Boot application, migrations, PDF and workflow code
frontend/      React/TypeScript/Tailwind interface
schemas/       Codex structured-output JSON Schemas
scripts/       Fixtures, corpus downloader, and TM benchmark
docs/          Dataset and performance notes
.agents/       Repository-local PDF render/verification skill
PLAN.md        Living product and implementation plan
```

License: [MIT](LICENSE).
