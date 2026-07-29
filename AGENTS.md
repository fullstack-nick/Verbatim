# Verbatim repository guidance

## Product priorities

- Keep page count and dimensions unchanged.
- Prefer visible structured findings over silent layout compromises.
- Keep project rules, document instructions, and model context versioned.
- Keep Codex behind provider-neutral interfaces.
- Keep PostgreSQL authoritative; Redis is only a cache.
- Prefer small, readable modules over framework-heavy abstractions.

## Development workflow

- Treat `PLAN.md` as a living plan. Update it when implementation evidence changes a decision.
- Use Java 25 for backend builds.
- Run frontend builds after meaningful UI changes.
- Render and visually inspect PDF output after meaningful PDF changes.
- Capture browser screenshots under `output/playwright/`.
- Keep PDF intermediates under `tmp/pdfs/` and final manual verification artifacts under
  `output/pdf/`.
- Do not commit `.verbatim-data/`, datasets, generated PDFs, credentials, or tool downloads.
- Commit coherent milestones with concise commit messages.

## Verification

- Backend: `backend/mvnw.cmd -f backend/pom.xml test`
- Frontend: `npm --prefix frontend run build`
- Infrastructure: `docker compose -f backend/compose.yaml config`

Testing is intentionally focused: exercise critical state, QA, and layout behavior without
building an oversized test suite.
