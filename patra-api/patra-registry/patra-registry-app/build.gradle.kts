/**
 * Patra Registry Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("patra.hexagonal-app")
}

dependencies {
    api(project(":patra-registry:patra-registry-domain"))
    api(project(":patra-registry:patra-registry-api"))
    api(project(":patra-spring-boot-starter-core"))

    // MapStruct
    api("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // Hibernate Validator
    api("org.hibernate.validator:hibernate-validator")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
