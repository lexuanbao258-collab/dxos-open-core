<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# Issue #90 — PR 8A: ArchUnit foundation

**Status:** implementation candidate for independent review; not architecture acceptance.
**Base:** `main@af21ae5ff59d8eb9b3c0fef0bc8a78295aeb588d`. **Related:** Issue #90 Step 5/8; Issue #87 selected review candidate is PR #95, not this branch.
**Owner:** Bảo. **Independent reviewer:** Yến; Ánh reviews subsequent Application/Gateway seam rules.

## Scope

- Pin `com.tngtech.archunit:archunit-junit5:1.5.1` as **testImplementation only**, with no production/runtime provider dependency. Maven Central: https://central.sonatype.com/artifact/com.tngtech.archunit/archunit-junit5/1.5.1
- Import compiled production `com.tricore.dxos..` with `DO_NOT_INCLUDE_TESTS`; positive rules select only `core..` origins. Assert that `AuthenticatedPrincipal`, `WorkflowRuntime`, `WorkflowPersistencePort` and `ObjectStoragePort` are present, and that the test class, forbidden canaries and allowed canary are absent from that production import.
- Record imported compiled class counts for each of Identity, Workflow and Data (`CORE_PRODUCTION_CLASS_COUNT` in JUnit stdout), with a nonzero assertion per capability. Counts include imported nested/synthetic/package-info classes, not just `.java` files; the representative assertions are independent of totals.
- **P0 fail-on-violation:** allow `java..` except `java.sql..`, plus Core-owned classes. The separate capability rules still forbid Identity/Workflow/Data coupling. No Application, Gateway, Infrastructure, legacy Request, Spring, Jakarta, JDBC/provider SDK or unrelated third-party dependencies.
- Enforce no cross-capability dependencies between `core.identity`, `core.workflow`, `core.data`. `WorkflowPersistencePort` remains Workflow-owned.
- Use the **same rule factory** for positive production rules, an allowed Java/same-Core fixture and forbidden test-only dependencies. The negative test must catch the rule's AssertionError and assert every expected forbidden target appears; catching any unrelated error is not PASS.
- No new production Java, migrations, server configuration, BFF or API changes. No broad exceptions.

## Rule rationale and executable examples (R02)

This is an intentional **strict package allowlist**, narrower than all Java-platform APIs. It preserves the existing 8A `java..`/Core boundary; it does not classify every `javax..` class as third-party. DR-02/03 explicitly prohibit JDBC, so accepting all of `java..` was insufficient: `java.sql..` is now denied too. There is no production import change or new architecture decision.

| Dependency example | Classification | 8A result / reason |
|---|---|---|
| `java.util.List`, `java.time.Instant` | JDK standard library | Allowed; positive canary includes both |
| Same Core package root | Core-owned types | Allowed by the foundation rule; Identity/Workflow/Data cross-capability dependencies are separately forbidden |
| `java.sql.Connection` | JDK `java.sql` module, JDBC API | Forbidden by DR-02/03 despite being JDK; negative canary must report it |
| `javax.crypto.Cipher` | JDK `java.base` module, not an external library | Still outside the existing strict `java..` allowlist; negative canary records this deliberate narrower policy, not a vendor classification |
| `jakarta.validation.Valid` | External Jakarta Validation API, not JDK | Forbidden; validation framework belongs outside generic Core |
| `org.slf4j.Logger` | External library | Forbidden under this strict gate even though it is not a provider SDK |
| Test-only `ForbiddenAdapterCanary` | Fake Infrastructure dependency | Forbidden by DR-01/02/03/10 foundation; absent from production scan |

The negative test asserts the real Java 21 module identities for Connection and Cipher, then verifies the same rule reports JDBC, JDK-javax, Jakarta, external library and Infrastructure targets. The allowed canary is explicitly imported and nonempty before the same rule is checked.

DR-04 is preserved by retaining WorkflowPersistencePort in Workflow and asserting its production import. No shared Core contracts or blanket `javax..` exception is introduced. Any future allowance for another Java-platform namespace or shared module contract requires an explicit reviewed rule change; no silent package waiver. This gate checks class dependencies, not reflective/runtime access or provider semantics.

## Execution instructions

From `backend/` with JDK 21 and network access to Maven Central:

```powershell
.\gradlew.bat test --tests '*CoreDependencyRulesTest' --rerun-tasks --console=plain --no-daemon
.\gradlew.bat test --tests '*CoreDependencyRulesTest.negativeCanaryDetectsAForbiddenProviderDependency' --rerun-tasks --console=plain --no-daemon
.\gradlew.bat clean test build --continue --console=plain --no-daemon
```

Record base SHA plus patch hash until a new commit is authorized; do not attribute an uncommitted correction's PASS to the baseline alone. Capture JDK/Gradle versions, OS, JUnit counts/skips, capability counts, negative-test assertions, dependency resolution and CI URL or NOT RUN. **No PostgreSQL, Keycloak or shared infrastructure execution is authorized by 8A.**

## Review and acceptance

- **Gate 1:** dependency resolves, all four architecture tests compile and PASS; the negative canary proves fail-detection; full clean regression/build PASS at the exact reviewed artifact. Report baseline fixture failures explicitly instead of importing another PR or weakening assertions to obtain green results.
- **Gate 2:** Yến independently reviews classpath selection, rules, no-overbroad-waiver, security implications; capture signed verdict.
- **Gate 3:** compare rules with DR-01..04/10 and exact `main`/selected composite package inventories; add Application/Gateway no-new-P0 rules only in a separately approved bounded PR after analyzing `#74` gateway → infrastructure seam.
- **Forbidden claim:** static dependency test PASS does **not** verify tenant ACL, OIDC, 404, PostgreSQL transaction, HTTP exposure, Flowable/APISIX/MinIO, or release readiness.
- **Merge:** no merge without successful CI evidence, exact SHA independent review and explicit authorization.
