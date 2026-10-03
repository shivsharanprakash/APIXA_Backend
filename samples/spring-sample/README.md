# samples/spring-sample — source analysis input (Steps 8 + 9)

Deliberately **not** a buildable Maven module: it contains no `pom.xml` and is not listed in the root
reactor. It exists only as *source input* for APIXA source analysis
(`apixa-source-analysis-service`, `SpringSourceAnalyzer` on Spoon in `noClasspath` mode), so it is
never compiled by the reactor and never affects any other module.

Point the analyzer at this directory:

```
POST http://localhost:8083/api/source/analyze/endpoints
POST http://localhost:8083/api/source/analyze/security
{ "projectPath": "C:\\Codes\\APIXA\\security-contract-analyzer\\samples\\spring-sample" }
```

## Files

| File | Purpose |
|------|---------|
| `src/main/java/com/example/users/UserController.java` | class-level + method-level paths, path variable, query parameter |
| `src/main/java/com/example/users/UserService.java` | non-controller (`@Service`): must contribute no endpoint |
| `src/main/java/com/example/admin/AdminController.java` | `@RequestMapping(value = ..., method = ...)`, `method = {GET, HEAD}`, `@PreAuthorize` that Step 8 must ignore |
| `src/main/java/com/example/alias/AliasController.java` | multi-path class annotation × multi-path method annotation |
| `src/main/java/com/example/legacy/LegacyUserController.java` | no class mapping, deliberate duplicate `GET /api/users`, method-less `@RequestMapping` |
| | `src/main/java/com/example/security/SecurityConfig.java` | Step 9 `SecurityFilterChain`: six `requestMatchers` rules + `anyRequest()`, one per line for readable line evidence |
| | `src/main/java/com/example/secured/SecuredMethods.java` | Step 9 method-level security (`@PreAuthorize`/`@Secured`/`@RolesAllowed`, incl. a complex and a non-literal expression). No mapping annotation, so it contributes **no** endpoint |

## Expected result

`analysisType = ENDPOINTS`, `analyzedFiles = 7`, `endpointCount = 14` (the two Step 9 sample files
declare no endpoint), ordered by path, HTTP method,
source file, source line:

| # | method | path | controller | Java method | file | line |
|---|--------|------|-----------|-------------|------|------|
| 1 | GET | `/admin/health` | `AdminController` | `health` | `AdminController.java` | 24 |
| 2 | HEAD | `/admin/health` | `AdminController` | `health` | `AdminController.java` | 24 |
| 3 | GET | `/admin/users` | `AdminController` | `users` | `AdminController.java` | 19 |
| 4 | GET | `/api/users` | `LegacyUserController` | `listUsersLegacy` | `LegacyUserController.java` | 12 |
| 5 | GET | `/api/users` | `UserController` | `listUsers` | `UserController.java` | 18 |
| 6 | POST | `/api/users` | `UserController` | `createUser` | `UserController.java` | 28 |
| 7 | DELETE | `/api/users/{id}` | `UserController` | `deleteUser` | `UserController.java` | 38 |
| 8 | GET | `/api/users/{id}` | `UserController` | `getUserById` | `UserController.java` | 23 |
| 9 | PUT | `/api/users/{id}` | `UserController` | `updateUser` | `UserController.java` | 33 |
| 10 | GET | `/api/v1/alias/items` | `AliasController` | `list` | `AliasController.java` | 12 |
| 11 | GET | `/api/v1/alias/things` | `AliasController` | `list` | `AliasController.java` | 12 |
| 12 | GET | `/api/v2/alias/items` | `AliasController` | `list` | `AliasController.java` | 12 |
| 13 | GET | `/api/v2/alias/things` | `AliasController` | `list` | `AliasController.java` | 12 |
| 14 | ANY | `/legacy/ping` | `LegacyUserController` | `ping` | `LegacyUserController.java` | 18 |

`sourceLine` is the line of the mapping annotation itself (identical to the method line for the
one-line declarations above). Rows 4 and 5 are the **deliberate duplicate** `GET /api/users`: both
source declarations are kept, they are never collapsed. Row 14 is `ANY` because
`@RequestMapping(path = "/legacy/ping")` declares no `method` attribute, which Spring maps for all
standard HTTP methods — one source declaration stays one record.

No endpoint carries `implementedSecurity`, `evidence` or `securityRules`: Step 8 analysis does not
analyse security (that is Step 9), and `@PreAuthorize` on `AdminController#users` has no effect here.

## Expected result — Step 9 (security)

`analysisType = SECURITY`, `status = COMPLETED`, `analyzedFiles = 7`, `securityRuleCount = 14`,
sorted by file, line, scope, rule type, pathPattern, operator:

| file | line | scope | type | operator | matcher | roles / authorities | notes |
|------|------|-------|------|----------|---------|---------------------|-------|
| `AdminController.java` | 18 | METHOD | ROLE | HAS_ANY_ROLE | — | ADMIN, AUDITOR | `@PreAuthorize` |
| `SecuredMethods.java` | 16 | METHOD | ROLE | HAS_ROLE | — | ADMIN | `@PreAuthorize` |
| `SecuredMethods.java` | 21 | METHOD | AUTHORITY | HAS_AUTHORITY | — | USER_READ | `@PreAuthorize` |
| `SecuredMethods.java` | 26 | METHOD | ROLE | SECURED | — | ROLE_ADMIN | `ROLE_` kept as declared |
| `SecuredMethods.java` | 31 | METHOD | ROLE | ROLES_ALLOWED | — | ADMIN | |
| `SecuredMethods.java` | 36 | METHOD | ROLE | HAS_ROLE | — | ADMIN | `complex=true`, full expression preserved (`... and #id == authentication.name`) — **not** flattened to ADMIN |
| `SecuredMethods.java` | 41 | METHOD | ROLE | HAS_ROLE | — | — | `unresolved=true`: `hasRole(ADMIN_ROLE)` is not a literal, nothing is guessed |
| `SecuredMethods.java` | 46 | METHOD | PERMIT_ALL | PERMIT_ALL | — | — | `@PreAuthorize("permitAll()")` |
| `SecurityConfig.java` | 19 | SECURITY_CONFIGURATION | ROLE | HAS_ROLE | `/admin/**` | ADMIN | |
| `SecurityConfig.java` | 20 | SECURITY_CONFIGURATION | ROLE | HAS_ANY_ROLE | `/manager/**` | ADMIN, MANAGER | |
| `SecurityConfig.java` | 21 | SECURITY_CONFIGURATION | AUTHORITY | HAS_AUTHORITY | `/api/users/**` | USER_READ | |
| `SecurityConfig.java` | 22 | SECURITY_CONFIGURATION | AUTHORITY | HAS_ANY_AUTHORITY | `/reports/**` | REPORT_READ, REPORT_WRITE | |
| `SecurityConfig.java` | 23 | SECURITY_CONFIGURATION | PERMIT_ALL | PERMIT_ALL | `/public/**` | — | |
| `SecurityConfig.java` | 24 | SECURITY_CONFIGURATION | AUTHENTICATED | AUTHENTICATED | `**` | — | `catchAll=true` (`anyRequest()`), no path invented |

The response contains **no** endpoint data and no comparison with any OpenAPI document: security
rules are discovered only from the declarations above, never mapped to HTTP paths (Step 10).
