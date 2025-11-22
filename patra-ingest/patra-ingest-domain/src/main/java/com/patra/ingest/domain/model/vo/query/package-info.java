/// 查询会话包
/// 
/// 包含从外部数据源获取的查询会话信息，用于批次生成规划。
/// 
/// ### 核心概念
/// 
/// - {@link com.patra.ingest.domain.model.vo.query.QuerySession} - 查询会话接口
///   - {@link com.patra.ingest.domain.model.vo.query.EmptyQuerySession} - 空会话实现
/// 
/// ### 设计原则
/// 
/// - **防腐层**：屏蔽外部数据源（Provenance Starter）的实现细节
///   - **轻量级**：只包含批次生成所需的最小信息集
///   - **不可变**：所有实现均为不可变值对象
/// 
/// ### 使用流程
/// 
/// ```
/// 
/// ProvenanceDataPort.prepareQuerySession()
///   ↓ 调用 Provenance Starter
/// QuerySessionTranslator.translate()
///   ↓ 翻译为领域模型
/// QuerySession
///   ↓ 输入到
/// BatchGenerationStrategy.generateBatches()
/// 
/// ```
/// 
/// @since 0.3.0
/// @author Patra Architecture Team
package com.patra.ingest.domain.model.vo.query;
