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
import org.gradle.kotlin.dsl.extra

/// 应用 Patra 统一的依赖管理配置
///
/// 包括：
/// - Spring Boot / Cloud / Cloud Alibaba / Testcontainers / Spring Modulith BOM
/// - 强制版本约束（解决依赖冲突）
/// - 安全版本覆盖（上游 BOM / 传递依赖尚未跟进漏洞修复的构件）
///
/// 版本统一从 `gradle/libs.versions.toml` 获取，确保单一来源。
///
/// @param libs Version Catalog 实例，在 Convention Plugin 中通过 `libs` 访问
fun Project.applyPatraDependencyManagement(libs: VersionCatalog) {
    // 从 Version Catalog 获取版本
    val springBootVersion = libs.findVersion("spring-boot").get().requiredVersion
    val springCloudVersion = libs.findVersion("spring-cloud").get().requiredVersion
    val springCloudAlibabaVersion = libs.findVersion("spring-cloud-alibaba").get().requiredVersion
    val testcontainersVersion = libs.findVersion("testcontainers").get().requiredVersion
    // 强制版本约束
    val httpclientVersion = libs.findVersion("httpclient").get().requiredVersion
    val objenesisVersion = libs.findVersion("objenesis").get().requiredVersion
    val log4jVersion = libs.findVersion("log4j").get().requiredVersion
    // 安全版本覆盖（上游 BOM / 传递依赖尚未跟进漏洞修复）
    fun version(alias: String) = libs.findVersion(alias).get().requiredVersion

    // 覆盖 Spring Boot BOM 管理的版本：键必须是 BOM 内部的版本属性名（如 ${rabbit-amqp-client.version}），
    // 依赖管理插件解析 BOM 时先取同名的项目属性。不用 bomProperty：boot 模块的 Spring Boot 插件还会自己
    // 导入一次 Boot BOM，bomProperty 只作用于下面这次导入，项目属性对两次都生效（Boot 插件文档推荐的写法）
    extra["rabbit-amqp-client.version"] = version("rabbit-amqp-client")
    extra["opentelemetry.version"] = version("opentelemetry")
    extra["thymeleaf.version"] = version("thymeleaf")

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
            // spring-cloud-alibaba 2025.1.0.0 BOM 内锁定的 log4j-core 2.25.1 上游 POM 损坏
            // 通过 bomProperty 覆盖 BOM 内部属性，使最终解析跳过坏版本
            mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:$springCloudAlibabaVersion") {
                bomProperty("log4j-core.version", log4jVersion)
                bomProperty("log4j-slf4j2-impl.version", log4jVersion)
            }
            mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
            mavenBom("org.springframework.modulith:spring-modulith-bom:${version("spring-modulith")}")
        }
        dependencies {
            // minio 8.6.0 / spring-cloud-starter 传递引入
            dependency("org.bouncycastle:bcprov-jdk18on:${version("bouncycastle")}")
            // commons-validator 1.7 传递引入
            dependency("commons-beanutils:commons-beanutils:${version("commons-beanutils")}")
            dependency("org.springframework.retry:spring-retry:${version("spring-retry")}")
            // kafka-clients、minio、clickhouse 客户端传递引入，各自的来源版本见 libs.versions.toml
            dependency("at.yawk.lz4:lz4-java:${version("lz4-java")}")
            dependency("org.xerial.snappy:snappy-java:${version("snappy-java")}")
            dependency("com.google.guava:guava:${version("guava")}")
            // testcontainers 1.21.4 要 1.24.0，minio 8.6.0 要 1.28.0。放这里而不放下面的 force：
            // force 管不到依赖管理插件自己解析 POM 用的临时配置，依赖图提交会把那里的 1.24.0 报成漏洞
            dependency("org.apache.commons:commons-compress:${version("commons-compress")}")
        }
    }

    // 强制版本约束，解决传递依赖冲突
    configurations.all {
        resolutionStrategy {
            // Elasticsearch 依赖冲突
            force("org.apache.httpcomponents:httpclient:$httpclientVersion")
            // Mockito vs Kryo 版本冲突
            force("org.objenesis:objenesis:$objenesisVersion")
        }
    }
}
