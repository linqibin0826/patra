/**
 * Patra Registry Infrastructure
 *
 * 基础设施层 - Repository 实现、外部服务集成
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    api(project(":patra-registry:patra-registry-domain"))
    api(project(":patra-common:patra-common-core"))
    api(project(":patra-spring-boot-starter-core"))
    api(project(":patra-spring-boot-starter-jpa"))

    // MapStruct
    api("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
