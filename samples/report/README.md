# samples/report — Step 16 report fixture

`step16-report-request.json` is a controlled end-to-end snapshot handed to
`POST /api/reports/generate`. It contains results that earlier APIXA steps already produced; the
Report Service renders them and never re-runs any analysis.

## Contents

| Section | Value exercised |
|---------|-----------------|
| project / apiVersion / analysisRun | APIXA Demo, V2, run 42 |
| contract | `GET /admin/users`, expected security ADMIN |
| sourceAnalysis | `GET /admin/users` in `AdminController`, rule `HAS_ROLE /admin/**` with roles [USER] |
| mappings | MATCHED, HIGH confidence |
| conformance | **MISMATCH** - contract ADMIN vs implementation USER |
| evidence | 5 items linking CONTRACT -> MAPPING -> SOURCE -> SOURCE_SECURITY -> CONFORMANCE |
| runtime | `GET http://localhost:9090/admin/users` observed **401**, execution COMPLETED |
| impact | SECURITY_CHANGED (ADMIN -> USER) and CONFORMANCE_CHANGED (MATCH -> MISMATCH) |
| benchmark | small metric snapshot including the `CTRL-001` negative control, which stays FAILED |
| limitations | six supplied limitations |

## Sanitization check

The fixture deliberately contains a credential-bearing request header
(`Authorization: Basic ...`) and a `Set-Cookie` response header so that masking can be verified. The
generated JSON, HTML and PDF must contain `[REDACTED]` for the authorization value and no `Set-Cookie`
value at all.

## Determinism

Generating twice from this file yields the same `reportId` (a digest of the content) and byte-identical
JSON and HTML; only `generatedAt` differs between runs.