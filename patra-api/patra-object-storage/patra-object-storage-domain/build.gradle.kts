/**
 * Patra Object Storage Domain
 *
 * 领域层 - 纯 Java 业务逻辑
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("patra.hexagonal-domain")
}

// 所有依赖由 patra.hexagonal-domain 插件提供:
// - patra-common-core (api)
// - Hutool, MapStruct
// - 测试依赖
//
// 跨模块共享 fixture 通过 src/testFixtures/ source set 暴露
// （patra.java-base 已 apply java-test-fixtures plugin）。
// 消费方用 testImplementation(testFixtures(project(":..."))) 引用。
