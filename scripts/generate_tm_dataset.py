"""Generate and optionally load a repeatable translation-memory benchmark."""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import uuid
from datetime import datetime, timezone
from pathlib import Path

import psycopg


DIMENSIONS = 384
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "datasets" / "generated" / "translation-memory.jsonl"

SOURCES = [
    "Open Settings to configure your account.",
    "Review the document before approving this translation.",
    "The translated page keeps the original dimensions and layout.",
    "Use the project terminology in every product guide.",
    "The report contains tables, headings, figures, and footnotes.",
    "A background review checks placeholders and HTML tags.",
    "Upload a high-quality scanned PDF for visual text extraction.",
    "Translation memory contains only explicitly approved segments.",
    "The minimum font scale prevents unreadable output.",
    "Export the approved revision as a new PDF document.",
]

TARGETS = [
    "Öffne die Einstellungen, um dein Konto zu konfigurieren.",
    "Prüfe das Dokument, bevor du diese Übersetzung freigibst.",
    "Die übersetzte Seite behält die ursprünglichen Abmessungen und das Layout.",
    "Verwende die Projektterminologie in jedem Produktleitfaden.",
    "Der Bericht enthält Tabellen, Überschriften, Abbildungen und Fußnoten.",
    "Eine Hintergrundprüfung kontrolliert Platzhalter und HTML-Tags.",
    "Lade für die visuelle Texterkennung ein hochwertiges gescanntes PDF hoch.",
    "Der Übersetzungsspeicher enthält nur ausdrücklich freigegebene Segmente.",
    "Die minimale Schriftskalierung verhindert unlesbare Ausgaben.",
    "Exportiere die freigegebene Revision als neues PDF-Dokument.",
]


def java_hash(value: str) -> int:
    result = 0
    for character in value:
        result = (31 * result + ord(character)) & 0xFFFFFFFF
    return result - 0x100000000 if result & 0x80000000 else result


def embedding(text: str) -> list[float]:
    vector = [0.0] * DIMENSIONS
    for word in re.split(r"[^\w]+", text.casefold()):
        if not word:
            continue
        features = [(word, 1.5)]
        padded = f"^{word}$"
        features.extend((padded[index : index + 3], 0.35) for index in range(len(padded) - 2))
        for feature, weight in features:
            hashed = java_hash(feature)
            vector[hashed % DIMENSIONS] += weight if hashed & 1 == 0 else -weight
    norm = math.sqrt(sum(value * value for value in vector))
    return [value / norm for value in vector] if norm else vector


def vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(f"{value:.8f}" for value in vector) + "]"


def connection_string() -> str:
    return os.getenv(
        "VERBATIM_PG_DSN",
        "postgresql://verbatim:verbatim@localhost:5432/verbatim",
    )


def generate(count: int) -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    now = datetime.now(timezone.utc).isoformat()
    for index in range(count):
        template = index % len(SOURCES)
        variant = index // len(SOURCES)
        source = f"{SOURCES[template]} Reference {variant:04d}."
        target = f"{TARGETS[template]} Referenz {variant:04d}."
        entries.append(
            {
                "id": str(uuid.uuid4()),
                "sourceLocale": "en-US",
                "targetLocale": "de-DE",
                "sourceText": source,
                "targetText": target,
                "sourceEmbedding": vector_literal(embedding(source)),
                "approvedAt": now,
            }
        )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8") as target:
        for entry in entries:
            target.write(json.dumps(entry, ensure_ascii=False) + "\n")
    return entries


def load(entries: list[dict[str, str]], project_id: str | None) -> None:
    with psycopg.connect(connection_string()) as connection:
        if project_id is None:
            project_id = str(
                connection.execute(
                    "SELECT id FROM project ORDER BY created_at LIMIT 1"
                ).fetchone()[0]
            )
        with connection.cursor() as cursor:
            cursor.executemany(
                """
                INSERT INTO translation_memory_entry(
                    id, project_id, source_locale, target_locale, source_text,
                    target_text, source_embedding, approved_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s::vector, %s)
                """,
                [
                    (
                        item["id"],
                        project_id,
                        item["sourceLocale"],
                        item["targetLocale"],
                        item["sourceText"],
                        item["targetText"],
                        item["sourceEmbedding"],
                        item["approvedAt"],
                    )
                    for item in entries
                ],
            )
        connection.execute("ANALYZE translation_memory_entry")
        query_vector = vector_literal(embedding("Open Settings for the account."))
        plan = connection.execute(
            """
            EXPLAIN (ANALYZE, BUFFERS)
            SELECT id, source_text, 1 - (source_embedding <=> %s::vector) AS similarity
            FROM translation_memory_entry
            WHERE project_id = %s
              AND source_locale = 'en-US'
              AND target_locale = 'de-DE'
              AND source_embedding IS NOT NULL
            ORDER BY source_embedding <=> %s::vector
            LIMIT 3
            """,
            (query_vector, project_id, query_vector),
        ).fetchall()
        print("\n".join(row[0] for row in plan))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=5_000)
    parser.add_argument("--load", action="store_true")
    parser.add_argument("--project-id")
    arguments = parser.parse_args()
    entries = generate(arguments.count)
    print(f"Generated {len(entries)} entries at {OUTPUT}")
    if arguments.load:
        load(entries, arguments.project_id)


if __name__ == "__main__":
    main()
