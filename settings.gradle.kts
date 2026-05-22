/**
 * Patra - Medical Publication Data Platform
 *
 * Gradle 9.2.1 多模块项目配置（monorepo workspace root）
 * 架构: 微服务 + 六边形架构 + DDD
 */

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

// ==================== patra-common ====================
includeAt(":patra-common:patra-common-model", "patra-api/patra-common/patra-common-model")
includeAt(":patra-common:patra-common-provenance-api", "patra-api/patra-common/patra-common-provenance-api")
includeAt(":patra-common:patra-common-enums", "patra-api/patra-common/patra-common-enums")
mapParent(":patra-common", "patra-api/patra-common")

// ==================== linqibin-commons ====================
// 决策 E：嵌套 path 与物理目录一一对应
includeAt(":linqibin-commons:linqibin-commons-core", "linqibin-commons/linqibin-commons-core")
includeAt(":linqibin-commons:linqibin-commons-storage", "linqibin-commons/linqibin-commons-storage")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-core", "linqibin-commons/linqibin-spring-boot-starter-core")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-web", "linqibin-commons/linqibin-spring-boot-starter-web")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-jpa", "linqibin-commons/linqibin-spring-boot-starter-jpa")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-batch", "linqibin-commons/linqibin-spring-boot-starter-batch")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-rest-client", "linqibin-commons/linqibin-spring-boot-starter-rest-client")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-http-interface", "linqibin-commons/linqibin-spring-boot-starter-http-interface")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-observability", "linqibin-commons/linqibin-spring-boot-starter-observability")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-redisson", "linqibin-commons/linqibin-spring-boot-starter-redisson")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-object-storage", "linqibin-commons/linqibin-spring-boot-starter-object-storage")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-test", "linqibin-commons/linqibin-spring-boot-starter-test")
includeAt(":linqibin-commons:linqibin-spring-boot-starter-openapi", "linqibin-commons/linqibin-spring-boot-starter-openapi")
mapParent(":linqibin-commons", "linqibin-commons")

// ==================== patra-starters ====================
includeAt(":patra-starters:patra-spring-boot-starter-provenance", "patra-starters/patra-spring-boot-starter-provenance")
includeAt(":patra-starters:patra-spring-boot-starter-expr", "patra-starters/patra-spring-boot-starter-expr")
mapParent(":patra-starters", "patra-starters")

// ==================== Expression Kernel ====================
includeAt(":patra-expr-kernel", "patra-api/patra-expr-kernel")

// ==================== Microservices ====================
includeAt(":patra-registry:patra-registry-domain", "patra-api/patra-registry/patra-registry-domain")
includeAt(":patra-registry:patra-registry-api", "patra-api/patra-registry/patra-registry-api")
includeAt(":patra-registry:patra-registry-app", "patra-api/patra-registry/patra-registry-app")
includeAt(":patra-registry:patra-registry-infra", "patra-api/patra-registry/patra-registry-infra")
includeAt(":patra-registry:patra-registry-adapter", "patra-api/patra-registry/patra-registry-adapter")
includeAt(":patra-registry:patra-registry-boot", "patra-api/patra-registry/patra-registry-boot")
mapParent(":patra-registry", "patra-api/patra-registry")

includeAt(":patra-ingest:patra-ingest-domain", "patra-api/patra-ingest/patra-ingest-domain")
includeAt(":patra-ingest:patra-ingest-api", "patra-api/patra-ingest/patra-ingest-api")
includeAt(":patra-ingest:patra-ingest-app", "patra-api/patra-ingest/patra-ingest-app")
includeAt(":patra-ingest:patra-ingest-infra", "patra-api/patra-ingest/patra-ingest-infra")
includeAt(":patra-ingest:patra-ingest-adapter", "patra-api/patra-ingest/patra-ingest-adapter")
includeAt(":patra-ingest:patra-ingest-boot", "patra-api/patra-ingest/patra-ingest-boot")
mapParent(":patra-ingest", "patra-api/patra-ingest")

includeAt(":patra-catalog:patra-catalog-domain", "patra-api/patra-catalog/patra-catalog-domain")
includeAt(":patra-catalog:patra-catalog-api", "patra-api/patra-catalog/patra-catalog-api")
includeAt(":patra-catalog:patra-catalog-app", "patra-api/patra-catalog/patra-catalog-app")
includeAt(":patra-catalog:patra-catalog-infra", "patra-api/patra-catalog/patra-catalog-infra")
includeAt(":patra-catalog:patra-catalog-adapter", "patra-api/patra-catalog/patra-catalog-adapter")
includeAt(":patra-catalog:patra-catalog-boot", "patra-api/patra-catalog/patra-catalog-boot")
mapParent(":patra-catalog", "patra-api/patra-catalog")

includeAt(":patra-object-storage:patra-object-storage-domain", "patra-api/patra-object-storage/patra-object-storage-domain")
includeAt(":patra-object-storage:patra-object-storage-api", "patra-api/patra-object-storage/patra-object-storage-api")
includeAt(":patra-object-storage:patra-object-storage-app", "patra-api/patra-object-storage/patra-object-storage-app")
includeAt(":patra-object-storage:patra-object-storage-infra", "patra-api/patra-object-storage/patra-object-storage-infra")
includeAt(":patra-object-storage:patra-object-storage-adapter", "patra-api/patra-object-storage/patra-object-storage-adapter")
includeAt(":patra-object-storage:patra-object-storage-boot", "patra-api/patra-object-storage/patra-object-storage-boot")
mapParent(":patra-object-storage", "patra-api/patra-object-storage")

// ==================== Gateway ====================
includeAt(":patra-gateway-boot", "patra-api/patra-gateway-boot")
