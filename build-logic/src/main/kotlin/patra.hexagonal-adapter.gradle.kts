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

dependencies {
    // MapStruct
    implementation("org.mapstruct:mapstruct:1.6.3")

    // Spring Transaction
    implementation("org.springframework:spring-tx")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
