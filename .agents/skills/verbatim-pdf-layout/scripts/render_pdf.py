from __future__ import annotations

import argparse
from pathlib import Path

import fitz


def render(
    pdf_path: Path,
    output_directory: Path,
    dpi: int,
    requested_pages: set[int] | None = None,
) -> list[Path]:
    output_directory.mkdir(parents=True, exist_ok=True)
    scale = dpi / 72
    rendered: list[Path] = []
    with fitz.open(pdf_path) as document:
        for index, page in enumerate(document):
            page_number = index + 1
            if requested_pages is not None and page_number not in requested_pages:
                continue
            target = output_directory / f"page-{page_number:04d}.png"
            pixmap = page.get_pixmap(matrix=fitz.Matrix(scale, scale), alpha=False)
            pixmap.save(target)
            rendered.append(target)
    return rendered


def main() -> None:
    parser = argparse.ArgumentParser(description="Render a PDF to stable page PNGs.")
    parser.add_argument("pdf", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--dpi", type=int, default=160)
    parser.add_argument(
        "--pages",
        help="Optional comma-separated one-based page numbers, for example 1,5,10.",
    )
    arguments = parser.parse_args()
    pages = (
        {int(value.strip()) for value in arguments.pages.split(",") if value.strip()}
        if arguments.pages
        else None
    )

    for path in render(arguments.pdf, arguments.output, arguments.dpi, pages):
        print(path.resolve())


if __name__ == "__main__":
    main()
