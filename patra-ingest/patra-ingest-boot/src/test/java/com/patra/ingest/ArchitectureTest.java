package com.patra.ingest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Architecture tests enforcing hexagonal/DDD rules for the ingest service.
 *
 * <p>Scope: only validates package dependencies and naming within the {@code com.patra.ingest}
 * namespace. Tests are intentionally permissive where the current codebase makes deliberate
 * trade-offs (e.g., adapter referencing enums from domain) while still preventing forbidden
 * couplings (e.g., app↔infra, adapter→infra, domain→frameworks).
 */
@AnalyzeClasses(
    packages = "com.patra.ingest",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTest {

  // ---------- P4.6.1: Domain purity (no framework dependencies) ----------

  @ArchTest
  static final ArchRule domain_should_not_depend_on_frameworks =
      noClasses()
          .that()
          .resideInAnyPackage("com.patra.ingest.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              // Spring & related
              "org.springframework..",
              "org.springframework.boot..",
              "org.springframework.data..",
              // ORM / mapping frameworks
              "org.hibernate..",
              "com.baomidou..",
              "org.mapstruct..",
              // Jakarta/Javax EE APIs (validation, etc.)
              "jakarta..",
              "javax..")
          .because("domain layer must be pure Java and framework-free");

  // ---------- P4.6.2: Dependency direction (adapter→app→domain; infra isolated) ----------

  @ArchTest
  static final ArchRule app_must_not_depend_on_adapter_or_infra =
      noClasses()
          .that()
          .resideInAnyPackage("com.patra.ingest.app..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.patra.ingest.adapter..", "com.patra.ingest.infra..")
          .because("application layer orchestrates domain and must not depend on adapter/infra");

  @ArchTest
  static final ArchRule infra_must_not_depend_on_app_or_adapter =
      noClasses()
          .that()
          .resideInAnyPackage("com.patra.ingest.infra..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.patra.ingest.app..", "com.patra.ingest.adapter..")
          .because("infrastructure implements ports and must not depend on app/adapter");

  @ArchTest
  static final ArchRule adapter_must_not_depend_on_infra =
      noClasses()
          .that()
          .resideInAnyPackage("com.patra.ingest.adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.patra.ingest.infra..")
          .because("adapters should call use cases; no direct infra access");

  @ArchTest
  static final ArchRule domain_must_not_depend_on_outer_layers =
      noClasses()
          .that()
          .resideInAnyPackage("com.patra.ingest.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.patra.ingest.app..", "com.patra.ingest.adapter..", "com.patra.ingest.infra..")
          .because("domain must not depend on application/adapter/infrastructure");

  // ---------- P4.6.3: Naming conventions ----------

  /**
   * Use case implementations should either be named {@code *Orchestrator} or {@code *UseCaseImpl}
   * when they implement a {@code *UseCase} interface. This keeps intent obvious and consistent.
   */
  @ArchTest
  static final ArchRule usecase_impls_named_orchestrator_or_impl =
      classes()
          .that()
          .resideInAnyPackage("com.patra.ingest.app..")
          .and()
          .areNotInterfaces()
          .and()
          .areTopLevelClasses()
          .should(
              new ArchCondition<>(
                  "be named *Orchestrator or *UseCaseImpl when implementing *UseCase") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                  boolean implementsUseCase =
                      javaClass.getAllRawInterfaces().stream()
                          .map(JavaClass::getSimpleName)
                          .anyMatch(n -> n.endsWith("UseCase"));
                  if (!implementsUseCase) {
                    return; // ignore non-use case classes within app package
                  }
                  String simple = javaClass.getSimpleName();
                  boolean ok = simple.endsWith("Orchestrator") || simple.endsWith("UseCaseImpl");
                  if (!ok) {
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            "use case implementation '"
                                + simple
                                + "' should end with Orchestrator or UseCaseImpl"));
                  }
                }
              });

  /** Repository implementations should follow the {@code *Repository*Impl} naming convention. */
  @ArchTest
  static final ArchRule repository_impls_follow_naming =
      classes()
          .that()
          .resideInAnyPackage("com.patra.ingest.infra.persistence.repository..")
          .and()
          .areNotInterfaces()
          .should(
              new ArchCondition<JavaClass>("match .*Repository.*Impl$") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                  String name = item.getSimpleName();
                  if (!name.matches(".*Repository.*Impl$")) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "repository impl name should match .*Repository.*Impl$: " + name));
                  }
                }
              })
          .because("repository adapters should be clearly named as implementations");

  /** RPC adapters/ports should end with {@code PortImpl} or {@code Adapter}. */
  @ArchTest
  static final ArchRule rpc_adapters_follow_naming =
      classes()
          .that()
          .resideInAnyPackage("com.patra.ingest.infra.rpc..")
          .and()
          .resideOutsideOfPackages("..converter..")
          .and()
          .areNotInterfaces()
          .should(
              new ArchCondition<JavaClass>("end with PortImpl or Adapter") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                  String name = item.getSimpleName();
                  boolean ok = name.endsWith("PortImpl") || name.endsWith("Adapter");
                  if (!ok) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item, "rpc adapter should end with PortImpl or Adapter: " + name));
                  }
                }
              })
          .because("RPC adapters should be either PortImpl or *Adapter by convention");
}
