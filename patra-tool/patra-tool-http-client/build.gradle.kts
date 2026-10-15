/**
 * patra-tool-http-client：RestClient、流式下载、隧道代理（原 rest-client）与 HTTP Interface 客户端（原 http-interface）。
 */
plugins {
    id("patra.tool-module")
    id("patra.spring-starter")
}

dependencies {
    api(project(":patra-tool:patra-tool-core"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-restclient")
    api("org.springframework:spring-web")
    api("tools.jackson.core:jackson-databind")
    api("org.apache.httpcomponents.client5:httpclient5")
    api("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    api("io.micrometer:micrometer-core")
    api(libs.commons.net)
    implementation(libs.hutool.core)
    compileOnly("org.springframework:spring-webflux")
    compileOnly("io.projectreactor.netty:reactor-netty-http")
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation("org.springframework:spring-webflux")
    testImplementation("io.projectreactor.netty:reactor-netty-http")
}

tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.75".toBigDecimal() } } }
}
