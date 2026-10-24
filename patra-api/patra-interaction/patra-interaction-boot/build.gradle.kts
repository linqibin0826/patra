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

    // 架构测试要看到全部五层：domain、app、api 在层间依赖接上之前不在运行时类路径上
    testImplementation(project(":patra-api:patra-interaction:patra-interaction-domain"))
    testImplementation(project(":patra-api:patra-interaction:patra-interaction-app"))
    testImplementation(project(":patra-api:patra-interaction:patra-interaction-api"))

    // 集成测试的 PG 容器初始化器
    testImplementation(testFixtures(project(":patra-api:patra-interaction:patra-interaction-infra")))
}
