/**
 * Catalog 领域事件包。
 *
 * <p>包含 Catalog 领域的所有领域事件（Domain Event），用于发布聚合根状态变更和触发后续业务流程。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>记录已发生的事实：领域事件表示过去发生的业务事实（使用过去时命名）
 *   <li>解耦聚合根：通过事件实现聚合根之间的松耦合通信
 *   <li>支持最终一致性：跨聚合操作通过领域事件实现最终一致性
 *   <li>触发后续流程：领域事件可触发下游业务逻辑（如通知、审计、索引更新）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.patra.catalog.domain.event.MeshImportStarted} - MeSH 导入任务启动事件
 *     <ul>
 *       <li>发布时机：任务从 PENDING 转换为 PROCESSING 状态时</li>
 *       <li>包含信息：任务 ID、数据源 URL、开始时间</li>
 *       <li>用途：记录任务启动时间、通知监控系统、触发资源预留</li>
 *     </ul>
 *   </li>
 *   <li>{@link com.patra.catalog.domain.event.MeshImportCompleted} - MeSH 导入任务完成事件
 *     <ul>
 *       <li>发布时机：所有表导入完成，任务状态变为 SUCCESS</li>
 *       <li>包含信息：任务 ID、总记录数、耗时（秒）、完成时间</li>
 *       <li>用途：触发索引构建、通知管理员、更新统计数据</li>
 *     </ul>
 *   </li>
 *   <li>{@link com.patra.catalog.domain.event.MeshImportFailed} - MeSH 导入任务失败事件
 *     <ul>
 *       <li>发布时机：任务失败，状态变为 FAILED</li>
 *       <li>包含信息：任务 ID、失败原因、已处理记录数、失败时间</li>
 *       <li>用途：触发告警、记录错误日志、通知管理员</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>过去时命名</b>：领域事件表示已发生的事实，使用过去时命名（如 Started、Completed、Failed）
 *   <li><b>不可变性</b>：领域事件一旦创建不可修改，使用 {@code record} 实现不可变性
 *   <li><b>自包含</b>：领域事件包含所有必要的上下文信息，避免事件处理时查询数据库
 *   <li><b>业务语义</b>：领域事件表达业务含义，而非技术细节（如 "订单已支付" 而非 "订单状态已更新"）
 *   <li><b>单一职责</b>：每个事件表示一个业务事实，不混合多个事件
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例 1：聚合根发布领域事件
 * public class MeshImportAggregate extends AggregateRoot<MeshImportId> {
 *
 *     public void startImport() {
 *         // 状态转换
 *         this.status = MeshImportTaskStatus.PROCESSING;
 *         this.startTime = Instant.now();
 *
 *         // 发布领域事件
 *         addDomainEvent(new MeshImportStarted(this.getId(), this.sourceUrl, this.startTime));
 *     }
 *
 *     public void markAsCompleted() {
 *         this.status = MeshImportTaskStatus.SUCCESS;
 *         this.endTime = Instant.now();
 *         long elapsedSeconds = Duration.between(startTime, endTime).getSeconds();
 *
 *         // 发布领域事件
 *         addDomainEvent(
 *             new MeshImportCompleted(this.getId(), this.totalRecords, elapsedSeconds, this.endTime)
 *         );
 *     }
 * }
 *
 * // 示例 2：Application 层处理领域事件
 * @Transactional
 * public MeshImportResultDTO startImport(StartImportCommand command) {
 *     MeshImportAggregate aggregate = createPendingTask();
 *
 *     // 状态转换（发布事件）
 *     aggregate.startImport(); // 发布 MeshImportStarted 事件
 *
 *     // 保存聚合根（事件会在事务提交后发布）
 *     aggregate = meshImportPort.save(aggregate);
 *
 *     // ... 执行导入逻辑
 *
 *     aggregate.markAsCompleted(); // 发布 MeshImportCompleted 事件
 *     meshImportPort.save(aggregate);
 *
 *     return buildSuccessResult(aggregate);
 * }
 *
 * // 示例 3：事件监听器处理领域事件
 * @Component
 * public class MeshImportEventListener {
 *
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void onMeshImportCompleted(MeshImportCompleted event) {
 *         log.info("MeSH 导入任务完成：任务ID={}，总记录数={}，耗时={}秒",
 *             event.importId(), event.totalRecords(), event.elapsedSeconds());
 *
 *         // 触发后续流程
 *         // 1. 构建 Elasticsearch 索引
 *         // 2. 发送完成通知
 *         // 3. 更新统计数据
 *     }
 *
 *     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 *     public void onMeshImportFailed(MeshImportFailed event) {
 *         log.error("MeSH 导入任务失败：任务ID={}，失败原因={}",
 *             event.importId(), event.failureReason());
 *
 *         // 触发告警
 *         alertService.sendAlert("MeSH 导入失败", event.failureReason());
 *     }
 * }
 * }</pre>
 *
 * @since 0.2.0
 * @author Patra Team
 */
package com.patra.catalog.domain.event;
