# samples/impact — Step 14 change impact fixture

Deterministic input for `POST http://localhost:8088/api/impact/analyze` (Body → raw → JSON). The service
compares supplied snapshots only — it never re-runs extraction, mapping, conformance or runtime probing.

```
POST http://localhost:8088/api/impact/analyze
{ ... contents of step14-impact-request.json ... }
```

## Cases and expected results (9 impact records)

| # | case | expected impact |
|---|------|-----------------|
| 1 | security implementation change | `GET /admin/users` SECURITY_CHANGED (implemented ADMIN → USER) |
| 2 | conformance change | `GET /admin/users` CONFORMANCE_CHANGED (MATCH → MISMATCH) |
| 3 | contract security change | `GET /reports` SECURITY_CHANGED (expected AUTHENTICATED → AUTHENTICATED roles [ADMIN]) |
| 4 | source security rule change | `HAS_ROLE /admin/**` SECURITY_CHANGED (ADMIN → USER) |
| 5 | runtime observation change | `GET /admin/users` RUNTIME_OBSERVATION_CHANGED (200 → 401, observation only) |
| 6 | removed endpoint | `GET /legacy` REMOVED_ENDPOINT |
| 7 | added endpoint | `POST /users` ADDED_ENDPOINT |
| 8 | HTTP method change | `GET /users` REMOVED_ENDPOINT + `POST /users` ADDED_ENDPOINT |
| 9 | path variable rename | `GET /users/{id}` → `GET /users/{userId}` — **no** impact record |
| 10 | unchanged endpoint | `GET /unchanged` — **no** impact record |

No severity, ranking or good/bad judgement is produced: every record states a factual difference only.
Evidence codes from both versions are preserved per record (`v1EvidenceCodes` / `v2EvidenceCodes`).