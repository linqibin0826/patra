/**
 * Patra - Root Build Script
 *
 * 根项目只负责聚合和项目信息定义
 * 实际构建逻辑在 build-logic Convention Plugins 中
 *
 * 常用命令：
 * - ./gradlew clean              清理所有模块
 * - ./gradlew build              构建所有模块
 * - ./gradlew check              运行所有检查（测试 + 代码质量）
 * - ./gradlew spotlessApply      格式化所有代码
 * - ./gradlew spotlessCheck      检查代码格式
 * - ./gradlew jacocoTestReport   生成覆盖率报告
 */

plugins {
    base
}

// ==================== Project Info ====================
allprojects {
    group = property("patraGroup") as String
    version = property("patraVersion") as String
}
