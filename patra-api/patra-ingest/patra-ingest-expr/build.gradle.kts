/**
 * patra-ingest-expr：表达式 AST（原 expr-kernel）与编译器（原 starter-expr）。
 */
plugins {
    id("patra.spring-starter")
}

dependencies {
    api(project(":patra-api:patra-common"))
    api(project(":patra-tool:patra-tool-core"))
    api(project(":patra-api:patra-registry:patra-registry-api"))
    implementation(project(":patra-api:patra-ingest:patra-ingest-domain"))
    api("org.springframework.boot:spring-boot-starter")
    api("tools.jackson.core:jackson-databind")
    compileOnly("io.micrometer:micrometer-core")
    testImplementation(project(":patra-tool:patra-tool-test"))
}
