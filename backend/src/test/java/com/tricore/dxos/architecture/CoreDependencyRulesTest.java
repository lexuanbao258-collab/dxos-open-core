package com.tricore.dxos.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tricore.dxos.architecture.canary.core.ForbiddenCoreCanary;
import com.tricore.dxos.architecture.canary.infrastructure.ForbiddenAdapterCanary;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #90 / 8A: strict Core-only dependency foundation (DR-01..04, DR-10).
 *
 * Imports compiled production classes, never test fixtures. Application and
 * Gateway rules are intentionally a separate, review-gated increment.
 */
class CoreDependencyRulesTest {
    private static final String ROOT = "com.tricore.dxos";
    private static final String CORE = ROOT + ".core";
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private static ArchRule coreIsJdkOnly(String packageRoot) {
        return noClasses().that().resideInAPackage(packageRoot + "..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("java..", packageRoot + "..");
    }

    @Test
    void coreHasRealProductionClassesAndNoNonJdkDependencies() {
        // Prevent a vacuous PASS caused by an empty/misnamed scan target.
        assertTrue(PRODUCTION.stream().anyMatch(type ->
                type.getPackageName().startsWith(CORE + ".")),
                "No compiled Core production classes were imported");
        coreIsJdkOnly(CORE).check(PRODUCTION);
    }

    @Test
    void coreCapabilitiesDoNotDependOnEachOther() {
        noClasses().that().resideInAPackage(CORE + ".identity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        CORE + ".workflow..", CORE + ".data..").check(PRODUCTION);
        noClasses().that().resideInAPackage(CORE + ".workflow..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        CORE + ".identity..", CORE + ".data..").check(PRODUCTION);
        noClasses().that().resideInAPackage(CORE + ".data..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        CORE + ".identity..", CORE + ".workflow..").check(PRODUCTION);
    }

    @Test
    void negativeCanaryDetectsAForbiddenProviderDependency() {
        // Exact same rule factory as the production check, redirected to a
        // test-only fake Core; the canary is excluded from PRODUCTION.
        String fakeCore = ROOT + ".architecture.canary.core";
        JavaClasses canary = new ClassFileImporter().importClasses(
                ForbiddenCoreCanary.class, ForbiddenAdapterCanary.class);
        AssertionError failure = assertThrows(AssertionError.class,
                () -> coreIsJdkOnly(fakeCore).check(canary));
        assertTrue(failure.getMessage().contains("ForbiddenAdapterCanary"),
                "The negative canary did not report its forbidden dependency");
    }
}
