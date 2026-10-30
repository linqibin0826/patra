/**
 * Patra - Medical Publication Data Platform
 *
 * Gradle 9.2.1 多模块项目配置（monorepo workspace root）
 * 架构: 微服务 + 六边形架构 + DDD
 */

import org.gradle.caching.http.HttpBuildCache

rootProject.name = "patra"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

// ==================== include helper ====================
/**
 * 集中映射 Gradle path 到物理目录（决策 E：Gradle path 必须与物理目录一一对应）。
 */
fun includeAt(path: String, dir: String) {
    include(path)
    project(path).projectDir = file(dir)
}

/**
 * 设置嵌套 path 父容器的 projectDir。
 * 当 include(":parent:child") 时 Gradle 隐式创建 :parent；该父容器无 build.gradle.kts，仅作容器，
 * 其 projectDir 默认从 rootProject.projectDir 解析，需手动重映射到实际物理目录。
 * 必须在所有子 include 之后调用（避免子 project 解析时父 project 还不存在）。
 */
fun mapParent(path: String, dir: String) {
    project(path).projectDir = file(dir)
}

// ==================== patra-api / patra-common ====================
includeAt(":patra-api:patra-common", "patra-api/patra-common")

// ==================== patra-tool ====================
includeAt(":patra-tool:patra-tool-core", "patra-tool/patra-tool-core")
includeAt(":patra-tool:patra-tool-cqrs", "patra-tool/patra-tool-cqrs")
includeAt(":patra-tool:patra-tool-web", "patra-tool/patra-tool-web")
includeAt(":patra-tool:patra-tool-jpa", "patra-tool/patra-tool-jpa")
includeAt(":patra-tool:patra-tool-batch", "patra-tool/patra-tool-batch")
includeAt(":patra-tool:patra-tool-object-storage", "patra-tool/patra-tool-object-storage")
includeAt(":patra-tool:patra-tool-observability", "patra-tool/patra-tool-observability")
includeAt(":patra-tool:patra-tool-openapi", "patra-tool/patra-tool-openapi")
includeAt(":patra-tool:patra-tool-test", "patra-tool/patra-tool-test")
includeAt(":patra-tool:patra-tool-http-client", "patra-tool/patra-tool-http-client")
includeAt(":patra-tool:patra-tool-security", "patra-tool/patra-tool-security")
includeAt(":patra-tool:patra-tool-launch", "patra-tool/patra-tool-launch")
mapParent(":patra-tool", "patra-tool")

// ==================== patra-api / Microservices ====================
includeAt(":patra-api:patra-registry:patra-registry-domain", "patra-api/patra-registry/patra-registry-domain")
includeAt(":patra-api:patra-registry:patra-registry-api", "patra-api/patra-registry/patra-registry-api")
includeAt(":patra-api:patra-registry:patra-registry-app", "patra-api/patra-registry/patra-registry-app")
includeAt(":patra-api:patra-registry:patra-registry-infra", "patra-api/patra-registry/patra-registry-infra")
includeAt(":patra-api:patra-registry:patra-registry-adapter", "patra-api/patra-registry/patra-registry-adapter")
includeAt(":patra-api:patra-registry:patra-registry-boot", "patra-api/patra-registry/patra-registry-boot")
mapParent(":patra-api:patra-registry", "patra-api/patra-registry")

includeAt(":patra-api:patra-catalog:patra-catalog-domain", "patra-api/patra-catalog/patra-catalog-domain")
includeAt(":patra-api:patra-catalog:patra-catalog-api", "patra-api/patra-catalog/patra-catalog-api")
includeAt(":patra-api:patra-catalog:patra-catalog-app", "patra-api/patra-catalog/patra-catalog-app")
includeAt(":patra-api:patra-catalog:patra-catalog-infra", "patra-api/patra-catalog/patra-catalog-infra")
includeAt(":patra-api:patra-catalog:patra-catalog-adapter", "patra-api/patra-catalog/patra-catalog-adapter")
includeAt(":patra-api:patra-catalog:patra-catalog-boot", "patra-api/patra-catalog/patra-catalog-boot")
mapParent(":patra-api:patra-catalog", "patra-api/patra-catalog")

includeAt(":patra-api:patra-identity:patra-identity-api", "patra-api/patra-identity/patra-identity-api")
includeAt(":patra-api:patra-identity:patra-identity-domain", "patra-api/patra-identity/patra-identity-domain")
includeAt(":patra-api:patra-identity:patra-identity-app", "patra-api/patra-identity/patra-identity-app")
includeAt(":patra-api:patra-identity:patra-identity-infra", "patra-api/patra-identity/patra-identity-infra")
includeAt(":patra-api:patra-identity:patra-identity-adapter", "patra-api/patra-identity/patra-identity-adapter")
includeAt(":patra-api:patra-identity:patra-identity-boot", "patra-api/patra-identity/patra-identity-boot")
mapParent(":patra-api:patra-identity", "patra-api/patra-identity")

includeAt(":patra-api:patra-interaction:patra-interaction-api", "patra-api/patra-interaction/patra-interaction-api")
includeAt(":patra-api:patra-interaction:patra-interaction-domain", "patra-api/patra-interaction/patra-interaction-domain")
includeAt(":patra-api:patra-interaction:patra-interaction-app", "patra-api/patra-interaction/patra-interaction-app")
includeAt(":patra-api:patra-interaction:patra-interaction-infra", "patra-api/patra-interaction/patra-interaction-infra")
includeAt(":patra-api:patra-interaction:patra-interaction-adapter", "patra-api/patra-interaction/patra-interaction-adapter")
includeAt(":patra-api:patra-interaction:patra-interaction-boot", "patra-api/patra-interaction/patra-interaction-boot")
mapParent(":patra-api:patra-interaction", "patra-api/patra-interaction")

// ==================== patra-api / Gateway ====================
includeAt(":patra-api:patra-gateway-boot", "patra-api/patra-gateway-boot")

// ==================== patra-api 容器层 ====================
// 所有子 include 之后再设置父容器 projectDir，避免子 project 解析时父 project 还不存在
mapParent(":patra-api", "patra-api")
