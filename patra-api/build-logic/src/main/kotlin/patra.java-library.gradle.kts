/**
 * Patra Java Library Convention Plugin
 *
 * 用于库模块（非 Spring Boot 应用）的配置
 * 继承 java-base 的所有配置
 */

plugins {
    id("patra.java-base")
    `java-library`
}

// 库模块生成源码和 Javadoc JAR
java {
    withSourcesJar()
    withJavadocJar()
}

// Javadoc 配置
tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        charSet = "UTF-8"
        // 允许文档中使用 markdown
        addStringOption("Xdoclint:none", "-quiet")
    }
}
