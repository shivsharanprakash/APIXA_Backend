# samples/mapping — Step 10 endpoint mapping fixture

Deterministic, minimal input for endpoint mapping (`POST /api/mappings/map`, port 8084). It is a
**request payload**, not a buildable project: the mapping service analyzes no source code, so no Java
file and no `pom.xml` is needed. Load `step10-mapping-request.json` in Postman (Body → raw → JSON),
or copy the JSON from that file.

```
POST http://localhost:8084/api/mappings/map
{ ... contents of step10-mapping-request.json ... }
```

## Cases covered

| # | case | contract endpoint | source endpoint(s) | expected status |
|---|------|-------------------|--------------------|-----------------|
| A | exact path | `GET /api/users` | `GET /api/users` in **two** controllers | MULTIPLE_CANDIDATES (both kept) |
| B | variable name differs | `GET /api/users/{id}` | `GET /api/users/{userId}` | MATCHED, MEDIUM |
| C | method difference | `GET /api/users/ping` | only `POST /api/users/ping` | UNMATCHED |
| D | static vs variable | `GET /api/users/all` | `GET /api/users/{userId}` | UNMATCHED |
| E | nested route | `GET /api/users/{id}/orders` | `GET /api/users/{userId}/orders` | MATCHED, MEDIUM |
| F | duplicate source | `GET /api/users` | `UserController` + `LegacyUserController` | MULTIPLE_CANDIDATES |
| G | missing source | `GET /missing` | — | UNMATCHED |
| H | exact, unique | `POST /api/users`, `GET /admin/users` | identical paths | MATCHED, HIGH |
| I | Spring regex variable | `GET /files/{name}` | `GET /files/{filename:.+}` | MATCHED, MEDIUM |
| J | trailing `**` wildcard | `GET /api/reports` | `GET /api/**` | UNCERTAIN, LOW |
| K | operationId irrelevant | `GET /api/users` (`operationId: fetchUser`) | Java method `listUsers` | matched on method+path only |

Sorting is deterministic: contract endpoints come back ordered by path then method, and candidates
ordered by path → method → file → line, so repeating the request returns a byte-identical response.

Both original paths are always reported unchanged (`method`/`path` = contract side,
`candidates[].path` = source side), and every candidate carries its Step 8 `file` + `lineStart`.

No security information is sent and none is compared — that is Step 11 (conformance).
