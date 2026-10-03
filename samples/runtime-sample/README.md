# samples/runtime-sample — Step 13 runtime verification target

A deterministic local Spring Boot API that the APIXA Runtime Service (port 8087) probes. It is a
**sample target**, not part of the APIXA backend. It lives outside the Maven reactor on purpose, so it
can never be started by an APIXA build.

```
cd samples/runtime-sample
mvn -B spring-boot:run          # http://localhost:9090
```

## Authentication model

HTTP Basic, two **dummy in-memory** users (nothing on disk, nothing logged):

| user | password | roles |
|------|----------|-------|
| `admin` | `admin123` | ADMIN |
| `user` | `user123` | USER |

Basic values for Postman: `admin` → `Basic YWRtaW46YWRtaW4xMjM=`, `user` → `Basic dXNlcjp1c2VyMTIz`.

## Observed behaviour (verified against the running sample)

| request | observed status |
|---------|-----------------|
| `GET /public/health` anonymous | 200 |
| `GET /admin/users` anonymous | 401 |
| `GET /admin/users` as `user` | 403 |
| `GET /admin/users` as `admin` | 200 |
| `GET /user/profile` as `user` | 200 |
| `GET /does-not-exist` | 404 |

Rule order in `SecurityConfig`: `/public/**` permitAll → `/admin/**` hasRole ADMIN → `/user/**`
authenticated → `anyRequest` permitAll (so unmapped paths reach Spring's 404 handler instead of being
turned into a 401 by the filter chain).

These are **observed HTTP statuses only**. The Runtime Service never converts them into a conformance
verdict, and no role is inferred from a status code — 401 means "not authenticated", 403 means
"reached the application, access forbidden".