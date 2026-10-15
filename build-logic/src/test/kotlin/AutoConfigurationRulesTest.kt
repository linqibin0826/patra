import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AutoConfigurationRulesTest {

    @TempDir lateinit var module: File

    private val pkgDir = "dev/patra/tool/demo"

    private fun src(path: String, body: String): File =
        File(module, "src/main/java/$path").apply { parentFile.mkdirs(); writeText(body.trimIndent()) }

    private fun test(path: String) =
        File(module, "src/test/java/$path").apply { parentFile.mkdirs(); writeText("class X {}") }

    private fun imports(vararg lines: String) =
        File(module, "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .apply { parentFile.mkdirs(); writeText(lines.joinToString("\n")) }

    private fun check() = AutoConfigurationRules.check(
        AutoConfigurationRules.Input(
            mainJava = File(module, "src/main/java"),
            importsFile = File(module, "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"),
            testJava = File(module, "src/test/java"),
            packageSegment = "demo",
            classPrefix = "Demo",
            configPrefix = "patra.demo",
        ),
    )

    private val goodAutoConfiguration = """
        package dev.patra.tool.demo.autoconfigure;
        @AutoConfiguration
        public final class DemoAutoConfiguration {}
    """

    @Test
    fun a_well_formed_module_has_no_problems() {
        src("$pkgDir/autoconfigure/DemoAutoConfiguration.java", goodAutoConfiguration)
        imports("dev.patra.tool.demo.autoconfigure.DemoAutoConfiguration")
        test("$pkgDir/autoconfigure/DemoAutoConfigurationTest.java")
        assertThat(check()).isEmpty()
    }

    @Test
    fun rule_a_requires_package_final_name_imports_and_test() {
        src("$pkgDir/impl/XAutoConfiguration.java", """
            package dev.patra.tool.demo.impl;
            @AutoConfiguration
            public class XAutoConfiguration {}
        """)
        imports()
        assertThat(check()).anySatisfy { assertThat(it).contains("规则 a") }
            .anySatisfy { assertThat(it).contains("public final class") }
            .anySatisfy { assertThat(it).contains("Demo<Aspect>AutoConfiguration") }
            .anySatisfy { assertThat(it).contains("没有登记") }
            .anySatisfy { assertThat(it).contains("缺少契约测试") }
    }

    @Test
    fun rule_b_rejects_imports_without_a_class() {
        imports("dev.patra.tool.demo.autoconfigure.GhostAutoConfiguration")
        assertThat(check()).singleElement().asString().contains("规则 b", "GhostAutoConfiguration")
    }

    @Test
    fun rule_c_rejects_stereotypes() {
        src("$pkgDir/Svc.java", """
            package dev.patra.tool.demo;
            @Component
            public class Svc {}
        """)
        assertThat(check()).singleElement().asString().contains("规则 c")
    }

    @Test
    fun rule_d_allows_configuration_only_inside_autoconfigure() {
        src("$pkgDir/Cfg.java", """
            package dev.patra.tool.demo;
            @Configuration
            public class Cfg {}
        """)
        src("$pkgDir/autoconfigure/Nested.java", """
            package dev.patra.tool.demo.autoconfigure;
            @Configuration(proxyBeanMethods = false)
            class Nested {}
        """)
        assertThat(check()).singleElement().asString().contains("规则 d", "Cfg.java")
    }

    @Test
    fun rule_e_rejects_bare_numeric_order_but_not_constants() {
        src("$pkgDir/A.java", """
            package dev.patra.tool.demo;
            public class A { @Order(100) void x() {} @Order(PatraOrders.X) void y() {} }
        """)
        assertThat(check()).singleElement().asString().contains("规则 e")
    }

    @Test
    fun rule_f_rejects_mapper_building_outside_autoconfigure() {
        src("$pkgDir/J.java", """
            package dev.patra.tool.demo;
            public class J { Object m = JsonMapper.builder().build(); }
        """)
        assertThat(check()).singleElement().asString().contains("规则 f")
    }

    @Test
    fun rule_g_requires_record_in_autoconfigure_with_module_prefix() {
        src("$pkgDir/autoconfigure/DemoProperties.java", """
            package dev.patra.tool.demo.autoconfigure;
            @ConfigurationProperties(prefix = "linqibin.starter.demo")
            public class DemoProperties {}
        """)
        assertThat(check()).anySatisfy { assertThat(it).contains("必须是 record") }
            .anySatisfy { assertThat(it).contains("配置前缀必须是 patra.demo") }
    }

    @Test
    fun comments_do_not_count() {
        src("$pkgDir/C.java", """
            package dev.patra.tool.demo;
            /// 例子：`@Component` 与 @Order(100) 只是文档
            // @Configuration
            public class C {}
        """)
        assertThat(check()).isEmpty()
    }
}
