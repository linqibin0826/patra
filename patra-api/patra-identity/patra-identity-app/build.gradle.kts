/**
 * Patra Identity Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-app")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-domain"))
    api(project(":linqibin-commons:linqibin-commons-core"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))
    api("org.springframework:spring-tx")
}
