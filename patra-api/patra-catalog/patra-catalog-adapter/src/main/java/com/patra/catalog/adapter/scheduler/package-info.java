/// 定时任务包（根包）。
///
/// 包含定时调度相关的所有组件，使用 XXL-Job 实现分布式任务调度。
///
/// ## 职责
///
/// - **暴露调度入口**：提供 XXL-Job 可调度的任务入口点
///   - **分布式协调**：使用 Redisson 分布式锁避免并发执行
///   - **调用编排器**：委派业务逻辑到 App 层 Orchestrator
///   - **日志记录**：记录任务执行日志到 XXL-Job 控制台
///
/// ## 架构位置
///
/// **Adapter 层 - 调度适配器**：
///
/// - 六边形架构的入站适配器（Inbound Adapter）
/// - 将定时调度信号转换为领域操作调用
/// - 与 REST 适配器地位相同，只是触发方式不同
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.adapter.scheduler;
