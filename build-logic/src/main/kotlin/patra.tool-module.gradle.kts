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
