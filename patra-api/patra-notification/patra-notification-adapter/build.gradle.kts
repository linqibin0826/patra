/**
 * Patra Notification Adapter
 *
 * 适配器层 - REST 接口、消息监听与 XXL-Job 任务处理器，按子域分包（kernel、inbox、preference、mail、realtime）
 *
 * project 依赖随第一个用到它的类一起加（R5：声明了就必须 import 到）
 */

plugins {
    id("patra.hexagonal-adapter")
}

dependencies {
    api(project(":patra-api:patra-notification:patra-notification-app"))

    api(project(":patra-tool:patra-tool-web"))

    api(libs.xxl.job)

    // @RabbitListener：信箱消费 interaction 的集成事件（PAP-143），邮件渠道消费 identity 的安全邮件事件（PAP-104）
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    // 事件 record：监听方法按它们反序列化
    implementation(project(":patra-api:patra-interaction:patra-interaction-api"))
    implementation(project(":patra-api:patra-identity:patra-identity-api"))
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
