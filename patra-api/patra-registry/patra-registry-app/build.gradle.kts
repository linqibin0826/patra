/**
 * Patra Registry Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("patra.hexagonal-app")
}

dependencies {
    api(project(":patra-api:patra-registry:patra-registry-domain"))
    api(project(":patra-api:patra-registry:patra-registry-api"))
    api(project(":patra-tool:patra-tool-core"))
    implementation("org.springframework.boot:spring-boot-starter")

    // MapStruct 由 patra.hexagonal-app 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // Hibernate Validator 由 patra.hexagonal-app 插件提供

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
}
