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

    // 架构测试要看到全部五层：domain、app 已经经 adapter、infra 传进来，api 还没有调用方
    testImplementation(project(":patra-api:patra-interaction:patra-interaction-api"))

    // 集成测试的 PG 容器初始化器
    testImplementation(testFixtures(project(":patra-api:patra-interaction:patra-interaction-infra")))
}
