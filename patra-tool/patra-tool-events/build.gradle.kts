/**
 * patra-tool-events：跨服务事件。
 *
 * - 发布端：Spring Modulith 事件发布登记（JDBC）当 Outbox，官方 AMQP 外送到 RabbitMQ，发送时同步等 publisher confirm
 * - 补投：XXL-Job 任务重投未完成的登记行
 * - 消费端：processed_event 去重、死信队列声明、ack / 重试 / prefetch 默认值
 */

plugins {
    id("patra.tool-module")
    id("patra.spring-starter")
}

dependencies {
    // IntegrationEvent / ProcessedEventPort
    api(project(":patra-tool:patra-tool-core"))

    // RabbitMQ 收发、登记表与去重表的 JDBC
    api("org.springframework.boot:spring-boot-starter-amqp")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("tools.jackson.core:jackson-databind")

    // Modulith 事件：只要事件相关构件，不要 starter（会带进模块结构校验 spring-modulith-core）
    api("org.springframework.modulith:spring-modulith-events-api")
    implementation("org.springframework.modulith:spring-modulith-events-core")
    runtimeOnly("org.springframework.modulith:spring-modulith-events-jdbc")
    runtimeOnly("org.springframework.modulith:spring-modulith-events-jackson")
    runtimeOnly("org.springframework.modulith:spring-modulith-events-amqp")

    // 补投任务：服务有 XXL-Job 执行器时才注册
    compileOnly(libs.xxl.job)

    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation(libs.xxl.job)
    testImplementation("org.springframework.boot:spring-boot-starter-jackson")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
}
