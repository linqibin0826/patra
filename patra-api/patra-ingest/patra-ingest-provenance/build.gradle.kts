/**
 * patra-ingest-provenance：ingest 对 PubMed / EPMC / Crossref 的客户端、处理器与 API 常量。
 */
plugins {
    id("patra.spring-starter")
}

dependencies {
    api(project(":patra-api:patra-common"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-jackson")
    api("org.springframework:spring-web")
    api("tools.jackson.core:jackson-databind")
    api("tools.jackson.dataformat:jackson-dataformat-xml")
    api(libs.hutool.core)
    api("org.slf4j:slf4j-api")
    compileOnly("io.micrometer:micrometer-core")
    // 自动配置里的 PubMedClient / EPMCClient 注入 http-client 出的 defaultRestClient bean，编译期不引用它的类
    runtimeOnly(project(":patra-tool:patra-tool-http-client"))
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation("org.springframework.boot:spring-boot-micrometer-metrics")
}
