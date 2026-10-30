import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DomainPurityRulesTest {

    @Test
    fun allows_only_the_whitelist() {
        val artifacts = listOf(
            "dev.patra:patra-tool-core", "org.projectlombok:lombok",
            "com.fasterxml.jackson.core:jackson-annotations", "tools.jackson.core:jackson-core",
            "tools.jackson.core:jackson-databind", "cn.hutool:hutool-core", "org.mapstruct:mapstruct",
        )
        assertThat(DomainPurityRules.violations(artifacts)).isEmpty()
    }

    @Test
    fun reports_everything_else_sorted() {
        val artifacts = listOf("org.springframework:spring-context", "dev.patra:patra-tool-web", "cn.hutool:hutool-core")
        assertThat(DomainPurityRules.violations(artifacts))
            .containsExactly("dev.patra:patra-tool-web", "org.springframework:spring-context")
    }
}
