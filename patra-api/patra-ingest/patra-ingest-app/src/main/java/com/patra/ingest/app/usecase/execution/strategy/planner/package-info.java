/**
 * 批次规划器包。
 *
 * <p>本包提供不同数据源的批次规划策略，根据数据源特性生成批次执行计划。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>根据数据源策略规划批次（如 PubMed、EPMC、Crossref）
 *   <li>计算批次大小和偏移量
 *   <li>处理分页参数（offset-based 或 cursor-based）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code BatchPlanner} - 批次规划器接口
 *   <li>{@code BatchPlannerRegistry} - 批次规划器注册表
 *   <li>{@code PubmedBatchPlanner} - PubMed 批次规划器
 *       <ul>
 *         <li>使用 offset-based 分页（retstart + retmax）
 *       </ul>
 * </ul>
 *
 * <h2>批次规划策略</h2>
 * <table border="1">
 *   <tr>
 *     <th>数据源</th>
 *     <th>实现类</th>
 *     <th>分页方式</th>
 *     <th>批次大小</th>
 *   </tr>
 *   <tr>
 *     <td>PubMed</td>
 *     <td>PubmedBatchPlanner</td>
 *     <td>Offset-based</td>
 *     <td>10000（retmax 参数）</td>
 *   </tr>
 *   <tr>
 *     <td>EPMC</td>
 *     <td>EpmcBatchPlanner</td>
 *     <td>Cursor-based</td>
 *     <td>1000（pageSize 参数）</td>
 *   </tr>
 *   <tr>
 *     <td>Crossref</td>
 *     <td>CrossrefBatchPlanner</td>
 *     <td>Offset-based</td>
 *     <td>5000（rows 参数）</td>
 *   </tr>
 * </table>
 *
 * <h2>分页方式对比</h2>
 * <h3>Offset-based（如 PubMed）</h3>
 * <ul>
 *   <li><strong>优点</strong>: 简单、可预测
 *   <li><strong>缺点</strong>: 大偏移量性能差、数据变化可能导致漏数据
 *   <li><strong>示例</strong>: {@code retstart=0&retmax=10000}
 * </ul>
 *
 * <h3>Cursor-based（如 EPMC）</h3>
 * <ul>
 *   <li><strong>优点</strong>: 性能稳定、不受数据变化影响
 *   <li><strong>缺点</strong>: 无法跳页、cursor 有时效性
 *   <li><strong>示例</strong>: {@code cursorMark=abc123&pageSize=1000}
 * </ul>
 *
 * <h2>使用示例</h2>
 * <h3>PubMed 批次规划器</h3>
 * <pre>{@code
 * @Component
 * public class PubmedBatchPlanner implements BatchPlanner {
 *
 *     @Override
 *     public String getProvenanceCode() {
 *         return "pubmed";
 *     }
 *
 *     @Override
 *     public BatchPlan plan(ExecutionContext context) {
 *         // 1. 获取总数（通过 ESearch API）
 *         var totalCount = fetchTotalCount(context);
 *
 *         // 2. 计算批次
 *         var batches = new ArrayList<Batch>();
 *         var batchSize = 10000;  // retmax 上限
 *         var offset = 0;
 *         int seq = 1;
 *
 *         while (offset < totalCount) {
 *             var currentBatchSize = Math.min(batchSize, totalCount - offset);
 *
 *             batches.add(Batch.builder()
 *                 .seq(seq++)
 *                 .offset(offset)
 *                 .batchSize(currentBatchSize)
 *                 .params(Map.of(
 *                     "retstart", String.valueOf(offset),
 *                     "retmax", String.valueOf(currentBatchSize)
 *                 ))
 *                 .build());
 *
 *             offset += currentBatchSize;
 *         }
 *
 *         return new BatchPlan(batches, totalCount);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.strategy.planner;
