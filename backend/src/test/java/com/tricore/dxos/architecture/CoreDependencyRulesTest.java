package com.tricore.dxos.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tricore.dxos.architecture.canary.core.ForbiddenCoreCanary;
import com.tricore.dxos.architecture.canary.infrastructure.ForbiddenAdapterCanary;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import com.tricore.dxos.core.data.ObjectStoragePort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static ArchRule coreUsesApprovedDependencies(String packageRoot) {
        // DR-02/03 prohibit JDBC even though java.sql is part of the JDK.
        return noClasses().that().resideInAPackage(packageRoot + "..")
                .should().dependOnClassesThat(not(resideInAnyPackage("java..", packageRoot + ".."))
                        .or(resideInAnyPackage("java.sql..")));
    }

    @Test
    void coreHasRepresentativeProductionClassesAndOnlyApprovedDependencies() {
        for (Class<?> representative : List.of(AuthenticatedPrincipal.class, WorkflowRuntime.class,
                WorkflowPersistencePort.class, ObjectStoragePort.class)) {
            assertTrue(PRODUCTION.contain(representative),
                    "Production scan missed " + representative.getName());
        }
        for (Class<?> testClass : List.of(CoreDependencyRulesTest.class, ForbiddenCoreCanary.class,
                ForbiddenCoreCanary.AllowedCoreCanary.class, ForbiddenAdapterCanary.class)) {
            assertFalse(PRODUCTION.contain(testClass),
                    "Production scan included test fixture " + testClass.getName());
        }
        for (String capability : List.of("identity", "workflow", "data")) {
            String packageName = CORE + "." + capability;
            long count = PRODUCTION.stream().filter(type -> type.getPackageName().equals(packageName)
                    || type.getPackageName().startsWith(packageName + ".")).count();
            assertTrue(count > 0, "No production classes imported for " + capability);
            System.out.printf("CORE_PRODUCTION_CLASS_COUNT %s=%d%n", capability, count);
        }
        coreUsesApprovedDependencies(CORE).check(PRODUCTION);
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
                () -> coreUsesApprovedDependencies(fakeCore).check(canary));
        assertEquals("java.sql", java.sql.Connection.class.getModule().getName());
        assertEquals("java.base", javax.crypto.Cipher.class.getModule().getName());
        for (String forbidden : List.of("ForbiddenAdapterCanary", "java.sql.Connection", "javax.crypto.Cipher",
                "jakarta.validation.Valid", "org.slf4j.Logger")) {
            assertTrue(failure.getMessage().contains(forbidden),
                    "The negative canary did not report " + forbidden);
        }
        System.out.println("NEGATIVE_CANARY_DETECTED infrastructure, JDBC, JDK javax, Jakarta, external library");
    }

    @Test
    void approvedJavaAndSameCoreDependenciesPassTheSameRule() {
        JavaClasses allowed = new ClassFileImporter().importClasses(ForbiddenCoreCanary.AllowedCoreCanary.class);
        assertTrue(allowed.contain(ForbiddenCoreCanary.AllowedCoreCanary.class));
        coreUsesApprovedDependencies(ROOT + ".architecture.canary.core").check(allowed);
    }
}
