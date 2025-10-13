package com.patra.ingest.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests to enforce hexagonal architecture and DDD patterns for patra-ingest.
 *
 * <p>These tests validate:
 *
 * <ul>
 *   <li>Domain layer has NO Spring/framework dependencies (Pure Java only)
 *   <li>Dependency direction: Adapter → App → Domain ← Infra
 *   <li>Naming conventions (*Orchestrator, *Port, *RepositoryImpl, *DO)
 *   <li>Package structure and layer boundaries
 *   <li>No circular dependencies between packages
 * </ul>
 *
 * <p>Reference: See .claude/AGENTS-architecture.md for architecture rules
 */
@DisplayName("Hexagonal Architecture Rules for patra-ingest")
class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    // Import all classes from the patra-ingest module (excluding test classes)
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.patra.ingest");
  }

  // ============================================================================
  // RULE 1: Domain Layer Must Be Pure Java (NO Framework Dependencies)
  // ============================================================================

  @Test
  @DisplayName("Domain layer must NOT depend on Spring Framework")
  void domainShouldNotDependOnSpring() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .because("Domain layer must be pure Java with no framework dependencies")
        .check(classes);
  }

  @Test
  @DisplayName("Domain layer must NOT depend on MyBatis-Plus")
  void domainShouldNotDependOnMyBatisPlus() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.baomidou..")
        .because("Domain layer must not depend on persistence framework")
        .check(classes);
  }

  @Test
  @DisplayName("Domain layer must NOT use Spring annotations")
  void domainShouldNotUseSpringAnnotations() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .beAnnotatedWith("org.springframework.stereotype.Service")
        .orShould()
        .beAnnotatedWith("org.springframework.stereotype.Component")
        .orShould()
        .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
        .because("Domain layer must not use Spring annotations")
        .check(classes);
  }

  // ============================================================================
  // RULE 2: Layer Dependency Direction
  // ============================================================================

  @Test
  @DisplayName("Enforce layered hexagonal architecture")
  void shouldFollowHexagonalArchitecture() {
    layeredArchitecture()
        .consideringAllDependencies()
        // Define layers
        .layer("Adapter")
        .definedBy("..adapter..")
        .layer("Application")
        .definedBy("..app..")
        .layer("Domain")
        .definedBy("..domain..")
        .layer("Infrastructure")
        .definedBy("..infra..")
        // Define allowed dependencies (outer → inner)
        .whereLayer("Adapter")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Application")
        .mayOnlyBeAccessedByLayers("Adapter")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Infrastructure")
        .whereLayer("Infrastructure")
        .mayNotBeAccessedByAnyLayer()
        .because("Hexagonal architecture must follow strict dependency rules")
        .check(classes);
  }

  @Test
  @DisplayName("Infrastructure layer must NOT be accessed by Application layer")
  void applicationShouldNotDependOnInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("..app..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..infra..")
        .because("Application should depend on Domain ports, not Infrastructure implementations")
        .check(classes);
  }

  @Test
  @DisplayName("Domain layer must NOT depend on Application layer")
  void domainShouldNotDependOnApplication() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..app..")
        .because("Domain is the core and should not know about outer layers")
        .check(classes);
  }

  // ============================================================================
  // RULE 3: Naming Conventions
  // ============================================================================

  @Test
  @DisplayName("Orchestrators must reside in app layer and end with 'Orchestrator'")
  void orchestratorsMustFollowNamingConvention() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Orchestrator")
        .should()
        .resideInAPackage("..app..")
        .because("Orchestrators coordinate use cases and belong in the Application layer")
        .check(classes);
  }

  @Test
  @DisplayName("Ports (interfaces) must reside in domain layer and end with 'Port'")
  void portsMustResideInDomain() {
    classes()
        .that()
        .areInterfaces()
        .and()
        .haveSimpleNameEndingWith("Port")
        .should()
        .resideInAPackage("..domain..")
        .because("Ports define domain contracts and must be in domain layer")
        .check(classes);
  }

  @Test
  @DisplayName(
      "Repository implementations must reside in infra layer and end with 'RepositoryImpl'")
  void repositoryImplsMustResideInInfra() {
    classes()
        .that()
        .haveSimpleNameEndingWith("RepositoryImpl")
        .should()
        .resideInAPackage("..infra..")
        .because("Repository implementations are infrastructure concerns")
        .check(classes);
  }

  @Test
  @DisplayName("Data Objects (DO) must reside in infra layer and end with 'DO'")
  void dataObjectsMustResideInInfra() {
    classes()
        .that()
        .haveSimpleNameEndingWith("DO")
        .should()
        .resideInAPackage("..infra..")
        .because("Data Objects are persistence-specific and belong in infrastructure")
        .check(classes);
  }

  // ============================================================================
  // RULE 4: Package Structure Validation
  // ============================================================================

  @Test
  @DisplayName("Controllers must reside in adapter layer")
  void controllersMustResideInAdapter() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .resideInAPackage("..adapter..")
        .because("Controllers are adapters that translate HTTP to domain operations")
        .check(classes);
  }

  @Test
  @DisplayName("Jobs/Schedulers must reside in adapter layer")
  void jobsMustResideInAdapter() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Job")
        .or()
        .haveSimpleNameEndingWith("Scheduler")
        .should()
        .resideInAPackage("..adapter..")
        .because("Jobs are adapters that trigger domain operations on schedule")
        .check(classes);
  }

  // ============================================================================
  // RULE 5: Prevent Circular Dependencies
  // ============================================================================

  @Test
  @DisplayName("No circular dependencies between packages")
  void noCircularDependencies() {
    com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
        .matching("com.patra.ingest.(*)..")
        .should()
        .beFreeOfCycles()
        .because("Circular dependencies create tight coupling and hinder maintainability")
        .check(classes);
  }

  // ============================================================================
  // RULE 6: Adapter Layer Must Not Contain Business Logic
  // ============================================================================

  @Test
  @DisplayName("Adapter layer must NOT contain 'Orchestrator' suffix classes")
  void adapterShouldNotContainOrchestrators() {
    noClasses()
        .that()
        .resideInAPackage("..adapter..")
        .should()
        .haveSimpleNameEndingWith("Orchestrator")
        .because("Business orchestration belongs in Application layer, not Adapter")
        .check(classes);
  }

  @Test
  @DisplayName("Adapter layer must NOT directly call Repository interfaces")
  void adapterShouldNotDirectlyCallRepositories() {
    noClasses()
        .that()
        .resideInAPackage("..adapter..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..domain..repository..")
        .because("Adapters should call Orchestrators, not repositories directly")
        .check(classes);
  }
}
