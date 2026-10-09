/**
 * Patra Identity Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-boot")
}

springBoot {
    mainClass = "dev.linqibin.patra.identity.PatraIdentityApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-identity:patra-identity-adapter"))
    implementation(project(":patra-api:patra-identity:patra-identity-infra"))

    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-observability"))
    implementation(project(":linqibin-commons:linqibin-spring-boot-starter-openapi"))

    // 从网关签的断言取当前用户：过滤器链、CurrentUserPort、401 / 403 输出、JPA 审计人
    implementation(project(":patra-starters:patra-spring-boot-starter-security"))

    // 装配 RedisSessionStore
    implementation(project(":patra-api:patra-identity:patra-identity-session"))

    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))

    // 测试里自动注入测试公钥，并能签出带身份的请求头
    testImplementation(testFixtures(project(":patra-starters:patra-spring-boot-starter-security")))
}
