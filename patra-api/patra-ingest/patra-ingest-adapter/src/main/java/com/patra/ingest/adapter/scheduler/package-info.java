/**
 * XXL-Job 定时任务调度适配器包。
 *
 * <p>包含驱动适配器(Driving Adapter)组件,接收 XXL-Job
 * 外部调度触发器并将其转换为内部应用用例调用。作为六边形架构适配器层的一部分,处理外部调度系统到内部业务逻辑的转换。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>从 JSON 解析 XXL-Job 参数
 *   <li>验证任务参数
 *   <li>委托给 {@code PlanIngestionUseCase} 或其他编排器
 *   <li>处理适配器层错误映射
 *   <li>向 XXL-Job 管理端报告任务执行结果
 * </ul>
 *
 * <h2>设计模式</h2>
 *
 * 通过 {@link com.patra.ingest.adapter.scheduler.job.AbstractProvenanceScheduleJob}
 * 使用模板方法模式提供统一的任务执行流程。具体任务类只需:
 *
 * <ul>
 *   <li>定义来源代码(例如 PUBMED, EMBASE)
 *   <li>定义操作代码(例如 HARVEST, PARSE)
 *   <li>使用 {@code @XxlJob} 注解暴露 XXL-Job 入口点
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * 所有任务类必须以 {@code Job} 后缀结尾(例如 {@code PubmedHarvestJob})。
 *
 * <h2>示例</h2>
 *
 * <pre>{@code
 * @Component
 * public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {
 *     @Override
 *     protected ProvenanceCode getProvenanceCode() {
 *         return ProvenanceCode.PUBMED;
 *     }
 *
 *     @Override
 *     protected OperationCode getOperationCode() {
 *         return OperationCode.HARVEST;
 *     }
 *
 *     @XxlJob("pubmedHarvest")
 *     public void run() {
 *         executeScheduleJob(XxlJobHelper.getJobParam());
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.adapter.scheduler;
