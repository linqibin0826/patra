import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeclaredDependencyRulesTest {

    private fun d(configuration: String, target: String) = ToolDependencyRules.Declared(configuration, target)

    private val targets = mapOf(
        ":patra-tool:patra-tool-core" to DeclaredDependencyRules.Target(setOf("dev.patra.tool.core", "dev.patra.tool.core.error"), autoConfigured = false),
        ":patra-tool:patra-tool-web" to DeclaredDependencyRules.Target(setOf("dev.patra.tool.web"), autoConfigured = true),
    )

    @Test
    fun flags_hard_project_dependency_that_nothing_imports() {
        val v = DeclaredDependencyRules.violations(
            listOf(d("api", ":patra-tool:patra-tool-core")), imports = setOf("java.util.List"), targets = targets, exempt = emptySet(),
        )
        assertThat(v).containsExactly("api -> :patra-tool:patra-tool-core（src/main/java 没有 import 它的任何包）")
    }

    @Test
    fun accepts_dependency_whose_package_is_imported() {
        val v = DeclaredDependencyRules.violations(
            listOf(d("implementation", ":patra-tool:patra-tool-core")),
            imports = setOf("dev.patra.tool.core.error.DomainException"), targets = targets, exempt = emptySet(),
        )
        assertThat(v).isEmpty()
    }

    @Test
    fun static_import_counts_as_usage() {
        val v = DeclaredDependencyRules.violations(
            listOf(d("api", ":patra-tool:patra-tool-core")),
            imports = setOf("dev.patra.tool.core.PatraOrders.HIGHEST"), targets = targets, exempt = emptySet(),
        )
        assertThat(v).isEmpty()
    }

    @Test
    fun auto_configured_targets_are_allowed_without_imports() {
        val v = DeclaredDependencyRules.violations(
            listOf(d("implementation", ":patra-tool:patra-tool-web")), imports = emptySet(), targets = targets, exempt = emptySet(),
        )
        assertThat(v).isEmpty()
    }

    @Test
    fun exempt_targets_and_soft_configurations_and_third_party_are_ignored() {
        val v = DeclaredDependencyRules.violations(
            listOf(
                d("api", ":patra-tool:patra-tool-core"),
                d("compileOnly", ":patra-tool:patra-tool-core"),
                d("testImplementation", ":patra-tool:patra-tool-core"),
                d("runtimeOnly", ":patra-tool:patra-tool-core"),
                d("api", "org.springframework:spring-context"),
            ),
            imports = emptySet(), targets = targets, exempt = setOf(":patra-tool:patra-tool-core"),
        )
        assertThat(v).isEmpty()
    }

    @Test
    fun unknown_targets_are_skipped() {
        val v = DeclaredDependencyRules.violations(
            listOf(d("api", ":patra-api:patra-nope")), imports = emptySet(), targets = targets, exempt = emptySet(),
        )
        assertThat(v).isEmpty()
    }
}
