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
    fun r1_also_rejects_patra_common_because_it_lives_under_patra_api() {
        assertThat(ToolDependencyRules.r1Violations(listOf(d("testImplementation", ":patra-api:patra-common"))))
            .containsExactly("testImplementation -> :patra-api:patra-common")
    }

    @Test
    fun r1_accepts_tool_and_third_party_targets() {
        assertThat(ToolDependencyRules.r1Violations(listOf(d("api", ":patra-tool:patra-tool-core")))).isEmpty()
    }
}
