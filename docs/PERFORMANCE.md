# Translation-memory query plan

The benchmark generator loaded 5,000 deterministic source/target pairs into
PostgreSQL 17 with pgvector, ran `ANALYZE`, and inspected the same
project-and-language-scoped cosine query used by the application.

Command:

```powershell
python scripts/generate_tm_dataset.py --count 5000 --load
```

Representative local result:

```text
Limit (actual time=0.742..0.748 rows=3 loops=1)
  Buffers: shared hit=468
  -> Index Scan using translation_memory_embedding_idx
       on translation_memory_entry
       (actual time=0.724..0.729 rows=3 loops=1)
       Order By: (source_embedding <=> $QUERY_VECTOR)
       Filter:
         source_embedding IS NOT NULL
         AND project_id = $PROJECT_ID
         AND source_locale = 'en-US'
         AND target_locale = 'de-DE'
Planning Time: 0.581 ms
Execution Time: 0.848 ms
```

The important property is the HNSW `Index Scan`; a sequential scan here would
signal either missing statistics, a missing vector, or a query shape that no
longer matches the index. Exact timings depend on hardware and cache state.

The built-in `HashingEmbeddingClient` is a deterministic, offline baseline. It
is useful for repeatable development and near-duplicate retrieval, but its
semantic quality is intentionally lower than a model embedding provider. The
domain boundary is the `EmbeddingClient` interface, so a production-quality
provider can replace it without changing PostgreSQL queries or review logic.
