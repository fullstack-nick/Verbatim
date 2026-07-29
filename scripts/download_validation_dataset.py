"""Download a small, text-document-only validation corpus for Verbatim.

The corpus intentionally mixes native PDFs with high-quality scans. It is not
application data and can be deleted at any time.
"""

from __future__ import annotations

import hashlib
import json
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "datasets" / "validation"

DOCUMENTS = [
    {
        "kind": "digital",
        "filename": "doclaynet-paper.pdf",
        "url": "https://arxiv.org/pdf/2206.01062",
        "description": "Scientific paper with figures, tables, references, and two-column pages.",
        "license_note": "arXiv distribution; see the paper for its license.",
    },
    {
        "kind": "digital",
        "filename": "irs-publication-4604-en-es.pdf",
        "url": "https://www.irs.gov/pub/irs-pdf/p4604ens.pdf",
        "description": "Official bilingual government brochure with strong graphic layout.",
        "license_note": "United States federal government publication.",
    },
    {
        "kind": "digital",
        "filename": "school-annual-report-2021.pdf",
        "url": "https://ia600503.us.archive.org/12/items/annual-report-2021_20231027/annual-report-2021.pdf",
        "description": "Multi-page public annual report containing tables and section hierarchy.",
        "license_note": "Publicly distributed report; retain its embedded disclaimer.",
    },
    {
        "kind": "scanned",
        "filename": "scientific-american-1896-05-16.pdf",
        "url": "https://ia800600.us.archive.org/24/items/scientific-american-1896-05-16/scientific-american-v74-n20-1896-05-16.pdf",
        "description": "Historical scanned periodical with dense columns and illustrations.",
        "license_note": "Public-domain historical scan from Internet Archive.",
    },
    {
        "kind": "scanned",
        "filename": "american-printer-manual-1885.pdf",
        "url": "https://archive.org/download/americanprinterm1885mack/americanprinterm1885mack.pdf",
        "description": "Public-domain scanned typography manual with varied print layouts.",
        "license_note": "Public-domain historical scan from Internet Archive.",
    },
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def download(item: dict[str, str]) -> dict[str, object]:
    directory = ROOT / item["kind"]
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / item["filename"]
    if not target.exists():
        print(f"Downloading {item['filename']}...")
        request = urllib.request.Request(
            item["url"],
            headers={"User-Agent": "Verbatim validation dataset/1.0"},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            target.write_bytes(response.read())
    if target.read_bytes()[:4] != b"%PDF":
        raise RuntimeError(f"{target} is not a PDF")
    return {
        **item,
        "relativePath": target.relative_to(ROOT.parents[1]).as_posix(),
        "bytes": target.stat().st_size,
        "sha256": sha256(target),
    }


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    manifest = [download(item) for item in DOCUMENTS]
    manifest_path = ROOT / "manifest.json"
    manifest_path.write_text(
        json.dumps({"documents": manifest}, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"Validation corpus ready: {ROOT}")
    print(f"Manifest: {manifest_path}")


if __name__ == "__main__":
    main()
