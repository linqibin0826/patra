/**
 * Patra Object Storage Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-app")
}

dependencies {
    // 内部模块
    api(project(":patra-object-storage:patra-object-storage-domain"))
    api(project(":patra-common:patra-common-enums"))
    api(project(":linqibin-commons:linqibin-commons-core"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))

    // Spring 依赖
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-aspectj")

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
    testImplementation(project(path = ":patra-object-storage:patra-object-storage-domain", configuration = "testArtifacts"))
}
