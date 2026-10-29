/**
 * Patra Interaction Infrastructure
 *
 * 基础设施层 - 持久化与 Flyway 迁移，按子域分包（subject、collection、discussion、post、notification、reaction）；
 * 每个子域一个 schema，迁移脚本在 db/migration/{子域}/，整个服务一份 Flyway 历史
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    api(project(":patra-api:patra-interaction:patra-interaction-domain"))

    // 对象登记表向 catalog 解析文献（PAP-109 的内部接口）；http-client 带来 spring-web 的 ResourceAccessException
    implementation(project(":patra-api:patra-catalog:patra-catalog-api"))
    // 收藏夹所有者摘要、设公开的资格、公开收藏夹的可见性、注销清理（PAP-108 的内部接口）
    implementation(project(":patra-api:patra-identity:patra-identity-api"))
    implementation(project(":patra-tool:patra-tool-http-client"))
    // 提醒摘录：评论 Markdown 转纯文本（PAP-123）
    implementation(libs.commonmark)

    // JPA、Flyway、PostgreSQL 驱动（自动配置生效）
    api(project(":patra-tool:patra-tool-jpa"))

    // 集成测试的 PG 容器初始化器放 testFixtures，infra 与 boot 的集成测试共用
    testFixturesImplementation(project(":patra-tool:patra-tool-test"))
    // http-client 的属性类带 Bean Validation 约束：集成测试的上下文要有校验实现（同 identity-infra）
    integrationTestImplementation("org.springframework.boot:spring-boot-starter-validation")
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每层每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
