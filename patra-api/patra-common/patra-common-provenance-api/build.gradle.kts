/**
 * Patra Common Provenance API
 *
 * Provenance API 常量定义：
 * - API endpoints (PubMed, EPMC, Crossref paths)
 * - Parameter keys
 * - Parameter values (类型安全的枚举)
 */

plugins {
    id("linqibin.java-library")
}

dependencies {
    // Jackson 用于枚举序列化
    api("com.fasterxml.jackson.core:jackson-annotations")

    // 测试依赖由 patra.java-library 插件提供
}
