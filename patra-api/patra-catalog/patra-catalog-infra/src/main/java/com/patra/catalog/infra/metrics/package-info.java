/// 监控指标包。
///
/// 使用 Micrometer 提供业务监控指标，支持 Prometheus、Grafana 可视化。
///
/// ## 职责
///
/// - **指标定义**：定义 MeSH 导入任务的自定义监控指标
///   - **指标记录**：在关键业务节点记录指标数据
///   - **Grafana 集成**：指标自动暴露给 Prometheus，供 Grafana 查询
///   - **实时监控**：提供任务进度、速度、成功率等实时监控数据
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.infra.metrics.MeshImportMetrics} - MeSH 导入自定义监控指标
///
/// - Timer：任务耗时（mesh.import.task.duration）
///       - Gauge：表级别进度（mesh.import.table.progress）
///       - Gauge：批次处理速度（mesh.import.batch.speed）
///       - Counter：成功任务数（mesh.import.task.success.count）
///       - Counter：失败任务数（mesh.import.task.failed.count）
///       - Counter：处理记录数（mesh.import.records.processed）
///
/// ## 指标类型说明
///
/// | 指标类型 | 用途 | 示例 | Grafana 查询 |
/// |---------|------|------|-------------|
/// | Timer | 记录耗时分布 | 任务总耗时 | histogram_quantile(0.95, mesh_import_task_duration) |
/// | Gauge | 记录动态变化的值 | 表进度百分比 | mesh_import_table_progress{table="descriptor"} |
/// | Counter | 累加计数 | 成功任务数 | rate(mesh_import_task_success_count[5m]) |
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：记录任务耗时
/// @Service
/// @RequiredArgsConstructor
/// public class MeshImportOrchestrator {
///
///     private final MeshImportMetrics metrics;
///
///     public void startImport() {
///         // 开始计时
///         Timer.Sample sample = metrics.startTaskTimer();
///
///         try {
///             // 执行导入逻辑...
///             doImport();
///
///             // 记录成功
///             metrics.recordTaskSuccess();
///             metrics.stopTaskTimer(sample, "SUCCESS");
///         } catch (Exception e) {
///             // 记录失败
///             metrics.recordTaskFailure("DOWNLOAD_ERROR");
///             metrics.stopTaskTimer(sample, "FAILED");
///             throw e;
///         }
///     }
/// }
///
/// // 示例 2：记录表级别进度
/// public void importDescriptors(Stream<MeshDescriptorAggregate> stream) {
///     AtomicInteger processedCount = new AtomicInteger(0);
///     int totalCount = 35000;
///
///     stream.forEach(descriptor -> {
///         // 保存到数据库...
///         repository.save(descriptor);
///
///         // 更新进度指标
///         int processed = processedCount.incrementAndGet();
///         double progress = (processed * 100.0) / totalCount;
///         metrics.updateTableProgress("descriptor", progress);
///     });
/// }
///
/// // 示例 3：记录批次处理速度
/// long startTime = System.currentTimeMillis();
/// int batchSize = 1000;
///
/// // 保存批次...
/// repository.batchSave(batch);
///
/// long endTime = System.currentTimeMillis();
/// double speed = batchSize * 1000.0 / (endTime - startTime);
/// metrics.recordBatchSpeed("descriptor", speed);
/// log.info("批次处理速度：{} 记录/秒", speed);
/// ```
///
/// ## Grafana 监控面板示例
///
/// ```promql
/// # 1. 任务成功率（过去 1 小时）
/// (
///   sum(increase(mesh_import_task_success_count[1h]))
///   /
///   sum(increase(mesh_import_task_success_count[1h]) + increase(mesh_import_task_failed_count[1h]))
/// ) * 100
///
/// # 2. 平均批次处理速度（按表分组）
/// avg by (table) (mesh_import_batch_speed)
///
/// # 3. 表级别进度（实时）
/// mesh_import_table_progress{table="descriptor"}
///
/// # 4. 任务耗时 P95（过去 5 分钟）
/// histogram_quantile(0.95, sum(rate(mesh_import_task_duration_bucket[5m])) by (le))
///
/// # 5. 失败任务数（按错误类型分组）
/// sum by (error_type) (increase(mesh_import_task_failed_count[1h]))
/// ```
///
/// ## 指标暴露端点
///
/// - **路径**：`/actuator/prometheus`
/// - **格式**：Prometheus 格式
/// - **示例输出**：
///
/// ```
/// # HELP mesh_import_task_duration MeSH 导入任务总耗时
/// # TYPE mesh_import_task_duration histogram
/// mesh_import_task_duration_bucket{status="SUCCESS",le="30.0"} 5
/// mesh_import_task_duration_bucket{status="SUCCESS",le="60.0"} 10
/// mesh_import_task_duration_sum{status="SUCCESS"} 250.5
/// mesh_import_task_duration_count{status="SUCCESS"} 15
///
/// # HELP mesh_import_table_progress MeSH 表级别进度百分比
/// # TYPE mesh_import_table_progress gauge
/// mesh_import_table_progress{table="descriptor"} 85.5
/// mesh_import_table_progress{table="qualifier"} 100.0
///
/// # HELP mesh_import_task_success_count MeSH 导入成功任务数
/// # TYPE mesh_import_task_success_count counter
/// mesh_import_task_success_count 42
/// ```
///
/// ## 设计原则
///
/// - **业务指标优先**：关注业务关键指标（成功率、进度、速度）
///   - **细粒度监控**：表级别、批次级别的监控数据
///   - **实时性**：Gauge 指标实时更新，支持实时监控
///   - **分类聚合**：支持按表名、错误类型等维度聚合
///
/// ## 架构位置
///
/// **Infrastructure 层 - 监控基础设施**：
///
/// - 提供技术支撑能力，不包含业务逻辑
/// - 被 App 层 Orchestrator 调用
/// - 指标数据暴露给 Prometheus/Grafana
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.infra.metrics;
