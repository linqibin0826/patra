import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ToolModulePluginFunctionalTest {

    @TempDir lateinit var root: File

    private fun write(path: String, content: String) {
        File(root, path).apply { parentFile.mkdirs(); writeText(content.trimIndent()) }
    }

    private fun project(): File {
        write("settings.gradle.kts", """
            rootProject.name = "fixture"
            include(":patra-tool:patra-tool-core", ":patra-tool:patra-tool-demo", ":patra-api:patra-fake")
        """)
        write("patra-tool/patra-tool-core/build.gradle.kts", """plugins { id("java-library") }""")
        write("patra-api/patra-fake/build.gradle.kts", """plugins { id("java-library") }""")
        return root
    }

    @Test
    fun check_fails_with_r1_when_tool_depends_on_patra_api() {
        project()
        write("patra-tool/patra-tool-demo/build.gradle.kts", """
            plugins { id("java-library"); id("patra.tool-module") }
            dependencies { implementation(project(":patra-api:patra-fake")) }
        """)
        val result = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments(":patra-tool:patra-tool-demo:check").buildAndFail()
        assertThat(result.output).contains("R1 违规：:patra-tool:patra-tool-demo", "implementation -> :patra-api:patra-fake")
    }

    @Test
    fun check_passes_for_a_clean_tool_module() {
        project()
        write("patra-tool/patra-tool-demo/build.gradle.kts", """
            plugins { id("java-library"); id("patra.tool-module") }
            dependencies { api(project(":patra-tool:patra-tool-core")) }
        """)
        val result = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments(":patra-tool:patra-tool-demo:check").build()
        assertThat(result.output).doesNotContain("违规")
    }
}
