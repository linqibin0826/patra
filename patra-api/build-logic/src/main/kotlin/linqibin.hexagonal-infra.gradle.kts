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
    id("linqibin.spring-library")
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// 使用 Version Catalog (libs) 声明依赖
dependencies {
    // MapStruct
    implementation(libs.findLibrary("mapstruct").get())

    // 常用的基础设施依赖由具体模块声明
    // 如: spring-data-jpa, http-interface 等

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
