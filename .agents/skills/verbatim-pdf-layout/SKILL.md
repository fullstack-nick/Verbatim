---
name: verbatim-pdf-layout
description: Render, compare, and visually verify source and translated PDF revisions in Verbatim. Use when creating or changing PDF extraction, composition, OCR overlays, typography, page geometry, or layout QA, and whenever a translated PDF artifact must be accepted or flagged.
---

# Verbatim PDF Layout

Follow this workflow for every meaningful PDF change.

## Prepare

1. Keep source PDFs immutable.
2. Place intermediate renders under `tmp/pdfs/<document-id>/<revision-id>/`.
3. Place final verification artifacts under `output/pdf/<document-id>/<revision-id>/`.
4. Record the exact source and revision paths being compared.

## Render

1. Render source and target pages at the same DPI and color settings.
2. Render all changed pages plus the page before and after each changed page.
3. For a new pipeline or cross-document change, render the complete fixture.
4. Stop and report the missing dependency if no approved renderer is available.

## Inspect

Verify:

- Page count, dimensions, orientation, and page order.
- Headers, footers, page numbers, margins, and section transitions.
- Text alignment, line wrapping, hierarchy, weight, color, and spacing.
- Tables, borders, charts, images, captions, and list indentation.
- Missing, clipped, overlapping, corrupted, or unreadable text.
- Black boxes, replacement glyphs, and unsupported characters.
- Background damage around OCR-replaced text.

Compare non-text regions separately from translated text regions. Do not use a full-page
pixel difference as the only acceptance signal.

## Decide

- Accept only when the latest rendered revision has no visible defects and deterministic
  layout checks pass.
- Return structured findings with page, region, code, severity, and concise evidence.
- Flag an unresolved page after the configured attempt limit. Do not loop indefinitely.
- Never add pages or cross the configured minimum font scale to hide overflow.

## Report

State:

- Source and revision inspected.
- Pages rendered.
- Deterministic checks run.
- Visual findings.
- Final result: `READY` or `LAYOUT_FLAGGED`.
