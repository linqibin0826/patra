/**
 * Patra Registry Domain
 *
 * 领域层 - 纯 Java 业务逻辑
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-domain")
}

// 所有依赖由 patra.hexagonal-domain 插件提供:
// - patra-common-core (api)
// - Hutool, MapStruct
// - 测试依赖
