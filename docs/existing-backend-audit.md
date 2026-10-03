# APIXA — Existing Backend Audit

- **Audit date:** 2026-09-23
- **Repo:** `C:\Codes\APIXA\security-contract-analyzer` @ commit `6edd69c` (branch `main`)
- **Method:** every non-`target` file read; regex sweeps for tests/annotations/dependencies; Maven build executed (`mvnw -B test`). **Audit-only — no source code modified; only this document was rewritten (the previous version described the obsolete single-module scaffold).**

---

## 1. Executive Summary

The repository has been **restructured into a 12-module Maven reactor** matching the target microservice layout: `apixa-common`, `apixa-gateway`, and 10 services. ~35 Java classes exist. The four research engines (contract parsing, Spoon source analysis, endpoint mapping, conformance comparison) are **real, coherent implementations** that share a common `SecurityPolicy` model.

Critical findings:

1. **BUILD FAIL — the reactor cannot compile.** Root `pom.xml` is non-parseable XML: `<dependencies>` opened at line 42 is never closed; a duplicate `</dependencyManagement>` appears at lines 68–69. `mvnw -B test` fails with `Non-parseable POM … @ line 68, column 26`. Nothing downstream of the POM is executable today.
2. **Implemented engines (preserve):** OpenAPI parsing/normalization (`OpenApiContractParser`), Spoon-based Spring endpoint + security extraction (`SpringSourceAnalyzer`), mapping engine with explicit ambiguity (`EndpointMapper`), rule-based conformance engine (`SecurityConformanceEngine`).
3. **Persistence exists in only 2 of 11 modules.** Project and Evidence use SQLite/JPA. Contract, Source Analysis, Mapping and Conformance keep results in **per-instance `ConcurrentHashMap` memory** — lost on restart, invisible to other services.
4. **Four services are bare scaffolds** (Runtime, Impact, Benchmark, Report: one `@SpringBootApplication` class each, zero controllers). Report carries an unused `openpdf` dependency.
5. **Zero tests** anywhere in the reactor (`@Test` found in no file). `apixa-common` declares a test dependency (`spring-boot-starter-webmvc-test`) with no tests to run.
6. **Gateway is not startable as configured:** it inherits `spring-boot-starter-data-jpa` from `apixa-common` but has **no `application.yml`** and no datasource → JPA auto-configuration fails at startup. It is also a hand-rolled `RestTemplate` proxy, **not** Spring Cloud Gateway, and has no CORS configuration.
7. **Stale config hazard:** `apixa-project-service` still contains the old scaffold's `application.properties`, which **overrides** the module's `application.yml` datasource (`security_analyzer.db` wins over `project.db`).
8. No Docker/Compose, no README, no health endpoints outside the gateway, and `TraceIdFilter` is implemented but **never registered** as a servlet filter — correlation IDs are currently dead code.
9. `SootUp`, `Z3`, `jqwik`, `SpringDoc`, `Spring Cloud` are **absent** from the entire build (only mentioned in the outdated audit doc).

**Overall:** the static-analysis research core (~60 % of the first milestone) exists and is worth preserving; the build, orchestration, persistence, runtime/impact/benchmark/report services, tests and ops tooling are missing or broken.

## 2. Existing Project Structure

```text
security-contract-analyzer/                 Maven reactor root (pom.xml — BROKEN XML)
├── pom.xml                                 parent POM, 12 modules (defect at lines 41–69)
├── mvnw / mvnw.cmd / .mvn/                 Maven wrapper (functional — ran the failed build)
├── apixa-common/                           shared lib: SecurityPolicy, error model, TraceContext(+dead filter)
├── apixa-gateway/                          hand-rolled proxy gateway (no application.yml)
├── apixa-project-service/                  project/version/run CRUD, SQLite (8081) + stale application.properties
├── apixa-contract-service/                 OpenAPI parse + normalize, in-memory store (8082)
├── apixa-source-analysis-service/          Spoon analyzer, in-memory store (8083)
├── apixa-mapping-service/                  endpoint mapper, in-memory store (8084)
├── apixa-conformance-service/              compare engine, no store (8085)
├── apixa-evidence-service/                 evidence persistence, SQLite (8086)
├── apixa-runtime-service/                  scaffold only (8087)
├── apixa-impact-service/                   scaffold only (8088)
├── apixa-benchmark-service/                scaffold only (8089)
├── apixa-report-service/                   scaffold only + unused openpdf dep (8090)
├── benchmark-project/                      EMPTY directory (zero files)
├── docs/existing-backend-audit.md          this document
├── target/                                 STALE classes of the deleted single-module scaffold
│   (com/securityanalyzer/security_contract_analyzer/…Application.class)
└── data/                                   created at runtime relative to working dir; a legacy
                                             security_analyzer.db is also committed under
                                             apixa-project-service/data/
```

Notes:
- **No** `Dockerfile`, `docker-compose.yml`, or `README`; `HELP.md` is the only other doc besides `docs/`.
- `target/` proves the codebase previously built as one app; that artifact is dead weight.
- No `src/test/java` directory exists in **any** module.
- Git history has exactly 2 commits (`6edd69c` main, `0098345` origin/Aman); the reactor structure is unreleased work.

## 3. Current Technology Stack

| Item | Value | Evidence |
|---|---|---|
| Java | 21 (env: 21.0.1) | parent `pom.xml:34` `<java.version>21</java.version>` |
| Spring Boot | **4.1.1** (`spring-boot-starter-parent`) | parent `pom.xml:8` |
| Spring Cloud | **absent** (no gateway/registry/config deps) | no reference in any POM |
| Spring Security | **absent** (analyzed projects use it; APIXA itself does not) | no `spring-boot-starter-security` |
| Build system | Maven 3.9.x wrapper | `mvnw.cmd`; wrapper executed successfully |
| Modules | 12 (pom-packaging root + 12 children) | parent `pom.xml:18–31` |
| Testing framework | JUnit 5 via `spring-boot-starter-webmvc-test` (declared, **never used**) | `apixa-common/pom.xml:27–31` |
| Database | SQLite `org.xerial:sqlite-jdbc:3.50.3.0` + Hibernate community `SQLiteDialect` 7.1.4.Final | parent `pom.xml:35–36,49–57`; every `application.yml` |
| OpenAPI parser | `io.swagger.parser.v3:swagger-parser:2.1.40` — **USED** | parent `pom.xml:59–62`; `OpenApiContractParser.java:7` |
| Java source analyzer | `fr.inria.gforge.spoon:spoon-core:11.2.1` — **USED** | parent `pom.xml:63–67`; `SpringSourceAnalyzer.java:8` |
| SootUp | **absent** | no POM entry, no import anywhere |
| Z3 | **absent** | no POM entry, no import anywhere |
| jqwik | **absent** | no POM entry, no import anywhere |
| SpringDoc | **absent** | no POM entry |
| HTTP client | plain `RestTemplate` (gateway only) | `GatewayController.java:9,30` |
| PDF lib | `com.github.librepdf:openpdf:2.0.3` — **DEPENDENCY ONLY** | `apixa-report-service/pom.xml:27–29` |
| JPA/Hibernate | `spring-boot-starter-data-jpa` via `apixa-common` — used in 2 modules, dead weight in the other 9 | `apixa-common/pom.xml:24–26` |
| Actuator | **absent** | no POM entry, no `/actuator` |
| Bootable-jar packaging | **absent** — modules declare no `spring-boot-maven-plugin` `<build>`; parent sets `skip=true` | parent `pom.xml:83–89` |
| Virtual threads | enabled in all service ymls | `threads.virtual.enabled: true` |

## 4. Current Services

| Module | Port | DB (configured) | Entities | Controllers | Engine code | Status |
|---|---|---|---|---|---|---|
| apixa-gateway | 8080 default (no yml) | none configured | – | `GatewayController` | route map | **BROKEN** (cannot start; no datasource for inherited JPA) |
| apixa-project-service | 8081 | `project.db` (overridden by stale `.properties` → `security_analyzer.db`) | 3 | `ProjectController` | – | PARTIALLY_IMPLEMENTED |
| apixa-contract-service | 8082 | `contract.db` configured, **unused** | 0 | `ContractController` | `OpenApiContractParser` | PARTIALLY_IMPLEMENTED |
| apixa-source-analysis-service | 8083 | `source-analysis.db` configured, **unused** | 0 | `SourceAnalysisController` | `SpringSourceAnalyzer` | PARTIALLY_IMPLEMENTED |
| apixa-mapping-service | 8084 | `mapping.db` configured, **unused** | 0 | `MappingController` | `EndpointMapper` | PARTIALLY_IMPLEMENTED |
| apixa-conformance-service | 8085 | `conformance.db` configured, **unused** | 0 | `ConformanceController` | `SecurityConformanceEngine` | PARTIALLY_IMPLEMENTED |
| apixa-evidence-service | 8086 | `evidence.db` — **USED** | 1 | `EvidenceController` | – | PARTIALLY_IMPLEMENTED |
| apixa-runtime-service | 8087 | `runtime.db` shell | 0 | 0 | 0 | SCAFFOLDED_ONLY |
| apixa-impact-service | 8088 | `impact.db` shell | 0 | 0 | 0 | SCAFFOLDED_ONLY |
| apixa-benchmark-service | 8089 | `benchmark.db` shell | 0 | 0 | 0 | SCAFFOLDED_ONLY |
| apixa-report-service | 8090 | `report.db` shell | 0 | 0 | 0 | SCAFFOLDED_ONLY (openpdf = DEPENDENCY_ONLY) |
| benchmark-project/ | – | – | – | – | – | empty directory (MISSING) |


Adopted from the task's stage plan; unchanged because nothing exists to reuse
beyond the scaffold:

## 5. Service-by-Service Audit

### 5.1 Gateway — Status: **BROKEN**

- **Directory:** `apixa-gateway/` · **App class:** `GatewayApplication` · **Port:** none configured (would default 8080) · **DB:** none · **Controller:** `GatewayController` · **Services/Repos:** none.
- **Responsibility (as written):** reverse-proxy `/api/**` to the 10 services, inject trace header, unified errors.
- **What exists** (`GatewayController.java`):
  - Route table for all 10 target routes (lines 18–28) incl. `/api/projects/**`, `/api/contracts/**`, `/api/source/**`, `/api/mappings/**`, `/api/conformance/**`, `/api/runtime/**`, `/api/evidence/**`, `/api/impact/**`, `/api/reports/**`, `/api/benchmark/**`.
  - `AntPathMatcher` route selection (line 44), method+query+body forwarding via `RestTemplate` (lines 53–62), `X-Trace-Id` propagation (line 57), status passthrough (63–64), 502 on unreachable (65–66), 404 for unrouted (46–48), `GET /api/health` (36–39).
- **Why it is broken:**
  - **No `application.yml`** in the module; `@SpringBootApplication(scanBasePackages="com.apixa")` drags in JPA auto-config from `apixa-common` with **no datasource** → `ThreadPoolJpa`/`DataSource` bean creation fails at startup.
  - `GatewayServicesProperties` (`@ConfigurationProperties(prefix="apixa.services")`) has **no configuration values anywhere** → `url()` returns `null` → every route would answer **503** even if the app started.
  - `TraceIdFilter` exists in common but is never registered → `TraceContext.currentOrNew()` at line 57 always generates a **new random id per request**; correlation across hops is not achieved.
- **Missing:** CORS for the Electron/React frontend (grep for `CrossOrigin`/`CorsRegistry` found nothing), request logging, timeouts, response-header forwarding, body-less GET/DELETE handling (it always attaches a body holder).

### 5.2 Project Service — Status: **PARTIALLY_IMPLEMENTED**

- **Directory:** `apixa-project-service/` · **App:** `ProjectServiceApplication` · **Port 8081** · **DB:** SQLite (JPA) · **Entities:** `ProjectEntity`, `ApiVersionEntity`, `AnalysisRunEntity` · **Repos:** 3 · **Controller:** `ProjectController` (12 endpoints) · **Service:** `ProjectService`.
- **Implemented:**
  - Full project CRUD with validation (`ProjectService.java:34–66`), name-blank checks (157–161).
  - Version create/list with label uniqueness (`ApiVersionRepository.existsByProjectIdAndVersionLabel`) and **SHA-256 of the OpenAPI content** (`ProjectService.java:79–82, 169–176`).
  - Analysis runs: start with apiVersion ownership check (91–112), list/get, status update limited to `CREATED|RUNNING|COMPLETED|FAILED` with `endedAt` set on terminal states (124–137), complete-with-summary (139–147).
  - Runs persist `openapiHash`, `sourceHash`, `analyzerVersion`, `configuration` (entities `AnalysisRunEntity.java:21–25`).
- **Missing / issues:**
  - `CreateVersionRequest.openapiContent` is hashed then **discarded** — the actual OpenAPI text is never stored (`ProjectService.java:79–82`), so downstream services cannot fetch the contract from the project store.
  - `sourcePath` is stored as a plain string; **no complete source code is stored** anywhere (by design a path reference, but nothing validates/read it here).
  - Delete cascades manually via Java loops (59–66), not FK-cascade; race-prone.
  - **Stale `src/main/resources/application.properties`** (old scaffold) overrides the yml datasource → writes go to `security_analyzer.db`, not `project.db`.
  - `AnalysisRunEntity.status` length 20 ok; `configuration` stored via `Map.toString()` (109) — not JSON.
  - No tests.

### 5.3 Contract Service — Status: **PARTIALLY_IMPLEMENTED**

- **Directory:** `apixa-contract-service/` · **App:** `ContractServiceApplication` · **Port 8082** · **DB:** none used (in-memory) · **Controller:** `ContractController` · **Engine:** `OpenApiContractParser` (`swagger-parser` 2.1.40).
- **Implemented** (all verified in `OpenApiContractParser.java`):
  - YAML **and** JSON reading via `OpenAPIParser().readContents(...)` with `setResolve(true)` (lines 32–40) + explicit failure messages (37–39).
  - Path & operation extraction for GET/POST/PUT/DELETE/PATCH (65–84); `operationId` capture (82).
  - Security-scheme extraction (42–46), security-requirement resolution with operation-level→global fallback (75–77).
  - Normalization into the shared `SecurityPolicy` model: `permitAll` for missing/empty security (90–97), scopes from oauth2 flows (108–112), roles from http-bearer entries (121–126), AUTHENTICATED fallback (117–120).
  - Traceability: `sourceFile` + synthetic JSONPath per endpoint (`$.paths['…'].get.security`) (80–83), `openapiHash` SHA-256 (128–135).
  - REST: `POST /api/contracts/import-content`, `POST /import-file`, `GET /`, `GET /{id}`; lookup helper `expectedPolicy(contractId, method, path)` (ContractService.java:56–63).
- **Missing / issues:**
  - **No persistence:** `ConcurrentHashMap` store; `get()` even warns *"re-import after restart"* (`ContractService.java:50`). The configured `contract.db` is never used; no entities/repos exist.
  - Schemes reduced to `name→type` strings — flows/scopes/openIdConnectUrl detail lost at scheme level.
  - `pathItem.getSecurity()` (path-level security object) and shared parameters ignored; `servers`/base URL ignored.
  - `id` is a random UUID per import — re-importing the same file yields a different id (no idempotency/dedup).
  - No tests.

### 5.4 Source Analysis Service — Status: **PARTIALLY_IMPLEMENTED**

- **Directory:** `apixa-source-analysis-service/` · **App:** `SourceAnalysisServiceApplication` · **Port 8083** · **DB:** none used (in-memory) · **Controller:** `SourceAnalysisController` · **Engine:** `SpringSourceAnalyzer` (Spoon 11.2.1).
- **Implemented** (verified in `SpringSourceAnalyzer.java`):
  - Recursive `.java` collection with `target/` exclusion (`collectJavaFiles`, 288–302).
  - **Spoon used for real** (66–81): per-file `Launcher`, `noClasspath` mode, `@RestController`/`@Controller` detection (73–76), class-level `@RequestMapping` base path (260–269).
  - Endpoint extraction: `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping` + method-level `@RequestMapping` (→ `ANY`) with path joining (84–116, 271–286).
  - Method security: `@PreAuthorize` (roles with `ROLE_`-prefix stripping, authorities, `permitAll`) (124–146), `@Secured`/`@RolesAllowed` (148–158); evidence with **file + line** (118–163).
  - Config-level regex scan of `SecurityFilterChain` text: `requestMatchers` patterns, `hasRole/hasAnyRole/hasAuthority/hasAnyAuthority/permitAll/authenticated`, `anyRequest` (181–238), with Ant-style matching of config patterns onto concrete endpoint paths (`configPolicyFor`, 165–179).
  - Output model covers file/lineStart/ruleType/pathPattern/roles/authorities/scopes fields (`SecurityEvidenceDto`).
- **Missing / issues:**
  - **SootUp: not present at all** — no dependency, no usage (bytecode analysis entirely absent).
  - `lineEnd` never populated for endpoints; `hasRole`/`hasAuthority` **expressions with constants or bean refs** (`@PreAuthorize("hasRole(@roles.X)")`), meta-annotations, and XML/`WebSecurityConfigurerAdapter` security are not handled.
  - Endpoint→config-rule matching is **first-match-wins** over an unordered regex-derived rule list; the real filter-chain ordering (first match wins *in declaration order*) is approximated only by text position — misattribution possible.
  - Scopes are never populated by the analyzer (field exists, always null).
  - No persistence (`ConcurrentHashMap`, key `SRC-<pathHash>` — **same project path always overwrites** previous result, `SourceAnalysisService.java:42–44`).
  - Class-level `@PreAuthorize` not extracted; no tests.

### 5.5 Mapping Service — Status: **PARTIALLY_IMPLEMENTED**

- **Directory:** `apixa-mapping-service/` · **App:** `MappingServiceApplication` · **Port 8084** · **DB:** none used (in-memory) · **Controller:** `MappingController` · **Engine:** `EndpointMapper`.
- **Implemented** (`EndpointMapper.java`):
  - Method + Ant-path matching between contract endpoints and implementation endpoints (23–48); all four statuses emitted: `MATCHED / MULTIPLE_CANDIDATES / UNMATCHED / UNCERTAIN` (29–47).
  - `ANY` and comma-listed impl methods accepted (50–55); confidence HIGH/MEDIUM/LOW from operationId equality, literal path equality, wildcard and path-var heuristics (57–66); ambiguity **kept explicit** with a candidates list + reason (35–41).
- **Missing / issues:**
  - Caller must POST **both endpoint lists in the request body** (`MapRequest`) — no service-to-service fetch of stored contract/source results (`MappingController.java:16–35`); it cannot self-orchestrate.
  - One-sided matching only (`matcher.match(impl.path, contractPath)`); impl `**` patterns yield LOW-confidence matches; `?` single-segment wildcards and case differences unhandled.
  - No persistence (batch ids `contractId-analysisId`, `MappingService.java:40–42`), no tests.

### 5.6 Conformance Service — Status: **PARTIALLY_IMPLEMENTED**

- **Directory:** `apixa-conformance-service/` · **App:** `ConformanceServiceApplication` · **Port 8085** · **DB:** none used · **Controller:** `ConformanceController` · **Engine:** `SecurityConformanceEngine` · **`ConformanceService.java` is an empty `@Service` placeholder (7 lines).**
- **Implemented** (`SecurityConformanceEngine.java`):
  - Real comparison algorithm with the four required outcomes `MATCH / MISMATCH / PARTIAL / UNVERIFIED` (enum line 19; logic 23–89).
  - Compares: open-vs-open, open-vs-protected, protected-vs-open (33–47); role/authority/scope set comparison with `ROLE_`-prefix equivalence and role↔authority cross-tolerance (49–75); expected-strong-but-impl-auth-only → `PARTIAL` (77–81); impl-stricter-than-contract → `PARTIAL` (82–86); both auth-only → `MATCH` (87–88).
  - REST: `POST /api/conformance/compare` and `/compare-batch` (`ConformanceController.java:22–33`).
- **Missing / issues:**
  - **No persistence, no findings store, no DB table** — results live only in the HTTP response; the `evidenceIds` field in `FindingDto` is **always null** (line 31).
  - Findings carry method/path/expected/implemented/reason but no evidence linkage and no run/contract/source ids; the service has **no HTTP client**, so it cannot orchestrate contract+source+mapping itself.
  - Engine cross-type tolerance is loose (roles ≈ authorities counts as MATCH — can mask real mismatches); batch endpoint invokes `engine.compare()` twice per item (lines 30–31); no tests.

### 5.7 Evidence Service — Status: **PARTIALLY_IMPLEMENTED**

- **Directory:** `apixa-evidence-service/` · **App:** `EvidenceServiceApplication` · **Port 8086** · **DB:** SQLite (JPA, **USED**) · **Entity:** `EvidenceEntity` (table `evidence`) · **Repo:** `EvidenceRepository` · **Controller:** `EvidenceController`.
- **Implemented:**
  - Persisted evidence rows with `evidenceCode`, `sourceType ∈ {CONTRACT, SOURCE, MAPPING, STATIC, DYNAMIC}` (`EvidenceEntity.java:12–15`), file/lineStart/lineEnd/jsonPath/ruleType/pathPattern/description/content(payload)/analysisRunId (16–25).
  - REST: `POST /api/evidence` (auto code `EV-<millis>`), `GET /`, `GET /run/{runId}`, `GET /{code}`; repo supports lookup by code/run/sourceType.
- **Missing / issues:**
  - **No evidence-fusion logic** — the service only stores what a client posts; nothing generates `STATIC` evidence rows from analysis output, and no finding↔evidence join exists (no `findingId`).
  - No dedup, no trace-id column, no tests.

### 5.8 Runtime Service — Status: **SCAFFOLDED_ONLY**

- `RuntimeServiceApplication.java` (10 lines) + `application.yml` (port 8087, unused `runtime.db`). **No HTTP client, no test-case generator, no results model, no controller.** No `HttpClient`/`OkHttp`/jqwik anywhere in the repo (grep negative). Dynamic security verification is **MISSING in code** (module-level intent DOCUMENTED_ONLY via POM description).

### 5.9 Impact Service — Status: **SCAFFOLDED_ONLY**

- `ImpactServiceApplication.java` + `application.yml` (port 8088, unused `impact.db`). No controllers/engines/entities. Version-to-version change analysis (`SECURITY_EXPANDING/RESTRICTING/NEUTRAL/UNKNOWN`) is **MISSING**.

### 5.10 Benchmark & Report Services — Status: **SCAFFOLDED_ONLY**

- **Benchmark:** `BenchmarkServiceApplication.java` + `application.yml` (port 8089, unused `benchmark.db`). No mutation engine, no ground truth, no metrics code. The folder `benchmark-project/` is **empty** (zero files) — no mutation target exists. All benchmark concepts are **MISSING** in code.
- **Report:** `ReportServiceApplication.java` + `application.yml` (port 8090, unused `report.db`). No report builder; `openpdf` 2.0.3 is a **DEPENDENCY_ONLY** entry in the POM. No PDF/HTML/JSON generation anywhere (grep negative). Report content model **MISSING**.

### 5.11 apixa-common — Status: **PARTIALLY_IMPLEMENTED (usable core)**

- **Shared model:** `SecurityPolicy` record — the de-facto contract between all engines: authentication/schemeName/roles/authorities/scopes/permitAll/unknown + factory helpers (`SecurityPolicy.java:8–43`). **Preserve.**
- **Error model:** `ApiException` (+badRequest/notFound/internal factories), `ErrorBody`, `GlobalExceptionHandler` (`@RestControllerAdvice` covering ApiException/validation/404/generic with trace-ready `ErrorBody`) — `GlobalExceptionHandler.java:14–44`. **Preserve.**
- **Trace:** `TraceContext` ThreadLocal + `TraceIdFilter` (`X-Trace-Id` in/out) — implemented but **never registered** as a filter anywhere; `apixa.service-name` yml keys are read by nothing (`apixa.service-name` grep-negative). Dead code today.
## 6. Feature Matrix

Legend: ✅ = implemented, 🟡 = partial, ❌ = missing/broken. Statuses per §22 classification rules.

| Feature | Implemented? | Partial? | Broken? | Evidence |
|---|---|---|---|---|
| Project CRUD | ✅ | – | – | `ProjectController.java:20–39`; `ProjectService.java:34–66`; JPA `ProjectRepository` |
| API versions | ✅ (create/list + hash) | 🟡 content discarded | – | `ProjectController.java:41–48`; `ProjectService.java:69–88` (content hashed then dropped) |
| Analysis runs | ✅ (lifecycle) | 🟡 no orchestration | – | `ProjectController.java:50–70`; `ProjectService.java:91–147` |
| OpenAPI YAML parser | ✅ | – | – | `OpenApiContractParser.java:28–40` (`readContents` handles YAML) |
| OpenAPI JSON parser | ✅ (same path) | – | – | same call parses JSON by content detection |
| Security extraction | ✅ | 🟡 schemes simplified | – | `OpenApiContractParser.java:42–46, 87–126` |
| Security normalization | ✅ | 🟡 no path-level security | – | `normalize()` 87–126 → `SecurityPolicy` |
| Traceability | 🟡 | ✅ sourceFile+JSONPath+hash | ❌ not persisted | `ContractEndpointDto` (sourceFile, jsonPath); no DB |
| Spring controller extraction | ✅ | – | – | `SpringSourceAnalyzer.java:72–81` |
| Spring endpoint extraction | ✅ | 🟡 no `params`/`consumes`/`headers` | – | `SpringSourceAnalyzer.java:84–116` |
| Spring Security extraction | ✅ | 🟡 ordering approximated | – | `SpringSourceAnalyzer.java:181–238` + `configPolicyFor` 165–179 |
| Role extraction | ✅ | – | – | `hasRole`/`hasAnyRole` 216–222; `@PreAuthorize` 128–138; `@Secured` 148–157 |
| Authority extraction | ✅ | – | – | `hasAuthority`/`hasAnyAuthority` 223–229; `@PreAuthorize` 140–142 |
| Method-security annotations | ✅ | 🟡 no class-level | – | `methodSecurity()` 118–163 |
| Endpoint mapping | ✅ core | 🟡 one-sided, no orchestration | – | `EndpointMapper.java:23–66` |
| Ambiguity handling | ✅ | – | – | `MULTIPLE_CANDIDATES` + candidates list `EndpointMapper.java:35–41` |
| Static conformance | ✅ core | 🟡 not stored | – | `SecurityConformanceEngine.compare()` 23–89 |
| MATCH | ✅ | – | – | engine 36–39, 60–68, 87–88 |
| MISMATCH | ✅ | – | – | engine 40–47 |
| PARTIAL | ✅ | – | – | engine 77–86 |
| UNVERIFIED | ✅ | – | – | engine 24–31 |
| Evidence fusion | ❌ | – | – | nothing links findings↔evidence; `FindingDto.evidenceIds` always null |
| Runtime verification | ❌ | – | – | module has 1 class; no HTTP client in repo |
| Version impact | ❌ | – | – | module has 1 class |
| PDF reporting | ❌ | – | – | openpdf declared (`report pom:27–29`), zero usage |
| HTML reporting | ❌ | – | – | no template/template-engine anywhere |
| JSON reporting | ❌ (as a service) | 🟡 JSON is just HTTP responses | – | no report builder exists |
| Benchmark | ❌ | – | – | 1-class module; `benchmark-project/` empty |
| Precision/Recall/F1 | ❌ | – | – | no metrics code anywhere |
| Docker Compose | ❌ | – | – | no docker files in repo |
| Health endpoints | 🟡 | ✅ gateway only | – | `GatewayController.java:36–39`; no actuator, no per-service health |
| Correlation IDs | ❌ | – | – | `TraceIdFilter` never registered; gateway generates fresh id per hop |

## 7. REST API Inventory

All paths are as coded; all are currently **unbuildable** until the POM is fixed. "Persistence" = backing store actually used.

| # | HTTP | Path | Controller | Method | Request DTO | Response DTO | Backing service | Persistence | Status |
|---|---|---|---|---|---|---|---|---|---|
| 1 | GET | `/api/health` | `GatewayController` | `health()` | – | `Map` | – | – | IMPLEMENTED (code) / BROKEN (start) |
| 2 | ANY | `/api/{routed}/**` | `GatewayController` | `proxy()` | passthrough | passthrough | downstream service | downstream | IMPLEMENTED (code) / BROKEN (start+config) |
| 3 | POST | `/api/projects` | `ProjectController` | `create` | `CreateProjectRequest` | `ProjectDto` | `ProjectService.createProject` | `ProjectRepository` (SQLite) | IMPLEMENTED |
| 4 | GET | `/api/projects` | `ProjectController` | `list` | – | `List<ProjectDto>` | `ProjectService.listProjects` | SQLite | IMPLEMENTED |
| 5 | GET | `/api/projects/{id}` | `ProjectController` | `get` | – | `ProjectDto` | `ProjectService.getProject` | SQLite | IMPLEMENTED |
| 6 | PUT | `/api/projects/{id}` | `ProjectController` | `update` | `UpdateProjectRequest` | `ProjectDto` | `ProjectService.updateProject` | SQLite | IMPLEMENTED |
| 7 | DELETE | `/api/projects/{id}` | `ProjectController` | `delete` | – | 204 | `ProjectService.deleteProject` | SQLite | IMPLEMENTED |
| 8 | POST | `/api/projects/{id}/versions` | `ProjectController` | `addVersion` | `CreateVersionRequest` | `ApiVersionDto` | `ProjectService.addVersion` | SQLite | IMPLEMENTED (content dropped) |
| 9 | GET | `/api/projects/{id}/versions` | `ProjectController` | `versions` | – | `List<ApiVersionDto>` | `ProjectService.listVersions` | SQLite | IMPLEMENTED |
| 10 | POST | `/api/projects/{id}/analysis-runs` | `ProjectController` | `startRun` | `StartRunRequest` | `AnalysisRunDto` | `ProjectService.startRun` | SQLite | IMPLEMENTED |
| 11 | GET | `/api/projects/{id}/analysis-runs` | `ProjectController` | `runs` | – | `List<AnalysisRunDto>` | `ProjectService.listRuns` | SQLite | IMPLEMENTED |
| 12 | GET | `/api/projects/analysis-runs/{runId}` | `ProjectController` | `run` | – | `AnalysisRunDto` | `ProjectService.getRun` | SQLite | IMPLEMENTED |
| 13 | POST | `/api/projects/analysis-runs/{runId}/status` | `ProjectController` | `updateRunStatus` | `UpdateRunStatusRequest` | `AnalysisRunDto` | `ProjectService.updateRunStatus` | SQLite | IMPLEMENTED |
| 14 | POST | `/api/projects/analysis-runs/{runId}/complete` | `ProjectController` | `completeRun` | `Map<String,String>` | `AnalysisRunDto` | `ProjectService.completeRun` | SQLite | IMPLEMENTED |

| 15 | POST | `/api/contracts/import-content` | `ContractController` | `importContent` | `ImportContentRequest` | `NormalizedSecurityContractDto` | `ContractService.importFromContent` | in-memory map | IMPLEMENTED (volatile) |
| 16 | POST | `/api/contracts/import-file` | `ContractController` | `importFile` | `ImportFileRequest` | `NormalizedSecurityContractDto` | `ContractService.importFromFile` | in-memory map | IMPLEMENTED (volatile) |
| 17 | GET | `/api/contracts` | `ContractController` | `list` | – | `Map<id,Contract>` | `ContractService.all` | in-memory map | IMPLEMENTED (volatile) |
| 18 | GET | `/api/contracts/{id}` | `ContractController` | `get` | – | `NormalizedSecurityContractDto` | `ContractService.get` | in-memory map | IMPLEMENTED (volatile) |
| 19 | POST | `/api/source/analyze` | `SourceAnalysisController` | `analyze` | `AnalyzeRequest` | `SourceAnalysisResultDto` | `SourceAnalysisService.analyze` | in-memory map | IMPLEMENTED (volatile) |
| 20 | GET | `/api/source` | `SourceAnalysisController` | `list` | – | `Map<id,Result>` | `SourceAnalysisService.all` | in-memory map | IMPLEMENTED (volatile) |
| 21 | GET | `/api/source/{id}` | `SourceAnalysisController` | `get` | – | `SourceAnalysisResultDto` | `SourceAnalysisService.get` | in-memory map | IMPLEMENTED (volatile) |
| 22 | POST | `/api/mappings/map` | `MappingController` | `map` | `MapRequest` (both lists) | `List<MappingResultDto>` | `MappingService.map` | in-memory map | IMPLEMENTED (volatile) |
| 23 | GET | `/api/mappings` | `MappingController` | `list` | – | `Map<id,List>` | `MappingService.all` | in-memory map | IMPLEMENTED (volatile) |
| 24 | GET | `/api/mappings/{id}` | `MappingController` | `get` | – | `List<MappingResultDto>` | `MappingService.get` | in-memory map | IMPLEMENTED (volatile) |
| 25 | POST | `/api/conformance/compare` | `ConformanceController` | `compare` | `CompareRequest` | `Finding` | `SecurityConformanceEngine.compare` | none | IMPLEMENTED (stateless) |
| 26 | POST | `/api/conformance/compare-batch` | `ConformanceController` | `compareBatch` | `List<CompareRequest>` | `List<FindingDto>` | engine (×2 per item) | none | IMPLEMENTED (minor bug: double compare) |
| 27 | POST | `/api/evidence` | `EvidenceController` | `store` | `EvidenceEntity` body | `EvidenceEntity` | `EvidenceService.store` | SQLite | IMPLEMENTED |
| 28 | GET | `/api/evidence` | `EvidenceController` | `all` | – | `List<EvidenceEntity>` | `EvidenceService.all` | SQLite | IMPLEMENTED |
| 29 | GET | `/api/evidence/run/{runId}` | `EvidenceController` | `byRun` | – | `List<EvidenceEntity>` | `EvidenceService.byRun` | SQLite | IMPLEMENTED |
| 30 | GET | `/api/evidence/{code}` | `EvidenceController` | `byCode` | – | `EvidenceEntity` | `EvidenceService.byCode` | SQLite | IMPLEMENTED |
| 31–34 | – | `/api/runtime/**`, `/api/impact/**`, `/api/reports/**`, `/api/benchmark/**` | – | – | – | – | – | – | MISSING (no controllers; gateway would 503) |

Legend for status: no endpoint is MOCK/HARDCODED/TODO-stubbed; all coded endpoints have real service+engine logic; the broken dimension is the **build/start**, not endpoint logic. `project-service` addVersion is the one IMPLEMENTED-with-defect case (content discarded).

## 8. Database Audit

**Architecture: service-owned SQLite files** (one per module, `jdbc:sqlite:./data/<module>.db`, `ddl-auto=update`, Hibernate community `SQLiteDialect`) — matches the target pattern *nominally*, but only 2 of 11 modules actually persist.

| Module | DB file (yml) | Actually used? | Tables (JPA entities) | What is persisted |
|---|---|---|---|---|
| project-service | `project.db` — **overridden by stale `application.properties`** → `security_analyzer.db` | YES (wrong file) | `projects`, `api_versions`, `analysis_runs` | project meta (name, apiName, baseUrl, sourcePath, sourceHash, createdAt); version label/path/hash/notes; run status/hashes/analyzerVersion/config/resultSummary/timestamps |
| contract/source/mapping/conformance | `<module>.db` configured | NO | – | nothing — in-memory maps only |
| evidence-service | `evidence.db` | YES | `evidence` | evidenceCode, sourceType ∈ {CONTRACT, SOURCE, MAPPING, STATIC, DYNAMIC}, file, lines, jsonPath, ruleType, pathPattern, description, content JSON, analysisRunId, createdAt |
| runtime/impact/benchmark/report | `<module>.db` configured | NO | – | nothing |
| gateway | none | – | – | – |

Notes:
- H2/PostgreSQL/MySQL: **absent** everywhere.
- A legacy `security_analyzer.db` is committed under `apixa-project-service/data/` (binary artifact in git — audit note only, not changed).
- SQLite + JPA `GenerationType.IDENTITY` works via the community dialect; **no FK constraints** are declared (all relations are plain `Long` columns).
- `GenerationType.IDENTITY` + SQLite is supported, but `ddl-auto=update` against SQLite is fragile for schema evolution (no ALTER ergonomics) — audit observation.

## 9. Analyzer Audit

| Analyzer | Library | Actually used? | Location | What it does | Gaps |
|---|---|---|---|---|---|
| OpenAPI parser | swagger-parser 2.1.40 | **YES** | `apixa-contract-service/.../openapi/OpenApiContractParser.java` | YAML/JSON → normalized `SecurityPolicy` per endpoint, scheme map, SHA-256, JSONPath | scheme detail, path-level security, dedup |
| Spring source analyzer | Spoon 11.2.1 | **YES** | `apixa-source-analysis-service/.../engine/SpringSourceAnalyzer.java` | controllers, endpoints, method+config security with line evidence | chain ordering, scopes, class-level security, constant/bean-ref SpEL |
| SootUp | absent | – | – | – | entire bytecode tier missing |
| Z3 | absent | – | – | – | no SMT solving anywhere |
| Regex tier | JDK `java.util.regex` | **YES** | `SpringSourceAnalyzer.extractSecurityFilterRules` (package-private — test-visible signature) | SecurityFilterChain DSL text scan | ordering semantics approximated by text position |

The research claim "static analysis of Spring Security configuration" is **supported by real code**; "dynamic/runtime verification", "impact", "benchmark", "report" have **no engine code at all**.

## 10. Test Audit

**There are zero tests in the entire reactor.** `@Test`, `@SpringBootTest`, `@WebMvcTest` appear in **no file** (regex sweep over all sources). No `src/test/java` directory exists in any module. The only test artifact is:

- `apixa-common/src/test/resources/application-common-test.yml` — a test *resource* with a single logging line, with no test to load it.

Per-subsystem coverage:

| Subsystem | Tests exist? | Meaningful logic covered? |
|---|---|---|
| Common (error/trace/model) | NO | – |
| Project CRUD | NO | – |
| OpenAPI parsing/normalization | NO | – |
| Spoon source analysis | NO | – |
| Endpoint mapping | NO | – |
| Conformance engine | NO | – |
| Evidence persistence | NO | – |
| Runtime / Impact / Benchmark / Report | NO | – |

**Test pass/fail: NOT RUNNABLE** — `mvnw test` aborts before the test phase (POM parse error). Note: `extractSecurityFilterRules` is package-private, suggesting the author *intended* unit tests that were never written.

## 11. Docker / Runtime Audit

- `Dockerfile`: **NOT FOUND** (recursive search, zero results).
- `docker-compose.yml`: **NOT FOUND**.
- No `.env`, no CI workflows, no k8s manifests.
- Which services can start today: **none provably** — the reactor does not compile (POM XML error); modules also lack `spring-boot-maven-plugin` build sections, so even with a fixed POM, `mvn package` would not produce runnable jars (parent sets `skip=true` and no module overrides it).
- If started from an IDE with a fixed POM: expected ports 8080(gateway, will fail — no datasource), 8081–8086 (likely OK; each writes `./data/*.db` relative to its working directory), 8087–8090 (empty shells start, expose nothing but 404s/error handler).
- Safe local execution was **not** attempted beyond the build, because the build itself fails (audit rule: do not fix).

## 12. Security / Privacy Audit

| Check | Result | Evidence |
|---|---|---|
| Secrets in source | **NOT FOUND** | all yml/properties files inspected: only DB paths + app names; no passwords, tokens, API keys |
| Tokens in logs | **NOT FOUND** | log statements log ids/titles/counts only (`ContractService.java:31–32`, `GlobalExceptionHandler.java:36–37`) |
| API keys in configuration | **NOT FOUND** | no property in any profile carries a credential |
| Source code sent externally | **NOT FOUND** | all analysis is local: `Files.readString` + Spoon in-process; gateway forwards to **loopback-configured services only** (no external endpoints configured) |
| Unsafe logging | **NOT FOUND** | no `System.out.println` debugging of payloads; stack traces only to log on 500 with class name only in response body |
| Committed binary DB | **FOUND (hygiene)** | `apixa-project-service/data/security_analyzer.db` committed to git |

## 13. End-to-End Workflow Status

Conceptual dry-run of the required pipeline using only existing code (no fixes applied):

| Step | State | Where it stops / what works |
|---|---|---|
| OpenAPI file → contract | **PASS** (in code) | `POST /api/contracts/import-content` → `OpenApiContractParser.parse` produces normalized contract (volatile in-memory) |
| Spring project → source analysis | **PASS** (in code) | `POST /api/source/analyze {projectPath}` → `SpringSourceAnalyzer.analyze` walks files, Spoon-extracts endpoints + security (volatile) |
| Map endpoints | **PARTIAL** | `POST /api/mappings/map` works **only if a client manually copies both endpoint arrays into the request** — no service fetches stored results |
| Compare security | **PARTIAL** | `POST /api/conformance/compare` produces MATCH/MISMATCH/PARTIAL/UNVERIFIED, **but the caller must assemble expected+implemented policies per endpoint by hand**; no orchestration exists |
| Produce finding | **MISSING** | nothing composes mapping+comparison into a persisted finding; `ConformanceService` is an empty placeholder |
| Attach evidence | **MISSING** | evidence rows can be POSTed manually, but nothing generates them from analysis, and `evidenceIds` on findings is always null |
| Persist run results | **MISSING** | contract/source/mapping/conformance results never touch a database |

```text
OpenAPI parsing       PASS   (volatile)
Source analysis       PASS   (volatile)
Endpoint mapping      PARTIAL (manual data shuttling required)
Conformance           PARTIAL (per-pair manual invocation)
Findings              MISSING
Evidence fusion       MISSING
Persistence of results  MISSING
```

**Workflow stops after conformance `compare()`:** the engine's `Finding` record is the pipeline's logical terminus today. Everything after it (finding persistence, evidence generation/linkage, reporting, runtime, impact, benchmark) is unimplemented.

## 14. Research Pipeline Gap

```text
CURRENT
   │
   ├─ OpenAPI parse + normalize ............ IMPLEMENTED (in-memory only)
   ├─ Spoon source/security extraction ..... IMPLEMENTED (in-memory only)
   ├─ Ant endpoint mapping ................. IMPLEMENTED (manual input, in-memory)
   ├─ Static conformance engine ............ IMPLEMENTED (stateless, per-pair)
   ├─ Evidence persistence ................. PARTIAL (store only, no fusion)
   ├─ Project/run bookkeeping .............. PARTIAL (metadata only)
   │
   ▼  MISSING / PARTIAL LAYER
   ├─ Build ............................... BROKEN (root POM XML)
   ├─ Orchestration (contract→source→map→compare) ... MISSING
   ├─ Finding persistence + evidence linkage ........ MISSING
   ├─ Correlation IDs / CORS / gateway runtime ...... MISSING/BROKEN
   ├─ Runtime verification .......................... MISSING
   ├─ Version impact ................................ MISSING
   ├─ Benchmark + mutations + P/R/F1 ................ MISSING
   ├─ Report generation (PDF/HTML/JSON) ............. MISSING
   └─ Tests (unit/integration/e2e) .................. MISSING
   │
   ▼
TARGET (research pipeline per thesis statement)
```

The single biggest architectural gap is the **absence of an orchestrator**: every engine works on in-process inputs, but no component fetches a stored contract, a stored source-analysis result, maps them, compares them, persists findings, and attaches evidence. `ConformanceService` (the natural home) is an empty class.

## 15. Recommended Implementation Order

1. **Fix the root `pom.xml`** (close `<dependencies>` properly; remove the duplicate `</dependencyManagement>`) — unblocks everything else.
2. Remove the stale `application.properties` in `apixa-project-service`; add `application.yml` (+ `apixa.services.*` URLs) to the gateway and exclude/disable JPA auto-config there (or slim `apixa-common` so JPA is opt-in per service).
3. Register `TraceIdFilter` (`FilterRegistrationBean` in common), enable CORS at the gateway, add actuator health to every service.
4. Persistence for contract/source/mapping/conformance results (entities keyed by `analysisRunId`) so runs survive restarts.
5. Orchestration endpoint (in conformance or a new orchestrator): runId → fetch contract + source analysis → map → compare → persist findings → generate STATIC evidence rows with `evidenceId` linkage.
6. Store OpenAPI content in the project service (currently discarded) and let the orchestrator resolve it.
7. Runtime service: HTTP client + targeted security cases (no-auth, invalid-auth, low-priv, required-role) + result recording.
8. Impact service: contract-vs-contract diff with SECURITY_EXPANDING/RESTRICTING/NEUTRAL/UNKNOWN.
9. Benchmark service + a real `benchmark-project/` sample app with mutations; store raw measurements; compute precision/recall/F1 for Static / Dynamic / Hybrid configurations.
10. Report service: JSON (from stored findings), HTML template, PDF via the already-declared openpdf.
11. Test suite per engine (parser fixtures, analyzer fixtures, mapper cases, conformance truth table) + integration test for the full pipeline.

## 16. Critical Missing Components

1. **Valid root POM** — blocks compilation of all 12 modules; first milestone is impossible until fixed.
2. **Orchestrator / analysis-run pipeline** — the four engines are islands; nothing chains contract → source → mapping → conformance → findings → evidence.
3. **Finding entity + evidence linkage** — no `finding` table, no `findingId`/`evidenceId` join; `evidenceIds` is always null.
4. **Result persistence** — 4 of 6 engine-bearing modules lose all data on restart.
5. **Gateway runtime configuration** — no yml, no `apixa.services.*` values, no CORS; and the module cannot start due to inherited JPA.
6. **Runtime service** (dynamic verification) — entirely absent (no HTTP client anywhere).
7. **Impact service** (version diff) — entirely absent.
8. **Benchmark service + mutation corpus** — entirely absent; `benchmark-project/` is empty.
9. **Report service** — entirely absent (openpdf = dependency only).
10. **Tests** — zero across the reactor.
11. **Bootable packaging** — no `spring-boot-maven-plugin` build sections; nothing produces runnable jars.

### Demonstration check (required case)

Contract `GET /admin/users` requiring `ADMIN` vs implementation `.requestMatchers("/admin/**").hasRole("USER")`:

- Contract side: security requirement normalizes to `SecurityPolicy(roles=[ADMIN])` (`OpenApiContractParser.normalize`).
- Source side: `extractSecurityFilterRules` captures pattern `/admin/**` + role `USER`; `configPolicyFor` Ant-matches it onto `/admin/users` → `SecurityPolicy(roles=[USER])`.
- Engine: expected {ADMIN} vs implemented {USER}, both non-open and strong → **MISMATCH** (`SecurityConformanceEngine.compare`, lines 49–75).

**Verdict: YES at engine level** — the code paths exist and are type-compatible. **PARTIALLY as a system**: a caller must manually ferry the two policies into `/api/conformance/compare`; nothing automates it, and today nothing runs at all because the build is broken.

## 17. Risks / Limitations

- **Everything is unverifiable at runtime** until the POM is fixed — all "IMPLEMENTED" endpoint statuses rest on code inspection, not execution.
- In-memory stores make the current architecture single-instance and non-durable; horizontal scaling or restarts destroy analysis state.
- Regex-based `SecurityFilterChain` scanning is brittle: chained-call window heuristics (lines 196–211) can mis-associate rules with patterns; real filter-chain ordering is only approximated.
- Loose role/authority cross-tolerance in the conformance engine can produce false MATCHes (research-accuracy risk).
- First-match-wins config-rule attribution may mislabel endpoints when multiple patterns match.
- Spring Boot 4.1.1 + Hibernate community SQLite dialect is an unusual stack; upgrade churn risk.
- Committed binary DB and stale scaffold artifacts (`target/`, old `application.properties`) indicate incomplete restructure hygiene.
- Git state: all restructure work sits unpushed on `main` (single commit `6edd69c`) with no tags or releases.


