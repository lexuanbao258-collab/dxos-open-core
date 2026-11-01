# Issue #90 — PR 8A: ArchUnit foundation

**Status:** implementation candidate for independent review; not architecture acceptance.
**Base:** `main@2a94a46579c4d3d9999b0da831d9c3bb505cc3e2`. **Related:** Issue #90 Step 5/8; Issue #87 selected review candidate is PR #95, not this branch.
**Owner:** Bảo. **Independent reviewer:** Yến; Ánh reviews subsequent Application/Gateway seam rules.

## Scope

- Pin `com.tngtech.archunit:archunit-junit5:1.5.1` as **testImplementation only**, with no production/runtime provider dependency. Maven Central: https://central.sonatype.com/artifact/com.tngtech.archunit/archunit-junit5/1.5.1
- Scan compiled **production** `com.tricore.dxos.core..` classes using ArchUnit `ClassFileImporter` excluding test classes.
- **P0 fail-on-violation:** all Core dependencies stay within JDK `java..` and Core-owned Java classes; no Application, Gateway, Infrastructure, legacy Request, Spring, Jakarta, JDBC/provider SDK or unrelated third-party imports.
- Enforce no cross-capability dependencies between `core.identity`, `core.workflow`, `core.data`. `WorkflowPersistencePort` remains Workflow-owned.
- Guard against vacuous empty Core scan; use test-only intentionally forbidden Core-to-Adapter dependency to assert the **same rule factory** fails.
- No new production Java, migrations, server configuration, BFF or API changes. No broad exceptions.

## Execution instructions (not yet run by this PR authoring environment)

From `backend/` with JDK 21 and network access to Maven Central:

```powershell
.\gradlew.bat test --tests '*CoreDependencyRulesTest' --console=plain --no-daemon
.\gradlew.bat clean test build --continue --console=plain --no-daemon
```

Record exact PR HEAD SHA; JDK/Gradle versions; JUnit XML counts and skips; dependency resolution and CI URL. **No commands executed against PostgreSQL, Keycloak, or shared infrastructure for 8A.**

## Review and acceptance

- **Gate 1:** dependency resolves, all three architecture tests compile and PASS; the negative canary proves fail-detection; full clean regression/build PASS at **this PR's SHA**.
- **Gate 2:** Yến independently reviews classpath selection, rules, no-overbroad-waiver, security implications; capture signed verdict.
- **Gate 3:** compare rules with DR-01..04/10 and exact `main`/selected composite package inventories; add Application/Gateway no-new-P0 rules only in a separately approved bounded PR after analyzing `#74` gateway → infrastructure seam.
- **Forbidden claim:** static dependency test PASS does **not** verify tenant ACL, OIDC, 404, PostgreSQL transaction, HTTP exposure, Flowable/APISIX/MinIO, or release readiness.
- **Merge:** no merge without successful CI evidence, exact SHA independent review and explicit authorization.
