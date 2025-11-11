/**
 * 数据源端口实现包 - Infrastructure 层桥接实现
 *
 * <p><b>职责</b>: 实现 Domain 层的 {@link com.patra.ingest.domain.port.DataSourcePort} 接口, 桥接到
 * Framework 层的 {@code patra-starter-provenance} 适配器实现。
 *
 * <h2>架构关系</h2>
 *
 * <pre>
 * Domain Layer (DataSourcePort) ← 定义契约
 *     ↑ 依赖
 * Application Layer (GenericBatchExecutor) ← 使用契约
 *     ↑ 实现
 * Infrastructure Layer (DataSourcePortAdapter) ← 桥接实现
 *     ↓ 使用
 * Framework Layer (DataSourceAdapter, AdapterRegistry) ← 技术支撑
 * </pre>
 *
 * <h2>核心转换逻辑</h2>
 *
 * <ul>
 *   <li><b>ExecutionContext + Batch → AdapterRequest</b>: 将领域层的执行上下文和批次定义转换为框架层请求
 *   <li><b>ProvenanceConfigSnapshot → ProvenanceConfig</b>: 将快照配置转换为运行时配置
 *   <li><b>AdapterResult → DataFetchResult</b>: 将框架层结果转换为领域层结果
 * </ul>
 *
 * <h2>依赖边界</h2>
 *
 * <ul>
 *   <li><b>向上依赖</b>: Domain 层的 Port 接口、值对象和快照
 *   <li><b>向下依赖</b>: Framework 层的 AdapterRegistry、DataSourceAdapter
 *   <li><b>平级协作</b>: Application 层的 ProvenanceConfigConverter (如需要)
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li>遵守六边形架构的依赖规则: Domain ← Infrastructure → Framework
 *   <li>不在此层添加业务逻辑,仅进行技术适配和类型转换
 *   <li>错误处理: 将框架层异常转换为领域层错误结果
 *   <li>日志记录: 记录关键转换过程和外部调用
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.infra.integration.datasource;
