// 工具模块约定（patra-tool/*）：R1 工具不依赖业务、R2 工具间只指向 core、R4 自动配置写法，全部挂 check。
// 规则本体在 ToolDependencyRules / AutoConfigurationRules（纯函数，有单测）；这里只负责收集输入与注册任务。
import org.gradle.api.artifacts.ProjectDependency

abstract class ToolDependencyCheck : DefaultTask() {
    @get:Input abstract val rule: Property<String>
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val violations: ListProperty<String>
    @get:Input abstract val advice: Property<String>

    @TaskAction
    fun check() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "${rule.get()} 违规：${modulePath.get()}\n" + found.joinToString("\n") { "  $it" } + "\n" + advice.get(),
            )
        }
    }
}

val declaredProjectDependencies = provider {
    configurations.flatMap { configuration ->
        configuration.dependencies.withType(ProjectDependency::class.java)
            .map { ToolDependencyRules.Declared(configuration.name, it.path) }
    }
}

tasks.register<ToolDependencyCheck>("checkToolBoundary") {
    group = "verification"
    description = "R1：工具模块不得依赖 patra-api/* 下的模块"
    rule.set("R1")
    modulePath.set(project.path)
    violations.set(declaredProjectDependencies.map { ToolDependencyRules.r1Violations(it) })
    advice.set("工具模块不得依赖 patra-api/* 下的任何模块；业务契约放回对应服务。")
}

tasks.register<ToolDependencyCheck>("checkToolDependencies") {
    group = "verification"
    description = "R2：工具间硬依赖只允许指向 patra-tool-core"
    rule.set("R2")
    modulePath.set(project.path)
    violations.set(declaredProjectDependencies.map { ToolDependencyRules.r2Violations(project.path, it) })
    advice.set("工具间硬依赖只允许指向 :patra-tool:patra-tool-core（白名单：security -> web）。要协作改成 compileOnly，并在自动配置类上加 @ConditionalOnClass。")
}

// 本插件可能先于 java 插件被 apply（plugins 块里排在 patra.spring-starter 前面），等 java-base 到位再挂 check
pluginManager.withPlugin("java-base") {
    tasks.named("check") {
        dependsOn("checkToolBoundary", "checkToolDependencies")
    }
}

// ==================== R4：自动配置写法 ====================
abstract class AutoConfigurationCheck : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val packageSegment: Property<String>
    @get:Input abstract val classPrefix: Property<String>
    @get:Input abstract val configPrefix: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sourceFiles: ConfigurableFileCollection
    @get:Internal abstract val mainJava: DirectoryProperty
    @get:Internal abstract val importsFile: RegularFileProperty
    @get:Internal abstract val testJava: DirectoryProperty

    @TaskAction
    fun check() {
        val problems = AutoConfigurationRules.check(
            AutoConfigurationRules.Input(
                mainJava.asFile.orNull, importsFile.asFile.orNull, testJava.asFile.orNull,
                packageSegment.get(), classPrefix.get(), configPrefix.get(),
            ),
        )
        if (problems.isNotEmpty()) {
            throw GradleException(
                "R4 违规：${modulePath.get()}\n" + problems.joinToString("\n") { "  $it" } +
                    "\n工具模块的 bean 一律由 autoconfigure 包里的自动配置注册，每个自动配置类配同名 ApplicationContextRunner 测试。",
            )
        }
    }
}

val capability = project.name.removePrefix("patra-tool-")
val importsPath = "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

tasks.register<AutoConfigurationCheck>("checkAutoConfiguration") {
    group = "verification"
    description = "R4：自动配置写法（包位置、public final、imports 双向一致、契约测试、无构造型注解）"
    modulePath.set(project.path)
    packageSegment.set(capability.replace("-", ""))
    classPrefix.set(capability.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) })
    configPrefix.set("patra.$capability")
    mainJava.set(layout.projectDirectory.dir("src/main/java"))
    importsFile.set(layout.projectDirectory.file(importsPath))
    testJava.set(layout.projectDirectory.dir("src/test/java"))
    sourceFiles.from(fileTree("src/main/java"), fileTree("src/test/java"), layout.projectDirectory.file(importsPath))
}
pluginManager.withPlugin("java-base") {
    tasks.named("check") {
        dependsOn("checkAutoConfiguration")
    }
}
