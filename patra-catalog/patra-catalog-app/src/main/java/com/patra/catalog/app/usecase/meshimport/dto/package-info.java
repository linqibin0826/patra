/// MeSH 导入用例 DTO 包。
///
/// 包含 MeSH 导入用例的数据传输对象（DTO），用于跨层传递数据。
///
/// ## 职责
///
/// - **数据封装**：封装用例的输入和输出数据
///   - **跨层传递**：在 App 层和 Adapter 层之间传递数据
///   - **格式转换**：将领域对象转换为适合外部使用的格式
///   - **解耦合**：避免领域对象直接暴露到外部
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO} - 导入任务结果响应对象
///
/// - 封装任务基本信息（taskId、taskName、status、startTime、message）
///       - 用于 REST API 响应和内部调用返回值
///
/// ## 设计原则
///
/// - **不可变性**：使用 @Builder + @AllArgsConstructor + final 字段确保不可变
///   - **扁平化**：DTO 采用扁平结构，避免深层嵌套
///   - **字段校验**：使用 Jakarta Validation 注解进行字段校验
///   - **文档化**：每个字段添加 JavaDoc 注释
///
/// ## DTO vs Domain 对象
///
/// | 特性 | DTO | Domain 对象 |
/// |------|-----|------------|
/// | 用途 | 数据传输 | 业务逻辑封装 |
/// | 层次 | App/Adapter 层 | Domain 层 |
/// | 可变性 | 不可变（@Builder） | 聚合根可变，值对象不可变 |
/// | 业务逻辑 | 无 | 包含业务规则 |
/// | 字段类型 | 简单类型（String、Integer） | 强类型（MeshImportId、枚举） |
/// | 序列化 | JSON 友好 | 可能包含复杂对象 |
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：在 Orchestrator 中创建 DTO
/// @Service
/// @RequiredArgsConstructor
/// public class MeshImportOrchestrator {
///
///     public MeshImportResultDTO startImport() {
///         MeshImportAggregate task = createTask();
///
///         // 转换为 DTO
///         return MeshImportResultDTO.builder()
///             .taskId(task.getId().value().toString())
///             .taskName(task.getTaskName())
///             .status(task.getStatus().getCode())
///             .startTime(task.getStartTime())
///             .message("任务已启动，正在下载 XML 文件...")
///             .build();
///     }
/// }
///
/// // 示例 2：在 Controller 中使用 DTO
/// @RestController
/// @RequiredArgsConstructor
/// public class MeshImportController {
///
///     private final MeshImportOrchestrator orchestrator;
///
///     @PostMapping("/start")
///     public ResponseEntity<MeshImportResultDTO> startImport() {
///         MeshImportResultDTO result = orchestrator.startImport();
///         return ResponseEntity.ok(result);
///     }
/// }
/// ```
///
/// ## 架构位置
///
/// **App 层 - 数据传输对象**：
///
/// - 桥接 App 层和 Adapter 层
/// - 不属于 Domain 层，不包含业务逻辑
/// - 可以被 Controller、Orchestrator、测试代码使用
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.app.usecase.meshimport.dto;
