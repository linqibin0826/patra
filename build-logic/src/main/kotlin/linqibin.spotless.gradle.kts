// Spotless Convention Plugin
// 应用于所有 Java 模块（通过 linqibin.java-base 间接 apply）。
// Java：googleJavaFormat 1.29.0（默认 2 空格缩进，与 .editorconfig 对齐）+ importOrder +
//   removeUnusedImports + trimTrailingWhitespace + endWithNewline；
//   targetExclude build/ + generated/ 不参与格式化（MapStruct / annotation processor 产物）。
// Gradle 脚本：kotlinGradle 覆盖根 + build-logic/**/*.gradle.kts，ktlint 1.5.0。
// 关联：PAP-13 + 决策 C（spotlessCheck 阻断 PR）。
//
// 知名 Gradle 9.5 bug：precompiled script plugin 顶层 /** */ 注释、tasks.named()、
// afterEvaluate{}、providers.gradleProperty().get() 都会让 plugin body 静默不执行
// （编译产物只有 constructor + main，配置代码被丢弃）。统一用单行 // 注释规避。

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
