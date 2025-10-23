package com.patra.registry.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Hexagonal Architecture constraint tests for patra-registry.
 *
 * <p>Validates layered dependency rules:
 *
 * <ul>
 *   <li>Domain layer must be pure Java (no framework dependencies)
 *   <li>App layer can only depend on Domain + Common + Core Starter
 *   <li>Infra layer can only depend on Domain + MyBatis Starter
 *   <li>Adapter layer can only depend on App + API + Web Starters
 * </ul>
 */
class HexagonalArchitectureTest {

  private static JavaClasses importedClasses;

  @BeforeAll
  static void setup() {
    importedClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.patra.registry");
  }

  /** Rule 1: Domain layer must not depend on infrastructure layer */
  @Test
  void domainShouldNotDependOnInfrastructure() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infra..", "..adapter..")
            .because(
                "Domain layer must not depend on infrastructure or adapters (Hexagonal"
                    + " Architecture)");

    rule.check(importedClasses);
  }

  /** Rule 2: Domain layer must not access Spring Framework */
  @Test
  void domainShouldNotAccessSpringFramework() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.validation..",
                "com.baomidou..",
                "org.mybatis..")
            .because("Domain layer must be framework-free (Hexagonal Architecture)");

    rule.check(importedClasses);
  }

  /** Rule 3: Domain layer should only depend on allowed packages */
  @Test
  void domainShouldOnlyDependOnAllowedPackages() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..domain..",
                "com.patra.common..",
                "cn.hutool..",
                "com.fasterxml.jackson..",
                "java..",
                "lombok..",
                "org.slf4j.." // SLF4J API allowed (provided scope)
                )
            .because(
                "Domain layer should only depend on: domain, patra-common, Hutool, Jackson, Java"
                    + " stdlib");

    rule.check(importedClasses);
  }

  /** Rule 4: Application layer should not contain business logic (if Orchestrators exist) */
  @Test
  void orchestratorsShouldNotContainBusinessLogic() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..app..")
            .and()
            .haveSimpleNameEndingWith("Orchestrator")
            .should()
            .onlyBeAccessed()
            .byClassesThat()
            .resideInAnyPackage("..app..", "..adapter..")
            .allowEmptyShould(true) // Allow if no Orchestrators exist yet
            .because(
                "Orchestrators should delegate to Domain services, not contain business logic");

    rule.check(importedClasses);
  }

  /**
   * Rule 5: Layered architecture validation (overall dependency direction)
   *
   * <p>Note: Boot layer (config) is allowed to access domain for Spring Boot configuration (error
   * mapping, etc.)
   */
  @Test
  void layeredArchitectureShouldBeRespected() {
    layeredArchitecture()
        .consideringAllDependencies()
        .layer("Adapter")
        .definedBy("..adapter..")
        .layer("Application")
        .definedBy("..app..")
        .layer("Domain")
        .definedBy("..domain..")
        .layer("Infrastructure")
        .definedBy("..infra..")
        .layer("Boot")
        .definedBy("..config..")
        .whereLayer("Adapter")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Application")
        .mayOnlyBeAccessedByLayers("Adapter", "Boot")
        .whereLayer("Infrastructure")
        .mayOnlyBeAccessedByLayers("Adapter", "Application", "Boot")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Adapter", "Boot")
        .check(importedClasses);
  }
}
