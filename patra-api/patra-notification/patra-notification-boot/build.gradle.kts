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

    // 通用事件模块：消费 interaction、identity 经 RabbitMQ 发来的集成事件（去重表与登记表见 infra 的 db/migration/kernel）
    implementation(project(":patra-tool:patra-tool-events"))

    // identity 内部接口的代理（批量收件信息）：infra 对 identity-api、http-client 是 implementation，传不到这里；
    // 安全邮件队列也用它绑定 identity 的交换机与路由键（契约常量在 identity-api）
    implementation(project(":patra-tool:patra-tool-http-client"))
    implementation(project(":patra-api:patra-identity:patra-identity-api"))

    // 身份断言验签 → CurrentUserPort：偏好读写要知道是谁在操作
    implementation(project(":patra-tool:patra-tool-security"))

    // 架构测试要看到全部五层：domain、app 已经经 adapter、infra 传进来，api 还没有调用方
    testImplementation(project(":patra-api:patra-notification:patra-notification-api"))

    // 集成测试的 PG 容器初始化器
    testImplementation(testFixtures(project(":patra-api:patra-notification:patra-notification-infra")))
    // 流程测试用测试私钥签身份断言
    testImplementation(testFixtures(project(":patra-tool:patra-tool-security")))
}
