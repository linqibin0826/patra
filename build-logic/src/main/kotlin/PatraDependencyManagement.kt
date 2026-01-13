/**
 * Patra Dependency Management
 *
 * 集中管理 BOM 版本和强制版本约束，避免在多个 Convention Plugin 中重复定义。
 *
 * 使用方式：
 * ```kotlin
 * plugins {
 *     id("io.spring.dependency-management")
 * }
 *
 * // 应用统一的依赖管理配置
 * applyPatraDependencyManagement()
 * ```
 */

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/// Patra 项目的 BOM 版本定义
///
/// 这些版本应与 `gradle/libs.versions.toml` 保持同步
object PatraVersions {
    const val SPRING_BOOT = "4.0.1"
    const val SPRING_CLOUD = "2025.1.0"
    const val RESILIENCE4J = "2.2.0"
    const val TESTCONTAINERS = "1.20.4"
    const val AWS_SDK = "2.25.36"

    // 强制版本约束 - 解决传递依赖冲突
    const val COMMONS_COMPRESS = "1.28.0"
    const val HTTP_CLIENT = "4.5.14"
    const val OBJENESIS = "3.4"
}

/// 应用 Patra 统一的依赖管理配置
///
/// 包括：
/// - Spring Boot / Cloud / Resilience4j / Testcontainers / AWS SDK BOM
/// - 强制版本约束（解决依赖冲突）
fun Project.applyPatraDependencyManagement() {
    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${PatraVersions.SPRING_BOOT}")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${PatraVersions.SPRING_CLOUD}")
            mavenBom("io.github.resilience4j:resilience4j-bom:${PatraVersions.RESILIENCE4J}")
            mavenBom("org.testcontainers:testcontainers-bom:${PatraVersions.TESTCONTAINERS}")
            mavenBom("software.amazon.awssdk:bom:${PatraVersions.AWS_SDK}")
        }
    }

    // 强制版本约束，解决传递依赖冲突
    configurations.all {
        resolutionStrategy {
            // Testcontainers vs MinIO 版本冲突
            force("org.apache.commons:commons-compress:${PatraVersions.COMMONS_COMPRESS}")
            // Elasticsearch 依赖冲突
            force("org.apache.httpcomponents:httpclient:${PatraVersions.HTTP_CLIENT}")
            // Mockito vs Kryo 版本冲突
            force("org.objenesis:objenesis:${PatraVersions.OBJENESIS}")
        }
    }
}
