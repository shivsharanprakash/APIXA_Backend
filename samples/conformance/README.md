# samples/conformance — Step 11 conformance fixture

Deterministic input for `POST http://localhost:8085/api/conformance/analyze` (Body → raw → JSON).
The conformance service discovers nothing: it consumes the structured output of Step 7
(`contractEndpoints`), Step 10 (`mappings`) and Step 9 (`securityRules`).

> `SecurityPolicy` uses primitive booleans, so `permitAll` **and** `unknown` must be present on every
> policy object, otherwise the body is rejected with 400.

## Cases

| # | contract endpoint | expected | source rule selected | mapping | conformance |
|---|-------------------|----------|----------------------|---------|-------------|
| 1 | `GET /health` | PUBLIC | `PERMIT_ALL /health` (3001) | MATCHED | **MATCH** |
| 2 | `GET /admin/users` | roles ADMIN | `HAS_ROLE /admin/**` (2001) | MATCHED | **MISMATCH** (implements USER) |
| 3 | `GET /admin/reports` | roles ADMIN | `HAS_ROLE /admin/reports` (3002) | MATCHED | **MATCH** |
| 4 | `GET /api/users` | roles USER | — | MULTIPLE_CANDIDATES | **UNVERIFIED** |
| 5 | `GET /missing` | roles USER | — | UNMATCHED | **UNVERIFIED** |
| 7 | `GET /admin/secret` | roles ADMIN | `HAS_ROLE /admin/**` (2001) | MATCHED | **MISMATCH** (catch-all `anyRequest()` → AUTHENTICATED did **not** compete) |
| 8 | `GET /dynamic` | roles ADMIN | `HAS_ROLE` METHOD rule, `unresolved` (5000) | MATCHED | **UNVERIFIED** (dynamic role) |
| 9 | `GET /private/docs` | oauth2 scope `read` | `HAS_ANY_AUTHORITY /private/**` → `SCOPE_read` | MATCHED | **MISMATCH** (engine defines no scope↔authority equivalence) |

Case 2 is the central APIXA research flow: `mappingStatus=MATCHED`, `conformanceStatus=MISMATCH`,
reason *"Contract requires roles [ADMIN] authorization, while the implementation requires roles [USER]."*

Rule precedence: METHOD annotation 5000 > CLASS annotation 4000 > concrete path 3000+n >
wildcard path 2000+n > catch-all `anyRequest()` 1000. Only the best tier participates.