/**
 * Patra Registry Infrastructure
 *
 * 基础设施层 - Repository 实现、外部服务集成
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-infra")
}

dependencies {
    api(project(":patra-registry:patra-registry-domain"))
    api(project(":patra-common:patra-common-enums"))
    api(project(":linqibin-commons:linqibin-commons-core"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))

    // MapStruct 由 patra.hexagonal-infra 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
