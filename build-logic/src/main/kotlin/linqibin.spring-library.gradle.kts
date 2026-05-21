/**
 * Patra Spring Library Convention Plugin
 *
 * 用于需要 Spring 依赖管理但不是 Boot 应用的模块
 * 如: Starter 模块、infra 层、adapter 层等
 *
 * 注意：虽然 patra.java-library 已经应用了 io.spring.dependency-management，
 * 但 BOM 配置需要在每个应用该插件的地方重新声明（Gradle 插件配置不自动继承）。
 * 这里通过 applyLinqibinDependencyManagement() 复用配置，确保一致性。
 */

plugins {
    id("linqibin.java-library")
    // 注意：patra.java-library -> patra.java-base 已经应用了此插件
    // 这里不需要重复应用
}

// 依赖管理配置已由 patra.java-base 通过 applyLinqibinDependencyManagement() 应用
// 无需重复配置
