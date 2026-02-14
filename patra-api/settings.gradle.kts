/**
 * Patra - Medical Publication Data Platform
 *
 * Gradle 9.2.1 多模块项目配置
 * 架构: 微服务 + 六边形架构 + DDD
 */

rootProject.name = "patra"

// 启用类型安全的项目访问器 (如 projects.patraCommonCore)
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ==================== Plugin Management ====================
pluginManagement {
    // 包含 build-logic 作为 Composite Build
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// ==================== Dependency Resolution Management ====================
dependencyResolutionManagement {
    repositories {
        // 阿里云镜像（加速国内下载）
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        mavenCentral()
    }

    // 强制所有子项目使用统一的仓库配置，禁止子项目自定义仓库
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

// ==================== Common Modules ====================
include(":patra-common:patra-common-core")
include(":patra-common:patra-common-storage")
include(":patra-common:patra-common-model")
include(":patra-common:patra-common-provenance-api")

// ==================== Expression Kernel ====================
include(":patra-expr-kernel")

// ==================== Spring Boot Starters ====================
include(":patra-spring-boot-starter-core")
include(":patra-spring-boot-starter-web")
include(":patra-spring-boot-starter-jpa")
include(":patra-spring-boot-starter-batch")
include(":patra-spring-boot-starter-rest-client")
include(":patra-spring-boot-starter-http-interface")
include(":patra-spring-boot-starter-observability")
include(":patra-spring-boot-starter-provenance")
include(":patra-spring-boot-starter-redisson")
include(":patra-spring-boot-starter-object-storage")
include(":patra-spring-boot-starter-expr")
include(":patra-spring-boot-starter-test")
include(":patra-spring-boot-starter-openapi")

// ==================== Microservices ====================
include(":patra-registry:patra-registry-domain")
include(":patra-registry:patra-registry-api")
include(":patra-registry:patra-registry-app")
include(":patra-registry:patra-registry-infra")
include(":patra-registry:patra-registry-adapter")
include(":patra-registry:patra-registry-boot")

include(":patra-ingest:patra-ingest-domain")
include(":patra-ingest:patra-ingest-api")
include(":patra-ingest:patra-ingest-app")
include(":patra-ingest:patra-ingest-infra")
include(":patra-ingest:patra-ingest-adapter")
include(":patra-ingest:patra-ingest-boot")

include(":patra-catalog:patra-catalog-domain")
include(":patra-catalog:patra-catalog-api")
include(":patra-catalog:patra-catalog-app")
include(":patra-catalog:patra-catalog-infra")
include(":patra-catalog:patra-catalog-adapter")
include(":patra-catalog:patra-catalog-boot")

include(":patra-object-storage:patra-object-storage-domain")
include(":patra-object-storage:patra-object-storage-api")
include(":patra-object-storage:patra-object-storage-app")
include(":patra-object-storage:patra-object-storage-infra")
include(":patra-object-storage:patra-object-storage-adapter")
include(":patra-object-storage:patra-object-storage-boot")

// ==================== Gateway ====================
include(":patra-gateway-boot")
