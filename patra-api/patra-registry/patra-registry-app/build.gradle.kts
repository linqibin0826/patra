/**
 * Patra Registry Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("linqibin.hexagonal-app")
}

dependencies {
    api(project(":patra-registry:patra-registry-domain"))
    api(project(":patra-registry:patra-registry-api"))
    api(project(":linqibin-spring-boot-starter-core"))

    // MapStruct 由 patra.hexagonal-app 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // Hibernate Validator 由 patra.hexagonal-app 插件提供

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
