# Validation datasets

Verbatim keeps downloaded evaluation documents outside Git. They are test
inputs, not runtime dependencies or training data.

Run:

```powershell
python scripts/download_validation_dataset.py
```

The downloader creates the following exact tree:

```text
datasets/
└── validation/
    ├── manifest.json
    ├── digital/
    │   ├── doclaynet-paper.pdf
    │   ├── irs-publication-4604-en-es.pdf
    │   └── school-annual-report-2021.pdf
    └── scanned/
        ├── american-printer-manual-1885.pdf
        └── scientific-american-1896-05-16.pdf
```

These are all documents containing printed text—not photos of physical objects.
The digital set exercises scientific, government-brochure, annual-report, table,
and multi-column layouts. The scanned set exercises real historical page scans,
dense columns, illustrations, and print variation without handwriting.

The manifest records the source URL, purpose, byte size, SHA-256 hash, and
license note for every file.

## Removing the downloaded data

After evaluation, the entire corpus can be reclaimed by deleting this one
directory:

```text
<repository>\datasets\validation
```

On this development machine, the absolute location is:

```text
C:\D_DRIVE\Nikita\JS\Verbatim\datasets\validation
```

Do not delete `.verbatim-data` at the same time unless you also want to erase
uploaded documents and generated revisions from the local application.

For broad layout-analysis benchmarking, DocLayNet remains a useful optional
source, but its full PDF extras are several gigabytes. Verbatim deliberately
does not download that corpus automatically.

## Generated translation-memory benchmark

Run the following after Docker Compose is healthy:

```powershell
python scripts/generate_tm_dataset.py --count 5000 --load
```

This creates:

```text
C:\D_DRIVE\Nikita\JS\Verbatim\datasets\generated\translation-memory.jsonl
```

and loads those rows into the local PostgreSQL `translation_memory_entry`
table. Delete the JSONL directory to reclaim the file copy. To remove only the
generated database rows, recreate the local Docker volumes or delete the rows
whose source text ends in `Reference NNNN.` from the development database.
The command also runs and prints `EXPLAIN (ANALYZE, BUFFERS)` for a
project-and-language-scoped nearest-neighbor query.
