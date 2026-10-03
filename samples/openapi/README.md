# OpenAPI import test inputs (Step 6)

Real files for manual Postman verification of `POST http://localhost:8082/api/contracts/import`
(`multipart/form-data`, field name `file`).

| File | Purpose | Expected |
|------|---------|----------|
| `valid-openapi.yaml` | valid OpenAPI 3.0.3 YAML | `201` + import metadata |
| `valid-openapi.json` | valid OpenAPI 3.0.3 JSON | `201` + import metadata |
| `invalid-openapi.yaml` | malformed YAML (unterminated string/flow sequence) | `400` `Failed to parse OpenAPI specification: ...` |
| `not-openapi.json` | valid JSON, not OpenAPI (no `openapi`, no `info`) | `400` `Not a valid OpenAPI document: ...` |
| `not-openapi.yaml` | valid YAML, not OpenAPI | `400` `Not a valid OpenAPI document: ...` |
| `empty.yaml` | 0 bytes | `400` `Uploaded file 'empty.yaml' is empty` |
| `spec.txt` | valid OpenAPI content, unsupported extension | `400` `Unsupported OpenAPI file type 'spec.txt' ...` |

`valid-openapi.yaml` / `valid-openapi.json` describe `GET /admin/users` (test input only — the
import step does not extract endpoints or security requirements).

## Step 7 contract extraction input

`step7-contract.yaml` is the deterministic input for
`GET http://localhost:8082/api/contracts/{id}/extract`. It is valid OpenAPI 3.0.3 and declares
6 paths / 6 operations:

| Element | Declares | Extraction expectation |
|---------|----------|------------------------|
| root `security: [bearerAuth: []]` | global requirement | `globalSecurity.source=GLOBAL`, `requiresSecurity=true` |
| `GET /health` | `security: []` | `declared=true`, `explicitlyPublic=true`, `requiresSecurity=false` |
| `GET /admin/users` | `security: [bearerAuth: []]` | `source=OPERATION`, `schemes[0].name=bearerAuth`, `type=HTTP`, `httpScheme=bearer` |
| `POST /admin/users` | bearerAuth + required body | `requestBody.required=true`, `content[].mediaType=application/json`, `schemaRef=#/components/schemas/User` |
| `GET /admin/reports` | `security: [oauth2: [admin]]` | `schemes[0].requiredScopes=[admin]`, `type=OAUTH2` |
| `GET /users/{id}` | no operation security | `source=GLOBAL` (inherited), path stays `/users/{id}`, path parameter `id` + query parameter `verbose` (enum) |
| `GET /public/status` | `security: [{apiKeyAuth: []}, {}]` | 2 alternatives, `anonymousAccessAllowed=true`, `requiresSecurity=false` |
| responses | `200`, `201`, `400`, `401`, `403` | listed with `statusCode` + `description`, numeric ascending |
