/**
 * 数据源抓取元数据包
 *
 * <p>包含从外部数据源获取的元数据信息，用于批次生成规划。
 *
 * <h3>核心概念</h3>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.vo.fetch.FetchMetadata} - 数据源抓取元数据接口
 *   <li>{@link com.patra.ingest.domain.model.vo.fetch.EmptyFetchMetadata} - 空元数据实现
 * </ul>
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li><strong>防腐层</strong>：屏蔽外部数据源（Provenance Starter）的实现细节
 *   <li><strong>轻量级</strong>：只包含批次生成所需的最小信息集
 *   <li><strong>不可变</strong>：所有实现均为不可变值对象
 * </ul>
 *
 * <h3>使用流程</h3>
 *
 * <pre>
 * ProvenanceDataPort.prepareFetchMetadata()
 *   ↓ 调用 Provenance Starter
 * FetchMetadataTranslator.translate()
 *   ↓ 翻译为领域模型
 * FetchMetadata
 *   ↓ 输入到
 * BatchGenerationStrategy.generateBatches()
 * </pre>
 *
 * @since 0.3.0
 * @author Patra Architecture Team
 */
package com.patra.ingest.domain.model.vo.fetch;
