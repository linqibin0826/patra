/**
 * Patra Notification Infrastructure
 *
 * 基础设施层 - 持久化与 Flyway 迁移，按子域分包（kernel、inbox、preference、mail、realtime）；
 * 每个子域一个 schema，迁移脚本在 db/migration/{子域}/，整个服务一份 Flyway 历史
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    // 实现领域层的仓储与读端口
    api(project(":patra-api:patra-notification:patra-notification-domain"))

    // identity 内部接口的契约（批量收件信息）与 HTTP Interface 的错误类型；代理在 boot 里装配
    implementation(project(":patra-api:patra-identity:patra-identity-api"))
    implementation(project(":patra-tool:patra-tool-http-client"))
    // interaction 内部接口的契约（对象视图与经验结果，列表与邮件读时现查）；代理在 boot 里装配
    implementation(project(":patra-api:patra-interaction:patra-interaction-api"))

    // JPA、Flyway、PostgreSQL 驱动（自动配置生效）
    api(project(":patra-tool:patra-tool-jpa"))

    // 发信：Boot 的 JavaMailSender 自动配置 + Jakarta Mail。用 api：boot 的装配类签名里要用 JavaMailSender
    api("org.springframework.boot:spring-boot-starter-mail")

    // 邮件模板：只要 Thymeleaf 引擎本体，不用 starter——通知服务的邮件不走 MVC 视图解析器
    implementation("org.thymeleaf:thymeleaf")

    // 集成测试的 PG 容器初始化器放 testFixtures，infra 与 boot 的集成测试共用
    testFixturesImplementation(project(":patra-tool:patra-tool-test"))
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
