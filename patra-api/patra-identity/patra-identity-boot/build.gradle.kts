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

    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
