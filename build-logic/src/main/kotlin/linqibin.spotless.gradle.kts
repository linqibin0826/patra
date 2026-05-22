/**
 * Spotless Convention Plugin
 *
 * 应用范围：所有 Java 模块（通过 linqibin.java-base 间接 apply）。
 *
 * Java 格式化规则：
 * - googleJavaFormat 1.29.0（默认 2 空格缩进，与 .editorconfig 对齐）
 * - importOrder() 规范 import 顺序
 * - removeUnusedImports / trimTrailingWhitespace / endWithNewline
 * - 仅 src/**\/*.java，排除 build 与 generated 产物
 *
 * Gradle 脚本规则：
 * - kotlinGradle 覆盖根目录 + build-logic 下所有 .gradle.kts
 * - ktlint 1.5.0
 *
 * 关联：PAP-13 + 决策 C（spotlessCheck 阻断 PR）。
 */

plugins {
    java
    id("com.diffplug.spotless")
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

spotless {
    java {
        target("src/**/*.java")
        targetExclude("**/build/**", "**/generated/**")
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "build-logic/**/*.gradle.kts")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
