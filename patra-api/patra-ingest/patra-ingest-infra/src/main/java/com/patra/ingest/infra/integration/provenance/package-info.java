/// 数据源端口实现包 - Infrastructure 层桥接实现
///
/// **职责**: 实现 Domain 层的 {@link com.patra.ingest.domain.port.ProvenanceDataPort} 接口, 桥接到
/// Framework 层的 `patra-starter-provenance` 提供者实现。
///
/// ## 架构关系
///
/// ```
///
/// Domain Layer (ProvenanceDataPort) ← 定义契约
///     ↑ 依赖
/// Application Layer (GenericBatchExecutor) ← 使用契约
///     ↑ 实现
/// Infrastructure Layer (ProvenanceDataAdapter) ← 桥接实现（适配器模式）
///     ↓ 使用
/// Framework Layer (ProvenanceDataProvider, ProviderRegistry) ← 技术支撑（提供者模式）
///
/// ```
///
/// ## 核心转换逻辑
///
/// - **ExecutionContext + Batch → ProviderRequest**: 将领域层的执行上下文和批次定义转换为框架层请求
///   - **ProvenanceConfigSnapshot → ProvenanceConfig**: 将快照配置转换为运行时配置
///   - **ProviderResult → DataFetchResult**: 将框架层结果转换为领域层结果
///
/// ## 依赖边界
///
/// - **向上依赖**: Domain 层的 Port 接口、值对象和快照
///   - **向下依赖**: Framework 层的 ProviderRegistry、ProvenanceDataProvider
///   - **平级协作**: Application 层的 ProvenanceConfigConverter (如需要)
///
/// ## 设计原则
///
/// - 遵守六边形架构的依赖规则: Domain ← Infrastructure → Framework
///   - 不在此层添加业务逻辑,仅进行技术适配和类型转换
///   - 错误处理: 将框架层异常转换为领域层错误结果
///   - 日志记录: 记录关键转换过程和外部调用
///
/// ## 命名语义
///
/// - **ProvenanceDataAdapter** (Infrastructure 层): 适配器，连接 Domain Port 和 Framework Provider
///   - **ProvenanceDataProvider** (Framework 层): 提供者，提供数据源访问能力
///   - **ProvenanceDataPort** (Domain 层): 端口，定义数据源访问契约
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.infra.integration.provenance;
