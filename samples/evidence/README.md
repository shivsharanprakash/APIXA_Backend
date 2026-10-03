# samples/evidence — Step 12 evidence fixture

Deterministic input for `POST http://localhost:8086/api/evidence/analyze` (Body → raw → JSON), plus
`verify-evidence.py`, which queries the SQLite file directly to prove persistence and linkage.

```
POST http://localhost:8086/api/evidence/analyze      # create / refresh the chain
GET  http://localhost:8086/api/evidence/set/STEP12-ADMIN
GET  http://localhost:8086/api/evidence/EV-STEP12-ADMIN-CONFORMANCE-1-GET-ADMIN-USERS
GET  http://localhost:8086/api/evidence/run/1
python samples/evidence/verify-evidence.py apixa-evidence-service/data/evidence.db
```

## Chains in the fixture

1. `GET /admin/users` — the primary research case: contract expects ADMIN, mapping MATCHED,
   `/admin/** → USER` in `SecurityConfig.java:44`, conformance **MISMATCH**.
   Produces 5 linked items: CONTRACT → MAPPING → SOURCE → SOURCE_SECURITY → CONFORMANCE.
2. `GET /api/users` — MULTIPLE_CANDIDATES → **UNVERIFIED**. Both candidates are preserved in the
   MAPPING content; none is selected, and the CONFORMANCE item links to CONTRACT + MAPPING only.

Evidence records facts and references only. The MATCH/MISMATCH/PARTIAL/UNVERIFIED verdict and its
reason come from Step 11 and are stored verbatim — never recomputed here.

Re-posting the same file updates the same 8 rows (deterministic `EV-<set>-<TYPE>-<METHOD>-<PATH>-<n>`
codes), so there is no duplicate growth.