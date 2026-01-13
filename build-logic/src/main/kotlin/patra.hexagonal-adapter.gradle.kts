/**
 * Patra Hexagonal Adapter Layer Convention Plugin
 *
 * 适配器层 - 入站适配器
 *
 * 职责：
 * - REST Controller
 * - 消息监听器
 * - 定时任务
 * - 请求/响应转换
 */

plugins {
    id("patra.spring-library")
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// 使用 Version Catalog (libs) 声明依赖
dependencies {
    // MapStruct
    implementation(libs.findLibrary("mapstruct").get())

    // Spring Transaction
    implementation("org.springframework:spring-tx")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
