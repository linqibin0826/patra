/**
 * Patra Catalog Domain
 *
 * 领域层 - 纯 Java 业务逻辑
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-domain")
}

// patra-common-core 由 patra.hexagonal-domain 插件提供
dependencies {
    api(project(":patra-api:patra-common:patra-common-model"))
}
