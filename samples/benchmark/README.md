# samples/benchmark — Step 15 ground truth

`step15-benchmark-cases.json` is the **controlled ground-truth suite** for the APIXA benchmark
(`apixa-benchmark-service`, port 8089). It is source-controlled, read-only for the benchmark, and is
never rewritten by a run.

## Case shape

```json
{
  "caseId": "CONF-002",
  "group": "CONFORMANCE",
  "description": "ADMIN vs USER mismatch",
  "input": { "...service request shape..." },
  "expected": { "mappingStatus": "MATCHED", "conformanceStatus": "MISMATCH" }
}
```

* `input` is exactly the request body the evaluated APIXA endpoint accepts
  (`/api/conformance/analyze`, `/api/mappings/map`, `/api/impact/analyze`, `/api/runtime/verify`).
* `expected` is authored independently of APIXA output. The runner never derives an expectation from
  the system under test.
* `baseCaseId` is used by `MUTATION` cases: the mutation is applied to a **copy** of that case's input
  and scored against the effect the mutation case declares.

## Groups

| Group | Cases | Evaluates |
|---|---|---|
| CONFORMANCE | 13 | Step 11 `POST /api/conformance/analyze` (incl. `CTRL-001` negative control) |
| MAPPING | 7 | Step 10 `POST /api/mappings/map` |
| IMPACT | 10 | Step 14 `POST /api/impact/analyze` |
| RUNTIME | 7 | Step 13 `POST /api/runtime/verify` against `samples/runtime-sample` |
| MUTATION | 8 | controlled mutations of a base case (in-memory only) |

## Negative control

`CTRL-001` deliberately declares the **wrong** ground truth for a case whose correct answer is MATCH.
It must always FAIL. If it ever passes, the harness is broken. Its failure is expected and is not an
APIXA defect.

## Runtime cases

`RUN-001..RUN-005` need the local Step 13 sample target on `http://localhost:9090`
(`cd samples/runtime-sample && mvn -B spring-boot:run`). Without it they are reported as `SKIPPED`,
never as a pass. `RUN-006` (unused local port) and `RUN-007` (bounded timeout) need no target.