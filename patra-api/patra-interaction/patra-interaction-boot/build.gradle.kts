/**
 * Patra Interaction Boot
 *
 * 启动层 - Spring Boot 应用入口、装配、XXL-Job 执行器
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.interaction.PatraInteractionApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-interaction:patra-interaction-adapter"))
    implementation(project(":patra-api:patra-interaction:patra-interaction-infra"))

    implementation(project(":patra-tool:patra-tool-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":patra-tool:patra-tool-observability"))
    implementation(project(":patra-tool:patra-tool-openapi"))

    // XXL-Job 执行器（执行器端口 = 服务端口 + 1 = 6501）
    implementation(libs.xxl.job)

    // catalog 内部接口的 HTTP Interface 代理（对象登记表解析文献）：infra 对 catalog-api 是 implementation，传不到这里
    implementation(project(":patra-tool:patra-tool-http-client"))
    implementation(project(":patra-api:patra-catalog:patra-catalog-api"))

    // identity 内部接口的代理（批量用户摘要），同上：infra 对 identity-api 是 implementation
    implementation(project(":patra-api:patra-identity:patra-identity-api"))

    // 身份断言验签 → CurrentUserPort：文献库、收藏夹的接口要知道是谁在操作
    implementation(project(":patra-tool:patra-tool-security"))

    // 集成事件外送（PAP-141）：Modulith 事件发布登记当 Outbox，提交后外送到 RabbitMQ 并等确认；
    // 映射成 api 模块里的集成事件 record
    implementation(project(":patra-tool:patra-tool-events"))
    implementation(project(":patra-api:patra-interaction:patra-interaction-api"))

    // 集成测试的 PG 容器初始化器
    testImplementation(testFixtures(project(":patra-api:patra-interaction:patra-interaction-infra")))
    // 流程测试用测试私钥签身份断言
    testImplementation(testFixtures(project(":patra-tool:patra-tool-security")))
}
