/**
 * Patra Hexagonal Domain Layer Convention Plugin
 *
 * 领域层 - 六边形架构的核心
 *
 * 关键约束：领域层必须是框架无关的纯 Java 代码
 * - 禁止 Spring Framework
 * - 禁止 Spring Boot
 * - 禁止 Jakarta EE (JPA, Validation, Servlet)
 * - 禁止持久化框架 (Hibernate)
 * - 禁止 Web 容器 (Tomcat, Netty)
 *
 * 允许：
 * - patra-common-core
 * - Lombok
 * - Hutool
 * - Jackson (序列化)
 * - 测试框架
 */

plugins {
    id("linqibin.java-library")
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// ==================== 领域层纯净性强制规则 ====================
// 禁止的依赖组（框架依赖）
val bannedGroups = setOf(
    "org.springframework",
    "org.springframework.boot",
    "org.springframework.cloud",
    "org.springframework.data",
    "org.springframework.security",
    "jakarta.persistence",
    "jakarta.validation",
    "jakarta.annotation",
    "jakarta.servlet",
    "jakarta.transaction",
    "org.hibernate",
    "org.hibernate.orm",
    "org.apache.tomcat",
    "org.apache.tomcat.embed",
    "io.netty"
)

// Configuration Cache 兼容的领域层纯净性检查任务
abstract class DomainPurityCheck : DefaultTask() {
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun check() {
        val v = violations.get()
        if (v.isNotEmpty()) {
            throw GradleException("""
                |
                |❌ DOMAIN LAYER VIOLATION - Hexagonal Architecture Constraint
                |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                |
                |The domain layer MUST be framework-free!
                |
                |Found forbidden dependencies:
                |${v.joinToString("\n") { "  - $it" }}
                |
                |✅ Allowed: patra-common, Lombok, Hutool, Jackson, Test frameworks
                |❌ Forbidden: Spring, Jakarta EE, Hibernate, Tomcat, Netty
                |
                |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """.trimMargin())
        }
    }
}

tasks.register<DomainPurityCheck>("enforceDomainPurity") {
    group = "verification"
    description = "Enforces domain layer purity - no framework dependencies allowed"

    // 在配置阶段解析依赖，结果作为任务输入属性（Configuration Cache 兼容）
    violations.set(provider {
        val result = mutableListOf<String>()
        configurations.filter { it.name in listOf("compileClasspath", "runtimeClasspath") }
            .forEach { config ->
                config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val moduleId = artifact.moduleVersion.id
                    if (bannedGroups.any { moduleId.group.startsWith(it) }) {
                        result.add("${moduleId.group}:${moduleId.name}:${moduleId.version}")
                    }
                }
            }
        result
    })
}

tasks.named("check") {
    dependsOn("enforceDomainPurity")
}

// 使用 Version Catalog (libs) 声明依赖
dependencies {
    // 领域层核心依赖
    api(project(":patra-common-enums"))
    api(project(":linqibin-commons-core"))

    // Hutool 工具库
    implementation(libs.findLibrary("hutool-core").get())

    // MapStruct (用于值对象映射)
    implementation(libs.findLibrary("mapstruct").get())

    // 测试依赖由 patra.java-library 提供
}
