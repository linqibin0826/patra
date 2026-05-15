/**
 * Patra Java Library Convention Plugin
 *
 * 用于库模块（非 Spring Boot 应用）的配置
 * 继承 java-base 的所有配置，并提供基础测试依赖
 */

plugins {
    id("linqibin.java-base")
    `java-library`
    `maven-publish`
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// 库模块生成源码和 Javadoc JAR
java {
    withSourcesJar()
    withJavadocJar()
}

// Maven 发布配置：所有 java-library 模块默认可以 publishToMavenLocal
// 用于 sutra-app 等下游项目通过 mavenLocal() 消费 patra starter 与 common
//
// versionMapping：patra 用 io.spring.dependency-management 注入 BOM，dependencies 声明无版本号；
// 发布时必须 resolve 出实际版本写进 pom，否则下游 mavenLocal 引用会报 "no version"
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            versionMapping {
                usage("java-api") { fromResolutionOf("runtimeClasspath") }
                usage("java-runtime") { fromResolutionResult() }
            }
        }
    }
}

// Javadoc 配置
tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        charSet = "UTF-8"
        // 允许文档中使用 markdown，禁用所有 doclint 检查
        addStringOption("Xdoclint:none", "-quiet")
    }
}

// 基础测试依赖（所有库模块通用）
// 使用 Version Catalog (libs) 声明依赖
dependencies {
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testImplementation(libs.findLibrary("mockito-core").get())
    testImplementation(libs.findLibrary("mockito-junit-jupiter").get())
}
