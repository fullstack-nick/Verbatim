# Verbatim

Verbatim is a local-first PDF translation workstation. It keeps project terminology,
translation memory, and user instructions close to the document while preserving page
geometry and visual hierarchy.

The repository is under active development. The full product and implementation roadmap
is in [PLAN.md](PLAN.md).

## Current stack

- Java 25 and Spring Boot 4
- jOOQ, PostgreSQL, pgvector, Flyway, and Redis
- React, TypeScript, Tailwind CSS, and Vite
- Apache PDFBox and Poppler-compatible rendering
- Local Codex CLI integration

## Local prerequisites

- Docker Desktop
- Node.js 22 or newer
- JDK 25
- Codex CLI authenticated with `codex login`
- Tesseract with the desired OCR language packs for scanned documents

The development workspace may use a portable JDK under `.tooling/`; that directory is
ignored by Git.

## Start infrastructure

```powershell
docker compose -f backend/compose.yaml up -d
```

## Start the backend

```powershell
$env:JAVA_HOME = (Resolve-Path .tooling/jdk-25).Path
backend/mvnw.cmd -f backend/pom.xml spring-boot:run
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Start the frontend

```powershell
Set-Location frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## Local data

Application artifacts are written under `.verbatim-data/` by default. Downloaded evaluation
datasets are stored under `datasets/`. Both directories are ignored by Git and are safe to
delete when the application is stopped.

More detailed setup, supported PDF boundaries, dataset locations, and troubleshooting will
be added as the corresponding implementation slices land.
