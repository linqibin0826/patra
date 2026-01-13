/**
 * Patra Object Storage Adapter
 *
 * 适配器层 - Controller、消息处理器
 */

plugins {
    id("patra.hexagonal-adapter")
}

dependencies {
    // 内部模块
    api(project(":patra-object-storage:patra-object-storage-app"))
    api(project(":patra-object-storage:patra-object-storage-api"))
    api(project(":patra-spring-boot-starter-web"))

    // Spring DAO exception hierarchy
    api("org.springframework:spring-tx")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
