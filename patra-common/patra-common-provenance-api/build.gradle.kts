/**
 * Patra Common Provenance API
 *
 * Provenance API 常量定义：
 * - API endpoints (PubMed, EPMC, Crossref paths)
 * - Parameter keys
 * - Parameter values (类型安全的枚举)
 */

plugins {
    id("patra.java-library")
}

dependencies {
    // Jackson 用于枚举序列化
    api("com.fasterxml.jackson.core:jackson-annotations")

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
