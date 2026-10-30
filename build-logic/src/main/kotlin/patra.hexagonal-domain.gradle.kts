/**
 * Patra Hexagonal Domain Layer Convention Plugin
 *
 * 领域层 - 六边形架构的核心
 *
 * 关键约束（R3，决策 C）：领域层编译类路径只允许白名单构件，
 * 名单在 DomainPurityRules.ALLOWED（patra-tool-core、Lombok、Jackson、Hutool、MapStruct）。
 */

plugins {
    id("patra.java-library")
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// ==================== R3：domain 编译类路径白名单 ====================
abstract class DomainPurityCheck : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val violations: ListProperty<String>

    @TaskAction
    fun check() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "R3 违规：${modulePath.get()}\n" +
                    found.joinToString("\n") { "  compileClasspath 含 $it" } +
                    "\ndomain 编译类路径只允许：${DomainPurityRules.ALLOWED.joinToString(", ")}",
            )
        }
    }
}

tasks.register<DomainPurityCheck>("checkDomainPurity") {
    group = "verification"
    description = "R3：domain 模块编译类路径只允许白名单构件"
    modulePath.set(project.path)
    violations.set(provider {
        configurations.getByName("compileClasspath").resolvedConfiguration.resolvedArtifacts
            .map { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}" }
            .let(DomainPurityRules::violations)
    })
}

tasks.named("check") {
    dependsOn("checkDomainPurity")
}

// 使用 Version Catalog (libs) 声明依赖
dependencies {
    // 领域层核心依赖
    api(project(":patra-tool:patra-tool-core"))

    // Hutool 工具库
    implementation(libs.findLibrary("hutool-core").get())

    // MapStruct (用于值对象映射)
    implementation(libs.findLibrary("mapstruct").get())

    // 测试依赖由 patra.java-library 提供
}

// ==================== R5：声明的 project 依赖必须被使用 ====================
// patra-tool-core 由本插件按层统一给出，不要求模块逐个 import
registerDeclaredDependencyCheck(exempt = setOf(":patra-tool:patra-tool-core"))
