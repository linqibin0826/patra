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
    // JPA、Flyway、PostgreSQL 驱动（自动配置生效）
    api(project(":patra-tool:patra-tool-jpa"))

    // 集成测试的 PG 容器初始化器放 testFixtures，infra 与 boot 的集成测试共用
    testFixturesImplementation(project(":patra-tool:patra-tool-test"))
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
