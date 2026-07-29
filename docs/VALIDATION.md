# Release validation

This file records the v0.1 acceptance pass. It is evidence for the repository,
not a claim that arbitrary PDFs can be reproduced pixel-for-pixel.

## Automated checks

- Java 25 backend build and the intentionally small test suite pass.
- The Spring context test starts real PostgreSQL/pgvector and Redis
  Testcontainers and applies Flyway.
- Placeholder, HTML, and terminology checks have focused unit coverage.
- The React/TypeScript production build passes.
- The 5,000-row translation-memory benchmark uses the pgvector HNSW index; see
  [PERFORMANCE.md](PERFORMANCE.md).

## End-to-end checks

Two fresh authenticated-Codex workflows were run through the public API:

1. A digitally generated one-page product guide was extracted, translated from
   English to German, checked, composed, rendered, visually reviewed, and
   exported.
2. A raster-only scan of the same document was classified as `SCANNED`, read by
   vision OCR, translated, composed over the scan, rendered, visually reviewed,
   and exported.

Both outputs kept the source page count and dimensions. The digital result
preserved the title hierarchy, colors, whitespace, table, and footer. The
initial scan result exposed an overlapping OCR-mask defect; the compositor was
changed to erase all source regions before drawing any translated text. A
second scan run removed source ghosts and clipping while preserving table
lines and page structure.

The final jobs remained `QA_FLAGGED` where German text required a font scale
below the configured minimum. That is the intended policy: Verbatim returns a
readable, reviewable PDF and a structured `TEXT_OVERFLOW` finding instead of
silently shrinking text.

## Visual inspection workflow

PDFs are rendered to page PNGs with:

```powershell
python .agents/skills/verbatim-pdf-layout/scripts/render_pdf.py `
  path/to/input.pdf output/pdf/check
```

During development the source/output pairs and representative real-world
dataset pages were inspected at 160 DPI. Generated renders live under
`output/pdf/` and are ignored by Git.

## Real-document corpus

The focused local corpus contains text documents rather than scans of physical
objects. It includes digital papers, a bilingual government publication, an
annual report with tables, and real scanned printed periodicals/manuals.
Sources, hashes, license notes, exact paths, and deletion instructions are in
[DATASETS.md](DATASETS.md).
