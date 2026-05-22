// SpotBugs Convention Plugin
// 应用于所有 Java 模块（通过 linqibin.java-base 间接 apply）。
// 配置：effort=MAX / reportLevel=LOW / ignoreFailures=false / toolVersion=4.9.3。
// 只跑 spotbugsMain，禁用 spotbugsTest / spotbugsIntegrationTest / spotbugsE2eTest 等。
// excludeFilter 沿用根目录 spotbugs-exclude.xml。HTML + XML 双报告。
// 关联：PAP-12 + 决策 C（spotbugsTest 不阻断）。
//
// 知名 Gradle 9.5 bug：precompiled script plugin 顶层 /** */ 注释、tasks.named()、
// afterEvaluate{}、providers.gradleProperty().get() 都会让 plugin body 静默不执行
// （编译产物只有 constructor + main，配置代码被丢弃）。统一用单行 // 注释规避。

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    java
    id("com.github.spotbugs")
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

spotbugs {
    toolVersion = libs.findVersion("spotbugs").get().requiredVersion
    effort = Effort.MAX
    reportLevel = Confidence.LOW
    ignoreFailures = false
    excludeFilter = rootProject.file("spotbugs-exclude.xml")
}

// 决策 C + PAP-12 AC：不分析 test 代码。
// 用 lazy filter + configureEach，覆盖所有 non-main spotbugs task。
// 原因：spotbugsIntegrationTest / spotbugsE2eTest 由 JVM Test Suites 注册的 sourceSet
// 派生而来，在本 plugin body 执行时尚未注册，tasks.named() 会抛 UnknownDomainObjectException。
// matching + configureEach 是 lazy，对未来注册的 task 也生效。
tasks.matching { it.name.startsWith("spotbugs") && it.name != "spotbugsMain" }
    .configureEach { enabled = false }

// 仅对 spotbugsMain 配置 HTML + XML 双报告
tasks.named<SpotBugsTask>("spotbugsMain") {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}
