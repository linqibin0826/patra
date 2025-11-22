/// 防腐层（Anti-Corruption Layer）
/// 
/// 职责：将外部依赖（Provenance Starter）的模型翻译为 Ingest 领域模型， 保护领域层不受外部变化影响。
/// 
/// **核心组件**：
/// 
/// - {@link com.patra.ingest.infra.integration.provenance.acl.PlanMetadataTranslator} - 翻译
///       Provenance 的 PlanMetadata 为 BatchSchedule
/// 
/// **设计原则**：
/// 
/// - 单向翻译：外部模型 → 领域模型
///   - 隔离变化：外部依赖变更时只需修改 Translator
///   - 类型安全：使用模式匹配处理不同数据源
/// 
/// @see com.patra.ingest.domain.model.vo.plan.BatchPlan
/// @author Patra Architecture Team
/// @since 0.3.0
package com.patra.ingest.infra.integration.provenance.acl;
