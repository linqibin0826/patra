/**
 * Patra Hexagonal Infrastructure Layer Convention Plugin
 *
 * 基础设施层 - 技术实现
 *
 * 职责：
 * - Repository 实现 (JPA)
 * - 外部服务客户端 (HTTP Interface)
 * - 消息队列适配器
 * - 缓存实现
 */

plugins {
    id("patra.spring-library")
}

dependencies {
    // MapStruct
    implementation("org.mapstruct:mapstruct:1.6.3")

    // 常用的基础设施依赖由具体模块声明
    // 如: spring-data-jpa, http-interface 等

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
