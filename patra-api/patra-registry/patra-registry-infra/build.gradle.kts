/**
 * Patra Registry Infrastructure
 *
 * 基础设施层 - Repository 实现、外部服务集成
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    api(project(":patra-api:patra-registry:patra-registry-domain"))
    api(project(":patra-tool:patra-tool-jpa"))

    // MapStruct 由 patra.hexagonal-infra 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
