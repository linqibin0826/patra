// Dependency Management Convention Plugin
// 应用统一依赖管理（BOM 导入 + 强制版本 + 安全版本覆盖），实现见 LinqibinDependencyManagement.kt。
// 由 linqibin.java-base 与根项目共用：根项目的 jacoco-report-aggregation 会解析全部子项目运行时依赖，
// 两处规则必须一致，否则聚合配置解析出的版本与实际构建不符（依赖图提交也会据此误报漏洞）。
//
// 知名 Gradle 9.5 bug：precompiled script plugin 顶层 /** */ 注释会破坏 body 执行，统一用单行 // 注释。

plugins {
    id("io.spring.dependency-management")
}

applyLinqibinDependencyManagement(the<VersionCatalogsExtension>().named("libs"))
