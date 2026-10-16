/**
 * Patra Identity Application
 *
 * 应用层 - 用例编排、事务边界
 */

plugins {
    id("patra.hexagonal-app")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-domain"))
    api(project(":patra-tool:patra-tool-core"))
    api(project(":patra-tool:patra-tool-cqrs"))
    api("org.springframework:spring-tx")
}
