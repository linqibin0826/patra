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
        write("patra-tool/patra-tool-core/src/main/java/dev/patra/tool/core/Core.java", "package dev.patra.tool.core; public class Core {}")
        write("patra-api/patra-fake/build.gradle.kts", """plugins { id("java-library") }""")
        write("patra-api/patra-fake/src/main/java/dev/patra/fake/Fake.java", "package dev.patra.fake; public class Fake {}")
        return root
    }

    /// demo 模块的主代码 import 指定类，让 R5（声明的依赖必须被 import）在该用例里不干扰被测规则。
    private fun demoUses(fqn: String) {
        write("patra-tool/patra-tool-demo/src/main/java/dev/patra/tool/demo/Demo.java", "package dev.patra.tool.demo;\n\nimport $fqn;\n\npublic class Demo {}\n")
    }

    @Test
    fun check_fails_with_r1_when_tool_depends_on_patra_api() {
        project()
        write("patra-tool/patra-tool-demo/build.gradle.kts", """
            plugins { id("java-library"); id("patra.tool-module") }
            dependencies { implementation(project(":patra-api:patra-fake")) }
        """)
        demoUses("dev.patra.fake.Fake")
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
        demoUses("dev.patra.tool.core.Core")
        val result = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments(":patra-tool:patra-tool-demo:check").build()
        assertThat(result.output).doesNotContain("违规")
    }

    @Test
    fun check_fails_with_r5_when_declared_project_dependency_is_never_imported() {
        project()
        write("patra-tool/patra-tool-demo/build.gradle.kts", """
            plugins { id("java-library"); id("patra.tool-module") }
            dependencies { api(project(":patra-tool:patra-tool-core")) }
        """)
        val result = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments(":patra-tool:patra-tool-demo:checkDeclaredDependencies").buildAndFail()
        assertThat(result.output).contains("R5 违规：:patra-tool:patra-tool-demo", "api -> :patra-tool:patra-tool-core")
    }
}
