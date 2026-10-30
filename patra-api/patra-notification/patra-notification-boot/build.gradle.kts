/**
 * Patra Notification Boot
 *
 * 启动层 - Spring Boot 应用入口、装配、XXL-Job 执行器
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.notification.PatraNotificationApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-notification:patra-notification-adapter"))
    implementation(project(":patra-api:patra-notification:patra-notification-infra"))

    implementation(project(":patra-tool:patra-tool-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":patra-tool:patra-tool-observability"))
    implementation(project(":patra-tool:patra-tool-openapi"))

    // XXL-Job 执行器（执行器端口 = 服务端口 + 1 = 6601）
    implementation(libs.xxl.job)

    // 架构测试要看到全部五层：domain、app、api 在后续 Issue 接上层间依赖之前不在运行时类路径上
    testImplementation(project(":patra-api:patra-notification:patra-notification-domain"))
    testImplementation(project(":patra-api:patra-notification:patra-notification-app"))
    testImplementation(project(":patra-api:patra-notification:patra-notification-api"))

    // 集成测试的 PG 容器初始化器
    testImplementation(testFixtures(project(":patra-api:patra-notification:patra-notification-infra")))
}
