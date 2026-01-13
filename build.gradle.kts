/**
 * Patra - Root Build Script
 *
 * 根项目只负责聚合和全局任务定义
 * 实际构建逻辑在 build-logic Convention Plugins 中
 */

plugins {
    base
}

// ==================== Project Info ====================
allprojects {
    group = property("patraGroup") as String
    version = property("patraVersion") as String
}

// ==================== Global Tasks ====================

// 格式化所有 Java 代码
tasks.register("formatAll") {
    description = "Format all Java source code with Google Java Format"
    group = "formatting"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("spotlessApply") })
}

// 检查所有代码格式
tasks.register("checkFormat") {
    description = "Check all Java source code formatting"
    group = "verification"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("spotlessCheck") })
}

// 运行所有测试（包括集成测试）
tasks.register("testAll") {
    description = "Run all tests including integration tests"
    group = "verification"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("check") })
}

// 生成所有 JaCoCo 报告
tasks.register("jacocoReportAll") {
    description = "Generate JaCoCo coverage reports for all modules"
    group = "reporting"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("jacocoTestReport") })
}

// 清理所有模块
tasks.register("cleanAll") {
    description = "Clean all modules"
    group = "build"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}
