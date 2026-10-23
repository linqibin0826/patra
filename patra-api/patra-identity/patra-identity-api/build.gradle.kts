/**
 * Patra Identity API
 *
 * 服务契约层 - 查会话的内部 HTTP Interface 与 DTO（网关调，PAP-106）
 */

plugins {
    id("patra.hexagonal-api")
}

dependencies {
    // @HttpExchange 注解（provided scope）
    compileOnly("org.springframework:spring-web")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
