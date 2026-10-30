import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolDependencyRulesTest {

    private fun d(configuration: String, target: String) = ToolDependencyRules.Declared(configuration, target)

    @Test
    fun r1_rejects_any_configuration_pointing_at_patra_api() {
        val violations = ToolDependencyRules.r1Violations(
            listOf(d("implementation", ":patra-api:patra-catalog:patra-catalog-api"), d("api", ":patra-tool:patra-tool-core")),
        )
        assertThat(violations).containsExactly("implementation -> :patra-api:patra-catalog:patra-catalog-api")
    }

    @Test
    fun r1_accepts_tool_and_third_party_targets() {
        assertThat(ToolDependencyRules.r1Violations(listOf(d("api", ":patra-tool:patra-tool-core")))).isEmpty()
    }

    private val jpa = ":patra-tool:patra-tool-jpa"

    @Test
    fun r2_allows_hard_dependency_on_core_only() {
        val v = ToolDependencyRules.r2Violations(jpa, listOf(d("api", ToolDependencyRules.CORE), d("api", ":patra-tool:patra-tool-web")))
        assertThat(v).containsExactly("api -> :patra-tool:patra-tool-web")
    }

    @Test
    fun r2_allows_compile_only_on_any_tool() {
        assertThat(ToolDependencyRules.r2Violations(jpa, listOf(d("compileOnly", ":patra-tool:patra-tool-web")))).isEmpty()
    }

    @Test
    fun r2_whitelists_security_to_web() {
        assertThat(ToolDependencyRules.r2Violations(":patra-tool:patra-tool-security", listOf(d("api", ":patra-tool:patra-tool-web")))).isEmpty()
    }

    @Test
    fun r2_lets_tests_use_core_test_and_compile_only_targets() {
        val declared = listOf(
            d("compileOnly", ":patra-tool:patra-tool-web"),
            d("testImplementation", ToolDependencyRules.TEST),
            d("integrationTestImplementation", ":patra-tool:patra-tool-web"),
            d("integrationTestImplementation", ":patra-tool:patra-tool-batch"),
        )
        assertThat(ToolDependencyRules.r2Violations(jpa, declared)).containsExactly("integrationTestImplementation -> :patra-tool:patra-tool-batch")
    }

    @Test
    fun r2_ignores_third_party_and_self() {
        assertThat(ToolDependencyRules.r2Violations(jpa, listOf(d("api", jpa)))).isEmpty()
    }
}
