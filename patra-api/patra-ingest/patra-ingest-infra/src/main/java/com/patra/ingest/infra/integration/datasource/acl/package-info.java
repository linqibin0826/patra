/**
 * 防腐层（Anti-Corruption Layer）
 *
 * <p>职责：将外部依赖（Provenance Starter）的模型翻译为 Ingest 领域模型，
 * 保护领域层不受外部变化影响。
 *
 * <p><strong>核心组件</strong>：
 * <ul>
 *   <li>{@link com.patra.ingest.infra.integration.datasource.acl.PlanMetadataTranslator} -
 *       翻译 Provenance 的 PlanMetadata 为 BatchPlan</li>
 * </ul>
 *
 * <p><strong>设计原则</strong>：
 * <ul>
 *   <li>单向翻译：外部模型 → 领域模型</li>
 *   <li>隔离变化：外部依赖变更时只需修改 Translator</li>
 *   <li>类型安全：使用模式匹配处理不同数据源</li>
 * </ul>
 *
 * @see com.patra.ingest.domain.model.vo.plan.BatchPlan
 * @author Patra Architecture Team
 * @since 0.3.0
 */
package com.patra.ingest.infra.integration.datasource.acl;
