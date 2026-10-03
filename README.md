# APIXA

## Security Contract Conformance Analyzer for OpenAPI-Based REST APIs

APIXA is a research-oriented security analysis platform for **OpenAPI-based Spring Boot REST APIs**.

The main goal of APIXA is to analyze whether the security requirements declared in an OpenAPI contract are consistent with the security rules actually implemented in the application.

Instead of relying only on runtime API testing, APIXA combines multiple representations of an API:

* OpenAPI security contracts
* Spring application source code
* Endpoint mappings
* Security rules
* Conformance results
* Evidence
* Runtime observations
* Version-to-version impact analysis
* Benchmark evaluation
* Automated reports

The platform is implemented as a set of Spring Boot microservices connected through a central API gateway.

---

# 1. Problem Statement

API security requirements can exist in multiple forms.

For example, an OpenAPI contract may specify:

```text
GET /admin/users
Required role: ADMIN
```

while the actual Spring Security configuration may contain:

```text
/admin/** -> USER
```

The API therefore has a difference between its **declared security contract** and its **implemented security policy**.

APIXA analyzes this difference and produces an evidence-backed conformance result.

Example:

```text
OpenAPI Contract
      |
      v
Expected Security
      |
      v
Endpoint Mapping
      |
      v
Source Analysis
      |
      v
Implemented Security
      |
      v
Conformance Analysis
      |
      v
Evidence
      |
      +------> Runtime Verification
      |
      +------> Version Impact Analysis
      |
      +------> Benchmark
      |
      +------> Report
```

---

# 2. Core Idea

APIXA treats the OpenAPI security requirements as a security contract and compares them with security policies implemented in the Spring application.

The analysis separates the following concepts:

1. **Contract Security**
   Security requirements declared by the OpenAPI specification.

2. **Implemented Security**
   Security rules discovered from the application source code.

3. **Endpoint Mapping**
   Mapping between contract endpoints and source-code endpoints/security rules.

4. **Conformance**
   Comparison between expected and implemented security.

5. **Evidence**
   Traceable information supporting the analysis result.

6. **Runtime Observation**
   Observations obtained by executing requests against a running application.

7. **Impact Analysis**
   Analysis of security-relevant changes between API/application versions.

This separation allows runtime observations to remain observations rather than automatically becoming security verdicts.

---

# 3. Example

Consider the following OpenAPI requirement:

```text
GET /admin/users
Required role: ADMIN
```

Suppose the Spring Security configuration contains:

```text
/admin/** -> USER
```

APIXA can represent the result as:

```text
Expected Security:
ADMIN

Implemented Security:
USER

Result:
MISMATCH
```

The result can be traced through the analysis pipeline:

```text
CONTRACT
   ↓
MAPPING
   ↓
SOURCE
   ↓
SOURCE_SECURITY
   ↓
CONFORMANCE
```

This traceability is one of the central design goals of the platform.

---

# 4. Architecture

APIXA is organized as a collection of Spring Boot services.

```text
                         +----------------+
                         |  APIXA Gateway |
                         |     :8080      |
                         +-------+--------+
                                 |
        +------------------------+------------------------+
        |            |             |          |            |
        v            v             v          v            v
   Project       Contract       Source     Mapping    Conformance
    :8081          :8082         :8083       :8084        :8085
        |            |             |          |            |
        +------------+-------------+----------+------------+
                                 |
                                 v
                            Evidence :8086
                                 |
              +------------------+------------------+
              |                  |                  |
              v                  v                  v
         Runtime :8087       Impact :8088       Benchmark :8089
                                                    |
                                                    v
                                               Report :8090
```

> Port assignments may vary depending on the local configuration. Check the corresponding service configuration before starting the complete system.

---

# 5. Services

| Service                         | Responsibility                                      |
| ------------------------------- | --------------------------------------------------- |
| `apixa-common`                  | Shared models, errors, tracing and common utilities |
| `apixa-gateway`                 | Central API gateway                                 |
| `apixa-project-service`         | Project and API version management                  |
| `apixa-contract-service`        | OpenAPI import and contract extraction              |
| `apixa-source-analysis-service` | Spring source-code and security analysis            |
| `apixa-mapping-service`         | Contract-to-source endpoint/security mapping        |
| `apixa-conformance-service`     | Security conformance analysis                       |
| `apixa-evidence-service`        | Evidence storage and traceability                   |
| `apixa-runtime-service`         | Runtime API verification                            |
| `apixa-impact-service`          | Version-to-version impact analysis                  |
| `apixa-benchmark-service`       | Controlled benchmark execution                      |
| `apixa-report-service`          | JSON, HTML and PDF report generation                |

---

# 6. Analysis Pipeline

The primary APIXA pipeline is:

```text
OpenAPI Specification
        |
        v
Contract Extraction
        |
        v
Source Code Analysis
        |
        v
Endpoint / Security Mapping
        |
        v
Security Conformance
        |
        v
Evidence Collection
        |
        +------------+
        |            |
        v            v
 Runtime         Impact
Verification     Analysis
        |            |
        +------+-----+
               |
               v
           Benchmark
               |
               v
             Report
```

---

# 7. Contract Analysis

APIXA accepts OpenAPI specifications and extracts information such as:

* API endpoints
* HTTP methods
* parameters
* request bodies
* responses
* media types
* security schemes
* security requirements

Example:

```yaml
paths:
  /admin/users:
    get:
      security:
        - bearerAuth: []
```

The contract service normalizes this information into internal representations used by subsequent analysis stages.

---

# 8. Source Analysis

The source-analysis service analyzes Spring Boot application source code.

It can identify information such as:

* Controller endpoints
* HTTP methods
* Request mappings
* Security annotations
* Spring Security configuration
* Authorization rules
* Roles/authorities
* Source locations

The purpose is to derive the application's implemented API and security view.

---

# 9. Endpoint and Security Mapping

The mapping service connects information from the contract and source analysis stages.

For example:

```text
Contract:

GET /admin/users


Source:

@GetMapping("/admin/users")
```

The mapper also considers security rules such as:

```text
/admin/** -> ADMIN
/api/**   -> USER
```

Specific endpoint mappings are preferred over broad wildcard rules when determining the applicable implementation rule.

---

# 10. Security Conformance

The conformance service compares:

```text
Expected Security
        vs
Implemented Security
```

Possible outcomes can include:

```text
MATCH
MISMATCH
UNKNOWN
```

A simplified example:

```text
Contract requirement:
ADMIN

Implemented requirement:
USER

Conformance:
MISMATCH
```

The conformance result is based on the normalized contract and mapped implementation evidence.

---

# 11. Evidence

APIXA maintains evidence associated with the analysis stages.

Example evidence chain:

```text
CONTRACT
   |
   +-- Contract security requirement

MAPPING
   |
   +-- Endpoint/security mapping

SOURCE
   |
   +-- Controller/security implementation

SOURCE_SECURITY
   |
   +-- Spring Security rule

CONFORMANCE
   |
   +-- Comparison result
```

This makes it possible to trace a conformance result back to the information that produced it.

---

# 12. Runtime Verification

Runtime verification is treated as a separate analysis layer.

For example, APIXA may execute:

```text
GET /admin/users
```

and observe:

```text
HTTP 401
HTTP 403
HTTP 200
```

These are recorded as **runtime observations**.

A runtime response is not automatically treated as proof that the static security contract is correct or incorrect.

This distinction helps keep static conformance analysis separate from dynamic execution behavior.

---

# 13. Version Impact Analysis

APIXA also provides a version-to-version analysis layer.

The purpose is to identify security-relevant changes between API/application versions.

Conceptually:

```text
Version N
   |
   v
Analysis
   |
   v
Version N+1
   |
   v
Analysis
   |
   v
Compare
   |
   v
Security Impact
```

Potential changes include:

* Endpoint additions
* Endpoint removals
* Security requirement changes
* Authorization rule changes
* Mapping changes
* Conformance changes

---

# 14. Benchmark

APIXA includes a controlled benchmark service for evaluating the analysis pipeline.

Benchmark fixtures can be found under:

```text
samples/benchmark/
```

The benchmark can be used to evaluate predefined cases and compare expected and observed analysis outcomes.

Example benchmark workflow:

```text
Benchmark Cases
      |
      v
Fixture Loading
      |
      v
Pipeline Execution
      |
      v
Result Collection
      |
      v
Metrics
      |
      v
Benchmark Report
```

---

# 15. Reports

The report service provides structured analysis output.

Supported report representations include:

* JSON
* HTML
* PDF

The report layer is designed to present the results produced by the analysis pipeline without changing the underlying conformance decision.

---

# 16. Repository Structure

```text
APIXA/
│
├── apixa-common/
├── apixa-gateway/
├── apixa-project-service/
├── apixa-contract-service/
├── apixa-source-analysis-service/
├── apixa-mapping-service/
├── apixa-conformance-service/
├── apixa-evidence-service/
├── apixa-runtime-service/
├── apixa-impact-service/
├── apixa-benchmark-service/
├── apixa-report-service/
│
├── samples/
│   ├── benchmark/
│   ├── conformance/
│   ├── evidence/
│   ├── impact/
│   ├── mapping/
│   ├── openapi/
│   ├── report/
│   ├── runtime-sample/
│   └── spring-sample/
│
├── docs/
│   ├── existing-backend-audit.md
│   └── implementation-progress.md
│
├── pom.xml
├── README.md
└── .gitignore
```

---

# 17. Sample Applications

The repository contains sample applications used to exercise different parts of the pipeline.

### Spring Sample

```text
samples/spring-sample/
```

Contains representative Spring controllers and security configurations.

### Runtime Sample

```text
samples/runtime-sample/
```

Provides a small Spring application that can be used for runtime verification.

### OpenAPI Samples

```text
samples/openapi/
```

Contains valid and invalid OpenAPI documents used to test contract extraction and validation behavior.

---

# 18. Getting Started

## Requirements

Recommended environment:

* Java 21
* Maven
* Git
* Spring Boot compatible environment
* Windows/Linux/macOS

Clone the repository:

```bash
git clone https://github.com/shivsharanprakash/APIXA_Backend.git
cd APIXA_Backend
```

Build the project:

```bash
mvn clean install
```

Individual services can then be started according to their configured ports.

For development, it is recommended to start the services in pipeline order and verify each service before proceeding to the next stage.

---

# 19. Example API Flow

A simplified analysis flow is:

```text
1. Create Project
        ↓
2. Import OpenAPI Contract
        ↓
3. Analyze Source Code
        ↓
4. Map Contract to Source
        ↓
5. Run Security Conformance
        ↓
6. Store Evidence
        ↓
7. Verify Runtime Behavior
        ↓
8. Analyze Version Impact
        ↓
9. Run Benchmark
        ↓
10. Generate Report
```

---

# 20. Example Requests

### Project

```http
POST /api/projects
```

### Contract Import

```http
POST /api/contracts/import
```

### Source Analysis

```http
POST /api/source-analysis/analyze
```

### Mapping

```http
POST /api/mapping
```

### Conformance

```http
POST /api/conformance
```

### Evidence

```http
POST /api/evidence
```

### Runtime Verification

```http
POST /api/runtime/verify
```

### Impact Analysis

```http
POST /api/impact
```

### Benchmark

```http
POST /api/benchmark/run
```

### Report

```http
POST /api/report
```

> Exact endpoint paths and request structures should be checked against the corresponding controller implementations and sample request files in `samples/`.

---

# 21. Reproducibility

The repository includes sample inputs and request files so that the individual analysis stages can be reproduced.

Important directories include:

```text
samples/openapi/
samples/spring-sample/
samples/runtime-sample/
samples/mapping/
samples/conformance/
samples/evidence/
samples/impact/
samples/benchmark/
samples/report/
```

The `docs/` directory contains additional implementation and backend audit documentation.

---

# 22. Research Positioning

APIXA does not claim that OpenAPI analysis, source-code analysis, runtime testing, endpoint mapping, benchmarking, or reporting are individually new techniques.

Instead, the project investigates an integrated workflow that connects these representations for **security contract conformance analysis**.

The central research question is:

> How can security requirements declared in an OpenAPI contract be systematically compared with security behavior implemented in a Spring-based REST application while maintaining traceable evidence across contract, source, mapping, conformance, runtime, and version-impact layers?

The architecture therefore emphasizes:

```text
Contract
   ↕
Implementation
   ↕
Mapping
   ↕
Conformance
   ↕
Evidence
   ↕
Runtime
   ↕
Version Impact
```

---

# 23. Current Scope

APIXA currently focuses on:

* OpenAPI-based REST APIs
* Spring Boot applications
* Spring MVC endpoint discovery
* Spring Security-related rules
* Contract-to-source mapping
* Security conformance
* Evidence tracing
* Runtime verification
* Version impact analysis
* Controlled benchmark execution
* Automated reporting

The analysis should be understood within these supported technologies and representations.

---

# 24. Project Status

APIXA backend implementation includes:

* Microservice-based backend architecture
* OpenAPI contract extraction
* Spring source analysis
* Endpoint/security mapping
* Security conformance analysis
* Evidence persistence
* Runtime verification
* Impact analysis
* Benchmark infrastructure
* Report generation
* API gateway
* Reproducible sample fixtures

The implementation and research documentation are maintained in the repository.

---

# 25. License

Add the project's license information here once the final repository licensing decision has been made.
