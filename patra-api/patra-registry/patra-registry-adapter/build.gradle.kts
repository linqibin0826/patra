/**
 * Patra Registry Adapter
 *
 * 适配器层 - Controller、Job、消息处理器
 */

plugins {
    id("patra.hexagonal-adapter")
}

dependencies {
    api(project(":patra-registry:patra-registry-app"))
    api(project(":patra-registry:patra-registry-api"))
    api(project(":patra-spring-boot-starter-web"))

    // MapStruct
    api("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // Spring DAO exception hierarchy
    api("org.springframework:spring-tx")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
