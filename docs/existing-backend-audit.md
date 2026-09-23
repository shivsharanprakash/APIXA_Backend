# APIXA — Existing Backend Audit

## 1. Audit method

- Walked the full workspace tree of `C:\Codes\APIXA`.
- Read `pom.xml`, `application.properties`, the sole application class and the sole test.
- Verified the Maven wrapper works (`mvnw -v` → Apache Maven 3.9.16, Java 21.0.1).

## 2. Current architecture

A **single** Spring Boot application scaffold — no microservices, no gateway, no controllers.

```text
C:\Codes\APIXA
└── security-contract-analyzer/          (single Spring Boot app, not a multi-module build)
    ├── pom.xml                          (Spring Boot 4.1.1, Java 21, SQLite)
    ├── mvnw / mvnw.cmd                  (Maven 3.9.16 wrapper)
    ├── data/security_analyzer.db        (existing SQLite file)
    ├── src/main/java/...                (1 class: @SpringBootApplication)
    └── src/test/java/...                (1 test: contextLoads)
```

## 3. Existing services

| Target service  | Found component |
|-----------------|-----------------|
| Gateway         | none |
| Project         | none (empty `project/` package dirs only) |
| Contract        | none (empty `contract/` package dirs only) |
| Source Analysis | none (empty `source/`, `security/`, `analysis/` package dirs only) |
| Mapping         | none (empty `mapping/` package dirs only) |
| Conformance     | none |
| Runtime         | none (empty `verification/` package dirs only) |
| Evidence        | none (empty `evidence/` package dirs only) |
| Impact          | none (empty `impact/` package dirs only) |
| Report          | none (empty `report/` package dirs only) |
| Benchmark       | none |

The `analysis`, `contract`, `evidence`, `impact`, `mapping`, `project`, `report`,
`security`, `source`, `verification` folders under
`src/main/java/com/securityanalyzer/security_contract_analyzer/` contain **zero `.java` files** —
they are package placeholders only.

## 4. Existing endpoints

None. There are no controllers.

## 5. Existing entities

None. There are no JPA entities.

## 6. Existing databases

SQLite via `jdbc:sqlite:./data/security_analyzer.db` with the Hibernate community
`SQLiteDialect`, `ddl-auto=update`. The file `data/security_analyzer.db` exists on disk.

## 7. Existing analyzer functionality

None. No Spoon/SootUp/Z3/Javaparser or OpenAPI parser dependency is present; no analyzer code exists.

## 8. Existing tests

One file: `SecurityContractAnalyzerApplicationTests.contextLoads`.

## 9. Existing Docker setup

None.

## 10. Existing configuration

- `spring.application.name=security-contract-analyzer`
- SQLite datasource `./data/security_analyzer.db`, driver `org.sqlite.JDBC`
- `spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect`
- `ddl-auto=update`, show-sql enabled
- No secrets present.

## 11. Working functionality

- Maven wrapper builds the scaffold (`mvn package` produced
  `target/security-contract-analyzer-0.0.1-SNAPSHOT.jar` previously).
- Spring context loads (context test passed in a previous build; surefire report present).
- SQLite persistence path configured.

## 12. Incomplete functionality

Essentially everything — the project is a Spring Boot skeleton with empty package
directories and a placeholder database.

## 13. Broken functionality

None observed: the scaffold compiles and its single test passes.

## 14. Missing functionality

All target services (gateway, project, contract, source analysis, mapping,
conformance, runtime, evidence, impact, report, benchmark), all REST endpoints,
all analyzers (OpenAPI parsing, Java/Spring Security source analysis), correlation/trace
IDs, common error model, health endpoints, Docker orchestration, and docs.

## 15. Service status determination (from the codebase)

```text
Gateway                  MISSING
Project Service          MISSING      (only empty package placeholders exist)
Contract Service         MISSING
Source Analysis          MISSING
Mapping Service          MISSING
Conformance Service      MISSING
Evidence Service         MISSING
Runtime Service          MISSING
Impact Service           MISSING
Report Service           MISSING
Benchmark Service        MISSING
```

## 16. Recommended implementation order

Adopted from the task's stage plan; unchanged because nothing exists to reuse
beyond the scaffold:

1. Re-structure the existing single project into a Maven multi-module reactor —
   **preserving** the existing artifact as `apixa-project-service` (same base
   package, same SQLite file).
2. Add shared foundations (parent BOM usage, common lib: trace IDs, error model,
   health) and the gateway.
3. Contract service with a real OpenAPI parser.
4. Source analysis service with a real Java source analyzer (Spoon).
5. Mapping service.
6. Conformance service (first major research engine).
7. Evidence service.
8. Runtime service (targeted dynamic verification).
9. Impact service.
10. Benchmark service.
11. Report service.
12. Docker Compose + end-to-end demonstration + docs.