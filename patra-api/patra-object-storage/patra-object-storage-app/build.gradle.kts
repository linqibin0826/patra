/**
 * Patra Object Storage Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("patra.hexagonal-app")
}

dependencies {
    implementation(libs.hutool.core)
    // 内部模块
    api(project(":patra-api:patra-object-storage:patra-object-storage-domain"))
    api(project(":patra-tool:patra-tool-cqrs"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation(testFixtures(project(":patra-api:patra-object-storage:patra-object-storage-domain")))
}
