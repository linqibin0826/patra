/**
 * Patra Spring Boot Starter - Security
 *
 * 基于 Spring Security 的当前用户识别（只做 servlet 一套）：
 * - 从网关传来的身份头建立认证
 * - 401 / 403 输出为统一的 ProblemDetail
 * - JPA 审计人接入
 *
 * Spring Security 只经本模块进入 classpath：只有需要识别用户的服务才引它。
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.spring-boot-starter")
}

dependencies {
    // 当前用户的抽象
    api(project(":patra-api:patra-common:patra-common-security"))

    // 统一错误格式（ProblemDetailAdapter、GlobalRestExceptionHandler）
    api(project(":linqibin-commons:linqibin-spring-boot-starter-web"))

    // Spring Security（版本由 Spring Boot BOM 管理）
    api("org.springframework.boot:spring-boot-starter-security")

    // 审计人接入：classpath 上有 starter-jpa 时才生效
    compileOnly(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))

    // 测试支持（testFixtures）：没有它，使用方的 @WebMvcTest 里没有安全过滤器
    "testFixturesApi"("org.springframework.boot:spring-boot-security-test")

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))

    // 集成测试要测审计列，需要 starter-jpa；单元测试的 classpath 故意不带它
    "integrationTestImplementation"(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))
}
