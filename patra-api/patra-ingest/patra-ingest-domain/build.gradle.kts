/**
 * Patra Ingest Domain
 *
 * 领域层 - 纯 Java 业务逻辑
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("patra.hexagonal-domain")
}

// patra-common-core 由 patra.hexagonal-domain 插件提供
dependencies {
    api(project(":patra-api:patra-common"))
    // Hutool 工具库（原经 patra-tool-core 传递，core 去第三方依赖后由领域层自己声明）
    api(libs.hutool.core)
}
