package dev.linqibin.patra.ingest.domain.strategy;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.model.vo.batch.Batch;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionContext;
import dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession;
import java.util.List;

/// 批次生成策略接口
///
/// 职责：定义如何根据查询会话生成批次列表
///
/// 设计原则：
///
/// - 策略模式：每个数据源对应一个策略实现
///   - 开闭原则：新增数据源无需修改 UnifiedBatchScheduleBuilder
///   - 单一职责：每个策略类只处理一种数据源的批次生成
///
/// 使用场景：
///
/// - UnifiedBatchScheduleBuilder 根据数据源代码选择对应策略
///   - 通过 Spring 自动注入所有策略实现
///   - 使用 Map 进行策略路由（基于数据源代码）
///
/// **设计要点**：
///
/// - {@link #getSupportedProvenanceCode()} 方法消除硬编码类型列表
///   - 策略类自己声明支持的 Provenance 代码，完全符合开闭原则
///   - 新增数据源时无需修改任何现有代码
///   - 解耦外部实现：不依赖 Provenance Starter 的具体类型
///
/// @author linqibin
/// @since 0.1.0
public interface BatchGenerationStrategy {

  /// 获取策略支持的 Provenance 代码
  ///
  /// 此方法允许策略类自己声明支持的 Provenance，避免了在 UnifiedBatchScheduleBuilder 中硬编码。
  ///
  /// **设计理由**：
  ///
  /// - 消除硬编码：无需在 UnifiedBatchScheduleBuilder 中维护已知类型列表
  ///   - 完全符合 OCP：新增数据源零修改
  ///   - 类型安全：使用枚举而非字符串，编译期检查
  ///   - 自动发现：Spring 启动时自动构建策略 Map
  ///
  /// @return 支持的 Provenance 代码枚举（如 PUBMED, DOAJ, EPMC）
  /// @example
  ///     ```java
  /// @Override
  /// public ProvenanceCode getSupportedProvenanceCode() {
  ///     return ProvenanceCode.PUBMED;
  /// ```
  ///
  /// @since 0.1.0
  ProvenanceCode getSupportedProvenanceCode();

  /// 根据查询会话生成批次列表
  ///
  /// 批次生成规则由具体策略实现，可能包括：
  ///
  /// - 根据总记录数和批次大小计算批次数量
  ///   - 设置每个批次的起始偏移量和大小
  ///   - 附加状态令牌（opaque，不解析其内容）
  ///
  /// @param session 查询会话（领域模型）
  /// @param ctx 执行上下文（包含配置信息）
  /// @return 批次列表
  List<Batch> generateBatches(QuerySession session, ExecutionContext ctx);
}
