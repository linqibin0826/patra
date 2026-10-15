/**
 * patra-tool-cqrs：命令总线接口、SimpleCommandBus、三个内置拦截器与自动配置。
 */
plugins {
    id("patra.boundary-check")
    id("patra.spring-starter")
}

dependencies {
    api(project(":patra-tool:patra-tool-core"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework:spring-context")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.micrometer:micrometer-observation")
    testImplementation(project(":patra-tool:patra-tool-test"))
}
