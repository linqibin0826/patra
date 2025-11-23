/// MeSH 数据导入策略包。
///
/// 职责：
///
/// - 提供不同数据类型的导入策略实现
///   - 封装单一数据类型的导入逻辑
///   - 支持策略模式（开闭原则）
///
/// **策略模式设计**：
///
/// - **接口**：{@link com.patra.catalog.app.usecase.meshimport.strategy.MeshDataImporter}
///   - **实现类**：
///   - {@link com.patra.catalog.app.usecase.meshimport.strategy.QualifierImporter} - 限定词（一次性批量）
///   - {@link com.patra.catalog.app.usecase.meshimport.strategy.DescriptorImporter} - 主题词（批次流式）
///   - {@link com.patra.catalog.app.usecase.meshimport.strategy.TreeNumberImporter} - 树形编号（批次流式）
///   - {@link com.patra.catalog.app.usecase.meshimport.strategy.EntryTermImporter} - 入口术语（批次流式）
///   - {@link com.patra.catalog.app.usecase.meshimport.strategy.ConceptImporter} - 概念（批次流式）
///
/// **设计原则**：
///
/// - **单一职责**：每个策略类只负责一种数据类型
///   - **开闭原则**：新增数据类型只需新增策略类，无需修改 Orchestrator
///   - **依赖倒置**：Orchestrator 依赖接口，不依赖具体实现
///
/// **使用方式**：
///
/// {@link com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator} 通过 Spring 自动注入所有策略实现，
/// 并按 {@link com.patra.catalog.domain.model.enums.MeshDataType} 枚举值调用对应策略。
///
/// **架构位置**：Application 层 → Use Case → Strategy
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.app.usecase.meshimport.strategy;
