/**
 * Provenance 提供者统一契约包。
 *
 * <p>定义 Ingest 引擎与数据源提供者之间的统一接口。提供者实现遵循六边形架构原则， 仅负责数据检索和格式转换，不包含业务逻辑。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义数据源提供者的统一契约 {@link ProvenanceDataProvider}
 *   <li>提供提供者注册和发现机制 {@link ProviderRegistry}
 *   <li>规范提供者请求和响应模型
 *   <li>支持批量执行和参数传递
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ProvenanceDataProvider} - 提供者契约接口
 *   <li>{@link ProviderRegistry} - 提供者注册表
 *   <li>{@link ProviderRequest} - 提供者请求载荷（包含配置和执行参数）
 *   <li>{@link ProviderResult} - 提供者响应结果
 *   <li>{@link BatchExecutionParams} - 批量执行参数（查询 + 完整参数包）
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><b>单一职责</b> - 提供者实现仅负责数据检索，不处理业务规则
 *   <li><b>开闭原则</b> - 通过实现 {@link ProvenanceDataProvider} 扩展新数据源，无需修改现有代码
 *   <li><b>依赖倒置</b> - Ingest 引擎依赖抽象接口，不依赖具体实现
 * </ul>
 *
 * <h2>架构重构说明（0.1.0）</h2>
 *
 * <p><strong>已删除的组件</strong>:
 *
 * <ul>
 *   <li><code>BatchMetadata</code> - 已删除，批次元数据现在封装在 {@link BatchExecutionParams} 中
 * </ul>
 *
 * <p><strong>简化后的参数传递模型</strong>:
 *
 * <pre>{@code
 * ProviderRequest {
 *     config: ProvenanceConfig          // HTTP、重试、限流等运行时配置
 *     executionParams: BatchExecutionParams {
 *         query: String                 // 编译后的查询字符串
 *         params: JsonNode              // 完整参数载荷（基础 + 分页 + 运行时状态）
 *     }
 * }
 * }</pre>
 *
 * <h2>提供者实现示例</h2>
 *
 * <pre>{@code
 * @Component
 * public class CustomProvenanceDataProvider implements ProvenanceDataProvider {
 *
 *     @Override
 *     public ProvenanceCode getProvenanceCode() {
 *         return ProvenanceCode.of("custom");
 *     }
 *
 *     @Override
 *     public Set<DataType> getSupportedDataTypes() {
 *         return Set.of(DataType.LITERATURE, DataType.CITATION);
 *     }
 *
 *     @Override
 *     public <T> ProviderResult<T> fetchData(
 *             ProviderRequest request,
 *             DataType dataType,
 *             Class<T> targetClass) {
 *         // 1. 检查数据类型支持
 *         if (!supports(dataType)) {
 *             return ProviderResult.failure(dataType, "不支持的数据类型", ErrorType.NON_RETRIABLE);
 *         }
 *
 *         // 2. 从 request.executionParams() 提取查询和参数
 *         String query = request.executionParams().query();
 *         JsonNode params = request.executionParams().params();
 *
 *         // 3. 调用外部 API
 *         // 4. 转换为目标类型
 *         // 5. 包装为 ProviderResult 返回
 *         return ProviderResult.success(data, dataType, nextCursor);
 *     }
 *
 *     @Override
 *     public PlanMetadata preparePlan(String query, JsonNode params, ProvenanceConfig config) {
 *         // 实现计划准备逻辑
 *         return new PlanMetadata(totalCount, sessionToken);
 *     }
 * }
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class IngestOrchestrator {
 *     private final ProviderRegistry registry;
 *
 *     public <T> void ingest(ProvenanceCode provenanceCode, DataType dataType, Class<T> targetClass) {
 *         // 1. 获取提供者
 *         ProvenanceDataProvider provider = registry.getProvider(provenanceCode, dataType);
 *
 *         // 2. 构建请求
 *         BatchExecutionParams executionParams = new BatchExecutionParams(query, params);
 *         ProviderRequest request = ProviderRequest.builder()
 *             .config(config)
 *             .executionParams(executionParams)
 *             .build();
 *
 *         // 3. 类型安全地获取数据
 *         ProviderResult<T> result = provider.fetchData(request, dataType, targetClass);
 *
 *         // 4. 处理结果
 *         if (result.success()) {
 *             List<T> data = result.data();
 *             // 处理泛型数据
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.provider;
