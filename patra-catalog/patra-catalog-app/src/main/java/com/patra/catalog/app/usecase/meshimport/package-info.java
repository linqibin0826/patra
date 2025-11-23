/// MeSH 数据导入用例实现。
///
/// ## 职责
///
/// 此包实现 MeSH（医学主题词表）数据的完整导入流程编排：
///
/// - **流程编排**：协调 Domain 层（聚合根）和 Infrastructure 层（端口）
/// - **事务管理**：管理导入过程的事务边界
/// - **进度跟踪**：实时更新导入任务的进度和状态
/// - **数据验证**：确保导入数据的完整性和准确性
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator} - 主编排器，协调完整导入流程
/// - {@link com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator} -
// 数据验证器，验证导入数据量是否符合预期
/// - {@link com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO} - 导入结果 DTO
///
/// ## 使用示例
///
/// <pre>{@code
/// @Autowired
/// private MeshImportOrchestrator orchestrator;
///
/// // 启动导入（使用配置文件中的默认 URL）
/// MeshImportResultDTO result = orchestrator.startImport();
///
/// // 重试失败任务
/// MeshImportId taskId = new MeshImportId(UUID.fromString("..."));
/// orchestrator.retryFailedTask(taskId);
/// }</pre>
///
/// ## 架构位置
///
/// **App 层（Application Layer）**：
///
/// - 位于六边形架构的应用层
/// - 编排 Domain 层业务逻辑
/// - 不包含业务规则（由 Domain 层负责）
///
/// ## 设计模式
///
/// - **编排器模式（Orchestrator Pattern）**：协调多个组件完成复杂流程
/// - **流式处理（Streaming）**：处理大规模 XML 数据时使用 Stream API
/// - **批量处理（Batch Processing）**：分批次保存数据，避免内存溢出
///
/// ## 导入流程
///
/// 1. **任务创建**：创建 PENDING 状态的导入任务
/// 2. **文件下载**：从 NLM 下载 Descriptor 和 Qualifier XML 文件
/// 3. **数据解析**：使用 StAX 流式解析 XML（避免内存溢出）
/// 4. **批量导入**：按顺序导入 5 张表：
///    - Qualifier（限定词，~80 条，一次性导入）
///    - Descriptor（主题词，~30,000 条，分批导入）
///    - TreeNumber（树形编号，~80,000 条，分批导入）
///    - EntryTerm（入口术语，~250,000 条，分批导入）
///    - Concept（概念，~180,000 条，分批导入）
/// 5. **数据验证**：验证实际导入量与预期量是否一致
/// 6. **任务完成**：标记任务为 SUCCESS 状态，发布领域事件
///
/// ## 错误处理
///
/// - **下载失败**：记录错误，标记任务为 FAILED
/// - **解析失败**：捕获异常，回滚事务
/// - **批次保存失败**：记录失败批次，回滚当前批次
/// - **验证失败**：抛出异常，提示数据量不符
///
/// ## 配置项
///
/// 在 Nacos 配置中心配置以下参数（data-id: patra-catalog.yaml）：
///
/// <pre>{@code
/// mesh:
///   import:
///     descriptor-source-url:
// https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
///     qualifier-source-url:
// https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml
///     batch-sizes:
///       descriptor: 1000
///       tree-number: 2000
///       entry-term: 2000
///       concept: 2000
///     expected-counts:
///       qualifier: 80
///       descriptor: 30000
///       tree-number: 80000
///       entry-term: 250000
///       concept: 180000
/// }</pre>
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.app.usecase.meshimport;
