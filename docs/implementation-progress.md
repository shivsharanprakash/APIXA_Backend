# APIXA Implementation Progress

Protocol: one functionality at a time. The **Manual Postman** column is marked
`PASS` **only** when the human developer explicitly confirms the actual result.

Roadmap numbering follows the canonical roadmap (0–18).

| Step | Functionality | Build | Automated / smoke | Manual Postman | Human result |
|------|---------------|-------|-------------------|----------------|--------------|
| 0 | Existing setup / build verification | PASS (`validate/test/package`) | N/A (no test classes exist) | N/A | N/A |
| 1 | Backend foundation (startup, config, SQLite) | PASS | startup + SQLite proven | PASS | PASS — confirmed by developer |
| 2 | Health endpoint | PASS | `GET /api/health` → 200 | PASS | PASS — confirmed by developer |
| 3 | Project CRUD | PASS | startup smoke + full CRUD cycle | PASS | **PASS — confirmed by developer** |
| 4 | API & Version | PASS (`mvn -pl apixa-project-service clean package`) | agent smoke: create/get/list/update/delete + 404/400 paths all verified; DB left empty | **PASS** | **PASS — confirmed by developer** |
| 5 | Analysis Run | PASS (`mvn -pl apixa-project-service clean package`) | startup + run 404 smoke only | **PASS** | **PASS — confirmed by developer (roadmap: VERIFIED)** |
| 6 | OpenAPI Import | PASS (`mvn -pl apixa-contract-service clean package`) | agent smoke on 8082: import YAML+JSON (201), retrieval, malformed YAML, non-OpenAPI YAML/JSON, empty file, unsupported `.txt`, missing `file` part, no declared version — all as expected | **PASS** | **PASS — confirmed by developer (roadmap: VERIFIED)** |
| 7 | Contract Extraction | PASS (`mvn -pl apixa-contract-service clean package`) | agent smoke on 8082: `GET /api/contracts/{id}/extract` → 200 (`endpointCount=6`), declared security (global / operation / explicit `security: []` / oauth2 scopes / anonymous alternative), parameters (path+query, path-level merge), request body, responses (200/401/403), 404 unknown id, output deterministic, Step 6 import + retrieval regression | **PASS** | **PASS — confirmed by developer (roadmap: VERIFIED)** |
| 8 | Source Endpoint Analysis | PASS (`mvn -pl apixa-source-analysis-service clean package`) | agent smoke on 8083: `POST /api/source/analyze/endpoints` on `samples/spring-sample` → 200 (`endpointCount=14`, `analyzedFiles=5`), class+method path composition, multi-path / multi-method expansion, path variables verbatim, mapping-annotation line evidence, duplicates preserved, determinism (two runs byte-identical), 400 for blank/missing/nonexistent/file path, empty directory → 200 with 0 endpoints, `POST /api/source/analyze` (FULL) regression | **PENDING** | *awaiting human Postman result* |
| 9 | Source Security Analysis | PASS (`mvn -pl apixa-source-analysis-service clean package`) | agent smoke on 8083: `POST /api/source/analyze/security` on `samples/spring-sample` → 200 (`analysisType=SECURITY`, `securityRuleCount=14`, `analyzedFiles=7`): all 6 `requestMatchers` rules, `anyRequest()` catch-all, method-level `@PreAuthorize`/`@Secured`/`@RolesAllowed`, complex expression preserved, non-literal argument marked unresolved, file/line evidence, determinism (two runs byte-identical), 400 for blank/missing/nonexistent/file path, Step 8 regression (`endpointCount=14`) | **PENDING** | *awaiting human Postman result* |
| 10 | Endpoint Mapping | PASS (`mvn -pl apixa-mapping-service clean package`) | agent smoke on 8084: `POST /api/mappings/map` with `samples/mapping/step10-mapping-request.json` → 200 for all 10 fixture cases (MATCHED / MULTIPLE_CANDIDATES / UNCERTAIN / UNMATCHED as expected), determinism (two runs byte-identical), original paths + `file`/`lineStart` preserved, 400 for empty lists / missing `implementationEndpoints` / malformed body, 404 unknown batch | **PENDING** | *awaiting human Postman result* |
| 11 | Conformance Engine | PASS (`mvn -pl apixa-conformance-service clean package`; `apixa-common` reinstalled into the local repo because the installed jar predated the Step 7/9 `SecurityPolicy` fields) | agent smoke on 8085: `POST /api/conformance/analyze` with `samples/conformance/step11-conformance-request.json` → 200 for all 8 cases (MATCH / MISMATCH / UNVERIFIED as expected), ADMIN-vs-USER research flow → `MATCHED` + `MISMATCH`, `/admin/**` beats `anyRequest()`, unresolved method rule → UNVERIFIED, MULTIPLE_CANDIDATES and UNMATCHED → UNVERIFIED, determinism (two runs byte-identical), 400 for empty/malformed body, `compare` + `compare-batch` regression (3 items → MATCH, MISMATCH, MATCH; one comparison per item) | **PENDING** | *awaiting human Postman result* |
| 12 | Evidence | PASS (`mvn -pl apixa-evidence-service clean package`) | agent smoke on 8086: `POST /api/evidence/analyze` with `samples/evidence/step12-evidence-request.json` → 200, 8 linked items (CONTRACT→MAPPING→SOURCE→SOURCE_SECURITY→CONFORMANCE for `/admin/users`; CONTRACT→MAPPING→CONFORMANCE for the UNVERIFIED MULTIPLE_CANDIDATES case), linkage codes verified, `SecurityConfig.java:44` preserved, exact Step 11 reason preserved, idempotency (4 posts → still 8 rows), SQLite persistence **verified through a full service restart**, 400 for empty/malformed body, pre-existing CRUD unchanged | **PENDING** | *awaiting human Postman result* |
| 13 | Runtime Verification | PASS (`mvn -pl apixa-runtime-service clean package`; sample target built separately) | agent smoke on 8087 against `samples/runtime-sample` on **9090**: `POST /api/runtime/verify` → A public 200, B `/admin/users` anonymous **401**, C as user **403**, D as admin 200, E `/user/profile` as user 200, F `/does-not-exist` **404** — all `status=COMPLETED`; G unused port → `FAILED`/`CONNECTION_FAILURE`; H blackhole IP with `timeoutMs=800` → `TIMEOUT`/`TIMEOUT`; I `Authorization` masked to `[REDACTED]` in result and absent from logs; J repeated request byte-identical apart from `verificationId`/`observedAt`/`durationMs`/`Date` header; 400 for missing URL, non-http scheme, unsupported method, non-positive timeout, malformed body; Steps 10/11/12 regression (mapping MATCHED, conformance 8 results, evidence 8 items) | **PENDING** | *awaiting human Postman result* |
| 14 | Change Impact | PASS (`mvn -pl apixa-impact-service clean package`) | agent smoke on 8088: `POST /api/impact/analyze` with `samples/impact/step14-impact-request.json` → 200 with 9 impact records covering every required case — implemented-security change ADMIN→USER (SECURITY_CHANGED), conformance MATCH→MISMATCH (CONFORMANCE_CHANGED), contract security change on `/reports` (SECURITY_CHANGED), source rule `/admin/**` ADMIN→USER (SECURITY_CHANGED), runtime 200→401 (RUNTIME_OBSERVATION_CHANGED, observation only), `GET /legacy` REMOVED, `POST /users` ADDED, `GET /users`→`POST /users` as removal+addition, `/users/{id}`→`/users/{userId}` and `/unchanged` produce **no** record; V1/V2 evidence codes preserved; determinism (two runs byte-identical); 400 for missing v1/v2, missing data, malformed body; Steps 10/11/12/13 regression intact | **PENDING** | *awaiting human Postman result* |
| 15+ | (later steps) | — | — | NOT STARTED | — |

---

## Step 14 — Change Impact (current)

- **Endpoint:** `POST http://localhost:8088/api/impact/analyze` — compares two supplied snapshots.
- **Request:** `{v1:{projectId,versionId,analysisRunId,contractId,endpoints[],securityRules[],
  runtimeObservations[]}, v2:{...}}`. Nothing is re-analysed and no other APIXA service is called.
- **Endpoint identity:** HTTP method + normalized path *structure*; `{id}` and `{userId}` are one logical
  endpoint (Step 10 segment semantics); trailing slash insignificant; original V1/V2 paths preserved.
  Because the method is part of the identity, `GET /users` → `POST /users` is removal + addition.
- **Security comparison:** order-insensitive normalized comparison of `SecurityPolicy` roles /
  authorities / scopes / authentication / permitAll / unknown. No scope↔authority equivalence invented.
  `null` policy = NOT_PROVIDED, never collapsed into PUBLIC.
- **Categories:** `ADDED_ENDPOINT`, `REMOVED_ENDPOINT`, `MODIFIED_ENDPOINT`, `SECURITY_CHANGED`,
  `CONFORMANCE_CHANGED`, `RUNTIME_OBSERVATION_CHANGED`. **No severity/ranking** — records state facts.
- **Runtime:** observational difference only; 200→401 is never turned into a verdict.
- **Determinism:** sorted by path → method → category → evidence refs → details; two runs byte-identical.
- **Persistence:** none — stateless by design; no evidence records are duplicated or moved.
- **Status:** agent smoke PASS (see table). **Manual Postman verification still pending.**

---

## Step 13 — Runtime Verification (current)

- **Endpoint:** `POST http://localhost:8087/api/runtime/verify` — exactly ONE HTTP request per call.
  No batch test-plan engine in this step.
- **Request:** `{targetBaseUrl, path, method, headers, queryParams, body, timeoutMs, analysisRunId, contractId}`.
  The target is always explicit; the service discovers nothing and calls no other APIXA service.
- **Result:** `status` is the *execution* status (COMPLETED / FAILED / TIMEOUT); `response.statusCode`
  is the raw observed HTTP status. A target 401/403/404 is a **successful verification** (HTTP 200 from
  this service), never a conformance verdict.
- **Transport:** JDK `HttpClient` — no second HTTP library added. Redirects are never followed.
- **Bounds:** connect + request timeout default 5000 ms (max 60000); response body capped at 16 KB;
  request body capped at 16 KB.
- **Sanitization:** `Authorization`, `Cookie`, `X-Api-Key`, … masked to `[REDACTED]` in the request view;
  `Set-Cookie` and credential headers omitted from responses; `password`/`token`/`secret` JSON fields
  masked in captured bodies. Safe headers kept: content-type, content-length, cache-control,
  www-authenticate, allow, location, retry-after, strict-transport-security (+ other non-sensitive, sorted).
- **Target sample:** `samples/runtime-sample` on **port 9090**, HTTP Basic with dummy in-memory users
  (`admin/admin123` → ADMIN, `user/user123` → USER). Built outside the Maven reactor.
- **Persistence:** none — the service is stateless by design and stores no credentials or observations.
- **Status:** agent smoke PASS (see table). **Manual Postman verification still pending.**

---

## Step 12 — Evidence (current)

- **Reused unchanged:** `EvidenceEntity` (table `evidence`), `EvidenceRepository`, the existing SQLite
  config (`./data/evidence.db`), and the pre-existing CRUD routes. The entity was extended with four
  **additive** columns only: `evidenceSetCode`, `httpMethod`, `endpointPath`, `relatedEvidenceCodes`.
- **New routes:** `POST /api/evidence/analyze` (create/refresh a linked chain) and
  `GET /api/evidence/set/{setId}` (retrieve it). No duplicate CRUD routes.
- **Linkage:** each item has a deterministic `EV-<set>-<TYPE>-<METHOD>-<PATH>-<n>` code and stores the
  codes it links to in `relatedEvidenceCodes`; the CONFORMANCE item points back at contract, mapping,
  source and security-rule records.
- **Correlation:** reuses `analysisRunId` + `contractId`; no random correlation ids invented.
- **Idempotency:** deterministic codes → re-posting updates the same rows (4 posts → 8 rows).
- **Evidence records facts, never verdicts:** the Step 11 status/reason are stored verbatim.
- **Verification:** `samples/evidence/verify-evidence.py` queries the SQLite file directly
  (table `evidence`, 18 columns).
- **Status:** agent smoke PASS (see table). **Manual Postman verification still pending.**

---

## Step 11 — Conformance Engine (current)

- **Endpoints:** `POST /api/conformance/compare` and `POST /api/conformance/compare-batch` (pre-existing,
  semantics unchanged; the batch now compares each item exactly **once**), plus the new
  `POST /api/conformance/analyze` — mapping-driven orchestration.
- **Request:** `{contractEndpoints[{method,path,operationId,expectedSecurity}], mappings[Step 10 result],
  securityRules[Step 9 rule]}`. Nothing is discovered here: no OpenAPI re-parse, no Spoon, no local path.
- **Mapping gate:** only `MATCHED` reaches a security verdict. `UNMATCHED`, `MULTIPLE_CANDIDATES` and
  `UNCERTAIN` → `UNVERIFIED` (never MISMATCH). `mappingStatus` is reported separately from `conformanceStatus`.
- **Rule precedence:** METHOD 5000 > CLASS 4000 > concrete path 3000+n > wildcard path 2000+n > catch-all
  (`anyRequest()`) 1000; only the best tier participates, so a broad fallback never competes. Equal-rank
  conflicting rules → UNVERIFIED.
- **Comparison:** delegated unchanged to the pre-existing `SecurityConformanceEngine`
  (MATCH / MISMATCH / PARTIAL / UNVERIFIED). No `bearerAuth == authenticated()` or scope↔authority
  equivalence is invented; such cases follow the engine's existing semantics.
- **Note:** `SecurityPolicy` has primitive booleans, so `permitAll` and `unknown` must be present in
  every policy object.
- **Status:** agent smoke PASS (see table). **Manual Postman verification still pending.**

---

## Step 10 — Endpoint Mapping (current)

- **Endpoint:** `POST http://localhost:8084/api/mappings/map` — the pre-existing route, reused; no second
  mapping API. `GET /api/mappings` and `GET /api/mappings/{id}` read the in-memory batch store.
- **Request:** `{ "contractId", "analysisId", "contractEndpoints":[{method,path,operationId}],
  "implementationEndpoints":[{className,methodName,httpMethod,path,file,lineStart}] }`. Both datasets are
  supplied by the caller; the service never reads an OpenAPI document nor a local source path, and never
  calls the contract / source analysis services (pipeline separation preserved).
- **Status:** agent smoke PASS (see table). **Manual Postman verification still pending.**

---

## Step 8 — Source Endpoint Analysis (current)

- **Endpoint added:** `POST /api/source/analyze/endpoints` on `http://localhost:8083`, request body identical to the pre-existing pass: `{"projectPath":"C:\\Codes\\APIXA\\security-contract-analyzer\\samples\\spring-sample"}` (the same `AnalyzeRequest` record — no second source-path API).
- **Route choice:** the pre-existing `POST /api/source/analyze` mixes endpoint discovery with Spring Security rules (Step 9 material). A narrower sibling route was added for Step 8 instead of changing that contract: same resource, same request body, endpoint discovery only — no OpenAPI contract is read, no contract service is called, no MATCH/MISMATCH is produced.
- **Result shape:** `SourceAnalysisResultDto` (reused, 3 additive fields) — `projectPath`, `analysisType` (`FULL` = pre-existing pass, `ENDPOINTS` = Step 8), `status=COMPLETED`, `analyzerVersion`, `analyzedFiles`, `endpointCount`, `endpoints[]`, `securityRules[]` (omitted for `ENDPOINTS`).
- **Per endpoint (pre-existing `SourceEndpointDto`, 4 additive fields — no duplicate endpoint DTO):** `className` (controller class), `methodName` (Java method), `httpMethod`, `path`, `file` (absolute source path), `lineStart` (line of the mapping annotation), `controllerPackage`, `parameterCount`, `pathResolved`, `unresolvedExpressions`. `implementedSecurity` / `evidence` are omitted because Step 8 performs no security analysis.
- **Supported mapping forms:** `@RestController` / `@Controller` class detection; class-level and method-level `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`; `value` and `path` aliases; single literals and literal arrays (every declared combination produced); `@RequestMapping(method = RequestMethod.X)` and multi-method arrays (GET + HEAD); method-less `@RequestMapping` reported as `ANY` (Spring maps all standard methods — one declaration stays one record).
- **Path handling:** class path + method path composed as Spring composes them (`/api/users` + `/{id}` → `/api/users/{id}`); a method annotation without a path yields the class path (`/api/users`, never `/api/users/`); empty/root mappings collapse to `/`; path variables keep their exact syntax (`/{id}`, never `/*` or `/:id`). Normalisation for mapping against a contract is Step 10.
- **Source evidence:** absolute Java source file plus the 1-based line of the mapping annotation (falls back to the method declaration line when the annotation has no position). `samples/spring-sample/README.md` lists the expected line of every sample endpoint.
- **Determinism:** sorted by path, HTTP method, source file, source line; the source root is normalised, so relative/absolute and `/`/`\` input forms produce identical `file` evidence. Verified: two calls return byte-identical responses.
- **Duplicates:** distinct source declarations are never collapsed — two controllers declaring `GET /api/users` yield two records with their own evidence (verified).
- **Reused:** Spoon 11.2.1 (`Launcher`, `noClasspath`, `TypeFilter`), the pre-existing per-file analysis, controller detection and security extraction, `ApiException` / `GlobalExceptionHandler`, the in-memory result store with `SRC-<pathHash>` ids, `SourceEndpointDto` / `SourceAnalysisResultDto`.


- **Endpoint added:** `GET /api/contracts/{id}/extract` on `http://localhost:8082` → `200` with the extracted contract; `404` (`ErrorBody`) for an unknown/expired contract id (imports are in memory).
- **Route choice:** no extraction endpoint existed. `{id}/extract` follows the existing `{id}` / `{id}/document` sub-resource pattern, is read-only (`GET`) and does not duplicate the Step 6 import route.
- **Result shape:** `ContractExtractionResultDto` — `contractId`, `status=EXTRACTED`, `extractionPerformed=true`, `sourceFile`, `openapiVersion`, `openapiHash`, `title`, `apiVersion`, `endpointCount`, `securitySchemeCount`, `globalSecurity`, `securitySchemes[]`, `endpoints[]`. `status`/`extractionPerformed` distinguish the extracted contract from the imported document (`IMPORTED`/`false`).
- **Per endpoint:** `method`, `path` (verbatim — `/users/{id}` stays `/users/{id}`), `operationId`, `summary`, `description`, `tags`, `deprecated`, `expectedSecurity` (pre-existing `SecurityPolicy`), `declaredSecurity`, `parameters[]`, `requestBody`, `responses[]`, `sourceFile`, `jsonPath`.
- **Security semantics (declared by the contract only):** `source` = `OPERATION` | `GLOBAL` (inherited, operation declares nothing) | `NONE`; `declared`; `explicitlyPublic` (effective `security: []`); `anonymousAccessAllowed` (nothing declared, empty array, or an empty requirement object `{}` among the alternatives); `requiresSecurity` (= declared and no anonymous alternative); `alternatives[]` = OR-list, each alternative an AND-set of scheme references with `name`, `type`, `httpScheme`, `bearerFormat`, `apiKeyIn`, `apiKeyName`, `openIdConnectUrl`, `requiredScopes`, `schemeDefined`.
- **Reused unchanged:** swagger-parser 2.1.40 through the existing `readOpenApi(...)`, the pre-existing `SecurityPolicy` normalization (`normalize(...)` untouched), `NormalizedSecurityContractDto`, the in-memory document store, `ApiException`/`GlobalExceptionHandler`, and the Step 6 endpoints (`POST /import`, `GET /{id}/document`).
- **Changed:** `SpringSourceAnalyzer` (new `analyzeEndpoints(...)`, endpoint discovery shared by both passes, alias/array/`RequestMethod` resolution, annotation-line evidence, fixed path composition, deterministic sort), `SourceAnalysisService` (`analyzeEndpoints(...)`, blank/invalid/nonexistent path → 400, parser failure → 400), `SourceAnalysisController` (`POST /analyze/endpoints`), `SourceEndpointDto`, `SourceAnalysisResultDto`, `apixa-source-analysis-service/pom.xml` (module-level `spring-boot-maven-plugin` with `<skip>false</skip>` — same minimal fix as project/contract service, otherwise `spring-boot:run` exits silently), `apixa-source-analysis-service/data/.gitkeep` (keeps the SQLite directory in git; without it startup fails with `SQLITE_CANTOPEN` — same pattern as contract service).
- **Test input:** `samples/spring-sample/` — source-only sample (no `pom.xml`, not a reactor module, never compiled by the build): 5 Java files, 14 expected endpoints, documented case by case in its README.
- **Security behaviour (explicit):** the Step 8 pass never reads `@PreAuthorize`, `@Secured`, `@RolesAllowed`, `hasRole*` / `hasAuthority*` / `permitAll` expressions, `SecurityFilterChain` or `requestMatchers`. `samples/spring-sample` carries `@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")` on purpose: the `ENDPOINTS` response contains no `securityRules`, `implementedSecurity` or `evidence` at all (verified).
- **Error behaviour:** blank/missing `projectPath` → 400; nonexistent path → 400; file instead of directory → 400; directory without Java source → 200 with `endpointCount=0`; unreadable file → skipped; malformed JSON body → 500 `Unexpected error: HttpMessageNotReadableException` (pre-existing `GlobalExceptionHandler` behaviour). No stack trace is ever returned in a body.
- **Known behaviours / limitations:**
  - Spoon 11.2.1 in `noClasspath` mode recovers from syntax errors: five deliberately broken Java variants (missing brace, unterminated string, garbage tokens, illegal declarations, annotation-only file) all returned 200 with 0 endpoints instead of an error. The `SourceParseException` guard maps a genuine parser failure to a controlled 400 (not triggered by any tested input).
  - Non-literal mapping values (constant references, concatenation) are never guessed: `pathResolved=false` plus the raw expression text in `unresolvedExpressions`.
  - Interface-level mappings, composed/meta-annotations (custom annotations annotated with `@RequestMapping`) and methods inherited from `@RequestMapping`-annotated superclasses are out of scope (not scanned). Class-level `@RequestMapping(method = ...)` is ignored.
  - Files are parsed one at a time in `noClasspath` mode, so a path constant declared in another file cannot be resolved.
  - Pre-existing defect fixed because it broke the shared discovery pass: the security helper called Spoon's `getValueAsString("value")`, which throws `ClassCastException` for array-valued annotations (`@RequestMapping(method = { ... })`, `@GetMapping({ ... })`) and made the pre-existing `POST /api/source/analyze` return 500 for such controllers. Literals are now read defensively; security semantics are unchanged (still Step 9's scope — e.g. `hasAnyRole(...)` in `@PreAuthorize` yields `AUTHENTICATED` without roles, exactly as before).
  - The result id remains `SRC-<pathHash>`, so re-analysing the same path replaces the stored result — now visible through `analysisType`.
- **Human verification result:** PENDING.

## Step 9 — Source Security Analysis (current)

- **Endpoint added:** `POST /api/source/analyze/security` on `http://localhost:8083`, same `AnalyzeRequest` body `{"projectPath":"C:\\Codes\\APIXA\\security-contract-analyzer\\samples\\spring-sample"}`. Independent of OpenAPI: no contract service call, no mapping service call; works with no OpenAPI document present.
- **Result shape:** existing `SourceAnalysisResultDto` (one additive field `securityRuleCount`) with `analysisType=SECURITY`, `status=COMPLETED`, `endpoints` omitted. Existing `SecurityEvidenceDto` reused with 8 additive fields (`scope`, `operator`, `expression`, `className`, `methodName`, `complex`, `unresolved`, `catchAll`) — no duplicate security DTO.
- **Two independent extraction sources:**
  - **Spoon (annotation scope):** `@PreAuthorize`/`@PostAuthorize`, `@Secured`, `@RolesAllowed` at class and method level, via one model build over the whole tree; exact annotation `SourcePosition` line and declaring class/method.
  - **Textual DSL scan (`SECURITY_CONFIGURATION`):** `requestMatchers(...)` chained with `hasRole` / `hasAnyRole` / `hasAuthority` / `hasAnyAuthority` / `permitAll` / `authenticated` / `anonymous` / `denyAll`, plus `anyRequest()`. The fluent Spring Security DSL does not resolve without a compile classpath, so it is read as source text (pre-existing `extractSecurityFilterRules` scan, enriched with the Step 9 fields).
- **Structured operators:** `HAS_ROLE`, `HAS_ANY_ROLE`, `HAS_AUTHORITY`, `HAS_ANY_AUTHORITY`, `PERMIT_ALL`, `AUTHENTICATED`, `ANONYMOUS`, `DENY_ALL`, `SECURED`, `ROLES_ALLOWED`, `EXPRESSION`. `type` is `ROLE` / `AUTHORITY` / `PERMIT_ALL` / `AUTHENTICATED` / `ANONYMOUS` / `DENY_ALL` / `EXPRESSION`. Declared role/authority order is preserved; semantically distinct mechanisms are never collapsed.
- **Scope field:** `SECURITY_CONFIGURATION` / `CLASS` / `METHOD`, never hidden in a string.
- **Complex / unresolved expressions (never guessed, no SpEL engine):**
  - `@PreAuthorize("hasRole('ADMIN') and #id == authentication.name")` → one `HAS_ROLE` rule with the roles extracted, plus `expression` = the **full original expression** and `complex=true`. It is not flattened to "ADMIN".
  - `@PreAuthorize("hasRole(ADMIN_ROLE)")` → `unresolved=true`, no roles invented, raw expression preserved.
- **`@Secured` / `@RolesAllowed`:** the `ROLE_` prefix is kept exactly as declared (`roles=["ROLE_ADMIN"]`); the distinction between the two annotation types is preserved through `operator` (`SECURED` vs `ROLES_ALLOWED`).
- **`anyRequest()`:** represented as `pathPattern="**"` plus the explicit `catchAll=true` flag. No path such as `/**` is invented.
- **Evidence:** every rule carries absolute `file`, `lineStart`, `lineEnd`; annotation rules add `className`/`methodName`. Lines come from Spoon `SourcePosition` (annotations) or the counted newline offset (DSL scan) — never guessed.
- **Determinism:** sorted by file → line → scope → rule type → pathPattern → operator. Two consecutive runs are byte-identical (verified).
- **No deduplication:** two independent declarations that happen to yield the same role stay separate records, so evidence traceability is preserved for Step 12.
- **Files changed:** `SpringSourceAnalyzer` (`analyzeSecurity`, `extractAnnotationSecurity`, `preAuthorizeRules`, `securedRules`, `rolesAllowedRules`, `annotationValuesRule`, `extractConfigRules`, `operatorFor`, `normalizedType`, `isComplexExpression`, `findCloseParen`, `buildModel`, `annotationLine`, sorting), `SourceAnalysisService.analyzeSecurity`, `SourceAnalysisController` (`POST /analyze/security`), `SecurityEvidenceDto`, `SourceAnalysisResultDto`, sample files, this doc. Step 8 discovery code was not modified.
- **Test input:** `samples/spring-sample` extended with `security/SecurityConfig.java` (6 chained rules, one per line) and `secured/SecuredMethods.java` (7 methods, none an endpoint, so `endpointCount` is unchanged at 14).
- **Explicitly NOT done (Steps 10–13):** no security→endpoint mapping, no OpenAPI comparison, no MATCH/MISMATCH, no inference of which endpoint a global matcher covers, no evidence fusion, no runtime verification. Nothing in this step says that a `@PreAuthorize` belongs to a specific HTTP path.
- **Known behaviours / limitations:**
  - The `SECURITY_CONFIGURATION` scan is textual, so a matcher argument that is a constant reference (`PUBLIC_PATHS`) yields a rule with no `pathPattern` rather than a guessed path; authorization chaining inside variables or extracted helper methods is out of scope.
  - `SecurityFilterChain` is detected through the matcher chain itself, not through the `@Bean` method signature — a matcher chain is still reported with its own file/line even if it lives in a helper method.
  - CSRF, CORS, session management, OAuth clients, password encoding and authentication providers are intentionally not analysed in this step.
  - Result id remains `SRC-<pathHash>`, so re-analysing the same path replaces the stored result.
- **Human verification result:** PENDING.

---

## Step 8 — Source Endpoint Analysis
---

## Step 7 — Contract Extraction (PASSED)

- **Changed:** `OpenApiContractParser` (shared `endpointsOf(...)` used by both the normalized contract and extraction, new `extract(...)`, declared-security / parameter / request-body / response extraction), `ContractEndpointDto` (8 new fields instead of a duplicate endpoint DTO), `ContractService.extract(id)` (computed on demand from the stored document), `ContractController`.
- **New DTOs:** `ContractExtractionResultDto`, `ContractSecurityDto`, `ContractSecurityAlternativeDto`, `ContractSecuritySchemeRefDto`, `ContractSecuritySchemeDto`, `ContractParameterDto`, `ContractRequestBodyDto`, `ContractMediaTypeDto`, `ContractResponseDto`.
- **Determinism:** endpoints by path then method; parameters by location (path, query, header, cookie) then name; responses numeric ascending, then `2XX` ranges, then `default`; media types and security schemes by name; scopes sorted; security alternatives in declared order. Verified: two imports of the same file produce identical `endpoints` JSON.
- **Persistence:** none added — extraction is computed on demand from the imported document, which is lost on restart exactly as in Step 6.
- **Not implemented (by scope):** source/Spring analysis, endpoint mapping, conformance, evidence, runtime verification, impact, benchmark, report.
- **Test input:** `samples/openapi/step7-contract.yaml` (6 paths / 6 operations: explicit public endpoint, bearer endpoint, oauth2 `admin` scope, global-inherited security, path + query parameters, POST with request body, 200/401/403 responses, optional/anonymous alternative).
- **Known behaviours / limitations:**
  - A **root-level** `security: []` is dropped by swagger-parser 2.1.40 (bytecode-verified: `OpenAPIDeserializer` applies root security only when the list is non-empty), so it is reported as `source=NONE, declared=false` with identical effective semantics (`anonymousAccessAllowed=true`, `requiresSecurity=false`). Operation-level `security: []` is preserved and reported as `explicitlyPublic=true`.
  - `expectedSecurity.authentication` for a bearer scheme is the scheme *type* (`http`) — pre-existing normalisation, left untouched.
  - Only the OpenAPI contract is read: no Java source is scanned and no Spring role/authority is inferred (Step 8+).
  - `POST /api/contracts/import` without any multipart body returns `500 Unexpected error: HttpMediaTypeNotSupportedException` — pre-existing `GlobalExceptionHandler` behaviour, unrelated to Step 7 (missing or empty multipart `file` part correctly returns `400`).
- **Human verification result:** PASS — confirmed by developer (roadmap: VERIFIED); Step 7 closed.

---

## Step 6 — OpenAPI Import (PASSED)

- **Endpoint added:** `POST /api/contracts/import` (`multipart/form-data`, field `file`) on `http://localhost:8082` → `201` with import metadata only.
- **Retrieval added:** `GET /api/contracts/{id}/document` → the imported document exactly as uploaded (`application/yaml` or `application/json`).
- **Reused unchanged:** `OpenApiContractParser` (swagger-parser 2.1.40 — no second parser, no new library), `NormalizedSecurityContractDto`, in-memory contract store, `ApiException`/`GlobalExceptionHandler` error model, existing `POST /import-content`, `POST /import-file`, `GET /api/contracts`, `GET /api/contracts/{id}`.
- **Changed:**
  - `OpenApiContractParser` — extracted the existing read step into `readOpenApi(...)` (parser exceptions now become 400 instead of 500), added `parseImported(...)` validation (declared `openapi`/`swagger` version + `info` + `paths`).
  - `ContractService` — added the imported-document store, `importUpload(...)`, `importedDocument(...)`, YAML/JSON format detection.
  - `ContractController` — added the multipart import endpoint and the raw-document retrieval endpoint.
  - `apixa-contract-service/pom.xml` — declared `spring-boot-maven-plugin` with `<skip>false</skip>` (root `pluginManagement` `skip=true` made `spring-boot:run` exit silently; same fix as Step 4).
  - `apixa-contract-service/data/.gitkeep` — keeps the SQLite directory in git (`jdbc:sqlite:./data/contract.db`); SQLite cannot create a missing directory, which caused `SQLITE_CANTOPEN`.
- **Storage:** unchanged in-memory design (no entities/repositories exist in this service). The uploaded document is kept verbatim in memory next to the normalized contract; `contract.db` stays empty (no JPA entities) → no SQL verification possible, use `GET /api/contracts/{id}/document`.
- **Not implemented (by scope):** endpoint extraction, security extraction, source analysis, mapping, conformance, evidence, runtime, impact, benchmark, report.
- **Test inputs:** `samples/openapi/` (`valid-openapi.yaml`, `valid-openapi.json`, `invalid-openapi.yaml`, `not-openapi.json`, `not-openapi.yaml`, `empty.yaml`, `spec.txt`).
- **Known behaviours / limitations:**
  - Imported id is a fresh UUID per import (existing design); nothing is persisted across restarts.
  - A Swagger 2.0 file is accepted and the parser reports the *converted* version (`3.0.1`), not the declared `2.0`.
  - Import is not linked to project / API version / analysis run — this service has no such model (not redesigned for this step; Step 4 `openapiHash` in the project store stays untouched).
- **Human verification result:** PASS — confirmed by developer (roadmap: VERIFIED); Step 6 closed. Contract extraction (Step 7) is a separate endpoint (`GET /api/contracts/{id}/extract`) and did not change this import behaviour.

---

## Step 5 — Analysis Run (PASSED)

- **Reused unchanged:** `AnalysisRunEntity` (`analysis_runs`), `AnalysisRunRepository`, DTOs, all 5 run endpoints.
- **Changed:** `ProjectService` — added `RUN_TRANSITIONS` + `requireTransition` (CREATED→RUNNING|FAILED, RUNNING→COMPLETED|FAILED; COMPLETED/FAILED terminal → 400), applied in `updateRunStatus` and `completeRun`.
- **Initial status:** `CREATED` (existing model; equivalent to QUEUED). `startedAt` = creation time (existing, non-updatable); `endedAt` set on COMPLETED/FAILED.
- **Human verification result:** PASS — confirmed by developer (roadmap: VERIFIED); Step 5 closed.

---

## Step 4 — API & Version (PASSED)

- **Files changed:**
  - `apixa-project-service/.../controller/ProjectController.java` — added `GET/PUT/DELETE /api/projects/{id}/versions/{versionId}`
  - `apixa-project-service/.../model/ProjectDtos.java` — added `UpdateVersionRequest(versionLabel, openapiPath, notes)`
  - `apixa-project-service/.../service/ProjectService.java` — added `getVersion`, `updateVersion`, `deleteVersion`, `requireVersion` helper
  - `apixa-project-service/.../repository/AnalysisRunRepository.java` — added `findByApiVersionId` (delete cascade, mirrors `deleteProject` convention)
  - `apixa-project-service/pom.xml` — declared `spring-boot-maven-plugin` with `<skip>false</skip>` (root pluginManagement `skip=true` made `spring-boot:run` exit silently; packaging unaffected — no repackage execution bound)
- **Reused unchanged:** `ApiVersionEntity` (`api_versions`), `ApiVersionRepository`, create/list endpoints, error model, SQLite config.
- **API endpoints:** `POST/GET /api/projects/{id}/versions`, `GET/PUT/DELETE /api/projects/{id}/versions/{versionId}` on `http://localhost:8081`.
- **Automated test status:** no test classes exist; agent smoke verified A–G scenarios (see step response).
- **Manual Postman test instructions:** provided in step response (blocks A–G).
- **Human verification result:** PASS — confirmed by developer (roadmap: VERIFIED); Step 4 closed.
- **Database verification:** `apixa-project-service/data/project.db` → table `api_versions` (insert → update → delete).
- **Known limitations:**
  - `openapiContent` is only hashed on create (`openapiHash`); PUT does not recompute it (OpenAPI import is Step 6).
  - Version belonging to a different project returns 404 (not 400) to avoid ID probing.
  - Deleting a version also deletes analysis runs referencing it (consistent with project cascade); analysis runs are Step 5.
  - Starting the service requires `mvn -pl apixa-project-service spring-boot:run` (now works after pom fix) or the classpath method.
  