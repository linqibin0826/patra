/// XXL-Job 定时任务实现。
///
/// 包含具体的定时任务 Job 类，通过 @XxlJob 注解暴露给 XXL-Job 调度中心。
///
/// ## 职责
///
/// - **任务注册**：使用 @XxlJob 注解注册任务到 XXL-Job 调度中心
///   - **分布式锁控制**：使用 Redisson 确保同一任务同时只有一个实例执行
///   - **参数解析**：从 {@link com.xxl.job.core.context.XxlJobHelper} 获取任务参数
///   - **执行委派**：调用 App 层 Orchestrator 执行实际业务逻辑
///   - **日志上报**：通过 {@link com.xxl.job.core.context.XxlJobHelper#log} 上报执行日志
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.adapter.scheduler.job.MeshImportJob} - MeSH 数据导入定时任务
///
/// - 任务名称：`meshImport`
///       - 触发方式：手动触发 / CRON 表达式（由 XXL-Job 控制台配置）
///       - 分布式锁：`mesh:import:lock`（避免多实例并发）
///       - 锁超时：30 分钟（租约时间）
///
/// ## 设计原则
///
/// - **非阻塞锁获取**：使用 tryLock(0, leaseTime, unit)，无法获取锁时立即返回
///   - **资源释放**：在 finally 块中释放锁，避免死锁
///   - **幂等性**：任务支持重复执行，不会造成数据重复
///   - **失败重试**：依赖 XXL-Job 的重试机制，不在 Job 内部实现
///
/// ## 分布式锁策略
///
/// 当多个实例同时运行时，通过 Redisson 分布式锁确保同一时刻只有一个实例执行：
///
/// ```java
/// RLock lock = redissonClient.getLock("mesh:import:lock");
/// boolean acquired = lock.tryLock(0, 30, TimeUnit.MINUTES);
/// if (!acquired) {
///     log.warn("无法获取锁，跳过本次执行");
///     return; // 正常退出，不抛异常
/// }
/// try {
///     // 执行业务逻辑
///     orchestrator.startImport();
/// } finally {
///     if (lock.isHeldByCurrentThread()) {
///         lock.unlock();
///     }
/// }
/// ```
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：在 XXL-Job 控制台手动触发任务
/// // 任务名称：meshImport
/// // 执行参数：留空（使用配置文件默认值）
/// // 执行结果：查看 XXL-Job 执行日志
///
/// // 示例 2：配置 CRON 定时执行
/// // 任务名称：meshImport
/// // CRON 表达式：0 0 2 * * ?（每天凌晨2点执行）
/// // 执行器路由策略：轮询 / 故障转移
/// // 阻塞处理策略：单机串行（防止并发）
///
/// // 示例 3：失败重试配置
/// // 重试次数：3
/// // 重试间隔：60秒
/// ```
///
/// ## 异常处理
///
/// - **锁获取失败**：记录警告日志，正常退出（不抛异常，不触发 XXL-Job 重试）
///   - **业务执行失败**：抛出异常，XXL-Job 会标记为失败并根据配置重试
///   - **资源清理**：finally 块确保锁的释放
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.adapter.scheduler.job;
