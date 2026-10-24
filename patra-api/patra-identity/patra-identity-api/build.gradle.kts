/**
 * Patra Identity API
 *
 * 服务契约层 - 内部 HTTP Interface 与 DTO：查会话（网关调，PAP-106）、批量用户摘要（interaction 调，PAP-108）
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
