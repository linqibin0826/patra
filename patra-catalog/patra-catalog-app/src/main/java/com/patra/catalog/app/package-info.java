/**
 * Catalog 应用层用例编排包。
 *
 * <p>Application 层负责用例编排（Use Case Orchestration），协调 Domain 层和 Infrastructure 层，管理事务边界。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>用例编排：编排复杂业务流程，调用 Domain 层和 Infrastructure 层完成业务目标
 *   <li>事务管理：定义事务边界（@Transactional），确保数据一致性
 *   <li>异常映射：将领域异常映射为 HTTP 状态码和错误码（通过 ErrorMappingContributor SPI）
 *   <li>数据验证：协调数据验证器，验证导入数据的完整性和一致性
 *   <li>DTO 转换：将领域对象转换为 DTO，供 Adapter 层使用
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li><b>usecase.meshimport 包</b> - MeSH 数据导入用例
 *     <ul>
 *       <li>{@link com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator} - MeSH 导入编排器
 *         <ul>
 *           <li>startImport(StartImportCommand)：开始导入任务</li>
 *           <li>retryFailedTask(MeshImportId)：重试失败任务</li>
 *           <li>clearAndRestart()：清除进度重新开始</li>
 *         </ul>
 *       </li>
 *       <li>{@link com.patra.catalog.app.usecase.meshimport.MeshProgressQueryOrchestrator} - 进度查询编排器（规划中）
 *         <ul>
 *           <li>queryProgress(MeshImportId)：查询导入进度</li>
 *         </ul>
 *       </li>
 *       <li>{@link com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator} - 数据量验证器
 *         <ul>
 *           <li>validateDataCounts(Map<String, Integer>)：验证各表数据量是否在预期范围内</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li><b>error 包</b> - 异常映射贡献者
 *     <ul>
 *       <li>{@link com.patra.catalog.app.error.MeshImportErrorMappingContributor} - MeSH 导入异常映射
 *         <ul>
 *           <li>将 IllegalStateException 映射为 409 Conflict（业务状态冲突）</li>
 *           <li>将 IllegalArgumentException（"任务不存在"）映射为 404 Not Found</li>
 *           <li>将 IllegalArgumentException（其他）映射为 400 Bad Request</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li><b>config 包</b> - 配置类
 *     <ul>
 *       <li>{@link com.patra.catalog.app.config.MeshImportConfig} - MeSH 导入配置属性</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>事务边界在 Application 层</b>：Orchestrator 方法使用 @Transactional 定义事务边界
 *   <li><b>薄 Application 层</b>：不包含业务逻辑，只编排领域对象和 Port 接口
 *   <li><b>依赖 Port 接口</b>：通过 Port 接口调用 Infrastructure 层，不直接依赖技术实现
 *   <li><b>集中异常处理</b>：通过 ErrorMappingContributor SPI 集中管理异常到 HTTP 状态码的映射
 *   <li><b>复用标准异常</b>：使用 JDK 标准异常（IllegalStateException、IllegalArgumentException），通过消息内容区分业务语义
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例 1：MeSH 导入用例编排
 * @Service
 * @RequiredArgsConstructor
 * public class MeshImportOrchestrator {
 *
 *     private final MeshImportPort meshImportPort;
 *     private final XmlParserPort xmlParserPort;
 *     private final MeshFileDownloadPort meshFileDownloadPort;
 *     private final MeshDescriptorPort meshDescriptorPort;
 *     private final MeshDataValidator meshDataValidator;
 *
 *     @Transactional
 *     public MeshImportResultDTO startImport(StartImportCommand command) {
 *         // 1. 前置检查
 *         if (meshImportPort.existsRunningTask()) {
 *             throw new IllegalStateException("已有正在运行的任务");
 *         }
 *
 *         // 2. 创建任务聚合根
 *         MeshImportAggregate aggregate = createPendingTask();
 *
 *         try {
 *             // 3. 下载 XML 文件
 *             File xmlFile = meshFileDownloadPort.download(command.sourceUrl());
 *
 *             // 4. 开始导入（状态转换）
 *             aggregate.startImport();
 *             aggregate = meshImportPort.save(aggregate);
 *
 *             // 5. 解析并批量导入数据
 *             Map<String, Integer> importedCounts = importAllData(xmlFile, aggregate);
 *
 *             // 6. 验证数据量
 *             meshDataValidator.validateDataCounts(importedCounts);
 *
 *             // 7. 标记任务完成
 *             aggregate.markAsCompleted();
 *             aggregate = meshImportPort.save(aggregate);
 *
 *             return buildSuccessResult(aggregate);
 *
 *         } catch (Exception ex) {
 *             aggregate.markAsFailed(ex.getMessage());
 *             meshImportPort.save(aggregate);
 *             throw new RuntimeException("MeSH 数据导入失败", ex);
 *         }
 *     }
 * }
 *
 * // 示例 2：异常映射（ErrorMappingContributor）
 * @Component
 * public class MeshImportErrorMappingContributor implements ErrorMappingContributor {
 *
 *     @Override
 *     public Optional<ErrorCodeLike> mapException(Throwable exception) {
 *         // IllegalStateException → 409 Conflict
 *         if (exception instanceof IllegalStateException) {
 *             return Optional.of(SimpleErrorCode.create("CATALOG", "0409"));
 *         }
 *
 *         // IllegalArgumentException（"任务不存在"）→ 404 Not Found
 *         if (exception instanceof IllegalArgumentException ex) {
 *             String message = ex.getMessage();
 *             if (message != null && message.contains("任务不存在")) {
 *                 return Optional.of(SimpleErrorCode.create("CATALOG", "0404"));
 *             }
 *             // 其他 IllegalArgumentException → 400 Bad Request
 *             return Optional.of(SimpleErrorCode.create("CATALOG", "0400"));
 *         }
 *
 *         return Optional.empty();
 *     }
 * }
 *
 * // 示例 3：数据量验证器
 * @Component
 * public class MeshDataValidator {
 *
 *     public ValidationResult validateDataCounts(Map<String, Integer> actualCounts) {
 *         Map<String, Integer> expectedCounts = Map.of(
 *             "descriptor", 35000,
 *             "qualifier", 100,
 *             "tree-number", 80000,
 *             "entry-term", 250000,
 *             "concept", 180000
 *         );
 *
 *         List<String> warnings = new ArrayList<>();
 *         for (Map.Entry<String, Integer> entry : actualCounts.entrySet()) {
 *             String tableName = entry.getKey();
 *             Integer actual = entry.getValue();
 *             Integer expected = expectedCounts.get(tableName);
 *
 *             if (expected != null) {
 *                 double deviation = Math.abs((double) (actual - expected) / expected);
 *                 if (deviation > 0.05) { // 超过 5% 差异
 *                     warnings.add(String.format(
 *                         "%s 数据量差异超过 5%%：预期 %d，实际 %d",
 *                         tableName, expected, actual
 *                     ));
 *                 }
 *             }
 *         }
 *
 *         return new ValidationResult(warnings);
 *     }
 * }
 * }</pre>
 *
 * @since 0.2.0
 * @author Patra Team
 */
package com.patra.catalog.app;
