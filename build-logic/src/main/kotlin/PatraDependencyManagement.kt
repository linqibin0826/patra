/**
 * Patra Dependency Management
 *
 * 集中管理依赖版本，使用 Gradle Version Catalog 作为单一版本来源。
 *
 * 使用方式：
 * ```kotlin
 * plugins {
 *     id("io.spring.dependency-management")
 * }
 *
 * // 应用统一的依赖管理配置
 * applyPatraDependencyManagement(libs)
 * ```
 *
 * 依赖声明（使用 Version Catalog）：
 * ```kotlin
 * dependencies {
 *     testImplementation(libs.junit.jupiter)
 *     testImplementation(libs.assertj.core)
 * }
 * ```
 */

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.kotlin.dsl.configure

/// 应用 Patra 统一的依赖管理配置
///
/// 包括：
/// - Spring Boot / Cloud / Resilience4j / Testcontainers BOM
/// - 强制版本约束（解决依赖冲突）
///
/// 版本统一从 `gradle/libs.versions.toml` 获取，确保单一来源。
///
/// @param libs Version Catalog 实例，在 Convention Plugin 中通过 `libs` 访问
fun Project.applyPatraDependencyManagement(libs: VersionCatalog) {
    // 从 Version Catalog 获取版本
    val springBootVersion = libs.findVersion("spring-boot").get().requiredVersion
    val springCloudVersion = libs.findVersion("spring-cloud").get().requiredVersion
    val resilience4jVersion = libs.findVersion("resilience4j").get().requiredVersion
    val testcontainersVersion = libs.findVersion("testcontainers").get().requiredVersion
    // 强制版本约束
    val commonsCompressVersion = libs.findVersion("commons-compress").get().requiredVersion
    val httpclientVersion = libs.findVersion("httpclient").get().requiredVersion
    val objenesisVersion = libs.findVersion("objenesis").get().requiredVersion

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
            mavenBom("io.github.resilience4j:resilience4j-bom:$resilience4jVersion")
            mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
        }
    }

    // 强制版本约束，解决传递依赖冲突
    configurations.all {
        resolutionStrategy {
            // Testcontainers vs MinIO 版本冲突
            force("org.apache.commons:commons-compress:$commonsCompressVersion")
            // Elasticsearch 依赖冲突
            force("org.apache.httpcomponents:httpclient:$httpclientVersion")
            // Mockito vs Kryo 版本冲突
            force("org.objenesis:objenesis:$objenesisVersion")
        }
    }
}
